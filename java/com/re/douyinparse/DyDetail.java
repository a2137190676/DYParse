package com.re.douyinparse;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

/**
 * 抖音 Web 详情接口客户端：/aweme/v1/web/aweme/detail/
 *
 * 实况（Live Photo）动轨只有这个接口下发，分享页 SSR 不含实况字段。
 *
 * 实测服务端行为（来自 ucmao/media-parser 公开技术文档 + 本机验证）：
 *   缺 uifid      -> 403 Blocked by ArgusSecurityPlugin Uifid Not Found
 *   缺 secsdk 签名 -> 403 Blocked by ArgusSecurityPlugin Signature Not Found
 *   同一个字节完全一致的请求也会在 200 与 403 之间随机跳变（概率性风控）
 *
 * 所以本类做三件事：补 msToken、带 uifid 请求头、算 x-secsdk-web-signature，
 * 然后重试若干次以提高单次通过率。
 */
public class DyDetail {

    /** 必须与实际发请求的 UA 完全一致，签名第三条摘要链会绑定它。
     *  query 参数是 PC 形态（browser_platform=Win32 / os_name=Windows），UA 必须也是 PC Chrome */
    public static final String UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    public static final String API =
        "https://www.douyin.com/aweme/v1/web/aweme/detail/";

    /** 主页作品列表接口（PC web 形态，需要 a_bogus 签名才能翻页） */
    public static final String API_POST =
        "https://www.douyin.com/aweme/v1/web/aweme/post/";

    /**
     * 构造主页作品列表的签名 query。
     * 与 detail 不同点：这里必须带 max_cursor，且 a_bogus 会覆盖全部参数，
     * 所以每次翻页都要用新的 cursor 重新算签名，不能直接改已有 URL 的参数。
     */
    public static String buildPostQuery(String secUid, long maxCursor, int count,
                                        String msToken, String uifid){
        Map<String, String> p = new LinkedHashMap<String, String>();
        p.put("device_platform", "webapp");
        p.put("aid", "6383");
        p.put("channel", "channel_pc_web");
        p.put("pc_client_type", "1");
        p.put("pc_libra_divert", "Windows");
        p.put("support_h265", "1");
        p.put("support_dash", "1");
        p.put("version_code", "190500");
        p.put("version_name", "19.5.0");
        p.put("cookie_enabled", "true");
        p.put("screen_width", "1920");
        p.put("screen_height", "1080");
        p.put("browser_language", "zh-CN");
        p.put("browser_platform", "Win32");
        p.put("browser_name", "Chrome");
        p.put("browser_version", "120.0.0.0");
        p.put("browser_online", "true");
        p.put("engine_name", "Blink");
        p.put("engine_version", "120.0.0.0");
        p.put("os_name", "Windows");
        p.put("os_version", "10");
        p.put("cpu_core_num", "8");
        p.put("device_memory", "8");
        p.put("platform", "PC");
        p.put("downlink", "10");
        p.put("effective_type", "4g");
        p.put("round_trip_time", "200");
        p.put("sec_user_id", secUid);
        p.put("max_cursor", String.valueOf(maxCursor));
        p.put("locate_query", "false");
        p.put("show_live_replay_strategy", "1");
        p.put("need_time_list", "1");
        p.put("time_list_query", "0");
        p.put("whale_cut_token", "");
        p.put("cut_version", "1");
        p.put("count", String.valueOf(count));
        p.put("publish_video_strategy_type", "2");
        p.put("msToken", msToken);
        if(uifid != null && uifid.length() > 0) p.put("uifid", uifid);
        return encodeQuery(p);
    }

