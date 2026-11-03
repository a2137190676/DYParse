package com.re.douyinparse;

import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * 腾讯视频解析（v.qq.com / m.v.qq.com）。
 *
 * 走 vv.video.qq.com/getinfo 接口：
 *   1) getinfo 拿 vl.vi[0]（含 fn、fvkey、ul.ui[0].url、td）
 *   2) 直链 = ul.ui[0].url + fn + "?vkey=" + fvkey
 *   3) fl.fi 里带全部清晰度档位（sd/hd/shd/fhd/2k/4k）
 *
 * 免登录。付费/会员内容只能拿到试看片段（em=0 但 td 很短），属正常限制。
 */
public class TxParser {
    static final String TAG = "DYP";

    /** 解析结果 */
    public static class TxVideo {
        public boolean ok = false;
        public String err = "";
        public String vid = "";
        public String title = "";
        public String cover = "";
        public String desc = "";
        public double durSec = 0;
        public long fileSize = 0;
        /** 最佳直链 */
        public String playUrl = "";
        /** 多清晰度：[label, url, 字节数] */
        public java.util.List<String[]> gears = new java.util.ArrayList<String[]>();
    }

    /** 是不是腾讯视频链接 */
    public static boolean isTxUrl(String u){
        if(u == null) return false;
        String s = u.toLowerCase();
        return s.contains("v.qq.com/") || s.contains("m.v.qq.com/")
                || s.contains("vv.video.qq.com") || s.contains("qq.com/x/page/")
                || s.contains("qq.com/x/cover/");
    }

