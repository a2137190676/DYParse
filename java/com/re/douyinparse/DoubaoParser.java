package com.re.douyinparse;

import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/**
 * 豆包（doubao.com）解析（借鉴 ucmao/media-parser 的 doubao 逆向方案）。
 *
 * 分享页路径：
 *  - /thread/{id}   对话分享（图片 / 视频）
 *  - /video-sharing 视频分享
 *  - /music-sharing 音乐分享
 *
 * 视频主要靠 samantha/media/get_play_info（POST {"key": vid}），比 FPLAY 解密路径简单稳定；
 * FPLAY 那条（alice/resource/get_video_model）需要额外解密，暂不实现。
 */
public class DoubaoParser {

    static final String TAG = "DYP";
    static final String PLAY_INFO_API = "https://www.doubao.com/samantha/media/get_play_info";

    public static class DbItem {
        public boolean ok = false;
        public String err = "";
        public String id = "";
        public String title = "";
        public String desc = "";
        public String author = "";
        public String cover = "";
        public String playUrl = "";
        public String audioUrl = "";
        public List<String> images = new ArrayList<String>();
        public List<String[]> gears = new ArrayList<String[]>();

        public boolean hasVideo(){ return playUrl != null && playUrl.length() > 0; }
        public boolean hasImages(){ return images != null && images.size() > 0; }
        public boolean hasAudio(){ return audioUrl != null && audioUrl.length() > 0; }
    }

    public static boolean isDoubaoUrl(String u){
        if(u == null) return false;
        return u.toLowerCase().contains("doubao.com");
    }

    static final String UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36";

    static String get(String url){
        java.net.HttpURLConnection uc = null;
        try{
            uc = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
            uc.setRequestMethod("GET");
            uc.setInstanceFollowRedirects(true);
            uc.setConnectTimeout(12000);
            uc.setReadTimeout(18000);
            uc.setRequestProperty("User-Agent", UA);
            uc.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
            uc.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9");
            int code = uc.getResponseCode();
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
            return new String(raw, "UTF-8");
        }catch(Throwable t){
            Log.i(TAG, "DB_GET_ERR " + t);
            return null;
        }finally{
            try{ if(uc != null) uc.disconnect(); }catch(Throwable t){}
        }
    }

    /** get_play_info：{"key": vid} → original_media_info.main_url */
    static String[] playInfo(String vid, String referer){
        java.net.HttpURLConnection uc = null;
        try{
            String q = "?version_code=20800&language=zh-CN&device_platform=web"
                     + "&aid=497858&real_aid=497858&pkg_type=release_version"
                     + "&samantha_web=1&use-olympus-account=1";
            uc = (java.net.HttpURLConnection) new java.net.URL(PLAY_INFO_API + q).openConnection();
            uc.setRequestMethod("POST");
            uc.setDoOutput(true);
            uc.setConnectTimeout(10000);
            uc.setReadTimeout(15000);
            uc.setRequestProperty("Content-Type", "application/json");
            uc.setRequestProperty("Origin", "https://www.doubao.com");
            uc.setRequestProperty("Referer", referer);
            uc.setRequestProperty("User-Agent", UA);
            java.io.OutputStream os = uc.getOutputStream();
            os.write(("{\"key\":\"" + vid + "\"}").getBytes("UTF-8"));
            os.close();
            int code = uc.getResponseCode();
            Log.i(TAG, "DB_PLAY_CODE " + code);
            if(code != 200) return null;
            StringBuilder sb = new StringBuilder();
            java.io.BufferedReader br = new java.io.BufferedReader(
                new java.io.InputStreamReader(uc.getInputStream(), "UTF-8"));
            String line;
            while((line = br.readLine()) != null) sb.append(line);
            br.close();
            JSONObject j = new JSONObject(sb.toString());
            if(j.optInt("code", -1) != 0) return null;
            JSONObject d = j.optJSONObject("data");
            if(d == null) return null;
            JSONObject omi = d.optJSONObject("original_media_info");
            String url = "";
            if(omi != null) url = sanitize(omi.optString("main_url", ""));
            if(url.length() == 0) url = sanitize(d.optString("main_url", ""));
            String poster = d.optString("poster_url", "");
            Log.i(TAG, "DB_PLAY ok=" + (url.length() > 0));
            return new String[]{ url, poster };
        }catch(Throwable t){
            Log.i(TAG, "DB_PLAY_ERR " + t);
            return null;
        }finally{
            try{ if(uc != null) uc.disconnect(); }catch(Throwable t){}
        }
    }

