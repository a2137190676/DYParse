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
        // 保留 .part 文件，重连时带 Range 从断点继续。
        // 2026-10 加强：后台被杀/网络抖动导致断流的场景，原来只 3 次且无退避，
        // 现改为最多 6 次 + 递增等待（1/2/3/5/8 秒），配合 .part 能接着下。
        File part = new File(target.getAbsolutePath() + ".part");
        int maxAttempt = 6;
        for(int attempt = 1; attempt <= maxAttempt; attempt++){
            boolean done = fetchOnce(url, target, part, cb, ttSessionObj, ctx);
            if(done) return true;
            long kept = part.exists() ? part.length() : 0;
            Log.i(TAG, "IDL_RETRY attempt=" + attempt + "/" + maxAttempt + " keep=" + kept);
            if(cb != null && attempt < maxAttempt){
                try{ cb.onProgress(kept, -1); }catch(Throwable t){}
            }
            // 退避等待：网络刚断时立刻重连大概率还是失败
            if(attempt < maxAttempt){
                long wait = attempt <= 2 ? attempt * 1000L
                          : (attempt == 3 ? 3000L : (attempt == 4 ? 5000L : 8000L));
                try{ Thread.sleep(wait); }catch(Throwable t){}
            }
        }
        // 服务器到期降级：B 站 / IG 中转下载失败 → 解 src/code 参数回退源站直连
        String fallback = extractFallbackDirect(url);
        if(fallback != null && !fallback.equals(url)){
            Log.i(TAG, "IDL_FALLBACK_DIRECT " + fallback.substring(0, Math.min(80, fallback.length())));
            try{ if(part.exists()) part.delete(); }catch(Throwable t){}
            boolean done = fetchOnce(fallback, target, part, cb, ttSessionObj, ctx);
            if(done) return true;
        }
        // 注意：这里【不删】.part，留给「继续下载」用（下次同一个 URL 会接着下）
        Log.i(TAG, "IDL_GIVEUP keep.part=" + (part.exists() ? part.length() : 0));
        return false;
    }

    /** 服务器中转 URL → 源站直链（/api/bili/dl?src=xxx → 解 xxx；/api/ins/dl → IG /media/ 端点） */
    static String extractFallbackDirect(String url){
        try{
            if(url == null) return null;
            if(url.indexOf("/api/bili/dl") >= 0 && url.indexOf("src=") > 0){
                int i = url.indexOf("src=") + 4;
                int j = url.indexOf('&', i);
                String enc = j > 0 ? url.substring(i, j) : url.substring(i);
                return java.net.URLDecoder.decode(enc, "UTF-8");
            }
            // IG：/api/ins/dl?code=xxx&kind=p&size=l → https://www.instagram.com/p/xxx/media/?size=l
            if(url.indexOf("/api/ins/dl") >= 0 && url.indexOf("code=") > 0){
                int i = url.indexOf("code=") + 5;
                int j = url.indexOf('&', i);
                String code = j > 0 ? url.substring(i, j) : url.substring(i);
                String size = "l";
                int si = url.indexOf("size=");
                if(si > 0){
                    int sj = url.indexOf('&', si);
                    size = (sj > 0 ? url.substring(si + 5, sj) : url.substring(si + 5));
                }
                return "https://www.instagram.com/p/" + code + "/media/?size=" + size;
            }
        }catch(Throwable t){}
        return null;
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
                // 但下载中转（bili/x/yt/link dl）强制 8080 HTTP 直连：
                // 真机 SrvClient 会把内置地址转 HTTPS 8443，而华为云安全组只放行 8080。
                if(SrvClient.isBuiltin(url)
                        && url.indexOf("/api/bili/dl") < 0
                        && url.indexOf("/api/x/dl") < 0
                        && url.indexOf("/api/yt/file") < 0
                        && url.indexOf("/api/link/dl") < 0
                        && url.indexOf("/api/ins/dl") < 0){
                    c = SrvClient.open(url, null);
                    SrvClient.sign(c, url);
                    c.setInstanceFollowRedirects(true);
                    c.setRequestProperty("User-Agent", MUA);
                    c.setConnectTimeout(15000);
                    c.setReadTimeout(60000);
                    c.connect();
                } else {
                    // 普通下载：一律直连。
                    // 海外 CDN 被墙时由「内置代理」开关决定是否走服务器中转（URL 已经是中转地址），
                    // 用户自己挂的代理 / TUN 由系统接管，App 不套任何 Proxy。
                    java.net.Proxy px = null;
                    c = (HttpURLConnection) new URL(url).openConnection(
                            java.net.Proxy.NO_PROXY);
                    // 服务器中转：必须带 HMAC 鉴权（AUTH_PREFIXES 保护 /api/），
                    // 用 SrvClient.sign 加头但不走它的 open（避免转 HTTPS 8443）。
                    if(url.indexOf("/api/ins/dl") >= 0 || url.indexOf("/api/bili/dl") >= 0
                            || url.indexOf("/api/x/dl") >= 0 || url.indexOf("/api/yt/file") >= 0
                            || url.indexOf("/api/link/dl") >= 0){
                        try{ SrvClient.sign(c, url); }catch(Throwable t){}
                    }
                    c.setInstanceFollowRedirects(true);
                    boolean isBili = url.indexOf("bilivideo.com") >= 0 || url.indexOf("bilibili.com") >= 0;
                    c.setRequestProperty("User-Agent", isBili ? BiliParser.UA_DESKTOP : MUA);
                    String ref = "https://www.douyin.com/";
                    if(url.indexOf("tiktok") >= 0 || url.indexOf("tiktokcdn") >= 0) ref = "https://www.tiktok.com/";
                    else if(KsParser.isKsUrl(url)) ref = "https://www.kuaishou.com/";
                    else if(MainActivity.isXDomain(url)) ref = "https://x.com/";
                    else if(url.indexOf("googlevideo.com") >= 0) ref = "https://www.youtube.com/";
                    else if(isBili) ref = "https://www.bilibili.com/";
                    else if(url.indexOf("/api/ins/dl") >= 0) ref = "https://www.instagram.com/";
                    c.setRequestProperty("Referer", ref);
                    // B 站 CDN 必须带 buvid3，否则 403
                    if(isBili){
                        String ck = BiliParser.biliCookie();
                        if(ck.length() > 0) c.setRequestProperty("Cookie", ck);
                    }
                    // 断点续传：带 Range 从 .part 大小继续
                    if(resumeFrom > 0) c.setRequestProperty("Range", "bytes=" + resumeFrom + "-");
                    // X/YT 中转地址（服务器在回源大视频，可能 1-2 分钟才出首字节）
                    if(url.indexOf("/api/x/dl") >= 0 || url.indexOf("/api/yt/file") >= 0){
                        c.setConnectTimeout(30000);
                        c.setReadTimeout(300000);
                    } else {
                        // 普通 CDN：
                        // - 连接 20 秒（弱网抖动留余量）
                        // - 读 90 秒（后台时系统会限流，30 秒太容易在慢速时被掐断）
                        c.setConnectTimeout(20000);
                        c.setReadTimeout(90000);
                    }
                    c.connect();
                }
            }

            int code = c.getResponseCode();
            // 206 = 续传成功；200 = 服务器忽略 Range，从头下
            if(code != 200 && code != 206){
                Log.i(TAG, "IDL_HTTP " + code + " " + url);
                msg = "HTTP " + code;
                String why;
                if(code == 403) why = "直链被 CDN 拒绝（通常是直链过期或需要代理）";
                else if(code == 404) why = "文件不存在（直链已过期，需要重新解析）";
                else if(code >= 500) why = "对方服务器出错或中转服务器回源失败";
                else why = "服务器返回了非预期的状态码";
                DiagLog.e(DiagLog.M_DOWNLOAD, "下载失败 | HTTP " + code + " | " + why
                    + "\n  目标地址: " + url
                    + "\n  保存路径: " + target.getAbsolutePath()
                    + "\n  续传起点: " + resumeFrom
                    + "\n  建议: 重新解析一次再试；若反复出现请把这段日志发给我们",
                    new java.io.IOException("HTTP " + code));
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
            try{
                out = new BufferedOutputStream(new FileOutputStream(part, resumeFrom > 0), 64 * 1024);
            }catch(Throwable fe){
                String reason = String.valueOf(fe);
                String hint;
                if(reason.indexOf("EACCES") >= 0 || reason.indexOf("Permission denied") >= 0){
                    hint = "没有存储权限。请到 系统设置 → 应用 → DY解析器 → 权限 → 存储 里打开";
                }else if(reason.indexOf("ENOSPC") >= 0 || reason.indexOf("No space") >= 0){
                    hint = "手机存储空间不足，清理后再试";
                }else if(reason.indexOf("EROFS") >= 0 || reason.indexOf("Read-only") >= 0){
                    hint = "目标目录是只读的，换一个下载目录试试";
                }else{
                    hint = "目录创建失败或系统拒绝写入";
                }
                DiagLog.e(DiagLog.M_DOWNLOAD, "下载失败 | 无法创建文件 | " + hint
                    + "\n  保存路径: " + part.getAbsolutePath()
                    + "\n  上级目录: " + (part.getParentFile() != null ? part.getParentFile().getAbsolutePath() : "?")
                    + "\n  目录是否存在: " + (part.getParentFile() != null && part.getParentFile().exists())
                    + "\n  目录可写: " + (part.getParentFile() != null && part.getParentFile().canWrite()), fe);
                msg = "无法写入文件：" + hint;
                if(cb != null) cb.onDone(false, null, msg);
                return false;
            }

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
            String reason = String.valueOf(t);
            String hint;
            if(reason.indexOf("EACCES") >= 0 || reason.indexOf("Permission denied") >= 0){
                hint = "没有存储权限 → 系统设置 → 应用 → DY解析器 → 权限 → 存储 打开";
            }else if(reason.indexOf("ENOSPC") >= 0){
                hint = "存储空间不足";
            }else if(reason.indexOf("UnknownHost") >= 0 || reason.indexOf("Unable to resolve") >= 0){
                hint = "域名解析失败（网络不通或需要代理）";
            }else if(reason.indexOf("SocketTimeout") >= 0 || reason.indexOf("timeout") >= 0){
                hint = "连接超时（网络慢或服务器无响应）";
            }else if(reason.indexOf("SSL") >= 0 || reason.indexOf("Cert") >= 0){
                hint = "TLS/证书校验失败";
            }else if(reason.indexOf("closed") >= 0 || reason.indexOf("reset") >= 0){
                hint = "连接被中途断开";
            }else{
                hint = "未归类的异常，请看下方堆栈";
            }
            StringBuilder sb = new StringBuilder();
            sb.append("下载失败 | ").append(hint)
              .append("\n  保存路径: ").append(target.getAbsolutePath())
              .append("\n  目标地址: ").append(url)
              .append("\n  已下载字节: ").append(resumeFrom);
            DiagLog.e(DiagLog.M_DOWNLOAD, sb.toString(), t);
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

    /** 探测 URL 是否可下载（HEAD 或 Range 请求，只看状态码，不拉全量）。
     *  用于更新镜像优选：镜像站很多，逐个 HEAD 一下挑可用的。 */
    public static boolean headOk(String url){
        java.net.HttpURLConnection uc = null;
        try{
            uc = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
            uc.setRequestMethod("HEAD");
            uc.setInstanceFollowRedirects(true);
            uc.setConnectTimeout(6000);
            uc.setReadTimeout(8000);
            uc.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13) DYParse");
            int code = uc.getResponseCode();
            // getContentLengthLong 是 API 24+，低版本用 getContentLength
            long len;
            if(android.os.Build.VERSION.SDK_INT >= 24){
                len = uc.getContentLengthLong();
            }else{
                len = uc.getContentLength();
            }
            return code >= 200 && code < 400 && (len <= 0 || len > 100000);
        }catch(Throwable t){
            return false;
        }finally{
            try{ if(uc != null) uc.disconnect(); }catch(Throwable t){}
        }
    }

    /** 探测远端文件总字节数（HEAD + Range 兜底）。失败返回 -1。
     *  用途：4K 原片体积可达 1~2GB，下载前先让用户知道有多大再决定画质。 */
    public static long probeSize(String url){
        long n = probeSizeOnce(url, "HEAD");
        if(n > 0) return n;
        return probeSizeOnce(url, "GET");
    }

    static long probeSizeOnce(String url, String method){
        java.net.HttpURLConnection uc = null;
        try{
            uc = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
            uc.setRequestMethod(method);
            uc.setInstanceFollowRedirects(true);
            uc.setConnectTimeout(8000);
            uc.setReadTimeout(12000);
            uc.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36");
            uc.setRequestProperty("Referer", "https://www.douyin.com/");
            // GET 探测时只要头几个字节，别把整个文件拉下来
            if(!"HEAD".equals(method)) uc.setRequestProperty("Range", "bytes=0-1");
            int code = uc.getResponseCode();
            if(code < 200 || code >= 400) return -1;
            long len;
            if(android.os.Build.VERSION.SDK_INT >= 24){
                len = uc.getContentLengthLong();
            }else{
                len = uc.getContentLength();
            }
            // 206 时 Content-Length 是分段长度，真实总长在 Content-Range 里
            String cr = uc.getHeaderField("Content-Range");
            if(cr != null){
                int slash = cr.lastIndexOf('/');
                if(slash > 0){
                    try{
                        long total = Long.parseLong(cr.substring(slash + 1).trim());
                        if(total > 0) return total;
                    }catch(Throwable t){}
                }
            }
            return len > 0 ? len : -1;
        }catch(Throwable t){
            return -1;
        }finally{
            try{ if(uc != null) uc.disconnect(); }catch(Throwable t){}
        }
    }

    /**
     * 原画地址探测专用：GET + Range: bytes=0-0 读 Content-Range。
     * 抖音的 /aweme/v1/play/ 先回 302，跟随跳转后 CDN 才给 206 + Content-Range，
     * 用 HEAD 走这条路很多 CDN 节点不回 Content-Range（只给 Content-Length，且是分段长度）。
     * 参考 JoeanAmier/TikTokDownloader 的 __request_video_size，它就是 GET + Range。
     */
    public static long probeSizeRange(String url){
        java.net.HttpURLConnection uc = null;
        try{
            uc = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
            uc.setRequestMethod("GET");
            uc.setInstanceFollowRedirects(true);
            uc.setConnectTimeout(8000);
            uc.setReadTimeout(12000);
            uc.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36");
            uc.setRequestProperty("Referer", "https://www.douyin.com/");
            uc.setRequestProperty("Range", "bytes=0-0");
            int code = uc.getResponseCode();
            Log.i("DYP", "PROBE_RANGE code=" + code);
            if(code < 200 || code >= 400) return -1;
            String cr = uc.getHeaderField("Content-Range");
            Log.i("DYP", "PROBE_RANGE cr=" + cr);
            if(cr != null){
                int slash = cr.lastIndexOf('/');
                if(slash > 0){
                    try{
                        long total = Long.parseLong(cr.substring(slash + 1).trim());
                        if(total > 0) return total;
                    }catch(Throwable t){}
                }
            }
            long len;
            if(android.os.Build.VERSION.SDK_INT >= 24) len = uc.getContentLengthLong();
            else len = uc.getContentLength();
            return len > 0 ? len : -1;
        }catch(Throwable t){
            Log.i("DYP", "PROBE_RANGE_ERR " + t);
            return -1;
        }finally{
            try{ if(uc != null) uc.disconnect(); }catch(Throwable t){}
        }
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