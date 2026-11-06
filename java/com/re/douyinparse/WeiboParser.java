package com.re.douyinparse;

import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * 微博解析（借鉴 ucmao/media-parser 的 weibo 逆向方案）。
 *
 * 覆盖：
 *  - 视频专页 video.weibo.com/show?fid=1034:xxx → /tv/api/component 拿多清晰度直链
 *  - 标准博文 m.weibo.cn/statuses/show → page_info.media_info.playback_list
 *  - 图集 pics → osj1080 去水印原图
 *  - 实况 pics[type=livephoto].videoSrc
 *
 * 全部本地 HTTP，不依赖任何第三方付费接口。免登录（必要时自动拿 Visitor 访客）。
 */
public class WeiboParser {

    static final String TAG = "DYP";

    /** 解析结果 */
    public static class WbVideo {
        public boolean ok = false;
        public String err = "";
        public String id = "";
        public String title = "";
        public String author = "";
        public String avatar = "";
        public String cover = "";
        public String desc = "";
        public long durMs = 0;
        public long playCount = 0;
        public long likeCount = 0;
        public long createdAt = 0;
        public String playUrl = "";
        /** 多清晰度：[label, url, 宽, 高] */
        public java.util.List<String[]> gears = new java.util.ArrayList<String[]>();
        /** 图集（已去水印） */
        public java.util.List<String> images = new java.util.ArrayList<String>();
        /** 实况动图（与 images 一一对应，可为空串） */
        public java.util.List<String> livePhotos = new java.util.ArrayList<String>();

        public boolean hasVideo(){ return playUrl != null && playUrl.length() > 0; }
        public boolean hasImages(){ return images != null && images.size() > 0; }
    }

    public static boolean isWeiboUrl(String u){
        if(u == null) return false;
        String s = u.toLowerCase(java.util.Locale.US);
        return s.contains("weibo.com") || s.contains("weibo.cn")
            || s.contains("t.cn/") || s.contains("sinaimg.cn");
    }

    /** 微博 Base62 → 数字 mid */
    static long base62Decode(String str){
        String alphabet = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
        long num = 0;
        for(int i = 0; i < str.length(); i++){
            char c = str.charAt(i);
            int idx = alphabet.indexOf(c);
            if(idx < 0) continue;
            num = num * 62 + idx;
        }
        return num;
    }

    /** 图床去水印：mw690/large/woriginal → osj1080 */
    static String stripWatermark(String u){
        if(u == null) return "";
        String[] marked = {"mw2000", "mw690", "woriginal", "large", "bmiddle", "thumbnail", "orj360", "orj480"};
        for(String m : marked){
            String p = "/" + m + "/";
            if(u.contains(p)){
                return u.replace(p, "/osj1080/");
            }
        }
        return u;
    }

    static String httpGet(String url, String referer, String cookie) {
        java.net.HttpURLConnection uc = null;
        try{
            uc = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
            uc.setRequestMethod("GET");
            uc.setInstanceFollowRedirects(true);
            uc.setConnectTimeout(10000);
            uc.setReadTimeout(15000);
            uc.setRequestProperty("User-Agent",
                "Mozilla/5.0 (Linux; Android 13; SM-G991B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36");
            uc.setRequestProperty("Accept", "application/json, text/plain, */*");
            // 关键：不带这个头，微博 statuses/show 会返回 HTML 而不是 JSON
            uc.setRequestProperty("X-Requested-With", "XMLHttpRequest");
            uc.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9");
            if(referer != null && referer.length() > 0) uc.setRequestProperty("Referer", referer);
            if(cookie != null && cookie.length() > 0) uc.setRequestProperty("Cookie", cookie);
            int code = uc.getResponseCode();
            if(code < 200 || code >= 400) return null;
            StringBuilder sb = new StringBuilder();
            java.io.BufferedReader br = new java.io.BufferedReader(
                new java.io.InputStreamReader(uc.getInputStream(), "UTF-8"));
            String line;
            while((line = br.readLine()) != null) sb.append(line);
            br.close();
            return sb.toString();
        }catch(Throwable t){
            Log.i(TAG, "WB_HTTP_ERR " + t);
            return null;
        }finally{
            try{ if(uc != null) uc.disconnect(); }catch(Throwable t){}
        }
    }