    /**
     * 极简版主页列表 query —— 完全按 WebView 自己发的那条请求来构造（实测参数表）：
     *   reflow_source, web_id, device_id, aid, from_did, user_cip, sec_uid,
     *   count, max_cursor, reflow_id, msToken, a_bogus
     * 之前那套 40 多个 webapp 指纹参数（pc_libra_divert、screen_*、browser_* 等）
     * 反而让服务端判定为「非真实浏览器」，第 2 页直接回 {"status_code":0}。
     */
    public static String buildSimpleQuery(String secUid, long maxCursor, int count,
                                          String msToken, String webId, String deviceId,
                                          String reflowSource, String reflowId, String userCip,
                                          String fromDid, String aid){
        Map<String, String> p = new LinkedHashMap<String, String>();
        p.put("reflow_source", reflowSource == null ? "" : reflowSource);
        p.put("web_id", webId == null ? "" : webId);
        p.put("device_id", deviceId == null ? "" : deviceId);
        p.put("aid", aid == null ? "6383" : aid);
        p.put("from_did", fromDid == null ? "0" : fromDid);
        p.put("user_cip", userCip == null ? "" : userCip);
        p.put("sec_uid", secUid);
        p.put("count", String.valueOf(count));
        p.put("max_cursor", String.valueOf(maxCursor));
        p.put("reflow_id", reflowId == null ? "" : reflowId);
        p.put("msToken", msToken == null ? "" : msToken);
        return encodeQuery(p);
    }

    /** 签名主页列表 query：a_bogus + secsdk（有 uifid 时） */
    public static String signPostQuery(String query, String cookie){
        return signPostQuery(query, cookie, true);
    }

    /**
     * @param withSecsdk false 时不签 secsdk（即不夹带 uifid）
     *        实测：WebView 自己的请求不带 uifid 能翻完全部；带 uifid 反而第 2 页返回 status_code:0
     */
    public static String signPostQuery(String query, String cookie, boolean withSecsdk){
        String uifid = "";
        if(withSecsdk){
            uifid = cookieValue(cookie, "UIFID_TEMP");
            if(uifid.length() == 0) uifid = cookieValue(cookie, "UIFID");
            if(uifid.length() == 0) uifid = cookieValue(cookie, "uifid");
        }
        String aBogus = new ABogusGenerator(UA).getValue(query);
        String signed = query + "&a_bogus=" + quoteNative(aBogus);
        if(uifid.length() > 0){
            signed = signSecsdk(signed, uifid, System.currentTimeMillis() / 1000L);
        }
        return signed;
    }

    /** 从 cookie 里取 msToken，没有就伪造 */
    public static String msTokenOf(String cookie){
        String msToken = cookieValue(cookie, "msToken");
        if(msToken.length() == 0) msToken = fakeMsToken();
        return msToken;
    }


    private static final String WEBSIGN_SALT = "A96D855A08C0A9707F8BEF0D9A527E4E";
    private static final String WEBSIGN_SAFE = "!*'()";

    public static class Result {
        public boolean ok;
        public int httpCode;
        public int tries;
        public String body = "";
        public String err = "";
        public String signedQuery = "";
        public String getUrl(){ return API + "?" + signedQuery; }
        @Override public String toString(){
            return "ok=" + ok + " http=" + httpCode + " tries=" + tries
                 + " err=" + err + " len=" + body.length();
        }
    }

    /** 随机 msToken，156 字符，和官方 SDK 形态一致 */
    static String fakeMsToken(){
        final String base = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
        Random r = new Random();
        StringBuilder sb = new StringBuilder(156);
        for(int i = 0; i < 156; i++) sb.append(base.charAt(r.nextInt(base.length())));
        return sb.toString();
    }

    /** 构造 query（不含 a_bogus / secsdk 签名） */
    static String buildQuery(String awemeId, String msToken, String uifid){
        Map<String, String> p = new LinkedHashMap<String, String>();
        p.put("device_platform", "webapp");
        p.put("aid", "6383");
        p.put("channel", "channel_pc_web");
        p.put("pc_client_type", "1");
        p.put("pc_libra_divert", "Windows");
        p.put("support_h265", "1");
        p.put("support_dash", "1");
        p.put("version_code", "190500");
        p.put("version_name", "19.5.0");
        p.put("cookie_enabled", "true");
        p.put("screen_width", "1920");
        p.put("screen_height", "1080");
        p.put("browser_language", "zh-CN");
        p.put("browser_platform", "Win32");
        p.put("browser_name", "Chrome");
        p.put("browser_version", "120.0.0.0");
        p.put("browser_online", "true");
        p.put("engine_name", "Blink");
        p.put("engine_version", "120.0.0.0");
        p.put("os_name", "Windows");
        p.put("os_version", "10");
        p.put("cpu_core_num", "8");
        p.put("device_memory", "8");
        p.put("platform", "PC");
        p.put("downlink", "10");
        p.put("effective_type", "4g");
        p.put("round_trip_time", "200");
        p.put("aweme_id", awemeId);
        p.put("msToken", msToken);
        if(uifid != null && uifid.length() > 0) p.put("uifid", uifid);
        return encodeQuery(p);
    }

