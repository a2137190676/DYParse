# -*- coding: utf-8 -*-
"""
修：「下载保存位置」改成系统选择器后，没有「恢复默认」入口了。

【原因】
  之前：
      点设置行 → showDirPicker（自建选择器，底部自带「恢复默认」按钮）
  现在：
      点设置行 → pickDirWithSystem（系统选择器只有「取消/确定」，没地方放恢复默认）

【改法】
  加一层前置说明弹窗（两步）：
      ① 显示当前保存位置 + 「选择文件夹」「恢复默认」「关闭」
      ② 点「选择文件夹」才进系统选择器
  这样「恢复默认」回来了，用户也能先看到自己现在存在哪。

  同时把平台目录的说明弹窗也统一成这个风格（本来就有恢复默认，保持一致）。
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
# 1. 设置行改成弹说明窗
# ============================================================
rep('1-设置行',
    '''        addSettingRow("下载保存位置",
            "所有平台的文件都放进这个文件夹，点这里选",
            new View.OnClickListener(){
            public void onClick(View v){
                String def = new java.io.File(
                        android.os.Environment.getExternalStorageDirectory(),
                        "Download/DYParse").getAbsolutePath();
                // 首选系统选择器（能返回上一级、能看全盘）
                pickDirWithSystem(KEY_DL_PATH, def, "选择下载保存位置");
            }});''',
    '''        addSettingRow("下载保存位置", dlRootLabel(),
            new View.OnClickListener(){
            public void onClick(View v){ showDlRootDialog(); }});''')

# ============================================================
# 2. 新增：当前路径副标题 + 说明弹窗（含恢复默认）
# ============================================================
anchor = '''    /** 打开系统目录选择器（SAF）。'''

helper = '''    /** 「下载保存位置」的副标题：显示当前实际路径 + 是不是自定义 */
    String dlRootLabel(){
        String def = new java.io.File(
                android.os.Environment.getExternalStorageDirectory(),
                "Download/DYParse").getAbsolutePath();
        String cur = getSharedPreferences(SP, MODE_PRIVATE)
                .getString(KEY_DL_PATH, "");
        boolean custom = cur != null && cur.length() > 0;
        String shown = custom ? cur : def;
        return (custom ? "当前（自定义）：" : "当前（默认）：") + "\\n" + shown;
    }

    /** 下载保存位置：先说明弹窗（含「恢复默认」），再进系统选择器。
     *
     *  为什么要有这一层：
     *      系统选择器（SAF）只有「取消 / 确定」，没地方放「恢复默认」。
     *      加这一层后：
     *        · 用户能先看到自己现在存在哪
     *        · 「恢复默认」有地方放了
     */
    void showDlRootDialog(){
        curDark = isDarkMode();
        final String def = new java.io.File(
                android.os.Environment.getExternalStorageDirectory(),
                "Download/DYParse").getAbsolutePath();
        String cur = getSharedPreferences(SP, MODE_PRIVATE)
                .getString(KEY_DL_PATH, "");
        final boolean custom = cur != null && cur.length() > 0;
        String shown = custom ? cur : def;

        dypDlg()
            .setTitle("下载保存位置")
            .setMessage((custom ? "当前（自定义）：\\n" : "当前（默认）：\\n") + shown
                    + "\\n\\n默认位置：\\n" + def
                    + "\\n\\n所有平台的文件都会放进这个文件夹，"
                    + "里面再按「平台 / 视频·图片·动图·音频·合集」自动分子目录。")
            .setPositiveButton("选择文件夹", new android.content.DialogInterface.OnClickListener(){
                public void onClick(android.content.DialogInterface dd, int w){
                    pickDirWithSystem(KEY_DL_PATH, def, "选择下载保存位置");
                }
            })
            .setNeutralButton("恢复默认", new android.content.DialogInterface.OnClickListener(){
                public void onClick(android.content.DialogInterface dd, int w){
                    getSharedPreferences(SP, MODE_PRIVATE).edit()
                        .putString(KEY_DL_PATH, "").apply();
                    DypLog.i("DIR_PICK root -> (恢复默认)");
                    buildSettingsPage();
                    showHintToast("已恢复默认：" + def);
                }
            })
            .setNegativeButton("关闭", null)
            .create().show();
    }

    /** 打开系统目录选择器（SAF）。'''

if 'void showDlRootDialog()' not in s:
    s = s.replace(anchor, helper, 1)
    ok.append('2-说明弹窗')
else:
    ok.append('2-已存在')

io.open(P, 'w', encoding='utf-8').write(s)
print('成功:', ok)
print('失败:', miss)