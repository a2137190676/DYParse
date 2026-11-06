# -*- coding: utf-8 -*-
"""精确修复：用 enqueueSingle 里的实际内容做锚点。"""
import io

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/MainActivity.java'
s = io.open(P, encoding='utf-8').read()

n = 0

# ---------- A) enqueueSingle 尾部：批量解析中只入队不启动 ----------
old = '''        Log.i("DYP", "DL_QUEUE_SINGLE + " + tag + " cat=" + cat);
        saveDlTasks();
        if(!dlRunning){
            dlRunning = true;
            runDownloadQueue();
        }
    }'''
new = '''        Log.i("DYP", "DL_QUEUE_SINGLE + " + tag + " cat=" + cat);
        saveDlTasks();
        // 批量解析阶段只入队不启动：否则第一条立刻开跑，队列里只有 1 个任务，并发等于 1。
        // 等全部解析完由 kickBatchDownloads 统一启动，并发数才生效。
        boolean inBatchParse = (batchDlIndex >= 0 && batchDlIndex < batchDlTotal);
        if(!dlRunning && !inBatchParse){
            dlRunning = true;
            runDownloadQueue();
        } else if(inBatchParse){
            Log.i("DYP", "DL_QUEUE_HOLD 批量解析中，暂不启动");
        }
    }'''
if old in s:
    s = s.replace(old, new, 1)
    n += 1
    print('A) enqueueSingle 尾部 OK')
else:
    print('A) MISS')

# ---------- B) enqueueDownload（第5参版）尾部 ----------
old2 = '''        Log.i("DYP", "DL_QUEUE + " + tag + " cat=" + cat + " mix=" + mixDir);
        saveDlTasks();
        if(!dlRunning){
            dlRunning = true;
            runDownloadQueue();
        }
    }'''
new2 = '''        Log.i("DYP", "DL_QUEUE + " + tag + " cat=" + cat + " mix=" + mixDir);
        saveDlTasks();
        boolean inBatchParse2 = (batchDlIndex >= 0 && batchDlIndex < batchDlTotal);
        if(!dlRunning && !inBatchParse2){
            dlRunning = true;
            runDownloadQueue();
        } else if(inBatchParse2){
            Log.i("DYP", "DL_QUEUE_HOLD 批量解析中，暂不启动");
        }
    }'''
if old2 in s:
    s = s.replace(old2, new2, 1)
    n += 1
    print('B) enqueueDownload 尾部 OK')
else:
    print('B) MISS（可能日志文本不同，稍后单独处理）')

# ---------- C) kickBatchDownloads 补启动 ----------
old3 = '''        Log.i("DYP", "BATCH_DL_KICK ok=" + ok + "/" + tot + " conc=" + dlConcurrency());
        s("批量下载：已入队 " + ok + "/" + tot + " 条，开始并发下载");
        showHintToast("批量完成解析：" + ok + "/" + tot + " 条，开始下载");
    }'''
new3 = '''        int queued;
        synchronized(downloadQueue){ queued = downloadQueue.size(); }
        Log.i("DYP", "BATCH_DL_KICK ok=" + ok + "/" + tot
                + " queued=" + queued + " conc=" + dlConcurrency());
        s("批量下载：已入队 " + ok + "/" + tot + " 条，开始并发下载");
        showHintToast("批量完成解析：" + ok + "/" + tot + " 条，开始下载");
        // 真正把队列拉起来，否则任务只是躺在队列里没人消费
        if(queued > 0 && !dlRunning){
            dlRunning = true;
            runDownloadQueue();
            Log.i("DYP", "BATCH_DL_QUEUE_START queued=" + queued);
        }else if(queued > 0){
            Log.i("DYP", "BATCH_DL_QUEUE_ALREADY_RUNNING queued=" + queued);
        }
    }'''
if old3 in s:
    s = s.replace(old3, new3, 1)
    n += 1
    print('C) kickBatchDownloads 补启动 OK')
else:
    print('C) MISS')

io.open(P, 'w', encoding='utf-8').write(s)
print('总改动:', n)