package com.re.douyinparse;

import android.content.Context;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Instagram 解析器。
 *
 * 两条链路：
 *  A) 本地直连（优先）：带 CookieStore 里的 instagram.txt 直连 i.instagram.com，
 *     走用户自己的网络 / TUN（App 不套代理）。视频流走 CDN 直链。
 *  B) 服务器中转（兜底）：本地失败或没有 cookie 时，退回 /api/ins/dl。
 *
 * 匿名（无 cookie）只能拿到封面 JPEG，视频流一律 403 login_required。
 */
public class InsParser {

    static final String TAG = "DYP";

    /** IG 安卓端 App-ID（公开常量，用于 api/v1 请求头） */
    static final String IG_APP_ID = "936619743392459";
    static final String IG_UA =
        "Instagram 269.0.0.18.75 Android (30/11; 420dpi; 1080x2340; samsung; SM-G991B; o1s; exynos2100; en_US; 314665256)";

    public static class InsMedia {
        public String code = "";
        public String kind = "";        // p | reel
        public String mediaUrl = "";    // 视频直链（本地 cookie 拿到）或服务器中转地址
        public String coverUrl = "";    // 封面
        public String author = "";
        public String desc = "";
        public long durMs = 0;
        public boolean isVideo = false;
        public boolean local = false;   // true=本地直连解析
        public String err = "";
        public boolean ok(){ return mediaUrl.length() > 0; }
    }

    public static boolean isInsUrl(String url){
        if(url == null) return false;
        String u = url.toLowerCase();
        return u.contains("instagram.com") || u.contains("instagr.am");
    }

    public static String extractCode(String url){
        if(url == null) return "";
        Matcher m = Pattern.compile("(?:/p/|/reel/|/reels/|/tv/)([A-Za-z0-9_-]{5,16})").matcher(url);
        return m.find() ? m.group(1) : "";
    }

    public static String kindOf(String url){
        if(url == null) return "p";
        String u = url.toLowerCase();
        return (u.contains("/reel/") || u.contains("/reels/")) ? "reel" : "p";
    }

    /** shortcode → media_id（IG 的 base64-url 变体解码） */
    public static long codeToId(String sc){
        final String AB = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";
        long n = 0;
        for(int i = 0; i < sc.length(); i++){
            int idx = AB.indexOf(sc.charAt(i));
            if(idx < 0) return 0;
            n = n * 64 + idx;
        }
        return n;
    }

    /**
     * 主入口：先本地（有 cookie 才试），失败再服务器。
     * @param useSrvFallback 是否允许服务器兜底（内置代理开关状态由调用方决定）
     */
    public static InsMedia resolve(Context ctx, String url, boolean useSrvFallback){
        InsMedia v = new InsMedia();
        String code = extractCode(url);
        if(code.length() == 0){
            v.err = "链接里没找到 IG 短码（/p/ 或 /reel/）";
            return v;
        }
        v.code = code;
        v.kind = kindOf(url);

        // A) 本地：有 cookie 才试
        if(CookieStore.insReady(ctx)){
            try{
                InsMedia loc = resolveLocal(ctx, v.code, v.kind);
                if(loc != null && loc.ok()){
                    return loc;
                }
                if(loc != null && loc.err.length() > 0){
                    Log.i(TAG, "INS_LOCAL_FAIL " + loc.err);
                    v.err = loc.err;
                }
            }catch(Throwable t){
                Log.i(TAG, "INS_LOCAL_ERR " + t);
                v.err = "本地解析异常：" + t;
            }
        }else{
            v.err = "未填 Instagram Cookie（设置 → Cookie → Instagram）";
        }

        // B) 服务器兜底
        if(!useSrvFallback) return v;
        InsMedia srv = resolveServer(ctx, v.code, v.kind);
        if(srv.ok()) return srv;
        if(v.err.length() == 0) v.err = srv.err;
        return v;
    }

    /** 旧签名兼容：默认允许服务器兜底 */
    public static InsMedia resolve(Context ctx, String url){
        return resolve(ctx, url, true);
    }

    // ================= 本地直连（带 cookie） =================

