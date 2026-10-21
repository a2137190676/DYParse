package com.re.douyinparse;

import android.content.Context;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 快手分享页解析器（纯 HTTP，不走 WebView）。
 *
 * 链路：
 *   v.kuaishou.com/{key}  --302-->  v.m.chenzhongtech.com/fw/photo/{photoId}?...
 *
 * 页面 HTML 里内嵌 window.INIT_STATE，难点在于顶层 key 被"逐字符 -1"混淆了
 * （tusjoh -> string，qipup -> photo），但所有 value 都是明文。
 * 所以这里完全按结构递归找「含 photoId + mainMvUrls/manifest 的那一个节点」，
 * 不依赖任何 key 名，页面改 key 也不影响。
 *
 * 取到的字段：
 *   photoId       作品 ID
 *   caption       标题/描述
 *   userName      作者
 *   timestamp     发布时间（毫秒）
 *   duration      时长（毫秒）
 *   viewCount     播放量
 *   mainMvUrls[]  无水印视频直链（多 CDN）
 *   coverUrls[]   封面
 *   soundTrack.audioUrls[]  独立音轨 m4a（转 MP3 比抽视频音轨快）
 *   atlas.list[] / imageUrls[]  图集
 */
public class KsParser {

    static final String TAG = "DYP";
    static final String UA = "Mozilla/5.0 (Linux; Android 12; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36";
    static final String REFERER = "https://www.kuaishou.com/";

    /** 解析结果 */
    public static class KsVideo {
        public String photoId = "";
        public String caption = "";
        public String author = "";
        public String playUrl = "";      // 无水印视频直链
        public String coverUrl = "";
        public String musicUrl = "";     // 独立音轨 m4a
        public String musicTitle = "";
        public String musicAuthor = "";
        public long durationMs = 0;
        public long timestampMs = 0;
        public long viewCount = 0;
        public long likeCount = 0;
        public String pageUrl = "";
        public String err = "";
        public final List<String> images = new ArrayList<String>();   // 图集

        public boolean hasVideo(){ return playUrl != null && playUrl.length() > 0; }
        public boolean hasImages(){ return !images.isEmpty(); }
        public boolean ok(){ return hasVideo() || hasImages(); }

