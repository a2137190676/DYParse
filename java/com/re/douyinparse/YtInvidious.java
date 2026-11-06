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

/**
 * YouTube 解析器 · Invidious 直连通道（v2.1.1 起的第一优先级）。
 *
 * 为什么需要这个类：
 *  2026-11 起 YouTube 网页端的 ytInitialPlayerResponse 里 streamingData 只给
 *  itag / mimeType / contentLength 这类元数据，**url 和 signatureCipher 两个字段全部消失**
 *  （实测 playabilityStatus=OK、34 条 adaptive、有 url 的 0 条），
 *  意味着流地址必须由服务端算 n-challenge 才下发，手机上再怎么抓页面也拿不到。
 *
 *  同时自建服务器 /api/yt/parse 被 YouTube 全面风控（yt-dlp 一挂就是 110 秒无响应）。
 *
 *  Invidious 是开源的 YouTube 前端，它自己在服务端做完整解析，
 *  调用 /api/v1/videos/{id} 直接返回带 url 的 adaptiveFormats（实测 32 条全带 url），
 *  且返回的 googlevideo 直链支持 Range（实测 206），手机上可以直接拉流/下载。
 *
 * 设计约束：
 *  1. 实例是公共免费服务，随时可能挂 —— 必须多实例轮询 + 全挂时回退服务器
 *  2. 实例列表硬编码一份保底，再叠加运行时探测到的可用实例
 *  3. 全程不依赖自建服务器，服务器到期也能用
 */
public class YtInvidious {

    static final String TAG = "DYP";
    static final String UA = "Mozilla/5.0 (Linux; Android 12; Pixel 5) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36";

    /**
     * 已知实例（保底名单）。
     * 顺序有意义：越靠前越优先。chocolatemoo53 是 2026-11 实测唯一还在返回 url 的，
     * 其余作为冗余，挂了会自动往后试。
     *
     * 注意：这些是第三方公共实例，可用性随时会变（实测 2026-11 除第一个外
     * 其余全部 403/401/404/SSL 中断）。所以本类只当「第一优先级」，
     * 上层 YtParser 一定还有 B 方案（本机 player.js 求解）和自建服务器兜底。
     */
    static final String[] INSTANCES = new String[]{
            "https://yt.chocolatemoo53.com",
            "https://inv.nadeko.net",
            "https://invidious.nerdvpn.de",
            "https://yewtu.be",
            "https://invidious.privacyredirect.com",
    };

    /** 调试开关：置 true 可强制跳过 Invidious，用来单独验证 B 方案 */
    static boolean SKIP_INVIDIOUS = false;

    /** 运行时探到的可用实例（成功过一次就提前，避免每次都从头试） */
    static String lastGood = "";
    /**
     * 无头 WebView 跑 player.js，解出 n-sig 改写后的完整 URL。
     *
     * 思路：
     *   1. 建一个 1x1 隐藏 WebView，打开 https://www.youtube.com/（让 player.js 自然加载）
     *   2. 等页面就绪后，把带 n 的原始 googlevideo 地址注入进去
     *   3. 用页面里已经加载好的 player.js 自己的 URL 解析类算出 n
     *   4. 拿回改写后的地址
     *
     * 返回：改写后的 url，失败返回 ""。
     * 调用必须在主线程；内部阻塞等待，最长约 25 秒。
     */
    static String ytSolveNByWebView(final android.content.Context ctx, final String rawUrl) {
        if (rawUrl == null || rawUrl.length() == 0) return "";
        final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        final String[] out = new String[]{""};

        new android.os.Handler(android.os.Looper.getMainLooper()).post(new Runnable() {
            public void run() {
                try {
                    final android.webkit.WebView wv = new android.webkit.WebView(ctx);
                    android.webkit.WebSettings ws = wv.getSettings();
                    ws.setJavaScriptEnabled(true);
                    ws.setDomStorageEnabled(true);
                    // 1x1 不可见，避免闪一下
                    wv.setLayoutParams(new android.view.ViewGroup.LayoutParams(1, 1));
                    wv.setVisibility(android.view.View.GONE);

                    wv.setWebViewClient(new android.webkit.WebViewClient() {
                        boolean injected = false;

                        public void onPageFinished(android.webkit.WebView view, String url) {
                            if (injected) return;
                            injected = true;
                            // 页面自带的 player.js 已加载完，把待解地址喂进去
                            String js = YtJsSolver.buildInjectScript(rawUrl);
                            DypLog.i("YT_JS_INJECT " + url);
                            view.evaluateJavascript(js, null);
                            // 给 JS 一点时间跑
                            new android.os.Handler(android.os.Looper.getMainLooper())
                                    .postDelayed(new Runnable() {
                                        public void run() {
                                            view.evaluateJavascript(
                                                    "(function(){try{return window.__DYP_NSIG||'';}catch(e){return '';}})()",
                                                    new android.webkit.ValueCallback<String>() {
                                                        public void onReceiveValue(String v) {
                                                            try {
                                                                if (v != null && v.length() > 2) {
                                                                    String s = v;
                                                                    if (s.startsWith("\"") && s.endsWith("\""))
                                                                        s = s.substring(1, s.length() - 1);
                                                                    s = s.replace("\\/", "/").replace("\\u003d", "=")
                                                                            .replace("\\u0026", "&");
                                                                    if (s.startsWith("http")) out[0] = s;
                                                                }
                                                                DypLog.i("YT_JS_RESULT len=" + (out[0] == null ? 0 : out[0].length())
                                                                        + " raw=" + (v == null ? "null" : v.substring(0, Math.min(60, v.length()))));
                                                            } catch (Throwable t) {
                                                            }
                                                            latch.countDown();
                                                        }
                                                    });
                                        }
                                    }, 3500);
                        }
                    });
                    wv.loadUrl("https://www.youtube.com/watch?v="
                            + YtInvidious.extractId(rawUrl));
                } catch (Throwable t) {
                    DypLog.i("YT_JS_WV_ERR " + t);
                    latch.countDown();
                }
            }
        });

        try {
            if (!latch.await(25, java.util.concurrent.TimeUnit.SECONDS)) {
                DypLog.i("YT_JS_TIMEOUT");
                return "";
            }
        } catch (Throwable t) {
            return "";
        }
        return out[0] == null ? "" : out[0];
    }