    static InsMedia resolveLocal(Context ctx, String code, String kind){
        InsMedia v = new InsMedia();
        v.code = code;
        v.kind = kind;
        v.local = true;

        long mid = codeToId(code);
        if(mid <= 0){
            v.err = "短码解析失败";
            return v;
        }
        String ck = CookieStore.header(ctx, CookieStore.PLATFORM_INS);
        String csrf = CookieStore.get(ctx, CookieStore.PLATFORM_INS, "csrftoken");
        if(csrf == null) csrf = "";
        Log.i(TAG, "INS_LOCAL try code=" + code + " mid=" + mid + " ck_len=" + ck.length());

        // 端点顺序：i.instagram.com（App 接口）最稳，web api 其次
        String[] urls = {
            "https://i.instagram.com/api/v1/media/" + mid + "/info/",
            "https://www.instagram.com/api/v1/media/" + mid + "/info/",
            "https://www.instagram.com/api/v1/media/" + mid + "/info/?__a=1&__d=dis"
        };
        String lastErr = "";
        for(int ui = 0; ui < urls.length; ui++){
            String infoUrl = urls[ui];
            String body = null;
            int http = 0;
            try{
                HttpURLConnection c = (HttpURLConnection) new URL(infoUrl).openConnection();
                c.setConnectTimeout(8000);
                c.setReadTimeout(10000);
                c.setRequestProperty("User-Agent",
                    "Mozilla/5.0 (Linux; Android 12; Pixel 5) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36");
                c.setRequestProperty("Cookie", ck);
                c.setRequestProperty("X-IG-App-ID", IG_APP_ID);
                c.setRequestProperty("X-ASBD-ID", "129477");
                c.setRequestProperty("X-IG-WWW-Claim", "0");
                c.setRequestProperty("X-Requested-With", "XMLHttpRequest");
                if(csrf.length() > 0) c.setRequestProperty("X-CSRFToken", csrf);
                c.setRequestProperty("Referer", "https://www.instagram.com/");
                c.setRequestProperty("Origin", "https://www.instagram.com");
                c.setRequestProperty("Accept", "*/*");
                c.setRequestProperty("Accept-Language", "en-US,en;q=0.9");
                http = c.getResponseCode();
                InputStream is = http >= 400 ? c.getErrorStream() : c.getInputStream();
                body = readAll(is);
                c.disconnect();
            }catch(Throwable t){
                Log.i(TAG, "INS_LOCAL_HTTP_ERR[" + ui + "] " + t);
                lastErr = "连不上 Instagram（" + t + "）";
                continue;
            }
            Log.i(TAG, "INS_LOCAL_HTTP[" + ui + "] http=" + http
                    + " len=" + (body == null ? 0 : body.length())
                    + " body=" + head(oneLine(body), 300));

            if(http == 200 && body != null && body.length() > 0){
                InsMedia parsed = parseMediaInfo(body);
                if(parsed != null && parsed.ok()){
                    parsed.code = code;
                    parsed.kind = kind;
                    parsed.local = true;
                    Log.i(TAG, "INS_LOCAL_OK ep=" + ui + " video=" + parsed.isVideo
                            + " url=" + head(parsed.mediaUrl, 60));
                    return parsed;
                }
                lastErr = parsed != null && parsed.err.length() > 0 ? parsed.err : "响应里没有媒体地址";
                continue;
            }
            if(http == 404){ lastErr = "作品不存在或已删除"; continue; }
            if(http == 401){ lastErr = "Instagram 登录态失效（重新登录）"; continue; }
            if(http == 400){ lastErr = "IG 接口返回 400（端点 " + ui + "）"; continue; }
            if(http == 403){ lastErr = "IG 拒绝访问（403，可能风控或登录态失效）"; continue; }
            lastErr = "Instagram 返回 HTTP " + http;
        }

        v.err = lastErr;
        String web = tryOgVideo(code, kind);
        if(web.length() > 0){
            v.mediaUrl = web;
            v.isVideo = true;
            v.coverUrl = web;
            v.err = "";
            Log.i(TAG, "INS_LOCAL_OG_OK " + head(web, 60));
            return v;
        }
        return v;
    }

    /** 日志用：去掉换行 */
    static String oneLine(String s){
        if(s == null) return "";
        return s.replace("\r", " ").replace("\n", " ").replace("\t", " ");
    }