    /** 主入口 */
    public static WbVideo resolve(String url){
        WbVideo v = new WbVideo();
        try{
            // 1) 视频专页 fid=1034:xxx
            java.util.regex.Matcher fm = java.util.regex.Pattern
                .compile("(?:fid=|/tv/show/)(\\d{4}):([0-9A-Za-z]+)").matcher(url);
            if(fm.find()){
                String mediaId = fm.group(2);
                Log.i(TAG, "WB_VIDEO_PATH mediaId=" + mediaId);
                resolveVideoComponent(v, mediaId, url);
                if(v.ok) return v;
            }
            // 2) 获取 visitor 访客 Cookie（微博允许匿名，但需要 visitor 会话才稳）
            String cookie = ensureVisitor();
            // 3) 从 URL 提取 uid + mid
            String uid = "", mid = "";
            java.util.regex.Matcher um = java.util.regex.Pattern
                .compile("(?:m\\.weibo\\.cn|weibo\\.com)/(\\d{5,})/([0-9A-Za-z]+)").matcher(url);
            if(um.find()){
                uid = um.group(1);
                mid = um.group(2);
            }
            if(mid.length() == 0){
                v.err = "无法识别微博链接";
                return v;
            }
            if(!mid.matches("\\d+")){
                long dec = base62Decode(mid);
                Log.i(TAG, "WB_BASE62 " + mid + " -> " + dec);
                if(dec > 0) mid = String.valueOf(dec);
            }
            Log.i(TAG, "WB_STATUS uid=" + uid + " mid=" + mid);
            resolveStatus(v, mid, cookie);
        }catch(Throwable t){
            v.err = String.valueOf(t);
            Log.i(TAG, "WB_RESOLVE_ERR " + t);
        }
        return v;
    }

    /** 自动获取 Visitor 访客凭证（genvisitor 拿 tid → incarnate） */
    static String ensureVisitor(){
        try{
            String gen = httpGet("https://passport.weibo.com/visitor/genvisitor?cb=parser_callback",
                "https://weibo.com/", "");
            if(gen == null) return "";
            java.util.regex.Matcher mt = java.util.regex.Pattern
                .compile("\"tid\":\"([^\"]+)\"").matcher(gen);
            if(!mt.find()){ Log.i(TAG, "WB_VISITOR no tid"); return ""; }
            String tid = mt.group(1);
            String inc = httpGet("https://passport.weibo.com/visitor/visitor?a=incarnate&t="
                + java.net.URLEncoder.encode(tid, "UTF-8")
                + "&w=2&c=095&gc=&cb=parser_callback&from=weibo", "https://weibo.com/", "");
            if(inc == null) return "";
            if(!inc.contains("20000000")){ Log.i(TAG, "WB_VISITOR fail retcode"); return ""; }
            java.util.regex.Matcher c1 = java.util.regex.Pattern
                .compile("\"sub\":\"([^\"]+)\"").matcher(inc);
            java.util.regex.Matcher c2 = java.util.regex.Pattern
                .compile("\"subp\":\"([^\"]+)\"").matcher(inc);
            String subCk = c1.find() ? c1.group(1) : "";
            String subpCk = c2.find() ? c2.group(1) : "";
            if(subCk.length() == 0) return "";
            String ck = "SUB=" + subCk + "; SUBP=" + subpCk;
            Log.i(TAG, "WB_VISITOR ok len=" + ck.length());
            return ck;
        }catch(Throwable t){
            Log.i(TAG, "WB_VISITOR_ERR " + t);
            return "";
        }
    }

