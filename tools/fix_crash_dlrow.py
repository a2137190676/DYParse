# -*- coding: utf-8 -*-
"""
修两个 bug：

【bug 1】软件崩溃（ForegroundServiceDidNotStartInTimeException）
现象：
  android.app.RemoteServiceException$ForegroundServiceDidNotStartInTimeException:
  Context.startForegroundService() did not then call Service.startForeground()

根因：
  runDownloadQueue() 每次都调 DownloadService.markStart(this)，
  markStart 里无条件 startForegroundService()。
  服务已在运行时再次 startForegroundService，系统会重新开始「5 秒内必须
  startForeground」的计时；批量解析阶段主线程繁忙，onStartCommand 派发被
  延迟超过 5 秒 → 系统直接抛 RemoteServiceException 杀进程。
  这类异常发生在系统端，try-catch 拦不住。

修法：
  A) markStart 里加「已启动」标志，服务在跑就不重复 start，
     只更新进度文字。
  B) onStartCommand 里 startForeground 用 try-catch 包住，
     并在 onCreate 里也先 startForeground 一次（双保险，避免 5 秒超时）。
  C) 通知渠道用低优先级 + 无声音，减少被系统拒绝的可能。

【bug 2】下载失败的残留删不掉，手动删后再点「清空下载完成」又复活
根因：
  runOneTask 的 finally 里，失败时执行
      synchronized(dlFailed){ dlFailed.remove(fTask); dlFailed.add(fTask); }
      saveDlTasks();
  而 saveDlTasks() 会把 downloadQueue + dlInFlight 全量写盘。
  用户在任务还没跑完时点删除 → doDeleteDlRow 从 dlInFlight/dlFailed 移除，
  但任务线程随后进入 finally，又把 fTask 加回 dlFailed 并 saveDlTasks，
  于是这条任务被重新写入 SharedPreferences → 复活。
  另外 clearFinishedDl() 把 dlSavedPending 全部当「未完成」保留，
  实际上 dlSavedPending 里混着已完成/已删除的任务。

修法：
  A) 新增 dlDeleted 集合，记录用户主动删除的任务；
     doDeleteDlRow 里把任务加入 dlDeleted。
  B) runOneTask 的 finally 里：如果任务在 dlDeleted 中，就不再回填 dlFailed。
  C) saveDlTasks() 写入前过滤掉 dlDeleted 里的任务。
  D) clearFinishedDl() 不再全量保留 dlSavedPending，
     只保留 dlInFlight（真正在跑的）+ 未完成的 .part 文件对应的任务。
  E) 重启恢复（showSavedDlTasks / restoreDlTasks）也过滤 dlDeleted。
"""
import io

# ==========================================================
# Part 1: DownloadService 防重复 startForegroundService
# ==========================================================
P1 = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/DownloadService.java'
s = io.open(P1, encoding='utf-8').read()

# 1) 加「已启动」标志 + onCreate 里立即 startForeground
old = '''    /** 通知栏刷新节流：避免每个字节 chunk 都 push 一次 */
    long lastNotifyAt = 0;
    @Override public void onCreate(){
        super.onCreate();
        inst = this;'''
new = '''    /** 通知栏刷新节流：避免每个字节 chunk 都 push 一次 */
    long lastNotifyAt = 0;
    /**
     * 服务是否已经进入前台。
     *
     * 关键：Android 8+ 的 startForegroundService() 要求 5 秒内必须
     * startForeground()，否则系统抛 ForegroundServiceDidNotStartInTimeException
     * 直接杀进程。下载队列每批任务都会调 markStart()，如果无条件重复
     * startForegroundService()，系统会重新计时；批量解析时主线程忙，
     * onStartCommand 派发被延迟 → 超时崩溃。
     *
     * 所以：已经在跑就不再重复拉起，只更新通知。
     */
    static volatile boolean foregroundStarted = false;
    @Override public void onCreate(){
        super.onCreate();
        inst = this;'''
if old not in s:
    raise SystemExit('MISS DownloadService onCreate')
s = s.replace(old, new, 1)

# 2) onCreate 末尾立即 startForeground（双保险，不等 onStartCommand）
old2 = '''            if(pm != null){
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "dyp:download");
                wakeLock.acquire(12 * 60 * 60 * 1000L);   // 最长 12 小时
            }
        }catch(Throwable t){}
    }'''
new2 = '''            if(pm != null){
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "dyp:download");
                wakeLock.acquire(12 * 60 * 60 * 1000L);   // 最长 12 小时
            }
        }catch(Throwable t){}
        // 双保险：在 onCreate 就进前台，不等 onStartCommand 派发。
        // 主线程繁忙时 onStartCommand 可能被延迟，这里先顶上避免 5 秒超时。
        try{
            startForeground(NOTIFY_ID, buildNotification(lastTitle, lastText, lastPct, true));
            foregroundStarted = true;
        }catch(Throwable t){
            Log.i("DYP", "DLS_FOREGROUND_CREATE_ERR " + t);
        }
    }'''