        /**
         * 转成 MainActivity.render() 认识的 JSON 字段：
         *   i_id / i_author / i_desc / i_time / i_dur / i_cover / i_play / i_ws / i_imgs
         *   i_musicTitle / i_musicAuthor / i_music / i_platform / i_stats
         */
        public String toResultJson(){
            StringBuilder b = new StringBuilder(2048);
            b.append('{');
            kv(b, "i_id", photoId);
            kv(b, "i_author", author);
            kv(b, "i_desc", caption);
            kv(b, "i_time", timestampMs > 0 ? String.valueOf(timestampMs) : "");
            kv(b, "i_dur", durationMs > 0 ? String.valueOf(durationMs) : "");
            kv(b, "i_cover", coverUrl);
            if(hasImages()){
                StringBuilder im = new StringBuilder();
                for(String u : images){
                    if(im.length() > 0) im.append('|');
                    im.append(u);
                }
                kv(b, "i_imgs", im.toString());
            } else {
                // 快手直链本身就是无水印，不再重复输出 i_ws
                kv(b, "i_play", playUrl);
            }
            kv(b, "i_musicTitle", musicTitle);
            kv(b, "i_musicAuthor", musicAuthor);
            kv(b, "i_music", musicUrl);
            kv(b, "i_platform", "快手");
            StringBuilder st = new StringBuilder();
            if(viewCount > 0) st.append("播放 ").append(viewCount);
            if(likeCount > 0){
                if(st.length() > 0) st.append("  ");
                st.append("赞 ").append(likeCount);
            }
            kv(b, "i_stats", st.toString());
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

    // ================= 对外入口 =================

    /** 短链/长链统一入口：跟随跳转 -> 抓页 -> 解析。失败返回带 err 的对象，不返回 null */
    public static KsVideo resolve(Context ctx, String url){
        KsVideo v = new KsVideo();
        v.pageUrl = url;
        if(url == null || url.length() == 0){ v.err = "空链接"; return v; }
        try{
            String real = url;
            if(isShort(url)){
                String r = followRedirect(ctx, url);
                if(r != null && r.length() > 0) real = r;
                Log.i(TAG, "KS_REAL " + trim(real));
            }
            v.pageUrl = real;
            String html = httpGet(ctx, real);
            if(html == null || html.length() < 500){
                v.err = "页面抓取失败";
                return v;
            }
            Log.i(TAG, "KS_HTML len=" + html.length() + " hasInit=" + (html.indexOf("INIT_STATE") >= 0));
            parseHtml(html, v);
            if(!v.ok() && v.err.length() == 0){
                if(html.indexOf("INIT_STATE") < 0){
                    v.err = "页面里没有 INIT_STATE，可能命中风控或作品已删除";
                } else {
                    v.err = "INIT_STATE 里没找到 photo 节点";
                }
            }
        }catch(Throwable t){
            Log.i(TAG, "KS_RESOLVE_ERR " + t);
            v.err = "" + t;
        }
        return v;
    }

    public static boolean isShort(String url){
        if(url == null) return false;
        return url.contains("v.kuaishou.com") || url.contains("v.m.chenzhongtech.com/s/");
    }

    /** 该 URL 是否属于快手域（含其 CDN） */
    public static boolean isKsUrl(String url){
        if(url == null) return false;
        String u = url.toLowerCase();
        return u.contains("kuaishou.com") || u.contains("chenzhongtech.com")
            || u.contains("gifshow.com") || u.contains("kuaishouapp.com")
            || u.contains("kwimgs.com") || u.contains("yximgs.com")
            || u.contains("ndcimgs.com") || u.contains("wskwai.com");
    }

    /** 快手 CDN 要求的 Referer；非快手返回 null 交由调用方决定 */
    public static String refererFor(String url){
        return isKsUrl(url) ? REFERER : null;
    }

    // ================= 网络 =================

    static String followRedirect(Context ctx, String url){
        String cur = url;
        for(int i = 0; i < 6; i++){
            HttpURLConnection c = null;
            try{
                c = MainActivity.openConn(ctx, cur);
                c.setInstanceFollowRedirects(false);
                c.setRequestProperty("User-Agent", UA);
                c.setConnectTimeout(12000);
                c.setReadTimeout(15000);
                int code = c.getResponseCode();
                if(code >= 300 && code < 400){
                    String loc = c.getHeaderField("Location");
                    if(loc == null || loc.length() == 0) return cur;
                    if(loc.startsWith("/")){
                        URL base = new URL(cur);
                        String port = base.getPort() > 0 ? (":" + base.getPort()) : "";
                        loc = base.getProtocol() + "://" + base.getHost() + port + loc;
                    }
                    Log.i(TAG, "KS_REDIR " + code + " -> " + trim(loc));
                    cur = loc;
                    continue;
                }
                return cur;
            }catch(Throwable t){
                Log.i(TAG, "KS_REDIR_ERR " + t);
                return cur;
            }finally{
                try{ if(c != null) c.disconnect(); }catch(Throwable t){}
            }
        }
        return cur;
    }

    static String httpGet(Context ctx, String url){
        HttpURLConnection c = null;
        InputStream in = null;
        try{
            c = MainActivity.openConn(ctx, url);
            c.setInstanceFollowRedirects(true);
            c.setRequestProperty("User-Agent", UA);
            c.setRequestProperty("Referer", REFERER);
            c.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
            c.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9");
            c.setConnectTimeout(15000);
            c.setReadTimeout(20000);
            int code = c.getResponseCode();
            Log.i(TAG, "KS_HTTP " + code);
            if(code < 200 || code >= 300) return null;
            in = c.getInputStream();
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n; long got = 0;
            while((n = in.read(buf)) > 0){
                bo.write(buf, 0, n);
                got += n;
                if(got > 6L * 1024 * 1024) break;
            }
            return new String(bo.toByteArray(), "UTF-8");
        }catch(Throwable t){
            Log.i(TAG, "KS_HTTP_ERR " + t);
            return null;
        }finally{
            try{ if(in != null) in.close(); }catch(Throwable t){}
            try{ if(c != null) c.disconnect(); }catch(Throwable t){}
        }
    }

    // ================= HTML -> JSON =================

    /** 从 HTML 里抠出 window.INIT_STATE 的 JSON 对象（括号配平，跳过字符串内括号） */
    public static String extractInitState(String html){
        if(html == null) return null;
        int p = html.indexOf("INIT_STATE");
        if(p < 0) return null;
        int st = html.indexOf('{', p);
        if(st < 0) return null;
        int depth = 0;
        boolean inStr = false;
        boolean esc = false;
        for(int i = st; i < html.length(); i++){
            char c = html.charAt(i);
            if(inStr){
                if(esc){ esc = false; }
                else if(c == '\\'){ esc = true; }
                else if(c == '"'){ inStr = false; }
                continue;
            }
            if(c == '"'){ inStr = true; continue; }
            if(c == '{') depth++;
            else if(c == '}'){
                depth--;
                if(depth == 0) return html.substring(st, i + 1);
            }
        }
        return null;
    }

    static void parseHtml(String html, KsVideo v){
        String json = extractInitState(html);
        if(json == null || json.length() < 60){
            v.err = "INIT_STATE 解析失败";
            return;
        }
        Object root;
        try{
            root = new MainActivity.JP(json).parse();
        }catch(Throwable t){
            Log.i(TAG, "KS_JP_ERR " + t);
            v.err = "INIT_STATE JSON 解析异常";
            return;
        }
        Map photo = findPhoto(root);
        if(photo == null){
            // 兜底：某些版本 photo 里没有 manifest，用 caption 反查
            photo = findPhotoLoose(root);
        }
        if(photo == null){ return; }

        v.photoId   = s(photo.get("photoId"));
        v.caption   = s(photo.get("caption"));
        v.author    = s(photo.get("userName"));
        v.durationMs = l(photo.get("duration"));
        v.timestampMs = l(photo.get("timestamp"));
        v.viewCount = l(photo.get("viewCount"));
        v.likeCount = l(photo.get("likeCount"));

        // 视频直链（多 CDN，取第一个）
        v.playUrl = firstUrlOf(photo.get("mainMvUrls"));
        // 封面
        v.coverUrl = firstUrlOf(photo.get("coverUrls"));
        if(v.coverUrl.length() == 0) v.coverUrl = firstUrlOf(photo.get("webpCoverUrls"));

        // 独立音轨
        Object stObj = photo.get("soundTrack");
        if(stObj instanceof Map){
            Map st = (Map) stObj;
            v.musicTitle  = s(st.get("name"));
            v.musicAuthor = s(st.get("artist"));
            v.musicUrl    = firstUrlOf(st.get("audioUrls"));
        }

        // 图集：photoType=IMAGE 或 atlas 非空
        String photoType = s(photo.get("photoType")).toUpperCase();
        List<String> atlas = atlasUrls(photo.get("atlas"));
        if(atlas.isEmpty() && photoType.contains("IMAGE")){
            atlas = atlasUrls(photo.get("ext_params"));   // 兜底：ext_params.atlas
        }
        if(!atlas.isEmpty()){
            v.images.addAll(atlas);
        } else if(photoType.contains("IMAGE") || photoType.contains("ATLAS")){
            List<String> iu = plainUrls(photo.get("imageUrls"));
            if(!iu.isEmpty()) v.images.addAll(iu);
        }

        // 纯图集不该再有视频；反之图集解析成功就清掉视频地址，避免 UI 走错分支
        if(v.hasImages()) v.playUrl = "";

        Log.i(TAG, "KS_PHOTO id=" + v.photoId + " type=" + photoType
                + " video=" + (v.hasVideo() ? "1" : "0") + " imgs=" + v.images.size()
                + " music=" + (v.musicUrl.length() > 0 ? "1" : "0"));
    }

    /** 递归找作品节点：有 photoId，且带 mainMvUrls / manifest / atlas 之一 */
    static Map findPhoto(Object o){
        if(o instanceof Map){
            Map m = (Map) o;
            if(m.get("photoId") != null
                    && (m.get("mainMvUrls") != null || m.get("manifest") != null || m.get("atlas") != null)){
                return m;
            }
            for(Object val : m.values()){
                Map r = findPhoto(val);
                if(r != null) return r;
            }
        } else if(o instanceof List){
            for(Object val : (List) o){
                Map r = findPhoto(val);
                if(r != null) return r;
            }
        }
        return null;
    }

    /** 放宽条件：photoId + caption，且不能是 soundTrack（它也有 photoId） */
    static Map findPhotoLoose(Object o){
        if(o instanceof Map){
            Map m = (Map) o;
            if(m.get("photoId") != null && m.get("caption") != null && m.get("soundTrack") == null){
                return m;
            }
            for(Object val : m.values()){
                Map r = findPhotoLoose(val);
                if(r != null) return r;
            }
        } else if(o instanceof List){
            for(Object val : (List) o){
                Map r = findPhotoLoose(val);
                if(r != null) return r;
            }
        }
        return null;
    }

    /** atlas 结构：{list:[{url:"..."}], ...}；也接受 {atlas:{list:[...]}} 嵌套一层 */
    static List<String> atlasUrls(Object atlas){
        List<String> out = new ArrayList<String>();
        if(atlas == null) return out;
        Object listObj = null;
        if(atlas instanceof Map){
            Map m = (Map) atlas;
            listObj = m.get("list");
            if(listObj == null && m.get("atlas") != null) return atlasUrls(m.get("atlas"));
        } else if(atlas instanceof List){
            listObj = atlas;
        }
        if(listObj instanceof List){
            for(Object it : (List) listObj){
                if(it instanceof Map){
                    Map im = (Map) it;
                    String u = s(im.get("url"));
                    if(u.length() == 0) u = firstUrlOf(im.get("cdn"));
                    if(u.length() == 0) u = firstUrlOf(im.get("urls"));
                    if(u.startsWith("http")) out.add(u);
                } else if(it instanceof String){
                    String u = (String) it;
                    if(u.startsWith("http")) out.add(u);
                }
            }
        }
        return out;
    }

    /** [{url:"..."},...] 或 ["http...", ...] */
    static List<String> plainUrls(Object arr){
        List<String> out = new ArrayList<String>();
        if(!(arr instanceof List)) return out;
        for(Object o : (List) arr){
            if(o instanceof Map){
                String u = s(((Map) o).get("url"));
                if(u.startsWith("http") && !out.contains(u)) out.add(u);
            } else if(o instanceof String){
                String u = (String) o;
                if(u.startsWith("http") && !out.contains(u)) out.add(u);
            }
        }
        return out;
    }

    /** [{url:"..."},...] 取第一个 */
    static String firstUrlOf(Object arr){
        if(arr instanceof List){
            for(Object o : (List) arr){
                if(o instanceof Map){
                    Object u = ((Map) o).get("url");
                    if(u != null && u.toString().startsWith("http")) return u.toString();
                } else if(o instanceof String){
                    String u = (String) o;
                    if(u.startsWith("http")) return u;
                }
            }
        } else if(arr instanceof String){
            return (String) arr;
        }
        return "";
    }

    static String s(Object o){ return o == null ? "" : o.toString(); }

    static long l(Object o){
        if(o instanceof Number) return ((Number) o).longValue();
        try{ return Long.parseLong(s(o).trim()); }catch(Throwable t){ return 0; }
    }

    static String trim(String s){
        if(s == null) return "";
        return s.length() > 160 ? s.substring(0, 160) + "..." : s;
    }
}
