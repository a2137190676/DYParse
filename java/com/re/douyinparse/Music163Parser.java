package com.re.douyinparse;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

/**
 * 网易云音乐解析（纯 API 方案，不做本地协议逆向）
 *
 * 设计原则（按用户要求）：
 *   · 走第三方公开 API，不逆向官方加密协议
 *   · API 挂了就明确告知用户「暂不可用」，不假装能用
 *   · 绝不抛异常，任何失败都返回带人话原因的 Item
 *
 * 数据源分工：
 *   元数据（歌名/歌手/封面/时长）：网易云官方公开接口 music.163.com/api/song/detail
 *                                   —— 免登录、免加密、长期稳定
 *   播放直链（mp3/flac）：          music-api.gdstudio.xyz
 *                                   —— 第三方公开实例，含版权限制，部分歌为空
 *   备用直链：                      api.injahow.cn/meting / api.i-meto.com/meting
 *                                   —— 仅当主源为空时兜底尝试
 *
 * 支持两种输入：
 *   · 歌曲链接：https://music.163.com/song?id=xxx  /  y.music.163.com/m/song?id=xxx
 *   · 纯数字ID：186016
 *   · 分享短链：https://163cn.tv/xxxxx（需跟随重定向）
 */
public class Music163Parser {

    static final String TAG = "DYP";

    /** 官方详情接口（免登录） */
    static final String API_DETAIL = "https://music.163.com/api/song/detail/";
    /** 官方搜索接口（免登录） */
    static final String API_SEARCH = "https://music.163.com/api/search/get/web";
    /** 第三方直链源（主） */
    static final String API_GDS = "https://music-api.gdstudio.xyz/api.php";
    /** 第三方直链源（备） */
    static final String API_METO = "https://api.i-meto.com/meting/api";

    static final String UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
        + "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";

    // ---------------- 数据结构 ----------------

    public static class Song {
        public String id = "";
        public String name = "";
        public String artist = "";
        public String album = "";
        public String cover = "";
        public long durationMs = 0;
        public String lyric = "";

        public String playUrl = "";
        public String playBr = "";       // 320 / 128 / 797(flac)
        public long playSize = 0;
        public String src = "";          // gdstudio / meto

        public boolean ok = false;
        public String err = "";

        public boolean hasAudio(){ return playUrl != null && playUrl.length() > 8; }
        public String durationText(){
            if(durationMs <= 0) return "";
            long s = durationMs / 1000;
            return String.format("%d:%02d", s / 60, s % 60);
        }
    }

    // ---------------- URL 识别 ----------------

    /** 是否网易云相关链接 */
    public static boolean isMusic163Url(String u){
        if(u == null) return false;
        String s = u.toLowerCase().trim();
        if(s.startsWith("magnet:") || s.contains(".torrent")) return false;
        return s.contains("music.163.com")
            || s.contains("y.music.163.com")
            || s.contains("163cn.tv")
            || s.startsWith("music163:")
            || s.contains("music.163.com/song")
            || s.contains("music.163.com/#/song")
            || s.contains("music.163.com/program")     // 电台节目
            || s.contains("music.163.com/album");      // 专辑
    }

    /** 输入是否被网易云解析器接管（URL 或纯数字 ID） */
    public static boolean canHandle(String input){
        if(input == null) return false;
        String s = input.trim();
        if(s.length() == 0) return false;
        if(isMusic163Url(s)) return true;
        // 纯数字 ID（6 位以上，避免误吃普通文字）
        if(s.matches("\\d{6,}")) return true;
        return false;
    }

    // ---------------- 主入口 ----------------

    /**
     * 解析。绝不抛异常。
     * @param input 歌曲链接 / 纯数字 ID / 分享文案
     */
    public static Song resolve(String input){
        Song sg = new Song();
        if(input == null || input.trim().length() == 0){
            sg.err = "请输入网易云歌曲链接或歌曲 ID";
            return sg;
        }
        try{
            String s = input.trim();
            String id = extractId(s);

            // 短链 163cn.tv：跟随重定向后再抠 ID
            if(id.length() == 0 && s.contains("163cn.tv")){
                String real = follow(s);
                Log.i(TAG, "M163_REDIR " + cut(real, 120));
                id = extractId(real);
            }

            // 只有文本没有 ID：用官方搜索兜底
            if(id.length() == 0){
                String kw = cleanText(s);
                if(kw.length() > 0){
                    Log.i(TAG, "M163_SEARCH kw=" + kw);
                    id = searchFirst(kw);
                }
            }

            if(id.length() == 0){
                sg.err = "无法从链接中识别歌曲 ID\n请用 App 分享 → 复制链接，或直接输入歌曲数字 ID";
                return sg;
            }

            Log.i(TAG, "M163_ID id=" + id);

            // 1) 官方元数据
            fillMeta(sg, id);
            Log.i(TAG, "M163_META name=" + sg.name + " artist=" + sg.artist
                + " dur=" + sg.durationMs + " cover=" + (sg.cover.length() > 0));

            // 2) 第三方直链
            fillAudio(sg, id);

            whyFail(sg);
        }catch(Throwable t){
            sg.err = "解析异常：" + t.getClass().getSimpleName();
            Log.i(TAG, "M163_EX " + t);
        }
        return sg;
    }

