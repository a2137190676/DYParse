# -*- coding: utf-8 -*-
"""
第 4 项收尾：设置页加「强制记录」和「日志保留天数」两个入口。

【为什么需要「强制记录」】
  调试日志默认关闭（减少无谓写入）。
  用户反馈问题时经常忘了先打开开关，导致日志里啥都没有。
  「强制记录」打开后，即使调试日志开关是关的，DypLog 也照写文件。

【为什么需要「日志保留天数」】
  用户要求「大于几天的时候清空旧日志」。
  DiagLog.cleanupByPolicy() 已经实现了按天数删，
  这里提供 UI 让用户自己选保留多久（关闭 / 3 / 7 / 15 / 30 天）。
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
# 1. 设置页加两行
# ============================================================
rep('1-设置行',
    '''        addSettingRow("崩溃日志", curCrashLogLabel(), new View.OnClickListener(){
            public void onClick(View v){ showCrashLogView(); }});
    }''',
    '''        addSettingRow("崩溃日志", curCrashLogLabel(), new View.OnClickListener(){
            public void onClick(View v){ showCrashLogView(); }});
        addSettingRow("强制记录", forceLogLabel(), new View.OnClickListener(){
            public void onClick(View v){ toggleForceLog(); }});
        addSettingRow("日志保留天数", keepDaysLabel(), new View.OnClickListener(){
            public void onClick(View v){ showKeepDaysDialog(); }});
    }

    // ================= 日志：强制记录 / 保留天数 =================

    /** 「强制记录」副标题 */
    String forceLogLabel(){
        boolean on = DypLog.isForceOn();
        if(on){
            return "已开启 · 调试日志开关关着也照写（帮我们抓现场用）";
        }
        return "已关闭 · 开关关着就不写文件，打开后即使日志开关关着也记录";
    }

    /** 切换强制记录 */
    void toggleForceLog(){
        boolean on = DypLog.isForceOn();
        DypLog.setForceOn(!on);
        showHintToast(!on
            ? "强制记录已开启：即使调试日志关着，功能日志也会写入文件"
            : "强制记录已关闭");
        buildSettingsPage();
    }

    /** 「日志保留天数」副标题 */
    String keepDaysLabel(){
        int d = DiagLog.keepDays();
        long sz = 0, dirSz = 0;
        int files = 0;
        try{
            java.io.File f = DiagLog.debugFile();
            if(f != null && f.exists()) sz = f.length();
            if(f != null && f.getParentFile() != null){
                java.io.File[] fs = f.getParentFile().listFiles();
                if(fs != null){
                    files = fs.length;
                    for(java.io.File x : fs) if(x != null && x.isFile()) dirSz += x.length();
                }
            }
        }catch(Throwable t){}
        String keep = (d <= 0) ? "不按时间清理" : ("保留 " + d + " 天");
        return keep + " · 单文件上限 100MB · 当前 "
                + InnerDownloader.sizeText(sz) + " / 目录 " + files + " 个共 "
                + InnerDownloader.sizeText(dirSz);
    }

    /** 选日志保留天数 */
    void showKeepDaysDialog(){
        curDark = isDarkMode();
        final int[] vals = { 3, 7, 15, 30, 0 };
        String[] items = new String[vals.length];
        for(int i = 0; i < vals.length; i++){
            if(vals[i] == 0) items[i] = "不按时间清理（只受 100MB 上限约束）";
            else items[i] = "保留 " + vals[i] + " 天";
        }
        int cur = DiagLog.keepDays();
        int sel = 1;   // 默认 7 天
        for(int i = 0; i < vals.length; i++) if(vals[i] == cur) sel = i;
        dypDlg()
            .setTitle("日志保留天数")
            .setMessage("超过天数的旧日志文件会被自动删除。\\n\\n"
                    + "另有两条固定规则，不受这里影响：\\n"
                    + "· 单个日志文件超过 100MB → 截断保留后半段\\n"
                    + "· logs 目录总大小超过 100MB → 从最旧的文件开始删\\n\\n"
                    + "debug.log / crash.log 是当前正在写的文件，不按天数删。")
            .setSingleChoiceItems(items, sel, new android.content.DialogInterface.OnClickListener(){
                public void onClick(android.content.DialogInterface dd, int which){
                    DiagLog.setKeepDays(vals[which]);
                    Log.i("DYP", "LOG_KEEP_DAYS set=" + vals[which]);
                    try{ dd.dismiss(); }catch(Throwable t){}
                    buildSettingsPage();
                    showHintToast(vals[which] == 0
                        ? "已关闭按天数清理" : ("日志保留 " + vals[which] + " 天"));
                }
            })
            .setNegativeButton("取消", null)
            .create().show();
    }''')

io.open(P, 'w', encoding='utf-8').write(s)
print('成功:', ok)
print('失败:', miss)