    /** 按 media-parser 的 _sign_secsdk 规则算 x-secsdk-web-signature */
    static String signSecsdk(String query, String uifid, long ts){
        if(uifid == null || uifid.length() == 0) return query;
        StringBuilder canon = new StringBuilder();
        for(String pair : query.split("&")){
            if(pair.length() == 0) continue;
            int e = pair.indexOf('=');
            String k = e >= 0 ? pair.substring(0, e) : pair;
            String v = e >= 0 ? pair.substring(e + 1) : "";
            k = unquotePlus(k);
            v = unquotePlus(v);
            if(canon.length() > 0) canon.append('&');
            canon.append(k).append('=').append(quoteNative(v));
        }
        String c = canon.toString();
        boolean hasUifid = false;
        for(String part : c.split("&")){
            int e2 = part.indexOf('=');
            if(e2 > 0 && part.substring(0, e2).equals("uifid")){ hasUifid = true; break; }
        }
        if(!hasUifid){
            c = c.length() > 0 ? c + "&uifid=" + quoteNative(uifid) : "uifid=" + quoteNative(uifid);
        }
        String signed = c + "&timestamp=" + ts;
        String plain = uifid + "_" + ts + "_" + WEBSIGN_SALT + "_" + signed;
        return signed + "&x-secsdk-web-signature=" + md5Hex(plain);
    }

    /** 完整签名 query */
    public static String buildSignedQuery(String awemeId, String cookie){
        String uifid = cookieValue(cookie, "UIFID_TEMP");
        if(uifid.length() == 0) uifid = cookieValue(cookie, "UIFID");
        if(uifid.length() == 0) uifid = cookieValue(cookie, "uifid");
        String msToken = cookieValue(cookie, "msToken");
        if(msToken.length() == 0) msToken = fakeMsToken();

        String query = buildQuery(awemeId, msToken, uifid);
        String aBogus = new ABogusGenerator(UA).getValue(query);
        String signed = query + "&a_bogus=" + quoteNative(aBogus);
        if(uifid.length() > 0){
            signed = signSecsdk(signed, uifid, System.currentTimeMillis() / 1000L);
        }
        return signed;
    }

