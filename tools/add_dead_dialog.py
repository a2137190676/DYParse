# -*- coding: utf-8 -*-
"""新增 showDyRiskDeadDialog()：终态拒绝时给用户明确的恢复步骤。"""
import io

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/MainActivity.java'
s = io.open(P, encoding='utf-8').read()

anchor = '''    /**
     * 判断是不是「终态拒绝」—— 抖音彻底不给机会了。'''

block = '''    /** 终态拒绝弹窗：不装样子，直接给能动作的步骤 */
    void showDyRiskDeadDialog(){
        try{
            final android.app.AlertDialog.Builder b = dypDlg();
            b.setTitle("抖音已拒绝访问");
            b.setMessage(
                "页面显示「抱歉出错了 / 请尝试在抖音内观看」，"
                + "这是抖音直接拒绝，不是滑块验证 —— 所以重试也不会出滑块，"
                + "这属于正常现象，不是你操作错了。\\n\\n"
                + "恢复办法（按顺序试）：\\n"
                + "1. 先停 10~15 分钟不要解析。抖音的拒绝是按频率累计的，"
                + "越点恢复越慢。\\n"
                + "2. 换网络：WiFi 和 5G 互换，出口 IP 变了风控等级也会变。\\n"
                + "3. 设置 →「抖音登录 / 风控处理」→ 清除登录 Cookie 后重新登录一次。"
                + "旧的 Cookie 可能已被标记。\\n"
                + "4. 上述都试过还不行，就等半小时以上。\\n\\n"
                + "提示：主页列表的批量下载已经改成直接用列表里的直链，"
                + "不会再逐条打开分享页，以后触发风控的频率会明显下降。");
            b.setPositiveButton("知道了", null);
            b.setNeutralButton("去设置看登录", new android.content.DialogInterface.OnClickListener(){
                public void onClick(android.content.DialogInterface d, int w){
                    try{ switchTab(4); }catch(Throwable t){}
                }
            });
            b.show();
        }catch(Throwable t){ Log.i("DYP", "RISK_DEAD_DLG_ERR " + t); }
    }

'''

if anchor in s:
    s = s.replace(anchor, block + anchor, 1)
    io.open(P, 'w', encoding='utf-8').write(s)
    print('OK 已插入 showDyRiskDeadDialog')
else:
    print('FAIL 找不到锚点')