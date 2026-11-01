package com.re.douyinparse;

import android.content.Context;
import android.util.Log;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * B 站解析器 —— 本地直连（B 站 API 国内可达，无需服务器）。
 * 数据流：view API（免签）拿标题/封面/分P → playurl API（fnval=1）拿整段 mp4。
 */
public class BiliParser {

    static final String TAG = "DYP";
    static final String UA = "Mozilla/5.0 (Linux; Android 12; Pixel 5) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36";
    /** CDN 拉流专用 UA：B 站 CDN 对手机 UA 一律 403，必须用桌面 UA */
    static final String UA_DESKTOP = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36";
    static final String REF = "https://www.bilibili.com/";

    public static class BiliVideo {
        public String aid = "";
        public String bvid = "";
        public String title = "";
        public String cover = "";
        public long durSec = 0;
        public String owner = "";
        public String playUrl = "";      // 整段 mp4（fnval=1 老路，画质上限低）
        /** DASH 视频轨（fnval=16/4048，画质可到 1080P60/4K，需配合 audioUrl 双轨） */
        public String videoUrl = "";
        /** DASH 音频轨 */
        public String audioUrl = "";
        /** 实际拿到的画质 id（80=1080P, 116=1080P60, 120=4K...） */
        public int qualityId = 0;
        public String qualityDesc = "";
        public String err = "";

        public static class Page {
            public int page = 1;
            public String part = "";
            public long cid = 0;
            public long durSec = 0;
            public String aid = "";      // 合集里每集是独立视频，有各自的 aid
            public String bvid = "";
            public String cover = "";
            public boolean fromSeason = false;   // 来自合集（ugc_season）而非分P
        }
        public final List<Page> pages = new ArrayList<Page>();

        /** 合集/分P 名（ugc_season 的 title，或单视频为空） */
        public String seasonTitle = "";
        public long seasonId = 0;

        public boolean hasPages(){ return pages.size() > 1; }
        public boolean ok(){ return playUrl.length() > 0; }
    }

    /** 是否 B 站链接 */
    public static boolean isBiliUrl(String url){
        if(url == null) return false;
        String u = url.toLowerCase();
        return u.contains("bilibili.com") || u.contains("b23.tv");
    }

    /** 纯 av 号 / BV 号输入（支持 "av123"、"BV1xx411c7mD"、"123456" 纯数字）
     *  注意：纯数字容易被当成抖音 ID，只在明确带 av/BV 前缀或长度为 10 的 BV 形态时启用 */
    public static boolean isBareId(String s){
        if(s == null) return false;
        String t = s.trim();
        if(t.length() == 0 || t.length() > 24) return false;
        if(t.indexOf('/') >= 0 || t.indexOf('.') >= 0 || t.indexOf(' ') >= 0) return false;
        // av + 数字
        if(t.matches("(?i)av\\d{6,20}")) return true;
        // BV + 10 位
        if(t.matches("BV[0-9A-Za-z]{10}")) return true;
        if(t.matches("(?i)bv[0-9A-Za-z]{10}")) return true;
        return false;
    }

    /** 纯号转标准链接 */
    public static String bareIdToUrl(String s){
        if(s == null) return null;
        String t = s.trim();
        if(t.length() == 0) return null;
        if(t.toLowerCase().startsWith("av")){
            return "https://www.bilibili.com/video/" + t.toLowerCase();
        }
        if(t.toLowerCase().startsWith("bv") && t.length() >= 12){
            // BV 号大小写敏感：第 3 位起是大小写区分的，只规范前缀
            String body = t.substring(2);
            return "https://www.bilibili.com/video/BV" + body;
        }
        return null;
    }

    /** 抽 aid（av 号或 BV 号） */
    public static String extractAid(String url){
        if(url == null) return "";
        Matcher m = Pattern.compile("av(\\d{6,20})", Pattern.CASE_INSENSITIVE).matcher(url);
        if(m.find()) return m.group(1);
        m = Pattern.compile("/video/(BV[0-9A-Za-z]{10})", Pattern.CASE_INSENSITIVE).matcher(url);
        if(m.find()) return m.group(1);
        m = Pattern.compile("(BV[0-9A-Za-z]{10})").matcher(url);
        if(m.find()) return m.group(1);
        return "";
    }

