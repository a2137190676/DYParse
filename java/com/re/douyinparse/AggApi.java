package com.re.douyinparse;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

/**
 * 聚合解析通道：video-crack v2（唯一线路，免 Key，本地直连）
 *
 * 接口：GET http://47.99.158.118/video-crack/v2/parse?content=<分享链接>
 * 返回：{"code":0,"msg":"success","data":{title,url,cover,imageUrl[],music,isThird}}
 *
 * 2026-11-08 全量实测（20 个平台各测一条）：
 *   通：B站（含 b23.tv 短链） 1.4s
 *       抖音短链 v.douyin.com  1.0s
 *       快手短链 v.kuaishou.com 1.2s（返回图集 imageUrl[1]）
 *       TikTok                5.4s
 *       微信视频号             1.3s
 *   不通：YouTube / 小红书 / 微博 / 西瓜 / 好看 / 微视 / AcFun / 知乎 /
 *         X / Instagram / 皮皮虾 -> code=-2「不支持的内容」
 *   注意：抖音/快手只吃「短链」，长链会返回 code=-2。
 *
 * 设计约束：
 *   1) 完全本地直连，不经自建服务器；
 *   2) 免 Key，无任何账号配置，设置页无需入口；
 *   3) 失败/不支持时静默回落本地解析器，绝不因接口故障导致解析不了。
 */
public class AggApi {
    static final String TAG = "DYP";

    /** video-crack v2 解析端点 */
    static final String BASE_VC = "http://47.99.158.118/video-crack/v2/parse";
    /** 即梦AI 视频信息（来自 short_videos/JimengParser.php） */
    static final String JIMENG_API = "https://jimeng.jianying.com/mweb/v1/get_item_info";
    /** 豆包 分享视频信息（来自 short_videos/DoubaoParser.php） */
    static final String DOUBAO_API =
        "https://www.doubao.com/creativity/share/get_video_share_info"
        + "?version_code=20800&language=zh-CN&device_platform=web"
        + "&aid=497858&real_aid=497858&pkg_type=release_version"
        + "&device_id=&pc_version=3.8.3&region=&sys_region="
        + "&samantha_web=1&use-olympus-account=1"
        + "&web_tab_id=4d5d17a6-6729-4c1e-9f55-09f093100f0a";

    static final String UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
        + "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";

    // ---------------- 结果模型 ----------------
    public static class Item {
        public boolean ok = false;
        public String err = "";
        public String src = "video-crack";
        public String platform = "";
        public String type = "video";    // video / images
        public String title = "";
        public String desc = "";
        public String author = "";
        public String avatar = "";
        public String cover = "";
        public String playUrl = "";
        public String quality = "";
        public String musicUrl = "";
        public long durSec = 0;
        public List<String[]> gears = new ArrayList<String[]>();
        public List<String> images = new ArrayList<String>();

        public boolean hasVideo(){ return playUrl != null && playUrl.length() > 0; }
        public boolean hasImages(){ return images != null && !images.isEmpty(); }
    }

    // ---------------- 平台识别 ----------------

    /**
     * video-crack 实测支持的平台。
     * 抖音/快手只认短链，长链会返回 code=-2，所以这里也只在短链时命中。
     */
    public static boolean supported(String url){
        String u = url == null ? "" : url.toLowerCase();
        if(u.contains("bilibili.com") || u.contains("b23.tv")) return true;
        // 抖音【不】走聚合通道：video-crack 只给一条「默认」地址，
        // 而本地直连 DyLocalParser 能拿到 11 档 bit_rate（含尺寸/码率标注），
        // 画质选择完全高一个档次。抖音一律交给 DyLocalParser 处理。
        // （曾因这里返回 true 导致抖音被聚合接管，画质只有单档——已修）
        if(u.contains("kuaishou.com")) return true;
        if(u.contains("tiktok.com")) return true;
        if(u.contains("weixin.qq.com/sph/") || u.contains("channels.weixin.qq.com")) return true;
        // 最右：实测 video-crack 支持（code=0），虽然 MiscParser 也有，但聚合更快
        if(u.contains("xiaochuankeji.cn") || u.contains("izuiyou.com")
                || u.contains("zuiyou.com")) return true;
        // 小红书（含 xhslink 短链）：v2.1.6 起 video-crack 已支持，实测 code=0。
        // 为什么必须走聚合而不是本地 XhsParser：
        //   小红书 2026 年起对网页端强制登录，桌面 UA 直接跳 /login，
        //   移动 UA 虽然能拿到页面，但只能拿到 H5_DTL（476KB）和 h264 259 档（27MB）。
        //   而 video-crack 直接返回 CDN 原图（1.17MB）+ 原画视频（40.6MB），
        //   画质高出一大截且无水印。本地 XhsParser 保留作为兜底。
        if(XhsParser.isXhsUrl(u)) return true;
        // 即梦AI：走官方接口（见 jimeng()）
        // 注意：豆包【不】走这里——原有 DoubaoParser 已实现完整的 get_play_info 播放协议，
        //      比本类里那个只匹配 .mp4 的兜底强得多，必须让它回落到 DoubaoParser。
        if(isJimengUrl(u)) return true;
        return false;
    }

