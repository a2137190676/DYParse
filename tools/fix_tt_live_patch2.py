# -*- coding: utf-8 -*-
"""
TikTok 实况动轨：本地优先 + 服务器补刀（B+A 落地）—— 修正版

【上一版的问题】
  把 fetchLiveFromServer 放在 TtParser 里，内部用 TtParser.open() 发请求。
  但自有服务器 /api/ 前缀有 HMAC 鉴权（SrvClient.sign），TtParser 不知道密钥，
  请求会被 hasAuth 拦掉 → 永远拿不到动轨。
  正确做法：用 MainActivity 的 openConn()，它内部走 SrvClient.open + SrvClient.sign。

【最终设计】
  在 MainActivity 里加 ttFetchLiveFromServer(pageUrl)：
    · 用 openConn() 发请求（自动带 HMAC）
    · 解析返回 JSON 的 liveImages 数组（App 已有 jarr/strArr 工具）
    · 任何异常/超时/非 200 都返回空列表，调用方按静态图集继续

  触发条件（只在必要时）：
    v.ok() && !v.images.isEmpty() && !v.isLive && 有服务器地址

  降级：
    有服务器 → 32 图 + 32 动轨（LIVE 角标、可存实况 jpg）
    无服务器/服务器挂了 → 32 图（正常图集，不报错）
"""
import io

BASE = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/'

p = BASE + 'MainActivity.java'
s = io.open(p, encoding='utf-8').read()

# ---------- 1) 加补刀方法（放在 strArr 之后，就近使用 jarr/strArr） ----------
ANCHOR = '''    /** 从 JSON 里取数组子串：{"key":[...]} -> [...] */'''
METHOD = '''    /**
     * 服务器补刀：图集作品的实况动轨（mp4 直链）。
     *
     * 为什么需要：
     *   TikTok 网页端（api-data）只下发静态图，imagePost.images[i] 里没有 video 子对象，
     *   整页 HTML 里一个 .mp4 都没有 —— 本地拿不到动轨。
     *   服务器走 tikwm，返回里的 live_images 是与 images 一一对应的 mp4 直链。
     *
     * 为什么放在 MainActivity 而不是 TtParser：
     *   自有服务器 /api/ 前缀有 HMAC 鉴权，必须用 openConn() 才会带签名。
     *
     * 失败一律返回空列表，调用方按「静态图集」继续，绝不阻塞主流程。
     */
    java.util.List<String> ttFetchLiveFromServer(String pageUrl){
        java.util.List<String> out = new java.util.ArrayList<String>();
        if(pageUrl == null || pageUrl.length() == 0) return out;
        String srv = effSrv();
        if(srv.length() == 0) return out;
        HttpURLConnection c = null;
        java.io.InputStream in = null;
        try{
            String api = srv + "/api/tt?url=" + java.net.URLEncoder.encode(pageUrl, "UTF-8");
            c = openConn(this, api);
            c.setConnectTimeout(12000);
            c.setReadTimeout(25000);
            int code = c.getResponseCode();
            if(code != 200){
                DypLog.i("TT_LIVE_SRV http=" + code);
                return out;
            }
            in = c.getInputStream();
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            int guard = 0;
            while((n = in.read(buf)) > 0){
                bo.write(buf, 0, n);
                if(++guard > 2048) break;   // 上限 16MB
            }
            String body = new String(bo.toByteArray(), "UTF-8");
            String arr = jarr(body, "liveImages");
            String[] items = strArr(arr);
            for(String u : items){
                if(u != null && u.length() > 0) out.add(u);
                else out.add("");
            }
            DypLog.i("TT_LIVE_SRV got=" + out.size() + " arrLen=" + arr.length());
        }catch(Throwable t){
            DypLog.i("TT_LIVE_SRV_ERR " + t);
        }finally{
            try{ if(in != null) in.close(); }catch(Throwable t){}
            try{ if(c != null) c.disconnect(); }catch(Throwable t){}
        }
        return out;
    }

'''

if ANCHOR in s and 'ttFetchLiveFromServer' not in s:
    s = s.replace(ANCHOR, METHOD + ANCHOR, 1)
    io.open(p, 'w', encoding='utf-8').write(s)
    print('MainActivity: ttFetchLiveFromServer 已加')
else:
    print('MainActivity: 方法跳过')

# ---------- 2) 解析流程里插入补刀 ----------
s = io.open(p, encoding='utf-8').read()
OLD = '''                if(v.ok()) curSession = sess;
                h.post(new Runnable(){ public void run(){
                    if(v.ok()){'''

NEW = '''                if(v.ok()) curSession = sess;
                // 图集但没动轨 → 向服务器补一次（实况动轨只有服务器那侧拿得到）
                if(v.ok() && !v.images.isEmpty() && !v.isLive){
                    try{
                        if(effSrv().length() > 0){
                            DypLog.i("TT_LIVE_PATCH try imgs=" + v.images.size());
                            java.util.List<String> live = ttFetchLiveFromServer(v.pageUrl);
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
                        }else{
                            DypLog.i("TT_LIVE_PATCH no-srv 跳过");
                        }
                    }catch(Throwable t){ DypLog.i("TT_LIVE_PATCH_ERR " + t); }
                }
                h.post(new Runnable(){ public void run(){
                    if(v.ok()){'''

if OLD in s and 'TT_LIVE_PATCH try' not in s:
    s = s.replace(OLD, NEW, 1)
    io.open(p, 'w', encoding='utf-8').write(s)
    print('MainActivity: 补刀流程已插入')
else:
    print('MainActivity: 补刀流程跳过（已存在或锚点未命中）')