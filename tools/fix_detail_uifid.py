# -*- coding: utf-8 -*-
"""
批量下载 detail 403 的真因修复：uifid 没传进去。

【证据】
  App 内确实拿到了 UIFID：
      CM_COOKIE_NAMES ...UIFID_TEMP...UIFID...
      UIFID_FROM_CM len=352 head=f670781d...
  但批量下载的 detail 仍被拒：
      BATCH_DETAIL_REQ ok=false http=403
      body=Blocked by ArgusSecurityPlugin Uifid Not Found

【原因】
  DyDetail.fetch(awemeId, cookie, 2) 里 uifid 只从**传入的那个 cookie 字符串**里抠：
      buildSignedQuery → cookieValue(cookie,"UIFID_TEMP"/"UIFID"/"uifid")
  批量下载传的是 CookieStore.header(本地文件)，那份里没有 UIFID_TEMP，
  于是 uifid="" → 请求不带 uifid → Argus 直接 403。

  而 App 明明有 dyCurUifid()（WebView 实时 Cookie → 本地 → 访客凭据，三级兜底）。

【改法】
  DyDetail.fetch 增加一个显式 uifid 参数：
      传入非空 → 直接用（并且写进 query + secsdk 签名）
      传入为空 → 保持原行为，从 cookie 里抠
  批量下载调用处改为传 dyCurUifid()。
"""
import io

D = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/DyDetail.java'
s = io.open(D, encoding='utf-8').read()
ok, miss = [], []


def rep(name, old, new):
    global s
    if old in s:
        s = s.replace(old, new, 1)
        ok.append(name)
    else:
        miss.append(name)


# 1) buildSignedQuery 增加显式 uifid 重载
rep('1-签名重载',
    '''    public static String buildSignedQuery(String awemeId, String cookie){
        String uifid = cookieValue(cookie, "UIFID_TEMP");
        if(uifid.length() == 0) uifid = cookieValue(cookie, "UIFID");
        if(uifid.length() == 0) uifid = cookieValue(cookie, "uifid");''',
    '''    public static String buildSignedQuery(String awemeId, String cookie){
        return buildSignedQuery(awemeId, cookie, "");
    }

    /**
     * @param uifidOverride 调用方已知的 uifid（如 App 的 dyCurUifid() 结果）。
     *        非空时优先用它 —— 因为 CookieStore 本地文件里可能没有 UIFID_TEMP，
     *        只从 cookie 抠会得到空串，请求就被 Argus 拒成 403 Uifid Not Found。
     */
    public static String buildSignedQuery(String awemeId, String cookie, String uifidOverride){
        String uifid = (uifidOverride == null) ? "" : uifidOverride.trim();
        if(uifid.length() == 0) uifid = cookieValue(cookie, "UIFID_TEMP");
        if(uifid.length() == 0) uifid = cookieValue(cookie, "UIFID");
        if(uifid.length() == 0) uifid = cookieValue(cookie, "uifid");''')

# 2) fetch 增加 uifid 重载
rep('2-fetch重载',
    '''    public static Result fetch(String awemeId, String cookie, int maxTries){
        Result r = new Result();
        for(int i = 0; i < maxTries; i++){
            r.tries = i + 1;
            try{
                r.signedQuery = buildSignedQuery(awemeId, cookie);''',
    '''    public static Result fetch(String awemeId, String cookie, int maxTries){
        return fetch(awemeId, cookie, maxTries, "");
    }

    /** 带显式 uifid 的 detail（批量下载用：本地 Cookie 里没有 UIFID_TEMP，必须外部补） */
    public static Result fetch(String awemeId, String cookie, int maxTries, String uifid){
        Result r = new Result();
        for(int i = 0; i < maxTries; i++){
            r.tries = i + 1;
            try{
                r.signedQuery = buildSignedQuery(awemeId, cookie, uifid);''')

io.open(D, 'w', encoding='utf-8').write(s)
print('DyDetail 成功:', ok)
print('DyDetail 失败:', miss)


# 3) 批量下载调用处：改用 dyEffectiveCookie + dyCurUifid
P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/MainActivity.java'
s = io.open(P, encoding='utf-8').read()
ok2, miss2 = [], []


def rep2(name, old, new):
    global s
    if old in s:
        s = s.replace(old, new, 1)
        ok2.append(name)
    else:
        miss2.append(name)


rep2('3-批量下载用实时Cookie+uifid',
     '''                // cookie：优先本地保存的完整登录态
                String ck = "";
                try{ ck = CookieStore.header(MainActivity.this, CookieStore.PLATFORM_DY); }
                catch(Throwable t){}
                if(ck == null) ck = "";
                DyDetail.Result r = DyDetail.fetch(awemeId, ck, 2);''',
     '''                // cookie / uifid：都走 App 的实时取值（WebView → 本地 → 访客凭据三级兜底）
                //
                // 以前这里只取 CookieStore 本地文件，那份里没有 UIFID_TEMP，
                // 于是 detail 请求不带 uifid，被抖音 Argus 直接 403：
                //   Blocked by ArgusSecurityPlugin Uifid Not Found
                // 而 App 内其实拿得到 uifid（日志 UIFID_FROM_CM len=352）。
                String ck = "";
                try{ ck = dyEffectiveCookie(); }catch(Throwable t){}
                if(ck == null) ck = "";
                String ui = "";
                try{ ui = dyCurUifid(); }catch(Throwable t){}
                if(ui == null) ui = "";
                DypLog.i("BATCH_DETAIL_CRED ckLen=" + ck.length() + " uifidLen=" + ui.length());
                DyDetail.Result r = DyDetail.fetch(awemeId, ck, 2, ui);''')

io.open(P, 'w', encoding='utf-8').write(s)
print('MainActivity 成功:', ok2)
print('MainActivity 失败:', miss2)