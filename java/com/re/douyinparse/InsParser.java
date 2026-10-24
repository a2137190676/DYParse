package com.re.douyinparse;

import android.content.Context;
import android.util.Log;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Instagram 解析器（匿名直出，走服务器中转）。
 * 匿名限制（IG 无登录 Cookie）：
 *   - /p/{code}   图文 → /media/?size=l 直出第一张 JPEG
 *   - /reel/{code} 视频 → 只能拿封面 JPEG（视频流需登录）
 * 链路：App → 服务器 /api/ins/dl（机场代理出口，带缓存）。
 */
public class InsParser {

    static final String TAG = "DYP";

    public static class InsMedia {
        public String code = "";
        public String kind = "";       // p | reel
        public String mediaUrl = "";   // 服务器中转下载地址
        public String coverUrl = "";   // 缩略图（服务器中转）
        public boolean isVideo = false; // 仅提示用（匿名下永远拿不到视频流）
        public String err = "";
        public boolean ok(){ return mediaUrl.length() > 0; }
    }

    /** 是否 Instagram 链接 */
    public static boolean isInsUrl(String url){
        if(url == null) return false;
        String u = url.toLowerCase();
        return u.contains("instagram.com") || u.contains("instagr.am");
    }

    /** 抽 shortcode：/p/ 或 /reel/ 或 /reels/ 后面的短码 */
    public static String extractCode(String url){
        if(url == null) return "";
        Matcher m = Pattern.compile("(?:/p/|/reel/|/reels/)([A-Za-z0-9_-]{5,16})")
                .matcher(url);
        return m.find() ? m.group(1) : "";
    }

    /** 判断 kind */
    public static String kindOf(String url){
        if(url == null) return "p";
        String u = url.toLowerCase();
        return (u.contains("/reel/") || u.contains("/reels/")) ? "reel" : "p";
    }

    /** 解析：返回带服务器中转地址的结果（不实际拉媒体） */
    public static InsMedia resolve(Context ctx, String url){
        InsMedia v = new InsMedia();
        String code = extractCode(url);
        if(code.length() == 0){
            v.err = "链接里没找到 IG 短码（/p/ 或 /reel/）";
            return v;
        }
        v.code = code;
        v.kind = kindOf(url);
        String srv = MainActivity.effSrvS(ctx);
        if(srv.length() == 0){
            v.err = "无服务器：Instagram 解析需要服务器中转（手机直连被墙）";
            return v;
        }
        // query 签名：tk = hmac(path\n ts\n id)，id 用 code（服务器端 check_auth 同样规则）
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
        // reel 的 /reel/{code}/media/ 端点 404，但 /p/{code}/media/ 能出封面图；
        // 匿名下 reel 本来就只能拿封面，统一走 kind=p。
        String dlKind = v.kind.equals("reel") ? "p" : "p";
        String base = srv + "/api/ins/dl?code=" + code + "&kind=" + dlKind + "&size=";
        String sig = "&ts=" + ts + "&tk=" + tk + "&id=" + code;
        v.mediaUrl = base + "l" + sig;
        v.coverUrl = base + "m" + sig;
        return v;
    }

    /** 生成 render 用 JSON（图片型） */
    public static String toResultJson(InsMedia v){
        StringBuilder b = new StringBuilder(512);
        b.append('{');
        kv(b, "i_id", "ig_" + v.code);
        kv(b, "i_author", "Instagram");
        kv(b, "i_desc", v.kind.equals("reel")
                ? "Instagram Reel（匿名仅封面：视频流需要登录，可保存封面图）"
                : "Instagram 图文");
        kv(b, "i_cover", v.coverUrl);
        // 图集单元素
        b.append("\"i_imgs\":\"").append(v.mediaUrl).append("\",");
        kv(b, "i_platform", "instagram");
        b.append("\"i_ok\":1}");
        return b.toString();
    }

    static void kv(StringBuilder b, String k, String v){
        b.append('"').append(k).append("\":\"");
        String s = v == null ? "" : v;
        for(int i = 0; i < s.length(); i++){
            char c = s.charAt(i);
            if(c == '"' || c == '\\' || c == '\n' || c == '\r') b.append('\\');
            b.append(c);
        }
        b.append("\",");
    }
}