    /** 失败时给出人话原因 */
    static void whyFail(Song sg){
        if(sg.hasAudio()){
            sg.ok = true;
            sg.err = "";
            return;
        }
        if(sg.name.length() == 0){
            sg.err = "网络请求失败\n可能原因：当前网络无法访问网易云接口\n建议：切换网络或用代理后重试";
        } else {
            // 有元数据但没直链 —— 最典型的情况
            sg.err = "《" + sg.name + "》暂无可下载音频\n"
                + "可能原因：\n"
                + "  · 该歌曲受版权保护，第三方接口未收录\n"
                + "  · 需要会员/付费才能播放\n"
                + "  · 第三方解析服务临时不可用\n"
                + "建议：换一首歌试试，或稍后再试";
        }
    }

    // ---------------- 元数据 ----------------

    static void fillMeta(Song sg, String id){
        try{
            String body = get(API_DETAIL + "?id=" + id + "&ids="
                + URLEncoder.encode("[" + id + "]", "UTF-8"));
            if(body == null || body.length() < 10) return;
            JSONObject j = new JSONObject(body);
            JSONArray arr = j.optJSONArray("songs");
            if(arr == null || arr.length() == 0) return;
            JSONObject s = arr.optJSONObject(0);
            if(s == null) return;

            sg.id = id;
            sg.name = s.optString("name", "");
            sg.durationMs = s.optLong("duration", 0);

            JSONArray artists = s.optJSONArray("artists");
            if(artists != null){
                List<String> names = new ArrayList<String>();
                for(int i = 0; i < artists.length(); i++){
                    JSONObject a = artists.optJSONObject(i);
                    if(a != null){
                        String n = a.optString("name", "");
                        if(n.length() > 0) names.add(n);
                    }
                }
                sg.artist = join(names, " / ");
            }

            JSONObject album = s.optJSONObject("album");
            if(album != null){
                sg.album = album.optString("name", "");
                String pic = album.optString("picUrl", "");
                if(pic.length() == 0) pic = album.optString("blurPicUrl", "");
                sg.cover = pic;
            }
        }catch(Throwable t){
            Log.i(TAG, "M163_META_ERR " + t.getClass().getSimpleName());
        }
    }

    // ---------------- 直链 ----------------

    static void fillAudio(Song sg, String id){
        // 音质优先级（按 App 内置播放器兼容性排序）：
        //   320 -> 192 -> 128  （MP3，播放器能解码，默认首选）
        //   999                （无损 FLAC，仅在前三者都没有时才用；
        //                       ExoPlayer 部分机型/内核不支持 FLAC 解码，
        //                       所以不作为默认，避免出现「解码失败」）
        String[] brs = new String[]{"320", "192", "128", "999"};
        for(int i = 0; i < brs.length; i++){
            if(tryGds(sg, id, brs[i])) return;
        }
        Log.i(TAG, "M163_GDS_EMPTY id=" + id);

        // 备源 i-meto：两步（拿 detail 里的签名 url，再拉流）
        if(tryMeto(sg, id)) return;

        Log.i(TAG, "M163_ALL_EMPTY id=" + id);
    }

    static boolean tryGds(Song sg, String id, String br){
        try{
            String body = get(API_GDS + "?types=url&source=netease&id=" + id + "&br=" + br);
            if(body == null || body.length() < 5) return false;
            JSONObject j = new JSONObject(body);
            String u = j.optString("url", "");
            if(u.length() < 10) return false;
            sg.playUrl = u;
            sg.playBr = String.valueOf(j.optInt("br", 0));
            sg.playSize = j.optLong("size", 0);
            sg.src = "gdstudio";
            Log.i(TAG, "M163_GDS_OK br=" + sg.playBr + " size=" + sg.playSize
                + " url=" + cut(u, 80));
            return true;
        }catch(Throwable t){
            return false;
        }
    }

