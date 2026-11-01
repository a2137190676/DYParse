package com.re.douyinparse;

import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/**
 * 轻量平台合集：微视 / AcFun / 最右 / 皮皮虾 / 皮皮搞笑 / 好看视频 / 西瓜视频
 * 全部借鉴 ucmao/media-parser 的本地逆向方案，纯 HTTP + 页面内嵌 JSON，免 Cookie、免服务器。
 */
public class MiscParser {

    static final String TAG = "DYP";

    public static class Item {
        public boolean ok = false;
        public String err = "";
        public String platform = "";
        public String id = "";
        public String title = "";
        public String desc = "";
        public String author = "";
        public String avatar = "";
        public String cover = "";
        public long createdAt = 0;
        public long durMs = 0;
        public long playCount = 0;
        public String playUrl = "";
        public List<String[]> gears = new ArrayList<String[]>();
        public List<String> images = new ArrayList<String>();

        public boolean hasVideo(){ return playUrl != null && playUrl.length() > 0; }
        public boolean hasImages(){ return images != null && images.size() > 0; }
    }

    // ---------- 平台识别 ----------
    static boolean isWeishi(String u){ return u != null && u.toLowerCase().contains("weishi.qq.com"); }
    static boolean isAcfun(String u){ return u != null && u.toLowerCase().contains("acfun.cn"); }
    static boolean isZuiyou(String u){
        if(u == null) return false;
        String s = u.toLowerCase();
        return s.contains("izuiyou.com") || s.contains("xiaochuankeji.cn") || s.contains("zuiyou.com");
    }
    static boolean isPipix(String u){
        if(u == null) return false;
        String s = u.toLowerCase();
        return s.contains("pipix.com") || s.contains("pipixia.com") || s.contains("hulushequ.com");
    }
    static boolean isPipigx(String u){
        if(u == null) return false;
        String s = u.toLowerCase();
        return s.contains("pipigx.com") || s.contains("ippzone.com");
    }
    static boolean isHaokan(String u){ return u != null && u.toLowerCase().contains("haokan.baidu.com"); }
    static boolean isXigua(String u){
        if(u == null) return false;
        String s = u.toLowerCase();
        return s.contains("ixigua.com") || s.contains("xigua.com");
    }

    public static boolean isMiscUrl(String u){
        return isWeishi(u) || isAcfun(u) || isZuiyou(u) || isPipix(u)
            || isPipigx(u) || isHaokan(u) || isXigua(u);
    }

    // ---------- HTTP ----------
    static String UA =
        "Mozilla/5.0 (Linux; Android 13; SM-G991B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36";

