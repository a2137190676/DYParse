package com.re.douyinparse;

import android.content.Context;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * TikTok 解析器（纯 HTTP + 自管 Cookie 会话）。
 *
 * ===== 核心机制（实测得出，别改顺序）=====
 *
 * 1) 页面有两个模板，靠 URL 形式决定：
 *      https://www.tiktok.com/@用户/video/{id}   → 新模板
 *          __UNIVERSAL_DATA_FOR_REHYDRATION__
 *          → __DEFAULT_SCOPE__["webapp.reflow.video.detail"]
 *      https://www.tiktok.com/@i/video/{id}      → 老模板（@i 是占位用户名）
 *          <script id="api-data"> → {"videoDetail":{...}}
 *    两条都要兜，谁先成功用谁。
 *
 * 2) Cookie 会话必须自洽。playAddr 里带 tk=tt_chain_token，
 *    CDN 拿这个参数去比对你请求头里的 cookie。页面和视频必须来自同一个
 *    CookieStore，否则 403。所以这里全程用一个 java.net.CookieManager 自己管，
 *    不碰 WebView 的 CookieManager。
 *
 * 3) 下载分流（实测两套 CDN 行为相反）：
 *      *.tiktokcdn.com            → 直连（走代理反而 SSL_ERROR_SYSCALL）
 *      *.tiktok.com (webapp-prime) → 走代理
 *    先按首选方式试，失败自动换另一种。
 */
public class TtParser {

    static final String TAG = "DYP";
    static final String MUA = "Mozilla/5.0 (Linux; Android 12; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36";
    static final String REFERER = "https://www.tiktok.com/";

    /** 解析结果 */
    public static class TtVideo {
        public String id = "";
        public String desc = "";
        public String author = "";
        public String authorId = "";
        public String playUrl = "";       // 无水印播放地址
        public String downloadUrl = "";   // downloadAddr（备用）
        public String cover = "";
        public String musicTitle = "";
        public String musicAuthor = "";
        public String musicUrl = "";      // 完整音轨（不是视频音轨）
        public long durationMs = 0;
        public long createTime = 0;
        public long diggCount = 0, commentCount = 0, shareCount = 0, playCount = 0;
        public String pageUrl = "";
        public String template = "";      // reflow / apidata，调试用
        public String err = "";
        public final List<String> images = new ArrayList<String>();

        public boolean hasVideo(){ return playUrl != null && playUrl.length() > 0; }
        public boolean hasImages(){ return !images.isEmpty(); }
        public boolean ok(){ return hasVideo() || hasImages(); }
    }

    /** 一次解析会话：页面和后续下载共用同一个 cookie 容器 */
    public static class Session {
        public final java.net.CookieManager cm = new java.net.CookieManager();
        public String referer = REFERER;
        String lastHtml = "";

        public String cookieHeader(){
            try{
                java.util.List<java.net.HttpCookie> list = cm.getCookieStore().get(new java.net.URI(REFERER));
                if(list == null || list.isEmpty()) return "";
                StringBuilder b = new StringBuilder();
                for(java.net.HttpCookie ck : list){
                    if(b.length() > 0) b.append("; ");
                    b.append(ck.getName()).append('=').append(ck.getValue());
                }
                return b.toString();
            }catch(Throwable t){}
            return "";
        }
    }

    // ================= 对外入口 =================

    /** 解析 TikTok 链接。成功返回带数据的对象，失败返回带 err 的对象，永不返回 null */
    public static TtVideo resolve(Context ctx, String url){
        Session s = new Session();
        TtVideo v = resolve(ctx, url, s);
        return v;
    }

