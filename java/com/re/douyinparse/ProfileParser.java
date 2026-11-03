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
    /** X 网页必须桌面 UA：移动 UA 拿到的是重定向引导页，没有服务端渲染的推文 */
    static final String XUA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36";

    public static String lastErr = "";

    /* ===== 作品类型：解析阶段就定死，下载阶段直接按它分派 ===== */
    public static final int KIND_UNKNOWN = 0;
    public static final int KIND_VIDEO   = 1;   // 普通视频
    public static final int KIND_IMAGE   = 2;   // 图集（纯图片）
    public static final int KIND_LIVE    = 3;   // 实况图（图片 + 动轨）
    public static final int KIND_MUSIC   = 4;   // 音乐作品（只有音频，无视频）
    public static final int KIND_ARTICLE = 5;   // 文章（长文，导出 markdown）

    public static class ProfileItem {
        public String id = "";
        public String title = "";
        public long durSec = 0;
        public String url = "";
        /** 列表响应里直接带的无水印直链（抖音/快手 WebView 方案用），空则下载时再解析 */
        public String directUrl = "";
        /** 图集（下载走图片队列） */
        public boolean isImage = false;
        /** 封面图 URL（列表缩略图用，可为空） */
        public String cover = "";
        /** 图集的全部图片地址（批量下载遍历用；视频条目为空） */
        public java.util.List<String> imgs = new java.util.ArrayList<String>();
        /** 与 imgs 一一对应的实况视频地址（空串表示该张不是实况） */
        public java.util.List<String> dyns = new java.util.ArrayList<String>();
        /**
         * 作品类型（解析阶段定死）：KIND_VIDEO / KIND_IMAGE / KIND_LIVE / KIND_MUSIC。
         * 下载阶段直接按它分派，不再「先当视频试一遍」。
         */
        public int kind = KIND_UNKNOWN;

        /** 文章正文（markdown，仅 KIND_ARTICLE 有效） */
        public String articleMd = "";
        /** 文章标题（KIND_ARTICLE） */
        public String articleTitle = "";
        /**
         * 平台名（抖音 / 快手 / B站 / TikTok / X / YouTube / Instagram …）。
         * 列表条目下载时必须带上它，否则 platOf(url) 认不出 CDN 域名，
         * 文件会落到「其他」目录。
         */
        public String plat = "";

        /** 类型中文名（列表 UI 显示用） */
        public String kindLabel(){
            switch(kind){
                case KIND_VIDEO: return "视频";
                case KIND_IMAGE: return "图集 " + imgs.size() + " 张";
                case KIND_LIVE: {
                    int n = 0;
                    for(String d : dyns){ if(d != null && d.length() > 0) n++; }
                    return "实况 " + imgs.size() + " 张(动" + n + ")";
                }
                case KIND_MUSIC: return "音乐(无视频)";
                case KIND_ARTICLE: return "文章";
                default: return "";
            }
        }
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
            c = (HttpURLConnection) new URL(api).openConnection();
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
    /** X（Twitter）用户主页：直连 syndication timeline-profile
     *  （无服务器，吃手机代理；公开账号约 100 条，敏感账号返回空 -> 上层回退 WebView） */
    public static List<ProfileItem> resolveXProfile(Context ctx, String screenName){
        lastErr = "";
        List<ProfileItem> out = new ArrayList<ProfileItem>();
        HttpURLConnection c = null;
        try{
            String sname = screenName == null ? "" : screenName.trim();
            if(sname.startsWith("@")) sname = sname.substring(1);
            if(sname.length() == 0){ lastErr = "X 用户名空"; return null; }
            String api = "https://syndication.twitter.com/srv/timeline-profile/screen-name/"
                    + URLEncoder.encode(sname, "UTF-8");
            c = MainActivity.openConn(ctx, api);
            c.setInstanceFollowRedirects(true);
            c.setRequestProperty("User-Agent", UA);
            c.setRequestProperty("Accept", "text/html,application/xhtml+xml,*/*");
            // 带登录 Cookie：敏感账号（成人内容号）匿名会被过滤成空
            try{
                String xck = CookieStore.header(ctx, CookieStore.PLATFORM_X);
                if(xck != null && xck.length() > 10){
                    c.setRequestProperty("Cookie", xck);
                    Log.i(TAG, "X_PROF_CK len=" + xck.length());
                }
            }catch(Throwable t){}
            c.setConnectTimeout(15000);
            c.setReadTimeout(25000);
            int code = c.getResponseCode();
            if(code != 200){
                lastErr = "X 主页 HTTP " + code;
                return null;
            }
            InputStream in = c.getInputStream();
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            in.close();
            String html = bo.toString("UTF-8");
            int k = html.indexOf("__NEXT_DATA__");
            Log.i(TAG, "X_PROF_HTTP len=" + html.length() + " nextData=" + (k >= 0));
            if(k < 0){
                lastErr = "X 主页结构异常（无 NEXT_DATA）";
                return null;
            }
            int s0 = html.indexOf('>', k);
            int e0 = html.indexOf("</script>", s0);
            if(s0 < 0 || e0 < 0){
                lastErr = "X 主页结构异常";
                return null;
            }
            String json = html.substring(s0 + 1, e0);
            Object root = XParser.Json.parse(json);
            List<Object> tweets = new ArrayList<Object>();
            collectXTweets(root, tweets, 0);
            for(Object o : tweets){
                if(!(o instanceof java.util.Map)) continue;
                java.util.Map m = (java.util.Map) o;
                String id = xstr(m.get("id_str"));
                if(id.length() < 6) continue;
                ProfileItem it = new ProfileItem();
                it.id = id;
                String txt = xstr(m.get("full_text"));
                if(txt.length() == 0) txt = xstr(m.get("text"));
                if(txt.length() > 60) txt = txt.substring(0, 60) + "…";
                it.title = txt.length() > 0 ? txt : ("X " + id);
                String permalink = xstr(m.get("permalink"));
                it.url = permalink.startsWith("/")
                        ? ("https://x.com" + permalink)
                        : ("https://x.com/i/status/" + id);
                // 封面 / 图集判定
                java.util.Map eu = xmap(m.get("extended_entities"));
                if(eu == null) eu = xmap(m.get("entities"));
                List<Object> med = xlist(eu == null ? null : eu.get("media"));
                if(med != null && !med.isEmpty()){
                    java.util.Map m0 = xmap(med.get(0));
                    if(m0 != null){
                        String mu = xstr(m0.get("media_url_https"));
                        if(mu.length() == 0) mu = xstr(m0.get("media_url"));
                        String tp = xstr(m0.get("type"));
                        if("photo".equals(tp)) it.isImage = true;
                        if(mu.length() > 0){
                            it.cover = mu + (mu.indexOf('?') >= 0 ? "&" : "?") + "name=small";
                        }
                    }
                }
                out.add(it);
            }
            Log.i(TAG, "X_PROF_ITEMS n=" + out.size());
            if(out.isEmpty()){
                // syndication 被 X 限流（实测 429，只回 2KB 空壳），
                // 改试 x.com 网页：服务端渲染了最近推文，游客态也出数据。
                Log.i(TAG, "X_PROF_SYN_EMPTY 改试 x.com 网页");
                List<ProfileItem> webList = resolveXProfileWeb(ctx, screenName);
                if(webList != null && !webList.isEmpty()) return webList;
                lastErr = "该账号主页需登录（敏感内容），请走网页登录";
                return null;
            }
            return out;
        }catch(Throwable t){
            Log.i(TAG, "X_PROF_ERR " + t);
            lastErr = "X 主页异常: " + t;
            return null;
        }finally{
            try{ if(c != null) c.disconnect(); }catch(Throwable t){}
        }
    }

    /** 递归找 X 推文对象（含 id_str + full_text/permalink/media） */
    static void collectXTweets(Object o, List<Object> out, int depth){
        if(o == null || depth > 16) return;
        if(o instanceof java.util.Map){
            java.util.Map m = (java.util.Map) o;
            Object idStr = m.get("id_str");
            if(idStr != null && (m.containsKey("full_text") || m.containsKey("permalink")
                    || m.containsKey("extended_entities"))){
                String sid = String.valueOf(idStr);
                if(sid.length() >= 6 && sid.matches("\\d+")) out.add(m);
            }
            for(Object v : m.values()) collectXTweets(v, out, depth + 1);
        }else if(o instanceof List){
            for(Object v : (List) o) collectXTweets(v, out, depth + 1);
        }
    }

    static String xstr(Object o){ return o == null ? "" : String.valueOf(o); }
    static java.util.Map xmap(Object o){ return (o instanceof java.util.Map) ? (java.util.Map) o : null; }
    static List<Object> xlist(Object o){
        if(o instanceof List) return (List<Object>) o;
        return null;
    }

    /**
     * X 主页：x.com 网页直出解析（syndication 被限流时的主力通道）。
     *
     * 实测结构（2026-11）：
     *   - HTML 约 130KB，服务端渲染了最近 4~20 条推文
     *   - 数据在 RSC 数据流里（多层转义），关键字段：
     *       "full_text":"..."                   推文正文
     *       "id_str":"<tweetId>"                推文 ID
     *       "media_url_https":"..."             视频封面 / 图片
     *       "content_type":"video/mp4" + "url"  多分辨率直链
     *   - 先把 \\\\" 反转义成 " 再做正则，否则匹配不到
     */
    public static List<ProfileItem> resolveXProfileWeb(Context ctx, String screenName){
        List<ProfileItem> out = new ArrayList<ProfileItem>();
        HttpURLConnection c = null;
        try{
            String sname = screenName == null ? "" : screenName.trim();
            if(sname.startsWith("@")) sname = sname.substring(1);
            if(sname.length() == 0) return out;
            String page = "https://x.com/" + URLEncoder.encode(sname, "UTF-8");
            c = MainActivity.openConn(ctx, page);
            c.setInstanceFollowRedirects(true);
            c.setConnectTimeout(15000);
            c.setReadTimeout(25000);
            c.setRequestProperty("User-Agent", XUA);
            c.setRequestProperty("Accept", "text/html,application/xhtml+xml,*/*");
            c.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
            try{
                String xck = CookieStore.header(ctx, CookieStore.PLATFORM_X);
                if(xck != null && xck.length() > 10){
                    c.setRequestProperty("Cookie", xck);
                    Log.i(TAG, "X_PROF_WEB_CK len=" + xck.length());
                }
            }catch(Throwable t){}
            int code = c.getResponseCode();
            if(code != 200){
                Log.i(TAG, "X_PROF_WEB http=" + code);
                return out;
            }
            InputStream in = c.getInputStream();
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            int guard = 0;
            while((n = in.read(buf)) > 0){
                bo.write(buf, 0, n);
                if(++guard > 4096) break;   // 上限 32MB
            }
            in.close();
            String html = bo.toString("UTF-8");
            // RSC 里是两层转义，反转义后才能用普通正则
            String u = html.replace("\\\\\"", "\"").replace("\\\"", "\"");
            Log.i(TAG, "X_PROF_WEB len=" + html.length() + " unesc=" + u.length());

            // 推文 ID（按出现顺序）
            List<String> ids = new ArrayList<String>();
            Matcher mi = Pattern.compile("\"id_str\"\\s*:\\s*\"(\\d{15,25})\"").matcher(u);
            java.util.Set<String> seen = new java.util.HashSet<String>();
            while(mi.find()){
                String id = mi.group(1);
                if(seen.add(id)) ids.add(id);
            }
            // 推文正文
            List<String> texts = new ArrayList<String>();
            Matcher mt = Pattern.compile("\"full_text\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(u);
            while(mt.find()) texts.add(unescX(mt.group(1)));

            // 视频直链：按 mediaId 归组，挑分辨率最高的一条
            java.util.Map<String, String> bestVideo = new java.util.LinkedHashMap<String, String>();
            java.util.Map<String, Integer> bestScore = new java.util.HashMap<String, Integer>();
            Matcher mv = Pattern.compile(
                    "https://video\\.twimg\\.com/[^\"\\s]+?/(\\d{15,25})/pu/vid/(?:avc1|hevc)/(\\d+)x(\\d+)/[^\"\\s]+?\\.mp4[^\"\\s]*")
                    .matcher(u);
            while(mv.find()){
                String mid = mv.group(1);
                int w = parseIntSafe(mv.group(2));
                int sc = w;
                Integer old = bestScore.get(mid);
                if(old == null || sc > old.intValue()){
                    bestScore.put(mid, Integer.valueOf(sc));
                    bestVideo.put(mid, mv.group(0));
                }
            }
            // 缩略图：ext_tw_video_thumb/<mediaId>/... 与图片 /media/<id>
            java.util.Map<String, String> thumbs = new java.util.LinkedHashMap<String, String>();
            Matcher mth = Pattern.compile(
                    "https://pbs\\.twimg\\.com/ext_tw_video_thumb/(\\d{15,25})/pu/img/[^\"\\s]+")
                    .matcher(u);
            while(mth.find()){
                if(!thumbs.containsKey(mth.group(1))) thumbs.put(mth.group(1), mth.group(0));
            }
            Log.i(TAG, "X_PROF_WEB ids=" + ids.size() + " text=" + texts.size()
                    + " video=" + bestVideo.size() + " thumb=" + thumbs.size());

            int cnt = Math.min(ids.size(), Math.max(texts.size(), bestVideo.size()));
            for(int i = 0; i < ids.size(); i++){
                String id = ids.get(i);
                ProfileItem it = new ProfileItem();
                it.id = id;
                it.title = i < texts.size() ? texts.get(i) : "";
                it.url = "https://x.com/" + sname + "/status/" + id;
                // 直链优先用视频（列表里可直接下载），没有视频就用缩略图
                String v = bestVideo.get(id);
                if(v == null || v.length() == 0){
                    v = thumbs.get(id);
                }
                if(v != null && v.length() > 0) it.directUrl = v;
                it.cover = thumbs.containsKey(id) ? thumbs.get(id) : "";
                it.kind = (bestVideo.get(id) != null) ? KIND_VIDEO : KIND_IMAGE;
                it.plat = "X";
                out.add(it);
            }
            Log.i(TAG, "X_PROF_WEB_ITEMS n=" + out.size());
            return out;
        }catch(Throwable t){
            Log.i(TAG, "X_PROF_WEB_ERR " + t);
            return out;
        }finally{
            try{ if(c != null) c.disconnect(); }catch(Throwable t){}
        }
    }

    static int parseIntSafe(String s){
        try{ return Integer.parseInt(s.trim()); }catch(Throwable t){ return 0; }
    }

    /** 反转义 JSON 字符串里的 \\n \\" \\uXXXX */
    static String unescX(String s){
        if(s == null) return "";
        StringBuilder sb = new StringBuilder(s.length());
        for(int i = 0; i < s.length(); i++){
            char ch = s.charAt(i);
            if(ch == '\\' && i + 1 < s.length()){
                char nx = s.charAt(i + 1);
                if(nx == 'n'){ sb.append(' '); i++; continue; }
                if(nx == 'r'){ i++; continue; }
                if(nx == 't'){ sb.append(' '); i++; continue; }
                if(nx == '"'){ sb.append('"'); i++; continue; }
                if(nx == '\\'){ sb.append('\\'); i++; continue; }
                if(nx == '/'){ sb.append('/'); i++; continue; }
                if(nx == 'u' && i + 5 < s.length()){
                    try{
                        sb.append((char) Integer.parseInt(s.substring(i + 2, i + 6), 16));
                        i += 5;
                        continue;
                    }catch(Throwable t){}
                }
            }
            sb.append(ch);
        }
        return sb.toString();
    }

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
            c = (HttpURLConnection) new URL(api).openConnection();
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