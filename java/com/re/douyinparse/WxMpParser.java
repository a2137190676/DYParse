package com.re.douyinparse;

import android.util.Log;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 微信公众号文章解析（借鉴 ucmao/media-parser 的 wechat_mp 逆向方案）。
 *
 * 全部本地 HTTP，免 Cookie、免服务器。公众号文章是 SSR 直出，变量直接写在 HTML 里：
 *  - var msg_title / var nickname / var msg_cdn_url / var user_name
 *  - 正文图 data-src → /0? 无损原图
 *  - 内嵌视频 {url: 'https://mpvideo.qpic.cn/...'} 多清晰度分组取最优
 *  - 内嵌音频 mpvoice → res.wx.qq.com/voice/getvoice?mediaid=...
 */
public class WxMpParser {

    static final String TAG = "DYP";

    public static class MpArticle {
        public boolean ok = false;
        public String err = "";
        public String title = "";
        public String author = "";
        public String authorId = "";
        public String desc = "";
        public String cover = "";
        public String publishTime = "";
        public long publishTs = 0;
        public String audioUrl = "";
        public String videoUrl = "";
        public List<String> videos = new ArrayList<String>();
        public List<String> images = new ArrayList<String>();

        public boolean hasVideo(){ return videoUrl != null && videoUrl.length() > 0; }
        public boolean hasImages(){ return images != null && images.size() > 0; }
        public boolean hasAudio(){ return audioUrl != null && audioUrl.length() > 0; }
    }

    public static boolean isWxMpUrl(String u){
        if(u == null) return false;
        String s = u.toLowerCase(java.util.Locale.US);
        return s.contains("mp.weixin.qq.com/s")
            || s.contains("mp.weixin.qq.com/mp/")
            || s.contains("weixin.qq.com/s?__biz")
            || s.contains("mp.weixin.qq.com/s?__biz");
    }

