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
 * X（Twitter）解析器 —— 纯 HTTP，不走 WebView。
 *
 * 数据源（按优先级）：
 *   1) api.fxtwitter.com/i/status/{id}   第三方镜像，结构最全（videos + photos + mosaic）
 *   2) cdn.syndication.twimg.com/tweet-result?id={id}&lang=en&token=a
 *      官方免登录接口，但 mediaEntities 常为 0，视频信息在 top-level video 字段里
 *
 * 实测结论（2026-10-21，三条样本）：
 *   - 单视频：fxtwitter videos=1，1280x720，431s
 *   - 图片：  fxtwitter photos=2，PNG（pbs.twimg.com/media/xxx.png?name=orig）
 *   - 多视频：fxtwitter videos=2，720x1280，各 ~20s
 *   - video.twimg.com / pbs.twimg.com 国内可直连，不需要代理
 *   - vxtwitter 返回 403，不可用
 *
 * 直链特征：
 *   视频 https://video.twimg.com/amplify_video/{vid}/vid/avc1/{w}x{h}/{name}.mp4?tag=29
 *   图片 https://pbs.twimg.com/media/{key}.{png|jpg}?name=orig
 *   两者都支持 Range，下载时带上 Referer: https://x.com/ 更稳。
 */
public class XParser {

    static final String TAG = "DYP";
    static final String UA = "Mozilla/5.0 (Linux; Android 12; Pixel 5) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36";

    /** 解析结果 */
    public static class XVideo {
        public String id = "";
        public String author = "";
        public String authorId = "";
        public String text = "";
        public String coverUrl = "";
        public long timestampMs = 0;
        public long likes = 0;
        public long retweets = 0;
        public String pageUrl = "";
        public String err = "";
        /** 推文卡片里的外部短链（无媒体时深挖这个） */
        public String cardUrl = "";
        /** 卡片标题（imgur 等站点的预览标题） */
        public String cardTitle = "";
        /** 服务器中转签名（/api/x/dl 的 query 鉴权） */
        public String serverDlTk = "";
        public String serverDlTs = "";
        /** 机场流量是否可用（false 时 App 要提示用户自己挂代理） */
        public boolean serverUsable = true;
        public String serverReason = "";
        /** 解析时的 Context（拿服务器地址用） */
        public java.lang.ref.WeakReference<Context> ctxRef = null;

        /** 视频直链（多视频时取第 1 条，其余在 videos 里） */
        public final List<String> videos = new ArrayList<String>();
        /** 视频尺寸，与 videos 一一对应："1280x720" */
        public final List<String> videoSize = new ArrayList<String>();
        /** 视频时长（秒），与 videos 一一对应 */
        public final List<String> videoDur = new ArrayList<String>();
        /** 视频封面，与 videos 一一对应 */
        public final List<String> videoPoster = new ArrayList<String>();
        /** 图片直链 */
        public final List<String> images = new ArrayList<String>();

        public boolean hasVideo(){ return !videos.isEmpty(); }
        public boolean hasImages(){ return !images.isEmpty(); }
        public boolean ok(){ return hasVideo() || hasImages(); }

        public String firstVideo(){
            return videos.isEmpty() ? "" : videos.get(0);
        }

        public long firstDurationMs(){
            if(videoDur.isEmpty()) return 0;
            try{
                double d = Double.parseDouble(videoDur.get(0));
                return (long) (d * 1000);
            }catch(Throwable t){ return 0; }
        }

