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
    // 实测：iPhone Safari UA 抓 www.tiktok.com 会被返回精简页（TT_HTTP 空页面），
    // Android Chrome UA 才能拿到完整的 api-data（含 imagePost 全部 32 张）。
    // 真正的关键不在这里，而在 normalize() 有没有把 /photo/ 保住 —— 见该方法注释。
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
        // 实况图：与 images 一一对应的动轨地址（空串 = 该张不是动轨）。
        // TikTok 网页端 api-data 只给静态图，动轨要靠 photomode 视频模板或移动端 API，
        // 这里留出结构，拿到就填，拿不到保持空（下载时按非实况处理）。
        public final java.util.List<String> liveUrls = new java.util.ArrayList<String>();
        public boolean isLive = false;
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
        // 【关键】TikTok 图集/实况图的作品路径是 /photo/{id}，不是 /video/{id}。
        // 原来这里无条件拼 /video/，把 photo 作品改成了 video 路径，
        // 服务端按 video 渲染 → 拿不到 imagePost → 「图集只剩 1 张 / 实况动轨消失」。
        boolean isPhoto = real.indexOf("/photo/") > 0;
        String kind = isPhoto ? "photo" : "video";
        int at = real.indexOf("/@");
        int vid = real.indexOf("/" + kind + "/");
        if(at >= 0 && vid > at){
            String user = real.substring(at, vid);
            return "https://www.tiktok.com" + user + "/" + kind + "/" + id;
        }
        return "https://www.tiktok.com/@i/" + kind + "/" + id;
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

    /** 打开连接：一律直连（用户自己的网络/代理/TUN 自然生效，App 不套 Proxy） */
    static HttpURLConnection open(Context ctx, String url, Session s, Boolean useProxy) throws Exception{
        URL u = new URL(url);
        HttpURLConnection c = (HttpURLConnection) u.openConnection();
        c.setRequestProperty("User-Agent", MUA);
        c.setRequestProperty("Referer", s != null ? s.referer : REFERER);
        String ck = s != null ? s.cookieHeader() : "";
        if(ck.length() == 0){
            // 兜底：Session 是新建的（CookieStore 空）时，从系统 CookieManager 取 WebView 的 cookie。
            // TikTok CDN 对无 Cookie 的请求一律 403，播放器能播是因为它拿到了 cookie。
            try{
                String web = android.webkit.CookieManager.getInstance().getCookie("https://www.tiktok.com/");
                if(web != null && web.length() > 0) ck = web;
            }catch(Throwable t){}
        }
        if(ck.length() > 0) c.setRequestProperty("Cookie", ck);
        // 实测：走代理时 TikTok 短链跳转经常要 12 秒以上，
        // 原来 25 秒读超时在链路抖动时会直接 SocketTimeoutException，
        // 短链展不开 → 「拿不到作品ID」。放宽到 40 秒。
        c.setConnectTimeout(20000);
        c.setReadTimeout(40000);
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
    static int ttDumpLeft = 3;
    static boolean tryParse(String html, TtVideo v){
        // 诊断：把页面落盘，确认 photo 作品的 imagePost 在哪个模板里
        try{
            if(html != null && html.length() > 1000 && ttDumpLeft > 0){
                ttDumpLeft--;
                java.io.File f = new java.io.File("/sdcard/Download/DYParse/tt_dump_"
                        + System.currentTimeMillis() + ".html");
                if(f.getParentFile() != null) f.getParentFile().mkdirs();
                java.io.FileOutputStream fo = new java.io.FileOutputStream(f);
                fo.write(html.getBytes("UTF-8"));
                fo.close();
                Log.i(TAG, "TT_DUMP " + f.getAbsolutePath() + " len=" + html.length()
                        + " hasImagePost=" + html.contains("imagePost")
                        + " hasImageURL=" + html.contains("imageURL")
                        + " hasUniversal=" + html.contains("__UNIVERSAL_DATA_FOR_REHYDRATION__")
                        + " hasApiData=" + html.contains("id=\"api-data\""));
            }
        }catch(Throwable t){ Log.i(TAG, "TT_DUMP_ERR " + t); }

        // 模板 A：__UNIVERSAL_DATA_FOR_REHYDRATION__（新页）
        String json = extractScriptById(html, "__UNIVERSAL_DATA_FOR_REHYDRATION__");
        boolean aOk = false;
        if(json != null && json.length() > 100){
            if(parseUniversal(json, v)){ v.template = "reflow"; aOk = true; }
        }
        // 模板 B：id="api-data"（老页 / @i 占位页）
        // 注意：photo / 实况图作品的 imagePost 只在完整页里，
        // api-data 是精简页，itemStruct 里没有 imagePost。
        // 所以如果 reflow 已经拿到了图片，就别再让 apidata 覆盖。
        if(aOk && v.images.size() > 0) return true;
        String json2 = extractScriptById(html, "api-data");
        if(json2 != null && json2.length() > 100){
            TtVideo tmp = new TtVideo();
            if(parseApiData(json2, tmp)){
                // 精简页只补齐缺失字段，不用它覆盖已有的图片/视频
                if(v.id.length() == 0) v.id = tmp.id;
                if(v.desc.length() == 0) v.desc = tmp.desc;
                if(v.author.length() == 0) v.author = tmp.author;
                if(v.cover.length() == 0) v.cover = tmp.cover;
                if(v.playUrl.length() == 0) v.playUrl = tmp.playUrl;
                if(v.downloadUrl.length() == 0) v.downloadUrl = tmp.downloadUrl;
                if(v.musicUrl.length() == 0){
                    v.musicUrl = tmp.musicUrl;
                    v.musicTitle = tmp.musicTitle;
                    v.musicAuthor = tmp.musicAuthor;
                }
                if(v.images.isEmpty() && !tmp.images.isEmpty()){
                    v.images.addAll(tmp.images);
                }
                if(v.template.length() == 0) v.template = "apidata";
                return true;
            }
        }
        if(aOk) return true;
        // 模板 C：直接找 RENDER_DATA（更老的兜底）
        String json3 = extractScriptById(html, "RENDER_DATA");
        if(json3 != null && json3.length() > 100){
            try{
                String dec = java.net.URLDecoder.decode(json3, "UTF-8");
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
                // 模糊匹配所有 video.detail / photo.detail 变体。
                // 服务器端 tt_module._dig_scope() 就是这么写的：
                //   keys = [k for k in scope.keys() if "video.detail" in k or "photo.detail" in k]
                // 本地原来只写死两个精确 key（webapp.video-detail / webapp.reflow.video.detail），
                // 一旦 TikTok 换成 webapp.photo-detail 或别的前缀就直接 miss，
                // 只能退到 api-data 精简页 —— 那份没有 live 动轨，图集也只剩壳。
                for(Object ko : sm.keySet()){
                    String k = String.valueOf(ko);
                    if(k.indexOf("video.detail") < 0 && k.indexOf("photo.detail") < 0) continue;
                    Object cand = sm.get(ko);
                    if(cand == null) continue;
                    Object cii = (cand instanceof Map) ? ((Map) cand).get("itemInfo") : null;
                    Object cit = (cii instanceof Map) ? ((Map) cii).get("itemStruct") : null;
                    if(cit instanceof Map && s(((Map) cit).get("id")).length() > 0){
                        vd = cand;
                        Log.i(TAG, "TT_SCOPE_HIT " + k);
                        break;
                    }
                }
                if(vd == null) vd = sm.get("webapp.video-detail");
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
                    String liveU = "";
                    if(o instanceof Map){
                        Object urls = ((Map) o).get("imageURL");
                        if(urls instanceof Map){
                            // imageURL.urlList 是**数组**（多个 CDN 候选）。
                            // 原来写成 s(urlList)，s() 是 o.toString()，
                            // List 会变成 "[url1, url2, ...]" 这种字符串，
                            // 既不是空串、也不以 http 开头 → 全被丢掉 → imgs=0。
                            // 正确做法：按 List 取第一个元素。
                            Object ul = ((Map) urls).get("urlList");
                            String u = "";
                            if(ul instanceof List && !((List) ul).isEmpty()){
                                u = s(((List) ul).get(0));
                            }else{
                                u = s(ul);
                            }
                            if(u.startsWith("http")) v.images.add(u);
                        }
                        // 实况图动轨：TikTok 在单张图里额外给 video / playAddr。
                        // 各版本字段名不一样，这里全试一遍。
                        liveU = liveOfImage((Map) o);
                    }
                    // liveUrls 必须与 images 一一对应（含空串），下载时才不会错位
                    v.liveUrls.add(liveU);
                    if(liveU.length() > 0) v.isLive = true;
                }
            }
        }

        Log.i(TAG, "TT_FILL id=" + v.id + " video=" + (v.hasVideo() ? "1" : "0")
                + " imgs=" + v.images.size() + " music=" + (v.musicUrl.length() > 0 ? "1" : "0"));
    }

    /** 从单张 image 里抠实况动轨地址；没有返回 ""。 */
    static String liveOfImage(Map im){
        try{
            // 形态一：images[i].video = { playAddr: {urlList:[...]}, duration:... }
            Object vv = im.get("video");
            String u = pickPlayUrl(vv);
            if(u.length() > 0) return u;
            // 形态二：images[i].playAddr / play_addr 直接挂
            u = pickPlayUrl(im.get("playAddr"));
            if(u.length() > 0) return u;
            u = pickPlayUrl(im.get("play_addr"));
            if(u.length() > 0) return u;
            // 形态三：images[i].dynamicUrl / dynamic_url
            u = s(im.get("dynamicUrl"));
            if(u.length() > 0 && u.startsWith("http")) return u;
            u = s(im.get("dynamic_url"));
            if(u.length() > 0 && u.startsWith("http")) return u;
        }catch(Throwable t){}
        return "";
    }

    /** 从 {urlList:[...]} / 字符串 / url 字段里取一个可播放地址 */
    static String pickPlayUrl(Object o){
        try{
            if(o == null) return "";
            if(o instanceof String){
                String u = (String) o;
                return u.startsWith("http") ? u : "";
            }
            if(o instanceof Map){
                Map m = (Map) o;
                Object ul = m.get("urlList");
                if(ul == null) ul = m.get("url_list");
                if(ul instanceof List){
                    for(Object x : (List) ul){
                        String u = s(x);
                        if(u.startsWith("http")) return u;
                    }
                }
                if(ul instanceof String){
                    String u = (String) ul;
                    if(u.startsWith("http")) return u;
                }
                String u = s(m.get("url"));
                if(u.startsWith("http")) return u;
                u = s(m.get("playAddr"));
                if(u.startsWith("http")) return u;
            }
        }catch(Throwable t){}
        return "";
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
     * 打开一个用于下载的流：一律直连（用户自己的网络/代理/TUN 自然生效）。
     *
     * @return 连接成功（2xx）的 HttpURLConnection，调用方负责读流和 disconnect
     */
    public static HttpURLConnection openDownload(Context ctx, String url, Session s){
        HttpURLConnection c = null;
        try{
            c = open(ctx, url, s, false);
            c.setInstanceFollowRedirects(true);
            c.setRequestProperty("Accept", "*/*");
            c.setRequestProperty("Accept-Language", "en-US,en;q=0.9");
            c.setRequestProperty("Sec-Fetch-Dest", "video");
            c.setRequestProperty("Sec-Fetch-Mode", "no-cors");
            c.setRequestProperty("Sec-Fetch-Site", "same-site");
            // TikTok CDN 对「无 Range 的整段 GET」很敏感，经常直接 403；
            // 而播放器（ExoPlayer）天然会带 Range 头，所以「能播不能下」。
            // 这里补一个 bytes=0- 让请求形态和播放器一致。
            c.setRequestProperty("Range", "bytes=0-");
            if(s != null) c.setRequestProperty("Referer", s.referer);
            int code = c.getResponseCode();
            Log.i(TAG, "TT_DL " + code + " " + trim(url));
            if(code >= 200 && code < 400){
                return c;
            }
            try{ c.disconnect(); }catch(Throwable t){}
        }catch(Throwable t){
            Log.i(TAG, "TT_DL_ERR " + t);
            try{ if(c != null) c.disconnect(); }catch(Throwable x){}
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