    /** 带会话版：调用方可以拿 Session 去下载（同一份 cookie） */
    public static TtVideo resolve(Context ctx, String url, Session s){
        TtVideo v = new TtVideo();
        v.pageUrl = url == null ? "" : url;
        if(url == null || url.length() == 0){ v.err = "空链接"; return v; }
        try{
            String real = normalize(ctx, url, s);
            v.pageUrl = real;
            Log.i(TAG, "TT_REAL " + trim(real));

            String id = extractItemId(real);
            if(id.length() == 0){
                v.err = "拿不到作品ID";
                return v;
            }

            // 页面模板按顺序试：先试规范化的真实链接，再试 @i 占位
            String[] candidates = new String[]{
                real,
                "https://www.tiktok.com/@i/video/" + id,
            };
            for(String pageUrl : candidates){
                String html = httpGet(ctx, pageUrl, s);
                if(html == null || html.length() < 1000){
                    Log.i(TAG, "TT_PAGE_EMPTY " + trim(pageUrl));
                    continue;
                }
                s.lastHtml = html;
                boolean parsed = tryParse(html, v);
                Log.i(TAG, "TT_TRY tpl=" + v.template + " ok=" + parsed
                        + " len=" + html.length() + " url=" + trim(pageUrl));
                if(parsed && v.ok()) break;
            }

            if(!v.ok() && v.err.length() == 0){
                v.err = detectBlock(s.lastHtml);
                if(v.err.length() == 0) v.err = "页面里没找到 itemStruct";
            }
        }catch(Throwable t){
            Log.i(TAG, "TT_RESOLVE_ERR " + t);
            v.err = "" + t;
        }
        return v;
    }

    /** 该 URL 是否属于 TikTok 域 */
    public static boolean isTtUrl(String url){
        if(url == null) return false;
        String u = url.toLowerCase();
        return u.contains("tiktok.com") || u.contains("tiktokv")
            || u.contains("tiktokcdn") || u.contains("byteoversea")
            || u.contains("ibytedtos") || u.contains("muscdn");
    }

    /** 该 CDN host 是不是「直连优先」那类（tiktokcdn 系） */
    public static boolean preferDirect(String url){
        if(url == null) return false;
        String u = url.toLowerCase();
        // 实测：v16m.tiktokcdn.com 直连 200，走代理 SSL_ERROR_SYSCALL
        return u.contains("tiktokcdn.com") && !u.contains("webapp-prime");
    }

    // ================= URL 处理 =================

    /** 短链换完整链接；短链失败时用 @i 占位兜底 */
    static String normalize(Context ctx, String url, Session s){
        boolean isShort = url.contains("tiktok.com/t/")
                       || url.contains("vt.tiktok.com")
                       || url.contains("vm.tiktok.com");
        if(!isShort){
            // 已经是完整链接，把跟踪参数砍掉
            String id = extractItemId(url);
            if(id.length() > 0 && url.indexOf("/video/") > 0){
                int at = url.indexOf("/@");
                int vid = url.indexOf("/video/");
                if(at == 0 - 1 || at < 0){
                    return "https://www.tiktok.com/@i/video/" + id;
                }
                // 保留 @用户名，只砍 query
                return "https://www.tiktok.com" + url.substring(url.indexOf("/@"), vid + "/video/".length() + id.length());
            }
            return url;
        }
        String real = followRedirect(ctx, url, s);
        if(real == null || real.length() == 0) real = url;
        String id = extractItemId(real);
        if(id.length() == 0) return real;
        // 优先保留真实用户名，拿不到就用 @i 占位
        int at = real.indexOf("/@");
        int vid = real.indexOf("/video/");
        if(at >= 0 && vid > at){
            return "https://www.tiktok.com" + real.substring(at, vid + "/video/".length() + id.length());
        }
        return "https://www.tiktok.com/@i/video/" + id;
    }

    /** 从任意 TikTok 链接里抠作品ID */
    public static String extractItemId(String url){
        if(url == null) return "";
        try{
            java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("/(?:video|photo)/(\\d{15,25})").matcher(url);
            if(m.find()) return m.group(1);
            m = java.util.regex.Pattern.compile("[?&]item_id=(\\d{15,25})").matcher(url);
            if(m.find()) return m.group(1);
        }catch(Throwable t){}
        return "";
    }