        /**
         * 转成 MainActivity.render() 认识的 JSON 字段。
         *
         * 多视频：把全部视频写进 i_mix（用 | 分隔，与 i_mixDur / i_mixSize 对齐），
         *         第 1 条同时写进 i_play，方便直接播放；
         * 图片：  写进 i_imgs。
         */
        public String toResultJson(){
            StringBuilder b = new StringBuilder(2048);
            b.append('{');
            kv(b, "i_id", id);
            kv(b, "i_author", author);
            kv(b, "i_desc", text);
            kv(b, "i_time", timestampMs > 0 ? String.valueOf(timestampMs) : "");
            long dur = firstDurationMs();
            kv(b, "i_dur", dur > 0 ? String.valueOf(dur) : "");
            kv(b, "i_cover", coverUrl);
            kv(b, "i_platform", "X");

            if(hasImages()){
                StringBuilder im = new StringBuilder();
                for(String u : images){
                    if(im.length() > 0) im.append('|');
                    im.append(u);
                }
                kv(b, "i_imgs", im.toString());
            }
            if(hasVideo()){
                kv(b, "i_play", firstVideo());
                if(videos.size() > 1){
                    // 多视频（X 的一条推可以带 2~4 个视频）
                    StringBuilder vs = new StringBuilder();
                    StringBuilder ds = new StringBuilder();
                    StringBuilder ss = new StringBuilder();
                    StringBuilder ps = new StringBuilder();
                    for(int i = 0; i < videos.size(); i++){
                        if(i > 0){ vs.append('|'); ds.append('|'); ss.append('|'); ps.append('|'); }
                        vs.append(videos.get(i));
                        ds.append(i < videoDur.size() ? videoDur.get(i) : "");
                        ss.append(i < videoSize.size() ? videoSize.get(i) : "");
                        ps.append(i < videoPoster.size() ? videoPoster.get(i) : "");
                    }
                    kv(b, "i_mix", vs.toString());
                    kv(b, "i_mixDur", ds.toString());
                    kv(b, "i_mixSize", ss.toString());
                    kv(b, "i_mixPoster", ps.toString());
                }
            }
            StringBuilder st = new StringBuilder();
            if(likes > 0) st.append("赞 ").append(likes);
            if(retweets > 0){
                if(st.length() > 0) st.append("  ");
                st.append("转推 ").append(retweets);
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

    /** 该 URL 是否属于 X / Twitter */
    public static boolean isXUrl(String url){
        if(url == null) return false;
        String u = url.toLowerCase();
        return u.contains("x.com/") || u.contains("twitter.com/")
            || u.contains("t.co/") || u.contains("fxtwitter.com/")
            || u.contains("vxtwitter.com/");
    }

    /** 从各种形态的链接里抽 tweet id：
     *   x.com/{user}/status/{id}
     *   x.com/i/status/{id}
     *   twitter.com/{user}/status/{id}
     *   x.com/i/web/status/{id}
     *   mobile.twitter.com/...
     *   也接受纯数字 id
     */
    public static String extractId(String url){
        if(url == null) return "";
        String u = url.trim();
        if(u.matches("\\d{15,25}")) return u;
        try{
            Matcher m = Pattern.compile("/status(?:es)?/(\\d{10,25})").matcher(u);
            if(m.find()) return m.group(1);
            // /i/web/status/{id} 已被上面覆盖；再兜底一次纯数字段
            m = Pattern.compile("(\\d{15,25})").matcher(u);
            if(m.find()) return m.group(1);
        }catch(Throwable t){}
        return "";
    }

    /** 主入口：解析一条 X 链接。失败返回带 err 的对象，不返回 null
     *  优先级（v1.3.4）：
     *    1) 用户自己挂了代理 → 直连 fxtwitter / syndication（不烧机场流量，
     *       服务器到期后依然可用）；媒体 URL 保持原始 CDN 直链，
     *       下载时也走用户代理（InnerDownloader 已支持）。
     *    2) 没代理 → 走自建服务器中转（服务器走机场代理，媒体可下）。
     *    3) 服务器不可用 → 回退本地直连（解析可用，下载需自己挂代理）。 */
    public static XVideo resolve(Context ctx, String url){
        XVideo v = new XVideo();
        v.pageUrl = url;
        v.ctxRef = new java.lang.ref.WeakReference<Context>(ctx);
        if(url == null || url.length() == 0){ v.err = "空链接"; return v; }
        String id = extractId(url);
        if(id.length() == 0){ v.err = "链接里没找到 tweet id"; return v; }
        v.id = id;

        // ---- 源 0（用户代理直连）：fxtwitter → syndication ----
        // 用户开了代理就不走服务器，国外数据源直连（保服务器到期后可用）。
        if(ProxyPolicy.hasUserProxy(ctx)){
            Log.i(TAG, "X_DIRECT user-proxy try id=" + id);
            if(tryDirectSources(ctx, v, url, id)){
                Log.i(TAG, "X_DIRECT ok id=" + id + " videos=" + v.videos.size()
                        + " imgs=" + v.images.size());
                return v;
            }
            Log.i(TAG, "X_DIRECT fail id=" + id + " -> fallback srv");
            v = new XVideo();
            v.pageUrl = url;
            v.ctxRef = new java.lang.ref.WeakReference<Context>(ctx);
            v.id = id;
        }

        // ---- 源 1：自建服务器（带机场代理，媒体可下）----
        String srv = MainActivity.effSrvS(ctx);
        if(srv.length() > 0){
            try{
                String js = httpGetText(ctx,
                        srv + "/api/x/parse?url=" + java.net.URLEncoder.encode(url, "UTF-8"),
                        15000, true);
                if(js != null && js.length() > 100 && js.indexOf("\"ok\"") >= 0){
                    if(parseServerJson(js, v)){
                        Log.i(TAG, "X_SRV ok id=" + id + " videos=" + v.videos.size()
                                + " imgs=" + v.images.size());
                        return v;
                    }
                    Log.i(TAG, "X_SRV parse miss");
                } else {
                    Log.i(TAG, "X_SRV miss len=" + (js == null ? -1 : js.length()));
                }
            }catch(Throwable t){
                Log.i(TAG, "X_SRV_ERR " + t);
            }
        }

        // ---- 源 2：本地直连（fxtwitter → syndication，作为最后兜底）----
        if(tryDirectSources(ctx, v, url, id)) return v;
if(!v.ok()) v.err = "所有数据源都没拿到媒体（作品可能已删除/受保护/仅登录可见）";
        return v;
    }

    /** 强制走服务器解析（播放失败回退用）：返回服务器中转 URL 的结果 */
    public static XVideo resolveServerOnly(Context ctx, String url){
        XVideo v = new XVideo();
        v.pageUrl = url;
        v.ctxRef = new java.lang.ref.WeakReference<Context>(ctx);
        if(url == null || url.length() == 0){ v.err = "空链接"; return v; }
        String id = extractId(url);
        if(id.length() == 0){ v.err = "链接里没找到 tweet id"; return v; }
        v.id = id;
        String srv = MainActivity.effSrvS(ctx);
        if(srv.length() == 0){
            v.err = "无服务器：X 播放失败回退需要服务器";
            return v;
        }
        try{
            String js = httpGetText(ctx,
                    srv + "/api/x/parse?url=" + java.net.URLEncoder.encode(url, "UTF-8"),
                    15000, true);
            if(js != null && js.length() > 100 && js.indexOf("\"ok\"") >= 0){
                if(parseServerJson(js, v)){
                    Log.i(TAG, "X_SRV_FB ok id=" + id + " videos=" + v.videos.size());
                    return v;
                }
            }
            v.err = "服务器解析失败";
        }catch(Throwable t){
            Log.i(TAG, "X_SRV_FB_ERR " + t);
            v.err = "服务器解析异常: " + t;
        }
        return v;
    }

    /** 直连数据源（用户代理优先）：fxtwitter → syndication */
    static boolean tryDirectSources(Context ctx, XVideo v, String url, String id){
        try{
            String js = httpGetText(ctx, "https://api.fxtwitter.com/i/status/" + id, 12000, false);
            if(js != null && js.length() > 100 && js.indexOf("\"tweet\"") >= 0){
                if(parseFxTwitter(js, v)){
                    Log.i(TAG, "X_FX ok id=" + id + " videos=" + v.videos.size() + " imgs=" + v.images.size());
                    return true;
                }
            } else {
                Log.i(TAG, "X_FX miss len=" + (js == null ? -1 : js.length()));
            }
        }catch(Throwable t){
            Log.i(TAG, "X_FX_ERR " + t);
        }

        try{
            String js = httpGetText(ctx,
                    "https://cdn.syndication.twimg.com/tweet-result?id=" + id + "&lang=en&token=a", 12000, false);
            if(js != null && js.length() > 100 && js.indexOf("\"id_str\"") >= 0){
                if(parseSyndication(js, v)){
                    Log.i(TAG, "X_SYN ok id=" + id + " videos=" + v.videos.size() + " imgs=" + v.images.size());
                    return true;
                }
            } else {
                Log.i(TAG, "X_SYN miss len=" + (js == null ? -1 : js.length()));
            }
        }catch(Throwable t){
            Log.i(TAG, "X_SYN_ERR " + t);
        }
        return false;
    }

    /**
     * 服务器 /api/x/parse 的响应：
     * { ok, id, type, author, text, cover,
     *   videos:[{url,size,dur,poster}], images:[{url,size}],
     *   dl_tk, dl_ts, sub_usable, sub_reason }
     * 媒体 URL 替换成服务器中转地址（带 query 签名）。
     */
    static boolean parseServerJson(String js, XVideo v){
        try{
            Object root = Json.parse(js);
            if(!(root instanceof java.util.Map)) return false;
            java.util.Map r = (java.util.Map) root;
            if(!Boolean.TRUE.equals(get(r, "ok")) && !"true".equals(s(get(r, "ok")))) return false;

            v.text = s(get(r, "text"));
            v.author = s(get(r, "author"));
            v.likes = l(get(r, "likes"));
            v.retweets = l(get(r, "retweets"));
            v.coverUrl = s(get(r, "cover"));
            v.serverDlTk = s(get(r, "dl_tk"));
            v.serverDlTs = s(get(r, "dl_ts"));
            v.serverUsable = !"false".equals(s(get(r, "sub_usable")));
            v.serverReason = s(get(r, "sub_reason"));

            String srv = MainActivity.effSrvS(v.ctxRef != null ? v.ctxRef.get() : null);
            String dlBase = srv + "/api/x/dl";
            boolean haveSig = v.serverDlTk.length() > 0 && v.serverDlTs.length() > 0;

            Object vsObj = get(r, "videos");
            if(vsObj instanceof List){
                for(int vi = 0; vi < ((List) vsObj).size(); vi++){
                    Object o = ((List) vsObj).get(vi);
                    if(!(o instanceof java.util.Map)) continue;
                    java.util.Map m = (java.util.Map) o;
                    String u = s(get(m, "url"));
                    if(u.length() == 0) continue;
                    // 换成服务器中转地址（带 query 签名，播放器/下载器直接可用）
                    if(srv.length() > 0 && haveSig){
                        u = dlBase + "?type=video&id=" + v.id + "&i=" + vi
                                + "&ts=" + v.serverDlTs + "&tk=" + v.serverDlTk;
                    }
                    v.videos.add(u);
                    v.videoSize.add(s(get(m, "size")));
                    v.videoDur.add(s(get(m, "dur")));
                    v.videoPoster.add(s(get(m, "poster")));
                }
            }
            Object psObj = get(r, "images");
            if(psObj instanceof List){
                for(int pi = 0; pi < ((List) psObj).size(); pi++){
                    Object o = ((List) psObj).get(pi);
                    if(!(o instanceof java.util.Map)) continue;
                    String u = s(get((java.util.Map) o, "url"));
                    if(u.length() == 0) continue;
                    // 图片同样走中转
                    if(srv.length() > 0 && haveSig){
                        u = dlBase + "?type=image&id=" + v.id + "&i=" + pi
                                + "&ts=" + v.serverDlTs + "&tk=" + v.serverDlTk;
                    }
                    if(!v.images.contains(u)) v.images.add(u);
                }
            }
            if(v.coverUrl.length() > 0 && srv.length() > 0 && haveSig){
                // 封面也换中转（海报图 pbs.twimg.com 同样被污染）
                v.coverUrl = dlBase + "?type=image&id=" + v.id + "&i=0"
                        + "&ts=" + v.serverDlTs + "&tk=" + v.serverDlTk;
            }
            return true;
        }catch(Throwable e){
            Log.i(TAG, "X_SRV_PARSE_ERR " + e);
            return false;
        }
    }

    // ================= fxtwitter =================

    /**
     * fxtwitter 结构：
     * { "code":200, "tweet": {
     *     "id":"...", "url":"...", "text":"...", "created_at":"Wed Oct 21 16:06:20 +0000 2026",
     *     "likes":163, "retweets":26,
     *     "author": {"name":"...", "screen_name":"...", "id":"..."},
     *     "media": {
     *        "all":    [ ... ],
     *        "videos": [ {"type":"video","url":"https://video.twimg.com/...", "width":1280,
     *                     "height":720,"duration":431.38,"thumbnail_url":"..."} ],
     *        "photos": [ {"type":"photo","url":"https://pbs.twimg.com/media/xxx.png?name=orig",
     *                     "width":570,"height":759} ]
     *     }
     * }}
     */
    static boolean parseFxTwitter(String js, XVideo v){
        try{
            Object root = Json.parse(js);
            if(!(root instanceof java.util.Map)) return false;
            java.util.Map t = (java.util.Map) get((java.util.Map) root, "tweet");
            if(t == null) return false;

            v.text = s(get(t, "text"));
            v.authorId = s(get(t, "id"));
            java.util.Map au = (java.util.Map) get(t, "author");
            if(au != null){
                String n = s(get(au, "name"));
                String sn = s(get(au, "screen_name"));
                v.author = n.length() > 0 ? (sn.length() > 0 ? n + " @" + sn : n) : sn;
            }
            v.likes = l(get(t, "likes"));
            v.retweets = l(get(t, "retweets"));
            v.timestampMs = parseFxDate(s(get(t, "created_at")));

            // 卡片外链：media 为 null 但 card 存在（第三方视频卡片）
            java.util.Map card = (java.util.Map) get(t, "card");
            if(card != null){
                v.cardUrl = s(get(card, "url"));
                v.cardTitle = s(get(card, "title"));
            }
            if(v.cardUrl.length() == 0){
                // 旧字段 twitter_card 或 raw_text 里的 t.co 链接
                v.cardUrl = s(get(t, "twitter_card"));
            }

            java.util.Map med = (java.util.Map) get(t, "media");
            if(med == null) return true;   // 纯文字推/卡片推，也算解析成功

            Object vsObj = get(med, "videos");
            if(vsObj instanceof List){
                for(Object o : (List) vsObj){
                    if(!(o instanceof java.util.Map)) continue;
                    java.util.Map m = (java.util.Map) o;
                    // 优先从 formats[] 里挑最高码率（fxtwitter 多档视频）
                    String u = s(get(m, "url"));
                    Object fo = get(m, "formats");
                    if(fo instanceof List && ((List) fo).size() > 0){
                        long bestBr = -1;
                        String bestU = "";
                        for(Object f : (List) fo){
                            if(!(f instanceof java.util.Map)) continue;
                            java.util.Map fm = (java.util.Map) f;
                            String fu = s(get(fm, "url"));
                            if(fu.length() == 0) continue;
                            long br = l(get(fm, "bitrate"));
                            if(br > bestBr){ bestBr = br; bestU = fu; }
                        }
                        if(bestU.length() > 0) u = bestU;
                    }
                    if(u.length() == 0) continue;
                    v.videos.add(u);
                    v.videoSize.add(s(get(m, "width")) + "x" + s(get(m, "height")));
                    v.videoDur.add(s(get(m, "duration")));
                    String poster = s(get(m, "thumbnail_url"));
                    v.videoPoster.add(poster);
                    if(v.coverUrl.length() == 0) v.coverUrl = poster;
                }
            }
            Object psObj = get(med, "photos");
            if(psObj instanceof List){
                for(Object o : (List) psObj){
                    if(!(o instanceof java.util.Map)) continue;
                    String u = s(get((java.util.Map) o, "url"));
                    if(u.length() == 0) continue;
                    if(!v.images.contains(u)) v.images.add(u);
                }
            }
            if(v.coverUrl.length() == 0 && !v.images.isEmpty()) v.coverUrl = v.images.get(0);
            return true;
        }catch(Throwable e){
            Log.i(TAG, "X_FX_PARSE_ERR " + e);
            return false;
        }
    }

    /** fxtwitter 的 created_at: "Wed Oct 21 16:06:20 +0000 2026" */
    static long parseFxDate(String s){
        if(s == null || s.length() < 20) return 0;
        try{
            java.text.SimpleDateFormat f =
                    new java.text.SimpleDateFormat("EEE MMM dd HH:mm:ss Z yyyy", java.util.Locale.US);
            java.util.Date d = f.parse(s);
            return d == null ? 0 : d.getTime();
        }catch(Throwable t){ return 0; }
    }

    // ================= syndication =================

    /**
     * syndication 结构（2026-10 实测）：
     *   { "id_str":"...", "text":"...", "created_at":"...",
     *     "user":{"name":"...","screen_name":"..."},
     *     "mediaEntities":[ ... ],   // 常常是 0
     *     "video": { "aspectRatio":[16,9], "contentType":"media_entity",
     *                "durationMs":431380, "poster":"https://pbs.twimg.com/...",
     *                "variants":[ {"type":"video/mp4","src":"https://video.twimg.com/..."} ] } }
     *
     * 注意：视频 variants 有的版本在 video.variants，有的在 mediaEntities[].video_info.variants。
     * 这里两条都尝试。
     */
    static boolean parseSyndication(String js, XVideo v){
        try{
            Object root = Json.parse(js);
            if(!(root instanceof java.util.Map)) return false;
            java.util.Map r = (java.util.Map) root;

            v.text = s(get(r, "text"));
            java.util.Map u = (java.util.Map) get(r, "user");
            if(u != null){
                String n = s(get(u, "name"));
                String sn = s(get(u, "screen_name"));
                v.author = n.length() > 0 ? (sn.length() > 0 ? n + " @" + sn : n) : sn;
            }
            v.timestampMs = parseIsoDate(s(get(r, "created_at")));

            // 顶层 video
            java.util.Map vd = (java.util.Map) get(r, "video");
            if(vd != null){
                String poster = s(get(vd, "poster"));
                if(poster.length() > 0) v.coverUrl = poster;
                List vs = (List) get(vd, "variants");
                String best = pickMp4(vs);
                if(best.length() > 0){
                    v.videos.add(best);
                    Object ar = get(vd, "aspectRatio");
                    if(ar instanceof List && ((List) ar).size() >= 2){
                        v.videoSize.add(((List) ar).get(0) + "x" + ((List) ar).get(1));
                    } else {
                        v.videoSize.add("");
                    }
                    long durMs = l(get(vd, "durationMs"));
                    v.videoDur.add(durMs > 0 ? String.valueOf(durMs / 1000.0) : "");
                    v.videoPoster.add(poster);
                }
            }

            // mediaEntities[]（图集 / 部分视频）
            Object meObj = get(r, "mediaEntities");
            if(meObj instanceof List){
                for(Object o : (List) meObj){
                    if(!(o instanceof java.util.Map)) continue;
                    java.util.Map m = (java.util.Map) o;
                    String type = s(get(m, "type"));
                    String mu = s(get(m, "media_url_https"));
                    java.util.Map vi = (java.util.Map) get(m, "video_info");
                    if(vi != null){
                        String best = pickMp4((List) get(vi, "variants"));
                        if(best.length() > 0 && !v.videos.contains(best)){
                            v.videos.add(best);
                            v.videoSize.add("");
                            long durMs = l(get(vi, "duration_millis"));
                            v.videoDur.add(durMs > 0 ? String.valueOf(durMs / 1000.0) : "");
                            v.videoPoster.add(mu);
                        }
                    } else if("photo".equals(type) && mu.length() > 0){
                        if(!v.images.contains(mu)) v.images.add(mu);
                    }
                }
            }
            if(v.coverUrl.length() == 0 && !v.images.isEmpty()) v.coverUrl = v.images.get(0);
            return true;
        }catch(Throwable e){
            Log.i(TAG, "X_SYN_PARSE_ERR " + e);
            return false;
        }
    }

    /** 从 variants 里挑码率最高的 video/mp4（有 bitrate 就比码率，否则取最后一个） */
    static String pickMp4(List variants){
        String best = "";
        long bestBr = -1;
        if(!(variants instanceof List)) return "";
        for(Object o : (List) variants){
            if(!(o instanceof java.util.Map)) continue;
            java.util.Map m = (java.util.Map) o;
            String ct = s(get(m, "content_type"));
            if(ct.length() == 0) ct = s(get(m, "type"));
            if(ct.indexOf("mp4") < 0) continue;
            String src = s(get(m, "url"));
            if(src.length() == 0) src = s(get(m, "src"));
            if(src.length() == 0) continue;
            long br = l(get(m, "bitrate"));
            if(br >= bestBr){ bestBr = br; best = src; }
        }
        return best;
    }

    /** syndication 的 created_at 是 ISO8601 */
    static long parseIsoDate(String s){
        if(s == null || s.length() < 10) return 0;
        try{
            String t = s.replace("Z", "+0000").replace(":", "");
            // 2026-10-21T16:06:20+0000 -> yyyy-MM-dd'T'HH:mm:ssZ
            java.text.SimpleDateFormat f =
                    new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", java.util.Locale.US);
            java.util.Date d = f.parse(t);
            return d == null ? 0 : d.getTime();
        }catch(Throwable t){
            try{
                java.text.SimpleDateFormat f =
                        new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", java.util.Locale.US);
                java.util.Date d = f.parse(s.replace("Z", "+0000").replace(":", ""));
                return d == null ? 0 : d.getTime();
            }catch(Throwable t2){ return 0; }
        }
    }

    // ================= HTTP =================

    static String httpGetText(Context ctx, String url, int timeout){
        return httpGetText(ctx, url, timeout, false);
    }

    /**
     * @param isApi 走内置服务器 /api/ 时 true：需要 HMAC 鉴权头（X-TS/X-NONCE/X-SIGN）
     */
    static String httpGetText(Context ctx, String url, int timeout, boolean isApi){
        HttpURLConnection c = null;
        try{
            // openConnection(null) 会抛 IllegalArgumentException，
            // 没配代理时必须显式传 Proxy.NO_PROXY。
            // isApi = 服务器 API：永不套用户代理（用户代理可能不通，服务器必须直连）
            java.net.Proxy px = isApi ? null : MainActivity.staticProxyFor(ctx, url);
            c = (HttpURLConnection) new URL(url).openConnection(
                    px != null ? px : java.net.Proxy.NO_PROXY);
            c.setRequestProperty("User-Agent", UA);
            c.setRequestProperty("Accept", "application/json,*/*");
            if(isApi){
                c.setRequestProperty("Referer", "");
                // 服务器 HMAC 鉴权：X-TS / X-NONCE / X-SIGN
                try{
                    String ts = String.valueOf(System.currentTimeMillis() / 1000);
                    String nonce = java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 12);
                    String path = "/api/x/parse";
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
                    Log.i(TAG, "X_SIGN_ERR " + t);
                }
            } else {
                c.setRequestProperty("Referer", "https://x.com/");
            }
            c.setConnectTimeout(timeout);
            c.setReadTimeout(timeout);
            int code = c.getResponseCode();
            if(code < 200 || code >= 300){
                Log.i(TAG, "X_HTTP " + code + " " + url);
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
            Log.i(TAG, "X_HTTP_ERR " + url + " " + t);
            return null;
        }finally{
            try{ if(c != null) c.disconnect(); }catch(Throwable t){}
        }
    }

    // ================= 小工具 =================

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

    /**
     * 极简 JSON 解析器（项目里没有 gson/jackson，MainActivity 也是手写的）。
     * 支持 object / array / string / number / true / false / null，
     * 处理反斜杠转义（引号、反斜杠、斜杠、n、r、t、以及 u 开头的四位十六进制）。
     */
    static class Json {
        private final String s;
        private int i = 0;

        private Json(String s){ this.s = s; }

        static Object parse(String s) throws Exception {
            Json p = new Json(s);
            p.ws();
            Object v = p.value();
            p.ws();
            return v;
        }

        private void ws(){
            while(i < s.length()){
                char c = s.charAt(i);
                if(c == ' ' || c == '\n' || c == '\r' || c == '\t') i++;
                else break;
            }
        }

        private Object value() throws Exception {
            if(i >= s.length()) return null;
            char c = s.charAt(i);
            if(c == '{') return obj();
            if(c == '[') return arr();
            if(c == '"') return str();
            if(c == 't'){ i += 4; return Boolean.TRUE; }
            if(c == 'f'){ i += 5; return Boolean.FALSE; }
            if(c == 'n'){ i += 4; return null; }
            return num();
        }

        private java.util.Map obj() throws Exception {
            java.util.Map m = new java.util.LinkedHashMap();
            i++;  // {
            ws();
            if(i < s.length() && s.charAt(i) == '}'){ i++; return m; }
            while(i < s.length()){
                ws();
                if(s.charAt(i) != '"') throw new Exception("obj key expect \" at " + i);
                String k = str();
                ws();
                if(i < s.length() && s.charAt(i) == ':') i++;
                ws();
                Object v = value();
                m.put(k, v);
                ws();
                if(i < s.length() && s.charAt(i) == ','){ i++; continue; }
                if(i < s.length() && s.charAt(i) == '}'){ i++; break; }
                throw new Exception("obj expect , or } at " + i);
            }
            return m;
        }

        private List arr() throws Exception {
            List a = new ArrayList();
            i++;  // [
            ws();
            if(i < s.length() && s.charAt(i) == ']'){ i++; return a; }
            while(i < s.length()){
                ws();
                a.add(value());
                ws();
                if(i < s.length() && s.charAt(i) == ','){ i++; continue; }
                if(i < s.length() && s.charAt(i) == ']'){ i++; break; }
                throw new Exception("arr expect , or ] at " + i);
            }
            return a;
        }

        private String str() throws Exception {
            StringBuilder b = new StringBuilder();
            i++;  // "
            while(i < s.length()){
                char c = s.charAt(i);
                if(c == '\\'){
                    char e = s.charAt(i + 1);
                    switch(e){
                        case '"':  b.append('"');  i += 2; break;
                        case '\\': b.append('\\'); i += 2; break;
                        case '/':  b.append('/');  i += 2; break;
                        case 'b':  b.append('\b'); i += 2; break;
                        case 'f':  b.append('\f'); i += 2; break;
                        case 'n':  b.append('\n'); i += 2; break;
                        case 'r':  b.append('\r'); i += 2; break;
                        case 't':  b.append('\t'); i += 2; break;
                        case 'u':
                            b.append((char) Integer.parseInt(s.substring(i + 2, i + 6), 16));
                            i += 6; break;
                        default: b.append(e); i += 2;
                    }
                } else if(c == '"'){
                    i++; return b.toString();
                } else {
                    b.append(c); i++;
                }
            }
            throw new Exception("str not closed");
        }

        private Object num(){
            int st = i;
            while(i < s.length()){
                char c = s.charAt(i);
                if((c >= '0' && c <= '9') || c == '-' || c == '+' || c == '.'
                        || c == 'e' || c == 'E') i++;
                else break;
            }
            String n = s.substring(st, i);
            if(n.length() == 0) return null;
            try{
                if(n.indexOf('.') < 0 && n.indexOf('e') < 0 && n.indexOf('E') < 0)
                    return Long.parseLong(n);
                return Double.parseDouble(n);
            }catch(Throwable t){ return null; }
        }
    }
}