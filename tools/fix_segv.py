# -*- coding: utf-8 -*-
"""修 libtorrent4j native SIGSEGV：
   shutdown() 释放 session 后，后台轮询线程仍持有旧 sm/h 引用，
   继续调 sm.remove(h) / h.status() → 访问已释放的 native 对象 → 段错误。
   加「session 代际」计数器：每次 shutdown 递增，轮询线程发现代际变了就立刻退出，
   不再碰任何 native 对象。
"""
import io

BT = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/BtDownloader.java'
d = io.open(BT, encoding='utf-8').read()
n = 0

# ---------- 1) 代际计数器 ----------
old = '''    /** 停掉全局 session（App 退出或用户手动停） */
    public static synchronized void shutdown() {'''
new = '''    /**
     * session 代际计数器：每次 shutdown() 递增。
     * 轮询线程在调任何 native 方法（h.status()/sm.remove()/h.forceReannounce()…）之前
     * 都要检查代际有没有变；变了说明 session 已被释放，必须立刻退出，
     * 否则会 SIGSEGV（native 崩溃，Java 的 catch Throwable 拦不住）。
     */
    static volatile int sGeneration = 0;

    /** 停掉全局 session（App 退出或用户手动停） */
    public static synchronized void shutdown() {'''
if old in d:
    d = d.replace(old, new, 1); n += 1; print('1) sGeneration 字段 OK')
else:
    print('MISS 1')

# ---------- 2) shutdown 里递增代际 ----------
old = '''            if (sSession != null) {
                sSession.stop();
                sSession = null;
                Log.i(TAG, "BT_SESSION_STOP");
            }'''
new = '''            // 先递增代际：让所有轮询线程立刻停手，不再碰 native 对象
            sGeneration++;
            Log.i(TAG, "BT_SESSION_STOP gen=" + sGeneration);
            if (sSession != null) {
                sSession.stop();
                sSession = null;
            }'''
if old in d:
    d = d.replace(old, new, 1); n += 1; print('2) shutdown 递增代际 OK')
else:
    print('MISS 2')

# ---------- 3) start() 里记录代际 ----------
old = '''                SessionManager sm = ensureSession();
                if (sm == null) {
                    cb.onError("BT 引擎启动失败（native 库未加载）");
                    return;
                }'''
new = '''                SessionManager sm = ensureSession();
                if (sm == null) {
                    cb.onError("BT 引擎启动失败（native 库未加载）");
                    return;
                }
                // 记下本线程所属的 session 代际：之后只要代际变了就立刻退出
                final int myGen = sGeneration;'''
if old in d:
    d = d.replace(old, new, 1); n += 1; print('3) start 记录代际 OK')
else:
    print('MISS 3')

# ---------- 4) 轮询循环开头检查代际 ----------
old = '''                while (true) {
                    // 轮询周期：磁力 UI 刷新太快会抖，改为 700ms 取状态、按变化才回调
                    Thread.sleep(700);'''
new = '''                while (true) {
                    // 轮询周期：磁力 UI 刷新太快会抖，改为 700ms 取状态、按变化才回调
                    Thread.sleep(700);
                    // 关键安全闸：session 已被 shutdown（用户暂停/删除任务）→ 立刻退出，
                    // 绝不能再碰 sm/h 这些已释放的 native 对象，否则 SIGSEGV 直接闪退。
                    if (myGen != sGeneration) {
                        Log.i(TAG, "BT_ABORT_SESSION_GONE gen=" + myGen + " now=" + sGeneration);
                        return;
                    }'''
if old in d:
    d = d.replace(old, new, 1); n += 1; print('4) 循环代际检查 OK')
else:
    print('MISS 4')

# ---------- 5) handles(sm) 前后也要防护 ----------
old = '''                    // 找 handle：磁力链接一开始拿不到 infoHash 之外的，遍历所有
                    List<TorrentHandle> hs = handles(sm);
                    if (hs.isEmpty()) {'''
new = '''                    // 找 handle：磁力链接一开始拿不到 infoHash 之外的，遍历所有
                    List<TorrentHandle> hs = handles(sm);
                    if (hs.isEmpty()) {'''
n += 0

# ---------- 6) 用户取消路径的 sm.remove 也要防护 ----------
old = '''                        if (sel != null && sel.length == 1 && sel[0] == -1) {
                            Log.i(TAG, "BT_CANCELLED_BY_USER");
                            try { sm.remove(h); } catch (Throwable te) {}
                            cb.onState("已取消");
                            return;
                        }'''
new = '''                        if (sel != null && sel.length == 1 && sel[0] == -1) {
                            Log.i(TAG, "BT_CANCELLED_BY_USER");
                            // 先确认 session 还在，否则 sm.remove 会 SIGSEGV
                            if (myGen == sGeneration) {
                                try { sm.remove(h); } catch (Throwable te) {}
                            } else {
                                Log.i(TAG, "BT_CANCEL_SESSION_GONE");
                            }
                            cb.onState("已取消");
                            return;
                        }'''
if old in d:
    d = d.replace(old, new, 1); n += 1; print('6) 取消路径防护 OK')
else:
    print('MISS 6')

io.open(BT, 'w', encoding='utf-8').write(d)
print('changed:', n)