    static boolean tryMeto(Song sg, String id){
        try{
            // 第一步：detail 里带 auth 签名的 url
            String body = get(API_METO + "?type=song&id=" + id + "&server=netease");
            if(body == null || body.length() < 10) return false;
            JSONArray arr = new JSONArray(body);
            if(arr.length() == 0) return false;
            JSONObject o = arr.optJSONObject(0);
            if(o == null) return false;
            String u = o.optString("url", "");
            if(u.length() < 10) return false;

            // 第二步：验证这个 url 真的能拉到音频（不是空 HTML）
            if(!probeAudio(u)) {
                Log.i(TAG, "M163_METO_PROBE_FAIL");
                return false;
            }
            sg.playUrl = u;
            sg.playBr = "128";
            sg.src = "meto";
            if(sg.name.length() == 0) sg.name = o.optString("title", "");
            if(sg.artist.length() == 0) sg.artist = o.optString("author", "");
            if(sg.cover.length() == 0) sg.cover = o.optString("pic", "");
            Log.i(TAG, "M163_METO_OK url=" + cut(u, 80));
            return true;
        }catch(Throwable t){
            return false;
        }
    }

    /** 探一次响应头，确认是音频不是空页面 */
    static boolean probeAudio(String u){
        HttpURLConnection c = null;
        InputStream in = null;
        try{
            c = (HttpURLConnection) new URL(u).openConnection();
            c.setRequestMethod("GET");
            c.setInstanceFollowRedirects(true);
            c.setConnectTimeout(8000);
            c.setReadTimeout(9000);
            c.setRequestProperty("User-Agent", UA);
            c.setRequestProperty("Referer", "https://music.163.com/");
            int code = c.getResponseCode();
            if(code != 200) return false;
            String ct = c.getContentType();
            if(ct == null) ct = "";
            if(ct.toLowerCase().contains("audio")
                || ct.toLowerCase().contains("octet-stream")
                || ct.toLowerCase().contains("mpeg")) return true;
            // 拿一点内容看是不是 ID3 / FLAC 头
            in = c.getInputStream();
            byte[] head = new byte[4];
            int n = in.read(head);
            if(n >= 3 && head[0] == 'I' && head[1] == 'D' && head[2] == '3') return true;
            if(n >= 4 && head[0] == 'f' && head[1] == 'L' && head[2] == 'a' && head[3] == 'C') return true;
            if(n >= 2 && (head[0] & 0xFF) == 0xFF && (head[1] & 0xE0) == 0xE0) return true; // mp3 帧头
            return false;
        }catch(Throwable t){
            return false;
        }finally{
            try{ if(in != null) in.close(); }catch(Throwable t){}
            try{ if(c != null) c.disconnect(); }catch(Throwable t){}
        }
    }

    // ---------------- ID 提取 ----------------

    /** 从链接里抠歌曲 ID */
    public static String extractId(String s){
        if(s == null) return "";
        String u = s.trim();

        // 1) 纯数字
        if(u.matches("\\d{6,}")) return u;

        // 2) 明确参数：?id=xxx / &id=xxx
        String[] keys = new String[]{"id=", "/song/", "/program/", "/album/"};
        for(int i = 0; i < keys.length; i++){
            int p = u.indexOf(keys[i]);
            if(p >= 0){
                int st = p + keys[i].length();
                StringBuilder sb = new StringBuilder();
                for(int k = st; k < u.length(); k++){
                    char c = u.charAt(k);
                    if(c >= '0' && c <= '9') sb.append(c);
                    else break;
                }
                if(sb.length() >= 6) return sb.toString();
            }
        }

        // 3) 兜底：全文找最长的数字串
        String best = "";
        StringBuilder cur = new StringBuilder();
        for(int k = 0; k <= u.length(); k++){
            char c = k < u.length() ? u.charAt(k) : ' ';
            if(c >= '0' && c <= '9') cur.append(c);
            else {
                if(cur.length() >= 6 && cur.length() > best.length()) best = cur.toString();
                cur.setLength(0);
            }
        }
        return best;
    }

    /** 纯数字 ID -> 标准链接 */
    public static String toUrl(String id){
        if(id == null || id.length() == 0) return "";
        return "https://music.163.com/song?id=" + id;
    }

    // ---------------- 搜索 ----------------

    /** 用官方搜索拿第一个歌曲 ID */
    static String searchFirst(String kw){
        try{
            String body = get(API_SEARCH + "?s=" + URLEncoder.encode(kw, "UTF-8")
                + "&type=1&limit=1&offset=0");
            if(body == null || body.length() < 20) return "";
            JSONObject j = new JSONObject(body);
            JSONObject r = j.optJSONObject("result");
            if(r == null) return "";
            JSONArray songs = r.optJSONArray("songs");
            if(songs == null || songs.length() == 0) return "";
            JSONObject s = songs.optJSONObject(0);
            if(s == null) return "";
            return String.valueOf(s.optLong("id", 0));
        }catch(Throwable t){
            return "";
        }
    }

