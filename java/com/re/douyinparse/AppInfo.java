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
            int sdk = Build.VERSION.SDK_INT;
            if(sdk >= 30){
                // Android 11+：分区存储，写 Download 公共目录走 File 路径在 targetSdk=30
                // 上仍可用（系统对 targetSdk<30 的设备有兼容），但部分 ROM 会拦，
                // 这里额外检查 MANAGE_EXTERNAL_STORAGE（有就给，没有也先放行，写入时再兜底 MediaStore）
                return true;
            }
            // Android 10 及以下：必须要有 WRITE_EXTERNAL_STORAGE
            // （Android 10 靠 Manifest 里的 requestLegacyExternalStorage 才能直写公共目录）
            boolean write = c.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    == android.content.pm.PackageManager.PERMISSION_GRANTED;
            if(!write) return false;
            // Android 7.x 及以下：不需要单独的读权限（WRITE 隐含 READ）
            // Android 8.0+（API 26+）读权限是独立的，部分 ROM 会单独拦 READ
            if(sdk <= 25) return true;
            boolean read = c.checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                    == android.content.pm.PackageManager.PERMISSION_GRANTED;
            // Android 10 写公共目录只要 WRITE 就够；读独立但下载不受影响，这里不看 READ
            return sdk >= 29 ? true : read;
        }catch(Throwable t){
            return false;
        }
    }

    /**
     * 权限是否被「永久拒绝」（用户勾了不再询问，或拒绝过两次）。
     * Android 7 上被拒后系统不会再弹窗，必须引导用户去设置页手动开。
     */
    public static boolean storagePermBlocked(android.app.Activity a){
        try{
            if(a == null) return false;
            int sdk = Build.VERSION.SDK_INT;
            if(sdk >= 30) return false;   // 30+ 不需要该权限
            if(sdk < 23) return false;    // Android 6 以下是安装时授权，不存在运行时拒绝
            boolean granted = a.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    == android.content.pm.PackageManager.PERMISSION_GRANTED;
            if(granted) return false;
            // shouldShowRequestPermissionRationale 为 false 且已申请过 → 视为永久拒绝
            boolean should = a.shouldShowRequestPermissionRationale(
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE);
            boolean asked = a.getSharedPreferences("dyp_pref", Context.MODE_PRIVATE)
                    .getBoolean("perm_storage_asked", false);
            return asked && !should;
        }catch(Throwable t){
            return false;
        }
    }

    /** Android 11+ 是否已授予「所有文件访问」权限（部分 ROM 写公共目录需要） */
    public static boolean hasAllFilesAccess(Context c){
        try{
            if(c == null || Build.VERSION.SDK_INT < 30) return false;
            return android.os.Environment.isExternalStorageManager();
        }catch(Throwable t){
            return false;
        }
    }
}