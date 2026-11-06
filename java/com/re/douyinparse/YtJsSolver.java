package com.re.douyinparse;

import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * YouTube 播放器 JS 求解器（B 方案）。
 *
 * 背景（2026-11 实测）：
 *   YouTube 网页端 ytInitialPlayerResponse.streamingData 里，adaptiveFormats 只剩
 *   itag / mimeType / contentLength 这些元数据，**url 和 signatureCipher 全都不下发**。
 *   流地址必须由 player.js 计算 n-sig 后自己拼出来。
 *
 *   而且新版 player.js 已经被 VM 化混淆：
 *     Xn=function(a,b,c,...){var m=b^a; if((a-4&14)==2){ud(m^3463,m^78,c);...}}
 *   全部逻辑都被转成 Xn(opcode, key, ...) / ud(opcode, key, ...) 这种虚拟机调用，
 *   静态正则抽取 sig/n 函数已经完全不可行（经典的那种 a=a.split("") 早已消失）。
 *
 * 因此本类的做法是「在设备上的 JS 引擎里直接跑 player.js」：
 *   1. 下载 base.js
 *   2. 注入一段引导脚本：把带 n 的原始 URL 丢给 youtube 自己的 URL 解析类
 *      （g.zs），让 player.js 自己算出合法 n，再取回改写后的完整 URL
 *   3. 用 WebView 当 JS 运行时执行（安卓自带，零依赖）
 *
 * 为什么用 WebView 而不是 Rhino：
 *   新版 player.js 用了大量 ES6+ 语法（class / 可选链 / 模板串 / let-const），
 *   Rhino 跑不动。WebView 的 V8 原生支持，且和官方浏览器行为一致。
 *
 * 兜底：本类失败时上层会自动回退 Invidious / 自建服务器。
 */
public class YtJsSolver {

    static final String TAG = "DYP";

    /** 从 player.js 里找 n-sig 的调用点，用于拼注入脚本 */
    static final Pattern JS_URL_PAT =
            Pattern.compile("\"(/s/player/[A-Za-z0-9_\\-]+/[^\"]*?base\\.js)\"");

    /** 从页面里抽 player.js 地址 */
    public static String findPlayerJs(String html) {
        if (html == null) return "";
        try {
            Matcher m = JS_URL_PAT.matcher(html);
            if (m.find()) {
                String p = m.group(1);
                return p.startsWith("http") ? p : ("https://www.youtube.com" + p);
            }
            // 兜底：jsUrl 字段
            int i = html.indexOf("\"jsUrl\":\"");
            if (i >= 0) {
                int s = i + 9;
                int e = html.indexOf('"', s);
                if (e > s) {
                    String p = html.substring(s, e);
                    if (p.startsWith("http")) return p;
                    if (p.startsWith("//")) return "https:" + p;
                    if (p.startsWith("/")) return "https://www.youtube.com" + p;
                }
            }
        } catch (Throwable t) {
        }
        return "";
    }

    /**
     * 从页面里抽出 streamingData 里「没有 url 但有 n」的 format 信息，
     * 并在 JS 里重建直链。
     *
     * 返回：改写好的 url 列表（与传入顺序一致），失败返回空数组
     */
    public static String solveN(String playerJsUrl, String rawUrlWithN) {
        // 这里不直接做，实际执行在 MainActivity 的 WebView 里（需要 UI 线程）
        return "";
    }

    /** 判断某条 YouTube 页面是否属于「拿不到 url」的新风控形态 */
    public static boolean needsJsSolver(String pageHtml) {
        if (pageHtml == null) return false;
        boolean hasStreaming = pageHtml.indexOf("\"streamingData\"") >= 0;
        boolean hasUrl = pageHtml.indexOf("\"url\":\"https://") >= 0
                || pageHtml.indexOf("\"signatureCipher\"") >= 0;
        // 有 streamingData 但完全没有 url 也没有 signatureCipher → 需要求解
        return hasStreaming && !hasUrl;
    }

    /**
     * 生成注入到 player.js 所在页面的引导脚本。
     *
     * 原理：player.js 执行完后，全局会挂上 _yt_player / g（内部命名空间），
     * 里面有一个 URL 解析类，对带 n 的地址调用它的 get("n") 会触发签名改写。
     * 我们只需要把原始地址喂进去，读改写结果即可。
     */
    public static String buildInjectScript(String rawUrl) {
        String u = esc(rawUrl);
        return "(function(){try{"
                + "var raw='" + u + "';"
                + "var out='';"
                // 玩家命名空间可能有多个别名，逐个试探
                + "var roots=[window.g,window._yt_player,window.ytcfg];"
                + "for(var i=0;i<roots.length;i++){var R=roots[i];if(!R)continue;"
                + "  try{"
                + "    if(R.zs){var o=new R.zs(raw,true);var n=o.get('n');"
                + "      if(n){out=raw.replace(/\\/n\\/[^/]+/,'/n/'+n);}"
                + "    }"
                + "  }catch(e){}"
                + "  if(out)break;"
                + "}"
                + "window.__DYP_NSIG=out||'';"
                + "}catch(e){window.__DYP_NSIG='ERR:'+e;}})();";
    }

