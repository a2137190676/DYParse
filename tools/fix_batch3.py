# -*- coding: utf-8 -*-
"""
一次修 4 个问题：

【问题1】没有 5 个并发，实际一条一条下
根因：批量下载走 startBatchProfileDl → stepBatchProfileDl 串行解析，
      每条解析完才入队。解析是串行的，所以下载也变成串行。

修法：批量下载改成「先全部解析入队，再统一下载」。
      · 解析阶段：串行（curPlay 是全局的，必须串行）
      · 下载阶段：全部入队后由 runDownloadQueue 按 dlConcurrency 并发下载
      现在解析完一条只是入队（不立刻触发下载），
      等全部解析完再统一 kick 一次，这样并发才能生效。

【问题2】12 个只成功 8 个，4 个失败
根因：串行解析时 800ms 间隔太短，后一条的 loadForParse 会打断前一条，
      导致 pendingAutoDlTag 被覆盖、curPlay 拿到上一条的地址。
      另外失败时没有重试。

修法：解析间隔加大到 1200ms，并加解析超时保护（15 秒没结果就跳过）。

【问题3】批量下载有重复
根因：startBatchProfileDl 里 picked 是 list.get(i)，
      如果主页列表里本来就有重复 url，会重复下载。
      另外并发入队时 taskStableName 相同也会撞名。

修法：入队前按 url 去重。

【问题4】解析完不要跳转到列表页（主页/合集除外）
根因：多处 switchTab(1) 无条件跳转。

修法：只保留主页解析（showProfileList）和合集（showMixList）的跳转，
      单条解析/批量解析不跳。
"""
import io

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/MainActivity.java'
s = io.open(P, encoding='utf-8').read()

# ========== 修问题3+1：入队去重 + 全部解析完再统一下载 ==========
old_start = '''    void startBatchProfileDl(java.util.List<ProfileParser.ProfileItem> picked){
        if(picked == null || picked.isEmpty()){
            showHintToast("没有勾选任何作品");
            return;
        }
        if(batchDlIndex >= 0 && batchDlIndex < batchDlQueue.size()){
            showHintToast("批量下载进行中，请稍候");
            return;
        }
        batchDlQueue.clear();
        batchDlQueue.addAll(picked);
        batchDlIndex = 0;
        batchDlTotal = picked.size();
        batchDlOk = 0;
        s("批量下载：共 " + batchDlTotal + " 条，逐条解析中…");
        showHintToast("开始批量下载 " + batchDlTotal + " 条（逐条解析）");
        switchTab(0);
        stepBatchProfileDl();
    }'''

new_start = '''    void startBatchProfileDl(java.util.List<ProfileParser.ProfileItem> picked){
        if(picked == null || picked.isEmpty()){
            showHintToast("没有勾选任何作品");
            return;
        }
        if(batchDlIndex >= 0 && batchDlIndex < batchDlQueue.size()){
            showHintToast("批量下载进行中，请稍候");
            return;
        }
        // 按 url 去重：主页列表里同一条作品可能出现多次，重复下载会撞名
        java.util.LinkedHashMap<String, ProfileParser.ProfileItem> uniq =
                new java.util.LinkedHashMap<String, ProfileParser.ProfileItem>();
        for(ProfileParser.ProfileItem it : picked){
            if(it == null) continue;
            String key = it.url != null && it.url.length() > 0 ? it.url
                    : ("id:" + (it.id == null ? "" : it.id));
            if(key.length() == 0) continue;
            if(!uniq.containsKey(key)) uniq.put(key, it);
        }
        if(uniq.isEmpty()){
            showHintToast("没有可下载的作品");
            return;
        }
        int dropped = picked.size() - uniq.size();
        batchDlQueue.clear();
        batchDlQueue.addAll(uniq.values());
        batchDlIndex = 0;
        batchDlTotal = batchDlQueue.size();
        batchDlOk = 0;
        String dupTip = dropped > 0 ? ("，已去重 " + dropped + " 条") : "";
        s("批量下载：共 " + batchDlTotal + " 条" + dupTip + "，先解析全部直链…");
        showHintToast("批量下载 " + batchDlTotal + " 条" + dupTip + "（解析完统一下载）");
        Log.i("DYP", "BATCH_DL_START total=" + batchDlTotal + " dropped=" + dropped);
        switchTab(0);
        stepBatchProfileDl();
    }'''
if old_start not in s:
    raise SystemExit('MISS startBatchProfileDl')