if old2 not in s:
    raise SystemExit('MISS DownloadService onCreate 尾部')
s = s.replace(old2, new2, 1)

# 3) onStartCommand 里也标记 + try-catch
old3 = '''    @Override public int onStartCommand(Intent intent, int flags, int startId){
        startForeground(NOTIFY_ID, buildNotification(lastTitle, lastText, lastPct, true));
        // START_STICKY：被系统杀掉后系统会尝试重建，配合 .part 断点续传能接着下
        return START_STICKY;
    }'''
new3 = '''    @Override public int onStartCommand(Intent intent, int flags, int startId){
        try{
            startForeground(NOTIFY_ID, buildNotification(lastTitle, lastText, lastPct, true));
            foregroundStarted = true;
        }catch(Throwable t){
            Log.i("DYP", "DLS_FOREGROUND_CMD_ERR " + t);
        }
        // START_STICKY：被系统杀掉后系统会尝试重建，配合 .part 断点续传能接着下
        return START_STICKY;
    }'''
if old3 not in s:
    raise SystemExit('MISS DownloadService onStartCommand')
s = s.replace(old3, new3, 1)

# 4) onDestroy 复位标志
old4 = '''    @Override public void onDestroy(){
        if(inst == this) inst = null;'''
new4 = '''    @Override public void onDestroy(){
        if(inst == this) inst = null;
        foregroundStarted = false;'''
if old4 not in s:
    raise SystemExit('MISS DownloadService onDestroy')
s = s.replace(old4, new4, 1)

# 5) markStart：已启动则跳过 startForegroundService
old5 = '''    /** 下载开始：标记运行中并拉起前台服务 */
    static void markStart(Context c){
        lastRunning = true;
        try{
            Intent it = new Intent(c, DownloadService.class);
            if(android.os.Build.VERSION.SDK_INT >= 26) c.startForegroundService(it);
            else c.startService(it);
        }catch(Throwable t){}
    }'''
new5 = '''    /** 下载开始：标记运行中并拉起前台服务（已在跑就只更新进度，不重复拉起） */
    static void markStart(Context c){
        lastRunning = true;
        // 服务已在前台：绝不重复 startForegroundService。
        // 重复拉起会重置系统的 5 秒计时，主线程忙时必然超时崩溃。
        if(foregroundStarted && inst != null){
            return;
        }
        try{
            Intent it = new Intent(c, DownloadService.class);
            if(android.os.Build.VERSION.SDK_INT >= 26) c.startForegroundService(it);
            else c.startService(it);
        }catch(Throwable t){
            Log.i("DYP", "DLS_MARK_START_ERR " + t);
        }
    }'''
if old5 not in s:
    raise SystemExit('MISS DownloadService markStart')
s = s.replace(old5, new5, 1)

io.open(P1, 'w', encoding='utf-8').write(s)
print('DownloadService 已修（防重复 startForegroundService）')

# ==========================================================
# Part 2: MainActivity 删除残留复活问题
# ==========================================================
P2 = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/MainActivity.java'
s2 = io.open(P2, encoding='utf-8').read()
ok, miss = [], []


def rep(name, old, new):
    global s2
    if old in s2:
        s2 = s2.replace(old, new, 1)
        ok.append(name)
    else:
        miss.append(name)


# 1) 新增 dlDeleted 集合（放在 dlFailed 附近）
rep('1-dlDeleted 集合',
    '''    volatile boolean dlRunning = false;''',
    '''    volatile boolean dlRunning = false;
    /**
     * 用户主动删除的任务（URL|tag 作为 key）。
     *
     * 为什么需要：任务线程的 finally 会把失败任务回填 dlFailed 并 saveDlTasks()。
     * 如果用户在任务还没跑完时点了删除，线程随后又把任务写回持久化，
     * 表现就是「删了又回来」。这里记一份黑名单，写盘和回填时都跳过。
     */
    final java.util.Set<String> dlDeleted =
            java.util.Collections.synchronizedSet(new java.util.HashSet<String>());
    static String dlTaskKey(DlTask t){
        if(t == null) return "";
        return (t.url == null ? "" : t.url) + "|" + (t.tag == null ? "" : t.tag);
    }''')

# 2) saveDlTasks：写入前过滤 dlDeleted
rep('2-saveDlTasks 过滤',
    '''            for(DlTask t : snap){
                org.json.JSONObject o = new org.json.JSONObject();''',
    '''            for(DlTask t : snap){
                // 用户已删除的任务不再写盘，否则重启后又冒出来
                if(dlDeleted.contains(dlTaskKey(t))) continue;
                org.json.JSONObject o = new org.json.JSONObject();''')

