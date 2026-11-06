# -*- coding: utf-8 -*-
"""风控显示分支里：先判死页面，死页面不亮 WebView，只给可操作说明。"""
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


# render 失败分支：先判死
rep('1-死页面不亮WebView',
    '''                // 风控页：把 WebView 亮出来让用户自己过验证，别偷偷藏掉
                boolean risk = isDyRiskPage(jsonForRender, curUrl);
                if(risk){''',
    '''                // 风控页：分两种，不能混
                //
                //  · 可挑战（滑块/验证码）→ 亮出 WebView + 操作条，让用户过
                //  · 终态拒绝（抱歉出错）  → 亮出来也没用，页面上没东西可点，
                //    用户只会以为 App 坏了。直接给恢复步骤。
                boolean riskDead = isDyRiskDead(jsonForRender, curUrl);
                if(riskDead){
                    Log.i("DYP", "DY_RISK_DEAD 抖音已终态拒绝（抱歉出错/请在抖音内观看）");
                    curRiskBlocked = false;
                    riskRetryUrl = "";
                    riskAutoRetry = 0;
                    riskLastRetryMs = 0L;
                    h.post(new Runnable(){ public void run(){
                        try{
                            web.setVisibility(View.GONE);
                            web.setLayoutParams(new LinearLayout.LayoutParams(1, 1));
                            pageParse.setVisibility(View.VISIBLE);
                        }catch(Throwable t){}
                        showHintToast("抖音已拒绝本次请求（不是滑块问题），需要等一段时间");
                        s("抖音已拒绝：等 10~15 分钟后重试，或换网络 / 重新登录");
                        showDyRiskDeadDialog();
                    }});
                    return;
                }
                // 风控页：把 WebView 亮出来让用户自己过验证，别偷偷藏掉
                boolean risk = isDyRiskPage(jsonForRender, curUrl);
                if(risk){''')

io.open(P, 'w', encoding='utf-8').write(s)
print('成功:', ok)
print('失败:', miss)