    /** 本地直连 GET JSON */
    static Context appCtx = null;

    /** 解析入口注入 context，用于读取本地 B 站 Cookie */
    public static void attach(Context c){ appCtx = c; }

    static String httpGet(String url) throws Exception{
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection(java.net.Proxy.NO_PROXY);
        c.setRequestProperty("User-Agent", UA);
        c.setRequestProperty("Referer", REF);
        String ck = biliCookie();
        if(ck.length() > 0) c.setRequestProperty("Cookie", ck);
        c.setInstanceFollowRedirects(true);
        c.setConnectTimeout(15000);
        c.setReadTimeout(20000);
        int code = c.getResponseCode();
        InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while((n = in.read(buf)) > 0) bo.write(buf, 0, n);
        in.close();
        c.disconnect();
        if(code != 200) throw new Exception("HTTP " + code);
        return bo.toString("UTF-8");
    }

    /** 解析 B 站视频（含分 P 信息 + 第 wantP 页直链） */
    public static BiliVideo resolve(Context ctx, String url, int wantP){
        appCtx = ctx;   // 让 biliCookie() 能读到本地登录 Cookie
        BiliVideo v = new BiliVideo();
        String aid = extractAid(url);
        if(aid.length() == 0){ v.err = "链接里没找到 av 号 / BV 号"; return v; }
        boolean isBv = aid.toUpperCase().startsWith("BV");
        try{
            // BV 号必须用 bvid 参数，av 号用 aid 参数；混用返回 -400
            String viewParam = isBv ? ("bvid=" + aid) : ("aid=" + aid);
            String js = httpGet("https://api.bilibili.com/x/web-interface/view?" + viewParam);
            Object root = XParser.Json.parse(js);
            if(!(root instanceof java.util.Map)){ v.err = "响应格式错误"; return v; }
            java.util.Map r = (java.util.Map) root;
            Object codeObj = r.get("code");
            long code = codeObj instanceof Number ? ((Number) codeObj).longValue() : 0;
            if(code != 0){
                v.err = "B站接口错误 code=" + code;
                return v;
            }
            java.util.Map d = (java.util.Map) r.get("data");
            if(d == null){ v.err = "数据为空"; return v; }
            v.aid = String.valueOf(d.get("aid"));
            v.bvid = String.valueOf(d.get("bvid") == null ? "" : d.get("bvid"));
            v.title = String.valueOf(d.get("title") == null ? "" : d.get("title"));
            v.cover = String.valueOf(d.get("pic") == null ? "" : d.get("pic"));
            Object dur = d.get("duration");
            v.durSec = dur instanceof Number ? ((Number) dur).longValue() : 0;
            java.util.Map owner = (java.util.Map) d.get("owner");
            if(owner != null){
                v.owner = String.valueOf(owner.get("name") == null ? "" : owner.get("name"));
            }
            Object pages = d.get("pages");
            if(pages instanceof List){
                for(Object o : (List) pages){
                    if(!(o instanceof java.util.Map)) continue;
                    java.util.Map p = (java.util.Map) o;
                    BiliVideo.Page pg = new BiliVideo.Page();
                    Object pn = p.get("page");
                    pg.page = pn instanceof Number ? ((Number) pn).intValue() : 1;
                    pg.part = String.valueOf(p.get("part") == null ? "" : p.get("part"));
                    Object cid = p.get("cid");
                    pg.cid = cid instanceof Number ? ((Number) cid).longValue() : 0;
                    Object pd = p.get("duration");
                    pg.durSec = pd instanceof Number ? ((Number) pd).longValue() : 0;
                    pg.aid = v.aid;
                    pg.bvid = v.bvid;
                    v.pages.add(pg);
                }
            }

            // ===== 合集（ugc_season）：一个合集里每集是独立视频，各有自己的 aid/cid =====
            Object season = d.get("ugc_season");
            if(season instanceof java.util.Map){
                java.util.Map sm = (java.util.Map) season;
                Object sid = sm.get("id");
                v.seasonId = sid instanceof Number ? ((Number) sid).longValue() : 0;
                v.seasonTitle = String.valueOf(sm.get("title") == null ? "" : sm.get("title"));
                List<BiliVideo.Page> eps = new ArrayList<BiliVideo.Page>();
                Object sections = sm.get("sections");
                if(sections instanceof List){
                    for(Object so : (List) sections){
                        if(!(so instanceof java.util.Map)) continue;
                        Object list = ((java.util.Map) so).get("episodes");
                        if(!(list instanceof List)) continue;
                        for(Object eo : (List) list){
                            if(!(eo instanceof java.util.Map)) continue;
                            java.util.Map em = (java.util.Map) eo;
                            BiliVideo.Page pg = new BiliVideo.Page();
                            pg.fromSeason = true;
                            Object ea = em.get("aid");
                            pg.aid = ea instanceof Number ? String.valueOf(((Number) ea).longValue())
                                    : String.valueOf(em.get("aid") == null ? "" : em.get("aid"));
                            pg.bvid = String.valueOf(em.get("bvid") == null ? "" : em.get("bvid"));
                            pg.part = String.valueOf(em.get("title") == null ? "" : em.get("title"));
                            Object ec = em.get("cid");
                            pg.cid = ec instanceof Number ? ((Number) ec).longValue() : 0;
                            Object ed = em.get("duration");
                            pg.durSec = ed instanceof Number ? ((Number) ed).longValue() : 0;
                            // 合集集封面在 arc.pic
                            Object arc = em.get("arc");
                            if(arc instanceof java.util.Map){
                                Object pic = ((java.util.Map) arc).get("pic");
                                if(pic != null) pg.cover = String.valueOf(pic);
                            }
                            if(pg.cid > 0 || pg.aid.length() > 0) eps.add(pg);
                        }
                    }
                }
                if(eps.size() > 0){
                    // 合集优先：把列表换成合集所有集，并重排 page 序号
                    v.pages.clear();
                    for(int i = 0; i < eps.size(); i++){
                        BiliVideo.Page e = eps.get(i);
                        e.page = i + 1;
                        v.pages.add(e);
                    }
                    Log.i(TAG, "BILI_SEASON id=" + v.seasonId + " eps=" + v.pages.size()
                            + " title=" + v.seasonTitle);
                }
            }

            // 拿第 wantP 集/页直链（合集里每集 aid 不同，必须用该集自己的 aid + cid）
            long cid = 0;
            String playAid = v.aid;
            if(v.pages.size() > 0 && wantP >= 1 && wantP <= v.pages.size()){
                BiliVideo.Page pg = v.pages.get(wantP - 1);
                cid = pg.cid;
                if(pg.aid != null && pg.aid.length() > 0) playAid = pg.aid;
            } else {
                Object c = d.get("cid");
                cid = c instanceof Number ? ((Number) c).longValue() : 0;
            }
            if(cid == 0){
                Object c = d.get("cid");
                cid = c instanceof Number ? ((Number) c).longValue() : 0;
            }
            if(cid > 0){
                // 先清空上一次的 DASH 结果，避免串台
                lastVideoUrl = ""; lastAudioUrl = ""; lastQuality = 0; lastQualityDesc = "";
                String u = getPlayUrl(ctx, playAid, String.valueOf(cid));
                v.playUrl = u;
                // getPlayUrl 内部会把 DASH 结果写进 lastXxx
                v.videoUrl = lastVideoUrl;
                v.audioUrl = lastAudioUrl;
                v.qualityId = lastQuality;
                v.qualityDesc = lastQualityDesc;
            }
            if(v.playUrl.length() == 0){
                v.err = "拿不到视频直链（可能需登录或大会员）";
            }
            return v;
        }catch(Throwable t){
            Log.i(TAG, "BILI_ERR " + t);
            v.err = "解析异常: " + t;
            return v;
        }
    }