    static String get(String url, String referer){
        java.net.HttpURLConnection uc = null;
        try{
            uc = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
            uc.setRequestMethod("GET");
            uc.setInstanceFollowRedirects(true);
            uc.setConnectTimeout(10000);
            uc.setReadTimeout(15000);
            uc.setRequestProperty("User-Agent", UA);
            uc.setRequestProperty("Accept",
                "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8");
            uc.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
            uc.setRequestProperty("Upgrade-Insecure-Requests", "1");
            uc.setRequestProperty("Sec-Fetch-Dest", "document");
            uc.setRequestProperty("Sec-Fetch-Mode", "navigate");
            uc.setRequestProperty("Sec-Fetch-Site", "none");
            uc.setRequestProperty("Sec-Fetch-User", "?1");
            uc.setRequestProperty("Cache-Control", "max-age=0");
            if(referer != null && referer.length() > 0) uc.setRequestProperty("Referer", referer);
            int code = uc.getResponseCode();
            if(code < 200 || code >= 400) return null;
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            java.io.InputStream in = uc.getInputStream();
            byte[] buf = new byte[8192];
            int n;
            while((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            in.close();
            byte[] raw = bo.toByteArray();
            // gzip / deflate 解压（服务器可能不管 Accept-Encoding 直接回 gzip）
            String enc = uc.getContentEncoding();
            if(enc != null && enc.toLowerCase().contains("gzip") || (raw.length > 2 && (raw[0] & 0xFF) == 0x1F && (raw[1] & 0xFF) == 0x8B)){
                try{
                    java.util.zip.GZIPInputStream gz = new java.util.zip.GZIPInputStream(
                        new java.io.ByteArrayInputStream(raw));
                    java.io.ByteArrayOutputStream out2 = new java.io.ByteArrayOutputStream();
                    byte[] b2 = new byte[8192];
                    int n2;
                    while((n2 = gz.read(b2)) > 0) out2.write(b2, 0, n2);
                    gz.close();
                    raw = out2.toByteArray();
                }catch(Throwable t){}
            }
            return new String(raw, "UTF-8");
        }catch(Throwable t){
            return null;
        }finally{
            try{ if(uc != null) uc.disconnect(); }catch(Throwable t){}
        }
    }

    /** 抓 window.xxx = {...}; 形式的内嵌 JSON */
    static JSONObject grabWindowJson(String html, String assign){
        if(html == null) return null;
        try{
            java.util.regex.Matcher m = java.util.regex.Pattern.compile(
                assign + "\\s*=\\s*(?:window\\.[A-Za-z_]+\\s*=\\s*)?(\\{.*?\\})\\s*;",
                java.util.regex.Pattern.DOTALL).matcher(html);
            if(!m.find()) return null;
            String s = m.group(1);
            return new JSONObject(fixSlashes(s));
        }catch(Throwable t){
            Log.i(TAG, "MISC_JSON_ERR " + t);
            return null;
        }
    }

    /** JSON 里的 \u002F 之类的转义（new JSONObject 已可处理，这里只补 // 开头） */
    static String fixSlashes(String s){
        if(s == null) return "";
        return s.replace("\\u002F", "/").replace("\\/", "/");
    }

    static String norm(String u){
        if(u == null) return "";
        String r = fixSlashes(u);
        if(r.startsWith("//")) r = "https:" + r;
        return r;
    }

    /** 主入口：按域名分发 */
    public static Item resolve(String url){
        if(isWeishi(url))  return weishi(url);
        if(isAcfun(url))   return acfun(url);
        if(isHaokan(url))  return haokan(url);
        if(isZuiyou(url))  return zuiyou(url);
        if(isPipigx(url))  return pipigx(url);
        if(isPipix(url))   return pipix(url);
        if(isXigua(url))   return xigua(url);
        Item it = new Item();
        it.err = "不支持的平台";
        return it;
    }

    // ---------- 微视 ----------
    static Item weishi(String url){
        Item it = new Item();
        it.platform = "微视";
        try{
            String html = get(url, "https://isee.weishi.qq.com");
            if(html == null){ it.err = "微视页面拉取失败"; return it; }
            Log.i(TAG, "WS_HTML len=" + html.length());
            JSONObject root = grabWindowJson(html, "window\\.Vise\\.initState");
            if(root == null){ it.err = "未找到 initState"; return it; }
            JSONArray fl = root.optJSONArray("feedsList");
            if(fl == null || fl.length() == 0){ it.err = "无 feedsList"; return it; }
            JSONObject f = fl.optJSONObject(0);
            if(f == null){ it.err = "feedsList 空"; return it; }
            it.id = f.optString("feedId", f.optString("id", ""));
            it.desc = f.optString("feedDesc", "");
            it.title = it.desc;
            it.playUrl = norm(f.optString("videoUrl", ""));
            it.cover = norm(f.optString("videoCover", ""));
            it.durMs = f.optLong("videoDuration", 0) * 1000L;
            JSONObject p = f.optJSONObject("poster");
            if(p != null){
                it.author = p.optString("nick", "");
                it.avatar = norm(p.optString("avatar", ""));
            }
            if(it.playUrl.length() > 0){
                it.gears.add(new String[]{ "原画", it.playUrl, "0", "0" });
            }
            it.ok = it.hasVideo();
            Log.i(TAG, "WS_OK v=" + it.hasVideo() + " author=" + it.author);
        }catch(Throwable t){
            it.err = String.valueOf(t);
            Log.i(TAG, "WS_ERR " + t);
        }
        return it;
    }

    // ---------- AcFun ----------
    static Item acfun(String url){
        Item it = new Item();
        it.platform = "AcFun";
        try{
            // 统一走移动版（PC 会 302）
            String fetchUrl = url;
            java.util.regex.Matcher am = java.util.regex.Pattern
                .compile("acfun\\.cn/v/(ac\\d+)").matcher(url);
            if(am.find()) fetchUrl = "https://m.acfun.cn/v/?ac=" + am.group(1).substring(2);
            Log.i(TAG, "AC_URL " + fetchUrl);
            String html = get(fetchUrl, "https://www.acfun.cn/");
            if(html == null){ it.err = "AcFun 页面拉取失败"; return it; }
            Log.i(TAG, "AC_HTML len=" + html.length());
            // 1) window.videoInfo -> 元数据
            JSONObject vi = grabWindowJson(html, "window\\.videoInfo");
            if(vi != null){
                it.title = vi.optString("title", "");
                it.desc = vi.optString("des", it.title);
                if(it.desc.length() == 0) it.desc = it.title;
                it.cover = norm(vi.optString("videoCover", vi.optString("cover", "")));
                it.id = vi.optString("ac", vi.optString("id", ""));
                it.author = vi.optString("userName", vi.optString("nickname", ""));
                JSONArray vs = vi.optJSONArray("videos");
                if(vs != null && vs.length() > 0){
                    JSONObject v0 = vs.optJSONObject(0);
                    if(v0 != null) it.durMs = v0.optLong("durationMillis", 0);
                }
                long pn = vi.optLong("playNum", 0);
                if(pn > 0) it.playCount = pn;
            }
            // 2) var playInfo -> streams[]
            java.util.regex.Matcher pm = java.util.regex.Pattern.compile(
                "var\\s+playInfo\\s*=\\s*(\\{.*?\\})\\s*;",
                java.util.regex.Pattern.DOTALL).matcher(html);
            if(pm.find()){
                try{
                    JSONObject pi = new JSONObject(fixSlashes(pm.group(1)));
                    JSONArray st = pi.optJSONArray("streams");
                    if(it.durMs == 0) it.durMs = pi.optLong("durationMillis", 0);
                    if(st != null){
                        for(int i = 0; i < st.length(); i++){
                            JSONObject s = st.optJSONObject(i);
                            if(s == null) continue;
                            String label = s.optString("qualityLabel", s.optString("qualityType", ""));
                            if(label.length() == 0) label = "清晰度" + i;
                            long w = s.optLong("width", 0);
                            long h = s.optLong("height", 0);
                            long sz = s.optLong("size", 0);
                            // 优先取 cdnUrls[0].url，没有就取 playUrls[0]
                            String u = "";
                            JSONArray cu = s.optJSONArray("cdnUrls");
                            if(cu != null && cu.length() > 0){
                                JSONObject c0 = cu.optJSONObject(0);
                                if(c0 != null) u = c0.optString("url", "");
                            }
                            if(u.length() == 0){
                                JSONArray pu = s.optJSONArray("playUrls");
                                if(pu != null && pu.length() > 0) u = pu.optString(0, "");
                            }
                            if(u.length() == 0) continue;
                            it.gears.add(new String[]{ label, norm(u),
                                String.valueOf(w), String.valueOf(h) });
                        }
                    }
                    // 无 streams 时取顶层 playUrls
                    if(it.gears.size() == 0){
                        JSONArray pu = pi.optJSONArray("playUrls");
                        if(pu != null && pu.length() > 0){
                            String u = pu.optString(0, "");
                            if(u.length() > 0) it.gears.add(new String[]{ "原画", norm(u), "0", "0" });
                        }
                    }
                }catch(Throwable t){
                    Log.i(TAG, "AC_PLAYINFO_ERR " + t);
                }
            }
            // 3) 按分辨率选最高
            if(it.gears.size() > 1){
                int best = 0;
                long bestScore = -1;
                for(int i = 0; i < it.gears.size(); i++){
                    long sc = parseL(it.gears.get(i)[2]) * parseL(it.gears.get(i)[3]);
                    if(sc > bestScore){ bestScore = sc; best = i; }
                }
                if(best != 0){
                    String[] t = it.gears.get(0);
                    it.gears.set(0, it.gears.get(best));
                    it.gears.set(best, t);
                }
            }
            if(it.gears.size() > 0) it.playUrl = it.gears.get(0)[1];
            it.ok = it.hasVideo() || it.title.length() > 0;
            Log.i(TAG, "AC_OK v=" + it.hasVideo() + " gears=" + it.gears.size()
                + " title=" + it.title + " dur=" + it.durMs);
            if(!it.ok && it.err.length() == 0) it.err = "未取到 AcFun 播放地址";
        }catch(Throwable t){
            it.err = String.valueOf(t);
            Log.i(TAG, "AC_ERR " + t);
        }
        return it;
    }

    static long parseL(String s){
        try{ return Long.parseLong(s); }catch(Throwable t){ return 0; }
    }

    // ---------- 好看视频 ----------
    static Item haokan(String url){
        Item it = new Item();
        it.platform = "好看视频";
        try{
            String html = get(url, "https://haokan.baidu.com/");
            if(html == null){ it.err = "好看页面拉取失败"; return it; }
            Log.i(TAG, "HK_HTML len=" + html.length());
            JSONObject root = grabWindowJson(html, "window\\.__PRELOADED_STATE__");
            if(root == null) root = grabWindowJson(html, "window\\.__INITIAL_STATE__");
            if(root == null){ it.err = "未找到内嵌状态"; return it; }
            JSONObject cur = root.optJSONObject("curVideoMeta");
            if(cur == null){
                JSONObject c = root.optJSONObject("content");
                if(c != null) cur = c.optJSONObject("curVideoMeta");
            }
            if(cur == null){ it.err = "无视频元数据"; return it; }
            it.id = cur.optString("id", "");
            it.title = cur.optString("title", cur.optString("seo_title", ""));
            it.desc = cur.optString("description", it.title);
            if(it.desc.length() == 0) it.desc = it.title;
            it.cover = norm(cur.optString("poster", cur.optString("cover", "")));
            // 作者：实测在 source_name（不是 author.name）
            it.author = cur.optString("source_name", "");
            if(it.author.length() == 0){
                JSONObject a = cur.optJSONObject("author");
                if(a != null) it.author = a.optString("name", "");
            }
            it.avatar = norm(cur.optString("avatar", ""));
            long ct = cur.optLong("publish_time", 0);
            if(ct > 0) it.createdAt = ct < 100000000000L ? ct * 1000L : ct;
            it.durMs = cur.optLong("duration", 0) * 1000L;
            it.playCount = cur.optLong("playcnt", 0);
            // 清晰度：clarityUrl[{key,rank,title,url}]
            JSONArray cl = cur.optJSONArray("clarityUrl");
            if(cl != null){
                for(int i = 0; i < cl.length(); i++){
                    JSONObject o = cl.optJSONObject(i);
                    if(o == null) continue;
                    String u = norm(o.optString("url", ""));
                    if(u.length() == 0) continue;
                    String label = o.optString("title", "");
                    if(label.length() == 0) label = o.optString("key", "清晰度" + i);
                    it.gears.add(new String[]{ label, u, "0", "0" });
                }
            }
            if(it.gears.size() == 0){
                String pu = norm(cur.optString("playurl", cur.optString("dash_url", "")));
                // html5_playurl 是百家号页面地址，不能当视频用
                if(pu.length() > 0 && pu.contains(".mp4")){
                    it.gears.add(new String[]{ "原画", pu, "0", "0" });
                }
            }
            if(it.gears.size() > 0) it.playUrl = it.gears.get(0)[1];
            it.ok = it.hasVideo() || it.title.length() > 0;
            Log.i(TAG, "HK_OK v=" + it.hasVideo() + " gears=" + it.gears.size()
                + " title=" + it.title + " author=" + it.author);
        }catch(Throwable t){
            it.err = String.valueOf(t);
            Log.i(TAG, "HK_ERR " + t);
        }
        return it;
    }

    // ---------- 最右 ----------
    static Item zuiyou(String url){
        Item it = new Item();
        it.platform = "最右";
        try{
            String html = get(url, "https://www.izuiyou.com/");
            if(html == null){ it.err = "最右页面拉取失败"; return it; }
            Log.i(TAG, "ZY_HTML len=" + html.length());
            JSONObject root = grabWindowJson(html, "window\\.__INITIAL_STATE__");
            if(root == null) root = grabWindowJson(html, "window\\.__NUXT__");
            if(root == null){ it.err = "未找到内嵌状态"; return it; }
            // 在整棵 JSON 里递归找 video / videoUrl
            String vurl = deepFindUrl(root, new String[]{ "videoUrl", "video_url", "playUrl", "url" }, "mp4");
            String cover = deepFindUrl(root, new String[]{ "cover", "coverUrl", "thumb", "poster" }, null);
            String desc = deepFindStr(root, new String[]{ "content", "desc", "title" });
            String nick = deepFindStr(root, new String[]{ "nickname", "name", "nick" });
            it.desc = desc;
            it.title = desc;
            it.author = nick;
            it.cover = norm(cover);
            it.playUrl = norm(vurl);
            if(it.playUrl.length() > 0) it.gears.add(new String[]{ "原画", it.playUrl, "0", "0" });
            it.ok = it.hasVideo();
            Log.i(TAG, "ZY_OK v=" + it.hasVideo() + " desc=" + desc);
        }catch(Throwable t){
            it.err = String.valueOf(t);
            Log.i(TAG, "ZY_ERR " + t);
        }
        return it;
    }

    // ---------- 皮皮虾 ----------
    static Item pipix(String url){
        Item it = new Item();
        it.platform = "皮皮虾";
        try{
            String html = get(url, "https://www.pipix.com/");
            if(html == null){ it.err = "皮皮虾页面拉取失败"; return it; }
            Log.i(TAG, "PPX_HTML len=" + html.length());
            JSONObject root = grabWindowJson(html, "window\\.__INITIAL_STATE__");
            String vurl = null, cover = null, desc = null, nick = null;
            if(root != null){
                vurl = deepFindUrl(root, new String[]{ "video_url", "videoUrl", "playUrl", "url" }, "mp4");
                cover = deepFindUrl(root, new String[]{ "cover", "coverUrl", "poster" }, null);
                desc = deepFindStr(root, new String[]{ "content", "title", "desc" });
                nick = deepFindStr(root, new String[]{ "name", "nickname" });
            }
            // 兜底：直接从 HTML 里挖
            if(vurl == null || vurl.length() == 0){
                java.util.regex.Matcher m = java.util.regex.Pattern.compile(
                    "https?:[^\\s\"'<>]{0,40}\\.mp4[^\\s\"'<>]{0,200}").matcher(html);
                if(m.find()) vurl = m.group(0);
            }
            it.desc = desc == null ? "" : desc;
            it.title = it.desc;
            it.author = nick == null ? "" : nick;
            it.cover = norm(cover);
            it.playUrl = norm(vurl);
            if(it.playUrl.length() > 0) it.gears.add(new String[]{ "原画", it.playUrl, "0", "0" });
            it.ok = it.hasVideo();
            Log.i(TAG, "PPX_OK v=" + it.hasVideo());
        }catch(Throwable t){
            it.err = String.valueOf(t);
            Log.i(TAG, "PPX_ERR " + t);
        }
        return it;
    }

    // ---------- 皮皮搞笑 ----------
    static Item pipigx(String url){
        Item it = new Item();
        it.platform = "皮皮搞笑";
        try{
            String html = get(url, "https://www.ippzone.com/");
            if(html == null){ it.err = "皮皮搞笑页面拉取失败"; return it; }
            Log.i(TAG, "PPG_HTML len=" + html.length());
            JSONObject root = grabWindowJson(html, "window\\.__INITIAL_STATE__");
            String vurl = null, cover = null, desc = null, nick = null;
            if(root != null){
                vurl = deepFindUrl(root, new String[]{ "video_url", "videoUrl", "playUrl", "url" }, "mp4");
                cover = deepFindUrl(root, new String[]{ "cover", "poster" }, null);
                desc = deepFindStr(root, new String[]{ "content", "title" });
                nick = deepFindStr(root, new String[]{ "name", "nickname" });
            }
            if(vurl == null || vurl.length() == 0){
                java.util.regex.Matcher m = java.util.regex.Pattern.compile(
                    "https?:[^\\s\"'<>]{0,60}\\.mp4[^\\s\"'<>]{0,200}").matcher(html);
                if(m.find()) vurl = m.group(0);
            }
            it.desc = desc == null ? "" : desc;
            it.title = it.desc;
            it.author = nick == null ? "" : nick;
            it.cover = norm(cover);
            it.playUrl = norm(vurl);
            if(it.playUrl.length() > 0) it.gears.add(new String[]{ "原画", it.playUrl, "0", "0" });
            it.ok = it.hasVideo();
            Log.i(TAG, "PPG_OK v=" + it.hasVideo());
        }catch(Throwable t){
            it.err = String.valueOf(t);
            Log.i(TAG, "PPG_ERR " + t);
        }
        return it;
    }

    // ---------- 西瓜视频 ----------
    /** 西瓜已纯 SPA（页面无数据）；但同一个 ID 在头条 article 页有完整 RENDER_DATA */
    static Item xigua(String url){
        Item it = new Item();
        it.platform = "西瓜视频";
        try{
            String id = "";
            java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("/(\\d{15,20})").matcher(url);
            if(m.find()) id = m.group(1);
            if(id.length() == 0){
                it.err = "无法识别西瓜视频 ID";
                return it;
            }
            Log.i(TAG, "XG_ID " + id);
            ToutiaoParser.TtItem t = ToutiaoParser.resolve("https://m.toutiao.com/article/" + id + "/");
            it.id = id;
            it.title = t.title;
            it.desc = t.desc;
            it.author = t.author;
            it.avatar = t.avatar;
            it.cover = t.cover;
            it.createdAt = t.createdAt;
            it.durMs = t.durMs;
            it.playCount = t.playCount;
            it.playUrl = t.playUrl;
            it.gears = t.gears;
            it.images = t.images;
            it.ok = t.ok;
            if(!it.ok) it.err = (t.err.length() > 0 ? t.err : "西瓜未取到媒体");
            Log.i(TAG, "XG_OK v=" + it.hasVideo() + " gears=" + it.gears.size()
                + " title=" + it.title);
        }catch(Throwable ex){
            it.err = String.valueOf(ex);
            Log.i(TAG, "XG_ERR " + ex);
        }
        return it;
    }

    // ---------- 递归工具 ----------
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
                String k = it.next();
                Object c = j.opt(k);
                if(c instanceof JSONObject || c instanceof JSONArray){
                    String r = deepFindStr(c, keys);
                    if(r != null) return r;
                }
            }
        }else if(o instanceof JSONArray){
            JSONArray a = (JSONArray)o;
            for(int i = 0; i < a.length(); i++){
                Object c = a.opt(i);
                String r = deepFindStr(c, keys);
                if(r != null) return r;
            }
        }
        return null;
    }

    static String deepFindUrl(Object o, String[] keys, String mustContain){
        if(o == null) return null;
        if(o instanceof JSONObject){
            JSONObject j = (JSONObject)o;
            for(int i = 0; i < keys.length; i++){
                String v = j.optString(keys[i], "");
                if(v.length() > 6 && (v.startsWith("http") || v.startsWith("//"))
                        && (mustContain == null || v.toLowerCase().contains(mustContain))){
                    return v;
                }
            }
            java.util.Iterator<String> it = j.keys();
            while(it.hasNext()){
                String k = it.next();
                Object c = j.opt(k);
                if(c instanceof JSONObject || c instanceof JSONArray){
                    String r = deepFindUrl(c, keys, mustContain);
                    if(r != null) return r;
                }
            }
        }else if(o instanceof JSONArray){
            JSONArray a = (JSONArray)o;
            for(int i = 0; i < a.length(); i++){
                Object c = a.opt(i);
                String r = deepFindUrl(c, keys, mustContain);
                if(r != null) return r;
            }
        }
        return null;
    }

    /** 输出 render() 认的 i_* JSON */
    public static String toJson(Item it){
        StringBuilder b = new StringBuilder();
        b.append("{");
        b.append("\"i_platform\":\"").append(esc(it.platform.length() > 0 ? it.platform : "其他")).append("\",");
        kv(b, "i_id", it.id.length() > 0 ? it.id : ("msc_" + Math.abs(it.title.hashCode())));
        if(it.author.length() > 0) kv(b, "i_author", it.author);
        if(it.title.length() > 0) kv(b, "i_desc", it.title);
        if(it.createdAt > 0) kv(b, "i_time", String.valueOf(it.createdAt));
        if(it.durMs > 0) kv(b, "i_dur", String.valueOf(it.durMs));
        if(it.cover.length() > 0) kv(b, "i_cover", it.cover);
        if(it.playCount > 0) kv(b, "i_stats", "播 " + it.playCount);
        if(it.hasVideo()) kv(b, "i_play", it.playUrl);
        if(it.hasImages()){
            StringBuilder im = new StringBuilder();
            for(int i = 0; i < it.images.size(); i++){
                if(i > 0) im.append("|");
                im.append(it.images.get(i));
            }
            kv(b, "i_imgs", im.toString());
        }
        if(it.gears.size() > 0){
            StringBuilder g = new StringBuilder("[");
            for(int i = 0; i < it.gears.size(); i++){
                String[] gg = it.gears.get(i);
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

    /** 返回 [imgs, play, cover]；imgs 用 | 分隔 */
    public static String[] toMedia(Item it){
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
    public static String toText(Item it){
        StringBuilder b = new StringBuilder();
        b.append("【平台】").append(it.platform.length() > 0 ? it.platform : "其他").append("\n");
        if(it.id.length() > 0) b.append("【作品ID】").append(it.id).append("\n");
        if(it.title.length() > 0) b.append("【标题】").append(it.title).append("\n");
        if(it.author.length() > 0) b.append("【作者】").append(it.author).append("\n");
        if(it.desc.length() > 0 && !it.desc.equals(it.title)) b.append("【描述】").append(it.desc).append("\n");
        if(it.createdAt > 0){
            java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm");
            f.setTimeZone(java.util.TimeZone.getTimeZone("Asia/Shanghai"));
            b.append("【发布时间】").append(f.format(new java.util.Date(it.createdAt))).append("\n");
        }
        if(it.durMs > 0) b.append("【时长】").append(it.durMs / 1000).append(" 秒\n");
        if(it.cover.length() > 0) b.append("【封面】").append(it.cover).append("\n");
        if(it.gears.size() > 0){
            for(int i = 0; i < it.gears.size(); i++){
                String[] g = it.gears.get(i);
                String label = (i == 0) ? "【视频直链】" : ("【" + g[0] + "】");
                b.append(label).append(g[1]).append("\n");
            }
        } else if(it.playUrl.length() > 0){
            b.append("【视频直链】").append(it.playUrl).append("\n");
        }
        if(it.images.size() > 0){
            b.append("【图集】").append(it.images.size()).append(" 张\n");
            for(int i = 0; i < it.images.size(); i++){
                b.append("【图").append(i + 1).append("】").append(it.images.get(i)).append("\n");
            }
        }
        if(it.hasVideo()) b.append("【音频下载】点下方「提取音频」，从视频中提取音轨为 MP3\n");
        return b.toString();
    }
}