    /** 跟随 302 拿真实地址（手动跳转，同时收 cookie） */
    static String followRedirect(Context ctx, String url, Session s){
        String cur = url;
        for(int i = 0; i < 6; i++){
            HttpURLConnection c = null;
            try{
                c = open(ctx, cur, s, false);
                c.setInstanceFollowRedirects(false);
                int code = c.getResponseCode();
                storeCookies(s, c);
                if(code >= 300 && code < 400){
                    String loc = c.getHeaderField("Location");
                    if(loc == null || loc.length() == 0) return cur;
                    if(loc.startsWith("/")){
                        URL base = new URL(cur);
                        loc = base.getProtocol() + "://" + base.getHost() + loc;
                    }
                    Log.i(TAG, "TT_REDIR " + code + " -> " + trim(loc));
                    cur = loc;
                    continue;
                }
                return cur;
            }catch(Throwable t){
                Log.i(TAG, "TT_REDIR_ERR " + t);
                return cur;
            }finally{
                try{ if(c != null) c.disconnect(); }catch(Throwable t){}
            }
        }
        return cur;
    }

    /** 打开连接；useProxy 为 null 时按 URL 自动判断 */
    static HttpURLConnection open(Context ctx, String url, Session s, Boolean useProxy) throws Exception{
        URL u = new URL(url);
        boolean proxy;
        if(useProxy == null){
            proxy = MainActivity.staticProxyFor(ctx, url) != null;
        } else {
            proxy = useProxy;
        }
        java.net.Proxy px = proxy ? MainActivity.staticProxyFor(ctx, url) : null;
        if(px == null && useProxy != null && useProxy){
            // 明确要求代理但没配，退回直连
            px = null;
        }
        HttpURLConnection c = (HttpURLConnection)(px != null ? u.openConnection(px) : u.openConnection());
        c.setRequestProperty("User-Agent", MUA);
        c.setRequestProperty("Referer", s != null ? s.referer : REFERER);
        String ck = s != null ? s.cookieHeader() : "";
        if(ck.length() > 0) c.setRequestProperty("Cookie", ck);
        c.setConnectTimeout(15000);
        c.setReadTimeout(25000);
        return c;
    }

    static void storeCookies(Session s, HttpURLConnection c){
        if(s == null) return;
        try{
            // java.net.CookieManager.put() 会按 Set-Cookie 自动解析并写入 CookieStore
            s.cm.put(new java.net.URI(REFERER), c.getHeaderFields());
        }catch(Throwable t){}
    }

    // ================= 网络 =================

    static String httpGet(Context ctx, String url, Session s){
        HttpURLConnection c = null;
        InputStream in = null;
        try{
            c = open(ctx, url, s, null);
            c.setInstanceFollowRedirects(true);
            c.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
            c.setRequestProperty("Accept-Language", "en-US,en;q=0.9");
            c.setRequestProperty("Sec-Fetch-Dest", "document");
            c.setRequestProperty("Sec-Fetch-Mode", "navigate");
            c.setRequestProperty("Sec-Fetch-Site", "none");
            c.setRequestProperty("Upgrade-Insecure-Requests", "1");
            int code = c.getResponseCode();
            storeCookies(s, c);
            Log.i(TAG, "TT_HTTP " + code);
            if(code < 200 || code >= 300) return null;
            in = c.getInputStream();
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n; long got = 0;
            while((n = in.read(buf)) > 0){
                bo.write(buf, 0, n);
                got += n;
                if(got > 8L * 1024 * 1024) break;
            }
            return new String(bo.toByteArray(), "UTF-8");
        }catch(Throwable t){
            Log.i(TAG, "TT_HTTP_ERR " + t);
            return null;
        }finally{
            try{ if(in != null) in.close(); }catch(Throwable t){}
            try{ if(c != null) c.disconnect(); }catch(Throwable t){}
        }
    }

    // ================= 页面解析 =================