    /** buvid3 缓存（B 站 CDN 必须带，否则一律 403） */
    static String buvid3 = "";

    /** 取 B 站 Cookie：本地登录 Cookie（SESSDATA，有它才有 1080P）+ buvid3 */
    static String biliCookie(){
        StringBuilder b = new StringBuilder();
        // 本地登录 Cookie（设置 → Cookie → B站 Cookie），有 SESSDATA 才能拿 1080P
        try{
            if(appCtx != null){
                String login = CookieStore.header(appCtx, CookieStore.PLATFORM_BILI);
                if(login != null && login.length() > 0){
                    b.append(login);
                    if(b.indexOf("SESSDATA=") >= 0 && !biliLoginLogged){
                        biliLoginLogged = true;
                        Log.i(TAG, "BILI_CK_LOGIN yes");
                    }
                }
            }
        }catch(Throwable t){}
        // buvid3：CDN 强制校验，缺了一律 403
        if(buvid3.length() == 0){
            try{
                String js = httpGetRaw("https://api.bilibili.com/x/frontend/finger/spi");
                Object root = XParser.Json.parse(js);
                if(root instanceof java.util.Map){
                    Object d = ((java.util.Map) root).get("data");
                    if(d instanceof java.util.Map){
                        Object b3 = ((java.util.Map) d).get("b_3");
                        if(b3 != null){
                            buvid3 = String.valueOf(b3);
                            Log.i(TAG, "BILI_BUVID3 ok len=" + buvid3.length());
                        }
                    }
                }
            }catch(Throwable t){
                Log.i(TAG, "BILI_BUVID3_ERR " + t);
            }
        }
        if(buvid3.length() > 0){
            if(b.length() > 0) b.append("; ");
            b.append("buvid3=").append(buvid3);
        }
        return b.toString();
    }
    static boolean biliLoginLogged = false;

