package com.re.douyinparse;

import android.content.Context;
import android.util.Log;
import java.io.ByteArrayOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * YouTube 解析器 —— 走自建服务器（服务器用 yt-dlp + 机场代理）。
 *
 * 设计要点：
 *  - 本地直连 YouTube 不可能（DNS 污染 + 风控），必须服务器中转
 *  - 部分视频被 YouTube 风控（LOGIN_REQUIRED），服务器会返回 yt_login_required，
 *    App 端要明确提示「这条视频需要登录 Cookie，无法解析」
 *  - 播放列表（playlist）支持，返回所有条目，App 端逐个点开解析
 */
public class YtParser {

    static final String TAG = "DYP";
    static final String UA = "Mozilla/5.0 (Linux; Android 12; Pixel 5) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36";

    /** 解析结果 */
    public static class YtVideo {
        public String id = "";
        public String title = "";
        public String author = "";
        public String thumb = "";
        public long durationSec = 0;
        public String videoUrl = "";   // 服务器解析出的直链（可能 HLS）
        public String audioUrl = "";
        public String err = "";
        public String pageUrl = "";

        /** 播放列表条目 */
        public static class Item {
            public String id = "";
            public String title = "";
            public long durSec = 0;
        }
        public final List<Item> items = new ArrayList<Item>();

        /** 服务器机场流量标记 */
        public boolean subUsable = true;
        public String subReason = "";

        public boolean isPlaylist(){ return !items.isEmpty(); }
        public boolean ok(){ return isPlaylist() || videoUrl.length() > 0; }

        public String toResultJson(){
            StringBuilder b = new StringBuilder(2048);
            b.append('{');
            kv(b, "i_id", id);
            kv(b, "i_author", author);
            kv(b, "i_desc", title);
            kv(b, "i_dur", durationSec > 0 ? String.valueOf(durationSec * 1000) : "");
            kv(b, "i_cover", thumb);
            kv(b, "i_platform", "YouTube");
            if(videoUrl.length() > 0) kv(b, "i_play", videoUrl);
            if(audioUrl.length() > 0) kv(b, "i_audio", audioUrl);
            b.append('}');
            return b.toString();
        }

        static void kv(StringBuilder b, String k, String v){
            if(v == null || v.length() == 0) return;
            if(b.length() > 1) b.append(',');
            b.append('"').append(k).append("\":\"").append(esc(v)).append('"');
        }

        static String esc(String s){
            StringBuilder b = new StringBuilder(s.length() + 16);
            for(int i = 0; i < s.length(); i++){
                char c = s.charAt(i);
                switch(c){
                    case '"':  b.append("\\\""); break;
                    case '\\': b.append("\\\\"); break;
                    case '\n': b.append("\\n"); break;
                    case '\r': b.append("\\r"); break;
                    case '\t': b.append("\\t"); break;
                    default:
                        if(c < 0x20) b.append(String.format("\\u%04x", (int) c));
                        else b.append(c);
                }
            }
            return b.toString();
        }
    }

    /** 是否 YouTube 链接 */
    public static boolean isYtUrl(String url){
        if(url == null) return false;
        String u = url.toLowerCase();
        return u.contains("youtube.com") || u.contains("youtu.be");
    }