    /**
     * 从 HTML 里抠「marker= {...}」的完整 JSON 对象（按花括号配平，跳过字符串内的括号）。
     * 用于抽 ytInitialPlayerResponse。
     */
    public static String extractJsonObject(String html, String marker) {
        if (html == null) return null;
        try {
            int i = html.indexOf(marker);
            if (i < 0) return null;
            i = html.indexOf('{', i);
            if (i < 0) return null;
            int depth = 0;
            boolean inq = false, esc = false;
            for (int k = i; k < html.length(); k++) {
                char c = html.charAt(k);
                if (inq) {
                    if (esc) esc = false;
                    else if (c == '\\') esc = true;
                    else if (c == '"') inq = false;
                } else {
                    if (c == '"') inq = true;
                    else if (c == '{') depth++;
                    else if (c == '}') {
                        depth--;
                        if (depth == 0) return html.substring(i, k + 1);
                    }
                }
            }
        } catch (Throwable t) {
        }
        return null;
    }

    /** 取 JSON 里 "key":"value" 的字符串值 */
    public static String jgetStr(String json, String key) {
        if (json == null) return "";
        try {
            int p = json.indexOf("\"" + key + "\"");
            if (p < 0) return "";
            int c = json.indexOf(':', p + key.length() + 2);
            if (c < 0) return "";
            int i = c + 1;
            while (i < json.length() && Character.isWhitespace(json.charAt(i))) i++;
            if (i >= json.length() || json.charAt(i) != '"') return "";
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
                } else if (x == '"') break;
                else b.append(x);
                i++;
            }
            return b.toString();
        } catch (Throwable t) {
            return "";
        }
    }

    /**
     * 从 playerResponse 里挑最优格式。
     * 返回 String[3]：{视频url, 音频url, 待求解的原始url}
     *   - 若格式自带 url，直接填 [0]/[1]
     *   - 若是 signatureCipher 形态，把解出来的原始地址填 [2] 交给 WebView
     *   - 若连 cipher 都没有（新版元数据形态），[0]/[2] 都为空
     */
    public static String[] pickBestFormats(String pr) {
        String[] out = new String[]{"", "", ""};
        if (pr == null) return out;
        try {
            String arr = jarrBody(pr, "adaptiveFormats");
            if (arr.length() == 0) return out;
            int bestV = -1, bestVbr = -1;
            int bestA = -1, bestAbr = -1;
            String cipherRaw = "";
            int bestCipherBr = -1;

            int pos = 0;
            while (true) {
                int s = arr.indexOf('{', pos);
                if (s < 0) break;
                int depth = 0;
                boolean inq = false, esc = false;
                int e = -1;
                for (int k = s; k < arr.length(); k++) {
                    char c = arr.charAt(k);
                    if (inq) {
                        if (esc) esc = false;
                        else if (c == '\\') esc = true;
                        else if (c == '"') inq = false;
                    } else {
                        if (c == '"') inq = true;
                        else if (c == '{') depth++;
                        else if (c == '}') {
                            depth--;
                            if (depth == 0) {
                                e = k;
                                break;
                            }
                        }
                    }
                }
                if (e < 0) break;
                String o = arr.substring(s, e + 1);
                pos = e + 1;

                String type = jgetStr(o, "type");
                String url = jgetStr(o, "url");
                int br = 0;
                try {
                    int bi = o.indexOf("\"bitrate\":");
                    if (bi > 0) {
                        int bs = bi + 10;
                        int be = bs;
                        while (be < o.length() && Character.isDigit(o.charAt(be))) be++;
                        br = Integer.parseInt(o.substring(bs, be));
                    }
                } catch (Throwable t) {
                }
                boolean isV = type.startsWith("video");
                if (url.length() > 0 && url.startsWith("http")) {
                    if (isV) {
                        boolean mp4 = type.indexOf("mp4") >= 0;
                        int score = br + (mp4 ? 5000000 : 0);
                        if (score > bestVbr) {
                            bestVbr = score;
                            bestV = 0;
                            out[0] = url;
                        }
                    } else if (type.startsWith("audio")) {
                        boolean m4a = type.indexOf("mp4") >= 0;
                        if (m4a && br > bestAbr) {
                            bestAbr = br;
                            out[1] = url;
                        } else if (bestAbr < 0 && br > 0 && out[1].length() == 0) {
                            out[1] = url;
                        }
                    }
                }
                // signatureCipher 形态
                String cipher = jgetStr(o, "signatureCipher");
                if (cipher.length() == 0) cipher = jgetStr(o, "cipher");
                if (cipher.length() > 0 && isV && br > bestCipherBr) {
                    bestCipherBr = br;
                    // cipher 里 s 需要签名，url 是基础地址；新版多带 n 参数
                    String cu = urlDecode(jgetParam(cipher, "url"));
                    String cn = jgetParam(cipher, "n");
                    if (cn.length() > 0) cu = cu + "&n=" + cn;
                    cipherRaw = cu;
                }
            }
            out[2] = cipherRaw;
        } catch (Throwable t) {
        }
        return out;
    }

    /** 抠 "key":[...] 的数组体（不含外层方括号） */
    static String jarrBody(String json, String key) {
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

    /** 从 a=1&b=2 里取参数 */
    static String jgetParam(String q, String key) {
        if (q == null) return "";
        String[] parts = q.split("&");
        for (String p : parts) {
            int e = p.indexOf('=');
            if (e > 0 && p.substring(0, e).equals(key)) return p.substring(e + 1);
        }
        return "";
    }

    static String urlDecode(String s) {
        try {
            return java.net.URLDecoder.decode(s, "UTF-8");
        } catch (Throwable t) {
            return s;
        }
    }

    static String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "").replace("\r", "");
    }
}