    /**
     * 从任意 YouTube 链接抠视频 ID
     */
    public static String extractId(String url) {
        if (url == null) return "";
        try {
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("(?:v=|/shorts/|/embed/|youtu\\.be/|/live/)([A-Za-z0-9_-]{11})")
                    .matcher(url);
            if (m.find()) return m.group(1);
            // 纯 ID
            if (url.matches("^[A-Za-z0-9_-]{11}$")) return url;
        } catch (Throwable t) {
        }
        return "";
    }

    /** 解析结果，字段与 YtParser.YtVideo 对齐 */
    public static class Result {
        public String id = "";
        public String title = "";
        public String author = "";
        public String thumb = "";
        public long durationSec = 0;
        public String videoUrl = "";
        public String audioUrl = "";
        public String err = "";
        public String source = "";   // 命中的实例域名，调试用
        public int qualityHeight = 0;

        public boolean ok() {
            return videoUrl.length() > 0;
        }
    }

    /**
     * 主入口：多实例轮询解析。
     * 全挂返回带 err 的对象，调用方据此回退自建服务器。
     */
    public static Result resolve(Context ctx, String pageUrl) {
        Result r = new Result();
        String id = extractId(pageUrl);
        if (id.length() == 0) {
            r.err = "拿不到视频 ID";
            return r;
        }
        r.id = id;
        if (SKIP_INVIDIOUS) {
            r.err = "SKIP_INVIDIOUS 调试开关已开启";
            return r;
        }

        List<String> order = new ArrayList<String>();
        if (lastGood.length() > 0) order.add(lastGood);
        for (String s : INSTANCES) if (!order.contains(s)) order.add(s);

        for (String base : order) {
            try {
                Result one = fetchOne(ctx, base, id);
                if (one != null && one.ok()) {
                    lastGood = base;
                    Log.i(TAG, "YT_INV_OK host=" + hostOf(base) + " q=" + one.qualityHeight
                            + "p v=" + one.videoUrl.length() + " a=" + one.audioUrl.length());
                    return one;
                }
                Log.i(TAG, "YT_INV_MISS " + hostOf(base)
                        + (one == null ? " null" : (" " + one.err)));
            } catch (Throwable t) {
                Log.i(TAG, "YT_INV_ERR " + hostOf(base) + " " + t);
            }
        }
        r.err = "所有 Invidious 实例都不可用";
        return r;
    }

    static String hostOf(String u) {
        try {
            return new URL(u).getHost();
        } catch (Throwable t) {
            return u;
        }
    }