    /** 主入口：走服务器解析。
     *  实测结论（2026-10）：googlevideo 直链的 ip= 参数绑定解析 IP，
     *  strip 后 403，用户自己的代理拿不到直链 —— direct 模式对 YouTube 无效。
     *  因此统一走服务器中转（yt-dlp 下载合并 mp4 进缓存，App 走 /api/yt/file）。
     *  服务器到期后：用户可自建服务器（源码开源）填进设置，功能不受影响。 */
    public static YtVideo resolve(Context ctx, String url){
        YtVideo v = new YtVideo();
        v.pageUrl = url;
        if(url == null || url.length() == 0){ v.err = "空链接"; return v; }

        String srv = MainActivity.effSrvS(ctx);
        if(srv.length() == 0){
            v.err = "无服务器：YouTube 解析必须走服务器（本地直连被 DNS 污染）";
            return v;
        }

        try{
            String api = srv + "/api/yt/parse?url=" + java.net.URLEncoder.encode(url, "UTF-8");
            String js = httpGetApi(ctx, api, 600000);
            if(js == null || js.length() < 50){
                v.err = "服务器无响应";
                return v;
            }
            Object root = XParser.Json.parse(js);
            if(!(root instanceof java.util.Map)){ v.err = "响应格式错误"; return v; }
            java.util.Map r = (java.util.Map) root;

            boolean ok = "true".equals(s(get(r, "ok"))) || Boolean.TRUE.equals(get(r, "ok"));
            if(!ok){
                String err = s(get(r, "err"));
                if("yt_login_required".equals(err) || err.contains("Sign in")){
                    v.err = "这条视频被 YouTube 风控（需登录 Cookie），暂无法解析";
                } else if("yt_bot_check".equals(err) || err.contains("not a bot")){
                    v.err = "YouTube 机器人验证拦截，稍后再试";
                } else if(err.length() > 0){
                    v.err = err;
                } else {
                    v.err = "解析失败";
                }
                return v;
            }

            v.subUsable = !"false".equals(s(get(r, "sub_usable")));
            v.subReason = s(get(r, "sub_reason"));

            String type = s(get(r, "type"));
            if("playlist".equals(type)){
                Object items = get(r, "items");
                if(items instanceof List){
                    for(Object o : (List) items){
                        if(!(o instanceof java.util.Map)) continue;
                        java.util.Map m = (java.util.Map) o;
                        YtParser.YtVideo.Item it = new YtParser.YtVideo.Item();
                        it.id = s(get(m, "id"));
                        it.title = s(get(m, "title"));
                        it.durSec = l(get(m, "dur"));
                        if(it.id.length() > 0) v.items.add(it);
                    }
                }
                if(v.items.isEmpty()) v.err = "播放列表为空";
                return v;
            }

            v.id = s(get(r, "id"));
            v.title = s(get(r, "title"));
            v.author = s(get(r, "author"));
            v.thumb = s(get(r, "thumb"));
            v.durationSec = l(get(r, "duration"));
            v.videoUrl = s(get(r, "video_url"));
            v.audioUrl = s(get(r, "audio_url"));
            if(v.videoUrl.length() == 0 && v.id.length() > 0){
                // 服务器已把合并好的 mp4 存进缓存，走 /api/yt/file 中转（支持 Range）
                v.videoUrl = srv + "/api/yt/file?id=" + v.id;
            }
            if(v.videoUrl.length() == 0) v.err = "没有拿到视频地址";
            return v;
        }catch(Throwable t){
            Log.i(TAG, "YT_PARSE_ERR " + t);
            v.err = "解析异常: " + t;
            return v;
        }
    }

    /** 带 HMAC 鉴权的 GET（服务器 API：永不套用户代理） */
    static String httpGetApi(Context ctx, String url, int timeout){
        HttpURLConnection c = null;
        try{
            // 服务器 API 强制直连：用户代理可能不通（YouTube 服务器必须能直连）
            c = (HttpURLConnection) new URL(url).openConnection(java.net.Proxy.NO_PROXY);
            c.setRequestProperty("User-Agent", UA);
            c.setRequestProperty("Accept", "application/json,*/*");
            // HMAC 鉴权
            try{
                String ts = String.valueOf(System.currentTimeMillis() / 1000);
                String nonce = java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 12);
                String path = "/api/yt/parse";
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
            }catch(Throwable t){
                Log.i(TAG, "YT_SIGN_ERR " + t);
            }
            c.setConnectTimeout(30000);
            c.setReadTimeout(timeout);
            int code = c.getResponseCode();
            if(code < 200 || code >= 300){
                Log.i(TAG, "YT_HTTP " + code + " " + url);
                return null;
            }
            java.io.InputStream in = c.getInputStream();
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            in.close();
            return new String(bo.toByteArray(), "UTF-8");
        }catch(Throwable t){
            Log.i(TAG, "YT_HTTP_ERR " + url + " " + t);
            return null;
        }finally{
            try{ if(c != null) c.disconnect(); }catch(Throwable t){}
        }
    }

    static Object get(java.util.Map m, String k){
        return m == null ? null : m.get(k);
    }

    static String s(Object o){
        if(o == null) return "";
        if(o instanceof Double){
            double d = (Double) o;
            if(d == Math.floor(d) && !Double.isInfinite(d)) return String.valueOf((long) d);
        }
        return String.valueOf(o);
    }

    static long l(Object o){
        if(o == null) return 0;
        if(o instanceof Number) return ((Number) o).longValue();
        try{ return Long.parseLong(String.valueOf(o).trim()); }catch(Throwable t){ return 0; }
    }
}