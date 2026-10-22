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
 * 下载前台服务：切后台也继续下，通知栏显示进度。
 * 下载队列仍由 MainActivity 的 downloadQueue 驱动（避免大改），
 * 本服务只负责：①持 WakeLock 防休眠 ②前台通知占位。
 */
public class DownloadService extends Service {

    static final String CHANNEL_ID = "dyparse_dl";
    static final int NOTIFY_ID = 1001;

    PowerManager.WakeLock wakeLock;

    @Override public void onCreate(){
        super.onCreate();
        try{
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID,
                    "下载", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("DYParse 下载进行中");
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
        startForeground(NOTIFY_ID, buildNotification("下载准备中..."));
        return START_STICKY;
    }

    @Override public void onDestroy(){
        try{
            if(wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        }catch(Throwable t){}
        try{ stopForeground(true); }catch(Throwable t){}
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent){ return null; }

    Notification buildNotification(String text){
        Notification.Builder b = new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle("DYParse 下载")
                .setContentText(text)
                .setOngoing(true)
                .setProgress(0, 0, true);   // 不确定进度
        return b.build();
    }
}