    /** 从各种腾讯视频链接里抠出 vid（20 位左右的字母数字） */
    public static String extractVid(String url){
        if(url == null) return "";
        try{
            // 直接带 vid= 参数
            int i = url.indexOf("vid=");
            if(i >= 0){
                String rest = url.substring(i + 4);
                StringBuilder sb = new StringBuilder();
                for(int k = 0; k < rest.length(); k++){
                    char c = rest.charAt(k);
                    if((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')) sb.append(c);
                    else break;
                }
                if(sb.length() >= 8) return sb.toString();
            }
            // /x/page/xxxxx.html 或 /x/cover/xxx/xxxx.html 路径最后一段
            int p = url.lastIndexOf('/');
            if(p >= 0){
                String last = url.substring(p + 1);
                int dot = last.indexOf('.');
                if(dot > 8){
                    String cand = last.substring(0, dot);
                    if(cand.matches("[A-Za-z0-9]{8,24}")) return cand;
                }
            }
        }catch(Throwable t){
            Log.i(TAG, "TX_VID_ERR " + t);
        }
        return "";
    }

    /** 主解析入口 */
    public static TxVideo parse(String url){
        TxVideo r = new TxVideo();
        try{
            String vid = extractVid(url);
            if(vid.length() < 8){
                r.err = "没从链接里找到视频 ID";
                return r;
            }
            r.vid = vid;
            Log.i(TAG, "TX_VID " + vid);

            // 1) 获取播放信息（从高到低试，拿第一个成功的）
            String[] defns = { "fhd", "shd", "hd", "sd" };
            JSONObject best = null;
            String bestDefn = "";
            for(String defn : defns){
                String api = "https://vv.video.qq.com/getinfo?vids=" + vid
                        + "&platform=101001&charge=0&otype=json&defn=" + defn
                        + "&sdtfrom=v1010";
                String body = httpGet(api, "https://v.qq.com/");
                if(body == null || body.length() < 50){
                    Log.i(TAG, "TX_GETINFO_EMPTY defn=" + defn);
                    continue;
                }
                int b = body.indexOf('{');
                if(b < 0) continue;
                String js = body.substring(b);
                // 腾讯返回的是 QZOutputJson={...}; 后面可能还有分号
                int e = js.lastIndexOf('}');
                if(e > 0) js = js.substring(0, e + 1);
                JSONObject o;
                try{ o = new JSONObject(js); }catch(Throwable t){
                    Log.i(TAG, "TX_JSON_ERR defn=" + defn + " " + t);
                    continue;
                }
                int em = o.optInt("em", -1);
                JSONObject vl = o.optJSONObject("vl");
                JSONArray vi = vl == null ? null : vl.optJSONArray("vi");
                if(em != 0 || vi == null || vi.length() == 0){
                    Log.i(TAG, "TX_NO_VI defn=" + defn + " em=" + em);
                    continue;
                }
                best = o;
                bestDefn = defn;
                Log.i(TAG, "TX_GETINFO_OK defn=" + defn);
                break;
            }
            if(best == null){
                r.err = "腾讯视频接口没返回可播放地址（可能是付费/会员专享）";
                return r;
            }

            // 2) 解析 vi 里的直链
            JSONObject vl = best.getJSONObject("vl");
            JSONArray vi = vl.getJSONArray("vi");
            for(int i = 0; i < vi.length(); i++){
                JSONObject v = vi.optJSONObject(i);
                if(v == null) continue;
                String fn = v.optString("fn", "");
                String fvkey = v.optString("fvkey", "");
                JSONObject ul = v.optJSONObject("ul");
                JSONArray ui = ul == null ? null : ul.optJSONArray("ui");
                if(ui == null || ui.length() == 0) continue;
                String base = ui.getJSONObject(0).optString("url", "");
                if(base.length() == 0) continue;
                String direct = base + fn + (fvkey.length() > 0 ? ("?vkey=" + fvkey) : "");
                if(r.playUrl.length() == 0){
                    r.playUrl = direct;
                    r.fileSize = v.optLong("fs", 0);
                    r.durSec = v.optDouble("td", 0);
                    Log.i(TAG, "TX_URL_OK size=" + r.fileSize + " td=" + r.durSec);
                }
                r.gears.add(new String[]{ bestDefn, direct, String.valueOf(v.optLong("fs", 0)) });
            }

            // 3) 标题 / 封面：从 fl.fi 或页面里取不到时用 vid 兜底
            try{
                JSONObject fl = best.optJSONObject("fl");
                JSONArray fi = fl == null ? null : fl.optJSONArray("fi");
                if(fi != null){
                    for(int i = 0; i < fi.length(); i++){
                        JSONObject f = fi.optJSONObject(i);
                        if(f == null) continue;
                        r.gears.add(new String[]{
                            f.optString("cname", f.optString("name", "?")), "", String.valueOf(f.optLong("fs", 0))
                        });
                    }
                }
            }catch(Throwable t){}

            if(r.playUrl.length() == 0){
                r.err = "拿到信息但没有直链";
                return r;
            }
            if(r.title.length() == 0) r.title = "腾讯视频 " + vid;
            r.ok = true;
            Log.i(TAG, "TX_OK gears=" + r.gears.size() + " url="
                    + r.playUrl.substring(0, Math.min(80, r.playUrl.length())));
        }catch(Throwable t){
            Log.i(TAG, "TX_ERR " + t);
            r.err = String.valueOf(t);
        }
        return r;
    }

    /** 简单 HTTP GET（纯文本） */
    static String httpGet(String url, String referer){
        java.net.HttpURLConnection c = null;
        try{
            c = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
            c.setRequestMethod("GET");
            c.setConnectTimeout(12000);
            c.setReadTimeout(15000);
            c.setInstanceFollowRedirects(true);
            c.setRequestProperty("User-Agent",
                "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Mobile Safari/537.36");
            if(referer != null) c.setRequestProperty("Referer", referer);
            int code = c.getResponseCode();
            java.io.InputStream in = (code >= 200 && code < 300)
                    ? c.getInputStream() : c.getErrorStream();
            if(in == null) return "";
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            in.close();
            return new String(bo.toByteArray(), "UTF-8");
        }catch(Throwable t){
            Log.i(TAG, "TX_HTTP_ERR " + t);
            return "";
        }finally{
            try{ if(c != null) c.disconnect(); }catch(Throwable t){}
        }
    }
}