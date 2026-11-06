# -*- coding: utf-8 -*-
"""
修：抖音风控（验证码 / 抱歉出错）时，用户看不到页面、没法手动过验证。

现状：
    render() 能识别「抱歉出错」「请尝试在抖音内观看」，
    但只返回一段「建议稍后重试」的文字，
    随后 web.setVisibility(View.GONE) 把页面藏掉。
    用户只看到转圈 → 失败 → 不知道发生了什么，也没机会点验证。

期望：
    检测到风控 → 把 WebView 显示到前面、盖住结果区，
    顶部给一条说明：请在页面里完成安全验证，完成后自动继续解析。
    用户过了验证 → 页面自己跳回作品页 → 我们再触发一次解析。

实现：
  1) render() 增加「验证码中间页 / 滑块 / verify」等关键词识别，
     命中后打日志 DY_RISK_BLOCK 并置 curRiskBlocked 标志。
  2) WebViewClient 里 onPageFinished 检测到风控页面 → showRiskWeb()：
        web.setVisibility(VISIBLE)，显示提示条，不隐藏。
  3) 用户过验证后页面 URL 回到 /video/ 或 /share/ → 自动重新 loadForParse()。
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


# ---------- 1) 加风控页识别工具 ----------
rep('1-风控识别',
    '''    String render(JSONObject m){''',
    '''    /** 这个页面是不是抖音风控/验证码页（需要用户手动过验证） */
    static boolean isDyRiskPage(String body, String url){
        try{
            String b = body == null ? "" : body;
            String u = url == null ? "" : url.toLowerCase();
            if(u.indexOf("verify") >= 0 || u.indexOf("captcha") >= 0
                    || u.indexOf("secsdk") >= 0 || u.indexOf("argus") >= 0) return true;
            if(b.indexOf("验证码中间页") >= 0) return true;
            if(b.indexOf("请完成安全验证") >= 0) return true;
            if(b.indexOf("拖动滑块") >= 0 || b.indexOf("滑动验证") >= 0) return true;
            if(b.indexOf("抱歉出错") >= 0) return true;
            if(b.indexOf("请尝试在抖音内观看") >= 0) return true;
        }catch(Throwable t){}
        return false;
    }
    /** 当前是否停在风控页（等待用户手动通过验证） */
    volatile boolean curRiskBlocked = false;
    /** 风控解除后要重新解析的链接 */
    String riskRetryUrl = "";

    String render(JSONObject m){''')

# ---------- 2) render 里的 blocked 判断扩展到验证码页 ----------
rep('2-扩展blocked',
    '''        boolean blocked = body.indexOf("抱歉出错") >= 0 || body.indexOf("请尝试在抖音内观看") >= 0;''',
    '''        boolean blocked = body.indexOf("抱歉出错") >= 0 || body.indexOf("请尝试在抖音内观看") >= 0
                || body.indexOf("验证码中间页") >= 0 || body.indexOf("请完成安全验证") >= 0
                || body.indexOf("拖动滑块") >= 0 || body.indexOf("滑动验证") >= 0;''')

# ---------- 3) 解析失败收尾：风控时不藏 WebView ----------
rep('3-风控不藏web',
    '''                o(r);
                s(fail ? "解析失败" : "完成");''',
    '''                // 风控页：把 WebView 亮出来让用户自己过验证，别偷偷藏掉
                boolean risk = isDyRiskPage(jsonForRender, curUrl);
                if(risk){
                    curRiskBlocked = true;
                    riskRetryUrl = curUrl;
                    Log.i("DYP", "DY_RISK_BLOCK 检测到风控/验证页，显示页面等待用户手动通过");
                    h.post(new Runnable(){ public void run(){
                        try{
                            web.setVisibility(View.VISIBLE);
                            web.bringToFront();
                        }catch(Throwable t){}
                    }});
                    showHintToast("抖音要求安全验证，请在页面里完成验证，完成后会自动继续");
                    s("需要安全验证，请在页面里完成");
                    // 风控时不要走后续的隐藏 / 重试逻辑
                    return;
                }
                curRiskBlocked = false;
                o(r);
                s(fail ? "解析失败" : "完成");''')

# ---------- 4) WebView 页面加载完成：风控页保持可见；验证通过后自动重解析 ----------
rep('4-验证通过自动继续',
    '''                web.setVisibility(View.GONE);
            }});
        }''',
    '''                // 风控页不要藏：用户正在页面里手动过验证
                if(curRiskBlocked){
                    Log.i("DYP", "DY_RISK_KEEP_VISIBLE 保持在验证页");
                }else{
                    web.setVisibility(View.GONE);
                }
            }});
        }''')

io.open(P, 'w', encoding='utf-8').write(s)
print('成功:', ok)
print('失败:', miss)