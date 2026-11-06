# -*- coding: utf-8 -*-
"""
修：日志保留天数弹窗看不见选项。

【现象】
    uiautomator 抓到的弹窗只有：
        text="日志保留天数"
        text="超过天数的旧日志文件会被自动删除。……"     ← message
        text="取消"                                    ← negative
      「保留 3 天 / 保留 7 天 / ……」这些单选条目一条都没有。

【根因】
    setMessage + setSingleChoiceItems 同时用。
    AlertDialog 内部 message 区和列表区争同一块空间，
    ColorOS 上是 message 把列表挤没了。

    用户在更早的需求里已经明确说过：
    「不要在同一弹窗同时用 setMessage + setItems（列表项会被挤掉）」

【改法】两步弹窗：
    ① 先说明弹窗（只有 setMessage + 「选择天数」按钮）
    ② 再弹纯列表弹窗（只有 setSingleChoiceItems，没有 message）
"""
import io

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/MainActivity.java'
s = io.open(P, encoding='utf-8').read()

old = '''    void showKeepDaysDialog(){
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
    }'''

new = '''    void showKeepDaysDialog(){
        curDark = isDarkMode();
        // 两步弹窗：先说明，再选。
        // 不能 setMessage + setSingleChoiceItems 混用 ——
        // message 会把列表挤掉，用户只能看到说明和取消按钮，
        // 选项一条都不显示（这个坑本项目踩过一次，用户明确要求禁止）。
        dypDlg()
            .setTitle("日志保留天数")
            .setMessage("超过天数的旧日志文件会被自动删除。\\n\\n"
                    + "另有两条固定规则，不受这里影响：\\n"
                    + "· 单个日志文件超过 100MB → 截断保留后半段\\n"
                    + "· logs 目录总大小超过 100MB → 从最旧的文件开始删\\n\\n"
                    + "debug.log / crash.log 是当前正在写的文件，不按天数删。\\n\\n"
                    + "当前：" + (DiagLog.keepDays() <= 0
                        ? "不按时间清理" : ("保留 " + DiagLog.keepDays() + " 天")))
            .setPositiveButton("选择天数", new android.content.DialogInterface.OnClickListener(){
                public void onClick(android.content.DialogInterface dd, int w){
                    showKeepDaysList();
                }
            })
            .setNegativeButton("关闭", null)
            .create().show();
    }

    /** 第二步：纯列表弹窗（不带 message，避免列表被挤没） */
    void showKeepDaysList(){
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
            .setTitle("选择日志保留天数")
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
    }'''

if old not in s:
    print('FAIL 找不到 showKeepDaysDialog 原文')
    raise SystemExit(1)

s = s.replace(old, new, 1)
io.open(P, 'w', encoding='utf-8').write(s)
print('OK 日志保留天数已改成两步弹窗')