    /** 注册 ttwid（匿名可用），返回 "ttwid=..." 片段；失败返回 "" */
    public static String registerTtwid(){
        try{
            String ua = UA;
            // 1) union register
            java.net.URL u = new java.net.URL("https://ttwid.bytedance.com/ttwid/union/register/");
            HttpURLConnection c = (HttpURLConnection) u.openConnection();
            c.setRequestMethod("POST");
            c.setRequestProperty("Content-Type", "application/json");
            c.setRequestProperty("User-Agent", ua);
            c.setDoOutput(true);
            byte[] payload = "{\"region\":\"cn\",\"aid\":6383,\"need_t\":1,\"service\":\"www.douyin.com\",\"domain\":\".douyin.com\"}".getBytes(StandardCharsets.UTF_8);
            c.getOutputStream().write(payload);
            c.getOutputStream().close();
            int code = c.getResponseCode();
            InputStream in = (code >= 200 && code < 400) ? c.getInputStream() : c.getErrorStream();
            String body = readAll(in);
            if(body == null || body.indexOf("redirect_url") < 0) return "";
            int p1 = body.indexOf("\"redirect_url\"");
            if(p1 < 0) return "";
            int p2 = body.indexOf("\"", p1 + 15);
            int p3 = body.indexOf("\"", p2 + 1);
            if(p2 < 0 || p3 < 0) return "";
            String redirect = body.substring(p2 + 1, p3);
            // 2) callback 拿 set-cookie
            java.net.URL u2 = new java.net.URL(redirect);
            HttpURLConnection c2 = (HttpURLConnection) u2.openConnection();
            c2.setRequestMethod("GET");
            c2.setRequestProperty("User-Agent", ua);
            c2.setInstanceFollowRedirects(false);
            int code2 = c2.getResponseCode();
            String sc = c2.getHeaderField("Set-Cookie");
            if(sc == null) return "";
            // Set-Cookie: ttwid=...; Path=/; ...
            int e1 = sc.indexOf(';');
            String kv = e1 > 0 ? sc.substring(0, e1) : sc;
            int eq = kv.indexOf('=');
            if(eq <= 0) return "";
            String name = kv.substring(0, eq).trim();
            String val = kv.substring(eq + 1).trim();
            return name + "=" + java.net.URLDecoder.decode(val, "UTF-8");
        }catch(Throwable t){
            return "";
        }
    }

    static String readAll(InputStream in){
        if(in == null) return null;
        try{
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            in.close();
            return new String(bo.toByteArray(), StandardCharsets.UTF_8);
        }catch(Throwable t){ return null; }
    }

    /** 取 detail JSON，带重试（风控是概率性的） */
    public static Result fetch(String awemeId, String cookie, int maxTries){
        Result r = new Result();
        for(int i = 0; i < maxTries; i++){
            r.tries = i + 1;
            try{
                r.signedQuery = buildSignedQuery(awemeId, cookie);
                URL u = new URL(API + "?" + r.signedQuery);
                HttpURLConnection c = (HttpURLConnection) u.openConnection();
                c.setRequestMethod("GET");
                c.setRequestProperty("User-Agent", UA);
                c.setRequestProperty("Accept", "application/json, text/plain, */*");
                c.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9");
                c.setRequestProperty("Referer", "https://www.douyin.com/note/" + awemeId);
                String uifid = cookieValue(cookie, "UIFID_TEMP");
                if(uifid.length() == 0) uifid = cookieValue(cookie, "UIFID");
                if(uifid.length() == 0) uifid = cookieValue(cookie, "uifid");
                if(uifid.length() > 0) c.setRequestProperty("uifid", uifid);
                String ck = cookie;
                if(cookieValue(ck, "ttwid").length() == 0){
                    String tw = registerTtwid();
                    if(tw.length() > 0){
                        ck = (ck == null || ck.length() == 0) ? tw : (ck + "; " + tw);
                    }
                }
                if(ck != null && ck.length() > 0) c.setRequestProperty("Cookie", ck);
                c.setConnectTimeout(12000);
                c.setReadTimeout(15000);
                r.httpCode = c.getResponseCode();
                InputStream in = (r.httpCode >= 200 && r.httpCode < 400) ? c.getInputStream() : c.getErrorStream();
                r.body = "";
                if(in != null){
                    ByteArrayOutputStream bo = new ByteArrayOutputStream();
                    byte[] buf = new byte[8192];
                    int n;
                    while((n = in.read(buf)) > 0) bo.write(buf, 0, n);
                    in.close();
                    r.body = new String(bo.toByteArray(), StandardCharsets.UTF_8);
                }
                if(r.body.indexOf("aweme_detail") >= 0 || r.body.indexOf("item_list") >= 0){
                    r.ok = true;
                    r.err = "";
                    return r;
                }
                r.err = "http=" + r.httpCode + " body="
                       + (r.body.length() > 120 ? r.body.substring(0, 120) : r.body);
            }catch(Throwable t){
                r.err = String.valueOf(t);
            }
            try{ Thread.sleep(400 + i * 400); }catch(Throwable t){}
        }
        return r;
    }

    public static Result fetch(String awemeId, String cookie){
        return fetch(awemeId, cookie, 3);
    }

