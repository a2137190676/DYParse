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
        // X 用户主页（x.com/xxx，不是 /status/）
        if((u.contains("x.com/") || u.contains("twitter.com/"))){
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
        return false;
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