    /** 即梦/剪映：jimeng.jianying.com、jianying.com 分享页 */
    static boolean isJimengUrl(String u){
        if(u == null) return false;
        return u.contains("jimeng.jianying.com") || u.contains("jimeng.")
            || u.contains("jianying.com/share") || u.contains("jianying.com/s/");
    }

    /** 从文本里抠第一个 http 链接 */
    static String firstHttp(String s){
        if(s == null) return "";
        int i = s.indexOf("http");
        if(i < 0) return "";
        int j = i;
        while(j < s.length()){
            char c = s.charAt(j);
            if(c == ' ' || c == '\n' || c == '\r' || c == '\t' || c == '"' || c == '\'') break;
            j++;
        }
        return s.substring(i, j);
    }

    /** 即梦：从 URL 抠 published_item_id（16 位以上数字） */
    static String jimengId(String url){
        String u = url == null ? "" : url;
        // 直接给的是纯数字 ID
        if(u.matches("\\d{16,}")) return u;
        java.util.regex.Matcher m = java.util.regex.Pattern
            .compile("(\\d{16,})").matcher(u);
        if(m.find()) return m.group(1);
        return "";
    }

    /** 是否值得尝试聚合通道（只对已知平台，避免任意网址都打一遍） */
    public static boolean mightHandle(String url){
        if(url == null || url.length() == 0) return false;
        return supported(url);
    }

    // ---------------- 主入口 ----------------

    /**
     * 聚合解析主入口。绝不抛异常；失败返回 ok=false，调用方继续走本地链路。
     * 线路：
     *   0) 即梦AI / 豆包  -> 各自官方接口
     *   1) video-crack v2 -> B站 / 抖音 / 快手 / TikTok / 视频号
     */
    /**
     * 小红书视频档位打分（v2.1.4）。
     * 直链形如 .../stream/79/110/258/xxxx_258.mp4，路径里的 258 / 520 就是档位号。
     *
     * 实测两档差异（同一作品，同一时长 123.767s、同为 3713 帧、同为 720x1280）：
     *   258 → avc1 / 42,577,287 字节 / 2.69 Mbps / x264 crf=22.0
     *   520 → hvc1 / 16,303,678 字节 / 0.95 Mbps / HEVC CRF 30.20
     * 520 虽然编码更新，但 CRF 30.2 比 22.0 差得多，画面糊、缩略图就能看出来。
     * 所以这里按「画质档位号」而不是「体积/编码」排序，258 优先。
     */
    static int gearScore(String u){
        if(u == null || u.length() == 0) return -1;
        // 优先看路径档位号
        int gear = gearOf(u);
        if(gear == 258) return 100;   // h264 高码率，实测画质最好
        if(gear == 259) return 95;    // h264 次档（网页端常见）
        if(gear == 520) return 40;    // h265 低 CRF 档，画质明显差
        if(gear > 0) return 50;       // 其他未知档，居中
        return 0;
    }