    /** 不带 Cookie 的裸 GET（专门用于取 buvid3，避免递归） */
    static String httpGetRaw(String url) throws Exception{
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection(java.net.Proxy.NO_PROXY);
        c.setRequestProperty("User-Agent", UA);
        c.setConnectTimeout(10000);
        c.setReadTimeout(12000);
        int code = c.getResponseCode();
        InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while((n = in.read(buf)) > 0) bo.write(buf, 0, n);
        try{ in.close(); }catch(Throwable t){}
        try{ c.disconnect(); }catch(Throwable t){}
        return new String(bo.toByteArray(), "UTF-8");
    }

    /** playurl API：优先 DASH（fnval=4048，画质可到 1080P60 / 4K / Dolby），
     *  拿不到 DASH 时回退 fnval=1 整段 mp4。
     *  B站 CDN 拉流必须用桌面 UA + buvid3 Cookie（手机 UA 一律 403）。
     *  有本地登录 Cookie（SESSDATA）才能拿到 1080P 以上，匿名封顶 480P/720P。 */
    static String getPlayUrl(Context ctx, String aid, String cid){
        // 登录态（SESSDATA）由服务端识别：带 Cookie 请求时自动放行更高画质档位，
        // 不需要在客户端判断，dash.video 里返回什么就取最高的那条。
        // DASH 请求只要一次：qn 传最高，服务端按你的身份自动降级到最高可用画质，
        // 返回的 dash.video 里会列出所有允许的档位，我们取 id 最大的那条即可。
        // （循环问不同 qn 没意义，服务端不会因为 qn 调低就给更高画质）
        int[] qns = { 127 };
        // ===== 1) DASH（fnval=4048 = DASH + 4K + Dolby + 8K）=====
        for(int qn : qns){
            try{
                String js = httpGet("https://api.bilibili.com/x/player/playurl?avid=" + aid
                        + "&cid=" + cid + "&fnval=4048&qn=" + qn + "&fourk=1");
                Object root = XParser.Json.parse(js);
                if(!(root instanceof java.util.Map)) continue;
                java.util.Map d = (java.util.Map) ((java.util.Map) root).get("data");
                if(d == null) continue;
                Object dashObj = d.get("dash");
                if(!(dashObj instanceof java.util.Map)) continue;
                java.util.Map dash = (java.util.Map) dashObj;
                Object vlist = dash.get("video");
                if(!(vlist instanceof List) || ((List) vlist).size() == 0) continue;
                // 视频轨：取 id 最大（画质最高），同 id 取码率最高
                String bestV = "";
                int bestQ = 0;
                long bestBw = -1;
                boolean bestIsAvc = false;
                for(Object vo : (List) vlist){
                    if(!(vo instanceof java.util.Map)) continue;
                    java.util.Map vm = (java.util.Map) vo;
                    Object qo = vm.get("id");
                    int q = qo instanceof Number ? ((Number) qo).intValue() : 0;
                    Object bwo = vm.get("bandwidth");
                    long bw = bwo instanceof Number ? ((Number) bwo).longValue() : 0;
                    Object cdo = vm.get("codecid");
                    int cd = cdo instanceof Number ? ((Number) cdo).intValue() : 0;
                    boolean isAvc = (cd == 7);   // 7=AVC(H.264)，兼容性最好；12=HEVC 13=AV1
                    String u = String.valueOf(vm.get("baseUrl") == null ? "" : vm.get("baseUrl"));
                    if(u.length() == 0) u = String.valueOf(vm.get("base_url") == null ? "" : vm.get("base_url"));
                    if(u.length() == 0) continue;
                    boolean better;
                    if(q != bestQ){
                        better = q > bestQ;
                    } else if(isAvc != bestIsAvc){
                        // 同画质优先 AVC（部分设备解不了 AV1/HEVC）
                        better = isAvc;
                    } else {
                        better = bw > bestBw;
                    }
                    if(better){
                        bestQ = q; bestBw = bw; bestV = u; bestIsAvc = isAvc;
                    }
                }
                if(bestV.length() == 0) continue;
                // 音频轨：优先 Dolby，其次 FLAC，最后普通 audio 里码率最高的
                String bestA = "";
                long bestAbw = -1;
                Object alist = dash.get("audio");
                if(alist instanceof List){
                    for(Object ao : (List) alist){
                        if(!(ao instanceof java.util.Map)) continue;
                        java.util.Map am = (java.util.Map) ao;
                        Object bwo = am.get("bandwidth");
                        long bw = bwo instanceof Number ? ((Number) bwo).longValue() : 0;
                        String u = String.valueOf(am.get("baseUrl") == null ? "" : am.get("baseUrl"));
                        if(u.length() == 0) u = String.valueOf(am.get("base_url") == null ? "" : am.get("base_url"));
                        if(u.length() == 0) continue;
                        if(bw > bestAbw){ bestAbw = bw; bestA = u; }
                    }
                }
                if(bestA.length() == 0){
                    // Dolby / FLAC 兜底
                    Object dolby = dash.get("dolby");
                    if(dolby instanceof java.util.Map){
                        Object au = ((java.util.Map) dolby).get("audio");
                        if(au instanceof List && ((List) au).size() > 0){
                            Object a0 = ((List) au).get(0);
                            if(a0 instanceof java.util.Map){
                                String u = String.valueOf(((java.util.Map) a0).get("baseUrl") == null ? ""
                                        : ((java.util.Map) a0).get("baseUrl"));
                                if(u.length() > 0) bestA = u;
                            }
                        }
                    }
                }
                String qd = qualityName(bestQ);
                Log.i(TAG, "BILI_DASH_OK qn=" + qn + " real=" + bestQ + "(" + qd + ")"
                        + " v=" + bestV.substring(0, Math.min(50, bestV.length()))
                        + " hasAudio=" + (bestA.length() > 0));
                lastVideoUrl = bestV;
                lastAudioUrl = bestA;
                lastQuality = bestQ;
                lastQualityDesc = qd;
                return bestV;
            }catch(Throwable t){
                Log.i(TAG, "BILI_DASH_ERR qn=" + qn + " " + t);
            }
        }
        // ===== 2) 回退 fnval=1 整段 mp4 =====
        // fnval=1 这条路服务端限制更严，需要按 qn 逐个试
        int[] mp4qns = { 80, 74, 64, 32, 16 };
        for(int qn : mp4qns){
            try{
                String js = httpGet("https://api.bilibili.com/x/player/playurl?avid=" + aid
                        + "&cid=" + cid + "&fnval=1&qn=" + qn);
                Object root = XParser.Json.parse(js);
                if(!(root instanceof java.util.Map)) continue;
                java.util.Map d = (java.util.Map) ((java.util.Map) root).get("data");
                if(d == null) continue;
                Object realQ = d.get("quality");
                Object durls = d.get("durl");
                if(durls instanceof List && ((List) durls).size() > 0){
                    Object first = ((List) durls).get(0);
                    if(first instanceof java.util.Map){
                        String u = String.valueOf(((java.util.Map) first).get("url"));
                        if(u != null && u.length() > 0){
                            int q = realQ instanceof Number ? ((Number) realQ).intValue() : 0;
                            Log.i(TAG, "BILI_PLAYURL_MP4 qn=" + qn + " real=" + q
                                    + "(" + qualityName(q) + ")");
                            lastVideoUrl = "";
                            lastAudioUrl = "";
                            lastQuality = q;
                            lastQualityDesc = qualityName(q);
                            return u;
                        }
                    }
                }
            }catch(Throwable t){
                Log.i(TAG, "BILI_PLAYURL qn=" + qn + " " + t);
            }
        }
        return "";
    }

