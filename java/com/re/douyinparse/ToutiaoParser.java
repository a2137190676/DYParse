package com.re.douyinparse;

import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/**
 * 今日头条解析（借鉴 ucmao/media-parser 的 toutiao 逆向方案）。
 *
 * 链路：m.toutiao.com 移动端 SSR（script#RENDER_DATA，URL 编码 JSON）
 *   → articleInfo.playAuthTokenV2（base64 的 JSON，含 GetPlayInfoToken）
 *   → https://vod.bytedanceapi.com/?{GetPlayInfoToken}
 *   → Result.Data.PlayInfoList（按 Bitrate 降序取最高）
 *
 * 微头条（w/）的正文/图片/作者在 articleInfo.thread.threadBase 下。
 * 全部本地 HTTP，免 Cookie、免服务器。
 */
public class ToutiaoParser {

    static final String TAG = "DYP";

    public static class TtItem {
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
        public long playCount = 0;
        public String playUrl = "";
        /** [label, url, w, h] */
        public List<String[]> gears = new ArrayList<String[]>();
        public List<String> images = new ArrayList<String>();

        public boolean hasVideo(){ return playUrl != null && playUrl.length() > 0; }
        public boolean hasImages(){ return images != null && images.size() > 0; }
    }

    public static boolean isToutiaoUrl(String u){
        if(u == null) return false;
        String s = u.toLowerCase(java.util.Locale.US);
        return s.contains("toutiao.com") || s.contains("toutiaocdn.com")
            || s.contains("ixigua.com") || s.contains("bytedanceapi.com");
    }

    static final String UA =
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
            uc.setRequestProperty("Accept", "text/html,application/json,*/*");
            if(referer != null && referer.length() > 0) uc.setRequestProperty("Referer", referer);
            int code = uc.getResponseCode();
            if(code < 200 || code >= 400) return null;
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            java.io.InputStream in = uc.getInputStream();
            byte[] buf = new byte[8192];
            int n;
            while((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            in.close();
            return new String(bo.toByteArray(), "UTF-8");
        }catch(Throwable t){
            return null;
        }finally{
            try{ if(uc != null) uc.disconnect(); }catch(Throwable t){}
        }
    }

