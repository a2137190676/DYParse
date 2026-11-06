# -*- coding: utf-8 -*-
"""只改 MainActivity：下载残留删不掉 / 删了又复活。"""
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


# 1) 新增 dlDeleted 集合
rep('1-dlDeleted',
    '    volatile boolean dlRunning = false;',
    '    volatile boolean dlRunning = false;\n'
    '    /**\n'
    '     * 用户主动删除的任务黑名单（key = url|tag）。\n'
    '     *\n'
    '     * 任务线程的 finally 会把失败任务回填 dlFailed 并 saveDlTasks()。\n'
    '     * 用户点删除后线程又写回持久化，表现就是「删了又回来」。\n'
    '     * 这里记一份黑名单，写盘、回填、恢复时都跳过。\n'
    '     */\n'
    '    final java.util.Set<String> dlDeleted =\n'
    '            java.util.Collections.synchronizedSet(new java.util.HashSet<String>());\n'
    '    static String dlTaskKey(DlTask t){\n'
    '        if(t == null) return "";\n'
    '        return (t.url == null ? "" : t.url) + "|" + (t.tag == null ? "" : t.tag);\n'
    '    }')

# 2) saveDlTasks 写入前过滤
rep('2-save过滤',
    '            for(DlTask t : snap){\n                org.json.JSONObject o = new org.json.JSONObject();',
    '            for(DlTask t : snap){\n'
    '                // 用户已删除的任务不再写盘，否则重启后又冒出来\n'
    '                if(dlDeleted.contains(dlTaskKey(t))) continue;\n'
    '                org.json.JSONObject o = new org.json.JSONObject();')

# 3) doDeleteDlRow 拉黑
rep('3-删除拉黑',
    '            DlTask t = dlRowResume.get(key);\n'
    '            if(t != null){\n'
    '                synchronized(downloadQueue){ downloadQueue.remove(t); }\n'
    '                synchronized(dlInFlight){ dlInFlight.remove(t); }\n'
    '                synchronized(dlFailed){ dlFailed.remove(t); }\n'
    '                synchronized(dlSavedPending){ dlSavedPending.remove(t); }\n'
    '                saveDlTasks();\n'
    '            }',
    '            DlTask t = dlRowResume.get(key);\n'
    '            if(t != null){\n'
    '                dlDeleted.add(dlTaskKey(t));\n'
    '                synchronized(downloadQueue){ downloadQueue.remove(t); }\n'
    '                synchronized(dlInFlight){ dlInFlight.remove(t); }\n'
    '                synchronized(dlFailed){ dlFailed.remove(t); }\n'
    '                synchronized(dlSavedPending){ dlSavedPending.remove(t); }\n'
    '                saveDlTasks();\n'
    '                Log.i("DYP", "DL_DEL_BLACKLIST " + dlTaskKey(t));\n'
    '            }')

# 4) finally 过滤
rep('4-finally过滤',
    '                            synchronized(dlInFlight){ dlInFlight.remove(fTask); }\n'
    '                            if(!ok){\n'
    '                                synchronized(dlFailed){ dlFailed.remove(fTask); dlFailed.add(fTask); }\n'
    '                            } else {\n'
    '                                synchronized(dlFailed){ dlFailed.remove(fTask); }\n'
    '                            }\n'
    '                            saveDlTasks();',
    '                            synchronized(dlInFlight){ dlInFlight.remove(fTask); }\n'
    '                            // 用户已删除：不回填、不写盘\n'
    '                            if(dlDeleted.contains(dlTaskKey(fTask))){\n'
    '                                synchronized(dlFailed){ dlFailed.remove(fTask); }\n'
    '                                Log.i("DYP", "DL_SKIP_DELETED " + dlTaskKey(fTask));\n'
    '                            } else if(!ok){\n'
    '                                synchronized(dlFailed){ dlFailed.remove(fTask); dlFailed.add(fTask); }\n'
    '                            } else {\n'
    '                                synchronized(dlFailed){ dlFailed.remove(fTask); }\n'
    '                            }\n'
    '                            saveDlTasks();')

# 5) 收尾 keep 过滤
rep('5-收尾keep',
    '                    java.util.List<DlTask> keep;\n'
    '                    synchronized(dlFailed){ keep = new java.util.ArrayList<DlTask>(dlFailed); }\n'
    '                    synchronized(dlInFlight){ for(DlTask t : dlInFlight) if(!keep.contains(t)) keep.add(t); }',
    '                    java.util.List<DlTask> keep = new java.util.ArrayList<DlTask>();\n'
    '                    synchronized(dlFailed){\n'
    '                        for(DlTask t : dlFailed){\n'
    '                            if(!dlDeleted.contains(dlTaskKey(t))) keep.add(t);\n'
    '                        }\n'
    '                    }\n'
    '                    synchronized(dlInFlight){\n'
    '                        for(DlTask t : dlInFlight){\n'
    '                            if(dlDeleted.contains(dlTaskKey(t))) continue;\n'
    '                            if(!keep.contains(t)) keep.add(t);\n'
    '                        }\n'
    '                    }')

# 6) restoreDlTasks 过滤
rep('6-restore过滤',
    '                String k = t.url + "|" + t.tag;\n                if(!seen.add(k)) continue;\n                todo.add(t);',
    '                String k = t.url + "|" + t.tag;\n'
    '                if(!seen.add(k)) continue;\n'
    '                if(dlDeleted.contains(dlTaskKey(t))) continue;\n'
    '                todo.add(t);')

# 7) showSavedDlTasks 过滤
rep('7-showSaved过滤',
    '                if(t == null || t.url == null || t.url.length() == 0) continue;\n'
    '                if(BtDownloader.isMagnet(t.url)) continue;',
    '                if(t == null || t.url == null || t.url.length() == 0) continue;\n'
    '                if(BtDownloader.isMagnet(t.url)) continue;\n'
    '                if(dlDeleted.contains(dlTaskKey(t))) continue;')

# 8) clearFinishedDl：只保留 in-flight
rep('8-clearFinished',
    '            // 保留 in-flight 未完成 + 待恢复清单\n'
    '            java.util.List<DlTask> keep = new java.util.ArrayList<DlTask>();\n'
    '            synchronized(dlInFlight){ keep.addAll(dlInFlight); }\n'
    '            synchronized(dlSavedPending){ keep.addAll(dlSavedPending); }',
    '            // 只保留真正还在下载的任务。\n'
    '            // 之前把 dlSavedPending 全量当「未完成」保留，导致历史失败任务\n'
    '            // （用户已删的、早已清掉的）又列回下载页，看起来像删不掉。\n'
    '            java.util.List<DlTask> keep = new java.util.ArrayList<DlTask>();\n'
    '            synchronized(dlInFlight){\n'
    '                for(DlTask t : dlInFlight){\n'
    '                    if(!dlDeleted.contains(dlTaskKey(t))) keep.add(t);\n'
    '                }\n'
    '            }')

io.open(P, 'w', encoding='utf-8').write(s)
print('成功:', ok)
print('失败:', miss)
print('dlDeleted:', s.count('dlDeleted'))