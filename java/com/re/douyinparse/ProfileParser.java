package com.re.douyinparse;

import android.content.Context;
import android.util.Log;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 主页列表解析器：走服务器 /api/profile/list。
 * 支持：YouTube 频道、TikTok 用户、X 用户主页。
 * 抖音/快手：服务器暂不支持（网页端需签名），返回提示。
 */
public class ProfileParser {

    static final String TAG = "DYP";
    static final String UA = "Mozilla/5.0 (Linux; Android 12; Pixel 5) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36";

    public static String lastErr = "";

    public static class ProfileItem {
        public String id = "";
        public String title = "";
        public long durSec = 0;
        public String url = "";
        /** 列表响应里直接带的无水印直链（抖音/快手 WebView 方案用），空则下载时再解析 */
        public String directUrl = "";
        /** 图集（下载走图片队列） */
        public boolean isImage = false;
    }

    /** 是否主页链接 */
    public static boolean isProfileUrl(String url){
        if(url == null) return false;
        String u = url.toLowerCase();
        // 抖音用户主页：douyin.com/user/xxx（不带 /video/ /note/ /slides/）
        if(u.contains("douyin.com/user/") && !u.contains("/video/")
                && !u.contains("/note/") && !u.contains("/slides/")){
            return true;
        }
        // 快手用户主页：kuaishou.com/profile/xxx 或 live.kuaishou.com/u/xxx
        if((u.contains("kuaishou.com/profile/") || u.contains("live.kuaishou.com/u/"))
                && !u.contains("/short-video/")){
            return true;
        }
        // B 站空间主页
        if(u.contains("bilibili.com") && java.util.regex.Pattern
                .compile("bilibili\\.com/space/\\d{5,12}").matcher(u).find()){
            return true;
        }
        // YouTube @频道（不带 watch?v= 或 playlist）
        if((u.contains("youtube.com/@") || u.contains("youtube.com/channel/"))
                && !u.contains("watch?") && !u.contains("playlist?")){
            return true;
        }
        // TikTok @用户
        if(u.contains("tiktok.com/@") && !u.contains("/video/") && !u.contains("/photo/")){
            return true;
        }
        // X 用户主页（x.com/xxx，不带 /status/ /photo/ 等作品路径）
        if((u.contains("x.com/") || u.contains("twitter.com/"))){
            if(u.contains("/status") || u.contains("/photo/") || u.contains("/video/")){
                return false;
            }
            Pattern p = Pattern.compile("(?:x|twitter)\\.com/([A-Za-z0-9_]+)(?:[/?].*)?$");
            Matcher m = p.matcher(u);
            if(m.find()){
                String name = m.group(1);
                return !name.equals("i") && !name.equals("home") && !name.equals("intent")
                        && !name.equals("settings") && !name.equals("search");
            }
        }
        // YouTube @频道主页
        if(u.contains("youtube.com/@") && !u.contains("watch?") && !u.contains("playlist?")){
            return true;
        }
        // Instagram 用户主页：instagram.com/{username}，不带 /p/ /reel/ /stories/
        if(u.contains("instagram.com/") || u.contains("instagr.am/")){
            if(u.contains("/p/") || u.contains("/reel/") || u.contains("/reels/")
                    || u.contains("/tv/") || u.contains("/stories/") || u.contains("/explore/")){
                return false;
            }
            Matcher m = Pattern.compile("instagram\\.com/([A-Za-z0-9_.]{2,30})(?:[/?#].*)?$").matcher(u);
            if(m.find()){
                String name = m.group(1);
                return !name.equals("accounts") && !name.equals("about")
                        && !name.equals("developer") && !name.equals("legal")
                        && !name.equals("directory") && !name.equals("web");
            }
        }
        return false;
    }

    /** 从 IG 主页链接里取用户名 */
    public static String igUsername(String url){
        if(url == null) return "";
        Matcher m = Pattern.compile("instagram\\.com/([A-Za-z0-9_.]{2,30})(?:[/?#]|$)")
                .matcher(url.toLowerCase());
        return m.find() ? m.group(1) : "";
    }

