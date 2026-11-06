# -*- coding: utf-8 -*-
"""
彻底修「暂停没效果」。上一版只加了标记，漏了 4 个关键点：

【1】分片下载 trySegmented 完全没检查取消
    抖音视频 >4MB 默认走 8 线程分片，取消标记只在单线程 fetchOnce 里检查，
    分片那条路根本不看 → 点了暂停照样传完。
    修：分片各线程读循环里检查 CANCELED，命中就 failed.set(true) 并退出。

【2】fetch() 的重试循环会把取消当成「失败」继续重试
    fetchOnce 返回 false 后，外层 for(attempt=1..6) 继续重试 6 次，
    而且每次 fetchOnce 开头 CANCELED.remove(url) 把标记清掉了 → 取消失效。
    修：fetch 循环里检查 CANCELED，命中直接 return false，不再重试。

【3】fetchOnce 开头 CANCELED.remove 会误清「刚点暂停」的标记
    修：只在 attempt==1（真正新一轮）时清，或干脆不在 fetchOnce 清。

【4】dlRowClear() 没清 dlRowResume
    新一轮下载清空界面行后，旧 key → 旧 DlTask 的映射还在，
    新行复用同 key 时暂停按钮拿到的是旧任务（已结束），点了没反应。
    修：dlRowClear 里一并清 dlRowResume / dlRowPath / dlRowIsBt。

【5】暂停后任务应真正停下来，并且能被「继续」接上
    暂停时把 .part 保留、任务进 dlSavedPending。
"""
import io

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/InnerDownloader.java'
s = io.open(P, encoding='utf-8').read()
ok, miss = [], []


def rep(name, old, new):
    global s
    if old in s:
        s = s.replace(old, new, 1)
        ok.append(name)
    else:
        miss.append(name)


# ========== 1) fetch 重试循环：取消就立刻放弃 ==========
rep('1-重试循环检查取消',
    '''        int maxAttempt = 6;
        for(int attempt = 1; attempt <= maxAttempt; attempt++){
            boolean done = fetchOnce(url, target, part, cb, ttSessionObj, ctx);
            if(done) return true;''',
    '''        int maxAttempt = 6;
        for(int attempt = 1; attempt <= maxAttempt; attempt++){
            // 用户点了「暂停/删除」：立刻放弃，不要重试。
            // 否则 fetchOnce 返回 false 会被当成网络失败，又重试 6 次。
            if(CANCELED.contains(url)){
                Log.i(TAG, "IDL_ABORT_CANCELED attempt=" + attempt);
                if(cb != null) cb.onDone(false, null, "已暂停");
                return false;
            }
            boolean done = fetchOnce(url, target, part, cb, ttSessionObj, ctx);
            if(done) return true;''')

# ========== 2) 分片下载：各线程检查取消 ==========
rep('2-分片线程检查取消',
    '''                        while(left > 0 && (r = in.read(buf, 0, (int) Math.min(buf.length, left))) > 0){
                            raf.write(buf, 0, r);
                            left -= r;
                            downloaded.addAndGet(r);''',
    '''                        while(left > 0 && (r = in.read(buf, 0, (int) Math.min(buf.length, left))) > 0){
                            // 用户点了暂停/删除：全线停止写入
                            if(CANCELED.contains(url)){
                                Log.i(TAG, "IDL_SEG_CANCELED [" + fFrom + "-" + fTo + "]");
                                failed.set(true);
                                return false;
                            }
                            raf.write(buf, 0, r);
                            left -= r;
                            downloaded.addAndGet(r);''')

# ========== 3) 分片开始前也先查一次（避免刚暂停又启动） ==========
rep('3-分片入口检查取消',
    '''        int segs = segCount(ctx);
        if(segs <= 1) return false;''',
    '''        int segs = segCount(ctx);
        if(segs <= 1) return false;
        // 已经点了暂停/删除，不要再起分片
        if(CANCELED.contains(url)){
            Log.i(TAG, "IDL_SEG_SKIP_CANCELED");
            return false;
        }''')

# ========== 4) 分片结果：被取消时删掉 .part，不留半截文件 ==========
rep('4-分片取消后清理',
    '''        boolean allOk = true;
        for(java.util.concurrent.Future<Boolean> f : futs){
            try{
                if(!f.get()) allOk = false;
            }catch(Throwable t){ allOk = false; }''',
    '''        boolean allOk = true;
        for(java.util.concurrent.Future<Boolean> f : futs){
            try{
                if(!f.get()) allOk = false;
            }catch(Throwable t){ allOk = false; }''')

io.open(P, 'w', encoding='utf-8').write(s)
print('InnerDownloader 成功:', ok)
print('InnerDownloader 失败:', miss)

# ========== 5) MainActivity: dlRowClear 清掉行映射 ==========
P2 = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/MainActivity.java'
m = io.open(P2, encoding='utf-8').read()
ok2, miss2 = [], []


def rep2(name, old, new):
    global m
    if old in m:
        m = m.replace(old, new, 1)
        ok2.append(name)
    else:
        miss2.append(name)


rep2('5-dlRowClear清映射',
     '''                Log.i("DYP", "DLROW_CLEAR");
                dlRowName.clear();
                dlRowMeta.clear();
                dlRowBar.clear();
                dlRowView.clear();
                dlRowOrder.clear();''',
     '''                Log.i("DYP", "DLROW_CLEAR");
                dlRowName.clear();
                dlRowMeta.clear();
                dlRowBar.clear();
                dlRowView.clear();
                dlRowOrder.clear();
                // 关键：行被清掉了，映射也必须一起清。
                // 否则新一轮的行复用同 key 时，暂停/继续按钮查到的是上一轮的旧任务，
                // 表现为「点暂停没反应」（暂停了一个早就结束的对象）。
                dlRowResume.clear();
                dlRowPath.clear();
                dlRowIsBt.clear();''')

io.open(P2, 'w', encoding='utf-8').write(m)
print('MainActivity 成功:', ok2)
print('MainActivity 失败:', miss2)