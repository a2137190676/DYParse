# -*- coding: utf-8 -*-
"""修「第一个没下完时再下第二个就下不了」：
   根因：runDownloadQueue 的消费循环 `if(downloadQueue.isEmpty()) break;`
        → 第一个任务出队后队列空 → 主循环 break → 进入 pool.awaitTermination 干等。
        此时用户再加新任务，dlRunning 仍为 true 不会启新循环，
        老循环已在等终止，再也不会消费队列 → 新任务永远卡在队列里。
   修法：
   1) 队列空时不立刻 break：只要还有任务在跑就 sleep 等新任务进来
   2) dlRowClear 只在首次启动时执行，不要每次都把正在下载的行清掉
   3) 进度分母 fTotal 动态化（后续追加的任务也计入）
"""
import io

MA = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/MainActivity.java'
m = io.open(MA, encoding='utf-8').read()
n = 0

# ---------- 1) 动态总数 + 首次标志 ----------
old = '''        final int conc = dlConcurrency();
        new Thread(new Runnable(){ public void run(){
            int total;
            synchronized(downloadQueue){ total = downloadQueue.size(); }
            final int fTotal = total;'''
new = '''        final int conc = dlConcurrency();
        new Thread(new Runnable(){ public void run(){
            int total;
            synchronized(downloadQueue){ total = downloadQueue.size(); }
            // 动态总数：后续再追加的任务也算进分母，否则进度会超过 100%
            final java.util.concurrent.atomic.AtomicInteger dynTotal =
                    new java.util.concurrent.atomic.AtomicInteger(total);'''
if old in m:
    m = m.replace(old, new, 1); n += 1; print('1) dynTotal OK')
else:
    print('MISS 1')

# ---------- 2) 首次启动才清空下载页 ----------
old = '''            final String[] lastCatDir = new String[]{ "" };
            progShow();
            dlRowClear();
            dlSetOverall(0, fTotal, 0);
            dlRefreshHead();
            Log.i("DYP", "DLQ_CONCURRENT start conc=" + conc + " total=" + fTotal);'''
new = '''            final String[] lastCatDir = new String[]{ "" };
            progShow();
            // 只有「本次队列刚开始」才清空下载页；
            // 队列在跑的过程中追加任务时不能清，否则正在下载的行会被抹掉。
            boolean firstStart = true;
            synchronized(dlInFlight){ firstStart = dlInFlight.isEmpty(); }
            if(firstStart){
                dlRowClear();
            }else{
                Log.i("DYP", "DLQ_KEEP_ROWS 队列追加，保留已有行");
            }
            dlSetOverall(0, dynTotal.get(), 0);
            dlRefreshHead();
            Log.i("DYP", "DLQ_CONCURRENT start conc=" + conc + " total=" + dynTotal.get());'''
if old in m:
    m = m.replace(old, new, 1); n += 1; print('2) 首次才 dlRowClear OK')
else:
    print('MISS 2')

# ---------- 3) 消费循环：队列空时等新任务，不要 break ----------
old = '''                while(true){
                    if(progCancel){
                        synchronized(downloadQueue){ downloadQueue.clear(); }
                        break;
                    }
                    DlTask task;
                    synchronized(downloadQueue){
                        if(downloadQueue.isEmpty()) break;
                        task = downloadQueue.remove(0);
                        // 出队即进「正在下载」：这里也落盘，
                        // 否则下载中退出软件，这个任务就彻底丢了
                        synchronized(dlInFlight){ dlInFlight.add(task); }
                        saveDlTasks();
                    }
                    if(task == null || task.url == null || task.url.length() == 0) continue;
                    final int taskIdx = idxSeq.incrementAndGet();
                    final DlTask fTask = task;
                    running.incrementAndGet();'''
new = '''                while(true){
                    if(progCancel){
                        synchronized(downloadQueue){ downloadQueue.clear(); }
                        break;
                    }
                    DlTask task = null;
                    synchronized(downloadQueue){
                        if(!downloadQueue.isEmpty()){
                            task = downloadQueue.remove(0);
                            // 出队即进「正在下载」：这里也落盘，
                            // 否则下载中退出软件，这个任务就彻底丢了
                            synchronized(dlInFlight){ dlInFlight.add(task); }
                            saveDlTasks();
                            // 分母跟着涨：后追加的任务也要计入进度总数
                            dynTotal.incrementAndGet();
                        }
                    }
                    if(task == null){
                        // 队列空了，但可能还有任务在跑 —— 不能直接 break，
                        // 否则用户这时候再点下载，新任务会永远卡在队列里没人消费。
                        if(running.get() > 0){
                            try{ Thread.sleep(300); }catch(Throwable t){}
                            continue;
                        }
                        // 没有任务在跑：最后再确认一次队列（防止刚加进来就漏掉）
                        synchronized(downloadQueue){
                            if(!downloadQueue.isEmpty()) continue;
                        }
                        break;
                    }
                    if(task.url == null || task.url.length() == 0){
                        dynTotal.decrementAndGet();
                        doneCount.incrementAndGet();
                        continue;
                    }
                    final int taskIdx = idxSeq.incrementAndGet();
                    final DlTask fTask = task;
                    running.incrementAndGet();'''
if old in m:
    m = m.replace(old, new, 1); n += 1; print('3) 队列空时等待 OK')
else:
    print('MISS 3')

# ---------- 4) 后续引用 fTotal 的地方改成 dynTotal ----------
# runOneTask 传参
old = '''                            ok = runOneTask(fTask, taskIdx, fTotal, lastCatDir,
                                    doneCount, running);'''
new = '''                            ok = runOneTask(fTask, taskIdx, dynTotal.get(), lastCatDir,
                                    doneCount, running);'''
if old in m:
    m = m.replace(old, new, 1); n += 1; print('4a) runOneTask 用 dynTotal OK')
else:
    print('MISS 4a')

# 进度回调里的 fTotal
old = '''                            float ratio = fTotal > 0 ? (float)((double) dc / fTotal) : 0f;
                            int act = running.get();
                            String label = String.format(Locale.US, "已完成 %d/%d%s",
                                    dc, fTotal, act > 0 ? ("，进行中 " + act + " 个") : "");
                            progSet(ratio, label);
                            dlSetOverall(dc, fTotal, act);
                            if(dc >= fTotal) dlRefreshHead();'''
new = '''                            int tot2 = dynTotal.get();
                            float ratio = tot2 > 0 ? (float)((double) dc / tot2) : 0f;
                            int act = running.get();
                            String label = String.format(Locale.US, "已完成 %d/%d%s",
                                    dc, tot2, act > 0 ? ("，进行中 " + act + " 个") : "");
                            progSet(ratio, label);
                            dlSetOverall(dc, tot2, act);
                            if(dc >= tot2) dlRefreshHead();'''
if old in m:
    m = m.replace(old, new, 1); n += 1; print('4b) 进度回调用 dynTotal OK')
else:
    print('MISS 4b')

io.open(MA, 'w', encoding='utf-8').write(m)
print('changed:', n)