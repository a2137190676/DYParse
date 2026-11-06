# -*- coding: utf-8 -*-
"""
修「下 1 个却显示 1/2」。

根因：
runDownloadQueue 一轮跑完后，在 keep 收尾里把「失败/未完成」任务
重新 addAll 回 downloadQueue：

    synchronized(downloadQueue){
        downloadQueue.clear();
        downloadQueue.addAll(keep);     // ← 这里
    }

于是下一轮（用户再点一个下载时）队列里已经有了上次的失败任务，
dynTotal 初始 = downloadQueue.size() = 1(旧) + 1(新) = 2，
界面就显示「已完成 1/2」，用户以为多下了一个。

而且那些旧任务的直链多半已过期，自动重下必然 403，纯浪费。

修法：
失败任务只写进 dlSavedPending（下载页「继续」清单），
不再塞回 downloadQueue。队列只为「本轮真正要下的」服务。
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


old = '''                    if(keep.isEmpty()){
                        clearDlTasks();
                        Log.i("DYP", "DL_TASKS_ALL_DONE");
                    }else{
                        synchronized(downloadQueue){
                            downloadQueue.clear();
                            downloadQueue.addAll(keep);
                        }
                        saveDlTasks();
                        final int kn = keep.size();
                        Log.i("DYP", "DL_TASKS_KEEP_FAILED n=" + kn);'''

new = '''                    if(keep.isEmpty()){
                        clearDlTasks();
                        Log.i("DYP", "DL_TASKS_ALL_DONE");
                    }else{
                        // 关键：失败任务【不塞回 downloadQueue】。
                        //
                        // 以前这里 downloadQueue.addAll(keep)，导致下一轮下载时
                        // dynTotal 初始值把上次失败的任务也算进去 ——
                        // 用户下 1 个却看到「已完成 1/2」，以为多下了一个；
                        // 而且旧任务直链早就过期，自动重下还是 403。
                        //
                        // 现在只写进 dlSavedPending（下载页的「继续」清单），
                        // 由用户点「继续」才真正重新入队。
                        synchronized(downloadQueue){ downloadQueue.clear(); }
                        synchronized(dlSavedPending){
                            dlSavedPending.clear();
                            dlSavedPending.addAll(keep);
                        }
                        saveDlTasks();
                        final int kn = keep.size();
                        Log.i("DYP", "DL_TASKS_KEEP_FAILED n=" + kn + " (只列到下载页，不自动重下)");'''

rep('失败任务不回队列', old, new)

io.open(P, 'w', encoding='utf-8').write(s)
print('成功:', ok)
print('失败:', miss)