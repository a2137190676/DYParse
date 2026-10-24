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
    static final String REF = "https://www.bilibili.com/";

    public static class BiliVideo {
        public String aid = "";
        public String bvid = "";
        public String title = "";
        public String cover = "";
        public long durSec = 0;
        public String owner = "";
        public String playUrl = "";      // 整段 mp4
        public String err = "";

        public static class Page {
            public int page = 1;
            public String part = "";
            public long cid = 0;
            public long durSec = 0;
        }
        public final List<Page> pages = new ArrayList<Page>();

        public boolean hasPages(){ return pages.size() > 1; }
        public boolean ok(){ return playUrl.length() > 0; }
    }

    /** 是否 B 站链接 */
    public static boolean isBiliUrl(String url){
        if(url == null) return false;
        String u = url.toLowerCase();
        return u.contains("bilibili.com") || u.contains("b23.tv");
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
    static String httpGet(String url) throws Exception{
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection(java.net.Proxy.NO_PROXY);
        c.setRequestProperty("User-Agent", UA);
        c.setRequestProperty("Referer", REF);
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
                    v.pages.add(pg);
                }
            }
            // 拿第 wantP 页直链
            long cid = v.pages.size() > 0 && wantP >= 1 && wantP <= v.pages.size()
                    ? v.pages.get(wantP - 1).cid
                    : (d.get("cid") instanceof Number ? ((Number) d.get("cid")).longValue() : 0);
            if(cid == 0){
                Object c = d.get("cid");
                cid = c instanceof Number ? ((Number) c).longValue() : 0;
            }
            if(cid > 0){
                v.playUrl = getPlayUrl(ctx, v.aid, String.valueOf(cid));
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

    /** playurl API：fnval=1 整段 mp4，qn 从高往低；拿到 CDN 直链后换成服务器中转
     *  服务器不可达时自动回退 CDN 直链（到期降级：手机直连 CDN 可能 403，但至少可尝试） */
    static String getPlayUrl(Context ctx, String aid, String cid){
        int[] qns = {80, 64, 32, 16};
        for(int qn : qns){
            try{
                String js = httpGet("https://api.bilibili.com/x/player/playurl?avid=" + aid
                        + "&cid=" + cid + "&fnval=1&qn=" + qn);
                Object root = XParser.Json.parse(js);
                if(!(root instanceof java.util.Map)) continue;
                java.util.Map d = (java.util.Map) ((java.util.Map) root).get("data");
                if(d == null) continue;
                Object durls = d.get("durl");
                if(durls instanceof List && ((List) durls).size() > 0){
                    Object first = ((List) durls).get(0);
                    if(first instanceof java.util.Map){
                        String u = String.valueOf(((java.util.Map) first).get("url"));
                        if(u != null && u.length() > 0){
                            // 换成服务器中转（手机直连 CDN 被 403，服务器出口可下）
                            String srv = MainActivity.effSrvS(ctx);
                            if(srv.length() > 0){
                                String enc = java.net.URLEncoder.encode(u, "UTF-8");
                                return srv + "/api/bili/dl?src=" + enc + "&key=" + aid + "_" + cid;
                            }
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
        if(v.playUrl.length() > 0) b.append("\"i_play\":\"").append(esc(v.playUrl)).append("\"");
        b.append('}');
        return b.toString();
    }

    static String esc(String s){
        if(s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}