    /** 从直链里抠档位号（路径里 110/<gear>/，文件名后缀 _<gear>.mp4） */
    static int gearOf(String u){
        if(u == null) return 0;
        try{
            java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("/110/(\\d+)/[0-9a-fA-F]+_(\\d+)\\.mp4").matcher(u);
            if(m.find()) return Integer.parseInt(m.group(1));
            // 兜底：只看文件名尾部 _数字.mp4
            java.util.regex.Matcher m2 = java.util.regex.Pattern
                .compile("_(\\d{2,4})\\.mp4").matcher(u);
            if(m2.find()) return Integer.parseInt(m2.group(1));
        }catch(Throwable t){}
        return 0;
    }

    /** 最高档分数（258 档 = 100）；重试到达这个分数就停 */
    static final int GEAR_BEST = 100;
    /** 小红书取档最多请求几次（实测 258 档约 60% 命中率，5 次几乎必中） */
    static final int XHS_GEAR_TRIES = 5;

    public static Item resolve(Context ctx, String url){
        Item bad = new Item();
        bad.err = "未命中聚合通道";
        if(ctx == null || url == null || url.length() == 0) return bad;
        if(!supported(url)){
            bad.err = "该平台不在聚合通道支持范围";
            return bad;
        }

        long t0 = System.currentTimeMillis();
        String lu = url.toLowerCase();

        // 线路 0：即梦AI
        if(isJimengUrl(lu)){
            Item jm = jimeng(ctx, url);
            DypLog.i("AGG_JIMENG ok=" + jm.ok + " v=" + jm.hasVideo()
                + " err=" + jm.err + " ms=" + (System.currentTimeMillis() - t0));
            return jm;
        }
        // 豆包不在此处理：supported() 已排除，交给 DoubaoParser（含 get_play_info 协议）

        // 线路 1：video-crack v2
        Item it = call(ctx, url);
        // 小红书：两条独立问题要一起解决
        //   (1) 接口偶发 code=-2「不支持的内容」（实测约 1/10 概率），
        //       首次就失败会很突兀，必须重试；
        //   (2) 接口在两档转码之间随机返回，必须挑高画质档：
        //         .../110/258/xxxx_258.mp4  avc1(h264)  42,577,287 字节  2.69 Mbps  crf=22.0
        //         .../110/520/xxxx_520.mp4  hvc1(h265)  16,303,678 字节  0.95 Mbps  CRF 30.20
        //       同分辨率 720x1280、同 3713 帧、同 123.767s，纯粹是编码参数差。
        //       520 档画质明显更差（用户实测：高画质那条的封面缩略图才有水印，
        //       低画质那条反而看不到 —— 说明高档才是平台原始转码，低档是二次压缩）。
        //   hash 不能跨档改写（实测 404），只能重新请求。
        if(XhsParser.isXhsUrl(url)){
            int score = it.ok && it.hasVideo() ? gearScore(it.playUrl) : -1;
            int tries = 1;
            String bestUrl = (it.ok && it.hasVideo()) ? it.playUrl : null;
            while(score < GEAR_BEST && tries < XHS_GEAR_TRIES){
                tries++;
                Item again = call(ctx, url);
                if(!again.ok || !again.hasVideo()){
                    DypLog.i("XHS_GEAR_RETRY_MISS tries=" + tries + " err=" + again.err);
                    continue;
                }
                int sc = gearScore(again.playUrl);
                if(sc > score){
                    score = sc;
                    bestUrl = again.playUrl;
                    // 首轮失败、这轮成功：把整个 Item 换成成功的这份
                    if(!it.ok){
                        it = again;
                    }
                    if(sc >= GEAR_BEST) break;
                }
            }
            if(it.ok && bestUrl != null && bestUrl.length() > 0 && !bestUrl.equals(it.playUrl)){
                it.playUrl = bestUrl;
                it.gears.clear();
                it.gears.add(new String[]{"默认", bestUrl, ""});
            }
            DypLog.i("XHS_GEAR_PICK ok=" + it.ok + " tries=" + tries + " score=" + score
                + " gear=" + (it.ok ? gearOf(it.playUrl) : 0)
                + " url=" + (it.playUrl == null || it.playUrl.length() == 0
                    ? "" : it.playUrl.substring(0, Math.min(80, it.playUrl.length()))));
        }
        if(it.ok){
            DypLog.i("AGG_OK src=" + it.src + " plat=" + it.platform
                + " v=" + it.hasVideo() + " imgs=" + it.images.size()
                + " ms=" + (System.currentTimeMillis() - t0));
        } else {
            DypLog.i("AGG_MISS err=" + it.err
                + " ms=" + (System.currentTimeMillis() - t0));
        }
        return it;
    }