    /** 视频专页：/tv/api/component（必须带 PAGE-REFERER，否则 200 空 body） */
    static void resolveVideoComponent(WbVideo v, String mediaId, String srcUrl){
        try{
            String cookie = ensureVisitor();
            String path = "/tv/show/1034:" + mediaId;
            String body = "data=" + java.net.URLEncoder.encode(
                "{\"Component_Play_Playinfo\":{\"oid\":\"" + mediaId + "\"}}", "UTF-8");
            java.net.HttpURLConnection uc = (java.net.HttpURLConnection)
                new java.net.URL("https://weibo.com/tv/api/component").openConnection();
            uc.setRequestMethod("POST");
            uc.setConnectTimeout(10000);
            uc.setReadTimeout(15000);
            uc.setDoOutput(true);
            uc.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            uc.setRequestProperty("Referer", "https://weibo.com" + path);
            uc.setRequestProperty("PAGE-REFERER", path);
            uc.setRequestProperty("X-Requested-With", "XMLHttpRequest");
            uc.setRequestProperty("User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            if(cookie.length() > 0) uc.setRequestProperty("Cookie", cookie);
            java.io.OutputStream os = uc.getOutputStream();
            os.write(body.getBytes("UTF-8"));
            os.close();
            int code = uc.getResponseCode();
            Log.i(TAG, "WB_TV_CODE " + code);
            if(code != 200) return;
            StringBuilder sb = new StringBuilder();
            java.io.BufferedReader br = new java.io.BufferedReader(
                new java.io.InputStreamReader(uc.getInputStream(), "UTF-8"));
            String line;
            while((line = br.readLine()) != null) sb.append(line);
            br.close();
            String txt = sb.toString();
            Log.i(TAG, "WB_TV_BODY len=" + txt.length());
            if(txt.length() == 0) return;
            JSONObject root = new JSONObject(txt);
            JSONObject data = root.optJSONObject("data");
            if(data == null) return;
            JSONObject info = data.optJSONObject("Component_Play_Playinfo");
            if(info == null) return;
            v.id = info.optString("idstr", info.optString("id", ""));
            v.title = info.optString("title", "");
            v.author = info.optString("author", info.optString("nickname", ""));
            v.avatar = normUrl(info.optString("avatar", ""));
            String tx = info.optString("text", "");
            v.desc = tx.replaceAll("<[^>]+>", "").trim();
            v.cover = normUrl(info.optString("cover_image", ""));
            v.playUrl = normUrl(info.optString("stream_url", ""));
            v.durMs = (long)(info.optDouble("duration_time", 0) * 1000);
            v.createdAt = info.optLong("real_date", 0) * 1000L;
            // 统计（play_count 是 "7,014" 这种带逗号的字符串）
            v.likeCount = info.optLong("attitudes_count", 0);
            v.playCount = numOf(info.optString("play_count", "0"));
            // 多清晰度 urls：{"超清 2K": "//f.video...mp4?template=1440x2560..."} 纯字符串映射
            JSONObject urls = info.optJSONObject("urls");
            if(urls != null){
                java.util.Iterator<String> it = urls.keys();
                while(it.hasNext()){
                    String k = it.next();
                    String raw = urls.optString(k, "");
                    if(raw == null || raw.length() == 0) continue;
                    String u = normUrl(raw);
                    int w = 0, h = 0;
                    java.util.regex.Matcher tm = java.util.regex.Pattern
                        .compile("template=(\\d+)x(\\d+)").matcher(u);
                    if(tm.find()){
                        w = Integer.parseInt(tm.group(1));
                        h = Integer.parseInt(tm.group(2));
                    }
                    v.gears.add(new String[]{ k, u, String.valueOf(w), String.valueOf(h) });
                }
            }
            // 主播放地址取最高档
            if(v.gears.size() > 0) v.playUrl = pickBestGear(v.gears);
            v.ok = v.playUrl.length() > 0;
            Log.i(TAG, "WB_TV ok=" + v.ok + " gears=" + v.gears.size()
                + " dur=" + v.durMs + " title=" + v.title);
        }catch(Throwable t){
            Log.i(TAG, "WB_TV_ERR " + t);
        }
    }

    /** 按 label 里的 2K/1080/720/480 挑最高档 */
    static String pickBestGear(java.util.List<String[]> gears){
        String best = "";
        int bestRank = -1;
        for(int i = 0; i < gears.size(); i++){
            String label = gears.get(i)[0];
            String u = gears.get(i)[1];
            int rank = 0;
            if(label.contains("2K") || label.contains("1440")) rank = 4;
            else if(label.contains("1080")) rank = 3;
            else if(label.contains("720")) rank = 2;
            else if(label.contains("480")) rank = 1;
            if(rank > bestRank){ bestRank = rank; best = u; }
        }
        if(best.length() == 0 && gears.size() > 0) best = gears.get(0)[1];
        return best;
    }

    /** play_count 可能是 "7,014" / "1.2万" 这种 */
    static long numOf(String s){
        if(s == null) return 0;
        s = s.replace(",", "").trim();
        if(s.length() == 0) return 0;
        try{
            if(s.endsWith("万")) return (long)(Double.parseDouble(s.substring(0, s.length() - 1)) * 10000);
            if(s.endsWith("亿")) return (long)(Double.parseDouble(s.substring(0, s.length() - 1)) * 100000000);
            return (long)Double.parseDouble(s);
        }catch(Throwable t){ return 0; }
    }

    /** 微博返回 // 开头，补 https: */
    static String normUrl(String u){
        if(u == null) return "";
        if(u.startsWith("//")) return "https:" + u;
        return u;
    }

    /** 标准博文：m.weibo.cn/statuses/show */
    static void resolveStatus(WbVideo v, String mid, String cookie){
        try{
            String api = "https://m.weibo.cn/statuses/show?id=" + mid;
            // 关键：visitor SUB Cookie 会让微博返回「精简版」（page_info 直接没有）。
            // 实测匿名请求才给完整 page_info，所以先用空 Cookie。
            String body = httpGet(api, "https://m.weibo.cn/status/", "");
            Log.i(TAG, "WB_STATUS_ANON len=" + (body == null ? 0 : body.length()));
            JSONObject root = null;
            if(body != null && body.trim().startsWith("{")){
                try{ root = new JSONObject(body); }catch(Throwable t){}
            }
            // 匿名拿不到（HTML 或解析失败）再带 visitor Cookie 重试
            if(root == null){
                Log.i(TAG, "WB_STATUS_ANON_FAIL，改用 visitor Cookie 重试");
                String b2 = httpGet(api, "https://m.weibo.cn/", cookie);
                if(b2 != null && b2.trim().startsWith("{")){
                    try{ root = new JSONObject(b2); }catch(Throwable t){}
                }
                if(root == null){
                    v.err = "微博接口无返回（可能触发了风控）";
                    return;
                }
            }
            // 微博返回的是 {"ok":1,"data":{...}}，必须先解包 data 层
            JSONObject inner = root.optJSONObject("data");
            if(inner != null){
                Log.i(TAG, "WB_UNWRAP data 层");
                root = inner;
            }
            v.id = mid;
            JSONObject user = root.optJSONObject("user");
            if(user != null){
                v.author = user.optString("screen_name", "");
                v.avatar = user.optString("avatar_hd", user.optString("profile_image_url", ""));
            }
            v.desc = root.optString("text_raw", root.optString("text", ""));
            // 去掉 HTML 标签
            v.desc = v.desc.replaceAll("<[^>]+>", "").trim();
            v.createdAt = root.optLong("created_at_timestamp", 0) * 1000L;
            v.likeCount = root.optLong("attitudes_count", 0);
            // 视频
            JSONObject pageInfo = root.optJSONObject("page_info");
            Log.i(TAG, "WB_PAGEINFO " + (pageInfo == null ? "NULL" : pageInfo.toString().substring(
                    0, Math.min(400, pageInfo.toString().length()))));

            // 关键：带 visitor Cookie 时微博会返回「精简版」，page_info 直接没有。
            // 拿不到 page_info 就再发一次不带 Cookie 的请求（curl 实测能拿到完整数据）。
            if(pageInfo == null){
                try{
                    String body2 = httpGet(api, "https://m.weibo.cn/", "");
                    Log.i(TAG, "WB_RETRY_NOCOOKIE len=" + (body2 == null ? 0 : body2.length()));
                    if(body2 != null && body2.length() > 100){
                        JSONObject root2 = new JSONObject(body2);
                        JSONObject pi2 = root2.optJSONObject("page_info");
                        if(pi2 != null){
                            Log.i(TAG, "WB_RETRY_GOT_PAGEINFO");
                            pageInfo = pi2;
                            // 顺带补上用户信息
                            if(v.author.length() == 0){
                                JSONObject u2 = root2.optJSONObject("user");
                                if(u2 != null){
                                    v.author = u2.optString("screen_name", "");
                                    v.avatar = u2.optString("avatar_hd",
                                            u2.optString("profile_image_url", ""));
                                }
                            }
                        }
                    }
                }catch(Throwable t2){
                    Log.i(TAG, "WB_RETRY_ERR " + t2);
                }
            }
            if(pageInfo != null){
                String t = pageInfo.optString("type", "");
                JSONObject mi = pageInfo.optJSONObject("media_info");
                if(mi != null){
                    JSONArray pl = mi.optJSONArray("playback_list");
                    if(pl != null){
                        for(int i = 0; i < pl.length(); i++){
                            JSONObject it = pl.optJSONObject(i);
                            if(it == null) continue;
                            JSONObject pd = it.optJSONObject("play_info");
                            if(pd == null) continue;
                            String u = pd.optString("url", "");
                            if(u.length() == 0) continue;
                            String label = pd.optString("quality_label", pd.optString("type", "视频"));
                            v.gears.add(new String[]{ label, u, "0", "0" });
                        }
                    }
                    String su = mi.optString("stream_url", "");
                    String mp4 = mi.optString("mp4_720p_mp4", mi.optString("mp4_hd_url", ""));
                    // 微博转腾讯视频：stream_url 是 redirect_tencent_video.json?vid=xxx
                    // 该跳转接口已失效（400），必须拿 vid 走腾讯自己的 getinfo
                    if(su.indexOf("redirect_tencent_video") >= 0){
                        String vid = "";
                        int q = su.indexOf("vid=");
                        if(q >= 0){
                            String rest = su.substring(q + 4);
                            StringBuilder sb = new StringBuilder();
                            for(int k = 0; k < rest.length(); k++){
                                char c = rest.charAt(k);
                                if((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                                        || (c >= '0' && c <= '9')) sb.append(c);
                                else break;
                            }
                            vid = sb.toString();
                        }
                        Log.i(TAG, "WB_TENCENT vid=" + vid);
                        if(vid.length() >= 8){
                            TxParser.TxVideo tv = TxParser.parse(
                                    "https://v.qq.com/x/page/" + vid + ".html");
                            if(tv.ok && tv.playUrl.length() > 0){
                                v.playUrl = tv.playUrl;
                                v.durMs = (long)(tv.durSec * 1000);
                                for(String[] g : tv.gears){
                                    v.gears.add(new String[]{ "腾讯" + g[0], g[1], "0", "0" });
                                }
                                Log.i(TAG, "WB_TENCENT_OK td=" + tv.durSec + " gears=" + tv.gears.size());
                            } else {
                                Log.i(TAG, "WB_TENCENT_FAIL " + tv.err);
                            }
                        }
                    } else if(su.length() > 0){
                        v.playUrl = su;
                    } else if(mp4.length() > 0){
                        v.playUrl = mp4;
                    }
                    if(v.gears.size() > 0 && v.playUrl.length() == 0){
                        v.playUrl = v.gears.get(0)[1];
                    }
                    v.cover = mi.optString("page_pic", "");
                }
                if(v.cover.length() == 0) v.cover = pageInfo.optString("page_pic", "");
                if(t.length() > 0) Log.i(TAG, "WB_PAGE_TYPE " + t);
            }
            if(v.cover.length() == 0) v.cover = root.optString("thumbnail_pic", "");
            // 图集 + 实况
            JSONArray pics = root.optJSONArray("pics");
            if(pics != null){
                for(int i = 0; i < pics.length(); i++){
                    JSONObject p = pics.optJSONObject(i);
                    if(p == null) continue;
                    String pu = p.optString("large", p.optString("url", ""));
                    if(pu.length() == 0) continue;
                    v.images.add(stripWatermark(pu));
                    String live = "";
                    String ptype = p.optString("type", "");
                    if("livephoto".equalsIgnoreCase(ptype)){
                        live = p.optString("videoSrc", p.optString("video_src", ""));
                    }
                    v.livePhotos.add(live);
                }
            }
            if(v.images.size() == 0 && v.cover.length() > 0){
                // 无图集时封面也可能就是图
            }
            v.ok = v.hasVideo() || v.hasImages();
            Log.i(TAG, "WB_STATUS ok=" + v.ok + " v=" + v.hasVideo()
                + " imgs=" + v.images.size() + " gears=" + v.gears.size());
            if(!v.ok && v.err.length() == 0) v.err = "微博未返回媒体";
        }catch(Throwable t){
            v.err = String.valueOf(t);
            Log.i(TAG, "WB_STATUS_ERR " + t);
        }
    }

    /** 转成 MainActivity.render() 认识的 JSON */
    public static String toResultJson(WbVideo v){
        StringBuilder b = new StringBuilder();
        b.append("{");
        b.append("\"i_platform\":\"微博\",");
        b.append("\"i_id\":\"").append(esc(v.id)).append("\",");
        b.append("\"i_author\":\"").append(esc(v.author)).append("\",");
        b.append("\"i_desc\":\"").append(esc(v.desc)).append("\",");
        if(v.createdAt > 0) b.append("\"i_time\":\"").append(v.createdAt).append("\",");
        if(v.durMs > 0) b.append("\"i_dur\":").append(v.durMs).append(",");
        if(v.cover.length() > 0) b.append("\"i_cover\":\"").append(esc(v.cover)).append("\",");
        if(v.author.length() > 0){
            StringBuilder st = new StringBuilder();
            st.append("赞 ").append(v.likeCount);
            b.append("\"i_stats\":\"").append(esc(st.toString())).append("\",");
        }
        if(v.hasVideo() && v.playUrl.length() > 0){
            b.append("\"i_play\":\"").append(esc(v.playUrl)).append("\",");
        }
        if(v.hasImages()){
            StringBuilder im = new StringBuilder();
            for(int i = 0; i < v.images.size(); i++){
                if(i > 0) im.append("|");
                im.append(v.images.get(i));
            }
            b.append("\"i_imgs\":\"").append(esc(im.toString())).append("\",");
            // 实况轨
            boolean anyLive = false;
            StringBuilder dy = new StringBuilder();
            for(int i = 0; i < v.livePhotos.size(); i++){
                if(i > 0) dy.append("|");
                String lp = v.livePhotos.get(i);
                if(lp != null && lp.length() > 0) anyLive = true;
                dy.append(lp == null ? "" : lp);
            }
            if(anyLive){
                b.append("\"i_dyn\":\"").append(esc(dy.toString())).append("\",");
                b.append("\"i_live\":1,");
            }
        }
        // 多清晰度档位
        if(v.gears.size() > 0){
            StringBuilder g = new StringBuilder("[");
            for(int i = 0; i < v.gears.size(); i++){
                String[] gg = v.gears.get(i);
                if(i > 0) g.append(",");
                g.append("{\"g\":\"").append(esc(gg[0])).append("\",");
                g.append("\"q\":0,\"br\":0,\"hdr\":0,\"hevc\":0,");
                g.append("\"w\":").append(gg[2]).append(",\"h\":").append(gg[3]).append(",\"fps\":0,");
                g.append("\"u\":\"").append(esc(gg[1])).append("\"}");
            }
            g.append("]");
            b.append("\"i_gears\":\"").append(esc(g.toString())).append("\",");
        }
        String s = b.toString();
        if(s.endsWith(",")) s = s.substring(0, s.length() - 1);
        return s + "}";
    }


    /** 输出 render() 认的 i_* JSON（这样图片/视频才会真正显示） */
    public static String toJson(WbVideo v){
        StringBuilder b = new StringBuilder();
        b.append("{");
        b.append("\"i_platform\":\"微博\",");
        kv(b, "i_id", v.id.length() > 0 ? v.id : ("wb_" + Math.abs(v.desc.hashCode())));
        if(v.author.length() > 0) kv(b, "i_author", v.author);
        if(v.desc.length() > 0) kv(b, "i_desc", v.desc);
        if(v.createdAt > 0) kv(b, "i_time", String.valueOf(v.createdAt));
        if(v.durMs > 0) kv(b, "i_dur", String.valueOf(v.durMs));
        if(v.cover.length() > 0) kv(b, "i_cover", v.cover);
        if(v.author.length() > 0){
            StringBuilder st = new StringBuilder();
            if(v.playCount > 0) st.append("播 ").append(v.playCount);
            if(v.likeCount > 0){ if(st.length() > 0) st.append("  "); st.append("赞 ").append(v.likeCount); }
            if(st.length() > 0) kv(b, "i_stats", st.toString());
        }
        if(v.hasVideo() && v.playUrl.length() > 0) kv(b, "i_play", v.playUrl);
        if(v.hasImages()){
            StringBuilder im = new StringBuilder();
            for(int i = 0; i < v.images.size(); i++){
                if(i > 0) im.append("|");
                im.append(v.images.get(i));
            }
            kv(b, "i_imgs", im.toString());
            boolean anyLive = false;
            StringBuilder dy = new StringBuilder();
            for(int i = 0; i < v.livePhotos.size(); i++){
                if(i > 0) dy.append("|");
                String lp = v.livePhotos.get(i);
                if(lp != null && lp.length() > 0) anyLive = true;
                dy.append(lp == null ? "" : lp);
            }
            if(anyLive){ kv(b, "i_dyn", dy.toString()); kv(b, "i_live", "1"); }
        }
        if(v.gears.size() > 0){
            StringBuilder g = new StringBuilder("[");
            for(int i = 0; i < v.gears.size(); i++){
                String[] gg = v.gears.get(i);
                if(i > 0) g.append(",");
                g.append("{\"g\":\"").append(esc(gg[0])).append("\",");
                g.append("\"q\":0,\"br\":0,\"hdr\":0,\"hevc\":0,");
                g.append("\"w\":").append(safeL(gg[2])).append(",\"h\":").append(safeL(gg[3]))
                 .append(",\"fps\":0,\"u\":\"").append(esc(gg[1])).append("\"}");
            }
            g.append("]");
            kv(b, "i_gears", g.toString());
        }
        String s = b.toString();
        if(s.endsWith(",")) s = s.substring(0, s.length() - 1);
        return s + "}";
    }

    static void kv(StringBuilder b, String k, String v){
        if(v == null) v = "";
        b.append("\"").append(k).append("\":\"").append(esc(v)).append("\",");
    }

    static String safeL(String v){
        if(v == null || v.length() == 0) return "0";
        try{ Long.parseLong(v); return v; }catch(Throwable t){ return "0"; }
    }


    /** 输出 renderCards() 认的「【标签】值」纯文本 */
    public static String toText(WbVideo v){
        StringBuilder b = new StringBuilder();
        b.append("【平台】微博\n");
        if(v.id.length() > 0) b.append("【作品ID】").append(v.id).append("\n");
        if(v.author.length() > 0) b.append("【作者】").append(v.author).append("\n");
        if(v.desc.length() > 0) b.append("【描述】").append(v.desc).append("\n");
        if(v.createdAt > 0){
            java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm");
            f.setTimeZone(java.util.TimeZone.getTimeZone("Asia/Shanghai"));
            b.append("【发布时间】").append(f.format(new java.util.Date(v.createdAt))).append("\n");
        }
        if(v.durMs > 0) b.append("【时长】").append(v.durMs / 1000).append(" 秒\n");
        // 数据
        StringBuilder st = new StringBuilder();
        if(v.playCount > 0) st.append("播放 ").append(v.playCount).append("  ");
        if(v.likeCount > 0) st.append("赞 ").append(v.likeCount);
        if(st.length() > 0) b.append("【数据】").append(st.toString().trim()).append("\n");
        if(v.cover.length() > 0) b.append("【封面】").append(v.cover).append("\n");
        // 清晰度档位
        if(v.gears.size() > 0){
            for(int i = 0; i < v.gears.size(); i++){
                String[] g = v.gears.get(i);
                String label = i == 0 ? "【视频直链】" : "【" + g[0] + "】";
                if(i == 0) b.append("【视频直链】").append(g[1]).append("\n");
                else b.append("【").append(g[0]).append("】").append(g[1]).append("\n");
            }
        } else if(v.playUrl.length() > 0){
            b.append("【视频直链】").append(v.playUrl).append("\n");
        }
        if(v.hasImages()){
            b.append("【图集】").append(v.images.size()).append(" 张\n");
            for(int i = 0; i < v.images.size(); i++){
                boolean live = v.livePhotos != null && i < v.livePhotos.size()
                    && v.livePhotos.get(i) != null && v.livePhotos.get(i).length() > 0;
                b.append("【图").append(i + 1).append(live ? "·实况" : "").append("】")
                 .append(v.images.get(i)).append("\n");
                if(live) b.append("【实况轨").append(i + 1).append("】")
                             .append(v.livePhotos.get(i)).append("\n");
            }
        }
        if(v.hasVideo()) b.append("【音频下载】点下方「提取音频」，从视频中提取音轨为 MP3\n");
        return b.toString();
    }

    /** 返回 [imgs, play, cover]；imgs 用 | 分隔 */
    public static String[] toMedia(WbVideo v){
        StringBuilder im = new StringBuilder();
        for(int i = 0; i < v.images.size(); i++){
            if(i > 0) im.append("|");
            im.append(v.images.get(i));
        }
        return new String[]{ im.toString(), v.playUrl == null ? "" : v.playUrl,
                             v.cover == null ? "" : v.cover };
    }


    static String esc(String s){
        if(s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "").replace("\t", " ");
    }
}