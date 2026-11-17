package com.re.douyinparse;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 抖音本地直连解析器（免 Cookie、免签名、免服务器）
 *
 * 方案来源：github.com/jiuhunwl/short_videos 的 DouyinParser.php
 * 核心接口：https://api.amemv.com/aweme/v1/aweme/detail/
 *
 * 关键参数（PHP 源码实测值，别乱改）：
 *   aid=6383  device_platform=web  version_code=190500  version_name=19.5.0
 *   UA 必须是移动端 UA —— 实测改回桌面 UA 会返回空 body
 *
 * 能力：
 *   · 视频：play_addr.uri + bit_rate[] 多档位，走 aweme.snssdk.com 拿原画 302
 *   · 图集：images[].url_list 原图
 *   · 实况：images[].video 动轨（Live Photo）
 *   · 背景音乐：music.play_url
 *   · 元数据：desc / author / cover / duration / statistics
 *
 * 实测（2026-11-08）：
 *   短链 v.douyin.com/irwsqD47 → aweme_id 7396234104540335395 → 92167 字节
 *   desc / author / 3 条直链 / 11 档 bit_rate / music 全部拿到
 */
public class DyLocalParser {
    static final String TAG = "DYP";

    /** 免 Cookie 详情接口 */
    static final String DETAIL = "https://api.amemv.com/aweme/v1/aweme/detail/";
    /** 原画播放地址模板：拼 vid 后 302 即原画 */
    static final String ORIGINAL_PLAY = "https://aweme.snssdk.com/aweme/v1/play/";

    /** 详情接口专用移动端 UA（必须移动端，桌面 UA 会返回空） */
    static final String MOBILE_UA =
        "Mozilla/5.0 (Linux; Android 13; 2210132C Build/TKQ1.220829.002) "
        + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Mobile Safari/537.36";
    /** 桌面端 UA：短链重定向 */
    static final String DESKTOP_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
        + "(KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36";

    static final Pattern P_ID = Pattern.compile(
        "/(?:video|note|share/video|share/note)/(\\d{15,})");

    // ---------------- 结果模型 ----------------
    public static class Item {
        public boolean ok = false;
        public String err = "";
        public String id = "";
        public String title = "";
        public String author = "";
        public String avatar = "";
        public String cover = "";
        public String playUrl = "";      // 主视频（原画优先）
        public String musicUrl = "";
        public String musicTitle = "";
        public String musicAuthor = "";
        public long durMs = 0;
        public long createdAt = 0;
        public long likeCount = 0;
        public long commentCount = 0;
        public long shareCount = 0;
        /** {标签, url, 大小} —— 多档清晰度 */
        public List<String[]> gears = new ArrayList<String[]>();
        /** 图集静态图 */
        public List<String> images = new ArrayList<String>();
        /** 实况动轨：与 images 一一对应，空串表示该张不是实况 */
        public List<String> liveVideos = new ArrayList<String>();

        public boolean hasVideo(){ return playUrl != null && playUrl.length() > 0; }
        public boolean hasImages(){ return images != null && !images.isEmpty(); }
        public boolean hasLive(){
            for(String v : liveVideos){ if(v != null && v.length() > 0) return true; }
            return false;
        }
        public boolean hasMusic(){ return musicUrl != null && musicUrl.length() > 0; }
    }

    // ---------------- 平台识别 ----------------
    public static boolean isDyUrl(String url){
        if(url == null) return false;
        String u = url.toLowerCase();
        return u.contains("douyin.com") || u.contains("iesdouyin.com")
            || u.contains("amemv.com");
    }

    /** 评论/主页/直播等非单作品链接不接 */
    public static boolean canHandle(String url){
        if(!isDyUrl(url)) return false;
        String u = url.toLowerCase();
        if(u.contains("/user/")) return false;
        if(u.contains("/live/")) return false;
        return true;
    }