    /** 主入口 */
    public static DbItem resolve(String url){
        DbItem it = new DbItem();
        try{
            String html = get(url);
            if(html == null || html.length() < 500){
                it.err = "豆包页面拉取失败";
                Log.i(TAG, "DB_FAIL len=" + (html == null ? -1 : html.length()));
                return it;
            }
            Log.i(TAG, "DB_HTML len=" + html.length());
            // 页面里有 <script> 里的 JSON payload（__NEXT_DATA__ / __INITIAL_STATE__ / 裸 JSON）
            JSONObject root = findMainJson(html);
            if(root == null){
                it.err = "未找到页面数据";
                return it;
            }
            Log.i(TAG, "DB_JSON keys=" + root.length());
            // 元数据
            it.title = deepFindStr(root, new String[]{ "title", "name" });
            it.desc = deepFindStr(root, new String[]{ "content", "desc", "text" });
            if(it.desc == null) it.desc = it.title;
            if(it.title == null) it.title = it.desc;
            if(it.title == null) it.title = "";
            if(it.desc == null) it.desc = "";
            String nick = deepFindStr(root, new String[]{ "nickname", "user_name", "author" });
            it.author = nick == null ? "" : nick;
            it.id = deepFindStr(root, new String[]{ "conversation_id", "thread_id", "id" });
            if(it.id == null) it.id = "";
            // 图片：递归收集所有 image url
            List<String> imgs = new ArrayList<String>();
            collectImages(root, imgs);
            it.images.addAll(uniq(imgs));
            // 视频：找 vid，再打 get_play_info
            List<String> vids = new ArrayList<String>();
            collectVids(root, vids);
            Log.i(TAG, "DB_VIDS " + vids.size());
            for(int i = 0; i < vids.size() && i < 4; i++){
                String[] r = playInfo(vids.get(i), url);
                if(r != null && r[0].length() > 0){
                    it.playUrl = r[0];
                    if(it.cover.length() == 0 && r[1].length() > 0) it.cover = r[1];
                    break;
                }
            }
            if(it.playUrl.length() > 0){
                it.gears.add(new String[]{ "原画", it.playUrl, "0", "0" });
            }
            // 封面兜底
            if(it.cover.length() == 0){
                String c = deepFindUrl(root, new String[]{ "poster", "cover", "cover_url", "image_url" });
                if(c != null) it.cover = c;
            }
            if(it.cover.length() == 0 && it.images.size() > 0) it.cover = it.images.get(0);
            // 音频（music-sharing）
            if(it.playUrl.length() == 0){
                String au = deepFindUrl(root, new String[]{ "audio_url", "music_url", "song_url", "url" });
                if(au != null && (au.contains(".mp3") || au.contains(".m4a") || au.contains("audio"))) {
                    it.audioUrl = au;
                }
            }
            it.ok = it.hasVideo() || it.hasImages() || it.hasAudio() || it.title.length() > 0;
            Log.i(TAG, "DB_OK title=" + it.title + " v=" + it.hasVideo()
                + " imgs=" + it.images.size() + " audio=" + it.hasAudio());
            if(!it.ok && it.err.length() == 0) it.err = "未取到豆包内容";
        }catch(Throwable t){
            it.err = String.valueOf(t);
            Log.i(TAG, "DB_ERR " + t);
        }
        return it;
    }