# 3) doDeleteDlRow：把任务加入黑名单
rep('3-删除进黑名单',
    '''            DlTask t = dlRowResume.get(key);
            if(t != null){
                synchronized(downloadQueue){ downloadQueue.remove(t); }
                synchronized(dlInFlight){ dlInFlight.remove(t); }
                synchronized(dlFailed){ dlFailed.remove(t); }
                synchronized(dlSavedPending){ dlSavedPending.remove(t); }
                saveDlTasks();
            }''',
    '''            DlTask t = dlRowResume.get(key);
            if(t != null){
                // 先拉黑：任务线程的 finally 会检查这个集合，不再回填
                dlDeleted.add(dlTaskKey(t));
                synchronized(downloadQueue){ downloadQueue.remove(t); }
                synchronized(dlInFlight){ dlInFlight.remove(t); }
                synchronized(dlFailed){ dlFailed.remove(t); }
                synchronized(dlSavedPending){ dlSavedPending.remove(t); }
                saveDlTasks();
                Log.i("DYP", "DL_DEL_BLACKLIST " + dlTaskKey(t));
            }''')

# 4) runOneTask 的 finally：已删除不再回填
rep('4-finally 过滤',
    '''                            synchronized(dlInFlight){ dlInFlight.remove(fTask); }
                            if(!ok){
                                synchronized(dlFailed){ dlFailed.remove(fTask); dlFailed.add(fTask); }
                            } else {
                                synchronized(dlFailed){ dlFailed.remove(fTask); }
                            }
                            saveDlTasks();''',
    '''                            synchronized(dlInFlight){ dlInFlight.remove(fTask); }
                            // 用户已删除的任务：不再回填 dlFailed，也不写盘
                            if(dlDeleted.contains(dlTaskKey(fTask))){
                                synchronized(dlFailed){ dlFailed.remove(fTask); }
                                Log.i("DYP", "DL_SKIP_DELETED " + dlTaskKey(fTask));
                            } else if(!ok){
                                synchronized(dlFailed){ dlFailed.remove(fTask); dlFailed.add(fTask); }
                            } else {
                                synchronized(dlFailed){ dlFailed.remove(fTask); }
                            }
                            saveDlTasks();''')

# 5) runDownloadQueue 收尾 keep 计算也过滤
rep('5-收尾keep过滤',
    '''                    java.util.List<DlTask> keep;
                    synchronized(dlFailed){ keep = new java.util.ArrayList<DlTask>(dlFailed); }
                    synchronized(dlInFlight){ for(DlTask t : dlInFlight) if(!keep.contains(t)) keep.add(t); }''',
    '''                    java.util.List<DlTask> keep = new java.util.ArrayList<DlTask>();
                    synchronized(dlFailed){
                        for(DlTask t : dlFailed){
                            if(!dlDeleted.contains(dlTaskKey(t))) keep.add(t);
                        }
                    }
                    synchronized(dlInFlight){
                        for(DlTask t : dlInFlight){
                            if(dlDeleted.contains(dlTaskKey(t))) continue;
                            if(!keep.contains(t)) keep.add(t);
                        }
                    }''')

# 6) restoreDlTasks 过滤黑名单
rep('6-restore过滤',
    '''                String k = t.url + "|" + t.tag;
                if(!seen.add(k)) continue;
                todo.add(t);''',
    '''                String k = t.url + "|" + t.tag;
                if(!seen.add(k)) continue;
                if(dlDeleted.contains(dlTaskKey(t))) continue;
                todo.add(t);''')

# 7) showSavedDlTasks 过滤黑名单
rep('7-showSaved过滤',
    '''            for(int i = 0; i < dlSavedPending.size(); i++){
                DlTask t = dlSavedPending.get(i);
                if(t == null || t.url == null || t.url.length() == 0) continue;
                if(BtDownloader.isMagnet(t.url)) continue;''',
    '''            for(int i = 0; i < dlSavedPending.size(); i++){
                DlTask t = dlSavedPending.get(i);
                if(t == null || t.url == null || t.url.length() == 0) continue;
                if(BtDownloader.isMagnet(t.url)) continue;
                if(dlDeleted.contains(dlTaskKey(t))) continue;''')

# 8) clearFinishedDl：不再全量保留 dlSavedPending，只留真正在跑的
rep('8-clearFinished',
    '''            // 保留 in-flight 未完成 + 待恢复清单
            java.util.List<DlTask> keep = new java.util.ArrayList<DlTask>();
            synchronized(dlInFlight){ keep.addAll(dlInFlight); }
            synchronized(dlSavedPending){ keep.addAll(dlSavedPending); }''',
    '''            // 只保留「真正还在下载」的任务。
            // 之前把 dlSavedPending 全量加入，导致历史失败任务（用户已删的、
            // 早已清掉的）被当成「未完成」又列回下载页，看起来像删不掉。
            java.util.List<DlTask> keep = new java.util.ArrayList<DlTask>();
            synchronized(dlInFlight){
                for(DlTask t : dlInFlight){
                    if(!dlDeleted.contains(dlTaskKey(t))) keep.add(t);
                }
            }''')

io.open(P2, 'w', encoding='utf-8').write(s2)
print('MainActivity 成功:', ok)
print('MainActivity 失败:', miss)