    // ---------------- 主入口 ----------------
    /**
     * 解析单个抖音作品。绝不抛异常，失败返回 ok=false。
     * @param ctx Context（用于套手机代理）
     * @param url 分享链接（短链/长链都行）
     */
    public static Item resolve(Context ctx, String url){
        Item bad = new Item();
        if(ctx == null || url == null || url.length() == 0){
            bad.err = "链接为空";
            return bad;
        }
        try{
            long t0 = System.currentTimeMillis();

            // 1) 拿 aweme_id
            String id = extractId(url);
            if(id.length() == 0){
                // 短链：跟一次重定向
                String real = follow(ctx, url);
                DypLog.i("DY_LOCAL_REDIR " + cut(real, 100));
                id = extractId(real);
                if(id.length() == 0 && real != null && real.length() > 0){
                    id = extractId(url);
                }
            }
            if(id.length() == 0){
                bad.err = "无法提取作品 ID";
                return bad;
            }
            DypLog.i("DY_LOCAL_ID id=" + id);

            // 2) 请求详情
            String q = DETAIL + "?aweme_id=" + id
                + "&aid=6383&device_platform=web"
                + "&version_code=190500&version_name=19.5.0";
            String body = httpGet(ctx, q, MOBILE_UA, 12000, 20000);
            if(body == null || body.length() == 0){
                bad.err = "详情接口空响应";
                return bad;
            }
            JSONObject j = new JSONObject(body);
            int sc = j.optInt("status_code", -1);
            if(sc != 0){
                bad.err = "status_code=" + sc;
                return bad;
            }
            JSONObject d = j.optJSONObject("aweme_detail");
            if(d == null){
                bad.err = "aweme_detail 为空";
                return bad;
            }

            // 3) 映射字段
            Item it = new Item();
            it.id = id;
            fill(it, d);
            it.ok = it.hasVideo() || it.hasImages();

            // 4) 视频：优先原画
            if(it.hasVideo()){
                String vid = jget(d, "video", "play_addr", "uri");
                if(vid.length() > 0){
                    String orig = follow(ctx,
                        ORIGINAL_PLAY + "?video_id=" + vid + "&ratio=default&line=0");
                    if(orig != null && orig.length() > 0
                            && (orig.contains("douyinvod") || orig.contains("snssdk")
                                || orig.contains("byte") || orig.contains("http"))){
                        it.playUrl = orig;
                        DypLog.i("DY_LOCAL_ORIGIN vid=" + cut(vid, 20) + " got=" + cut(orig, 70));
                    }
                }
            }

            DypLog.i("DY_LOCAL_OK ok=" + it.ok + " v=" + it.hasVideo()
                + " imgs=" + it.images.size() + " live=" + it.hasLive()
                + " music=" + it.hasMusic() + " gears=" + it.gears.size()
                + " ms=" + (System.currentTimeMillis() - t0));
            return it;
        }catch(Throwable t){
            DypLog.i("DY_LOCAL_ERR " + t);
            bad.err = "EX " + t;
            return bad;
        }
    }

