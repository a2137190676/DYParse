package com.re.douyinparse;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;

/**
 * 分区存储（Scoped Storage）兼容写入。
 *
 * 背景：
 *  - Android 9 及以下：直接 File 写 /sdcard/Download/... 就行，只要有 WRITE_EXTERNAL_STORAGE。
 *  - Android 10（API 29）：默认强制分区存储。靠 Manifest 的
 *    android:requestLegacyExternalStorage="true" 才能继续用 File 路径直写公共目录。
 *  - Android 11+（API 30+）：requestLegacyExternalStorage 失效。targetSdk=30 时
 *    Download 公共目录用 File 路径写通常还能成（系统兼容层），但部分 ROM 会拦。
 *
 * 所以策略是「先试 File 直写，失败再走 MediaStore 兜底」：
 *  1. 调用方仍然按老方式算 File 路径，先 mkdirs + 写 .part
 *  2. 如果 mkdirs 或落盘失败 → 走 {@link #saveToDownloads} 落进公共 Downloads/DYParse
 *  3. 返回最终的 Uri（可能是 null，表示 MediaStore 也失败）
 */
public class ScopedStore {

    private static final String TAG = "ScopedStore";

    /** 当前设备是否需要走 MediaStore 兜底（API 29+ 都可能需要，实际看写入结果） */
    public static boolean needScoped(){
        return Build.VERSION.SDK_INT >= 29;
    }

    /**
     * 把本地临时文件复制到公共 Downloads/DYParse/<subDir>/<name>。
     *
     * @param ctx    上下文
     * @param src    已经下载好的本地文件（可能是 .part 或 app 私有目录里的临时文件）
     * @param subDir 子目录（如 "视频" / "图片" / "音频"），可为 null
     * @param name   落盘文件名（含扩展名）
     * @return 成功返回 MediaStore Uri，失败返回 null
     */
    public static Uri saveToDownloads(Context ctx, File src, String subDir, String name){
        if(ctx == null || src == null || !src.exists()) return null;
        OutputStream os = null;
        try{
            String rel = Environment.DIRECTORY_DOWNLOADS + "/DYParse";
            if(subDir != null && subDir.length() > 0) rel = rel + "/" + subDir;

            ContentResolver cr = ctx.getContentResolver();
            ContentValues v = new ContentValues();
            v.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
            v.put(MediaStore.MediaColumns.MIME_TYPE, mimeOf(name));
            if(Build.VERSION.SDK_INT >= 29){
                v.put(MediaStore.MediaColumns.RELATIVE_PATH, rel);
                v.put(MediaStore.MediaColumns.IS_PENDING, 1);
            }else{
                // API 28 及以下用 DATA 列（虽然只有 API29+ 才需要兜底，这里留着保险）
                File dir = new File(Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DOWNLOADS), "DYParse"
                        + (subDir != null && subDir.length() > 0 ? "/" + subDir : ""));
                if(!dir.exists()) dir.mkdirs();
                v.put(MediaStore.MediaColumns.DATA, new File(dir, name).getAbsolutePath());
            }

            Uri collection = Build.VERSION.SDK_INT >= 29
                    ? MediaStore.Downloads.EXTERNAL_CONTENT_URI
                    : MediaStore.Files.getContentUri("external");
            Uri uri = cr.insert(collection, v);
            if(uri == null){
                android.util.Log.i(TAG, "SCOPED_INSERT_NULL name=" + name);
                return null;
            }

            os = cr.openOutputStream(uri);
            if(os == null){
                cr.delete(uri, null, null);
                android.util.Log.i(TAG, "SCOPED_OS_NULL name=" + name);
                return null;
            }
            FileInputStream in = new FileInputStream(src);
            byte[] buf = new byte[64 * 1024];
            int n;
            long total = 0;
            while((n = in.read(buf)) > 0){
                os.write(buf, 0, n);
                total += n;
            }
            in.close();
            os.flush();
            os.close();
            os = null;

            if(Build.VERSION.SDK_INT >= 29){
                ContentValues done = new ContentValues();
                done.put(MediaStore.MediaColumns.IS_PENDING, 0);
                cr.update(uri, done, null, null);
            }
            android.util.Log.i(TAG, "SCOPED_SAVE_OK name=" + name + " bytes=" + total + " uri=" + uri);
            return uri;
        }catch(Throwable t){
            android.util.Log.i(TAG, "SCOPED_SAVE_ERR name=" + name + " err=" + t);
            try{ if(os != null) os.close(); }catch(Throwable t2){}
            return null;
        }
    }

    /** 按扩展名给 MIME（MediaStore 需要，否则相册/播放器识别不到） */
    public static String mimeOf(String name){
        if(name == null) return "application/octet-stream";
        String low = name.toLowerCase();
        if(low.endsWith(".mp4")) return "video/mp4";
        if(low.endsWith(".m4a")) return "audio/mp4";
        if(low.endsWith(".mp3")) return "audio/mpeg";
        if(low.endsWith(".aac")) return "audio/aac";
        if(low.endsWith(".jpg") || low.endsWith(".jpeg")) return "image/jpeg";
        if(low.endsWith(".png")) return "image/png";
        if(low.endsWith(".webp")) return "image/webp";
        if(low.endsWith(".gif")) return "image/gif";
        if(low.endsWith(".apk")) return "application/vnd.android.package-archive";
        return "application/octet-stream";
    }

    /** 判断目标目录能否直接写入（试创建 + 试写空文件） */
    public static boolean canWriteDir(File dir){
        try{
            if(dir == null) return false;
            if(!dir.exists() && !dir.mkdirs()) return false;
            File probe = new File(dir, ".dyp_probe_" + System.currentTimeMillis());
            boolean ok = probe.createNewFile();
            if(ok) probe.delete();
            return ok;
        }catch(Throwable t){
            return false;
        }
    }
}
