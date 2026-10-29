package com.re.douyinparse;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.os.PowerManager;
/**
 * 下载前台服务：切后台也继续下，通知栏显示实时进度。
 * 下载队列仍由 MainActivity 的 downloadQueue 驱动（避免大改），
 * 本服务负责：①持 WakeLock 防休眠 ②前台通知 + 进度同步。
 *
 * 进度同步方式：MainActivity 每次 progSet() 时调 DownloadService.updateProgress()，
 * 通过静态引用拿到正在运行的服务实例，直接更新通知栏（不额外起线程）。
 */
public class DownloadService extends Service {
    static final String CHANNEL_ID = "dyparse_dl";
    static final int NOTIFY_ID = 1001;
    PowerManager.WakeLock wakeLock;
    /** 当前运行实例（可能为 null，服务没起来时进度只更新界面） */
    static volatile DownloadService inst;
    /** 最近一次进度状态，服务重连时用它恢复通知 */
    static volatile String lastTitle = "下载准备中";
    static volatile String lastText = "";
    static volatile int lastPct = -1;          // -1 = 不确定进度
    static volatile boolean lastRunning = false;
    /** 通知栏刷新节流：避免每个字节 chunk 都 push 一次 */
    long lastNotifyAt = 0;

    @Override public void onCreate(){
        super.onCreate();
        inst = this;
        try{
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID,
                    "下载", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("DYParse 下载进行中");
            ch.setShowBadge(false);
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if(nm != null) nm.createNotificationChannel(ch);
        }catch(Throwable t){}
        try{
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if(pm != null){
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "dyp:download");
                wakeLock.acquire(12 * 60 * 60 * 1000L);   // 最长 12 小时
            }
        }catch(Throwable t){}
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId){
        startForeground(NOTIFY_ID, buildNotification(lastTitle, lastText, lastPct, true));
        // START_STICKY：被系统杀掉后系统会尝试重建，配合 .part 断点续传能接着下
        return START_STICKY;
    }

    @Override public void onDestroy(){
        if(inst == this) inst = null;
        try{
            if(wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        }catch(Throwable t){}
        try{ stopForeground(true); }catch(Throwable t){}
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent){ return null; }

    // ===== 供 MainActivity 调用的进度同步接口 =====

    /** 更新通知栏进度。pct<0 表示不确定进度（转圈） */
    static void updateProgress(String title, String text, int pct){
        lastTitle = title != null ? title : lastTitle;
        lastText = text != null ? text : lastText;
        lastPct = pct;
        lastRunning = true;
        DownloadService s = inst;
        if(s == null) return;
        s.pushNotify(false);
    }

    /** 下载开始：标记运行中并拉起前台服务 */
    static void markStart(Context c){
        lastRunning = true;
        try{
            Intent it = new Intent(c, DownloadService.class);
            if(android.os.Build.VERSION.SDK_INT >= 26) c.startForegroundService(it);
            else c.startService(it);
        }catch(Throwable t){}
    }

    /** 下载全部结束：收起前台通知 */
    static void markStop(Context c, String finalText){
        lastRunning = false;
        DownloadService s = inst;
        if(s != null){
            try{
                if(finalText != null){
                    lastText = finalText;
                    lastPct = 100;
                    s.pushNotify(true);
                }
                s.stopForeground(true);
            }catch(Throwable t){}
            try{ s.stopSelf(); }catch(Throwable t){}
        }
    }

    /** 内部：真正 push 通知（带节流，避免刷屏） */
    void pushNotify(boolean force){
        try{
            long now = System.currentTimeMillis();
            if(!force && now - lastNotifyAt < 400) return;   // 400ms 节流
            lastNotifyAt = now;
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if(nm == null) return;
            nm.notify(NOTIFY_ID, buildNotification(lastTitle, lastText, lastPct, !force));
        }catch(Throwable t){}
    }

    Notification buildNotification(String title, String text, int pct, boolean ongoing){
        // 注意：new Notification.Builder(ctx, channelId) 双参构造是 API 26+ 才有的，
        // Android 7/8（API 24/25/26 以下）会抛 NoSuchMethodError 导致前台服务起不来、下载失败。
        // 所以按版本分别构造。
        Notification.Builder b;
        if(android.os.Build.VERSION.SDK_INT >= 26){
            b = new Notification.Builder(this, CHANNEL_ID);
        }else{
            b = new Notification.Builder(this);
        }
        b.setSmallIcon(android.R.drawable.stat_sys_download)
         .setContentTitle(title == null ? "DYParse 下载" : title)
         .setContentText(text == null ? "" : text)
         .setOngoing(ongoing);
        if(pct >= 0){
            b.setProgress(100, Math.max(0, Math.min(100, pct)), false);
        }else{
            b.setProgress(0, 0, true);   // 不确定进度
        }
        if(android.os.Build.VERSION.SDK_INT >= 16){
            return b.build();
        }
        return b.getNotification();
    }
}