    /**
     * Instagram 主页列表：本地带 Cookie 直连 web_profile_info（走用户自己网络）。
     * 没 Cookie 或失败返回 null，lastErr 写好。
     */
    public static List<ProfileItem> resolveInsProfile(Context ctx, String url){
        lastErr = "";
        String user = igUsername(url);
        if(user.length() == 0){
            lastErr = "链接里没找到 IG 用户名";
            return null;
        }
        if(!CookieStore.insReady(ctx)){
            lastErr = "IG 主页需要登录：设置 → Instagram Cookie";
            return null;
        }
        String ck = CookieStore.header(ctx, CookieStore.PLATFORM_INS);
        String csrf = CookieStore.get(ctx, CookieStore.PLATFORM_INS, "csrftoken");
        if(csrf == null) csrf = "";

        String api = "";
        String body = null;
        int code = 0;
        HttpURLConnection c = null;
        // 多端点重试：i 站、www 站、usernameinfo POST
        String[] eps = {
            "https://i.instagram.com/api/v1/users/web_profile_info/?username=",
            "https://www.instagram.com/api/v1/users/web_profile_info/?username="
        };
        for(int ei = 0; ei < eps.length; ei++){
        try{
            api = eps[ei] + java.net.URLEncoder.encode(user, "UTF-8");
            c = (HttpURLConnection) new URL(api).openConnection(java.net.Proxy.NO_PROXY);
            c.setConnectTimeout(10000);
            c.setReadTimeout(20000);
            c.setRequestProperty("User-Agent", UA);
            c.setRequestProperty("Cookie", ck);
            c.setRequestProperty("X-IG-App-ID", "936619743392459");
            c.setRequestProperty("X-ASBD-ID", "129477");
            c.setRequestProperty("X-Requested-With", "XMLHttpRequest");
            if(csrf.length() > 0) c.setRequestProperty("X-CSRFToken", csrf);
            c.setRequestProperty("Referer", "https://www.instagram.com/" + user + "/");
            c.setRequestProperty("Accept", "*/*");
            code = c.getResponseCode();
            InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while(in != null && (n = in.read(buf)) > 0) bo.write(buf, 0, n);
            if(in != null) in.close();
            body = bo.toString("UTF-8");
        }catch(Throwable t){
            Log.i(TAG, "INS_PROFILE_ERR[" + ei + "] " + t);
            lastErr = "连不上 Instagram（" + t + "）";
            continue;
        }finally{
            try{ if(c != null) c.disconnect(); }catch(Throwable t){}
        }
        Log.i(TAG, "INS_PROFILE_HTTP[" + ei + "] " + code + " user=" + user
                + " len=" + (body == null ? 0 : body.length())
                + " head=" + (body == null ? "" : body.substring(0, Math.min(200, body.length())).replace("\n", " ")));

        if(code != 200 || body == null || body.length() == 0){
            lastErr = "IG 主页接口 HTTP " + code;
            continue;
        }
        List<ProfileItem> out = parseInsTimeline(body);
        if(out.isEmpty()){
            lastErr = "主页没有作品（或账号私密）";
            continue;
        }
        return out;
        }
        return null;
    }

    /** 解析 IG web_profile_info 的 JSON，抽出最近作品 */
    static List<ProfileItem> parseInsTimeline(String json){
        List<ProfileItem> out = new ArrayList<ProfileItem>();
        try{
            int k = json.indexOf("\"edge_owner_to_timeline_media\"");
            if(k < 0) k = json.indexOf("\"edges\"");
            if(k < 0) return out;
            // 逐个找 shortcode
            int p = k;
            int guard = 0;
            while(guard++ < 200){
                int sc = json.indexOf("\"shortcode\":\"", p);
                if(sc < 0) break;
                int s1 = sc + 13;
                int s2 = json.indexOf('"', s1);
                if(s2 <= s1) break;
                String code = json.substring(s1, s2);
                // 这一段里的字段
                int seg = json.indexOf("\"shortcode\":\"", s2);
                String slice = json.substring(sc, seg > 0 ? Math.min(seg, sc + 4000) : Math.min(json.length(), sc + 4000));

                ProfileItem it = new ProfileItem();
                it.id = code;
                it.url = "https://www.instagram.com/p/" + code + "/";
                it.title = insStr(slice, "edge_media_to_caption") ;
                if(it.title.length() == 0) it.title = insStr(slice, "text");
                if(it.title.length() == 0) it.title = "Instagram " + code;
                boolean isVideo = slice.indexOf("\"is_video\":true") >= 0
                        || slice.indexOf("\"media_type\":2") >= 0;
                it.isImage = !isVideo;
                long dur = insNum(slice, "video_duration");
                it.durSec = dur;
                if(code.length() >= 5) out.add(it);
                p = s2;
            }
        }catch(Throwable t){
            Log.i(TAG, "INS_PROFILE_PARSE_ERR " + t);
        }
        Log.i(TAG, "INS_PROFILE_PARSE n=" + out.size());
        return out;
    }