    /** 最近一次解析结果（DASH 双轨用） */
    static String lastVideoUrl = "";
    static String lastAudioUrl = "";
    static int lastQuality = 0;
    static String lastQualityDesc = "";

    /** qn → 可读画质名 */
    static String qualityName(int q){
        switch(q){
            case 127: return "8K";
            case 126: return "Dolby视界";
            case 125: return "HDR";
            case 120: return "4K";
            case 116: return "1080P60";
            case 112: return "1080P+";
            case 80:  return "1080P";
            case 74:  return "720P60";
            case 64:  return "720P";
            case 32:  return "480P";
            case 16:  return "360P";
            default:  return q > 0 ? ("qn" + q) : "未知";
        }
    }

    /** 服务器中转地址（CDN 直连 403 时兜底）。
     *  必须带 key（服务器缓存键），否则每次请求都会重新回源。 */
    static String biliProxyUrl(Context ctx, String cdnUrl, String aid, String cid){
        try{
            String srv = MainActivity.effSrvS(ctx);
            if(srv.length() == 0) return cdnUrl;
            String enc = java.net.URLEncoder.encode(cdnUrl, "UTF-8");
            String key = (aid != null ? aid : "") + "_" + (cid != null && cid.length() > 0 ? cid : "0");
            return srv + "/api/bili/dl?src=" + enc + "&key=" + key;
        }catch(Throwable t){ return cdnUrl; }
    }

