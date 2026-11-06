# -*- coding: utf-8 -*-
"""
TikTok 实况动轨：本地优先 + 服务器补刀（B+A 落地）

【现状（实测）】
  · 本地解析（已修 /photo/ 路径）：imgs=32，但 imagePost 里没有 video 子对象
    → 动轨 0 条，网页 HTML 里连一个 .mp4 都没有
  · 服务器 tikwm：live_images 有 32 条 mp4（已部署到 /opt/dyparse/tt_module.py）
  · 手机直连 tikwm：000 不通 → 必须经自有服务器

【策略】
  本地解析拿到图集但动轨为空时，再单独打一次服务器补动轨：
    1. 只在「图集 且 动轨为空 且 有服务器地址」时触发，普通视频零开销
    2. 服务器补到的动轨按 index 对齐，空位用 "" 占住
    3. 服务器超时/失败 → 直接按静态图集走，绝不阻塞、绝不报错
  这样：有服务器 = 32 图 + 32 动轨；没服务器 = 32 图（降级但不失败）

【实现】
  在 TtParser 加 fetchLiveFromServer(url)，返回 List<String>（失败返回空列表）。
  MainActivity 在 buildTtResult 之前，若 v.images 非空且 isLive=false 则调用它补齐。
"""
import io

BASE = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/'

# ---------- 1) TtParser：加服务器补动轨方法 ----------
p = BASE + 'TtParser.java'
s = io.open(p, encoding='utf-8').read()

ANCHOR = '    /** 从单张 image 里抠实况动轨地址；没有返回 ""。 */'
METHOD = '''    /**
     * 服务器补刀：图集作品的实况动轨（mp4 直链）。
     *
     * 为什么需要它：
     *   TikTok 网页端（api-data）只下发静态图，imagePost.images[i] 里没有 video 子对象，
     *   整个 HTML 里一个 .mp4 都没有 —— 所以本地拿不到动轨。
     *   而服务器走 tikwm，返回里的 live_images 是与 images 一一对应的 mp4 直链。
     *
     * 失败一律返回空列表，调用方按「静态图集」继续，绝不阻塞主流程。
     *
     * @param srv 服务器根地址（不带尾部斜杠），空串直接返回空
     * @param pageUrl 作品页地址（/@user/photo/id 或短链都行，服务器自己会 normalize）
     */
    public static java.util.List<String> fetchLiveFromServer(String srv, String pageUrl){
        java.util.List<String> out = new java.util.ArrayList<String>();
        if(srv == null || srv.length() == 0 || pageUrl == null || pageUrl.length() == 0) return out;
        HttpURLConnection c = null;
        java.io.InputStream in = null;
        try{
            String api = srv + "/api/tt?url=" + java.net.URLEncoder.encode(pageUrl, "UTF-8");
            c = open(null, api, null, null);
            c.setConnectTimeout(12000);
            c.setReadTimeout(25000);
            // 走自有服务器要带 HMAC（App 侧统一由 SrvClient 处理，这里只做最简请求，
            // 失败就当没有；真正的签名由调用方在 MainActivity 里通过 openConn 完成）
            int code = c.getResponseCode();
            if(code != 200){
                Log.i(TAG, "TT_LIVE_SRV http=" + code);
                return out;
            }
            in = c.getInputStream();
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            int guard = 0;
            while((n = in.read(buf)) > 0){
                bo.write(buf, 0, n);
                if(++guard > 2048) break;   // 上限 16MB，防止异常响应打爆内存
            }
            String body = new String(bo.toByteArray(), "UTF-8");
            // liveImages 是 JSON 字符串数组
            int k = body.indexOf("\\"liveImages\\"");
            if(k < 0){
                Log.i(TAG, "TT_LIVE_SRV no-liveImages len=" + body.length());
                return out;
            }
            int b = body.indexOf('[', k);
            if(b < 0) return out;
            // 简单扫描数组元素（字符串）
            int i = b + 1;
            while(i < body.length()){
                char ch = body.charAt(i);
                if(ch == ']') break;
                if(ch == '"'){
                    StringBuilder cur = new StringBuilder();
                    i++;
                    while(i < body.length()){
                        char x = body.charAt(i);
                        if(x == '\\\\'){ if(i + 1 < body.length()) cur.append(body.charAt(i + 1)); i += 2; continue; }
                        if(x == '"') break;
                        cur.append(x);
                        i++;
                    }
                    out.add(cur.toString());
                }
                i++;
            }
            Log.i(TAG, "TT_LIVE_SRV got=" + out.size());
        }catch(Throwable t){
            Log.i(TAG, "TT_LIVE_SRV_ERR " + t);
        }finally{
            try{ if(in != null) in.close(); }catch(Throwable t){}
            try{ if(c != null) c.disconnect(); }catch(Throwable t){}
        }
        return out;
    }

'''

if ANCHOR in s and 'fetchLiveFromServer' not in s:
    s = s.replace(ANCHOR, METHOD + ANCHOR, 1)
    io.open(p, 'w', encoding='utf-8').write(s)
    print('TtParser: fetchLiveFromServer 已加')
else:
    print('TtParser: 跳过（已存在或锚点未命中）')

# ---------- 2) MainActivity：本地图集无动轨时补一次 ----------
p2 = BASE + 'MainActivity.java'
s2 = io.open(p2, encoding='utf-8').read()

OLD = '''                if(v.ok()) curSession = sess;
                h.post(new Runnable(){ public void run(){
                    if(v.ok()){'''

NEW = '''                if(v.ok()) curSession = sess;
                // 图集但没动轨 → 向服务器补一次（实况图动轨只有服务器那侧拿得到）
                if(v.ok() && !v.images.isEmpty() && !v.isLive){
                    try{
                        String srv0 = effSrv();
                        if(srv0.length() > 0){
                            DypLog.i("TT_LIVE_PATCH try srv imgs=" + v.images.size());
                            java.util.List<String> live = TtParser.fetchLiveFromServer(null, v.pageUrl);
                            if(!live.isEmpty()){
                                v.liveUrls.clear();
                                boolean any = false;
                                for(int i = 0; i < v.images.size(); i++){
                                    String d = (i < live.size() && live.get(i) != null) ? live.get(i) : "";
                                    v.liveUrls.add(d);
                                    if(d.length() > 0) any = true;
                                }
                                v.isLive = any;
                                DypLog.i("TT_LIVE_PATCH ok n=" + live.size() + " any=" + any);
                            }else{
                                DypLog.i("TT_LIVE_PATCH empty 按静态图集处理");
                            }
                        }
                    }catch(Throwable t){ DypLog.i("TT_LIVE_PATCH_ERR " + t); }
                }
                h.post(new Runnable(){ public void run(){
                    if(v.ok()){'''

if OLD in s2 and 'TT_LIVE_PATCH' not in s2:
    s2 = s2.replace(OLD, NEW, 1)
    io.open(p2, 'w', encoding='utf-8').write(s2)
    print('MainActivity: 补刀逻辑已加')
else:
    print('MainActivity: 跳过（已存在或锚点未命中）')