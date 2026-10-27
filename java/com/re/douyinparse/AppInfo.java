package com.re.douyinparse;

import android.content.Context;
import android.os.Build;

/** 版本/设备信息，给日志头部和「关于」用 */
public class AppInfo {

    static Context ctx;

    public static void init(Context c){
        if(c != null && ctx == null) ctx = c.getApplicationContext();
    }

    /** "1.3.7.1 (build 103701)" */
    public static String versionLine(){
        try{
            android.content.pm.PackageInfo pi = ctx.getPackageManager()
                    .getPackageInfo(ctx.getPackageName(), 0);
            String vn = pi.versionName;
            int vc = pi.versionCode;
            return vn + " (build " + vc + ")";
        }catch(Throwable t){
            return "未知";
        }
    }

    /** "PJX110 / Android 16 / arm64-v8a" */
    public static String deviceLine(){
        try{
            String abi = "";
            try{
                if(Build.SUPPORTED_ABIS != null && Build.SUPPORTED_ABIS.length > 0)
                    abi = Build.SUPPORTED_ABIS[0];
            }catch(Throwable t){}
            return Build.MODEL + " / Android " + Build.VERSION.RELEASE
                    + " (API " + Build.VERSION.SDK_INT + ")"
                    + (abi.length() > 0 ? " / " + abi : "");
        }catch(Throwable t){
            return "未知设备";
        }
    }

    /** 存储权限是否已授予（Android 11+ 分区下载目录只需旧权限的放宽判断） */
    public static boolean hasStoragePerm(Context c){
        try{
            if(c == null) return false;
            if(Build.VERSION.SDK_INT >= 30){
                // Android 11+ 写 Download 公共目录通常不需要运行时权限，但有些 ROM 仍会拦
                return true;
            }
            return c.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    == android.content.pm.PackageManager.PERMISSION_GRANTED;
        }catch(Throwable t){
            return false;
        }
    }
}