    /** 依次尝试两种模板 */
    static boolean tryParse(String html, TtVideo v){
        // 模板 A：__UNIVERSAL_DATA_FOR_REHYDRATION__（新页）
        String json = extractScriptById(html, "__UNIVERSAL_DATA_FOR_REHYDRATION__");
        if(json != null && json.length() > 100){
            if(parseUniversal(json, v)){ v.template = "reflow"; return true; }
        }
        // 模板 B：id="api-data"（老页 / @i 占位页）
        json = extractScriptById(html, "api-data");
        if(json != null && json.length() > 100){
            if(parseApiData(json, v)){ v.template = "apidata"; return true; }
        }
        // 模板 C：直接找 RENDER_DATA（更老的兜底）
        json = extractScriptById(html, "RENDER_DATA");
        if(json != null && json.length() > 100){
            try{
                String dec = java.net.URLDecoder.decode(json, "UTF-8");
                if(parseUniversal(dec, v)){ v.template = "render"; return true; }
            }catch(Throwable t){}
        }
        return false;
    }

    /** 抠 <script id="xxx" ...>body</script> */
    static String extractScriptById(String html, String id){
        try{
            int i = html.indexOf("id=\"" + id + "\"");
            if(i < 0) return null;
            // 往左找到 <script
            int ls = html.lastIndexOf("<script", i);
            if(ls < 0) return null;
            int s = html.indexOf('>', i);
            if(s < 0) return null;
            int e = html.indexOf("</script>", s);
            if(e < 0) return null;
            String body = html.substring(s + 1, e).trim();
            if(body.length() == 0) return null;
            if(body.startsWith("{")) return body;
            int a0 = body.indexOf('{');
            if(a0 >= 0) return body.substring(a0);
            return body;
        }catch(Throwable t){ return null; }
    }

    /** 解析 __UNIVERSAL_DATA_FOR_REHYDRATION__ */
    static boolean parseUniversal(String json, TtVideo v){
        try{
            Object root = new MainActivity.JP(json).parse();
            Object scope = findKey(root, "__DEFAULT_SCOPE__");
            Object vd = null;
            if(scope instanceof Map){
                Map sm = (Map) scope;
                // 新旧两种 key 都试
                vd = sm.get("webapp.video-detail");
                if(vd == null) vd = sm.get("webapp.reflow.video.detail");
            }
            if(vd == null) vd = findKey(root, "videoDetail");
            if(vd == null) return false;
            Object item = null;
            if(vd instanceof Map){
                Object ii = ((Map) vd).get("itemInfo");
                if(ii instanceof Map) item = ((Map) ii).get("itemStruct");
            }
            if(!(item instanceof Map)) item = findKey(root, "itemStruct");
            if(!(item instanceof Map)) return false;
            fill((Map) item, v);
            return true;
        }catch(Throwable t){
            Log.i(TAG, "TT_PARSE_UNIV_ERR " + t);
            return false;
        }
    }

    /** 解析 api-data（{"videoDetail":{...}}） */
    static boolean parseApiData(String json, TtVideo v){
        try{
            Object root = new MainActivity.JP(json).parse();
            Object item = findKey(root, "itemStruct");
            if(!(item instanceof Map)) return false;
            fill((Map) item, v);
            return true;
        }catch(Throwable t){
            Log.i(TAG, "TT_PARSE_API_ERR " + t);
            return false;
        }
    }

