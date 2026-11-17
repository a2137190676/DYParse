package com.re.douyinparse;

import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 小红书解析（借鉴 ucmao/media-parser 的 xiaohongshu 逆向方案）。
 *
 * 页面 window.__INITIAL_STATE__ 里就有完整笔记数据（PC 结构与移动 H5 结构双兼容）。
 * 免登录可解析大部分笔记；高频访问会被 302 到 /login，此时需配置 Cookie（设置页支持）。
 */
public class XhsParser {

    static final String TAG = "DYP";

    public static class XhsNote {
        public boolean ok = false;
        public String err = "";
        public String id = "";
        public String title = "";
        public String desc = "";
        public String author = "";
        public String avatar = "";
        public String cover = "";
        public long createdAt = 0;
        public long durMs = 0;
        public long likeCount = 0;
        public String playUrl = "";
        public List<String[]> gears = new ArrayList<String[]>();
        public List<String> images = new ArrayList<String>();

        public boolean hasVideo(){ return playUrl != null && playUrl.length() > 0; }
        public boolean hasImages(){ return images != null && images.size() > 0; }
    }

    public static boolean isXhsUrl(String u){
        if(u == null) return false;
        String s = u.toLowerCase(java.util.Locale.US);
        return s.contains("xiaohongshu.com") || s.contains("xhslink.com")
            || s.contains("xhslink.cn") || s.contains("xhs.com");
    }
    static final String UA_PC =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36";
    static final String UA_M =
        "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1";

    /**
     * 小红书自 2026 年起对桌面 UA 强制跳登录页（/login?redirectPath=...），
     * 只有「移动 UA + /discovery/item/ 路径 + xsec_token」才返回真实笔记数据。
     * 实测：PC UA → 36859 字节登录页（noteDetailMap 不存在）
     *       移动 UA → 63618 字节真实页面（LAUNCHER_SSR_STORE_PAGE_DATA 存在）
     * 所以 UA_M 必须排在第一位。
     */
    static final String[] UA_TRY = new String[]{ UA_M, UA_PC };

    /** 页面里取真实数据的位置标记，按优先级排列 */
    static final String[] STATE_MARKERS = new String[]{
        "LAUNCHER_SSR_STORE_PAGE_DATA",   // 移动 H5 真数据（首选）
        "__INITIAL_STATE__",              // PC / 老结构
        "window.__STORE__"
    };

    /** 判断一段 HTML 是不是「有笔记数据」，而不是登录页/空壳 */
    static boolean looksLikeNote(String html){
        if(html == null || html.length() < 2000) return false;
        if(html.indexOf("noteData") < 0 && html.indexOf("noteDetailMap") < 0) return false;
        if(html.indexOf("imageList") < 0 && html.indexOf("\"video\"") < 0) return false;
        return true;
    }


    /**
     * 把 Location 补成绝对地址（v2.1.5 新增）。
     * 小红书返回的 Location 可能长这样：
     *   /discovery/item/xxx?app_platform=android&...   ← 协议相对路径
     *   discovery/item/xxx?...                          ← 纯相对路径
     *   //www.xiaohongshu.com/discovery/item/xxx        ← 协议相对
     *   https://www.xiaohongshu.com/...                 ← 已经是绝对地址
     */
    static String absolutize(String base, String loc){
        if(loc == null || loc.length() == 0) return loc;
        String l = loc.trim();
        if(l.startsWith("http://") || l.startsWith("https://")) return l;
        String scheme = "https";
        String host = "www.xiaohongshu.com";
        try{
            java.net.URL bu = new java.net.URL(base);
            scheme = bu.getProtocol();
            if(bu.getHost() != null && bu.getHost().length() > 0) host = bu.getHost();
        }catch(Throwable t){}
        if(l.startsWith("//")) return scheme + ":" + l;
        if(l.startsWith("/")) return scheme + "://" + host + l;
        // 纯相对路径：挂在 base 的目录下
        int q = base.indexOf('?');
        String baseNoQ = q >= 0 ? base.substring(0, q) : base;
        int slash = baseNoQ.lastIndexOf('/');
        if(slash > 8) return baseNoQ.substring(0, slash + 1) + l;
        return scheme + "://" + host + "/" + l;
    }

