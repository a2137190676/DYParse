package com.re.douyinparse;

import android.util.Log;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * 应用内下载器：不走系统 DownloadManager，边下边写盘，带进度回调。
 */
public class InnerDownloader {

    static final String TAG = "DYP";
    static final String MUA = "Mozilla/5.0 (Linux; Android 12; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36";

    /** 进度回调 */
    public interface Cb {
        void onStart(long totalBytes);
        void onProgress(long doneBytes, long totalBytes);
        void onDone(boolean ok, File out, String msg);
    }

    /**
     * 同步下载 url 到 target 文件。
     * @return 成功返回 true
     */
    public static boolean fetch(String url, File target, Cb cb){
        return fetch(url, target, cb, null, null);
    }

    /**
     * 带 TikTok 会话的下载。
     * ttSession 非空且 URL 属于 TikTok 时，走 cookie 会话 + 直连/代理分流；
     * 否则按普通下载处理（Referer 自适应）。
     */
    public static boolean fetch(String url, File target, Cb cb, Object ttSessionObj){
        return fetch(url, target, cb, ttSessionObj, null);
    }

    public static boolean fetch(String url, File target, Cb cb, Object ttSessionObj, android.content.Context ctx){
        HttpURLConnection c = null;
        InputStream in = null;
        BufferedOutputStream out = null;
        boolean ok = false;
        String msg = "";
        try{
            if(target.getParentFile() != null && !target.getParentFile().exists()){
                target.getParentFile().mkdirs();
            }

            boolean tt = ttSessionObj instanceof TtParser.Session
                      && TtParser.isTtUrl(url);
            if(tt){
                // TikTok：交给 TtParser 做分流（内部已处理 cookie 和代理切换）
                c = TtParser.openDownload(ctx, url, (TtParser.Session) ttSessionObj);
                if(c == null){
                    msg = "TikTok CDN 拒绝（代理/直连都不通）";
                    Log.i(TAG, "IDL_TT_FAIL " + url);
                    if(cb != null) cb.onDone(false, null, msg);
                    return false;
                }
            } else {
                // 内置服务器分发（自更新 APK）：走 SrvClient 的 TLS pin + HMAC
                if(SrvClient.isBuiltin(url)){
                    c = SrvClient.open(url, null);
                    SrvClient.sign(c, url);
                    c.setInstanceFollowRedirects(true);
                    c.setRequestProperty("User-Agent", MUA);
                    c.setConnectTimeout(15000);
                    c.setReadTimeout(60000);
                    c.connect();
                } else {
                    c = (HttpURLConnection) new URL(url).openConnection();
                    c.setInstanceFollowRedirects(true);
                    c.setRequestProperty("User-Agent", MUA);
                    String ref = "https://www.douyin.com/";
                    if(url.indexOf("tiktok") >= 0 || url.indexOf("tiktokcdn") >= 0) ref = "https://www.tiktok.com/";
                    else if(KsParser.isKsUrl(url)) ref = "https://www.kuaishou.com/";
                    c.setRequestProperty("Referer", ref);
                    c.setConnectTimeout(15000);
                    c.setReadTimeout(30000);
                    c.connect();
                }
            }

            int code = c.getResponseCode();
            if(code < 200 || code >= 300){
                Log.i(TAG, "IDL_HTTP " + code + " " + url);
                msg = "HTTP " + code;
                if(cb != null) cb.onDone(false, null, msg);
                return false;
            }

            long total = -1;
            try{ total = c.getContentLength(); }catch(Throwable t){}
            if(total <= 0){
                String cr = c.getHeaderField("Content-Range");
                if(cr != null && cr.contains("/")){
                    try{ total = Long.parseLong(cr.substring(cr.lastIndexOf('/') + 1).trim()); }catch(Throwable t){}
                }
            }

            in = c.getInputStream();
            out = new BufferedOutputStream(new FileOutputStream(target), 64 * 1024);

            if(cb != null) cb.onStart(total);

            byte[] buf = new byte[64 * 1024];
            int n; long got = 0; long last = 0;
            while((n = in.read(buf)) > 0){
                out.write(buf, 0, n);
                got += n;
                if(cb != null && (got - last > 128 * 1024)){
                    last = got;
                    cb.onProgress(got, total);
                }
            }
            out.flush();
            out.close(); out = null;
            in.close(); in = null;

            ok = got > 0;
            if(cb != null) cb.onProgress(got, total > 0 ? total : got);
            Log.i(TAG, "IDL ok " + got + "/" + total + " -> " + target.getName());

        }catch(Throwable t){
            Log.i(TAG, "IDL_ERR " + t);
            msg = "" + t;
            try{ if(target.exists()) target.delete(); }catch(Throwable x){}
            ok = false;
        }finally{
            try{ if(out != null) out.close(); }catch(Throwable t){}
            try{ if(in != null) in.close(); }catch(Throwable t){}
            try{ if(c != null) c.disconnect(); }catch(Throwable t){}
        }

        if(cb != null) cb.onDone(ok, ok ? target : null, msg);
        return ok;
    }

    /** 断点文件用：只取长度，用于显示 */
    public static String sizeText(long b){
        if(b < 1024) return b + "B";
        if(b < 1024L * 1024) return String.format(java.util.Locale.US, "%.0fKB", b / 1024.0);
        return String.format(java.util.Locale.US, "%.1fMB", b / 1048576.0);
    }
}