    /** 从 itemStruct 抠字段 */
    static void fill(Map it, TtVideo v){
        v.id = s(it.get("id"));
        v.desc = s(it.get("desc"));
        v.createTime = l(it.get("createTime"));

        Object au = it.get("author");
        if(au instanceof Map){
            Map a = (Map) au;
            v.author = s(a.get("nickname"));
            if(v.author.length() == 0) v.author = s(a.get("uniqueId"));
            v.authorId = s(a.get("uniqueId"));
        }

        Object vo = it.get("video");
        if(vo instanceof Map){
            Map vd = (Map) vo;
            v.playUrl = s(vd.get("playAddr"));
            v.downloadUrl = s(vd.get("downloadAddr"));
            v.cover = s(vd.get("cover"));
            if(v.cover.length() == 0) v.cover = s(vd.get("originCover"));
            if(v.cover.length() == 0) v.cover = s(vd.get("reflowCover"));
            long d = l(vd.get("duration"));
            // reflow 模板的 duration 是秒，api-data 也是秒
            v.durationMs = d > 0 ? d * 1000L : 0;

            // 多码率表：挑 h265 / 最高码率
            Object bi = vd.get("bitrateInfo");
            if(bi instanceof List && !((List) bi).isEmpty()){
                String best = pickBestBitrate((List) bi);
                if(best.length() > 0){
                    Log.i(TAG, "TT_BITRATE picked " + trim(best));
                    v.playUrl = best;
                }
            }
        }

        Object mu = it.get("music");
        if(mu instanceof Map){
            Map m = (Map) mu;
            v.musicTitle = s(m.get("title"));
            v.musicAuthor = s(m.get("authorName"));
            v.musicUrl = s(m.get("playUrl"));
        }

        Object st = it.get("stats");
        if(st instanceof Map){
            Map sm = (Map) st;
            v.diggCount = l(sm.get("diggCount"));
            v.commentCount = l(sm.get("commentCount"));
            v.shareCount = l(sm.get("shareCount"));
            v.playCount = l(sm.get("playCount"));
        }

        // 图集（photo 作品）
        Object imgPost = it.get("imagePost");
        if(imgPost instanceof Map){
            Object imgs = ((Map) imgPost).get("images");
            if(imgs instanceof List){
                for(Object o : (List) imgs){
                    if(o instanceof Map){
                        Object urls = ((Map) o).get("imageURL");
                        if(urls instanceof Map){
                            String u = s(((Map) urls).get("urlList"));
                            if(u.length() == 0){
                                Object ul = ((Map) urls).get("urlList");
                                if(ul instanceof List && !((List) ul).isEmpty()) u = s(((List) ul).get(0));
                            }
                            if(u.startsWith("http")) v.images.add(u);
                        }
                    }
                }
            }
        }

        Log.i(TAG, "TT_FILL id=" + v.id + " video=" + (v.hasVideo() ? "1" : "0")
                + " imgs=" + v.images.size() + " music=" + (v.musicUrl.length() > 0 ? "1" : "0"));
    }

    /** 从 bitrateInfo 里挑最好的：优先 h265，其次码率最高的 */
    static String pickBestBitrate(List bi){
        String bestH265 = "", bestAny = "";
        long bestH265Br = -1, bestAnyBr = -1;
        for(Object o : bi){
            if(!(o instanceof Map)) continue;
            Map b = (Map) o;
            String codec = s(b.get("CodecType")).toLowerCase();
            long br = l(b.get("Bitrate"));
            String url = firstOf(b.get("PlayAddr"));
            if(url.length() == 0) continue;
            if(codec.contains("h265") || codec.contains("hevc") || codec.contains("bytevc1")){
                if(br > bestH265Br){ bestH265Br = br; bestH265 = url; }
            }
            if(br > bestAnyBr){ bestAnyBr = br; bestAny = url; }
        }
        return bestH265.length() > 0 ? bestH265 : bestAny;
    }

    /** PlayAddr 结构可能是 {UrlList:[...]} 或 [...] 或字符串 */
    static String firstOf(Object o){
        if(o instanceof String) return (String) o;
        if(o instanceof List){
            for(Object x : (List) o){ if(x instanceof String && ((String) x).startsWith("http")) return (String) x; }
            return "";
        }
        if(o instanceof Map){
            Map m = (Map) o;
            Object ul = m.get("UrlList");
            if(ul != null) return firstOf(ul);
            Object u = m.get("urlList");
            if(u != null) return firstOf(u);
        }
        return "";
    }

    /** 判断是不是风控/失效页 */
    static String detectBlock(String html){
        if(html == null || html.length() == 0) return "页面为空";
        if(html.contains("verify_event") || html.contains("captcha")) return "触发验证码";
        if(html.contains("statusCode") && html.contains("10101")) return "风控拦截(10101)";
        if(html.contains("statusCode") && html.contains("10000")) return "风控拦截(10000)";
        if(html.contains("Couldn't find this account") || html.contains("Video currently unavailable"))
            return "作品不存在或已删除";
        return "";
    }