s = s.replace(old_start, new_start, 1)

# ========== 修问题2：解析间隔加大 + 去重后统计 ==========
old_step = '''        pendingAutoDlTag = "视频" + (batchDlIndex + 1);
        pendingAutoDlIdx = batchDlIndex;
        s("批量下载 " + (batchDlIndex + 1) + "/" + batchDlTotal + "：解析中…");
        Log.i("DYP", "BATCH_DL_PARSE idx=" + batchDlIndex + "/" + batchDlTotal + " url=" + u);
        loadForParse(u);
    }'''
new_step = '''        pendingAutoDlTag = "视频" + (batchDlIndex + 1);
        pendingAutoDlIdx = batchDlIndex;
        s("批量下载 " + (batchDlIndex + 1) + "/" + batchDlTotal + "：解析中…");
        Log.i("DYP", "BATCH_DL_PARSE idx=" + batchDlIndex + "/" + batchDlTotal + " url=" + u);
        loadForParse(u);
        // 解析超时保护：20 秒还没回调就跳过这一条，避免整批卡死
        final int myIdx = batchDlIndex;
        h.postDelayed(new Runnable(){ public void run(){
            if(batchDlIndex == myIdx && pendingAutoDlTag.length() > 0){
                Log.i("DYP", "BATCH_DL_TIMEOUT idx=" + myIdx);
                pendingAutoDlTag = "";
                pendingAutoDlIdx = -1;
                batchDlIndex++;
                if(batchDlIndex >= batchDlTotal){
                    kickBatchDownloads();
                }else{
                    stepBatchProfileDl();
                }
            }
        }}, 20000);
    }

    /**
     * 全部解析完，统一下载。
     *
     * 关键：这里是并发生效的地方。
     * 解析阶段串行（curPlay 是全局的），但下载阶段全部交给 runDownloadQueue，
     * 它会按 dlConcurrency（默认 5）开线程池并发下载。
     */
    void kickBatchDownloads(){
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
if old_step not in s:
    raise SystemExit('MISS stepBatchProfileDl')
s = s.replace(old_step, new_step, 1)

# ========== 修问题1：解析完成回调里不要在最后一条立即下载，改为统一 kick ==========
old_done = '''                    if(batchDlIndex >= 0){
                        batchDlIndex++;
                        h.postDelayed(new Runnable(){ public void run(){ stepBatchProfileDl(); }}, 800);
                    }'''
new_done = '''                    if(batchDlIndex >= 0){
                        batchDlIndex++;
                        // 间隔 1200ms：太短会打断下一条解析，导致 curPlay 拿到上一条地址
                        if(batchDlIndex >= batchDlTotal){
                            kickBatchDownloads();
                        }else{
                            h.postDelayed(new Runnable(){ public void run(){ stepBatchProfileDl(); }}, 1200);
                        }
                    }'''
if old_done not in s:
    raise SystemExit('MISS 完成回调')
s = s.replace(old_done, new_done, 1)

# ========== 修问题1：入队时不立刻触发下载，等全部入队后统一跑 ==========
# 在批量下载期间，enqueueDownload 只是入队不启动队列
old_enq = '''        downloadQueue.add(t);
        saveDlTasks();
        if(!dlRunning){
            dlRunning = true;
            runDownloadQueue();
        }
    }
    /** 从链接里抠稳定的作品 id'''
new_enq = '''        downloadQueue.add(t);
        saveDlTasks();
        // 批量下载解析阶段：只入队不启动，等全部解析完由 kickBatchDownloads 统一启动，
        // 否则第一个任务会立刻开跑，但队列只有 1 个，并发等于 1。
        boolean inBatchParse = (batchDlIndex >= 0 && batchDlIndex < batchDlTotal);
        if(!dlRunning && !inBatchParse){
            dlRunning = true;
            runDownloadQueue();
        } else if(inBatchParse){
            Log.i("DYP", "DL_QUEUE_HOLD 批量解析中，暂不启动下载队列");
        }
    }
    /** 从链接里抠稳定的作品 id'''
if old_enq not in s:
    raise SystemExit('MISS enqueueDownload 尾部')
s = s.replace(old_enq, new_enq, 1)

io.open(P, 'w', encoding='utf-8').write(s)
print('问题1/2/3 修完')
print('kickBatchDownloads:', s.count('kickBatchDownloads'))
print('BATCH_DL_START:', s.count('BATCH_DL_START'))