    /**
     * 即梦AI 视频解析（协议来自 short_videos/JimengParser.php）
     *   POST https://jimeng.jianying.com/mweb/v1/get_item_info
     *   body {"published_item_id":"<16位以上数字>"}
     *   实测返回 {"ret":"2032","errmsg":"itemId not exist"} 说明协议通路正常。
     */
    static Item jimeng(Context ctx, String url){
        Item it = new Item();
        it.src = "jimeng";
        it.platform = "即梦AI";
        try{
            String id = jimengId(url);
            if(id.length() == 0){
                String real = follow(ctx, url);
                id = jimengId(real);
                DypLog.i("AGG_JIMENG_REDIR " + cut(real, 100));
            }
            if(id.length() == 0){
                it.err = "无法提取即梦作品 ID";
                return it;
            }
            String body = httpPostJson(ctx, JIMENG_API,
                "{\"published_item_id\":\"" + id + "\"}", 12000, 20000);
            if(body == null || body.length() == 0){
                it.err = "即梦空响应";
                return it;
            }
            JSONObject j = new JSONObject(body);
            String ret = j.optString("ret", "");
            if(ret.length() > 0 && !"0".equals(ret) && !"200".equals(ret)){
                it.err = "ret=" + ret + " " + j.optString("errmsg", "");
                return it;
            }
            JSONObject d = j.optJSONObject("data");
            if(d == null){ it.err = "即梦 data 为空"; return it; }
            JSONObject item = d.optJSONObject("item");
            if(item == null) item = d;
            JSONObject v = item.optJSONObject("video");
            if(v == null) v = item.optJSONObject("video_info");
            if(v != null) extractJimengVideo(it, v);
            if(it.playUrl.length() == 0) it.playUrl = deepFindUrl(body, ".mp4");
            it.title = firstOf(item, "title", "desc", "prompt");
            it.cover = firstOf(item, "cover_url", "cover");
            it.ok = it.playUrl.length() > 0;
            if(!it.ok) it.err = "即梦未找到视频地址";
        }catch(Throwable t){
            it.err = "EX " + t;
        }
        return it;
    }

    static void extractJimengVideo(Item it, JSONObject v){
        String[][] paths = {
            {"transcoded_video", "origin", "video_url"},
            {"transcoded_video", "720p", "video_url"},
            {"origin_video", "video_url"},
            {"video_url", ""},
        };
        for(int i = 0; i < paths.length; i++){
            String[] p = paths[i];
            try{
                JSONObject cur = v;
                boolean bad = false;
                for(int k = 0; k < p.length - 1; k++){
                    if(p[k].length() == 0){ bad = true; break; }
                    cur = cur.optJSONObject(p[k]);
                    if(cur == null){ bad = true; break; }
                }
                if(bad || cur == null) continue;
                String key = p[p.length - 1];
                if(key.length() == 0) continue;
                String u = cur.optString(key, "");
                if(u.length() > 0){
                    it.playUrl = u;
                    it.gears.add(new String[]{p[0], u, ""});
                    return;
                }
            }catch(Throwable t){}
        }
    }