    /** 从 JSON 片段里取 "key":"value" 的字符串值（去掉转义） */
    static String insStr(String json, String key){
        try{
            int k = json.indexOf("\"" + key + "\"");
            if(k < 0) return "";
            int c = json.indexOf(':', k + key.length() + 2);
            if(c < 0) return "";
            int q = json.indexOf('"', c);
            if(q < 0) return "";
            int j = q + 1;
            StringBuilder sb = new StringBuilder();
            while(j < json.length()){
                char ch = json.charAt(j);
                if(ch == '\\'){
                    char nx = j + 1 < json.length() ? json.charAt(j + 1) : 0;
                    if(nx == 'n'){ sb.append(' '); j += 2; continue; }
                    if(nx == 'u'){ j += 6; continue; }
                    j += 2; continue;
                }
                if(ch == '"') break;
                sb.append(ch);
                j++;
            }
            String s = sb.toString().trim();
            if(s.length() > 80) s = s.substring(0, 80) + "…";
            return s;
        }catch(Throwable t){ return ""; }
    }

    static long insNum(String json, String key){
        try{
            int k = json.indexOf("\"" + key + "\":");
            if(k < 0) return 0;
            int j = k + key.length() + 3;
            StringBuilder sb = new StringBuilder();
            while(j < json.length()){
                char ch = json.charAt(j);
                if(Character.isDigit(ch) || ch == '.') sb.append(ch);
                else if(sb.length() > 0) break;
                j++;
            }
            return (long) Double.parseDouble(sb.toString());
        }catch(Throwable t){ return 0; }
    }

    /** 解析主页列表；失败返回 null 并把 lastErr 写好 */
    public static List<ProfileItem> resolveProfile(Context ctx, String url){
        lastErr = "";
        List<ProfileItem> out = new ArrayList<ProfileItem>();
        HttpURLConnection c = null;
        try{
            String srv = MainActivity.effSrvS(ctx);
            if(srv.length() == 0){
                lastErr = "无服务器：主页列表需要服务器（解析境外平台）";
                return null;
            }
            String api = srv + "/api/profile/list?url=" + URLEncoder.encode(url, "UTF-8")
                    + "&uid=" + MainActivity.ytUid(ctx);
            c = (HttpURLConnection) new URL(api).openConnection(java.net.Proxy.NO_PROXY);
            c.setRequestProperty("User-Agent", UA);
            c.setRequestProperty("Accept", "application/json,*/*");
            // HMAC 鉴权
            String ts = String.valueOf(System.currentTimeMillis() / 1000);
            String nonce = java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 12);
            String path = "/api/profile/list";
            String msg = path + "\n" + ts + "\n" + nonce;
            String key = MainActivity.srvHmacKeyS(ctx);
            if(key.length() > 0){
                javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
                mac.init(new javax.crypto.spec.SecretKeySpec(key.getBytes("UTF-8"), "HmacSHA256"));
                byte[] raw = mac.doFinal(msg.getBytes("UTF-8"));
                StringBuilder sb = new StringBuilder();
                for(byte b : raw) sb.append(String.format("%02x", b));
                c.setRequestProperty("X-TS", ts);
                c.setRequestProperty("X-NONCE", nonce);
                c.setRequestProperty("X-SIGN", sb.toString());
            }
            c.setConnectTimeout(20000);
            c.setReadTimeout(120000);   // yt-dlp flat 可能较慢
            int code = c.getResponseCode();
            InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            in.close();
            String body = bo.toString("UTF-8");
            Log.i(TAG, "PROFILE_HTTP " + code + " len=" + body.length() + " preview=" + body.substring(0, Math.min(150, body.length())));
            if(code != 200){
                lastErr = "服务器错误 HTTP " + code;
                return null;
            }
            Object root = XParser.Json.parse(body);
            if(!(root instanceof java.util.Map)){
                lastErr = "响应格式错误";
                return null;
            }
            java.util.Map r = (java.util.Map) root;
            Object okObj = r.get("ok");
            boolean ok = Boolean.TRUE.equals(okObj) || "true".equals(String.valueOf(okObj));
            if(!ok){
                lastErr = String.valueOf(r.get("err"));
                return null;
            }
            Object items = r.get("items");
            if(items instanceof List){
                for(Object o : (List) items){
                    if(!(o instanceof java.util.Map)) continue;
                    java.util.Map m = (java.util.Map) o;
                    ProfileItem it = new ProfileItem();
                    it.id = String.valueOf(m.get("id") == null ? "" : m.get("id"));
                    it.title = String.valueOf(m.get("title") == null ? "" : m.get("title"));
                    Object dur = m.get("dur");
                    it.durSec = dur instanceof Number ? ((Number) dur).longValue() : 0;
                    it.url = String.valueOf(m.get("url") == null ? "" : m.get("url"));
                    if(it.id.length() > 0 || it.url.length() > 0) out.add(it);
                }
            }
            if(out.isEmpty()){
                lastErr = "主页没有作品";
                return null;
            }
            return out;
        }catch(Throwable t){
            Log.i(TAG, "PROFILE_ERR " + t);
            lastErr = "解析异常: " + t;
            return null;
        }finally{
            try{ if(c != null) c.disconnect(); }catch(Throwable t){}
        }
    }
}