    // ---------------- 字段映射 ----------------
    static void fill(Item it, JSONObject d){
        it.title = str(d, "desc");
        it.createdAt = d.optLong("create_time", 0) * 1000;
        it.durMs = d.optLong("duration", 0);

        JSONObject au = d.optJSONObject("author");
        if(au != null){
            it.author = str(au, "nickname");
            it.avatar = firstUrl(au.optJSONObject("avatar_thumb"), "url_list");
            if(it.avatar.length() == 0){
                it.avatar = firstUrl(au.optJSONObject("avatar_medium"), "url_list");
            }
        }

        JSONObject st = d.optJSONObject("statistics");
        if(st != null){
            it.likeCount = st.optLong("digg_count", 0);
            it.commentCount = st.optLong("comment_count", 0);
            it.shareCount = st.optLong("share_count", 0);
        }

        // 封面
        JSONObject v = d.optJSONObject("video");
        if(v != null){
            it.cover = firstUrl(v.optJSONObject("cover"), "url_list");
            if(it.cover.length() == 0){
                it.cover = firstUrl(v.optJSONObject("origin_cover"), "url_list");
            }
            // 主播放地址
            JSONObject pa = v.optJSONObject("play_addr");
            if(pa != null){
                JSONArray ul = pa.optJSONArray("url_list");
                if(ul != null && ul.length() > 0){
                    it.playUrl = ul.optString(0, "");
                }
            }
            // 多档清晰度（bit_rate 数组，按码率从高到低）
            JSONArray br = v.optJSONArray("bit_rate");
            if(br != null){
                for(int i = 0; i < br.length(); i++){
                    JSONObject o = br.optJSONObject(i);
                    if(o == null) continue;
                    JSONObject pa2 = o.optJSONObject("play_addr");
                    if(pa2 == null) continue;
                    JSONArray ul2 = pa2.optJSONArray("url_list");
                    if(ul2 == null || ul2.length() == 0) continue;
                    String u = ul2.optString(0, "");
                    if(u.length() == 0) continue;
                    long rate = o.optLong("bit_rate", 0);
                    String gear = o.optString("gear_name", "");
                    String label = gear.length() > 0 ? gear : (rate > 0 ? (rate / 1000) + "kbps" : "档" + (i + 1));
                    long size = pa2.optLong("data_size", 0);
                    it.gears.add(new String[]{label, u,
                        size > 0 ? InnerDownloader.sizeText(size) : ""});
                }
            }
        }

        // 图集 + 实况
        JSONArray imgs = d.optJSONArray("images");
        if(imgs != null){
            for(int i = 0; i < imgs.length(); i++){
                JSONObject o = imgs.optJSONObject(i);
                if(o == null){ it.images.add(""); it.liveVideos.add(""); continue; }
                String u = firstUrl(o, "url_list");
                if(u.length() == 0){
                    // 有些版本放在 download_url_list
                    u = firstUrl(o, "download_url_list");
                }
                it.images.add(u);
                // 实况动轨
                JSONObject lv = o.optJSONObject("video");
                String lvUrl = "";
                if(lv != null){
                    lvUrl = firstUrl(lv.optJSONObject("play_addr"), "url_list");
                }
                it.liveVideos.add(lvUrl);
            }
        }
        if(it.images.size() > 0 && it.playUrl.length() == 0){
            // 纯图集，把空地址剔掉
            List<String> okImgs = new ArrayList<String>();
            List<String> okLive = new ArrayList<String>();
            for(int i = 0; i < it.images.size(); i++){
                String u = it.images.get(i);
                if(u != null && u.length() > 0){
                    okImgs.add(u);
                    okLive.add(i < it.liveVideos.size() ? it.liveVideos.get(i) : "");
                }
            }
            it.images = okImgs;
            it.liveVideos = okLive;
        }

        // 背景音乐
        JSONObject mu = d.optJSONObject("music");
        if(mu != null){
            it.musicUrl = firstUrl(mu.optJSONObject("play_url"), "url_list");
            it.musicTitle = str(mu, "title");
            it.musicAuthor = str(mu, "author");
        }
    }

    // ---------------- ID 提取 / 重定向 ----------------
    static String extractId(String url){
        if(url == null) return "";
        Matcher m = P_ID.matcher(url);
        if(m.find()) return m.group(1);
        return "";
    }

    /** 跟随短链重定向，返回最终 URL（失败返回原 URL） */
    static String follow(Context ctx, String url){
        HttpURLConnection c = null;
        try{
            c = MainActivity.openConn(ctx, url);
            c.setRequestMethod("GET");
            c.setInstanceFollowRedirects(true);
            c.setConnectTimeout(10000);
            c.setReadTimeout(12000);
            c.setRequestProperty("User-Agent", DESKTOP_UA);
            int code = c.getResponseCode();
            String real = c.getURL() == null ? url : c.getURL().toString();
            if(code >= 400) return url;
            return real;
        }catch(Throwable t){
            return url;
        }finally{
            try{ if(c != null) c.disconnect(); }catch(Throwable t){}
        }
    }

    // ---------------- HTTP ----------------
    static String httpGet(Context ctx, String url, String ua,
                          int connMs, int readMs) throws Exception{
        HttpURLConnection c = null;
        InputStream in = null;
        try{
            c = MainActivity.openConn(ctx, url);
            c.setRequestMethod("GET");
            c.setInstanceFollowRedirects(true);
            c.setConnectTimeout(connMs);
            c.setReadTimeout(readMs);
            c.setRequestProperty("User-Agent", ua);
            c.setRequestProperty("Accept", "application/json, text/plain, */*");
            c.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9");
            int code = c.getResponseCode();
            in = (code >= 200 && code < 300) ? c.getInputStream() : c.getErrorStream();
            if(in == null) return "";
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            String body = new String(bo.toByteArray(), "UTF-8");
            if(code < 200 || code >= 300){
                DypLog.i("DY_LOCAL_HTTP code=" + code + " body=" + cut(body, 120));
            }
            return body;
        }finally{
            try{ if(in != null) in.close(); }catch(Throwable t){}
            try{ if(c != null) c.disconnect(); }catch(Throwable t){}
        }
    }

    // ---------------- 小工具 ----------------
    static String str(JSONObject o, String k){
        if(o == null) return "";
        String v = o.optString(k, "");
        return v == null ? "" : v.trim();
    }