    /** detail 响应直接可用（render 里的 formatNode 认 aweme_detail），原样返回 */
    public static String normalizeDetail(String json){
        return json;
    }

    /** 从 iesdouyin 长链提取 aweme_id */
    public static String extractAwemeId(String real){
        if(real == null) return "";
        java.util.regex.Matcher mm = java.util.regex.Pattern
            .compile("/(?:video|note|slides)/(\\d{6,25})")
            .matcher(real);
        return mm.find() ? mm.group(1) : "";
    }

    /** cookie 串取单键 */
    static String cookieValue(String cookie, String name){
        if(cookie == null || cookie.length() == 0) return "";
        for(String part : cookie.split(";")){
            int e = part.indexOf('=');
            if(e <= 0) continue;
            if(part.substring(0, e).trim().equals(name)) return part.substring(e + 1).trim();
        }
        return "";
    }

    static String md5Hex(String s){
        try{
            byte[] d = MessageDigest.getInstance("MD5").digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder b = new StringBuilder(d.length * 2);
            for(byte x : d){
                b.append(Character.forDigit((x >> 4) & 0xF, 16));
                b.append(Character.forDigit(x & 0xF, 16));
            }
            return b.toString();
        }catch(Throwable t){ return ""; }
    }

    /** Python urlencode（quote_plus） */
    static String encodeQuery(Map<String, String> params){
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for(Map.Entry<String, String> en : params.entrySet()){
            if(!first) sb.append('&');
            first = false;
            sb.append(quotePlus(en.getKey())).append('=').append(quotePlus(en.getValue()));
        }
        return sb.toString();
    }

    static String quotePlus(String v){
        String in = v == null ? "" : v;
        byte[] b = in.getBytes(StandardCharsets.UTF_8);
        StringBuilder o = new StringBuilder(b.length);
        for(byte x : b){
            int u = x & 0xFF;
            if(isAlwaysSafe(u)){
                o.append((char) u);
            }else if(u == ' '){
                o.append('+');
            }else{
                o.append('%');
                o.append(Character.toUpperCase(Character.forDigit((u >> 4) & 0xF, 16)));
                o.append(Character.toUpperCase(Character.forDigit(u & 0xF, 16)));
            }
        }
        return o.toString();
    }

    /** JS encodeURIComponent 风格，safe 额外保留 !*'() */
    static String quoteNative(String v){
        String in = v == null ? "" : v;
        byte[] b = in.getBytes(StandardCharsets.UTF_8);
        StringBuilder o = new StringBuilder(b.length);
        for(byte x : b){
            int u = x & 0xFF;
            if(isAlwaysSafe(u) || WEBSIGN_SAFE.indexOf((char) u) >= 0){
                o.append((char) u);
            }else{
                o.append('%');
                o.append(Character.toUpperCase(Character.forDigit((u >> 4) & 0xF, 16)));
                o.append(Character.toUpperCase(Character.forDigit(u & 0xF, 16)));
            }
        }
        return o.toString();
    }

    /** 解码 %XX；'+' 保留字面量（unquote 而非 unquote_plus） */
    static String unquotePlus(String v){
        if(v == null) return "";
        if(v.indexOf('%') < 0) return v;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for(int i = 0; i < v.length(); i++){
            char ch = v.charAt(i);
            if(ch == '%' && i + 2 < v.length()){
                int hi = Character.digit(v.charAt(i + 1), 16);
                int lo = Character.digit(v.charAt(i + 2), 16);
                if(hi >= 0 && lo >= 0){
                    out.write((hi << 4) | lo);
                    i += 2;
                    continue;
                }
            }
            byte[] e = String.valueOf(ch).getBytes(StandardCharsets.UTF_8);
            out.write(e, 0, e.length);
        }
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    static boolean isAlwaysSafe(int u){
        return (u >= 'a' && u <= 'z') || (u >= 'A' && u <= 'Z') || (u >= '0' && u <= '9')
            || u == '_' || u == '.' || u == '-' || u == '~';
    }
}