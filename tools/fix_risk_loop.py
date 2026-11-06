# -*- coding: utf-8 -*-
"""
修两个风控问题：

【问题1】过完滑块后，后续几条又触发风控，但滑块页不再显示
    原因：风控页只在「解析失败」分支里（render 返回 blocked）才 showWeb。
          而后续条目可能命中另一条路径（比如 WebView 直接跳到 verify 页，
          根本没走到 render），这时没人把 WebView 亮出来，
          用户只看到转圈 / 失败，不知道要再验证一次。

    修法：onPageFinished 里无论哪条路径，只要 URL 命中 verify/captcha
          或页面文本含验证关键词，就主动把 WebView 亮出来。

【问题2】每条都要重新验证（过了一次没用）
    原因：验证通过只改内存标志，没有把验证后拿到的 Cookie 落到本地；
          抖音的验证结果体现在 Cookie 里（ttwid / sessionid / 
          passport_csrf 等），不保存就每条重新验。

    修法：验证通过时立刻把 CookieManager 里的抖音 Cookie 全量存到
          SharedPreferences，并回灌 WebView；下次解析直接带着走。
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


# ---------- A) 验证通过时：保存 Cookie，后续复用 ----------
rep('A-验证通过存Cookie',
    '''                    if(backToContent && !stillRisk){
                        curRiskBlocked = false;
                        final String retry = riskRetryUrl;
                        riskRetryUrl = "";
                        Log.i("DYP", "DY_RISK_CLEARED 验证通过，自动重新解析 " + retry);
                        showHintToast("验证已通过，正在重新解析…");''',
    '''                    if(backToContent && !stillRisk){
                        curRiskBlocked = false;
                        final String retry = riskRetryUrl;
                        riskRetryUrl = "";
                        Log.i("DYP", "DY_RISK_CLEARED 验证通过，自动重新解析 " + retry);
                        // 关键：把验证后拿到的 Cookie 存下来。
                        // 抖音的「已验证」状态存在 Cookie 里（ttwid / sessionid 等），
                        // 不保存就变成每条都要重新拖一次滑块。
                        try{
                            saveDyCookieAfterVerify();
                        }catch(Throwable t){
                            Log.i("DYP", "DY_RISK_SAVE_CK_ERR " + t);
                        }
                        showHintToast("验证已通过，正在重新解析…");''')

# ---------- B) onPageFinished：任何路径都检测风控页 ----------
rep('B-页面级风控检测',
    '''                // 风控页自动续跑：用户在页面里过完验证后，
                // URL 会自己跳回作品页 / 分享页，这时自动重新解析一次，
                // 用户不用手动再点「解析」按钮。
                if(curRiskBlocked && u != null && riskRetryUrl.length() > 0){''',
    '''                // 【风控页主动亮出来】
                //
                // 除了 render() 的失败分支，WebView 自己也可能直接跳到 verify 页
                // （抖音的验证码中间页就是这种，URL 停在 verify，不会走 render）。
                // 之前只有 render 失败才显示网页，于是第二条开始：
                // 风控了 → 没人亮页面 → 用户只看到转圈，以为卡死。
                // 这里统一在页面加载完成时兜一次。
                if(u != null && (u.indexOf("verify") >= 0 || u.indexOf("captcha") >= 0
                        || u.indexOf("secsdk") >= 0)){
                    if(!curRiskBlocked){
                        Log.i("DYP", "DY_RISK_PAGE_DETECTED url=" + u);
                        curRiskBlocked = true;
                        if(riskRetryUrl.length() == 0) riskRetryUrl = curUrl;
                        h.post(new Runnable(){ public void run(){
                            try{
                                pageParse.setVisibility(View.GONE);
                                web.setLayoutParams(new LinearLayout.LayoutParams(
                                        LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
                                web.setVisibility(View.VISIBLE);
                                web.bringToFront();
                            }catch(Throwable t){}
                        }});
                        showHintToast("请在页面里完成安全验证，完成后自动继续");
                        s("需要安全验证，请在页面里完成");
                    }
                }
                // 风控页自动续跑：用户在页面里过完验证后，
                // URL 会自己跳回作品页 / 分享页，这时自动重新解析一次，
                // 用户不用手动再点「解析」按钮。
                if(curRiskBlocked && u != null && riskRetryUrl.length() > 0){''')

# ---------- C) 新增：验证后保存抖音 Cookie 的方法 ----------
rep('C-保存Cookie方法',
    '''    /** 当前是否停在风控页（等待用户手动通过验证） */''',
    '''    /**
     * 风控验证通过后，把抖音 Cookie 存到本地。
     *
     * 抖音的「已通过验证」是记在 Cookie 里的（ttwid / sessionid / 
     * passport_csrf_token 等）。不落盘的话，每次解析都是干净会话，
     * 表现就是「每条都要重新拖滑块」。
     */
    void saveDyCookieAfterVerify(){
        try{
            String ck = android.webkit.CookieManager.getInstance()
                    .getCookie("https://www.douyin.com");
            if(ck == null || ck.length() == 0){
                Log.i("DYP", "DY_RISK_NO_CK 验证后没拿到 Cookie");
                return;
            }
            // 只要含关键项才存，避免把空壳 Cookie 覆盖掉之前有效的
            boolean useful = ck.indexOf("ttwid=") >= 0
                    || ck.indexOf("sessionid=") >= 0
                    || ck.indexOf("passport_csrf_token") >= 0
                    || ck.indexOf("msToken=") >= 0
                    || ck.indexOf("odin_tt=") >= 0;
            if(!useful){
                Log.i("DYP", "DY_RISK_CK_NOT_USEFUL len=" + ck.length());
                return;
            }
            getSharedPreferences(SP, MODE_PRIVATE).edit()
                    .putString("dy_cookie", ck)
                    .putLong("dy_cookie_ts", System.currentTimeMillis())
                    .apply();
            Log.i("DYP", "DY_RISK_CK_SAVED len=" + ck.length()
                    + " hasTtwid=" + (ck.indexOf("ttwid=") >= 0)
                    + " hasSid=" + (ck.indexOf("sessionid=") >= 0));
        }catch(Throwable t){
            Log.i("DYP", "DY_RISK_CK_SAVE_ERR " + t);
        }
    }
    /** 当前是否停在风控页（等待用户手动通过验证） */''')

io.open(P, 'w', encoding='utf-8').write(s)
print('成功:', ok)
print('失败:', miss)