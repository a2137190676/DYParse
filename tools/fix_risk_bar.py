# -*- coding: utf-8 -*-
"""
修：风控页「卡死不动」+「切页签被 WebView 盖住」

用户报的现象：
    1. 滑块验证过了，一直停在那个 web 界面不动
    2. 点别的页签（列表/下载/历史/设置），WebView 还盖在上面，
       下面露出「抖音要求安全验证，请在页面里完成验证，完成后会自动继续」

两层根因：

  【根因 A】App 猜不出验证过没过
      旧逻辑靠 onPageFinished 判断 URL 是否跳回 /video/ 或 /share/。
      但抖音验证通过后，URL 常常还停在 verify 页，靠 URL 判永远判不出来。
      于是 curRiskBlocked 一直是 true，WebView 一直亮着，没人收。
      而地址栏那个 /share/video/ 本来就含 /share/，
      又会导致「误判通过 → 重解析 → 立刻又被拦」的死循环。

  【根因 B】switchTab 只管自己的页，不管 WebView
      WebView 在布局里是 1x1，一旦风控把它铺满并 bringToFront，
      它就变成了最上层浮层。switchTab(1) 只 GONE 了 pageParse，
      WebView 还在最前面，用户看到的就是「点了没反应」。

改法（不猜了，改成显式操作条）：

  1. 风控页显示时，不再依赖 URL 自动判定。
     改为在页面上叠一条操作条：【✓ 我已完成验证】 【重试】 【关闭】
     用户自己说「过了」，App 就存 Cookie + 收起网页 + 回到解析页。
     这样绝不会死循环，也绝不会卡住。

  2. switchTab 里统一兜底：切到任何非解析页时，
     只要不是登录模式，就把 WebView 收起来（GONE + 1x1）。
     保证任何页签都能正常看到。

  3. 风控时的提示文案改成告诉用户「过了点上面的按钮」。
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


# ============================================================
# 1) 风控自动判定整段删掉，换成「什么都没变，等用户点按钮」
# ============================================================
rep('1-删掉URL自动判定',
    '''                if(curRiskBlocked && u != null && riskRetryUrl.length() > 0){
                    boolean backToContent = (u.indexOf("/video/") >= 0
                            || u.indexOf("/share/") >= 0
                            || u.indexOf("/note/") >= 0
                            || u.indexOf("/slides/") >= 0);
                    boolean stillRisk = (u.indexOf("verify") >= 0 || u.indexOf("captcha") >= 0
                            || u.indexOf("secsdk") >= 0 || u.indexOf("argus") >= 0);
                    // 节流：两次自动重试之间至少 8 秒。
                    // 以前没有节流，onPageFinished 每次触发都算「验证通过」，
                    // 于是「通过 → 重解析 → 立刻又被拦 → 再通过」死循环，
                    // 每 2 秒一轮，最后被抖音升级成「抱歉出错了」。
                    long nowMs = System.currentTimeMillis();
                    boolean tooSoon = (nowMs - riskLastRetryMs) < 8000L;
                    if(tooSoon){
                        Log.i("DYP", "DY_RISK_THROTTLE 距上次重试仅 "
                                + (nowMs - riskLastRetryMs) + "ms，跳过");
                        return;
                    }
                    if(riskAutoRetry >= RISK_MAX_RETRY){
                        Log.i("DYP", "DY_RISK_GIVEUP 已自动重试 " + riskAutoRetry
                                + " 次仍被风控，停止重试");
                        curRiskBlocked = false;
                        riskRetryUrl = "";
                        riskAutoRetry = 0;
                        h.post(new Runnable(){ public void run(){
                            showHintToast("抖音风控未解除，请稍等几分钟再试（或先在 App 里打开抖音登录）");
                            s("风控未解除，已停止自动重试");
                        }});
                        return;
                    }
                    if(backToContent && !stillRisk){
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
                    }
                }''',
    '''                // 【风控不再由代码猜】
                //
                // 以前这里靠 URL 判断「验证过没过」，两个致命问题：
                //   a) 抖音验证通过后 URL 常停在 verify 页，靠 URL 永远判不出来
                //      → 风控页永远不消失，用户被卡在 web 界面
                //   b) 分享页地址 /share/video/ 本身就含 /share/，
                //      于是「误判通过 → 自动重解析 → 立刻又被拦」死循环
                //      → 最后被抖音升级成「抱歉出错了」
                //
                // 现在改成：代码不再猜。风控页上叠一条操作条，
                // 由用户点「我已完成验证」，App 才存 Cookie 并收起网页。
                // 用户还能点「重试」「关闭」，任何情况都不会卡死。''')

# ============================================================
# 2) 风控页显示时，加上操作条 + 改文案
# ============================================================
rep('2-风控显示加操作条',
    '''                        h.post(new Runnable(){ public void run(){
                            try{
                                pageParse.setVisibility(View.GONE);
                                web.setLayoutParams(new LinearLayout.LayoutParams(
                                        LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
                                web.setVisibility(View.VISIBLE);
                                web.bringToFront();
                            }catch(Throwable t){}
                        }});
                        showHintToast("请在页面里完成安全验证，完成后自动继续");
                        s("需要安全验证，请在页面里完成");''',
    '''                        h.post(new Runnable(){ public void run(){
                            try{
                                pageParse.setVisibility(View.GONE);
                                web.setLayoutParams(new LinearLayout.LayoutParams(
                                        LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
                                web.setVisibility(View.VISIBLE);
                                web.bringToFront();
                            }catch(Throwable t){}
                            showRiskBar();
                        }});
                        showHintToast("请在页面里完成验证，完成后点下方「我已完成验证」");
                        s("需要安全验证，请完成验证后点按钮");''')

# ============================================================
# 3) render 失败分支（9060 附近）也一样加操作条
# ============================================================
rep('3-render风控加操作条',
    '''                            pageParse.setVisibility(View.GONE);
                            web.setLayoutParams(new LinearLayout.LayoutParams(
                                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
                            web.setVisibility(View.VISIBLE);
                            web.bringToFront();
                        }catch(Throwable t){}
                    }});
                    showHintToast("抖音要求安全验证，请在页面里完成验证，完成后会自动继续");
                    s("需要安全验证，请在页面里完成");''',
    '''                            pageParse.setVisibility(View.GONE);
                            web.setLayoutParams(new LinearLayout.LayoutParams(
                                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
                            web.setVisibility(View.VISIBLE);
                            web.bringToFront();
                        }catch(Throwable t){}
                        showRiskBar();
                    }});
                    showHintToast("抖音要求安全验证，请在页面里完成验证");
                    s("需要安全验证，请完成验证后点按钮");''')

# ============================================================
# 4) switchTab 兜底：切到非解析页时收起 WebView
# ============================================================
rep('4-switchTab收WebView',
    '''            pageSettings.setVisibility(idx == 4 ? View.VISIBLE : View.GONE);''',
    '''            pageSettings.setVisibility(idx == 4 ? View.VISIBLE : View.GONE);
            // 【关键兜底】切到「非解析页」时把 WebView 收起来。
            //
            // 风控时 WebView 会被铺满 + bringToFront，变成最上层浮层。
            // 以前 switchTab 只 GONE 了自己的 pageParse，
            // WebView 还盖在最前面，于是用户点「列表」「下载」「设置」
            // 看到的还是那个验证页面 —— 就是这个原因。
            //
            // 登录类模式不能收（用户正在页面里操作），其余一律收起。
            try{
                if(idx != 0 && web != null && web.getVisibility() == View.VISIBLE){
                    boolean loginMode = dyProfileLoginMode || dyMobileLogin
                            || biliLoginMode || ytLoginMode || insLoginMode
                            || xhsLoginMode || ybLoginMode;
                    if(!loginMode){
                        Log.i("DYP", "TAB_HIDE_WEB idx=" + idx);
                        web.setVisibility(View.GONE);
                        web.setLayoutParams(new LinearLayout.LayoutParams(1, 1));
                        // 风控态也一起清掉，避免回来又自动展开
                        if(curRiskBlocked){
                            Log.i("DYP", "DY_RISK_CLEARED 用户切走了页签");
                            curRiskBlocked = false;
                            riskRetryUrl = "";
                            hideRiskBar();
                        }
                    }
                }
            }catch(Throwable t){}''')

io.open(P, 'w', encoding='utf-8').write(s)
print('成功:', ok)
print('失败:', miss)