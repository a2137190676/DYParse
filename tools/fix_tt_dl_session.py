# -*- coding: utf-8 -*-
"""
修：TikTok 视频下载 403（能播不能下）。

【根因】
  MainActivity 第 20915 行：
      final Object ttSes = TtParser.isTtUrl(task.url)
              ? new TtParser.Session() : curSession;

  下载 TikTok 时**新建了一个空 Session**。
  而 TtParser.Session 的唯一作用就是装 cookie：
      public final java.net.CookieManager cm = new java.net.CookieManager();
      public String cookieHeader(){ ... cm.getCookieStore().get(...) ... }

  新建出来 CookieManager 就是空的 → cookieHeader() 返回 "" →
  TtParser.open() 里 `if(ck.length() > 0) setRequestProperty("Cookie", ck)`
  这一行被跳过 → 请求不带任何 Cookie → TikTok CDN 返回 403。

  播放器为什么能播？因为播放器走的是：
      final String ckFinal = ttUrl && curSession != null ? curSession.cookieHeader() : null;
  用的是 curSession —— 解析阶段 WebView 积攒下来的真 cookie。

  所以现象就是「能播不能下」：
     播放 = curSession（有 cookie）  → 200
     下载 = new Session()（空 cookie）→ 403

【改法】
  直接复用 curSession。curSession 为 null 时再兜底新建。
  这样下载和播放用的是同一份 cookie，请求形态一致。
"""
import io

p = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/MainActivity.java'
s = io.open(p, encoding='utf-8').read()

OLD = '''                        // TikTok 下载需要 Session（cookie + 代理分流）
                        final Object ttSes = TtParser.isTtUrl(task.url)
                                ? new TtParser.Session() : curSession;'''

NEW = '''                        // TikTok 下载必须复用解析阶段的 Session。
                        // 这里原来写的是 new TtParser.Session()，等于新建一个空 cookie 容器，
                        // 下载请求不带 Cookie → TikTok CDN 直接 403（表现就是「能播不能下」）。
                        // 播放器用的是 curSession，所以能播；下载也必须用同一份。
                        final Object ttSes = TtParser.isTtUrl(task.url)
                                ? (curSession != null ? curSession : new TtParser.Session())
                                : curSession;
                        if(TtParser.isTtUrl(task.url)){
                            int ckLen = -1;
                            try{ ckLen = ((TtParser.Session) ttSes).cookieHeader().length(); }catch(Throwable t){}
                            DypLog.i("TT_DL_SESSION reuse=" + (ttSes == curSession) + " ckLen=" + ckLen);
                        }'''

if OLD in s:
    s = s.replace(OLD, NEW, 1)
    io.open(p, 'w', encoding='utf-8').write(s)
    print('已改成复用 curSession')
else:
    print('WARN: 未命中，检查缩进')
    idx = s.find('TikTok 下载需要 Session')
    print('附近内容:', repr(s[idx-80:idx+200]) if idx > 0 else '没找到')