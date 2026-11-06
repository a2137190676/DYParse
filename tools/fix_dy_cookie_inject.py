# -*- coding: utf-8 -*-
"""
【核心修复】解析前把本地登录 Cookie 注入 WebView。

问题：
    loadForParse() 从头到尾没有注入 Cookie。
    Cookie 只在 App 启动时由 restoreDyCookieToWebView() 灌一次，
    而且那个函数有个提前 return：

        String cm = CookieManager.getCookie("https://www.douyin.com/");
        boolean liveOk = cm != null && (cm.indexOf("sessionid=") >= 0 ...);
        if(liveOk) { return; }        // ← WebView 里「看着有」就不灌

    但 WebView 里的 Cookie 可能只有 ttwid/UIFID 这种半截，
    sessionid 是本地 CookieStore 里的（用户手动登录/粘贴的），
    两者不同步 → 解析走匿名会话 → 风控当然一直触发。
    用户明明「已经登录过了」，App 却按匿名在请求。

修法：
    loadForParse 入口，对抖音类链接先把 CookieStore 里的登录态
    逐条 setCookie 到 douyin.com / iesdouyin.com，再 loadUrl。
    （每次注入成本很低，只遍历几十条 key=value，不会卡顿。）
"""
import io

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/MainActivity.java'
s = io.open(P, encoding='utf-8').read()
ok, miss = [], []


def rep(name, old, new):
    global s
    if old in s:
        s = s.replace(old, new, 1)
        ok.append(name)
    else:
        miss.append(name)


# ---------- 1) 新增：解析前注入抖音 Cookie ----------
rep('1-注入方法',
    '''    String dyEffectiveCookie(){''',
    '''    /**
     * 解析抖音链接前，把本地保存的登录 Cookie 灌进 WebView。
     *
     * 为什么必须每次都灌：
     *   用户「在设置里登录过」→ Cookie 存在 CookieStore（本地文件）。
     *   但 WebView 的 CookieManager 是另一个存储，两者不同步。
     *   restoreDyCookieToWebView() 只在启动跑一次，而且发现 WebView
     *   里有 sessionid 就直接 return —— 实际那个 sessionid 可能是
     *   很久以前的半截，或者压根只有 ttwid。
     *   结果：解析用的是匿名会话 → 天天风控。
     *
     * 这里在每次解析前无条件补齐，成本只是遍历几十个 key=value。
     */
    void injectDyCookieBeforeParse(){
        try{
            String local = CookieStore.header(this, CookieStore.PLATFORM_DY);
            if(local == null || local.trim().length() < 32){
                Log.i("DYP", "DY_CK_INJECT skip 本地无登录态");
                return;
            }
            boolean hasLogin = local.indexOf("sessionid=") >= 0
                    || local.indexOf("sid_tt=") >= 0
                    || local.indexOf("sid_guard=") >= 0;
            android.webkit.CookieManager cm = android.webkit.CookieManager.getInstance();
            cm.setAcceptCookie(true);
            int n = 0;
            for(String kv : local.split("[;\\n\\r]+")){
                String t = kv.trim();
                if(t.length() < 3 || t.indexOf('=') <= 0) continue;
                // 两个域都灌：抖音的分享页在 iesdouyin，主站在 douyin
                cm.setCookie("https://www.douyin.com/", t + "; path=/");
                cm.setCookie("https://www.iesdouyin.com/", t + "; path=/");
                n++;
            }
            try{ cm.flush(); }catch(Throwable t){}
            Log.i("DYP", "DY_CK_INJECT n=" + n + " hasLogin=" + hasLogin
                    + " hasSid=" + (local.indexOf("sessionid=") >= 0));
        }catch(Throwable t){
            Log.i("DYP", "DY_CK_INJECT_ERR " + t);
        }
    }

    String dyEffectiveCookie(){''')

# ---------- 2) loadForParse 里调用 ----------
rep('2-loadForParse调用',
    '''        curUrl = url;
        // 新解析开始：复位各平台「已回退」标志，否则第二次解析不会触发回退''',
    '''        curUrl = url;
        // 抖音类链接：解析前先把登录 Cookie 灌进 WebView。
        // 不灌的话解析走匿名会话，风控会一直找上门（用户明明登录过了）。
        try{
            String lu = url == null ? "" : url.toLowerCase();
            if(lu.indexOf("douyin.com") >= 0 || lu.indexOf("iesdouyin.com") >= 0
                    || lu.indexOf("amemv.com") >= 0){
                injectDyCookieBeforeParse();
            }
        }catch(Throwable t){
            Log.i("DYP", "DY_CK_INJECT_WRAP_ERR " + t);
        }
        // 新解析开始：复位各平台「已回退」标志，否则第二次解析不会触发回退''')

# ---------- 3) restoreDyCookieToWebView 去掉「看着有就不灌」的早退 ----------
rep('3-去掉早退',
    '''            String cm = CookieManager.getInstance().getCookie("https://www.douyin.com/");
            boolean liveOk = cm != null && (cm.indexOf("sessionid=") >= 0 || cm.indexOf("sid_tt=") >= 0);
            if(liveOk) {
                Log.i("DYP", "DY_CK_RESTORE skip webview_already_ok");
                return;
            }''',
    '''            String cm = CookieManager.getInstance().getCookie("https://www.douyin.com/");
            // 注意：这里不要因为「WebView 里看着有 sessionid」就跳过。
            // WebView 的 Cookie 可能是很早以前的半截，而本地文件才是刚登录的真凭据。
            // 以本地为准，直接覆盖灌入（同名 key 后写覆盖前写）。
            Log.i("DYP", "DY_CK_RESTORE force 以本地为准 len(local)="
                    + local.length() + " len(web)=" + (cm == null ? 0 : cm.length()));''')

io.open(P, 'w', encoding='utf-8').write(s)
print('成功:', ok)
print('失败:', miss)