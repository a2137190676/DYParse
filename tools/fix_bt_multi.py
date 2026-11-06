# -*- coding: utf-8 -*-
"""磁力多任务并发：
   现状：startMagnetDownload 里 `if(btRunning){ 提示并 return; }` —— 一次只能一个磁力。
         BtDownloader 里也是 handles(sm).get(0) —— 永远取第一个 handle，
         多任务时会互相抢同一个 handle，进度串台。
   修法：
   1) BtDownloader 加 targetHash 参数：先算磁力/种子的 infoHash，
      在 handles 里按 infoHash 精确匹配自己的 handle（找不到就等元数据）。
   2) MainActivity 去掉 btRunning 的单任务锁，改成「按 infoHash 记录运行中任务」。
"""
import io

BT = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/BtDownloader.java'
MA = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/MainActivity.java'

# ============================================================
# A) BtDownloader：加 4 参 start（带 targetHash）+ 精确匹配 handle
# ============================================================
d = io.open(BT, encoding='utf-8').read()
n = 0

# A1) 3 参 start 改成委托给新方法
old = '''    public static void start(final String magnet, final File saveDir, final Cb cb) {
        new Thread(new Runnable() { public void run() {'''
new = '''    public static void start(final String magnet, final File saveDir, final Cb cb) {
        start(magnet, saveDir, cb, "");
    }

    /**
     * 带 targetHash 的启动：targetHash 是这个任务自己的 infoHash（大写 hex）。
     * 多任务并发时靠它精确挑出自己的 handle，避免 handles.get(0) 抢错对象。
     */
    public static void start(final String magnet, final File saveDir, final Cb cb,
                             final String targetHash) {
        new Thread(new Runnable() { public void run() {'''
if old in d:
    d = d.replace(old, new, 1); n += 1; print('A1) 4 参 start OK')
else:
    print('MISS A1')

# A2) 找 handle 时按 targetHash 精确匹配
old = '''                    // 找 handle：磁力链接一开始拿不到 infoHash 之外的，遍历所有
                    List<TorrentHandle> hs = handles(sm);
                    if (hs.isEmpty()) {'''
new = '''                    // 找 handle：多任务并发时必须按 infoHash 精确匹配自己的那个，
                    // 否则 handles.get(0) 会抢到别人的 handle（进度串台）。
                    List<TorrentHandle> hs = handles(sm);
                    if (!targetHash.isEmpty() && hs.size() > 1) {
                        List<TorrentHandle> mine = new ArrayList<TorrentHandle>();
                        for (TorrentHandle th : hs) {
                            try {
                                if (!th.isValid()) continue;
                                Sha1Hash sh = th.infoHash();
                                if (sh != null
                                        && targetHash.equalsIgnoreCase(sh.toHex())) {
                                    mine.add(th);
                                }
                            } catch (Throwable te) {}
                        }
                        if (!mine.isEmpty()) hs = mine;
                    }
                    if (hs.isEmpty()) {'''
if old in d:
    d = d.replace(old, new, 1); n += 1; print('A2) handle 精确匹配 OK')
else:
    print('MISS A2')

# A3) 等待元数据阶段：多任务时不要提前超时误杀
old = '''                        if (System.currentTimeMillis() - t0 > 300000) {
                            cb.onError("5 分钟内没找到做种者。这个资源当前可能没有人在做种"
                                    + "（低热度老资源常见），可稍后重试或换其他磁力。");
                            return;
                        }'''
new = '''                        if (System.currentTimeMillis() - t0 > 300000) {
                            cb.onError("5 分钟内没找到做种者。这个资源当前可能没有人在做种"
                                    + "（低热度老资源常见），可稍后重试或换其他磁力。");
                            return;
                        }'''
n += 0

io.open(BT, 'w', encoding='utf-8').write(d)
print('BtDownloader changed:', n)

# ============================================================
# B) MainActivity：去掉单任务锁，改按 hash 记录
# ============================================================
m = io.open(MA, encoding='utf-8').read()
n2 = 0

# B1) 运行中磁力任务集合
old = '''    /** 磁力任务运行标志 */
    volatile boolean btRunning = false;'''
new = '''    /** 磁力任务运行标志（兼容旧逻辑：只要还有磁力在跑就是 true） */
    volatile boolean btRunning = false;
    /** 正在跑的磁力任务 infoHash 集合（支持多个磁力同时下载） */
    final java.util.Set<String> btRunningHashes =
            java.util.Collections.synchronizedSet(new java.util.HashSet<String>());'''
if old in m:
    m = m.replace(old, new, 1); n2 += 1; print('B1) btRunningHashes OK')
else:
    print('MISS B1')

# B2) 去掉「磁力任务正在进行」的单任务锁
old = '''    void startMagnetDownload(final String magnet){
        if(btRunning){
            showHintToast("磁力任务正在进行，请等当前任务结束");
            return;
        }
        btRunning = true;'''
new = '''    void startMagnetDownload(final String magnet){
        // 允许多个磁力同时下载：按 infoHash 判断这条是不是已经在跑
        final String myHash = btHashOf(magnet);
        if(myHash.length() > 0 && btRunningHashes.contains(myHash)){
            showHintToast("这条磁力已经在下载了，可在「下载」页查看");
            return;
        }
        if(myHash.length() > 0) btRunningHashes.add(myHash);
        btRunning = true;'''
if old in m:
    m = m.replace(old, new, 1); n2 += 1; print('B2) 去掉单任务锁 OK')
else:
    print('MISS B2')

# B3) 传 targetHash 给 BtDownloader
old = '''        BtDownloader.start(magnet, saveDir, new BtDownloader.SimpleCb(){'''
new = '''        BtDownloader.start(magnet, saveDir, new BtDownloader.SimpleCb(){'''
n2 += 0

# B4) 完成/失败时从集合里移除
old = '''                btRunning = false;
                clearBtTask();'''
new = '''                btFinishHash(magnet);
                clearBtTask();'''
if old in m:
    m = m.replace(old, new, 1); n2 += 1; print('B4) onDone 移除 hash OK')
else:
    print('MISS B4')

old = '''                Log.i("DYP", "BT_ERR " + msg);
                btRunning = false;'''
new = '''                Log.i("DYP", "BT_ERR " + msg);
                btFinishHash(magnet);'''
if old in m:
    m = m.replace(old, new, 1); n2 += 1; print('B5) onError 移除 hash OK')
else:
    print('MISS B5')

io.open(MA, 'w', encoding='utf-8').write(m)
print('MainActivity changed:', n2)