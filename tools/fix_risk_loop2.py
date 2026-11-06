# -*- coding: utf-8 -*-
"""
修死循环：验证通过 → 立刻又被判风控 → 再"通过" → ... 每 2 秒一轮，
最后把抖音彻底惹毛，从「滑块验证」升级成「抱歉出错了」。

日志证据（每秒都在刷）：
    19:14:19.419 DY_RISK_CLEARED 验证通过，自动重新解析 .../share/video/7703121486688570664/
    19:14:19.428 DY_RISK_CK_SAVED n=60 len=6754 hasTtwid=true hasSid=true
    19:14:19.928 DY_RISK_BLOCK 检测到风控/验证页
    19:14:21.296 DY_RISK_CLEARED 验证通过...
    19:14:21.733 DY_RISK_BLOCK ...

根因有三：
  [1] 「验证通过」的判定只看 URL 有没有 /video/ 或 /share/，
      但风险页的 URL 也可能带 share/video 片段 —— 于是每 2 秒触发一次
      「通过 → 重新解析 → 又被拦 → 又通过」。
  [2] 没有防重入：同一时刻可能有两三个 postDelayed 的 retry 在排队。
  [3] 没有次数上限：风控是持续不可恢复时（抱歉出错了），应该停下来
      让用户处理，而不是无限重试把风控等级越打越高。

修法：
  · 「验证通过」必须同时满足：当前 URL 是内容页 AND 页面不含任何风控关键词
    AND 距上次自动重试超过 8 秒。
  · 整轮风控自动重试最多 3 次，超了就停手，明确提示用户。
  · 重试期间打日志，方便定位。
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


# ---------- 1) 加防护字段 ----------
rep('1-加防护字段',
    '''    /** 当前是否停在风控页（等待用户手动通过验证） */
    volatile boolean curRiskBlocked = false;''',
    '''    /** 当前是否停在风控页（等待用户手动通过验证） */
    volatile boolean curRiskBlocked = false;
    /** 风控自动重试次数上限（防止无限重试把风控等级越打越高） */
    volatile int riskAutoRetry = 0;
    /** 上次风控自动重试的时间戳，用来做 8 秒节流 */
    volatile long riskLastRetryMs = 0L;
    /** 单轮自动重试上限 */
    static final int RISK_MAX_RETRY = 3;''')

# ---------- 2) 「验证通过」判定加严 + 节流 + 次数上限 ----------
rep('2-判定加严',
    '''                    boolean backToContent = (u.indexOf("/video/") >= 0
                            || u.indexOf("/share/") >= 0
                            || u.indexOf("/note/") >= 0
                            || u.indexOf("/slides/") >= 0);
                    boolean stillRisk = (u.indexOf("verify") >= 0 || u.indexOf("captcha") >= 0);
                    if(backToContent && !stillRisk){''',
    '''                    boolean backToContent = (u.indexOf("/video/") >= 0
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
                        riskAutoRetry++;
                        riskLastRetryMs = nowMs;
                        Log.i("DYP", "DY_RISK_RETRY n=" + riskAutoRetry + "/" + RISK_MAX_RETRY);''')

# ---------- 3) 解析成功时重置风控计数 ----------
rep('3-成功重置',
    '''                curRiskBlocked = false;
                o(r);
                s(fail ? "解析失败" : "完成");''',
    '''                curRiskBlocked = false;
                // 解析成功 → 风控已解除，把自动重试计数清零，下次风控重新计
                if(!fail){
                    riskAutoRetry = 0;
                    riskLastRetryMs = 0L;
                }
                o(r);
                s(fail ? "解析失败" : "完成");''')

io.open(P, 'w', encoding='utf-8').write(s)
print('成功:', ok)
print('失败:', miss)