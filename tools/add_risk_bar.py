# -*- coding: utf-8 -*-
"""新增风控操作条 showRiskBar() / hideRiskBar()，插在 showDyLoginBar 前面。"""
import io

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/MainActivity.java'
s = io.open(P, encoding='utf-8').read()

anchor = '''    void showDyLoginBar(){'''

block = '''    // ============================================================
    // 风控操作条
    //
    // 为什么需要它：
    //   抖音验证通过后，URL 经常还停在 verify 页，代码没法从 URL 判断
    //   「用户到底过没过」。以前硬猜，结果是死循环或永久卡住。
    //   现在把判断权交给用户：页面上叠一条条，
    //   用户过完验证自己点「我已完成验证」，App 就存 Cookie 收页面。
    // ============================================================

    LinearLayout riskBar = null;

    /** 风控操作条：✓ 我已完成验证 / 重试 / 关闭 */
    void showRiskBar(){
        try{
            if(riskBar != null && riskBar.getParent() != null) return;
            android.view.ViewGroup host = (web == null) ? null
                    : (web.getParent() instanceof android.view.ViewGroup
                        ? (android.view.ViewGroup) web.getParent() : null);
            if(host == null){ Log.i("DYP", "RISK_BAR no_host"); return; }

            LinearLayout bar = new LinearLayout(this);
            bar.setOrientation(LinearLayout.HORIZONTAL);
            bar.setBackgroundColor(curDark ? 0xEE22262E : 0xEEFFFFFF);
            bar.setPadding(dp(10), dp(8), dp(8), dp(8));
            bar.setId(900005);

            TextView tip = new TextView(this);
            tip.setTextSize(11);
            tip.setTextColor(curDark ? 0xFFB8BEC9 : 0xFF4B5563);
            tip.setText("验证完成后点右边 →");
            LinearLayout.LayoutParams lp1 = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            bar.addView(tip, lp1);

            android.widget.Button bOk = new android.widget.Button(this);
            bOk.setText("我已完成验证");
            bOk.setTextSize(12);
            bOk.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){
                // 用户说过了 → 存 Cookie，收起网页，回到解析页
                try{ saveDyCookieAfterVerify(); }catch(Throwable t){
                    Log.i("DYP", "RISK_BAR_SAVE_ERR " + t);
                }
                curRiskBlocked = false;
                riskRetryUrl = "";
                riskAutoRetry = 0;
                riskLastRetryMs = 0L;
                Log.i("DYP", "RISK_BAR_USER_OK 用户确认已完成验证");
                hideRiskBar();
                try{
                    web.setVisibility(View.GONE);
                    web.setLayoutParams(new LinearLayout.LayoutParams(1, 1));
                    pageParse.setVisibility(View.VISIBLE);
                }catch(Throwable t){}
                showHintToast("已验证并已保存。请点「解析」重试一次");
                s("验证已完成，请点「解析」重试");
            }});
            bar.addView(bOk);

            android.widget.Button bRetry = new android.widget.Button(this);
            bRetry.setText("重试");
            bRetry.setTextSize(12);
            bRetry.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){
                // 用户自己点的重试，不受自动重试上限限制
                final String u = (riskRetryUrl != null && riskRetryUrl.length() > 0)
                        ? riskRetryUrl : curUrl;
                Log.i("DYP", "RISK_BAR_USER_RETRY " + u);
                hideRiskBar();
                curRiskBlocked = false;
                riskRetryUrl = "";
                riskAutoRetry = 0;
                riskLastRetryMs = 0L;
                try{
                    web.setVisibility(View.GONE);
                    web.setLayoutParams(new LinearLayout.LayoutParams(1, 1));
                    pageParse.setVisibility(View.VISIBLE);
                }catch(Throwable t){}
                if(u != null && u.length() > 0){
                    final String uu = u;
                    h.postDelayed(new Runnable(){ public void run(){
                        try{ loadForParse(uu); }catch(Throwable t){}
                    }}, 600);
                }
            }});
            bar.addView(bRetry);

            android.widget.Button bClose = new android.widget.Button(this);
            bClose.setText("关闭");
            bClose.setTextSize(12);
            bClose.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){
                Log.i("DYP", "RISK_BAR_USER_CLOSE");
                hideRiskBar();
                curRiskBlocked = false;
                riskRetryUrl = "";
                riskAutoRetry = 0;
                try{
                    web.setVisibility(View.GONE);
                    web.setLayoutParams(new LinearLayout.LayoutParams(1, 1));
                    pageParse.setVisibility(View.VISIBLE);
                }catch(Throwable t){}
                showHintToast("已关闭。风控通常需要等 10~15 分钟再试");
                s("就绪");
            }});
            bar.addView(bClose);

            android.view.ViewGroup.LayoutParams flp;
            if(host instanceof android.widget.FrameLayout){
                android.widget.FrameLayout.LayoutParams p =
                    new android.widget.FrameLayout.LayoutParams(
                        android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                        android.widget.FrameLayout.LayoutParams.WRAP_CONTENT);
                p.gravity = android.view.Gravity.BOTTOM;
                flp = p;
            } else if(host instanceof LinearLayout){
                flp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            } else {
                flp = new android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
            }
            host.addView(bar, flp);
            bar.bringToFront();
            riskBar = bar;
            Log.i("DYP", "RISK_BAR_SHOWN");
        }catch(Throwable t){ Log.i("DYP", "RISK_BAR_ERR " + t); }
    }

    void hideRiskBar(){
        try{
            if(riskBar != null && riskBar.getParent() instanceof android.view.ViewGroup){
                ((android.view.ViewGroup) riskBar.getParent()).removeView(riskBar);
            }
        }catch(Throwable t){}
        riskBar = null;
    }

'''

if anchor in s:
    s = s.replace(anchor, block + anchor, 1)
    io.open(P, 'w', encoding='utf-8').write(s)
    print('OK 已插入 showRiskBar/hideRiskBar')
else:
    print('FAIL 找不到锚点')