    /**
     * 跟随重定向直接抓（v2.1.5 兜底通道）。
     *
     * 为什么需要：实测同一个 URL
     *   Python urlopen（默认跟随重定向）→ 200，len=83278，有 noteData
     *   App 的 fetch（setInstanceFollowRedirects=false）→ 302 一路跳到 /login
     * 小红书用「跳回自身 + Set-Cookie」才放行，不跟随就永远拿不到正文。
     * 所以把「关重定向自己判」和「交给系统跟随」两条都走一遍。
     */
    static String fetchFollow(String url, String ua, String cookie){
        java.net.HttpURLConnection uc = null;
        try{
            uc = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
            uc.setRequestMethod("GET");
            uc.setInstanceFollowRedirects(true);
            uc.setConnectTimeout(10000);
            uc.setReadTimeout(15000);
            uc.setRequestProperty("User-Agent", ua);
            uc.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
            uc.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9");
            uc.setRequestProperty("Referer", "https://www.xiaohongshu.com/");
            if(cookie != null && cookie.length() > 0) uc.setRequestProperty("Cookie", cookie);
            int code = uc.getResponseCode();
            Log.i(TAG, "XHS_FOLLOW_CODE " + code + " final=" + uc.getURL());
            if(code < 200 || code >= 400) return null;
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            java.io.InputStream in = uc.getInputStream();
            byte[] buf = new byte[8192];
            int n;
            while((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            in.close();
            byte[] raw = bo.toByteArray();
            if(raw.length > 2 && (raw[0] & 0xFF) == 0x1F && (raw[1] & 0xFF) == 0x8B){
                try{
                    java.util.zip.GZIPInputStream gz = new java.util.zip.GZIPInputStream(
                        new java.io.ByteArrayInputStream(raw));
                    java.io.ByteArrayOutputStream o2 = new java.io.ByteArrayOutputStream();
                    byte[] b2 = new byte[8192];
                    int n2;
                    while((n2 = gz.read(b2)) > 0) o2.write(b2, 0, n2);
                    gz.close();
                    raw = o2.toByteArray();
                }catch(Throwable t){}
            }
            return new String(raw, "UTF-8");
        }catch(Throwable t){
            Log.i(TAG, "XHS_FOLLOW_ERR " + t);
            return null;
        }finally{
            if(uc != null) try{ uc.disconnect(); }catch(Throwable t){}
        }
    }

    /** 返回 [最终URL, HTML]，失败返回 null */
    static String[] fetch(String url, String ua, String cookie){
        java.net.HttpURLConnection uc = null;
        try{
            uc = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
            uc.setRequestMethod("GET");
            uc.setInstanceFollowRedirects(false);   // 自己判 302 到 login
            uc.setConnectTimeout(10000);
            uc.setReadTimeout(15000);
            uc.setRequestProperty("User-Agent", ua);
            uc.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
            uc.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9");
            uc.setRequestProperty("Referer", "https://www.xiaohongshu.com/");
            if(cookie != null && cookie.length() > 0) uc.setRequestProperty("Cookie", cookie);
            int code = uc.getResponseCode();
            String loc = uc.getHeaderField("Location");
            Log.i(TAG, "XHS_CODE " + code + (loc != null ? (" loc=" + loc) : ""));
            if(code == 301 || code == 302 || code == 303 || code == 307){
                if(loc != null && loc.length() > 0){
                    // 小红书会返回相对 Location（如 "/discovery/item/xxx?...&xsec_token=..."），
                    // Java 的 HttpURLConnection 不会自动补全，必须自己拼成绝对地址，
                    // 否则后续 new URL() 直接 MalformedURLException: no protocol。
                    // （Python urllib 会自动处理，所以终端脚本能跑、App 里跑不通——v2.1.5 修复）
                    return new String[]{ absolutize(url, loc), null };
                }
                return null;
            }
            if(code < 200 || code >= 400) return null;
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            java.io.InputStream in = uc.getInputStream();
            byte[] buf = new byte[8192];
            int n;
            while((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            in.close();
            byte[] raw = bo.toByteArray();
            if(raw.length > 2 && (raw[0] & 0xFF) == 0x1F && (raw[1] & 0xFF) == 0x8B){
                try{
                    java.util.zip.GZIPInputStream gz = new java.util.zip.GZIPInputStream(
                        new java.io.ByteArrayInputStream(raw));
                    java.io.ByteArrayOutputStream o2 = new java.io.ByteArrayOutputStream();
                    byte[] b2 = new byte[8192];
                    int n2;
                    while((n2 = gz.read(b2)) > 0) o2.write(b2, 0, n2);
                    gz.close();
                    raw = o2.toByteArray();
                }catch(Throwable t){}
            }
            return new String[]{ url, new String(raw, "UTF-8") };
        }catch(Throwable t){
            Log.i(TAG, "XHS_HTTP_ERR " + t);
            return null;
        }finally{
            try{ if(uc != null) uc.disconnect(); }catch(Throwable t){}
        }
    }

    /** 主入口 */
    public static XhsNote resolve(String url){
        return resolve(url, "");
    }

    /** 带 Cookie 版（web_session 等），登录后可绕过 300031 风控 */
    public static XhsNote resolve(String url, String cookie){
        XhsNote n = new XhsNote();
        try{
            String realUrl = url;
            if(cookie == null) cookie = "";
            Log.i(TAG, "XHS_RESOLVE cookieLen=" + cookie.length());
            // 小红书现在强制登录，两种页面形态都试一遍
            if(realUrl.contains("/explore/")){
                String alt = realUrl.replace("/explore/", "/discovery/item/");
                Log.i(TAG, "XHS_ALT " + alt);
            }
            String html = null;
            // ── 抓取策略（v2.1.5 重写）────────────────────────────────────────
            // 小红书对 PC UA 强制跳 /login，只有移动 UA 才吐真实数据。
            // 短链先 302 拿到真 URL，所以每轮都要用最新的 realUrl 重建候选，
            // 不能在循环外一次性构建（否则第二轮还在请求短链，白白 302）。
            String lastLoc = "";
            for(int ui = 0; ui < UA_TRY.length && html == null; ui++){
                for(int pass = 0; pass < 4 && html == null; pass++){
                    // 每轮根据当前 realUrl 生成候选：原文 + 换另一条路径
                    String cur = realUrl;
                    String tryUrl;
                    if(pass == 0 || pass == 2){
                        tryUrl = cur;
                    }else{
                        if(cur.contains("/explore/")) tryUrl = cur.replace("/explore/", "/discovery/item/");
                        else if(cur.contains("/discovery/item/")) tryUrl = cur.replace("/discovery/item/", "/explore/");
                        else tryUrl = cur;
                    }
                    if(tryUrl.equals(lastLoc) && pass > 0) continue;
                    String r[] = fetch(tryUrl, UA_TRY[ui], cookie);
                    if(r == null) continue;
                    if(r[1] != null){
                        // 拿到 HTML，但要确认是真笔记页而不是登录空壳
                        if(looksLikeNote(r[1])){
                            html = r[1];
                            Log.i(TAG, "XHS_HIT ua=" + (ui == 0 ? "M" : "PC")
                                + " pass=" + pass + " len=" + r[1].length());
                            break;
                        }
                        Log.i(TAG, "XHS_THIN ua=" + (ui == 0 ? "M" : "PC")
                            + " len=" + r[1].length() + "（非笔记页，继续换）");
                        continue;
                    }
                    // 302：记下新地址，下一轮用它重建候选
                    if(r[0] != null && r[0].length() > 0){
                        realUrl = r[0];
                        lastLoc = r[0];
                    }
                    Log.i(TAG, "XHS_REDIR " + realUrl);
                    if(realUrl.contains("/login")) continue;
                    if(realUrl.contains("error_code=300031")){
                        n.err = "小红书风控拦截（error_code=300031），稍后重试或换网络";
                        Log.i(TAG, "XHS_RISK 300031");
                        return n;
                    }
                    if(realUrl.contains("error_code=")){
                        n.err = "小红书返回错误：" + realUrl;
                        Log.i(TAG, "XHS_ERRCODE " + realUrl);
                        return n;
                    }
                    if(realUrl.contains("/404") || realUrl.contains("note_error")){
                        n.err = "笔记已被删除或不存在";
                        return n;
                    }
                }
            }
            if(html == null || html.length() < 500){
                // 最后兜底：交给系统自动跟随重定向再抓一次（关键路径，见 fetchFollow 注释）
                for(int ui = 0; ui < UA_TRY.length && (html == null || html.length() < 500); ui++){
                    String cur = realUrl;
                    String got = fetchFollow(cur, UA_TRY[ui], cookie);
                    if(got != null && looksLikeNote(got)){
                        html = got;
                        Log.i(TAG, "XHS_FOLLOW_HIT ua=" + (ui == 0 ? "M" : "PC")
                            + " len=" + got.length());
                        break;
                    }
                    // 换 explore 再试一次
                    String alt = cur.contains("/explore/")
                        ? cur.replace("/explore/", "/discovery/item/")
                        : (cur.contains("/discovery/item/")
                            ? cur.replace("/discovery/item/", "/explore/") : null);
                    if(alt != null){
                        String got2 = fetchFollow(alt, UA_TRY[ui], cookie);
                        if(got2 != null && looksLikeNote(got2)){
                            html = got2;
                            Log.i(TAG, "XHS_FOLLOW_HIT alt ua=" + (ui == 0 ? "M" : "PC")
                                + " len=" + got2.length());
                            break;
                        }
                    }
                }
            }
            if(html == null || html.length() < 500){
                n.err = cookie.length() > 0
                    ? "小红书页面拉取失败（Cookie 可能已过期）"
                    : "小红书需要登录才能看笔记，请在设置里填入小红书 Cookie";
                Log.i(TAG, "XHS_FAIL len=" + (html == null ? -1 : html.length()));
                return n;
            }
            Log.i(TAG, "XHS_HTML len=" + html.length());
            // ── 抽页面数据（v2.1.5 重写）─────────────────────────────────────
            // 两条完全不同的结构：
            //   1) 移动 H5：window.__LAUNCHER_SSR_STORE_PAGE_DATA = {...}
            //      真数据在 root.data.noteData，且字符串里 URL 是 http:\u002F\u002F 转义
            //   2) PC 老结构：window.__INITIAL_STATE__ = {...}
            //      真数据在 root.note.noteDetailMap[firstNoteId].note
            // 用 indexOf + 括号配平截取，不用正则（避免 Java/正则双层转义踩坑）。
            JSONObject root = null;
            for(int mi = 0; mi < STATE_MARKERS.length && root == null; mi++){
                String marker = STATE_MARKERS[mi];
                int ip = html.indexOf(marker);
                if(ip < 0) continue;
                String js = sliceJsonObject(html, ip);
                if(js == null || js.length() < 10){
                    Log.i(TAG, "XHS_SLICE_EMPTY " + marker);
                    continue;
                }
                // 小红书 JS 对象里有 undefined / new Map(...) 等非法 JSON
                js = js.replaceAll("undefined", "null");
                js = js.replaceAll("new Map\\(\\)[^,}]*", "{}");
                js = js.replaceAll("new Set\\(\\)[^,}]*", "[]");
                try{
                    root = new JSONObject(js);
                    Log.i(TAG, "XHS_ROOT marker=" + marker + " len=" + js.length()
                        + " keys=" + root.length());
                }catch(Throwable te){
                    Log.i(TAG, "XHS_JSON_ERR " + marker + " " + te);
                }
            }
            if(root == null){
                n.err = "页面数据结构变化（JSON 解析失败）";
                return n;
            }
            JSONObject note = extractNote(root);
            if(note == null){
                n.err = "小红书返回的页面里没有笔记数据（可能笔记已删或需要登录）";
                Log.i(TAG, "XHS_NO_NOTE rootKeys=" + root.length());
                return n;
            }
            parseNote(n, note);
            n.ok = n.hasVideo() || n.hasImages() || n.title.length() > 0;
            Log.i(TAG, "XHS_OK title=" + n.title + " v=" + n.hasVideo()
                + " imgs=" + n.images.size() + " author=" + n.author);
            if(!n.ok && n.err.length() == 0) n.err = "未取到笔记内容";
        }catch(Throwable t){
            n.err = String.valueOf(t);
            Log.i(TAG, "XHS_ERR " + t);
        }
        return n;
    }

    /** 兼容各种结构（v2.1.5 扩展：新增移动 H5 的 root.data.noteData） */
    static JSONObject extractNote(JSONObject root){
        try{
            // ── 1) 移动 H5：__LAUNCHER_SSR_STORE_PAGE_DATA ──
            //    真实结构：{ routeQuery, data:{ noteData:{ title, desc, imageList, video, user } } }
            JSONObject dataTop = root.optJSONObject("data");
            if(dataTop != null){
                JSONObject ndTop = dataTop.optJSONObject("noteData");
                if(ndTop != null && ndTop.length() > 0
                    && (ndTop.has("imageList") || ndTop.has("video") || ndTop.has("noteId"))){
                    Log.i(TAG, "XHS_NOTE_SRC data.noteData");
                    return ndTop;
                }
                // data.noteDataMap[noteId].note
                JSONObject mapTop = dataTop.optJSONObject("noteDataMap");
                if(mapTop != null){
                    JSONObject got = pickFromMap(mapTop);
                    if(got != null){ Log.i(TAG, "XHS_NOTE_SRC data.noteDataMap"); return got; }
                }
            }
            // 兼容 root.noteData 直接就是笔记
            JSONObject ndDirect = root.optJSONObject("noteData");
            if(ndDirect != null && (ndDirect.has("imageList") || ndDirect.has("video"))){
                Log.i(TAG, "XHS_NOTE_SRC root.noteData");
                return ndDirect;
            }

            // ── 2) PC 老结构：__INITIAL_STATE__ ──
            JSONObject noteRoot = root.optJSONObject("note");
            if(noteRoot != null){
                String fid = noteRoot.optString("firstNoteId", "");
                JSONObject map = noteRoot.optJSONObject("noteDetailMap");
                if(map != null){
                    if(fid.length() > 0){
                        JSONObject item = map.optJSONObject(fid);
                        if(item != null){
                            JSONObject nn = item.optJSONObject("note");
                            if(nn != null && nn.length() > 0){
                                Log.i(TAG, "XHS_NOTE_SRC noteDetailMap[first]");
                                return nn;
                            }
                        }
                    }
                    JSONObject got = pickFromMap(map);
                    if(got != null){ Log.i(TAG, "XHS_NOTE_SRC noteDetailMap[*]"); return got; }
                }
            }
            JSONObject nd = root.optJSONObject("noteData");
            if(nd != null){
                JSONObject d = nd.optJSONObject("data");
                if(d != null){
                    JSONObject n2 = d.optJSONObject("noteData");
                    if(n2 != null && n2.length() > 0) return n2;
                }
                JSONObject n3 = nd.optJSONObject("note");
                if(n3 != null && n3.length() > 0) return n3;
            }
            if(noteRoot != null){
                JSONObject n4 = noteRoot.optJSONObject("note");
                if(n4 != null && n4.length() > 0) return n4;
            }
            // ── 3) 最后兜底：全局递归找带 imageList 的对象 ──
            JSONObject deep = deepFindNote(root, 0);
            if(deep != null){ Log.i(TAG, "XHS_NOTE_SRC deepFind"); return deep; }
        }catch(Throwable t){
            Log.i(TAG, "XHS_EXTRACT_ERR " + t);
        }
        return null;
    }

    /** 从 noteDetailMap / noteDataMap 这类 map 里取第一个有效 note */
    static JSONObject pickFromMap(JSONObject map){
        try{
            Iterator<String> it = map.keys();
            while(it.hasNext()){
                JSONObject item = map.optJSONObject(it.next());
                if(item == null) continue;
                JSONObject nn = item.optJSONObject("note");
                if(nn == null) nn = item.optJSONObject("noteCard");
                if(nn != null && (nn.has("imageList") || nn.has("video"))) return nn;
            }
        }catch(Throwable t){}
        return null;
    }

    /** 递归兜底：找第一个同时像是「笔记」的对象 */
    static JSONObject deepFindNote(JSONObject o, int depth){
        if(o == null || depth > 6) return null;
        if(o.has("imageList") && (o.has("desc") || o.has("title") || o.has("noteId"))){
            return o;
        }
        try{
            Iterator<String> it = o.keys();
            while(it.hasNext()){
                Object v = o.opt(it.next());
                if(v instanceof JSONObject){
                    JSONObject got = deepFindNote((JSONObject)v, depth + 1);
                    if(got != null) return got;
                }else if(v instanceof JSONArray){
                    JSONArray arr = (JSONArray)v;
                    for(int i = 0; i < arr.length() && i < 8; i++){
                        Object e = arr.opt(i);
                        if(e instanceof JSONObject){
                            JSONObject got = deepFindNote((JSONObject)e, depth + 1);
                            if(got != null) return got;
                        }
                    }
                }
            }
        }catch(Throwable t){}
        return null;
    }

    /**
     * 从 html 里 startIdx 位置开始，向后找到第一个 '{'，再按括号配平截出完整 JSON 对象。
     * 为什么要配平而不是直接找 "</script>"：JSON 字符串里可能包含 '}' 和 '</script>'，
     * 只有数括号深度才能稳定切开。（v2.1.5 新增）
     */
    static String sliceJsonObject(String html, int startIdx){
        int b = html.indexOf("{", startIdx);
        if(b < 0) return null;
        int depth = 0;
        boolean inStr = false;
        boolean esc = false;
        for(int i = b; i < html.length(); i++){
            char c = html.charAt(i);
            if(inStr){
                if(esc){ esc = false; }
                else if(c == '\\'){ esc = true; }
                else if(c == '"'){ inStr = false; }
                continue;
            }
            if(c == '"'){ inStr = true; continue; }
            if(c == '{'){ depth++; }
            else if(c == '}'){
                depth--;
                if(depth == 0) return html.substring(b, i + 1);
            }
        }
        return null;
    }

    static void parseNote(XhsNote n, JSONObject note){
        n.id = note.optString("noteId", note.optString("id", ""));
        n.title = note.optString("title", "");
        n.desc = note.optString("desc", note.optString("description", ""));
        if(n.title.length() == 0) n.title = n.desc;
        long ct = note.optLong("time", note.optLong("createTime", 0));
        if(ct > 0) n.createdAt = ct < 100000000000L ? ct * 1000L : ct;
        // 互动数据
        JSONObject ii = note.optJSONObject("interactInfo");
        if(ii != null){
            n.likeCount = numOf(ii.optString("likedCount", "0"));
        }
        // 作者
        JSONObject u = note.optJSONObject("user");
        if(u != null){
            n.author = u.optString("nickname", u.optString("nickName", ""));
            n.avatar = ensureHttps(u.optString("avatar", u.optString("image", "")));
        }
        // 图集
        JSONArray il = note.optJSONArray("imageList");
        if(il != null){
            for(int i = 0; i < il.length(); i++){
                JSONObject img = il.optJSONObject(i);
                if(img == null) continue;
                String u2 = cleanImage(img);
                if(u2.length() > 0) n.images.add(u2);
                if(n.cover.length() == 0) n.cover = u2;
            }
        }
        // 视频（v2.1.5：改为收集全部档位，按码率降序）
        JSONObject v = note.optJSONObject("video");
        if(v != null){
            JSONArray gs = collectGears(v);
            if(gs != null && gs.length() > 0){
                for(int i = 0; i < gs.length() && i < 20; i++){
                    JSONArray one = gs.optJSONArray(i);
                    if(one == null) continue;
                    n.gears.add(new String[]{
                        one.optString(0, "档" + (i + 1)),
                        one.optString(1, ""),
                        one.optString(2, "0"),
                        one.optString(3, "0")
                    });
                }
                n.playUrl = n.gears.get(0)[1];
                Log.i(TAG, "XHS_GEARS n=" + n.gears.size() + " best=" + n.gears.get(0)[0]);
            }else{
                String pu = extractVideoUrl(v);
                if(pu.length() > 0){
                    n.playUrl = pu;
                    n.gears.add(new String[]{ "原画", pu, "0", "0" });
                }
            }
            // 视频封面
            if(n.cover.length() == 0){
                JSONObject media0 = v.optJSONObject("media");
                if(media0 != null){
                    String cv = media0.optString("coverUrl", media0.optString("cover", ""));
                    if(cv.length() > 0) n.cover = ensureHttps(unescapeSlashes(cv));
                }
            }
            if(n.cover.length() == 0){
                String ok2 = firstNested(v, new String[]{ "consumer", "originVideoKey" });
                if(ok2 != null && ok2.length() > 0){
                    n.cover = ensureHttps(unescapeSlashes(
                        ok2.startsWith("http") ? ok2 : ("https://sns-video-bd.xhscdn.com/" + strip(ok2))));
                }
            }
            JSONObject media = v.optJSONObject("media");
            if(media != null){
                JSONObject stream = media.optJSONObject("stream");
                if(stream != null){
                    long d = 0;
                    String[] dcodecs = new String[]{ "h264", "h265" };
                    for(int di = 0; di < dcodecs.length && d == 0; di++){
                        JSONArray arr = stream.optJSONArray(dcodecs[di]);
                        if(arr != null && arr.length() > 0){
                            JSONObject h0 = arr.optJSONObject(0);
                            if(h0 != null) d = h0.optLong("duration", 0);
                        }
                    }
                    if(d > 0) n.durMs = d;
                }
            }
        }
        if(n.cover.length() == 0 && n.images.size() > 0) n.cover = n.images.get(0);
    }

    /**
     * 小红书视频选流（v2.1.5 重写）
     *
     * 实测结构（2026-11，移动 H5 页面）：
     *   video.media.stream = {
     *     h264: [ { streamDesc:"MINI_APP_259", 27073728B, 1749980bps, 720x1280, masterUrl, backupUrls } ],
     *     h265: [ { streamDesc:"X265_MP4_WEB_520_h5", 16303678B, 1053830bps, 720x1280, masterUrl } ],
     *     h266: [], av1: []
     *   }
     *
     * 老代码问题：
     *   1) 先找 consumer.originVideoKey —— 移动端页面根本没这个字段，白忙；
     *   2) 只取 h264[0]，且 mediaV2 在移动页不存在；
     *   3) 最后 deepFind("url") 会抓到封面图或头像，得到「有水印、放不出来」的假地址。
     *
     * 新逻辑：把所有 codec 的所有档位收集起来，按 (码率, 体积) 排序取最优，
     * 只信 masterUrl / backupUrls，绝不 deepFind 乱抓。
     */
    static String extractVideoUrl(JSONObject v){
        try{
            JSONArray gears = collectGears(v);
            if(gears != null && gears.length() > 0){
                // gears 已按码率降序，第 0 个就是最好的
                return gears.optJSONArray(0) == null ? "" : gears.optJSONArray(0).optString(1, "");
            }
            // 1) consumer.originVideoKey（PC 端才有的原画 key）
            JSONObject consumer = v.optJSONObject("consumer");
            String ok = "";
            if(consumer != null){
                ok = consumer.optString("originVideoKey",
                     consumer.optString("origin_video_key", ""));
            }
            if(ok.length() == 0) ok = v.optString("originVideoKey", v.optString("origin_video_key", ""));
            if(ok.length() > 0){
                return ensureHttps(unescapeSlashes(
                    ok.startsWith("http") ? ok : ("https://sns-video-bd.xhscdn.com/" + strip(ok))));
            }
            // 2) mediaV2（字符串化 JSON）
            String mv2 = v.optString("mediaV2", "");
            if(mv2.length() > 10){
                try{
                    JSONObject m2 = new JSONObject(mv2);
                    String k2 = deepFindStr(m2, new String[]{ "originVideoKey", "origin_video_key", "masterUrl" });
                    if(k2 != null && k2.length() > 0){
                        return ensureHttps(unescapeSlashes(
                            k2.startsWith("http") ? k2 : ("https://sns-video-bd.xhscdn.com/" + strip(k2))));
                    }
                }catch(Throwable t){}
            }
        }catch(Throwable t){
            Log.i(TAG, "XHS_VIDEO_ERR " + t);
        }
        return "";
    }

    /**
     * 收集所有视频档位，返回 JSONArray of [名称, 地址, 字节, 码率]，
     * 按「码率 * 体积」降序排列（码率更可靠，体积做 tie-break）。
     * v2.1.5 新增，供 extractVideoUrl 与 gears 展示共用。
     */
    static JSONArray collectGears(JSONObject v){
        JSONArray out = new JSONArray();
        try{
            JSONObject media = v.optJSONObject("media");
            if(media == null) return out;
            JSONObject stream = media.optJSONObject("stream");
            if(stream == null) return out;
            String[] codecs = new String[]{ "h264", "h265", "h266", "av1" };
            for(int ci = 0; ci < codecs.length; ci++){
                JSONArray arr = stream.optJSONArray(codecs[ci]);
                if(arr == null) continue;
                for(int i = 0; i < arr.length(); i++){
                    JSONObject s = arr.optJSONObject(i);
                    if(s == null) continue;
                    String u = s.optString("masterUrl", "");
                    if(u.length() == 0){
                        JSONArray bu = s.optJSONArray("backupUrls");
                        if(bu != null && bu.length() > 0) u = bu.optString(0, "");
                    }
                    if(u.length() == 0 || !u.startsWith("http")) continue;
                    u = ensureHttps(unescapeSlashes(u));
                    long sz = s.optLong("size", 0);
                    long br = s.optLong("avgBitrate", 0);
                    int w = s.optInt("width", 0);
                    int h = s.optInt("height", 0);
                    String desc = s.optString("streamDesc", codecs[ci]);
                    String label = codecs[ci].toUpperCase();
                    if(w > 0 && h > 0) label += " " + w + "x" + h;
                    if(br > 0) label += " " + (br / 1000) + "kbps";
                    label += " (" + desc + ")";
                    JSONArray one = new JSONArray();
                    one.put(label);
                    one.put(u);
                    one.put(String.valueOf(sz));
                    one.put(String.valueOf(br));
                    out.put(one);
                }
            }
            // 按码率降序（码率相同按体积降序）——简单插入排序，档位很少
            for(int i = 1; i < out.length(); i++){
                JSONArray cur = out.optJSONArray(i);
                long cbr = numOf(cur.optString(3, "0"));
                long csz = numOf(cur.optString(2, "0"));
                int j = i - 1;
                while(j >= 0){
                    JSONArray prev = out.optJSONArray(j);
                    long pbr = numOf(prev.optString(3, "0"));
                    long psz = numOf(prev.optString(2, "0"));
                    boolean curBigger = (cbr > pbr) || (cbr == pbr && csz > psz);
                    if(!curBigger) break;
                    out.put(j + 1, prev);
                    j--;
                }
                out.put(j + 1, cur);
            }
        }catch(Throwable t){
            Log.i(TAG, "XHS_GEARS_ERR " + t);
        }
        return out;
    }

    /**
     * 小红书图片选档（v2.1.5 重写）
     *
     * 真实数据结构（实测 2026-11）：
     *   imageList[i] = {
     *     fileId, width, height, livePhoto,
     *     url: "http://sns-webpic-qc.xhscdn.com/...!h5_1080jpg",     ← 顶层，通常就是大图
     *     infoList: [
     *       { imageScene:"H5_DTL", url:"...!h5_1080jpg" },            ← 详情大图（476KB）
     *       { imageScene:"H5_PRV", url:"...!style_xxx"    }            ← 预览小图（18KB）
     *     ]
     *   }
     *
     * 老代码用「第一个不等于 WM 的」→ 顺序一变就拿到 H5_PRV，画质崩掉。
     * 新逻辑：按场景优先级打分，H5_DTL / WB_DFT / 有 h5_1080 / 无 style_ 前缀的
     * 才算高清；确保绝不返回 H5_PRV / WB_PRV 这类预览图。
     */
    static String cleanImage(JSONObject img){
        try{
            String best = "";
            int bestScore = -1;

            JSONArray infos = img.optJSONArray("infoList");
            if(infos != null){
                for(int i = 0; i < infos.length(); i++){
                    JSONObject o = infos.optJSONObject(i);
                    if(o == null) continue;
                    String iu = o.optString("url", "");
                    if(iu.length() == 0) continue;
                    String scene = o.optString("imageScene", "");
                    int sc = scoreImage(scene, iu);
                    if(sc < 0) continue;                 // 带水印/预览图，直接跳过
                    if(sc > bestScore){ bestScore = sc; best = iu; }
                }
            }
            // infoList 没合适的，再看顶层 url / urlDefault
            if(best.length() == 0){
                String[] cand = new String[]{
                    img.optString("url", ""),
                    img.optString("urlDefault", ""),
                    img.optString("urlPre", "")
                };
                for(int i = 0; i < cand.length; i++){
                    String u = cand[i];
                    if(u == null || u.length() == 0) continue;
                    int sc = scoreImage("", u);
                    if(sc < 0) continue;
                    if(sc > bestScore){ bestScore = sc; best = u; }
                }
            }
            if(best.length() == 0) return "";
            return ensureHttps(unescapeSlashes(best));
        }catch(Throwable t){ return ""; }
    }

    /**
     * 给图片档位打分，分越高越清晰。返回 -1 表示不可用（预览图/带水印）。
     *
     * 判据来自实测字节数对比：
     *   H5_DTL  476,119 字节  ← 要这个
     *   H5_PRV   18,808 字节  ← 千万别要
     */
    static int scoreImage(String scene, String url){
        if(url == null || url.length() == 0) return -1;
        String s = (scene == null ? "" : scene).toUpperCase();
        String u = url.toLowerCase();

        // 明确排除：预览图、带水印、缩略图
        if(s.contains("PRV") || s.contains("PREV") || s.contains("THUMB")) return -1;
        if(s.contains("WM")) return -1;
        if(u.contains("style_")) return -1;          // H5_PRV 的模板标记
        if(u.contains("_prv") || u.contains("thumb")) return -1;

        int score = 0;
        // 场景优先级
        if(s.equals("H5_DTL")) score += 100;
        else if(s.contains("DTL")) score += 90;
        else if(s.contains("WB_DFT")) score += 80;
        else if(s.contains("CRD")) score += 60;
        else if(s.contains("H5")) score += 30;

        // URL 特征
        if(u.contains("h5_1080")) score += 40;
        if(u.contains("1080")) score += 20;
        if(u.contains("!nd_dft") || u.contains("dft_wm")) score -= 10;
        if(u.contains(".jpg") || u.contains("jpg")) score += 5;
        if(u.contains("webp")) score -= 5;           // 优先 jpg，webp 兼容性差

        return score;
    }

    /** JSON 里 URL 可能是 http:\u002F\u002Fxxx 形式，统一还原成 https://xxx */
    static String unescapeSlashes(String u){
        if(u == null) return "";
        String s = u;
        if(s.indexOf("\\u002F") >= 0) s = s.replace("\\u002F", "/");
        if(s.indexOf("\\u002f") >= 0) s = s.replace("\\u002f", "/");
        if(s.indexOf("\\/") >= 0) s = s.replace("\\/", "/");
        return s;
    }

    static String firstNested(JSONObject o, String[] path){
        JSONObject cur = o;
        for(int i = 0; i < path.length; i++){
            if(cur == null) return "";
            if(i == path.length - 1) return cur.optString(path[i], "");
            cur = cur.optJSONObject(path[i]);
        }
        return "";
    }

    static String deepFindStr(Object o, String[] keys){
        if(o == null) return null;
        if(o instanceof JSONObject){
            JSONObject j = (JSONObject)o;
            for(int i = 0; i < keys.length; i++){
                String v = j.optString(keys[i], "");
                if(v.length() > 6 && !v.startsWith("{") && !v.startsWith("[")) return v;
            }
            Iterator<String> it = j.keys();
            while(it.hasNext()){
                Object c = j.opt(it.next());
                if(c instanceof JSONObject || c instanceof JSONArray){
                    String r = deepFindStr(c, keys);
                    if(r != null) return r;
                }
            }
        }else if(o instanceof JSONArray){
            JSONArray a = (JSONArray)o;
            for(int i = 0; i < a.length(); i++){
                String r = deepFindStr(a.opt(i), keys);
                if(r != null) return r;
            }
        }
        return null;
    }

    static String strip(String s){
        if(s == null) return "";
        String r = s.trim();
        while(r.startsWith("/")) r = r.substring(1);
        return r;
    }

    static String ensureHttps(String u){
        if(u == null) return "";
        String r = u.replace("\\\\u002F", "/").replace("\\\\/", "/");
        if(r.startsWith("//")) return "https:" + r;
        return r;
    }

    static long numOf(String s){
        if(s == null) return 0;
        s = s.replace(",", "").trim();
        if(s.length() == 0) return 0;
        try{
            if(s.endsWith("万")) return (long)(Double.parseDouble(s.substring(0, s.length() - 1)) * 10000);
            if(s.endsWith("w")) return (long)(Double.parseDouble(s.substring(0, s.length() - 1)) * 10000);
            if(s.endsWith("亿")) return (long)(Double.parseDouble(s.substring(0, s.length() - 1)) * 100000000);
            return (long)Double.parseDouble(s);
        }catch(Throwable t){ return 0; }
    }

    /** 输出 render() 认的 i_* JSON */
    public static String toJson(XhsNote n){
        StringBuilder b = new StringBuilder();
        b.append("{");
        b.append("\"i_platform\":\"小红书\",");
        kv(b, "i_id", n.id.length() > 0 ? n.id : ("xhs_" + Math.abs(n.title.hashCode())));
        if(n.author.length() > 0) kv(b, "i_author", n.author);
        if(n.title.length() > 0) kv(b, "i_desc", n.title);
        if(n.createdAt > 0) kv(b, "i_time", String.valueOf(n.createdAt));
        if(n.durMs > 0) kv(b, "i_dur", String.valueOf(n.durMs));
        if(n.cover.length() > 0) kv(b, "i_cover", n.cover);
        if(n.likeCount > 0) kv(b, "i_stats", "赞 " + n.likeCount);
        if(n.hasVideo()) kv(b, "i_play", n.playUrl);
        if(n.hasImages()){
            StringBuilder im = new StringBuilder();
            for(int i = 0; i < n.images.size(); i++){
                if(i > 0) im.append("|");
                im.append(n.images.get(i));
            }
            kv(b, "i_imgs", im.toString());
        }
        String s = b.toString();
        if(s.endsWith(",")) s = s.substring(0, s.length() - 1);
        return s + "}";
    }

    static void kv(StringBuilder b, String k, String v){
        if(v == null) v = "";
        b.append("\"").append(k).append("\":\"").append(esc(v)).append("\",");
    }

    /** 返回 [imgs, play, cover]；imgs 用 | 分隔 */
    public static String[] toMedia(XhsNote n){
        StringBuilder im = new StringBuilder();
        for(int i = 0; i < n.images.size(); i++){
            if(i > 0) im.append("|");
            im.append(n.images.get(i));
        }
        return new String[]{ im.toString(), n.playUrl == null ? "" : n.playUrl,
                             n.cover == null ? "" : n.cover };
    }


    static String esc(String s){
        if(s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "").replace("\t", " ");
    }


    /** 输出 renderCards() 认的「【标签】值」文本 */
    public static String toText(XhsNote n){
        StringBuilder b = new StringBuilder();
        b.append("【平台】小红书\n");
        if(n.id.length() > 0) b.append("【作品ID】").append(n.id).append("\n");
        if(n.title.length() > 0) b.append("【标题】").append(n.title).append("\n");
        if(n.author.length() > 0) b.append("【作者】").append(n.author).append("\n");
        if(n.desc.length() > 0 && !n.desc.equals(n.title)) b.append("【描述】").append(n.desc).append("\n");
        if(n.createdAt > 0){
            java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm");
            f.setTimeZone(java.util.TimeZone.getTimeZone("Asia/Shanghai"));
            b.append("【发布时间】").append(f.format(new java.util.Date(n.createdAt))).append("\n");
        }
        if(n.durMs > 0) b.append("【时长】").append(n.durMs / 1000).append(" 秒\n");
        if(n.likeCount > 0) b.append("【数据】赞 ").append(n.likeCount).append("\n");
        if(n.cover.length() > 0) b.append("【封面】").append(n.cover).append("\n");
        if(n.hasVideo()){
            b.append("【视频直链】").append(n.playUrl).append("\n");
        }
        if(n.hasImages()){
            b.append("【图集】").append(n.images.size()).append(" 张\n");
            for(int i = 0; i < n.images.size(); i++){
                b.append("【图").append(i + 1).append("】").append(n.images.get(i)).append("\n");
            }
        }
        if(n.hasVideo()) b.append("【音频下载】点下方「提取音频」，从视频中提取音轨为 MP3\n");
        return b.toString();
    }
}