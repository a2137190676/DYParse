# -*- coding: utf-8 -*-
"""
修「批量下载 403」：直链过期。

现象：
  BATCH_DL_KICK ok=13/13 queued=1 conc=5
  DL_END 12/13 ok=false HTTP 403
  DL_END 11/13 ok=false HTTP 403

根因：
  抖音视频直链带时效签名（x-expires，约 5~10 分钟）。
  现在的流程是「全部解析完 → 统一下载」，
  13 条串行解析每条约 8~15 秒，全部解析完要 2~3 分钟，
  最早解析出来的那几条，等到真正下载时签名已经过期 → 403。

修法：
  改成「边解析边下载的流水线」：
    · 解析完一条立刻入队并启动下载（不等待全部解析完）
    · 但为了保持并发，用「解析 N 条后启动队列」的策略：
      解析到第 conc 条时启动下载队列，后面边解析边补充进队列
    · 这样队列里始终有多条任务，并发能拉满，同时直链又不会过期

  实现：新增 batchDlFirstKick 标志，解析到第 conc 条时首次 kick，
  之后每条解析完直接入队（队列已在跑，自动并发消费）。
"""
import io

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/MainActivity.java'
s = io.open(P, encoding='utf-8').read()
ok, miss = [], []


def rep(name, old, new):
    global s
    if old in s:
        s = s.replace(old, new, 1)
        ok.append(name)
    else:
        miss.append(name)


# ---------- 1) 新增首次 kick 标志 ----------
rep('1-标志',
    '''    int batchDlTotal = 0;
    int batchDlOk = 0;''',
    '''    int batchDlTotal = 0;
    int batchDlOk = 0;
    /** 批量下载是否已首次启动下载队列（解析够 conc 条就启动，避免直链过期） */
    boolean batchDlKicked = false;''')

# ---------- 2) 解析成功入队后：够 conc 条就首次启动 ----------
rep('2-首次kick',
    '''                        enqueueDownload(dl, tag, "mp4", "视频");
                        showHintToast("已加入下载队列：" + tag);
                        s("已加入下载队列：" + tag);
                        batchDlOk++;''',
    '''                        enqueueDownload(dl, tag, "mp4", "视频");
                        showHintToast("已加入下载队列：" + tag);
                        s("已加入下载队列：" + tag);
                        batchDlOk++;
                        // 直链有时效（约 5~10 分钟），不能等全部解析完再下。
                        // 解析够 conc 条就启动队列，之后边解析边补充，
                        // 既能并发拉满，又不会让早解析出的直链过期。
                        if(!batchDlKicked && batchDlOk >= dlConcurrency()){
                            batchDlKicked = true;
                            int q;
                            synchronized(downloadQueue){ q = downloadQueue.size(); }
                            Log.i("DYP", "BATCH_DL_EARLY_KICK ok=" + batchDlOk
                                    + " queued=" + q + " conc=" + dlConcurrency());
                            if(q > 0 && !dlRunning){
                                dlRunning = true;
                                runDownloadQueue();
                            }
                        }''')

# ---------- 3) startBatchProfileDl 里重置标志 ----------
rep('3-重置标志',
    '''        batchDlIndex = 0;
        batchDlTotal = batchDlQueue.size();
        batchDlOk = 0;
        String dupTip''',
    '''        batchDlIndex = 0;
        batchDlTotal = batchDlQueue.size();
        batchDlOk = 0;
        batchDlKicked = false;
        String dupTip''')

# ---------- 4) kickBatchDownloads 结束时重置标志 ----------
rep('4-kick重置',
    '''        batchDlIndex = -1;
        batchDlQueue.clear();
        batchDlTotal = 0;
        batchDlOk = 0;
        int queued;''',
    '''        batchDlIndex = -1;
        batchDlQueue.clear();
        batchDlTotal = 0;
        batchDlOk = 0;
        batchDlKicked = false;
        int queued;''')

# ---------- 5) enqueueSingle 里：批量解析阶段不再无脑 hold ----------
# 改成：只有还没首次 kick 时才 hold
rep('5-hold条件',
    '''        // 批量解析阶段只入队不启动：否则第一条立刻开跑，队列里只有 1 个任务，并发等于 1。
        // 等全部解析完由 kickBatchDownloads 统一启动，并发数才生效。
        boolean inBatchParse = (batchDlIndex >= 0 && batchDlIndex < batchDlTotal);
        if(!dlRunning && !inBatchParse){
            dlRunning = true;
            runDownloadQueue();
        } else if(inBatchParse){
            Log.i("DYP", "DL_QUEUE_HOLD 批量解析中，暂不启动");
        }
    }''',
    '''        // 批量解析阶段：解析够 conc 条后由 BATCH_DL_EARLY_KICK 启动队列，
        // 之后每条入队都直接跑（dlRunning 已是 true）。
        // 在首次 kick 之前不启动，避免队列里只有 1 条时并发等于 1。
        boolean holdForBatch = (batchDlIndex >= 0 && batchDlIndex < batchDlTotal
                && !batchDlKicked);
        if(!dlRunning && !holdForBatch){
            dlRunning = true;
            runDownloadQueue();
        } else if(holdForBatch){
            Log.i("DYP", "DL_QUEUE_HOLD 批量解析中（未达并发数），暂不启动");
        }
    }''')

io.open(P, 'w', encoding='utf-8').write(s)
print('成功:', ok)
print('失败:', miss)
print('batchDlKicked:', s.count('batchDlKicked'))