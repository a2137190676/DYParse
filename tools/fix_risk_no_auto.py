# -*- coding: utf-8 -*-
"""
改：风控验证通过后，不再自动重新解析，改为提示用户手动点。

日志证据：
    19:24:04  DY_RISK_RETRY n=1/3      ← 用户过了滑块
    19:24:04  DY_RISK_CLEARED 验证通过，自动重新解析
    19:24:07  DY_RISK_BLOCK            ← 3 秒后又被拦
    19:24:21  DY_RISK_RETRY n=2/3      ← 用户又过了一次
    19:24:21  DY_RISK_CLEARED 验证通过，自动重新解析   ← 立刻
    19:24:21  DY_RISK_BLOCK            ← 又被拦

问题本质：
    用户过完滑块，抖音确实放行了（Cookie 已更新，len 从 6754 → 12275）。
    但「自动重新解析」= 又发一次请求，而这个请求刚好处在
    风控的冷却窗口里，于是又被拦。
    然后 App 再自动重试，用户再滑一次，又被拦……

    这是自己给自己加码。风控是按请求频率算的，
    验证通过后立刻再打一次，是最容易被二次拦的动作。

改法：
    验证通过 → 只保存 Cookie + 提示「验证已通过，请手动点解析」。
    不自动发请求。用户自己控制节奏，风控才有机会冷却。

    自动重试次数从 3 降到 1（只用于网络抖动那种偶发情况）。
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


# ---------- 1) 验证通过后不再自动重解析 ----------
rep('1-不自动重解析',
    '''                    if(backToContent && !stillRisk){
                        riskAutoRetry++;
                        riskLastRetryMs = nowMs;
                        Log.i("DYP", "DY_RISK_RETRY n=" + riskAutoRetry + "/" + RISK_MAX_RETRY);
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
                        showHintToast("验证已通过，正在重新解析…");
                        h.postDelayed(new Runnable(){ public void run(){
                            try{ loadForParse(retry); }catch(Throwable t){
                                Log.i("DYP", "DY_RISK_RETRY_ERR " + t); }
                        }}, 1200);
                    }''',
    '''                    if(backToContent && !stillRisk){
                        curRiskBlocked = false;
                        riskRetryUrl = "";
                        Log.i("DYP", "DY_RISK_CLEARED 验证通过");
                        // 把验证后拿到的 Cookie 存下来 —— 这是唯一必须做的。
                        // 抖音的「已验证」状态存在 Cookie 里，存了下次就不用再滑。
                        try{
                            saveDyCookieAfterVerify();
                        }catch(Throwable t){
                            Log.i("DYP", "DY_RISK_SAVE_CK_ERR " + t);
                        }
                        // 【关键改动】不再自动重新解析。
                        //
                        // 实测：自动重解析会被二次拦（日志 19:24:04 通过 →
                        // 19:24:07 又被拦 → 用户再滑 → 又被拦）。
                        // 因为验证刚通过的那几秒正是风控冷却窗口，
                        // 这时候再发请求最容易被打回。让用户自己控节奏。
                        h.post(new Runnable(){ public void run(){
                            showHintToast("验证已通过，Cookie 已保存。请点「解析」继续");
                            s("验证已通过，点「解析」继续");
                            // 收起网页，露出解析页，让用户自己点
                            try{
                                web.setVisibility(View.GONE);
                                web.setLayoutParams(new LinearLayout.LayoutParams(1, 1));
                                pageParse.setVisibility(View.VISIBLE);
                            }catch(Throwable t){}
                        }});
                    }''')

# ---------- 2) 自动重试上限降到 1 ----------
rep('2-重试上限降1',
    '''    /** 单轮自动重试上限 */
    static final int RISK_MAX_RETRY = 3;''',
    '''    /** 单轮自动重试上限（只兜网络抖动那种偶发，不做风控对抗） */
    static final int RISK_MAX_RETRY = 1;''')

io.open(P, 'w', encoding='utf-8').write(s)
print('成功:', ok)
print('失败:', miss)