    /**
     * 打单个实例。
     * 优先 /api/v1/videos/{id}（JSON），失败再试 /latest_version?id=&itag=（重定向到直链）。
     */
    static Result fetchOne(Context ctx, String base, String id) {
        String api = base + "/api/v1/videos/" + id;
        byte[] body = httpGet(ctx, api, 12000, 15000);
        if (body == null || body.length < 200) return null;
        String js = new String(body, java.nio.charset.Charset.forName("UTF-8"));
        Log.i(TAG, "YT_INV_BODY host=" + hostOf(base) + " len=" + js.length());

        Result r = new Result();
        r.id = id;
        r.source = hostOf(base);
        r.title = str(jget(js, "title"));
        r.author = str(jget(js, "author"));
        try {
            r.durationSec = Long.parseLong(str(jget(js, "lengthSeconds")).trim());
        } catch (Throwable t) {
        }
        String thumb = str(jget(js, "videoThumbnails"));
        // 缩略图直接用 i.ytimg 固定模板，不用解析数组
        if (r.title.length() == 0) r.err = "无 title";
        r.thumb = "https://i.ytimg.com/vi/" + id + "/maxresdefault.jpg";

        // adaptiveFormats 里挑最优视频（bitrate 最大）+ 最优 mp4 音轨
        String af = jarr(js, "adaptiveFormats");
        if (af.length() == 0) {
            r.err = "无 adaptiveFormats";
            return r;
        }
        String[] items = splitObjs(af);
        String bestVideo = "";
        int bestVideoBr = -1;
        int bestVideoH = 0;
        String bestAudio = "";
        int bestAudioBr = -1;
        String fallbackAudio = "";
        int fallbackAudioBr = -1;

        for (String o : items) {
            String type = str(jget(o, "type"));
            String url = str(jget(o, "url"));
            if (url.length() == 0) continue;
            if (!url.startsWith("http")) continue;
            int br = 0;
            try {
                br = Integer.parseInt(str(jget(o, "bitrate")).trim());
            } catch (Throwable t) {
            }
            if (type.startsWith("video")) {
                // 优先 mp4（安卓解码器对 vp9/av1 支持不稳定），同码率下 mp4 更稳
                boolean mp4 = type.indexOf("mp4") >= 0;
                int h = hOf(o);
                int score = br + (mp4 ? 5000000 : 0);
                if (score > bestVideoBr) {
                    bestVideoBr = score;
                    bestVideo = url;
                    bestVideoH = h;
                }
            } else if (type.startsWith("audio")) {
                boolean m4a = type.indexOf("mp4") >= 0;
                if (m4a) {
                    if (br > bestAudioBr) {
                        bestAudioBr = br;
                        bestAudio = url;
                    }
                } else {
                    if (br > fallbackAudioBr) {
                        fallbackAudioBr = br;
                        fallbackAudio = url;
                    }
                }
            }
        }
        if (bestAudio.length() == 0) bestAudio = fallbackAudio;

        r.videoUrl = bestVideo;
        r.audioUrl = bestAudio;
        r.qualityHeight = bestVideoH;
        if (bestVideo.length() == 0) r.err = "没有可用视频轨";
        return r;
    }

    /** 从 format 对象里读分辨率高度（resolution="2160p" 或 height 字段） */
    static int hOf(String obj) {
        try {
            String res = str(jget(obj, "resolution"));
            if (res.length() > 0) {
                java.util.regex.Matcher m = java.util.regex.Pattern
                        .compile("(\\d{3,4})p").matcher(res);
                if (m.find()) return Integer.parseInt(m.group(1));
            }
            String h = str(jgetRaw(obj, "height"));
            if (h.length() > 0) return (int) Double.parseDouble(h);
        } catch (Throwable t) {
        }
        return 0;
    }

    // ================= 极简 JSON / HTTP 工具 =================

    /** 取 "key": "value" 里的字符串值（跳过数组/对象的写法） */
    static Object jget(String json, String key) {
        if (json == null) return "";
        int p = json.indexOf("\"" + key + "\"");
        if (p < 0) return "";
        int c = json.indexOf(':', p + key.length() + 2);
        if (c < 0) return "";
        int i = c + 1;
        while (i < json.length() && Character.isWhitespace(json.charAt(i))) i++;
        if (i >= json.length()) return "";
        char ch = json.charAt(i);
        if (ch == '"') {
            StringBuilder b = new StringBuilder();
            i++;
            while (i < json.length()) {
                char x = json.charAt(i);
                if (x == '\\') {
                    i++;
                    if (i >= json.length()) break;
                    char e = json.charAt(i);
                    if (e == 'n') b.append('\n');
                    else if (e == 't') b.append('\t');
                    else if (e == 'r') b.append('\r');
                    else if (e == 'u') {
                        try {
                            b.append((char) Integer.parseInt(json.substring(i + 1, i + 5), 16));
                            i += 4;
                        } catch (Throwable t) {
                        }
                    } else b.append(e);
                } else if (x == '"') {
                    break;
                } else {
                    b.append(x);
                }
                i++;
            }
            return b.toString();
        }
        // 数字 / true / false
        int b = i;
        while (i < json.length()) {
            char x = json.charAt(i);
            if (x == ',' || x == '}' || x == ']' || Character.isWhitespace(x)) break;
            i++;
        }
        return json.substring(b, Math.min(i, json.length()));
    }