    /** 网页 og:video 兜底（不带 cookie，走用户网络） */
    static String tryOgVideo(String code, String kind){
        String[] paths = {
            "https://www.instagram.com/reel/" + code + "/",
            "https://www.instagram.com/p/" + code + "/"
        };
        for(String p : paths){
            try{
                HttpURLConnection c = (HttpURLConnection) new URL(p).openConnection();
                c.setConnectTimeout(10000);
                c.setReadTimeout(12000);
                c.setInstanceFollowRedirects(true);
                c.setRequestProperty("User-Agent",
                    "Mozilla/5.0 (iPhone; CPU iPhone OS 16_0 like Mac OS X) AppleWebKit/605.1.15 "
                    + "(KHTML, like Gecko) Version/16.0 Mobile/15E148 Safari/604.1");
                c.setRequestProperty("Accept-Language", "en-US,en;q=0.9");
                int http = c.getResponseCode();
                if(http != 200){ c.disconnect(); continue; }
                String html = readAll(c.getInputStream());
                c.disconnect();
                if(html == null || html.length() < 1024) continue;
                Matcher m = Pattern.compile(
                    "<meta[^>]+property=\"og:video(?::secure_url)?\"[^>]+content=\"([^\"]+)\"")
                    .matcher(html);
                if(m.find()){
                    String u = m.group(1).replace("&amp;", "&");
                    if(u.startsWith("http")) return u;
                }
                Matcher m2 = Pattern.compile("\"video_url\":\"([^\"]+)\"").matcher(html);
                if(m2.find()){
                    String u = m2.group(1).replace("\\/", "/").replace("\\u0026", "&");
                    if(u.startsWith("http")) return u;
                }
            }catch(Throwable t){
                Log.i(TAG, "INS_OG_ERR " + t);
            }
        }
        return "";
    }

    /** 解析 i.instagram.com/api/v1/media/{id}/info/ 的 JSON */
    static InsMedia parseMediaInfo(String json){
        InsMedia v = new InsMedia();
        try{
            int i = json.indexOf("\"items\"");
            if(i < 0){ v.err = "无 items"; return v; }
            int md = json.indexOf("\"media_type\":", i);
            int mt = 1;
            if(md > 0){
                int e = md + 13;
                StringBuilder sb = new StringBuilder();
                while(e < json.length() && (Character.isDigit(json.charAt(e)) || json.charAt(e) == '-')){
                    sb.append(json.charAt(e)); e++;
                }
                try{ mt = Integer.parseInt(sb.toString()); }catch(Throwable t){}
            }
            v.isVideo = (mt == 2);
            String video = pick(json, i, "video_versions", "url");
            if(video.length() == 0) video = unesc(pickStr(json, i, "video_url"));
            String img = pick(json, i, "candidates", "url");
            if(img.length() == 0) img = unesc(pickStr(json, i, "display_url"));
            String user = pickStr(json, i, "username");
            if(user.length() > 0) v.author = user;
            String cap = pickStr(json, i, "text");
            if(cap.length() > 0) v.desc = cap;
            long dur = pickLong(json, i, "video_duration");
            if(dur > 0) v.durMs = dur;

            if(video.length() > 0){
                v.mediaUrl = video;
                v.coverUrl = img;
                return v;
            }
            if(img.length() > 0){
                v.mediaUrl = img;
                v.coverUrl = img;
                v.isVideo = false;
                v.err = "";
                return v;
            }
            v.err = "JSON 里没有媒体地址（可能需要重新登录）";
        }catch(Throwable t){
            v.err = "JSON 解析异常 " + t;
        }
        return v;
    }

    static String pick(String json, int start, String key, String sub){
        try{
            int k = json.indexOf("\"" + key + "\"", start);
            if(k < 0) return "";
            if(sub == null) return unesc(pickStr(json, k, key));
            int u = json.indexOf("\"" + sub + "\"", k);
            if(u < 0) return "";
            return unesc(pickStr(json, u, sub));
        }catch(Throwable t){ return ""; }
    }

    static String pickStr(String json, int from, String key){
        try{
            int k = json.indexOf("\"" + key + "\"", from);
            if(k < 0) return "";
            int c = json.indexOf(':', k + key.length() + 2);
            if(c < 0) return "";
            int q1 = json.indexOf('"', c);
            if(q1 < 0) return "";
            int j = q1 + 1;
            StringBuilder sb = new StringBuilder();
            while(j < json.length()){
                char ch = json.charAt(j);
                if(ch == '\\'){
                    sb.append(ch);
                    if(j + 1 < json.length()){ sb.append(json.charAt(j + 1)); j += 2; continue; }
                }
                if(ch == '"') break;
                sb.append(ch);
                j++;
            }
            return sb.toString();
        }catch(Throwable t){ return ""; }
    }

