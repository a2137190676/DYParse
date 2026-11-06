# -*- coding: utf-8 -*-
"""逐项安全替换：每项独立判断，失败只跳过不中断。"""
import io

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/MainActivity.java'
s = io.open(P, encoding='utf-8').read()
ok = []
miss = []


def rep(name, old, new, cnt=1):
    global s
    if old in s:
        s = s.replace(old, new, cnt)
        ok.append(name)
    else:
        miss.append(name)


# ===== 1) startBatchProfileDl：去重 =====
rep('1-去重',
    '''        batchDlQueue.clear();
        batchDlQueue.addAll(picked);
        batchDlIndex = 0;
        batchDlTotal = picked.size();
        batchDlOk = 0;
        s("批量下载：共 " + batchDlTotal + " 条，逐条解析中…");
        showHintToast("开始批量下载 " + batchDlTotal + " 条（逐条解析）");
        switchTab(0);
        stepBatchProfileDl();''',
    '''        // 按 url 去重：主页列表可能有重复条目，重复下载会撞名
        java.util.LinkedHashMap<String, ProfileParser.ProfileItem> uniq =
                new java.util.LinkedHashMap<String, ProfileParser.ProfileItem>();
        for(ProfileParser.ProfileItem it : picked){
            if(it == null) continue;
            String key = it.url != null && it.url.length() > 0 ? it.url
                    : ("id:" + (it.id == null ? "" : it.id));
            if(key.length() == 0) continue;
            if(!uniq.containsKey(key)) uniq.put(key, it);
        }
        if(uniq.isEmpty()){ showHintToast("没有可下载的作品"); return; }
        int dropped = picked.size() - uniq.size();
        batchDlQueue.clear();
        batchDlQueue.addAll(uniq.values());
        batchDlIndex = 0;
        batchDlTotal = batchDlQueue.size();
        batchDlOk = 0;
        String dupTip = dropped > 0 ? ("，已去重 " + dropped + " 条") : "";
        s("批量下载：共 " + batchDlTotal + " 条" + dupTip + "，先解析全部直链…");
        showHintToast("批量下载 " + batchDlTotal + " 条" + dupTip);
        Log.i("DYP", "BATCH_DL_START total=" + batchDlTotal + " dropped=" + dropped);
        switchTab(0);
        stepBatchProfileDl();''')

# ===== 2) stepBatchProfileDl 尾部加超时保护 + kickBatchDownloads 函数 =====
rep('2-超时+kick',
    '''        Log.i("DYP", "BATCH_DL_PARSE idx=" + batchDlIndex + "/" + batchDlTotal + " url=" + u);
        loadForParse(u);
    }''',
    '''        Log.i("DYP", "BATCH_DL_PARSE idx=" + batchDlIndex + "/" + batchDlTotal + " url=" + u);
        loadForParse(u);
        // 超时保护：20 秒没回调就跳过，避免整批卡死
        final int myIdx = batchDlIndex;
        h.postDelayed(new Runnable(){ public void run(){
            if(batchDlIndex == myIdx && pendingAutoDlTag.length() > 0){
                Log.i("DYP", "BATCH_DL_TIMEOUT idx=" + myIdx);
                pendingAutoDlTag = "";
                pendingAutoDlIdx = -1;
                batchDlIndex++;
                if(batchDlIndex >= batchDlTotal) kickBatchDownloads();
                else stepBatchProfileDl();
            }
        }}, 20000);
    }

    /** 全部解析完，统一下载：这里并发才生效（队列里已有多条，线程池按 dlConcurrency 跑） */
    void kickBatchDownloads(){
        int okN = batchDlOk;
        int tot = batchDlTotal;
        batchDlIndex = -1;
        batchDlQueue.clear();
        batchDlTotal = 0;
        batchDlOk = 0;
        int queued;
        synchronized(downloadQueue){ queued = downloadQueue.size(); }
        Log.i("DYP", "BATCH_DL_KICK ok=" + okN + "/" + tot
                + " queued=" + queued + " conc=" + dlConcurrency());
        s("批量下载：已入队 " + okN + "/" + tot + " 条，开始并发下载");
        showHintToast("解析完成 " + okN + "/" + tot + " 条，开始下载");
        if(queued > 0 && !dlRunning){
            dlRunning = true;
            runDownloadQueue();
            Log.i("DYP", "BATCH_DL_QUEUE_START queued=" + queued);
        }else if(queued > 0){
            Log.i("DYP", "BATCH_DL_QUEUE_ALREADY queued=" + queued);
        }
    }''')

# ===== 3) 完成回调：改成 kick 而不是逐条 =====
rep('3-回调kick',
    '''                    if(batchDlIndex >= 0){
                        batchDlIndex++;
                        h.postDelayed(new Runnable(){ public void run(){ stepBatchProfileDl(); }}, 800);
                    }''',
    '''                    if(batchDlIndex >= 0){
                        batchDlIndex++;
                        // 1200ms：太短会打断下一条解析，curPlay 会拿到上一条地址
                        if(batchDlIndex >= batchDlTotal){
                            kickBatchDownloads();
                        }else{
                            h.postDelayed(new Runnable(){ public void run(){ stepBatchProfileDl(); }}, 1200);
                        }
                    }''')

# ===== 4) 页签跳转：批量下载不跳列表 =====
rep('4-批量不跳列表',
    '''        Log.i("DYP", "BATCH_DL_START total=" + batchDlTotal + " dropped=" + dropped);
        switchTab(0);
        stepBatchProfileDl();''',
    '''        Log.i("DYP", "BATCH_DL_START total=" + batchDlTotal + " dropped=" + dropped);
        // 批量下载不切页签：用户要求只有主页/合集解析才跳列表
        stepBatchProfileDl();''')

io.open(P, 'w', encoding='utf-8').write(s)
print('成功:', ok)
print('失败:', miss)
print('kickBatchDownloads 出现:', s.count('kickBatchDownloads'))