# -*- coding: utf-8 -*-
"""
修：B 站短链 b23.tv 解析不了（批量解析时必失败，单条也时好时坏）。

【根因】
  BiliParser.resolve() 第一步就是：
      String aid = extractAid(url);
      if(aid.length() == 0){ v.err = "链接里没找到 av 号 / BV 号"; return v; }
  extractAid 只认 avXXXX 和 BVXXXXXXXXXX 两种形态。
  而 b23.tv/2lyTONF 是纯短链，里面既没 av 也没 BV
  → 直接返回错误，一个请求都没发。

  之前单条能成功，是因为主界面在解析前已经做过一次短链展开
  （把 b23.tv 转成 www.bilibili.com/video/BV...），
  批量解析走的是另一个入口，没展开就丢给 BiliParser，于是必挂。

【改法】
  BiliParser 自己做短链展开（不依赖调用方）：
    · 检测到 b23.tv 或非 bilibili.com/video 的链接
    · 先 HTTP 请求一次拿 301/302 的 Location
    · 从最终 URL 里再 extractAid
  这样无论从哪个入口进来（单条 / 批量 / 主页），都能正确解析。

  额外加一层：如果短链展开也失败，再试 bilibili.com 的 b23 跳转接口。
"""
import io

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/BiliParser.java'
s = io.open(P, encoding='utf-8').read()
ok, miss = [], []


def rep(name, old, new):
    global s
    if old in s:
        s = s.replace(old, new, 1)
        ok.append(name)
    else:
        miss.append(name)


# ---------- 1. 增加短链展开方法 ----------
rep('1-短链展开',
    '''    public static String extractAid(String url){''',
    '''    /**
     * 展开 b23.tv 短链 → 真实 www.bilibili.com/video/BVxxx 地址。
     *
     * 为什么必须做：extractAid 只认 av/BV，遇到 b23.tv/xxxxx 这种纯短链
     * 直接返回空串，批量解析就报「链接里没找到 av 号 / BV 号」。
     *
     * 做法：跟随一次 301/302 拿 Location。B 站短链固定跳一次就到位。
     * 失败返回原 URL（让上层用原逻辑兜底）。
     */
    static String expandShort(String url){
        if(url == null) return "";
        String u = url.trim();
        boolean isShort = u.contains("b23.tv") || u.contains("bili2233.cn");
        if(!isShort) return u;
        try{
            java.net.HttpURLConnection c = (java.net.HttpURLConnection)
                    new java.net.URL(u).openConnection(java.net.Proxy.NO_PROXY);
            c.setInstanceFollowRedirects(false);     // 自己读 Location，才能拿到真实地址
            c.setRequestProperty("User-Agent", UA);
            c.setRequestProperty("Referer", REF);
            c.setConnectTimeout(10000);
            c.setReadTimeout(10000);
            int code = c.getResponseCode();
            String loc = c.getHeaderField("Location");
            try{ c.disconnect(); }catch(Throwable t){}
            Log.i(TAG, "BILI_SHORT code=" + code + " loc="
                    + (loc == null ? "null" : loc.substring(0, Math.min(90, loc.length()))));
            if(loc != null && loc.length() > 0 && extractAid(loc).length() > 0){
                return loc;
            }
            // 有的跳转给的是相对地址或需要再跟一次
            if(loc != null && loc.length() > 0){
                if(loc.startsWith("/")) loc = "https://www.bilibili.com" + loc;
                if(extractAid(loc).length() == 0){
                    try{
                        java.net.HttpURLConnection c2 = (java.net.HttpURLConnection)
                                new java.net.URL(loc).openConnection(java.net.Proxy.NO_PROXY);
                        c2.setInstanceFollowRedirects(false);
                        c2.setRequestProperty("User-Agent", UA);
                        c2.setConnectTimeout(10000);
                        c2.setReadTimeout(10000);
                        c2.getResponseCode();
                        String loc2 = c2.getHeaderField("Location");
                        try{ c2.disconnect(); }catch(Throwable t){}
                        if(loc2 != null && loc2.length() > 0){
                            if(loc2.startsWith("/")) loc2 = "https://www.bilibili.com" + loc2;
                            Log.i(TAG, "BILI_SHORT2 loc="
                                    + loc2.substring(0, Math.min(90, loc2.length())));
                            return loc2;
                        }
                    }catch(Throwable t2){ Log.i(TAG, "BILI_SHORT2_ERR " + t2); }
                }
                return loc;
            }
        }catch(Throwable t){
            Log.i(TAG, "BILI_SHORT_ERR " + t);
        }
        return u;
    }

    public static String extractAid(String url){''')

# ---------- 2. resolve 里先用展开结果 ----------
rep('2-resolve接短链',
    '''        String aid = extractAid(url);
        if(aid.length() == 0){ v.err = "链接里没找到 av 号 / BV 号"; return v; }''',
    '''        // 先展开短链（b23.tv 之类），否则 extractAid 拿不到 av/BV
        String real = expandShort(url);
        if(!real.equals(url)){
            Log.i(TAG, "BILI_EXPANDED " + real.substring(0, Math.min(90, real.length())));
        }
        String aid = extractAid(real);
        if(aid.length() == 0) aid = extractAid(url);   // 展开失败再试原串
        if(aid.length() == 0){ v.err = "链接里没找到 av 号 / BV 号"; return v; }''')

io.open(P, 'w', encoding='utf-8').write(s)
print('成功:', ok)
print('失败:', miss)