    /** 从页面里找最大块合法 JSON 作主数据 */
    static JSONObject findMainJson(String html){
        // 1) __NEXT_DATA__
        String[] markers = { "__NEXT_DATA__", "__INITIAL_STATE__", "__NUXT__" };
        for(int i = 0; i < markers.length; i++){
            int p = html.indexOf(markers[i]);
            if(p < 0) continue;
            int b = html.indexOf('{', p);
            if(b < 0) continue;
            JSONObject o = tryParseObject(html, b);
            if(o != null && o.length() > 3){
                Log.i(TAG, "DB_JSON_FROM " + markers[i]);
                return o;
            }
        }
        // 2) 兜底：从所有 { 里找最大合法 JSON
        JSONObject best = null;
        int bestLen = 0;
        int idx = 0;
        int tries = 0;
        while(tries < 40){
            int b = html.indexOf('{', idx);
            if(b < 0) break;
            JSONObject o = tryParseObject(html, b);
            if(o != null){
                String s = o.toString();
                if(s.length() > bestLen){ bestLen = s.length(); best = o; }
            }
            idx = b + 1;
            tries++;
        }
        if(best != null) Log.i(TAG, "DB_JSON_BEST len=" + bestLen);
        return best;
    }

    /** 从 start 位置的 { 开始尝试解析一个完整 JSON 对象 */
    static JSONObject tryParseObject(String s, int start){
        int depth = 0;
        boolean inStr = false;
        boolean esc = false;
        for(int i = start; i < s.length(); i++){
            char c = s.charAt(i);
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
                if(depth == 0){
                    String sub = s.substring(start, i + 1);
                    try{ return new JSONObject(sub); }catch(Throwable t){ return null; }
                }
            }
        }
        return null;
    }

    /** 递归找 vid / video_id */
    static void collectVids(Object o, List<String> out){
        if(o == null) return;
        if(o instanceof JSONObject){
            JSONObject j = (JSONObject)o;
            String[] keys = { "vid", "video_id", "videoId" };
            for(int i = 0; i < keys.length; i++){
                String v = j.optString(keys[i], "");
                if(v.length() > 8 && !out.contains(v)) out.add(v);
            }
            java.util.Iterator<String> it = j.keys();
            while(it.hasNext()){
                Object c = j.opt(it.next());
                if(c instanceof JSONObject || c instanceof JSONArray) collectVids(c, out);
            }
        }else if(o instanceof JSONArray){
            JSONArray a = (JSONArray)o;
            for(int i = 0; i < a.length(); i++) collectVids(a.opt(i), out);
        }
    }

    /** 递归收集图片 url */
    static void collectImages(Object o, List<String> out){
        if(o == null) return;
        if(o instanceof JSONObject){
            JSONObject j = (JSONObject)o;
            String[] keys = { "image_url", "imageUrl", "url", "origin_url", "raw_url" };
            for(int i = 0; i < keys.length; i++){
                String v = j.optString(keys[i], "");
                if(v.startsWith("http") && isImageUrl(v) && !out.contains(v)) out.add(v);
            }
            java.util.Iterator<String> it = j.keys();
            while(it.hasNext()){
                Object c = j.opt(it.next());
                if(c instanceof JSONObject || c instanceof JSONArray) collectImages(c, out);
            }
        }else if(o instanceof JSONArray){
            JSONArray a = (JSONArray)o;
            for(int i = 0; i < a.length(); i++) collectImages(a.opt(i), out);
        }
    }

    static boolean isImageUrl(String u){
        String s = u.toLowerCase();
        if(s.contains(".mp4") || s.contains(".m3u8")) return false;
        return s.contains(".jpg") || s.contains(".jpeg") || s.contains(".png")
            || s.contains(".webp") || s.contains(".gif") || s.contains("image");
    }

    static List<String> uniq(List<String> in){
        List<String> out = new ArrayList<String>();
        for(int i = 0; i < in.size(); i++){
            if(!out.contains(in.get(i))) out.add(in.get(i));
        }
        return out;
    }

    static String sanitize(String u){
        if(u == null) return "";
        String r = u.replace("\\u002F", "/").replace("\\/", "/").trim();
        if(r.startsWith("//")) r = "https:" + r;
        return r;
    }

    static String deepFindStr(Object o, String[] keys){
        if(o == null) return null;
        if(o instanceof JSONObject){
            JSONObject j = (JSONObject)o;
            for(int i = 0; i < keys.length; i++){
                String v = j.optString(keys[i], "");
                if(v.length() > 0 && !v.startsWith("{") && !v.startsWith("[")) return v;
            }
            java.util.Iterator<String> it = j.keys();
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

    static String deepFindUrl(Object o, String[] keys){
        if(o == null) return null;
        if(o instanceof JSONObject){
            JSONObject j = (JSONObject)o;
            for(int i = 0; i < keys.length; i++){
                String v = j.optString(keys[i], "");
                if(v.startsWith("http")) return v;
                if(v.startsWith("//")) return "https:" + v;
            }
            java.util.Iterator<String> it = j.keys();
            while(it.hasNext()){
                Object c = j.opt(it.next());
                if(c instanceof JSONObject || c instanceof JSONArray){
                    String r = deepFindUrl(c, keys);
                    if(r != null) return r;
                }
            }
        }else if(o instanceof JSONArray){
            JSONArray a = (JSONArray)o;
            for(int i = 0; i < a.length(); i++){
                String r = deepFindUrl(a.opt(i), keys);
                if(r != null) return r;
            }
        }
        return null;
    }

    /** 输出 render() 认的 i_* JSON */
    public static String toJson(DbItem it){
        StringBuilder b = new StringBuilder();
        b.append("{");
        b.append("\"i_platform\":\"豆包\",");
        kv(b, "i_id", it.id.length() > 0 ? it.id : ("db_" + Math.abs(it.title.hashCode())));
        if(it.author.length() > 0) kv(b, "i_author", it.author);
        if(it.title.length() > 0) kv(b, "i_desc", it.title);
        if(it.cover.length() > 0) kv(b, "i_cover", it.cover);
        if(it.hasVideo()) kv(b, "i_play", it.playUrl);
        if(it.hasAudio()) kv(b, "i_bgm", it.audioUrl);
        if(it.hasImages()){
            StringBuilder im = new StringBuilder();
            for(int i = 0; i < it.images.size(); i++){
                if(i > 0) im.append("|");
                im.append(it.images.get(i));
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
    public static String[] toMedia(DbItem it){
        StringBuilder im = new StringBuilder();
        for(int i = 0; i < it.images.size(); i++){
            if(i > 0) im.append("|");
            im.append(it.images.get(i));
        }
        return new String[]{ im.toString(), it.playUrl == null ? "" : it.playUrl,
                             it.cover == null ? "" : it.cover };
    }


    static String esc(String s){
        if(s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "").replace("\t", " ");
    }


    /** 输出 renderCards() 认的「【标签】值」文本 */
    public static String toText(DbItem it){
        StringBuilder b = new StringBuilder();
        b.append("【平台】豆包\n");
        if(it.id.length() > 0) b.append("【作品ID】").append(it.id).append("\n");
        if(it.title.length() > 0) b.append("【标题】").append(it.title).append("\n");
        if(it.author.length() > 0) b.append("【作者】").append(it.author).append("\n");
        if(it.desc.length() > 0 && !it.desc.equals(it.title)) b.append("【描述】").append(it.desc).append("\n");
        if(it.cover.length() > 0) b.append("【封面】").append(it.cover).append("\n");
        if(it.hasVideo()) b.append("【视频直链】").append(it.playUrl).append("\n");
        if(it.hasAudio()) b.append("【音频直链】").append(it.audioUrl).append("\n");
        if(it.hasImages()){
            b.append("【图集】").append(it.images.size()).append(" 张\n");
            for(int i = 0; i < it.images.size(); i++){
                b.append("【图").append(i + 1).append("】").append(it.images.get(i)).append("\n");
            }
        }
        if(it.hasVideo()) b.append("【音频下载】点下方「提取音频」，从视频中提取音轨为 MP3\n");
        return b.toString();
    }
}