    static long pickLong(String json, int from, String key){
        try{
            int k = json.indexOf("\"" + key + "\"", from);
            if(k < 0) return 0;
            int c = json.indexOf(':', k + key.length() + 2);
            if(c < 0) return 0;
            int j = c + 1;
            StringBuilder sb = new StringBuilder();
            while(j < json.length()){
                char ch = json.charAt(j);
                if(Character.isDigit(ch) || ch == '.' || ch == '-') sb.append(ch);
                else if(sb.length() > 0) break;
                j++;
            }
            double d = Double.parseDouble(sb.toString());
            return (long) (d * 1000);
        }catch(Throwable t){ return 0; }
    }

    static String unesc(String s){
        if(s == null) return "";
        return s.replace("\\/", "/").replace("\\u0026", "&").replace("\\\"", "\"");
    }

    static String head(String s, int n){
        if(s == null) return "";
        return s.length() <= n ? s : s.substring(0, n);
    }

    static String readAll(InputStream is){
        if(is == null) return "";
        try{
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while((n = is.read(buf)) > 0) bo.write(buf, 0, n);
            is.close();
            return new String(bo.toByteArray(), "UTF-8");
        }catch(Throwable t){ return ""; }
    }

    // ================= 服务器中转（兜底） =================

    static InsMedia resolveServer(Context ctx, String code, String kind){
        InsMedia v = new InsMedia();
        v.code = code;
        v.kind = kind;
        String srv = MainActivity.effSrvS(ctx);
        if(srv.length() == 0){
            v.err = "无服务器可用";
            return v;
        }
        String ts = String.valueOf(System.currentTimeMillis() / 1000);
        String tk = "";
        try{
            String key = MainActivity.srvHmacKeyS(ctx);
            if(key.length() > 0){
                javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
                mac.init(new javax.crypto.spec.SecretKeySpec(key.getBytes("UTF-8"), "HmacSHA256"));
                byte[] raw = mac.doFinal(("/api/ins/dl\n" + ts + "\n" + code).getBytes("UTF-8"));
                StringBuilder sb = new StringBuilder();
                for(byte b : raw) sb.append(String.format("%02x", b));
                tk = sb.toString();
            }
        }catch(Throwable t){}
        String base = srv + "/api/ins/dl?code=" + code + "&kind=p&size=";
        String sig = "&ts=" + ts + "&tk=" + tk + "&id=" + code;
        v.mediaUrl = base + "l" + sig;
        v.coverUrl = base + "m" + sig;
        Log.i(TAG, "INS_SRV url=" + head(v.mediaUrl, 70));
        return v;
    }

    // ================= 渲染 =================

    public static String toResultJson(InsMedia v){
        StringBuilder b = new StringBuilder(512);
        b.append('{');
        kv(b, "i_id", "ig_" + v.code);
        kv(b, "i_author", v.author.length() > 0 ? v.author : "Instagram");
        String d = v.desc;
        if(d == null) d = "";
        if(d.length() == 0){
            if(v.isVideo) d = v.local ? "Instagram Reel（本地解析）" : "Instagram Reel";
            else d = v.local ? "Instagram 图文（本地解析）" : "Instagram 图文";
        }
        kv(b, "i_desc", d);
        if(v.durMs > 0) b.append("\"i_dur\":\"").append(v.durMs).append("\",");
        if(v.coverUrl.length() > 0) kv(b, "i_cover", v.coverUrl);
        if(v.isVideo){
            // 视频：走 i_play（播放/下载都取这个）
            b.append("\"i_play\":\"").append(esc(v.mediaUrl)).append("\",");
        }else{
            // 图文：走 i_imgs（图片队列）
            b.append("\"i_imgs\":\"").append(esc(v.mediaUrl)).append("\",");
        }
        kv(b, "i_platform", "instagram");
        b.append("\"i_ok\":1}");
        return b.toString();
    }

    static String esc(String s){
        if(s == null) return "";
        StringBuilder b = new StringBuilder(s.length() + 8);
        for(int i = 0; i < s.length(); i++){
            char c = s.charAt(i);
            if(c == '"' || c == '\\' || c == '\n' || c == '\r') b.append('\\');
            b.append(c);
        }
        return b.toString();
    }

    static void kv(StringBuilder b, String k, String v){
        b.append('"').append(k).append("\":\"").append(esc(v)).append("\",");
    }
}