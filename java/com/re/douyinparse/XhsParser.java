package com.re.douyinparse;

import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 小红书解析（借鉴 ucmao/media-parser 的 xiaohongshu 逆向方案）。
 *
 * 页面 window.__INITIAL_STATE__ 里就有完整笔记数据（PC 结构与移动 H5 结构双兼容）。
 * 免登录可解析大部分笔记；高频访问会被 302 到 /login，此时需配置 Cookie（设置页支持）。
 */
public class XhsParser {

    static final String TAG = "DYP";

    public static class XhsNote {
        public boolean ok = false;
        public String err = "";
        public String id = "";
        public String title = "";
        public String desc = "";
        public String author = "";
        public String avatar = "";
        public String cover = "";
        public long createdAt = 0;
        public long durMs = 0;
        public long likeCount = 0;
        public String playUrl = "";
        public List<String[]> gears = new ArrayList<String[]>();
        public List<String> images = new ArrayList<String>();

        public boolean hasVideo(){ return playUrl != null && playUrl.length() > 0; }
        public boolean hasImages(){ return images != null && images.size() > 0; }
    }

    public static boolean isXhsUrl(String u){
        if(u == null) return false;
        String s = u.toLowerCase(java.util.Locale.US);
        return s.contains("xiaohongshu.com") || s.contains("xhslink.com")
            || s.contains("xhslink.cn") || s.contains("xhs.com");
    }

    static final String UA_PC =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36";
    static final String UA_M =
        "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1";