    /**
     * 豆包 分享视频解析（协议来自 short_videos/DoubaoParser.php）
     *   POST https://www.doubao.com/creativity/share/get_video_share_info
     *   实测 {"code":710010202,"msg":"系统错误"} 说明接口存活，需真实 share_id
     */
    static Item doubao(Context ctx, String url){
        Item it = new Item();
        it.src = "doubao";
        it.platform = "豆包";
        try{
            String id = doubaoId(url);
            if(id.length() == 0){ it.err = "无法提取豆包分享 ID"; return it; }
            String form = "share_id=" + java.net.URLEncoder.encode(id, "UTF-8");
            String body = httpPostForm(ctx, DOUBAO_API, form, 12000, 20000);
            if(body == null || body.length() == 0){
                it.err = "豆包空响应";
                return it;
            }
            JSONObject j = new JSONObject(body);
            int code = j.optInt("code", -1);
            if(code != 0){
                it.err = "code=" + code + " " + j.optString("msg", "");
                return it;
            }
            JSONObject d = j.optJSONObject("data");
            if(d == null){ it.err = "豆包 data 为空"; return it; }
            it.title = firstOf(d, "title", "desc", "prompt");
            it.cover = firstOf(d, "cover_url", "cover");
            it.playUrl = firstOf(d, "video_url", "url", "play_url");
            if(it.playUrl.length() == 0) it.playUrl = deepFindUrl(body, ".mp4");
            if(it.playUrl.length() > 0){
                it.gears.add(new String[]{"默认", it.playUrl, ""});
            }
            it.ok = it.playUrl.length() > 0;
            if(!it.ok) it.err = "豆包未找到视频地址";
        }catch(Throwable t){
            it.err = "EX " + t;
        }
        return it;
    }

    static String doubaoId(String url){
        if(url == null) return "";
        java.util.regex.Matcher m = java.util.regex.Pattern
            .compile("(?:share_id=|/share/|/thread/)([A-Za-z0-9_-]{8,})").matcher(url);
        if(m.find()) return m.group(1);
        return "";
    }

    /** 递归在 JSON 文本里找以 suffix 结尾的 URL（兜底） */
    static String deepFindUrl(String body, String suffix){
        if(body == null) return "";
        java.util.regex.Matcher m = java.util.regex.Pattern
            .compile("(https?:[^\"'\\\\\\s]+?" + java.util.regex.Pattern.quote(suffix) + ")")
            .matcher(body);
        if(m.find()) return m.group(1);
        return "";
    }

    /** 跟随重定向拿最终 URL */
    static String follow(Context ctx, String url){
        HttpURLConnection c = null;
        try{
            c = MainActivity.openConn(ctx, url);
            c.setRequestMethod("GET");
            c.setInstanceFollowRedirects(true);
            c.setConnectTimeout(10000);
            c.setReadTimeout(12000);
            c.setRequestProperty("User-Agent", UA);
            int code = c.getResponseCode();
            String real = c.getURL() == null ? url : c.getURL().toString();
            return code >= 400 ? url : real;
        }catch(Throwable t){
            return url;
        }finally{
            try{ if(c != null) c.disconnect(); }catch(Throwable t){}
        }
    }

    /** POST JSON */
    static String httpPostJson(Context ctx, String url, String json,
                               int connMs, int readMs) throws Exception{
        return httpPost(ctx, url, json.getBytes("UTF-8"),
            "application/json; charset=utf-8", connMs, readMs);
    }

    /** POST form */
    static String httpPostForm(Context ctx, String url, String form,
                               int connMs, int readMs) throws Exception{
        return httpPost(ctx, url, form.getBytes("UTF-8"),
            "application/x-www-form-urlencoded", connMs, readMs);
    }

    static String httpPost(Context ctx, String url, byte[] data, String ct,
                           int connMs, int readMs) throws Exception{
        HttpURLConnection c = null;
        InputStream in = null;
        try{
            c = MainActivity.openConn(ctx, url);
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            c.setInstanceFollowRedirects(true);
            c.setConnectTimeout(connMs);
            c.setReadTimeout(readMs);
            c.setRequestProperty("User-Agent", UA);
            c.setRequestProperty("Content-Type", ct);
            c.setRequestProperty("Accept", "application/json, text/plain, */*");
            c.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9");
            c.getOutputStream().write(data);
            int code = c.getResponseCode();
            in = (code >= 200 && code < 300) ? c.getInputStream() : c.getErrorStream();
            if(in == null) return "";
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            String body = new String(bo.toByteArray(), "UTF-8");
            if(code < 200 || code >= 300){
                DypLog.i("AGG_POST code=" + code + " body=" + cut(body, 140));
            }
            return body;
        }finally{
            try{ if(in != null) in.close(); }catch(Throwable t){}
            try{ if(c != null) c.disconnect(); }catch(Throwable t){}
        }
    }