    static String fetch(String url){
        java.net.HttpURLConnection uc = null;
        try{
            uc = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
            uc.setRequestMethod("GET");
            uc.setInstanceFollowRedirects(true);
            uc.setConnectTimeout(12000);
            uc.setReadTimeout(20000);
            uc.setRequestProperty("User-Agent",
                "Mozilla/5.0 (Linux; Android 13; SM-G991B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36");
            uc.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
            uc.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9");
            int code = uc.getResponseCode();
            Log.i(TAG, "WXMP_CODE " + code);
            if(code < 200 || code >= 400) return null;
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            java.io.InputStream in = uc.getInputStream();
            byte[] buf = new byte[8192];
            int n;
            while((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            in.close();
            String enc = "UTF-8";
            String ct = uc.getContentType();
            if(ct != null && ct.toLowerCase().contains("gbk")) enc = "GBK";
            return new String(bo.toByteArray(), enc);
        }catch(Throwable t){
            Log.i(TAG, "WXMP_HTTP_ERR " + t);
            return null;
        }finally{
            try{ if(uc != null) uc.disconnect(); }catch(Throwable t){}
        }
    }

    static String pick(Pattern p, String html){
        if(html == null) return "";
        Matcher m = p.matcher(html);
        if(!m.find()) return "";
        return m.group(1) == null ? "" : m.group(1).trim();
    }

    static String unesc(String s){
        if(s == null) return "";
        return s.replace("\\x26amp;", "&").replace("&amp;", "&")
                .replace("\\x22", "\"").replace("\\/", "/")
                .replace("&quot;", "\"").replace("&#39;", "'")
                .replace("&lt;", "<").replace("&gt;", ">");
    }

    /** 主入口 */
    public static MpArticle resolve(String url){
        MpArticle a = new MpArticle();
        try{
            String html = fetch(url);
            if(html == null || html.length() < 500){
                a.err = "公众号文章拉取失败";
                Log.i(TAG, "WXMP_FAIL len=" + (html == null ? -1 : html.length()));
                return a;
            }
            Log.i(TAG, "WXMP_HTML len=" + html.length());
            // 风控/删除检测
            if(html.contains("该内容已被发布者删除") || html.contains("此内容因违规无法查看")){
                a.err = "文章已被删除或违规不可见";
                return a;
            }
            if(html.contains("环境异常") && html.contains("完成验证")){
                a.err = "公众号触发风控，需稍后重试";
                return a;
            }
            // 标题（可能跟 .html(false) / htmlDecode("...") 包裹）
            a.title = unesc(firstOf(html, new String[]{
                "var msg_title\\s*=\\s*htmlDecode\\(\\s*[\'\"](.*?)[\'\"]\\s*\\)",
                "var msg_title\\s*=\\s*[\'\"](.*?)[\'\"]",
                "msg_title\\s*:\\s*[\'\"](.*?)[\'\"]",
                "<meta\\s+property=\"og:title\"\\s+content=\"(.*?)\""
            }));
            a.title = stripHtmlFns(a.title);
            if(a.title.length() == 0){
                a.title = unesc(firstOf(html, new String[]{ "<title>([^<]*)</title>" }));
            }
            // 作者（nickname 常被 htmlDecode\(\) 包着）
            a.author = unesc(firstOf(html, new String[]{
                "var nickname\\s*=\\s*htmlDecode\\(\\s*[\'\"](.*?)[\'\"]\\s*\\)",
                "var nickname\\s*=\\s*[\'\"](.*?)[\'\"]",
                "nick_name\\s*:\\s*[\'\"](.*?)[\'\"]"
            }));
            a.authorId = unesc(firstOf(html, new String[]{
                "var user_name\\s*=\\s*[\'\"](.*?)[\'\"]",
                "user_name\\s*:\\s*[\'\"](.*?)[\'\"]"
            }));
            // 封面
            a.cover = unesc(firstOf(html, new String[]{
                "var msg_cdn_url\\s*=\\s*[\'\"](.*?)[\'\"]",
                "cdn_url\\s*:\\s*[\'\"](.*?)[\'\"]",
                "<meta\\s+property=\"og:image\"\\s+content=\"(.*?)\""
            }));
            // 发布时间：var ct = "1790833583" / var create_time = "1790833583" * 1
            String ts = firstOf(html, new String[]{
                "var\\s+ct\\s*=\\s*[\'\"](\\d{10})[\'\"]",
                "var\\s+create_time\\s*=\\s*[\'\"](\\d{10})[\'\"]",
                "create_time\\s*=\\s*[\'\"](\\d{10})[\'\"]",
                "publish_time%22%3A(\\d{10})"
            });
            if(ts.length() > 0){
                try{
                    a.publishTs = Long.parseLong(ts) * 1000L;
                    java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm");
                    f.setTimeZone(java.util.TimeZone.getTimeZone("Asia/Shanghai"));
                    a.publishTime = f.format(new java.util.Date(a.publishTs));
                }catch(Throwable t){}
            }
            // 摘要
            a.desc = unesc(firstOf(html, new String[]{
                "<meta\\s+name=\"description\"\\s+content=\"(.*?)\"",
                "var msg_desc\\s*=\\s*htmlDecode\\(\\s*[\'\"](.*?)[\'\"]\\s*\\)",
                "var msg_desc\\s*=\\s*[\'\"](.*?)[\'\"]"
            }));
            a.desc = stripHtmlFns(a.desc).replaceAll("<[^>]+>", "").trim();
            if(a.desc.equals(a.title)) a.desc = "";
            // 正文图：data-src（懒加载原图）
            Pattern ip = Pattern.compile("<img[^>]+data-src=\"([^\"]+)\"");
            Matcher im = ip.matcher(html);
            java.util.LinkedHashSet<String> imgSet = new java.util.LinkedHashSet<String>();
            while(im.find()){
                String u = unesc(im.group(1));
                if(u.startsWith("//")) u = "https:" + u;
                if(!u.contains("mmbiz")) continue;
                // /0? 无损原始画质
                u = toOriginalImg(u);
                imgSet.add(u);
            }
            a.images.addAll(imgSet);
            if(a.images.size() == 0 && a.cover.length() > 0){
                a.images.add(a.cover);
            }
            // 内嵌音频 mpvoice
            String voiceId = pick(Pattern.compile(
                "<mpvoice[^>]*voice_encode_fileid=\"([^\"]+)\""), html);
            if(voiceId.length() == 0) voiceId = pick(Pattern.compile("\"voice_encode_fileid\"\\s*:\\s*\"([^\"]+)\""), html);
            if(voiceId.length() > 0){
                a.audioUrl = "https://res.wx.qq.com/voice/getvoice?mediaid=" + voiceId;
            }
            // 内嵌视频：按 file_id 分组，取最优
            collectVideos(a, html);
            a.ok = a.title.length() > 0 || a.hasVideo() || a.hasImages();
            Log.i(TAG, "WXMP_OK title=" + a.title + " imgs=" + a.images.size()
                + " vids=" + a.videos.size() + " audio=" + (a.audioUrl.length() > 0 ? 1 : 0));
            if(!a.ok && a.err.length() == 0) a.err = "未取到文章内容";
        }catch(Throwable t){
            a.err = String.valueOf(t);
            Log.i(TAG, "WXMP_ERR " + t);
        }
        return a;
    }


    /** 按顺序尝试多个正则，返回第一个命中的 group(1) */
    static String firstOf(String html, String[] pats){
        if(html == null) return "";
        for(int i = 0; i < pats.length; i++){
            Matcher m = Pattern.compile(pats[i], Pattern.DOTALL).matcher(html);
            if(m.find()){
                String s = m.group(1);
                if(s != null && s.trim().length() > 0) return s.trim();
            }
        }
        return "";
    }

    /** 去掉残留的 .html(false) / htmlDecode( ) 外壳 */
    static String stripHtmlFns(String s){
        if(s == null) return "";
        String r = s.trim();
        if(r.endsWith(".html(false)")) r = r.substring(0, r.length() - 12).trim();
        r = r.replaceAll("^htmlDecode\\s*\\(\\s*", "");
        if(r.endsWith(")")) r = r.substring(0, r.length() - 1);
        if(r.startsWith("\"") || r.startsWith("'")) r = r.substring(1);
        if(r.endsWith("\"") || r.endsWith("'")) r = r.substring(0, r.length() - 1);
        return r.trim();
    }

    /** mmbiz 图转无损原图：/640?wx_fmt=jpeg → /0?wx_fmt=jpeg */
    static String toOriginalImg(String u){
        if(u == null) return "";
        // mmbiz 图尺寸段：/0 无损、/640 /300 /200 /6 有损 → 统一 /0
        String r = u.replaceAll("/(?:640|300|200|1080|750|6)(\\?|$)", "/0$1");
        // 部分链接尺寸在尾部且无问号
        r = r.replaceAll("/(?:640|300|200|1080|750|6)$", "/0");
        if(r.startsWith("//")) r = "https:" + r;
        return r;
    }

    /** 内嵌视频 mpvideo.qpic.cn：同 file_id 多码率，按 quality/分辨率/filesize 取最优 */
    static void collectVideos(MpArticle a, String html){
        if(html == null) return;
        // 1) 先把所有 mpvideo 直链取出来（不依赖周围块结构，最稳）
        //    并把 &amp; 转回 &
        java.util.regex.Matcher dm = Pattern.compile(
            "(https?:)?//mpvideo\\.qpic\\.cn/[^\\s\'\"<>\\\\]+").matcher(html);
        java.util.LinkedHashMap<String, java.util.List<String>> groups =
            new java.util.LinkedHashMap<String, java.util.List<String>>();
        while(dm.find()){
            String u = dm.group(0);
            if(u.startsWith("//")) u = "http:" + u;
            u = unesc(u);
            // 去掉尾部残留的 \x26amp; / &amp;
            String key = gearKey(u);
            if(key.length() == 0) continue;
            java.util.List<String> lst = groups.get(key);
            if(lst == null){ lst = new java.util.ArrayList<String>(); groups.put(key, lst); }
            if(!lst.contains(u)) lst.add(u);
        }
        // 2) 每个 file_id 取最高码率
        java.util.Iterator<String> it = groups.keySet().iterator();
        while(it.hasNext()){
            String key = it.next();
            java.util.List<String> lst = groups.get(key);
            String best = null;
            int bestRank = -1;
            for(int i = 0; i < lst.size(); i++){
                String u = lst.get(i);
                int rank = gearRank(u);
                if(rank > bestRank){ bestRank = rank; best = u; }
            }
            if(best != null && !a.videos.contains(best)){
                a.videos.add(best);
                Log.i(TAG, "WXMP_VIDEO " + key + " rank=" + bestRank + " " + best);
            }
        }
        if(a.videos.size() > 0) a.videoUrl = a.videos.get(0);
    }

    /** 从 mpvideo 链接取 file_id 作分组键 */
    static String gearKey(String u){
        Matcher m = Pattern.compile("mpvideo\\.qpic\\.cn/([A-Za-z0-9_-]+)\\.f(\\d+)").matcher(u);
        if(m.find()) return m.group(1);
        Matcher m2 = Pattern.compile("mpvideo\\.qpic\\.cn/([A-Za-z0-9_-]+)").matcher(u);
        if(m2.find()) return m2.group(1);
        return "";
    }

    /** 测略码率等级：f10104/f10102 > f10004/f10002（微信内部编码） */
    static int gearRank(String u){
        Matcher m = Pattern.compile("\\.f(\\d+)\\.mp4").matcher(u);
        if(!m.find()) return 0;
        String f = m.group(1);
        // f10002=SD f10004=SD+ f10102=HD f10104=HD+；数字越大越高
        try{ return Integer.parseInt(f); }catch(Throwable t){ return 0; }
    }

    static int cmpGear(String[] a, String[] b){
        int qa = (int)parseL(a[0]), qb = (int)parseL(b[0]);
        if(qa != qb) return qa > qb ? 1 : -1;
        long ra = parseL(a[1]), rb = parseL(b[1]);
        if(ra != rb) return ra > rb ? 1 : -1;
        long fa = parseL(a[2]), fb = parseL(b[2]);
        return fa > fb ? 1 : (fa == fb ? 0 : -1);
    }

    static long parseL(String s){
        try{ return Long.parseLong(s); }catch(Throwable t){ return 0; }
    }

    static int numAttr(String block, String key){
        Matcher m = Pattern.compile(key + "\\s*:\\s*[\'\\\"`]?(\\d+)").matcher(block);
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    }

    /** 转成 render() 认识的 JSON */
    public static String toResultJson(MpArticle a){
        StringBuilder b = new StringBuilder();
        b.append("{");
        b.append("\"i_platform\":\"微信公众号\",");
        // render() 用 i_id 当门禁，没有就不处理 i_play/i_imgs，必须给个值
        kv(b, "i_id", a.authorId.length() > 0 ? a.authorId : ("wxmp_" + Math.abs(a.title.hashCode())));
        b.append("\"i_author\":\"").append(esc(a.author)).append("\",");
        b.append("\"i_desc\":\"").append(esc(a.title)).append("\",");
        if(a.publishTs > 0) b.append("\"i_time\":\"").append(a.publishTs).append("\",");
        if(a.cover.length() > 0) b.append("\"i_cover\":\"").append(esc(a.cover)).append("\",");
        if(a.hasVideo()){
            b.append("\"i_play\":\"").append(esc(a.videoUrl)).append("\",");
        }
        if(a.hasAudio()){
            b.append("\"i_bgm\":\"").append(esc(a.audioUrl)).append("\",");
        }
        if(a.hasImages()){
            StringBuilder im = new StringBuilder();
            for(int i = 0; i < a.images.size(); i++){
                if(i > 0) im.append("|");
                im.append(a.images.get(i));
            }
            b.append("\"i_imgs\":\"").append(esc(im.toString())).append("\",");
        }
        if(a.videos.size() > 1){
            StringBuilder g = new StringBuilder("[");
            for(int i = 0; i < a.videos.size(); i++){
                if(i > 0) g.append(",");
                g.append("{\"g\":\"视频").append(i + 1).append("\",\"q\":0,\"br\":0,\"hdr\":0,\"hevc\":0,");
                g.append("\"w\":0,\"h\":0,\"fps\":0,\"u\":\"").append(esc(a.videos.get(i))).append("\"}");
            }
            g.append("]");
            b.append("\"i_gears\":\"").append(esc(g.toString())).append("\",");
        }
        String s = b.toString();
        if(s.endsWith(",")) s = s.substring(0, s.length() - 1);
        return s + "}";
    }


    /** 输出 render() 认的 i_* JSON */
    public static String toJson(MpArticle a){
        StringBuilder b = new StringBuilder();
        b.append("{");
        b.append("\"i_platform\":\"微信公众号\",");
        if(a.author.length() > 0) kv(b, "i_author", a.author);
        if(a.title.length() > 0) kv(b, "i_desc", a.title);
        if(a.publishTs > 0) kv(b, "i_time", String.valueOf(a.publishTs));
        if(a.cover.length() > 0) kv(b, "i_cover", a.cover);
        if(a.hasVideo()) kv(b, "i_play", a.videoUrl);
        if(a.hasAudio()) kv(b, "i_bgm", a.audioUrl);
        if(a.hasImages()){
            StringBuilder im = new StringBuilder();
            for(int i = 0; i < a.images.size(); i++){
                if(i > 0) im.append("|");
                im.append(a.images.get(i));
            }
            kv(b, "i_imgs", im.toString());
        }
        if(a.videos.size() > 1){
            StringBuilder g = new StringBuilder("[");
            for(int i = 0; i < a.videos.size(); i++){
                if(i > 0) g.append(",");
                g.append("{\"g\":\"视频").append(i + 1).append("\",\"q\":0,\"br\":0,\"hdr\":0,\"hevc\":0,");
                g.append("\"w\":0,\"h\":0,\"fps\":0,\"u\":\"").append(esc(a.videos.get(i))).append("\"}");
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


    /** 输出 renderCards() 认的「【标签】值」纯文本 */
    public static String toText(MpArticle a){
        StringBuilder b = new StringBuilder();
        b.append("【平台】微信公众号\n");
        if(a.title.length() > 0) b.append("【标题】").append(a.title).append("\n");
        if(a.author.length() > 0) b.append("【公众号】").append(a.author).append("\n");
        if(a.desc.length() > 0) b.append("【摘要】").append(a.desc).append("\n");
        if(a.publishTime.length() > 0) b.append("【发布时间】").append(a.publishTime).append("\n");
        if(a.cover.length() > 0) b.append("【封面】").append(a.cover).append("\n");
        if(a.hasVideo()){
            b.append("【视频直链】").append(a.videoUrl).append("\n");
            for(int i = 1; i < a.videos.size(); i++){
                b.append("【视频").append(i + 1).append("】").append(a.videos.get(i)).append("\n");
            }
        }
        if(a.hasAudio()) b.append("【嵌入音频】").append(a.audioUrl).append("\n");
        if(a.hasImages()){
            b.append("【图集】").append(a.images.size()).append(" 张\n");
            for(int i = 0; i < a.images.size(); i++){
                b.append("【图").append(i + 1).append("】").append(a.images.get(i)).append("\n");
            }
        }
        if(a.hasVideo()) b.append("【音频下载】点下方「提取音频」，从视频中提取音轨为 MP3\n");
        return b.toString();
    }

    /** 返回 [imgs, play, cover]；imgs 用 | 分隔 */
    public static String[] toMedia(MpArticle a){
        StringBuilder im = new StringBuilder();
        for(int i = 0; i < a.images.size(); i++){
            if(i > 0) im.append("|");
            im.append(a.images.get(i));
        }
        return new String[]{ im.toString(), a.videoUrl == null ? "" : a.videoUrl,
                             a.cover == null ? "" : a.cover };
    }


    static String esc(String s){
        if(s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "").replace("\t", " ");
    }
}