    /** 取 obj 下某个 {url_list:[...]} 的第一条 */
    static String firstUrl(JSONObject o, String listKey){
        if(o == null) return "";
        JSONArray a = o.optJSONArray(listKey);
        if(a == null || a.length() == 0) return "";
        return a.optString(0, "");
    }

    /** a.b.c 链式取字符串 */
    static String jget(JSONObject o, String... path){
        JSONObject cur = o;
        for(int i = 0; i < path.length - 1; i++){
            cur = cur == null ? null : cur.optJSONObject(path[i]);
        }
        return cur == null ? "" : str(cur, path[path.length - 1]);
    }

    static String cut(String s, int n){
        if(s == null) return "";
        return s.length() <= n ? s : s.substring(0, n);
    }

    // ---------------- 展示 ----------------
    public static String toText(Item it){
        StringBuilder sb = new StringBuilder();
        sb.append("【平台】抖音（本地直连）\n");
        if(it.title.length() > 0) sb.append("【标题】").append(it.title).append("\n");
        if(it.author.length() > 0) sb.append("【作者】").append(it.author).append("\n");
        if(it.durMs > 0) sb.append("【时长】").append(it.durMs / 1000).append("s\n");
        if(it.likeCount > 0) sb.append("【点赞】").append(it.likeCount);
        if(it.commentCount > 0) sb.append("  【评论】").append(it.commentCount);
        if(it.shareCount > 0) sb.append("  【分享】").append(it.shareCount);
        if(it.likeCount > 0 || it.commentCount > 0 || it.shareCount > 0) sb.append("\n");
        if(it.gears.size() > 1){
            sb.append("【清晰度】").append(it.gears.size()).append(" 档\n");
            for(int i = 0; i < it.gears.size(); i++){
                String[] g = it.gears.get(i);
                sb.append("  ").append(g[0]);
                if(g[2] != null && g[2].length() > 0) sb.append(" · ").append(g[2]);
                sb.append("\n");
            }
        }
        if(it.images.size() > 0){
            sb.append("【图片】").append(it.images.size()).append(" 张");
            if(it.hasLive()) sb.append("（含实况）");
            sb.append("\n");
        }
        if(it.hasMusic()){
            sb.append("【音乐】");
            if(it.musicTitle.length() > 0) sb.append(it.musicTitle);
            if(it.musicAuthor.length() > 0) sb.append(" - ").append(it.musicAuthor);
            sb.append("\n");
        }
        if(it.playUrl.length() > 0) sb.append("【直链】").append(it.playUrl).append("\n");
        sb.append("【来源】amemv detail（免 Cookie）");
        return sb.toString();
    }

    /**
     * 生成媒体信息，顺序必须与 o2(text, imgs, play, cover) 对齐：
     *   [0] imgs    —— 图片列表，**必须用 | 分隔**（parseImgs 的约定）
     *                  实况图用 |DYN| 分隔成两段：图片段|DYN|动轨段
     *   [1] play    —— 视频地址
     *   [2] cover   —— 封面
     *
     * v2.1.4 修复：之前这里用 \n 拼接，而 parseImgs() 用 split("[|]") 分割，
     * 导致 7 张图被当成 1 个超长字符串，UI 显示「7 张」但只解析出 1 张。
     * 约定必须与 MiscParser / ToutiaoParser / XhsParser 等保持一致。
     */
    public static String[] toMedia(Item it){
        StringBuilder im = new StringBuilder();
        int n = (it.images == null) ? 0 : it.images.size();
        for(int i = 0; i < n; i++){
            if(im.length() > 0) im.append("|");
            im.append(it.images.get(i));
        }
        // 实况动轨：只在有动轨时追加 |DYN| 段，且长度必须与图片一一对齐
        boolean hasLive = false;
        if(it.liveVideos != null){
            for(int i = 0; i < it.liveVideos.size(); i++){
                String v = it.liveVideos.get(i);
                if(v != null && v.length() > 0){ hasLive = true; break; }
            }
        }
        if(hasLive && n > 0){
            im.append("|DYN|");
            for(int i = 0; i < n; i++){
                if(i > 0) im.append("|");
                String v = (i < it.liveVideos.size()) ? it.liveVideos.get(i) : "";
                im.append(v == null ? "" : v);
            }
        }
        return new String[]{im.toString(), it.playUrl, it.cover};
    }
}