    /** 把分享文案里的关键信息抽出来（去 emoji、去 URL） */
    static String cleanText(String s){
        if(s == null) return "";
        String t = s;
        // 去掉 URL
        t = t.replaceAll("https?://\\S+", " ");
        // 去掉常见分享话术
        t = t.replaceAll("分享单曲", " ").replaceAll("分享", " ");
        t = t.replaceAll("@网易云音乐", " ").replaceAll("网易云音乐", " ");
        t = t.replaceAll("\\(.*?\\)", " ");
        t = t.replaceAll("[\\[【].*?[\\]】]", " ");
        t = t.replaceAll("[\\p{So}\\p{Cn}]", " ");   // emoji / 私用区
        t = t.trim().replaceAll("\\s+", " ");
        return t.length() > 60 ? t.substring(0, 60) : t;
    }

    // ---------------- HTTP ----------------

    static String get(String url){
        HttpURLConnection c = null;
        InputStream in = null;
        try{
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setRequestMethod("GET");
            c.setInstanceFollowRedirects(true);
            c.setConnectTimeout(10000);
            c.setReadTimeout(15000);
            c.setRequestProperty("User-Agent", UA);
            c.setRequestProperty("Referer", "https://music.163.com/");
            c.setRequestProperty("Accept", "application/json, text/plain, */*");
            int code = c.getResponseCode();
            in = (code >= 200 && code < 300) ? c.getInputStream() : c.getErrorStream();
            if(in == null) return "";
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            return new String(bo.toByteArray(), "UTF-8");
        }catch(Throwable t){
            return "";
        }finally{
            try{ if(in != null) in.close(); }catch(Throwable t){}
            try{ if(c != null) c.disconnect(); }catch(Throwable t){}
        }
    }

    static String follow(String url){
        HttpURLConnection c = null;
        try{
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setRequestMethod("GET");
            c.setInstanceFollowRedirects(true);
            c.setConnectTimeout(9000);
            c.setReadTimeout(11000);
            c.setRequestProperty("User-Agent", UA);
            c.getResponseCode();
            return c.getURL() == null ? url : c.getURL().toString();
        }catch(Throwable t){
            return url;
        }finally{
            try{ if(c != null) c.disconnect(); }catch(Throwable t){}
        }
    }

    // ---------------- 输出 ----------------

    /** 生成展示文本 */
    public static String toText(Song sg){
        if(sg == null) return "网易云解析失败";
        if(!sg.ok) return "解析失败：" + sg.err;

        StringBuilder sb = new StringBuilder();
        sb.append("【").append(sg.name).append("】\n");
        if(sg.artist.length() > 0) sb.append("歌手：").append(sg.artist).append("\n");
        if(sg.album.length() > 0) sb.append("专辑：").append(sg.album).append("\n");
        if(sg.durationMs > 0) sb.append("时长：").append(sg.durationText()).append("\n");

        String q = sg.playBr;
        if("797".equals(q) || "999".equals(q)) q = "无损 FLAC（含 flac 格式，若播放器不支持请用「转MP3」）";
        else if(q.length() > 0) q = q + "kbps MP3";
        if(q.length() > 0){
            sb.append("音质：").append(q);
            if(sg.playSize > 0){
                sb.append("（").append(String.format("%.1f MB", sg.playSize / 1048576.0)).append("）");
            }
            sb.append("\n");
        }
        sb.append("\n音频直链：\n").append(sg.playUrl);
        return sb.toString();
    }

    /**
     * 生成媒体信息，顺序必须与 o2(text, imgs, play, cover) 对齐：
     *   [0] imgs    —— 图集（音乐没有图集，必须给空串）
     *   [1] play    —— 播放/下载地址
     *   [2] cover   —— 封面
     * 文件名单独通过 fileName() 取，不再塞进这个数组。
     */
    public static String[] toMedia(Song sg){
        if(sg == null || !sg.ok) return new String[]{"", "", ""};
        return new String[]{"", sg.playUrl, sg.cover};
    }

    /** 下载用的文件名（带扩展名） */
    public static String fileName(Song sg){
        if(sg == null || !sg.ok) return "music163.mp3";
        String ext = "mp3";
        if("797".equals(sg.playBr) || "999".equals(sg.playBr)) ext = "flac";
        String safe = (sg.artist.length() > 0 ? sg.artist + " - " : "") + sg.name;
        safe = safe.replaceAll("[\\\\/:*?\"<>|]", "_");
        if(safe.length() > 80) safe = safe.substring(0, 80);
        return safe + "." + ext;
    }

    // ---------------- 小工具 ----------------

    static String join(List<String> arr, String sep){
        if(arr == null || arr.size() == 0) return "";
        StringBuilder sb = new StringBuilder();
        for(int i = 0; i < arr.size(); i++){
            if(i > 0) sb.append(sep);
            sb.append(arr.get(i));
        }
        return sb.toString();
    }

    static String cut(String s, int n){
        if(s == null) return "";
        return s.length() <= n ? s : s.substring(0, n) + "...";
    }
}