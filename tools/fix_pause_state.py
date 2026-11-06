# -*- coding: utf-8 -*-
"""
单视频暂停专属问题（批量也有）：

【A】暂停后行状态被覆盖成「✗ 失败」
    runOneTask 结尾：
        dlRowUpdate(rowKey, ok ? 1000 : -1, ok ? "✓ 完成" : "✗ 失败");
    InnerDownloader 被取消时返回 false → ok=false → 这行把
    暂停按钮刚写的「已暂停 · 点「继续」接着下」覆盖成「✗ 失败」。
    用户看到的就是：点暂停，闪一下「已暂停」，立刻变「失败」，像没生效。

【B】暂停后被当成失败任务回填 dlFailed
    runDownloadQueue 的 finally：
        } else if(!ok){ dlFailed.add(fTask); }
    暂停的任务会进 dlFailed，收尾时又被写成「未完成」，
    和用户主动点「继续」的语义冲突，还会让下载页出现重复条目。

修法：加一个 dlPaused 集合（key=url|tag），暂停时登记；
     runOneTask 结尾和 finally 回填时都跳过已暂停的任务。
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


# ---------- 1) 新增 dlPaused 集合 ----------
rep('1-dlPaused集合',
    '''    final java.util.Set<String> dlDeleted =
            java.util.Collections.synchronizedSet(new java.util.HashSet<String>());''',
    '''    final java.util.Set<String> dlDeleted =
            java.util.Collections.synchronizedSet(new java.util.HashSet<String>());
    /**
     * 用户主动「暂停」的任务（key = url|tag）。
     *
     * 暂停会中断下载线程，InnerDownloader.fetch 返回 false，
     * 如果不管，界面那行会被 runOneTask 结尾改写成「✗ 失败」，
     * 并且 finally 会把任务当失败回填 → 表现为「点了暂停像没反应」。
     * 这里单独记账，结尾与回填时都跳过。
     */
    final java.util.Set<String> dlPaused =
            java.util.Collections.synchronizedSet(new java.util.HashSet<String>());''')

# ---------- 2) 暂停按钮：登记 dlPaused ----------
rep('2-暂停登记',
    '''                            try{ InnerDownloader.cancel(t.url); }catch(Throwable te){}
                            synchronized(downloadQueue){ downloadQueue.remove(t); }''',
    '''                            try{ InnerDownloader.cancel(t.url); }catch(Throwable te){}
                            dlPaused.add(dlTaskKey(t));   // 记为「用户主动暂停」，不要当失败
                            synchronized(downloadQueue){ downloadQueue.remove(t); }''')

# ---------- 3) runOneTask 结尾：暂停的不改写状态 ----------
rep('3-结尾跳过暂停',
    '''                    h.post(new Runnable(){ public void run(){ progSet(1f, "(" + idx + "/" + total + ") 完成"); }});
                    dlRowUpdate(rowKey, ok ? 1000 : -1, ok ? "✓ 完成" : "✗ 失败");
                    return ok;''',
    '''                    h.post(new Runnable(){ public void run(){ progSet(1f, "(" + idx + "/" + total + ") 完成"); }});
                    // 用户主动暂停：不要把这行改写成「✗ 失败」，
                    // 否则暂停状态一闪就被失败覆盖，看着像暂停没生效。
                    if(dlPaused.contains(dlTaskKey(task))){
                        Log.i("DYP", "DL_ONE_PAUSED_KEEP " + task.tag);
                        dlRowUpdate(rowKey, -1, "已暂停 · 点「继续」接着下");
                    }else{
                        dlRowUpdate(rowKey, ok ? 1000 : -1, ok ? "✓ 完成" : "✗ 失败");
                    }
                    return ok;''')

# ---------- 4) finally 回填：暂停的不进 dlFailed ----------
rep('4-回填跳过暂停',
    '''                            if(dlDeleted.contains(dlTaskKey(fTask))){
                                synchronized(dlFailed){ dlFailed.remove(fTask); }
                                Log.i("DYP", "DL_SKIP_DELETED " + dlTaskKey(fTask));
                            } else if(!ok){''',
    '''                            if(dlDeleted.contains(dlTaskKey(fTask))){
                                synchronized(dlFailed){ dlFailed.remove(fTask); }
                                Log.i("DYP", "DL_SKIP_DELETED " + dlTaskKey(fTask));
                            } else if(dlPaused.contains(dlTaskKey(fTask))){
                                // 用户主动暂停：不算失败，也不进失败清单
                                synchronized(dlFailed){ dlFailed.remove(fTask); }
                                Log.i("DYP", "DL_SKIP_PAUSED " + dlTaskKey(fTask));
                            } else if(!ok){''')

# ---------- 5) 入队时清掉暂停标记（重新下载） ----------
rep('5-入队清暂停',
    '''        try{ InnerDownloader.clearCancel(url); dlDeleted.remove(dlTaskKey(t)); }catch(Throwable te){}''',
    '''        try{ InnerDownloader.clearCancel(url); dlDeleted.remove(dlTaskKey(t));
             dlPaused.remove(dlTaskKey(t)); }catch(Throwable te){}''')

io.open(P, 'w', encoding='utf-8').write(s)
print('成功:', ok)
print('失败:', miss)
print('dlPaused 引用数:', s.count('dlPaused'))