    /** 解析 B 站用户主页作品列表（本地 wbi 签名直连）。
     *  返回 items 列表，失败返回 null */
    public static java.util.List<java.util.Map<String, Object>> resolveSpace(String mid, int page, int pageSize){
        try{
            // 1) nav 拿 wbi key
            String navJs = httpGet("https://api.bilibili.com/x/web-interface/nav");
            Object navRoot = XParser.Json.parse(navJs);
            if(!(navRoot instanceof java.util.Map)) return null;
            java.util.Map navData = (java.util.Map) ((java.util.Map) navRoot).get("data");
            if(navData == null) return null;
            java.util.Map wbiImg = (java.util.Map) navData.get("wbi_img");
            if(wbiImg == null) return null;
            BiliWbi wbi = new BiliWbi();
            if(!wbi.setKey(String.valueOf(wbiImg.get("img_url")),
                    String.valueOf(wbiImg.get("sub_url")))){
                return null;
            }
            // 2) 空间接口 + 签名
            java.util.Map<String, String> params = new java.util.HashMap<String, String>();
            params.put("mid", mid);
            params.put("ps", String.valueOf(pageSize));
            params.put("pn", String.valueOf(page));
            params.put("web_location", "1550101");
            java.util.Map<String, String> signed = wbi.sign(params);
            StringBuilder q = new StringBuilder();
            for(java.util.Map.Entry<String, String> e : signed.entrySet()){
                if(q.length() > 0) q.append('&');
                q.append(e.getKey()).append('=').append(e.getValue());
            }
            String js = httpGet("https://api.bilibili.com/x/space/wbi/arc/search?" + q);
            Object root = XParser.Json.parse(js);
            if(!(root instanceof java.util.Map)) return null;
            java.util.Map r = (java.util.Map) root;
            Object codeObj = r.get("code");
            long code = codeObj instanceof Number ? ((Number) codeObj).longValue() : -1;
            if(code != 0){
                Log.i(TAG, "BILI_SPACE code=" + code);
                return null;
            }
            java.util.Map data = (java.util.Map) r.get("data");
            if(data == null) return null;
            java.util.Map list = (java.util.Map) data.get("list");
            if(list == null) return null;
            Object vlist = list.get("vlist");
            java.util.List<java.util.Map<String, Object>> out =
                    new java.util.ArrayList<java.util.Map<String, Object>>();
            if(vlist instanceof List){
                for(Object o : (List) vlist){
                    if(!(o instanceof java.util.Map)) continue;
                    java.util.Map v = (java.util.Map) o;
                    java.util.Map<String, Object> it = new java.util.HashMap<String, Object>();
                    it.put("bvid", String.valueOf(v.get("bvid")));
                    it.put("title", String.valueOf(v.get("title")));
                    it.put("play", String.valueOf(v.get("play")));
                    it.put("created", String.valueOf(v.get("created")));
                    out.add(it);
                }
            }
            return out;
        }catch(Throwable t){
            Log.i(TAG, "BILI_SPACE_ERR " + t);
            return null;
        }
    }

