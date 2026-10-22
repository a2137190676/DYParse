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
        // 断点续传 + 重试：大文件（YouTube 4K 等）经服务器中转容易中途断。
        // 保留 .part 文件，重连时带 Range 从断点继续，最多 3 次。
        File part = new File(target.getAbsolutePath() + ".part");
        for(int attempt = 1; attempt <= 3; attempt++){
            boolean done = fetchOnce(url, target, part, cb, ttSessionObj, ctx);
            if(done) return true;
            Log.i(TAG, "IDL_RETRY attempt=" + attempt + " keep=" + (part.exists() ? part.length() : 0));
        }
        try{ if(part.exists()) part.delete(); }catch(Throwable t){}
        return false;
    }

    private static boolean fetchOnce(String url, File target, File part, Cb cb,
                                     Object ttSessionObj, android.content.Context ctx){
        HttpURLConnection c = null;
        InputStream in = null;
        BufferedOutputStream out = null;
        boolean ok = false;
        String msg = "";
        long resumeFrom = 0;
        if(part.exists()) resumeFrom = part.length();
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
                    // 普通下载：支持代理（X / TikTok / YouTube 的 CDN 在国内常被 DNS 污染）
                    java.net.Proxy px = null;
                    try{
                        if(url.indexOf("googlevideo.com") >= 0){
                            // YouTube 直链：用户代理优先（direct 模式）；这里不看「仅 TikTok」开关
                            px = ProxyPolicy.userProxy(ctx);
                        } else if(url.indexOf("/api/x/dl") >= 0 || url.indexOf("/api/yt/file") >= 0){
                            // 服务器中转：强制直连，不套用户代理
                            px = null;
                        } else {
                            px = MainActivity.staticProxyFor(ctx, url);
                        }
                    }catch(Throwable t){}
                    c = (HttpURLConnection) new URL(url).openConnection(
                            px != null ? px : java.net.Proxy.NO_PROXY);
                    c.setInstanceFollowRedirects(true);
                    c.setRequestProperty("User-Agent", MUA);
                    String ref = "https://www.douyin.com/";
                    if(url.indexOf("tiktok") >= 0 || url.indexOf("tiktokcdn") >= 0) ref = "https://www.tiktok.com/";
                    else if(KsParser.isKsUrl(url)) ref = "https://www.kuaishou.com/";
                    else if(MainActivity.isXDomain(url)) ref = "https://x.com/";
                    else if(url.indexOf("googlevideo.com") >= 0) ref = "https://www.youtube.com/";
                    c.setRequestProperty("Referer", ref);
                    // 断点续传：带 Range 从 .part 大小继续
                    if(resumeFrom > 0) c.setRequestProperty("Range", "bytes=" + resumeFrom + "-");
                    // X/YT 中转地址（服务器在回源大视频，可能 1-2 分钟才出首字节）
                    if(url.indexOf("/api/x/dl") >= 0 || url.indexOf("/api/yt/file") >= 0){
                        c.setConnectTimeout(30000);
                        c.setReadTimeout(300000);
                    } else {
                        c.setConnectTimeout(15000);
                        c.setReadTimeout(30000);
                    }
                    c.connect();
                }
            }

            int code = c.getResponseCode();
            // 206 = 续传成功；200 = 服务器忽略 Range，从头下
            if(code != 200 && code != 206){
                Log.i(TAG, "IDL_HTTP " + code + " " + url);
                msg = "HTTP " + code;
                if(cb != null) cb.onDone(false, null, msg);
                return false;
            }
            if(code == 200 && resumeFrom > 0){
                // 服务器不支持续传，从头来
                resumeFrom = 0;
                try{ if(part.exists()) part.delete(); }catch(Throwable t){}
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
            // 写 .part：有断点就追加，否则覆盖
            out = new BufferedOutputStream(new FileOutputStream(part, resumeFrom > 0), 64 * 1024);

            if(cb != null) cb.onStart(total > 0 ? total : -1);

            byte[] buf = new byte[64 * 1024];
            int n; long got = resumeFrom; long last = resumeFrom;
            while((n = in.read(buf)) > 0){
                out.write(buf, 0, n);
                got += n;
                if(cb != null && (got - last > 128 * 1024)){
                    last = got;
                    cb.onProgress(got, total > 0 ? total : -1);
                }
            }
            out.flush();
            out.close(); out = null;
            in.close(); in = null;

            ok = got > 0;
            if(cb != null) cb.onProgress(got, total > 0 ? total : got);
            Log.i(TAG, "IDL ok " + got + "/" + total + " -> " + target.getName());
            // 完成：.part 改名成正式文件
            try{
                if(target.exists()) target.delete();
                if(part.renameTo(target)){ part.delete(); }
            }catch(Throwable t){
                Log.i(TAG, "IDL_RENAME_ERR " + t);
            }

        }catch(Throwable t){
            Log.i(TAG, "IDL_ERR " + t);
            msg = "" + t;
            // 保留 .part：下次重试带 Range 续传，不再从头下
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

    /** 清理残留 .part 文件（下载队列结束或 App 启动时调用） */
    public static void cleanPartFiles(File dir){
        if(dir == null || !dir.isDirectory()) return;
        try{
            File[] fs = dir.listFiles();
            if(fs == null) return;
            for(File f : fs){
                if(f.isDirectory()) cleanPartFiles(f);
                else if(f.getName().endsWith(".part")) f.delete();
            }
        }catch(Throwable t){}
    }
}