    /** 返回 [最终URL, HTML]，失败返回 null */
    static String[] fetch(String url, String ua, String cookie){
        java.net.HttpURLConnection uc = null;
        try{
            uc = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
            uc.setRequestMethod("GET");
            uc.setInstanceFollowRedirects(false);   // 自己判 302 到 login
            uc.setConnectTimeout(10000);
            uc.setReadTimeout(15000);
            uc.setRequestProperty("User-Agent", ua);
            uc.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
            uc.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9");
            uc.setRequestProperty("Referer", "https://www.xiaohongshu.com/");
            if(cookie != null && cookie.length() > 0) uc.setRequestProperty("Cookie", cookie);
            int code = uc.getResponseCode();
            String loc = uc.getHeaderField("Location");
            Log.i(TAG, "XHS_CODE " + code + (loc != null ? (" loc=" + loc) : ""));
            if(code == 301 || code == 302 || code == 303 || code == 307){
                if(loc != null && loc.length() > 0){
                    return new String[]{ loc, null };
                }
                return null;
            }
            if(code < 200 || code >= 400) return null;
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            java.io.InputStream in = uc.getInputStream();
            byte[] buf = new byte[8192];
            int n;
            while((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            in.close();
            byte[] raw = bo.toByteArray();
            if(raw.length > 2 && (raw[0] & 0xFF) == 0x1F && (raw[1] & 0xFF) == 0x8B){
                try{
                    java.util.zip.GZIPInputStream gz = new java.util.zip.GZIPInputStream(
                        new java.io.ByteArrayInputStream(raw));
                    java.io.ByteArrayOutputStream o2 = new java.io.ByteArrayOutputStream();
                    byte[] b2 = new byte[8192];
                    int n2;
                    while((n2 = gz.read(b2)) > 0) o2.write(b2, 0, n2);
                    gz.close();
                    raw = o2.toByteArray();
                }catch(Throwable t){}
            }
            return new String[]{ url, new String(raw, "UTF-8") };
        }catch(Throwable t){
            Log.i(TAG, "XHS_HTTP_ERR " + t);
            return null;
        }finally{
            try{ if(uc != null) uc.disconnect(); }catch(Throwable t){}
        }
    }

    /** 主入口 */
    public static XhsNote resolve(String url){
        return resolve(url, "");
    }

    /** 带 Cookie 版（web_session 等），登录后可绕过 300031 风控 */
    public static XhsNote resolve(String url, String cookie){
        XhsNote n = new XhsNote();
        try{
            String realUrl = url;
            if(cookie == null) cookie = "";
            Log.i(TAG, "XHS_RESOLVE cookieLen=" + cookie.length());
            // 小红书现在强制登录，两种页面形态都试一遍
            if(realUrl.contains("/explore/")){
                String alt = realUrl.replace("/explore/", "/discovery/item/");
                Log.i(TAG, "XHS_ALT " + alt);
            }
            String html = null;
            // 短链先解一次
            for(int i = 0; i < 3; i++){
                String[] r = fetch(realUrl, i == 0 ? UA_PC : UA_M, cookie);
                if(r == null){ break; }
                if(r[1] != null){ html = r[1]; realUrl = r[0]; break; }
                // 302：跟一次
                realUrl = r[0];
                Log.i(TAG, "XHS_REDIR " + realUrl);
                if(realUrl.contains("/login")){
                    n.err = "小红书触发风控（需配置 Cookie）";
                    return n;
                }
                if(realUrl.contains("error_code=300031")){
                    n.err = "小红书风控拦截（error_code=300031），稍后重试或换网络";
                    Log.i(TAG, "XHS_RISK 300031");
                    return n;
                }
                if(realUrl.contains("error_code=")){
                    n.err = "小红书返回错误：" + realUrl;
                    Log.i(TAG, "XHS_ERRCODE " + realUrl);
                    return n;
                }
                if(realUrl.contains("/404") || realUrl.contains("note_error")){
                    n.err = "笔记已被删除或不存在";
                    return n;
                }
            }
            if(html == null || html.length() < 500){
                n.err = "小红书页面拉取失败";
                Log.i(TAG, "XHS_FAIL len=" + (html == null ? -1 : html.length()));
                return n;
            }
            Log.i(TAG, "XHS_HTML len=" + html.length());
            // 抽 __INITIAL_STATE__
            java.util.regex.Matcher m = java.util.regex.Pattern.compile(
                "window\\\\.__INITIAL_STATE__\\\\s*=\\\\s*(\\\\{.*?\\\\})</script>",
                java.util.regex.Pattern.DOTALL).matcher(html);
            if(!m.find()){
                m = java.util.regex.Pattern.compile(
                    "window\\\\.__INITIAL_STATE__\\\\s*=\\\\s*(\\\\{.*\\\\})",
                    java.util.regex.Pattern.DOTALL).matcher(html);
                if(!m.find()){
                    n.err = "未找到页面数据";
                    return n;
                }
            }
            String js = m.group(1);
            // 小红书 JS 对象里有 undefined / new Map(...) 等非法 JSON
            js = js.replaceAll("\\\\bundefined\\\\b", "null")
                   .replaceAll("new\\\\s+Map\\\\s*\\\\([^)]*\\\\)", "{}")
                   .replaceAll("new\\\\s+Set\\\\s*\\\\([^)]*\\\\)", "[]")
                   .replaceAll("new\\\\s+Date\\\\s*\\\\([^)]*\\\\)", "null");
            JSONObject root = new JSONObject(js);
            JSONObject note = extractNote(root);
            if(note == null){
                n.err = "小红书现已强制登录才给笔记详情（未登录返回空数据）";
                return n;
            }
            parseNote(n, note);
            n.ok = n.hasVideo() || n.hasImages() || n.title.length() > 0;
            Log.i(TAG, "XHS_OK title=" + n.title + " v=" + n.hasVideo()
                + " imgs=" + n.images.size() + " author=" + n.author);
            if(!n.ok && n.err.length() == 0) n.err = "未取到笔记内容";
        }catch(Throwable t){
            n.err = String.valueOf(t);
            Log.i(TAG, "XHS_ERR " + t);
        }
        return n;
    }

    /** 兼容 PC / 移动 H5 / 兜底三种结构 */
    static JSONObject extractNote(JSONObject root){
        try{
            JSONObject noteRoot = root.optJSONObject("note");
            if(noteRoot != null){
                String fid = noteRoot.optString("firstNoteId", "");
                JSONObject map = noteRoot.optJSONObject("noteDetailMap");
                if(map != null){
                    if(fid.length() > 0){
                        JSONObject item = map.optJSONObject(fid);
                        if(item != null){
                            JSONObject nn = item.optJSONObject("note");
                            if(nn != null && nn.length() > 0) return nn;
                        }
                    }
                    // 没有 firstNoteId 就拿第一个
                    Iterator<String> it = map.keys();
                    while(it.hasNext()){
                        JSONObject item = map.optJSONObject(it.next());
                        if(item == null) continue;
                        JSONObject nn = item.optJSONObject("note");
                        if(nn != null && nn.length() > 0) return nn;
                    }
                }
            }
            JSONObject nd = root.optJSONObject("noteData");
            if(nd != null){
                JSONObject d = nd.optJSONObject("data");
                if(d != null){
                    JSONObject n2 = d.optJSONObject("noteData");
                    if(n2 != null && n2.length() > 0) return n2;
                }
                JSONObject n3 = nd.optJSONObject("note");
                if(n3 != null && n3.length() > 0) return n3;
            }
            if(noteRoot != null){
                JSONObject n4 = noteRoot.optJSONObject("note");
                if(n4 != null && n4.length() > 0) return n4;
            }
        }catch(Throwable t){
            Log.i(TAG, "XHS_EXTRACT_ERR " + t);
        }
        return null;
    }

    static void parseNote(XhsNote n, JSONObject note){
        n.id = note.optString("noteId", note.optString("id", ""));
        n.title = note.optString("title", "");
        n.desc = note.optString("desc", note.optString("description", ""));
        if(n.title.length() == 0) n.title = n.desc;
        long ct = note.optLong("time", note.optLong("createTime", 0));
        if(ct > 0) n.createdAt = ct < 100000000000L ? ct * 1000L : ct;
        // 互动数据
        JSONObject ii = note.optJSONObject("interactInfo");
        if(ii != null){
            n.likeCount = numOf(ii.optString("likedCount", "0"));
        }
        // 作者
        JSONObject u = note.optJSONObject("user");
        if(u != null){
            n.author = u.optString("nickname", u.optString("nickName", ""));
            n.avatar = ensureHttps(u.optString("avatar", u.optString("image", "")));
        }
        // 图集
        JSONArray il = note.optJSONArray("imageList");
        if(il != null){
            for(int i = 0; i < il.length(); i++){
                JSONObject img = il.optJSONObject(i);
                if(img == null) continue;
                String u2 = cleanImage(img);
                if(u2.length() > 0) n.images.add(u2);
                if(n.cover.length() == 0) n.cover = u2;
            }
        }
        // 视频
        JSONObject v = note.optJSONObject("video");
        if(v != null){
            n.playUrl = extractVideoUrl(v);
            if(n.playUrl.length() > 0){
                n.gears.add(new String[]{ "原画", n.playUrl, "0", "0" });
            }
            // 视频封面
            if(n.cover.length() == 0){
                n.cover = ensureHttps(firstNested(v, new String[]{
                    "consumer", "originVideoKey" }));
            }
            JSONObject media = v.optJSONObject("media");
            if(media != null){
                JSONObject stream = media.optJSONObject("stream");
                if(stream != null){
                    long d = 0;
                    JSONArray h264 = stream.optJSONArray("h264");
                    if(h264 != null && h264.length() > 0){
                        JSONObject h0 = h264.optJSONObject(0);
                        if(h0 != null) d = h0.optLong("duration", 0);
                    }
                    if(d > 0) n.durMs = d;
                }
            }
        }
        if(n.cover.length() == 0 && n.images.size() > 0) n.cover = n.images.get(0);
    }

    /** 从 video 节点挖直链（优先 originVideoKey，其次 mediaV2，再次 media.stream） */
    static String extractVideoUrl(JSONObject v){
        try{
            // 1) consumer.originVideoKey
            JSONObject consumer = v.optJSONObject("consumer");
            String ok = "";
            if(consumer != null){
                ok = consumer.optString("originVideoKey",
                     consumer.optString("origin_video_key", ""));
            }
            if(ok.length() == 0) ok = v.optString("originVideoKey", v.optString("origin_video_key", ""));
            if(ok.length() > 0){
                String u = ok.startsWith("http") ? ok : ("https://sns-video-bd.xhscdn.com/" + strip(ok));
                return u;
            }
            // 2) mediaV2（字符串化 JSON）
            String mv2 = v.optString("mediaV2", "");
            if(mv2.length() > 10){
                try{
                    JSONObject m2 = new JSONObject(mv2);
                    String k2 = deepFindStr(m2, new String[]{ "originVideoKey", "origin_video_key", "masterUrl" });
                    if(k2 != null && k2.length() > 0){
                        return k2.startsWith("http") ? k2 : ("https://sns-video-bd.xhscdn.com/" + strip(k2));
                    }
                }catch(Throwable t){}
            }
            // 3) media.stream.h264[0].masterUrl
            JSONObject media = v.optJSONObject("media");
            if(media != null){
                JSONObject stream = media.optJSONObject("stream");
                if(stream != null){
                    JSONArray h264 = stream.optJSONArray("h264");
                    if(h264 != null && h264.length() > 0){
                        JSONObject h0 = h264.optJSONObject(0);
                        if(h0 != null){
                            String u = h0.optString("masterUrl", "");
                            if(u.length() > 0) return u;
                        }
                    }
                }
            }
            // 4) 全局递归兜底
            String r = deepFindStr(v, new String[]{ "masterUrl", "backupUrls", "url" });
            if(r != null && r.startsWith("http")) return r;
        }catch(Throwable t){
            Log.i(TAG, "XHS_VIDEO_ERR " + t);
        }
        return "";
    }

    /** 小红书的 m1210x1613_xxx!nc_n_webp_mw_1 这类后缀要去掉拿原图 */
    static String cleanImage(JSONObject img){
        try{
            // 优先 infoList 里的原图
            JSONArray infos = img.optJSONArray("infoList");
            String best = "";
            if(infos != null){
                for(int i = 0; i < infos.length(); i++){
                    JSONObject o = infos.optJSONObject(i);
                    if(o == null) continue;
                    String iu = o.optString("url", "");
                    if(iu.length() == 0) continue;
                    String imgFmt = o.optString("imageScene", "");
                    // WB_DFT / CRD_WM_JPG / CRD_PRV_WEBP 等，优先取没有水印的
                    if(imgFmt.contains("WM")) continue;
                    if(best.length() == 0) best = iu;
                }
            }
            if(best.length() == 0){
                best = img.optString("urlDefault",
                       img.optString("url", img.optString("urlPre", "")));
            }
            if(best.length() == 0) return "";
            return ensureHttps(best);
        }catch(Throwable t){ return ""; }
    }

    static String firstNested(JSONObject o, String[] path){
        JSONObject cur = o;
        for(int i = 0; i < path.length; i++){
            if(cur == null) return "";
            if(i == path.length - 1) return cur.optString(path[i], "");
            cur = cur.optJSONObject(path[i]);
        }
        return "";
    }

    static String deepFindStr(Object o, String[] keys){
        if(o == null) return null;
        if(o instanceof JSONObject){
            JSONObject j = (JSONObject)o;
            for(int i = 0; i < keys.length; i++){
                String v = j.optString(keys[i], "");
                if(v.length() > 6 && !v.startsWith("{") && !v.startsWith("[")) return v;
            }
            Iterator<String> it = j.keys();
            while(it.hasNext()){
                Object c = j.opt(it.next());
                if(c instanceof JSONObject || c instanceof JSONArray){
                    String r = deepFindStr(c, keys);
                    if(r != null) return r;
                }
            }
        }else if(o instanceof JSONArray){
            JSONArray a = (JSONArray)o;
            for(int i = 0; i < a.length(); i++){
                String r = deepFindStr(a.opt(i), keys);
                if(r != null) return r;
            }
        }
        return null;
    }

    static String strip(String s){
        if(s == null) return "";
        String r = s.trim();
        while(r.startsWith("/")) r = r.substring(1);
        return r;
    }

    static String ensureHttps(String u){
        if(u == null) return "";
        String r = u.replace("\\\\u002F", "/").replace("\\\\/", "/");
        if(r.startsWith("//")) return "https:" + r;
        return r;
    }

    static long numOf(String s){
        if(s == null) return 0;
        s = s.replace(",", "").trim();
        if(s.length() == 0) return 0;
        try{
            if(s.endsWith("万")) return (long)(Double.parseDouble(s.substring(0, s.length() - 1)) * 10000);
            if(s.endsWith("w")) return (long)(Double.parseDouble(s.substring(0, s.length() - 1)) * 10000);
            if(s.endsWith("亿")) return (long)(Double.parseDouble(s.substring(0, s.length() - 1)) * 100000000);
            return (long)Double.parseDouble(s);
        }catch(Throwable t){ return 0; }
    }

    /** 输出 render() 认的 i_* JSON */
    public static String toJson(XhsNote n){
        StringBuilder b = new StringBuilder();
        b.append("{");
        b.append("\"i_platform\":\"小红书\",");
        kv(b, "i_id", n.id.length() > 0 ? n.id : ("xhs_" + Math.abs(n.title.hashCode())));
        if(n.author.length() > 0) kv(b, "i_author", n.author);
        if(n.title.length() > 0) kv(b, "i_desc", n.title);
        if(n.createdAt > 0) kv(b, "i_time", String.valueOf(n.createdAt));
        if(n.durMs > 0) kv(b, "i_dur", String.valueOf(n.durMs));
        if(n.cover.length() > 0) kv(b, "i_cover", n.cover);
        if(n.likeCount > 0) kv(b, "i_stats", "赞 " + n.likeCount);
        if(n.hasVideo()) kv(b, "i_play", n.playUrl);
        if(n.hasImages()){
            StringBuilder im = new StringBuilder();
            for(int i = 0; i < n.images.size(); i++){
                if(i > 0) im.append("|");
                im.append(n.images.get(i));
            }
            kv(b, "i_imgs", im.toString());
        }
        String s = b.toString();
        if(s.endsWith(",")) s = s.substring(0, s.length() - 1);
        return s + "}";
    }

    static void kv(StringBuilder b, String k, String v){
        if(v == null) v = "";
        b.append("\"").append(k).append("\":\"").append(esc(v)).append("\",");
    }

    /** 返回 [imgs, play, cover]；imgs 用 | 分隔 */
    public static String[] toMedia(XhsNote n){
        StringBuilder im = new StringBuilder();
        for(int i = 0; i < n.images.size(); i++){
            if(i > 0) im.append("|");
            im.append(n.images.get(i));
        }
        return new String[]{ im.toString(), n.playUrl == null ? "" : n.playUrl,
                             n.cover == null ? "" : n.cover };
    }


    static String esc(String s){
        if(s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "").replace("\t", " ");
    }


    /** 输出 renderCards() 认的「【标签】值」文本 */
    public static String toText(XhsNote n){
        StringBuilder b = new StringBuilder();
        b.append("【平台】小红书\n");
        if(n.id.length() > 0) b.append("【作品ID】").append(n.id).append("\n");
        if(n.title.length() > 0) b.append("【标题】").append(n.title).append("\n");
        if(n.author.length() > 0) b.append("【作者】").append(n.author).append("\n");
        if(n.desc.length() > 0 && !n.desc.equals(n.title)) b.append("【描述】").append(n.desc).append("\n");
        if(n.createdAt > 0){
            java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm");
            f.setTimeZone(java.util.TimeZone.getTimeZone("Asia/Shanghai"));
            b.append("【发布时间】").append(f.format(new java.util.Date(n.createdAt))).append("\n");
        }
        if(n.durMs > 0) b.append("【时长】").append(n.durMs / 1000).append(" 秒\n");
        if(n.likeCount > 0) b.append("【数据】赞 ").append(n.likeCount).append("\n");
        if(n.cover.length() > 0) b.append("【封面】").append(n.cover).append("\n");
        if(n.hasVideo()){
            b.append("【视频直链】").append(n.playUrl).append("\n");
        }
        if(n.hasImages()){
            b.append("【图集】").append(n.images.size()).append(" 张\n");
            for(int i = 0; i < n.images.size(); i++){
                b.append("【图").append(i + 1).append("】").append(n.images.get(i)).append("\n");
            }
        }
        if(n.hasVideo()) b.append("【音频下载】点下方「提取音频」，从视频中提取音轨为 MP3\n");
        return b.toString();
    }
}