    /** 转 render JSON（只给第 1 页直链；分P列表走列表页签） */
    public static String toResultJson(BiliVideo v){
        StringBuilder b = new StringBuilder(1024);
        b.append('{');
        b.append("\"i_id\":\"").append(v.aid).append("\",");
        b.append("\"i_author\":\"").append(esc(v.owner)).append("\",");
        b.append("\"i_desc\":\"").append(esc(v.title)).append("\",");
        if(v.durSec > 0) b.append("\"i_dur\":\"").append(v.durSec * 1000).append("\",");
        b.append("\"i_cover\":\"").append(esc(v.cover)).append("\",");
        b.append("\"i_platform\":\"B站\",");
        if(v.seasonId > 0){
            b.append("\"i_season_id\":").append(v.seasonId).append(',');
            b.append("\"i_season_title\":\"").append(esc(v.seasonTitle)).append("\",");
            b.append("\"i_season_count\":").append(v.pages.size()).append(',');
        }
        if(v.playUrl.length() > 0) b.append("\"i_play\":\"").append(esc(v.playUrl)).append("\"");
        // DASH 双轨：音频轨单独给出，播放时用 MergingMediaSource 合并
        if(v.audioUrl != null && v.audioUrl.length() > 0){
            b.append(",\"i_audio\":\"").append(esc(v.audioUrl)).append("\"");
        }
        if(v.qualityDesc != null && v.qualityDesc.length() > 0){
            b.append(",\"i_quality\":\"").append(esc(v.qualityDesc)).append("\"");
        }
        b.append('}');
        return b.toString();
    }

    static String esc(String s){
        if(s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}