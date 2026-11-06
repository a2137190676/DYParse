# -*- coding: utf-8 -*-
"""
修两个问题：
A) 恢复未完成任务时不再自动重新下载（只列到下载页，等用户点「继续」）
   根因：restoreDlTasks 里直接 runDownloadQueue()，
        用户新下 1 个 → 上次失败的也一起进队列 → 显示 1/2。
B) 行内「暂停」要真正中断下载（之前只从队列移除，IO 线程还在读）
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


# ============ A) 恢复任务不自动下载 ============
old_a = (
    '            Log.i("DYP", "DL_TASKS_RESTORE n=" + todo.size());\n'
    '            s("发现 " + todo.size() + " 个未完成的下载，继续下载…");\n'
    'showHintToast("继续上次 " + todo.size() + " 个下载任务");\n'
    '            if(!dlRunning){\n'
    '                dlRunning = true;\n'
    '                runDownloadQueue();\n'
    '            }'
)

new_a = (
    '            Log.i("DYP", "DL_TASKS_RESTORE n=" + todo.size());\n'
    '            // 关键：恢复的未完成任务【不自动重新下载】。\n'
    '            // 之前这里直接 runDownloadQueue()，导致用户新下 1 个视频时，\n'
    '            // 上次失败的任务被一起塞进队列 → 下载页显示「1/2」，\n'
    '            // 用户以为多下了一个；而且失败的直链多半已过期，重下还是 403。\n'
    '            // 现在只渲染到下载页，由用户手动点「继续」再下。\n'
    '            synchronized(dlSavedPending){\n'
    '                dlSavedPending.clear();\n'
    '                for(DlTask t : todo) dlSavedPending.add(t);\n'
    '            }\n'
    '            s("发现 " + todo.size() + " 个未完成任务，可在「下载」页点「继续」重下");\n'
    '            showHintToast("上次有 " + todo.size() + " 个未完成任务，已放到下载页");\n'
    '            dlRefreshHead();\n'
    '            showSavedDlTasks();'
)
rep('A-恢复不自动跑', old_a, new_a)

# ============ B) 暂停真正中断 ============
old_b = (
    '                        DlTask t = dlRowResume.get(key);\n'
    '                        if(t != null){\n'
    '                            // 普通下载：从队列/在飞列表移除，放进待恢复清单\n'
    '                            synchronized(downloadQueue){ downloadQueue.remove(t); }\n'
    '                            synchronized(dlInFlight){ dlInFlight.remove(t); }\n'
    '                            synchronized(dlFailed){ dlFailed.remove(t); dlFailed.add(t); }\n'
    '                            saveDlTasks();\n'
    '                            Log.i("DYP", "DL_ROW_PAUSE " + t.tag);'
)

new_b = (
    '                        DlTask t = dlRowResume.get(key);\n'
    '                        if(t != null){\n'
    '                            // 暂停：必须同时做三件事，否则「暂停」按钮无效\n'
    '                            // 1) 置取消标记 → InnerDownloader 读循环下一块就退出\n'
    '                            // 2) 从队列/在飞列表移除 → 调度器不再消费它\n'
    '                            // 3) 放进待恢复清单 → 用户可点「继续」接着下\n'
    '                            try{ InnerDownloader.cancel(t.url); }catch(Throwable te){}\n'
    '                            synchronized(downloadQueue){ downloadQueue.remove(t); }\n'
    '                            synchronized(dlInFlight){ dlInFlight.remove(t); }\n'
    '                            synchronized(dlFailed){ dlFailed.remove(t); dlFailed.add(t); }\n'
    '                            synchronized(dlSavedPending){ dlSavedPending.add(t); }\n'
    '                            saveDlTasks();\n'
    '                            Log.i("DYP", "DL_ROW_PAUSE " + t.tag + " url=" + t.url);'
)
rep('B-暂停中断', old_b, new_b)

io.open(P, 'w', encoding='utf-8').write(s)
print('成功:', ok)
print('失败:', miss)
print('dlSavedPending 引用数:', s.count('dlSavedPending'))
