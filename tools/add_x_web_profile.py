# -*- coding: utf-8 -*-
"""
给 ProfileParser 加一条 X 主页的新数据源：x.com 网页直出。

背景：
  syndication.twitter.com 已被 429 限流（实测返回 2218 字节空壳），
  nitter 已停服（返回的是律师函公告页），fxtwitter 只有单推文没有时间线。
  实测 x.com/<name> 网页直连能拿到 132KB HTML，里面服务端渲染了最近推文，
  含 full_text / mp4 多分辨率直链 / 缩略图 / id_str，游客态也出数据。

做法：
  resolveXProfile 里 syndication 失败后不再直接放弃，先试 x.com 网页，
  两条都失败才回退 WebView。
"""
import io

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/ProfileParser.java'
d = io.open(P, encoding='utf-8').read()

anchor = '''            Log.i(TAG, "X_PROF_ITEMS n=" + out.size());
            if(out.isEmpty()){
                lastErr = "该账号主页需登录（敏感内容），请走网页登录";
                return null;
            }
            return out;'''

if anchor not in d:
    print('MISS anchor')
    raise SystemExit(1)

# 改为：syndication 为空时，先试 x.com 网页，再放弃
new_tail = '''            Log.i(TAG, "X_PROF_ITEMS n=" + out.size());
            if(out.isEmpty()){
                // syndication 被 X 限流（实测 429，只回 2KB 空壳），
                // 改试 x.com 网页：服务端渲染了最近推文，游客态也出数据。
                Log.i(TAG, "X_PROF_SYN_EMPTY 改试 x.com 网页");
                List<ProfileItem> webList = resolveXProfileWeb(ctx, screenName);
                if(webList != null && !webList.isEmpty()) return webList;
                lastErr = "该账号主页需登录（敏感内容），请走网页登录";
                return null;
            }
            return out;'''

d = d.replace(anchor, new_tail, 1)

# 在 resolveProfile 之前插入新方法
marker = '''    public static List<ProfileItem> resolveProfile(Context ctx, String url){'''

