# -*- coding: utf-8 -*-
"""
补两件事：
  A) kickBatchDownloads 里真正启动下载队列（否则入了队没人消费）
  B) 修「解析完不要跳列表页」：只保留主页/合集跳转
"""
import io

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/MainActivity.java'
s = io.open(P, encoding='utf-8').read()

# ========== A) kickBatchDownloads 真正启动下载队列 ==========
old_kick = '''    void kickBatchDownloads(){
        int ok = batchDlOk;
        int tot = batchDlTotal;
        batchDlIndex = -1;
        batchDlQueue.clear();
        batchDlTotal = 0;
        batchDlOk = 0;
        Log.i("DYP", "BATCH_DL_KICK ok=" + ok + "/" + tot + " conc=" + dlConcurrency());
        s("批量下载：已入队 " + ok + "/" + tot + " 条，开始并发下载");
        showHintToast("批量完成解析：" + ok + "/" + tot + " 条，开始下载");
    }'''

new_kick = '''    void kickBatchDownloads(){
        int ok = batchDlOk;
        int tot = batchDlTotal;
        batchDlIndex = -1;
        batchDlQueue.clear();
        batchDlTotal = 0;
        batchDlOk = 0;
        int queued;
        synchronized(downloadQueue){ queued = downloadQueue.size(); }
        Log.i("DYP", "BATCH_DL_KICK ok=" + ok + "/" + tot
                + " queued=" + queued + " conc=" + dlConcurrency());
        s("批量下载：已入队 " + ok + "/" + tot + " 条，开始并发下载");
        showHintToast("批量完成解析：" + ok + "/" + tot + " 条，开始下载");
        // 关键：真正把下载队列拉起来，否则任务只是躺在队列里没人消费
        if(queued > 0 && !dlRunning){
            dlRunning = true;
            runDownloadQueue();
            Log.i("DYP", "BATCH_DL_QUEUE_START queued=" + queued);
        } else if(queued > 0 && dlRunning){
            Log.i("DYP", "BATCH_DL_QUEUE_ALREADY_RUNNING queued=" + queued);
        }
    }'''
if old_kick not in s:
    raise SystemExit('MISS kickBatchDownloads')
s = s.replace(old_kick, new_kick, 1)

# ========== B) 修页签跳转：只在主页/合集跳列表 ==========
# 找出所有 switchTab(1) 的上下文，逐个判断
# 1) 行 6352：showMixList（合集）—— 保留
# 2) 行 10987 附近：需要看上下文
# 3) 13216/13250/13294/13355：主页相关

# 先看 10987 附近
import re
lines = s.split('\n')
print('=== switchTab(1) 出现位置 ===')
for i, ln in enumerate(lines):
    if 'switchTab(1)' in ln:
        ctx_start = max(0, i - 3)
        ctx = '\n'.join(lines[ctx_start:i + 1])
        print('--- 行 %d ---' % (i + 1))
        print(ctx[-400:])
        print()

io.open(P, 'w', encoding='utf-8').write(s)
print('A) kickBatchDownloads 已补启动逻辑')