    /** 递归找 key（和 MainActivity 里的一致，这里独立一份避免循环依赖） */
    static Object findKey(Object o, String k){
        if(o instanceof Map){
            Map m = (Map) o;
            if(m.containsKey(k)) return m.get(k);
            for(Object v : m.values()){
                Object r = findKey(v, k);
                if(r != null) return r;
            }
        } else if(o instanceof List){
            for(Object v : (List) o){
                Object r = findKey(v, k);
                if(r != null) return r;
            }
        }
        return null;
    }

    static String s(Object o){ return o == null ? "" : o.toString(); }

    static long l(Object o){
        if(o instanceof Number) return ((Number) o).longValue();
        try{ return Long.parseLong(s(o).trim()); }catch(Throwable t){ return 0; }
    }

    /** 组装 JSON 键值对（跳过空值） */
    public static void Kv(StringBuilder b, String k, String v){
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

    static String trim(String s){
        if(s == null) return "";
        return s.length() > 130 ? s.substring(0, 130) + "..." : s;
    }

    // ================= 下载分流 =================

    /**
     * 打开一个用于下载的流，自动处理「代理/直连」分流 + 失败降级。
     *
     * 实测两套 CDN 行为相反：
     *   v16m.tiktokcdn.com       直连 200，走代理 SSL_ERROR_SYSCALL
     *   v16-webapp-prime.tiktok.com  必须走代理
     * 所以策略是：按 host 选首选方式，失败自动换另一种。
     *
     * @return 连接成功（2xx）的 HttpURLConnection，调用方负责读流和 disconnect
     */
    public static HttpURLConnection openDownload(Context ctx, String url, Session s){
        boolean directFirst = preferDirect(url);
        // 先首选，再另一种
        Boolean[] order = directFirst
            ? new Boolean[]{ Boolean.FALSE, Boolean.TRUE }
            : new Boolean[]{ Boolean.TRUE, Boolean.FALSE };

        for(int i = 0; i < order.length; i++){
            Boolean useProxy = order[i];
            // 要求代理但没配代理，跳过
            if(useProxy.booleanValue() && MainActivity.staticProxyFor(ctx, url) == null){
                Log.i(TAG, "TT_DL skip proxy (not configured)");
                continue;
            }
            HttpURLConnection c = null;
            try{
                c = open(ctx, url, s, useProxy);
                c.setInstanceFollowRedirects(true);
                c.setRequestProperty("Accept", "*/*");
                c.setRequestProperty("Accept-Language", "en-US,en;q=0.9");
                c.setRequestProperty("Sec-Fetch-Dest", "video");
                c.setRequestProperty("Sec-Fetch-Mode", "no-cors");
                c.setRequestProperty("Sec-Fetch-Site", "same-site");
                if(s != null) c.setRequestProperty("Referer", s.referer);
                int code = c.getResponseCode();
                Log.i(TAG, "TT_DL " + code + " proxy=" + useProxy + " " + trim(url));
                if(code >= 200 && code < 400){
                    return c;
                }
                // 4xx/5xx 换下一种方式
                try{ c.disconnect(); }catch(Throwable t){}
            }catch(Throwable t){
                Log.i(TAG, "TT_DL_ERR proxy=" + useProxy + " " + t);
                try{ if(c != null) c.disconnect(); }catch(Throwable x){}
            }
        }
        return null;
    }

    /** 读长度：Content-Length 或 Content-Range */
    public static long contentLength(HttpURLConnection c){
        try{
            long n = c.getContentLength();
            if(n > 0) return n;
            String cr = c.getHeaderField("Content-Range");
            if(cr != null && cr.contains("/")){
                return Long.parseLong(cr.substring(cr.lastIndexOf('/') + 1).trim());
            }
        }catch(Throwable t){}
        return -1;
    }
}