    /** 调 video-crack v2 并解析 */
    static Item call(Context ctx, String url){
        Item it = new Item();
        try{
            String full = BASE_VC + "?content=" + URLEncoder.encode(url, "UTF-8");
            String body = httpGet(ctx, full, 15000, 25000);
            if(body == null || body.length() == 0){
                it.err = "空响应";
                return it;
            }
            JSONObject j = new JSONObject(body);
            int code = j.optInt("code", -999);
            String msg = j.optString("msg", "");
            if(code != 0){
                it.err = "code=" + code + " " + msg;
                return it;
            }
            JSONObject d = j.optJSONObject("data");
            if(d == null){
                it.err = "data 为空";
                return it;
            }
            fill(it, d);
            it.platform = platformName(url, it);
            it.ok = it.hasVideo() || it.hasImages();
            if(!it.ok) it.err = "无播放地址与图片";
        }catch(Throwable t){
            it.err = "EX " + t;
        }
        return it;
    }

    // ---------------- 字段映射 ----------------
    /** 小红书等 CDN 给的直链是 http://，App 全局开了 cleartext 限制后会被拒；统一升到 https */
    static String httpsify(String u){
        if(u == null || u.length() == 0) return "";
        String s = u.trim();
        if(s.startsWith("http://")) return "https://" + s.substring(7);
        return s;
    }

    static void fill(Item it, JSONObject d){
        it.title = firstOf(d, "title", "desc", "description");
        it.desc = firstOf(d, "desc", "description");
        // v2.1.6：video-crack 对小红书返回的 title 形如
        //   "青椒火腿饼 |||青椒火腿饼 谁能拒绝…🔥 外皮焦香…#馅饼 #面食"
        // 前半是作品名，"|||" 后半是「标题 + 正文」。整串拿来当文件名会把
        // 作品名重复两遍（用户实测：xhs_青椒火腿饼_青椒火腿饼_...）。
        // 规则：按 ||| 拆开，取信息量更大的那一段（一般后半更长），
        // 再把段首与前半相同的前缀去掉，避免重复。
        if(it.title.indexOf("|||") >= 0){
            String[] seg = it.title.split("\\|\\|\\|");
            String head = seg.length > 0 ? seg[0].trim() : "";
            String body = seg.length > 1 ? seg[1].trim() : "";
            for(int i = 2; i < seg.length; i++){
                if(seg[i] != null && seg[i].trim().length() > 0) body = body + " " + seg[i].trim();
            }
            // 后半段如果以作品名开头，去掉这个重复前缀
            if(head.length() > 0 && body.startsWith(head)){
                body = body.substring(head.length()).trim();
            }
            String picked = (body.length() > 0) ? body : head;
            it.title = picked;
            DypLog.i("AGG_TITLE_SPLIT head=" + head + " pickedLen=" + picked.length());
        }
        it.cover = httpsify(firstOf(d, "cover", "pic"));
        it.playUrl = httpsify(firstOf(d, "url", "video", "play"));
        it.musicUrl = s(d, "music");
        it.author = firstOf(d, "author", "auther", "nickname");
        // 图集：imageUrl 是数组，元素可能是字符串或对象
        JSONArray arr = d.optJSONArray("imageUrl");
        if(arr == null) arr = d.optJSONArray("images");
        if(arr != null){
            for(int i = 0; i < arr.length(); i++){
                Object o = arr.opt(i);
                if(o instanceof String){
                    String v = ((String) o).trim();
                    if(v.length() > 0) it.images.add(httpsify(v));
                } else if(o instanceof JSONObject){
                    String v = firstOf((JSONObject) o, "url", "src", "image");
                    if(v.length() > 0) it.images.add(httpsify(v));
                }
            }
        }
        if(it.images.size() > 0 && it.playUrl.length() == 0) it.type = "images";
        // v2.1.6 修复：小红书视频作品的 imageUrl 里塞的其实是「封面」，
        // 有 playUrl 时当图集会让界面走图片预览、视频被丢掉（用户实测：只有缩略图）。
        // 规则：已经有视频直链时，单张「图」视为封面并入 cover，不当图集。
        if(it.playUrl.length() > 0 && it.images.size() == 1){
            if(it.cover.length() == 0) it.cover = it.images.get(0);
            it.images.clear();
            it.type = "video";
        }
        // 主链进清晰度表，便于下载时挑选
        if(it.playUrl.length() > 0){
            it.gears.add(new String[]{"默认", it.playUrl, ""});
        }
        // isThird：非 0 表示第三方直链，记录到 desc 尾部便于排查
        int third = d.optInt("isThird", 0);
        if(third != 0){
            it.quality = "第三方直链";
        }
    }