new_method = r'''    /**
     * X 主页：x.com 网页直出解析（syndication 被限流时的主力通道）。
     *
     * 实测结构（2026-11）：
     *   - HTML 约 130KB，服务端渲染了最近 4~20 条推文
     *   - 数据在 RSC 数据流里（多层转义），关键字段：
     *       "full_text":"..."                   推文正文
     *       "id_str":"<tweetId>"                推文 ID
     *       "media_url_https":"..."             视频封面 / 图片
     *       "content_type":"video/mp4" + "url"  多分辨率直链
     *   - 先把 \\\\" 反转义成 " 再做正则，否则匹配不到
     */
    public static List<ProfileItem> resolveXProfileWeb(Context ctx, String screenName){
        List<ProfileItem> out = new ArrayList<ProfileItem>();
        HttpURLConnection c = null;
        try{
            String sname = screenName == null ? "" : screenName.trim();
            if(sname.startsWith("@")) sname = sname.substring(1);
            if(sname.length() == 0) return out;
            String page = "https://x.com/" + URLEncoder.encode(sname, "UTF-8");
            c = MainActivity.openConn(ctx, page);
            c.setInstanceFollowRedirects(true);
            c.setConnectTimeout(15000);
            c.setReadTimeout(25000);
            c.setRequestProperty("User-Agent", XUA);
            c.setRequestProperty("Accept", "text/html,application/xhtml+xml,*/*");
            c.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
            try{
                String xck = CookieStore.header(ctx, CookieStore.PLATFORM_X);
                if(xck != null && xck.length() > 10){
                    c.setRequestProperty("Cookie", xck);
                    Log.i(TAG, "X_PROF_WEB_CK len=" + xck.length());
                }
            }catch(Throwable t){}
            int code = c.getResponseCode();
            if(code != 200){
                Log.i(TAG, "X_PROF_WEB http=" + code);
                return out;
            }
            InputStream in = c.getInputStream();
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            int guard = 0;
            while((n = in.read(buf)) > 0){
                bo.write(buf, 0, n);
                if(++guard > 4096) break;   // 上限 32MB
            }
            in.close();
            String html = bo.toString("UTF-8");
            // RSC 里是两层转义，反转义后才能用普通正则
            String u = html.replace("\\\\\"", "\"").replace("\\\"", "\"");
            Log.i(TAG, "X_PROF_WEB len=" + html.length() + " unesc=" + u.length());

            // 推文 ID（按出现顺序）
            List<String> ids = new ArrayList<String>();
            Matcher mi = Pattern.compile("\"id_str\"\\s*:\\s*\"(\\d{15,25})\"").matcher(u);
            java.util.Set<String> seen = new java.util.HashSet<String>();
            while(mi.find()){
                String id = mi.group(1);
                if(seen.add(id)) ids.add(id);
            }
            // 推文正文
            List<String> texts = new ArrayList<String>();
            Matcher mt = Pattern.compile("\"full_text\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(u);
            while(mt.find()) texts.add(unescX(mt.group(1)));

            // 视频直链：按 mediaId 归组，挑分辨率最高的一条
            java.util.Map<String, String> bestVideo = new java.util.LinkedHashMap<String, String>();
            java.util.Map<String, Integer> bestScore = new java.util.HashMap<String, Integer>();
            Matcher mv = Pattern.compile(
                    "https://video\\.twimg\\.com/[^\"\\s]+?/(\\d{15,25})/pu/vid/(?:avc1|hevc)/(\\d+)x(\\d+)/[^\"\\s]+?\\.mp4[^\"\\s]*")
                    .matcher(u);
            while(mv.find()){
                String mid = mv.group(1);
                int w = parseIntSafe(mv.group(2));
                int sc = w;
                Integer old = bestScore.get(mid);
                if(old == null || sc > old.intValue()){
                    bestScore.put(mid, Integer.valueOf(sc));
                    bestVideo.put(mid, mv.group(0));
                }
            }
            // 缩略图：ext_tw_video_thumb/<mediaId>/... 与图片 /media/<id>
            java.util.Map<String, String> thumbs = new java.util.LinkedHashMap<String, String>();
            Matcher mth = Pattern.compile(
                    "https://pbs\\.twimg\\.com/ext_tw_video_thumb/(\\d{15,25})/pu/img/[^\"\\s]+")
                    .matcher(u);
            while(mth.find()){
                if(!thumbs.containsKey(mth.group(1))) thumbs.put(mth.group(1), mth.group(0));
            }
            Log.i(TAG, "X_PROF_WEB ids=" + ids.size() + " text=" + texts.size()
                    + " video=" + bestVideo.size() + " thumb=" + thumbs.size());

            int cnt = Math.min(ids.size(), Math.max(texts.size(), bestVideo.size()));
            for(int i = 0; i < ids.size(); i++){
                String id = ids.get(i);
                ProfileItem it = new ProfileItem();
                it.id = id;
                it.title = i < texts.size() ? texts.get(i) : "";
                it.url = "https://x.com/" + sname + "/status/" + id;
                // 直链优先用视频（列表里可直接下载），没有视频就用缩略图
                String v = bestVideo.get(id);
                if(v == null || v.length() == 0){
                    v = thumbs.get(id);
                }
                if(v != null && v.length() > 0) it.directUrl = v;
                it.cover = thumbs.containsKey(id) ? thumbs.get(id) : "";
                it.kind = (bestVideo.get(id) != null) ? KIND_VIDEO : KIND_IMAGE;
                out.add(it);
            }
            Log.i(TAG, "X_PROF_WEB_ITEMS n=" + out.size());
            return out;
        }catch(Throwable t){
            Log.i(TAG, "X_PROF_WEB_ERR " + t);
            return out;
        }finally{
            try{ if(c != null) c.disconnect(); }catch(Throwable t){}
        }
    }

    static int parseIntSafe(String s){
        try{ return Integer.parseInt(s.trim()); }catch(Throwable t){ return 0; }
    }

    /** 反转义 JSON 字符串里的 \\n \\" \\uXXXX */
    static String unescX(String s){
        if(s == null) return "";
        StringBuilder sb = new StringBuilder(s.length());
        for(int i = 0; i < s.length(); i++){
            char ch = s.charAt(i);
            if(ch == '\\' && i + 1 < s.length()){
                char nx = s.charAt(i + 1);
                if(nx == 'n'){ sb.append(' '); i++; continue; }
                if(nx == 'r'){ i++; continue; }
                if(nx == 't'){ sb.append(' '); i++; continue; }
                if(nx == '"'){ sb.append('"'); i++; continue; }
                if(nx == '\\'){ sb.append('\\'); i++; continue; }
                if(nx == '/'){ sb.append('/'); i++; continue; }
                if(nx == 'u' && i + 5 < s.length()){
                    try{
                        sb.append((char) Integer.parseInt(s.substring(i + 2, i + 6), 16));
                        i += 5;
                        continue;
                    }catch(Throwable t){}
                }
            }
            sb.append(ch);
        }
        return sb.toString();
    }

'''

if marker not in d:
    print('MISS marker')
    raise SystemExit(1)

d = d.replace(marker, new_method + marker, 1)

# 加桌面 UA 常量
d = d.replace(
    '''    static final String UA = "Mozilla/5.0 (Linux; Android 12; Pixel 5) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36";''',
    '''    static final String UA = "Mozilla/5.0 (Linux; Android 12; Pixel 5) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36";
    /** X 网页必须桌面 UA：移动 UA 拿到的是重定向引导页，没有服务端渲染的推文 */
    static final String XUA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36";''', 1)

io.open(P, 'w', encoding='utf-8').write(d)
print('OK 新增 resolveXProfileWeb + XUA, len=', len(d))