    /** 取原始 token（数字/true/false），与 jget 相同但语义更清晰 */
    static Object jgetRaw(String json, String key) {
        return jget(json, key);
    }

    /** 抠 "key":[...] 的数组体（含方括号） */
    static String jarr(String json, String key) {
        if (json == null) return "";
        int p = json.indexOf("\"" + key + "\"");
        if (p < 0) return "";
        int c = json.indexOf('[', p);
        if (c < 0) return "";
        int depth = 0;
        boolean inq = false, esc = false;
        for (int i = c; i < json.length(); i++) {
            char ch = json.charAt(i);
            if (inq) {
                if (esc) esc = false;
                else if (ch == '\\') esc = true;
                else if (ch == '"') inq = false;
            } else {
                if (ch == '"') inq = true;
                else if (ch == '[') depth++;
                else if (ch == ']') {
                    depth--;
                    if (depth == 0) return json.substring(c + 1, i);
                }
            }
        }
        return "";
    }

    /** 把 "[{...},{...}]" 拆成单个对象串 */
    static String[] splitObjs(String arrBody) {
        List<String> out = new ArrayList<String>();
        int depth = 0;
        boolean inq = false, esc = false;
        int start = -1;
        for (int i = 0; i < arrBody.length(); i++) {
            char ch = arrBody.charAt(i);
            if (inq) {
                if (esc) esc = false;
                else if (ch == '\\') esc = true;
                else if (ch == '"') inq = false;
                continue;
            }
            if (ch == '"') {
                inq = true;
            } else if (ch == '{') {
                if (depth == 0) start = i;
                depth++;
            } else if (ch == '}') {
                depth--;
                if (depth == 0 && start >= 0) {
                    out.add(arrBody.substring(start, i + 1));
                    start = -1;
                }
            }
        }
        return out.toArray(new String[0]);
    }

    static String str(Object o) {
        return o == null ? "" : o.toString();
    }

    /** 直连 GET（Invidious 实例不走自建服务器，但要走手机代理） */
    static byte[] httpGet(android.content.Context ctx, String url, int connMs, int readMs) {
        HttpURLConnection c = null;
        InputStream in = null;
        try {
            // 关键：必须走 MainActivity.openConn —— 它会按用户在设置里的
            // 代理开关决定是否套用本机代理。
            // 实测（2026-11）：直接 new URL().openConnection() 走系统直连，
            // 打 yt.chocolatemoo53.com 会 SSLHandshakeException: connection closed，
            // 而同一台机器上 python（走本地代理）访问同一地址 200/12105 字节。
            // 说明公共实例在 TUN 直连路径上被中断，必须和 TikTok 一样走代理通道。
            c = MainActivity.openConn(ctx, url);
            c.setInstanceFollowRedirects(true);
            c.setConnectTimeout(connMs);
            c.setReadTimeout(readMs);
            c.setRequestProperty("User-Agent", UA);
            c.setRequestProperty("Accept", "application/json,*/*");
            c.setRequestProperty("Accept-Encoding", "gzip");
            int code = c.getResponseCode();
            if (code < 200 || code >= 300) {
                Log.i(TAG, "YT_INV_HTTP " + code + " " + hostOf(url));
                return null;
            }
            in = c.getInputStream();
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            int guard = 0;
            while ((n = in.read(buf)) > 0) {
                bo.write(buf, 0, n);
                if (++guard > 4096) break;   // 上限 32MB
            }
            byte[] raw = bo.toByteArray();
            // 简单 gunzip（Invidious 可能不压缩，兜底判断）
            if (raw.length > 2 && (raw[0] & 0xff) == 0x1f && (raw[1] & 0xff) == 0x8b) {
                try {
                    java.util.zip.GZIPInputStream gz = new java.util.zip.GZIPInputStream(
                            new java.io.ByteArrayInputStream(raw));
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    byte[] b2 = new byte[8192];
                    int m;
                    while ((m = gz.read(b2)) > 0) out.write(b2, 0, m);
                    gz.close();
                    return out.toByteArray();
                } catch (Throwable t) {
                }
            }
            return raw;
        } catch (Throwable t) {
            Log.i(TAG, "YT_INV_GEX " + hostOf(url) + " " + t);
            return null;
        } finally {
            try {
                if (in != null) in.close();
            } catch (Throwable t) {
            }
            try {
                if (c != null) c.disconnect();
            } catch (Throwable t) {
            }
        }
    }
}