    static String platformName(String url, Item it){
        String u = url == null ? "" : url.toLowerCase();
        if(u.contains("bilibili.com") || u.contains("b23.tv")) return "哔哩哔哩";
        if(u.contains("douyin.com") || u.contains("iesdouyin.com")) return "抖音";
        if(u.contains("kuaishou.com")) return "快手";
        if(u.contains("tiktok.com")) return "TikTok";
        if(u.contains("weixin.qq.com/sph/")) return "微信视频号";
        if(isJimengUrl(u)) return "即梦AI";
        if(u.contains("doubao.com")) return "豆包";
        if(XhsParser.isXhsUrl(u)) return "小红书";
        return it.type.equals("images") ? "图集" : "视频";
    }

    // ---------------- HTTP ----------------
    static String httpGet(Context ctx, String url, int connMs, int readMs) throws Exception{
        HttpURLConnection c = null;
        InputStream in = null;
        try{
            // 走 MainActivity.openConn，自动套用手机代理（FlClash / 内置代理）
            c = MainActivity.openConn(ctx, url);
            c.setRequestMethod("GET");
            c.setInstanceFollowRedirects(true);
            c.setConnectTimeout(connMs);
            c.setReadTimeout(readMs);
            c.setRequestProperty("User-Agent", UA);
            c.setRequestProperty("Accept", "application/json,text/plain,*/*");
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
                DypLog.i("AGG_HTTP code=" + code + " body=" + cut(body, 150));
            }
            return body;
        }finally{
            try{ if(in != null) in.close(); }catch(Throwable t){}
            try{ if(c != null) c.disconnect(); }catch(Throwable t){}
        }
    }

    // ---------------- 小工具 ----------------
    static String s(JSONObject o, String k){
        if(o == null) return "";
        Object v = o.opt(k);
        if(v == null || v == JSONObject.NULL) return "";
        if(v instanceof String) return ((String) v).trim();
        return "";
    }

    static String firstOf(JSONObject o, String... keys){
        for(String k : keys){
            String v = s(o, k);
            if(v.length() > 0) return v;
        }
        return "";
    }

    static String cut(String s, int n){
        if(s == null) return "";
        return s.length() <= n ? s : s.substring(0, n);
    }

    // ---------------- 展示 ----------------
    public static String toText(Item it){
        StringBuilder sb = new StringBuilder();
        sb.append("【平台】").append(it.platform).append("（聚合通道）\n");
        if(it.title.length() > 0) sb.append("【标题】").append(it.title).append("\n");
        if(it.author.length() > 0) sb.append("【作者】").append(it.author).append("\n");
        if(it.quality.length() > 0) sb.append("【说明】").append(it.quality).append("\n");
        if(it.images.size() > 0) sb.append("【图片】").append(it.images.size()).append(" 张\n");
        if(it.playUrl.length() > 0) sb.append("【直链】").append(it.playUrl).append("\n");
        if(it.musicUrl.length() > 0) sb.append("【音乐】").append(it.musicUrl).append("\n");
        sb.append("【来源】video-crack");
        return sb.toString();
    }

    /** 生成媒体信息 [imgs, play, cover]，imgs 必须用 | 分隔（parseImgs 的约定） */
    public static String[] toMedia(Item it){
        StringBuilder im = new StringBuilder();
        int n = (it.images == null) ? 0 : it.images.size();
        for(int i = 0; i < n; i++){
            if(im.length() > 0) im.append("|");
            im.append(it.images.get(i));
        }
        String play = it.playUrl.length() > 0 ? it.playUrl
                    : (it.gears.isEmpty() ? "" : it.gears.get(0)[1]);
        return new String[]{im.toString(), play, it.cover};
    }
}