    /** 从任意头条链接里抽 item_id */
    static String extractItemId(String url){
        if(url == null) return "";
        // 纯数字长 ID（15~20 位）
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d{15,20})").matcher(url);
        if(m.find()) return m.group(1);
        // /w/ /video/ /group/ /item/ 后面
        m = java.util.regex.Pattern.compile("(?:/w/|/video/|/group/|/item/|/i)([0-9a-zA-Z_-]{10,})").matcher(url);
        if(m.find()) return m.group(1);
        return "";
    }

    /** 主入口 */
    public static TtItem resolve(String url){
        TtItem it = new TtItem();
        try{
            String id = extractItemId(url);
            if(id.length() == 0){ it.err = "无法识别头条链接"; return it; }
            it.id = id;
            Log.i(TAG, "TT_ID " + id);
            // 注意：/group/ 会 301 → /article/，直接用 article 最快
            String[] cands = new String[]{
                "https://m.toutiao.com/article/" + id + "/",
                "https://m.toutiao.com/video/" + id + "/",
                "https://m.toutiao.com/w/" + id + "/",
                "https://m.toutiao.com/i" + id + "/",
                "https://m.toutiao.com/group/" + id + "/",
                "https://m.toutiao.com/item/" + id + "/"
            };
            String html = null;
            String usedUrl = null;
            for(int i = 0; i < cands.length; i++){
                String h = get(cands[i], null);
                if(h == null || h.length() < 500) continue;
                if(h.contains("RENDER_DATA")){ html = h; usedUrl = cands[i]; break; }
            }
            if(html == null){ it.err = "头条页面拉取失败"; Log.i(TAG, "TT_FAIL"); return it; }
            Log.i(TAG, "TT_HTML len=" + html.length() + " url=" + usedUrl);
            // 抽 RENDER_DATA
            java.util.regex.Matcher m = java.util.regex.Pattern.compile(
                "<script[^>]*id=\"RENDER_DATA\"[^>]*>(.*?)</script>", java.util.regex.Pattern.DOTALL).matcher(html);
            if(!m.find()){ it.err = "未找到 RENDER_DATA"; return it; }
            String raw = m.group(1).trim();
            try{
                raw = java.net.URLDecoder.decode(raw, "UTF-8");
            }catch(Throwable t){}
            JSONObject root = new JSONObject(raw);
            String rtype = root.optString("type", "");
            if("404".equals(rtype) || "error".equals(rtype)){
                it.err = "头条页面不存在（404）";
                Log.i(TAG, "TT_404 type=" + rtype);
                return it;
            }
            JSONObject ai = root.optJSONObject("articleInfo");
            if(ai == null){ it.err = "RENDER_DATA 无 articleInfo"; return it; }
            // 微头条归一化
            JSONObject thread = ai.optJSONObject("thread");
            JSONObject tb = (thread != null) ? thread.optJSONObject("threadBase") : null;
            // 标题
            it.title = ai.optString("title", "");
            if(it.title.length() == 0 && root.optJSONObject("seoTDK") != null){
                String st = root.optJSONObject("seoTDK").optString("title", "");
                if(st.endsWith("-今日头条")) st = st.substring(0, st.length() - 5);
                it.title = st.trim();
            }
            if(it.title.length() == 0 && tb != null) it.title = tb.optString("title", "");
            // 正文
            String content = ai.optString("content", "");
            if(content.length() == 0 && tb != null){
                content = tb.optString("content", tb.optString("richContent", ""));
            }
            it.desc = cleanDesc(content);
            // 作者
            JSONObject mu = ai.optJSONObject("mediaUser");
            if(mu != null){
                it.author = mu.optString("screenName", mu.optString("name", ""));
                it.avatar = mu.optString("avatarUrl", "");
            }
            if(it.author.length() == 0 && tb != null){
                JSONObject u = tb.optJSONObject("user");
                JSONObject info = (u != null) ? u.optJSONObject("info") : null;
                if(info != null){
                    it.author = info.optString("name", "");
                    it.avatar = info.optString("avatarUrl", "");
                }
            }
            // 时间
            long ct = ai.optLong("publishTime", ai.optLong("createTime", 0));
            if(ct <= 0 && tb != null) ct = tb.optLong("createTime", 0);
            if(ct > 0){
                if(ct < 100000000000L) ct = ct * 1000L;
                it.createdAt = ct;
            }
            // 封面
            it.cover = ai.optString("posterUrl", ai.optString("coverUrl", ""));
            if(it.cover.length() == 0){
                JSONObject vi = ai.optJSONObject("videoInfo");
                if(vi != null) it.cover = vi.optString("posterUrl", vi.optString("coverUrl", ""));
            }
            // 图集（微头条）
            JSONArray ti = ai.optJSONArray("threadImageList");
            if(ti != null){
                for(int i = 0; i < ti.length(); i++){
                    String u2 = ti.optString(i, "");
                    if(u2.length() > 0) it.images.add(u2);
                }
            }
            if(it.images.size() == 0 && tb != null){
                JSONArray lg = tb.optJSONArray("largeImageList");
                if(lg == null) lg = tb.optJSONArray("originImageList");
                if(lg != null){
                    for(int i = 0; i < lg.length(); i++){
                        JSONObject o = lg.optJSONObject(i);
                        if(o == null) continue;
                        String u2 = o.optString("url", "");
                        if(u2.length() > 0) it.images.add(u2);
                    }
                }
            }
            if(it.images.size() == 0 && it.cover.length() > 0) it.images.add(it.cover);
            // 视频：playAuthTokenV2 → vod.bytedanceapi.com
            String tk = ai.optString("playAuthTokenV2", "");
            if(tk.length() > 0){
                resolveVod(it, tk);
            }
            it.ok = it.hasVideo() || it.hasImages() || it.title.length() > 0;
            Log.i(TAG, "TT_OK title=" + it.title + " author=" + it.author
                + " v=" + it.hasVideo() + " gears=" + it.gears.size()
                + " imgs=" + it.images.size());
            if(!it.ok && it.err.length() == 0) it.err = "未取到头条内容";
        }catch(Throwable t){
            it.err = String.valueOf(t);
            Log.i(TAG, "TT_ERR " + t);
        }
        return it;
    }

    /** playAuthTokenV2 → GetPlayInfoToken → vod 接口 */
    static void resolveVod(TtItem it, String tokenV2){
        try{
            String dec = new String(android.util.Base64.decode(tokenV2, android.util.Base64.DEFAULT), "UTF-8");
            JSONObject to = new JSONObject(dec);
            String q = to.optString("GetPlayInfoToken", "");
            if(q.length() == 0){ Log.i(TAG, "TT_VOD no query"); return; }
            String vodUrl = "https://vod.bytedanceapi.com/?" + q;
            String body = get(vodUrl, "https://m.toutiao.com/");
            if(body == null){ Log.i(TAG, "TT_VOD null"); return; }
            Log.i(TAG, "TT_VOD len=" + body.length());
            JSONObject r = new JSONObject(body);
            JSONObject result = r.optJSONObject("Result");
            JSONObject data = (result != null) ? result.optJSONObject("Data") : r.optJSONObject("Data");
            if(data == null){ Log.i(TAG, "TT_VOD no Data"); return; }
            if(it.cover.length() == 0) it.cover = data.optString("CoverUrl", "");
            JSONArray pl = data.optJSONArray("PlayInfoList");
            if(pl == null){ Log.i(TAG, "TT_VOD no PlayInfoList"); return; }
            // 收集全部档位
            List<long[]> meta = new ArrayList<long[]>();
            for(int i = 0; i < pl.length(); i++){
                JSONObject o = pl.optJSONObject(i);
                if(o == null) continue;
                String u = o.optString("MainPlayUrl", o.optString("BackupPlayUrl", ""));
                if(u.length() == 0) continue;
                long br = o.optLong("Bitrate", 0);
                long w = o.optLong("Width", 0);
                long h = o.optLong("Height", 0);
                long d = o.optLong("Duration", 0);
                if(d > 0 && it.durMs == 0) it.durMs = d * 1000L;
                String label = (h > 0) ? (h + "P") : ("码率" + (br / 1000) + "k");
                it.gears.add(new String[]{ label, u, String.valueOf(w), String.valueOf(h) });
                meta.add(new long[]{ br, it.gears.size() - 1 });
            }
            // 按 Bitrate 降序取最高
            if(it.gears.size() > 0){
                int best = 0;
                long bestBr = -1;
                for(int i = 0; i < it.gears.size(); i++){
                    long br = 0;
                    try{ br = Long.parseLong(it.gears.get(i)[2]); }catch(Throwable t){}
                    // 用分辨率当权重（更直观）
                    br = parseL(it.gears.get(i)[2]) * parseL(it.gears.get(i)[3]);
                    if(br > bestBr){ bestBr = br; best = i; }
                }
                it.playUrl = it.gears.get(best)[1];
                // 把最高档提到第一个
                if(best != 0){
                    String[] tmp = it.gears.get(0);
                    it.gears.set(0, it.gears.get(best));
                    it.gears.set(best, tmp);
                }
            }
            Log.i(TAG, "TT_VOD ok gears=" + it.gears.size() + " dur=" + it.durMs);
        }catch(Throwable t){
            Log.i(TAG, "TT_VOD_ERR " + t);
        }
    }

    static long parseL(String s){
        try{ return Long.parseLong(s); }catch(Throwable t){ return 0; }
    }

    /** 正文去 HTML、去多余空白 */
    static String cleanDesc(String c){
        if(c == null) return "";
        String s = c.replaceAll("<br\\s*/?>", "\n")
                   .replaceAll("<[^>]+>", "")
                   .replace("&nbsp;", " ")
                   .replace("&amp;", "&")
                   .replace("&lt;", "<")
                   .replace("&quot;", "\"");
        s = s.replaceAll("[\\t\\f\\v ]+", " ");
        s = s.replaceAll("\\n{3,}", "\n\n");
        return s.trim();
    }

    /** 输出 render() 认的 i_* JSON */
    public static String toJson(TtItem it){
        StringBuilder b = new StringBuilder();
        b.append("{");
        b.append("\"i_platform\":\"今日头条\",");
        kv(b, "i_id", it.id.length() > 0 ? it.id : ("tt_" + Math.abs(it.title.hashCode())));
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


    /** 输出 renderCards() 认的「【标签】值」文本 */
    public static String toText(TtItem it){
        StringBuilder b = new StringBuilder();
        b.append("【平台】今日头条\n");
        if(it.id.length() > 0) b.append("【作品ID】").append(it.id).append("\n");
        if(it.title.length() > 0) b.append("【标题】").append(it.title).append("\n");
        if(it.author.length() > 0) b.append("【作者】").append(it.author).append("\n");
        if(it.desc.length() > 0) b.append("【描述】").append(it.desc).append("\n");
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
    /** 返回 [imgs, play, cover]；imgs 用 | 分隔 */
    public static String[] toMedia(TtItem it){
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

}