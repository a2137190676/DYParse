# -*- coding: utf-8 -*-
"""
「各平台下载路径」的二级弹窗也改成目录选择器。

现状：showDlPlatEditDialog 是一个 EditText，用户要手打绝对路径。
改成：两步弹窗
    ① 说明 + 「选择目录 / 恢复默认」两个按钮
    ② 走 showDirPicker
（用户之前明确要求：不要 setMessage + setItems 混用，列表项会被挤掉）

同时把 dlDirFor 里「分类目录」的设置入口也接上选择器（如果存在）。
"""
import io

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/MainActivity.java'
s = io.open(P, encoding='utf-8').read()

# 先把整个 showDlPlatEditDialog 找出来看看边界
start_marker = '''    /** 修改某个平台的根目录 */
    void showDlPlatEditDialog(final String plat){'''
i = s.find(start_marker)
if i < 0:
    print('FAIL 找不到 showDlPlatEditDialog')
    raise SystemExit(1)

# 往下找第一个 "\n    }\n\n" 结束（这个方法内部嵌套不多，用括号深度更稳）
depth = 0
j = i
started = False
while j < len(s):
    c = s[j]
    if c == '{':
        depth += 1
        started = True
    elif c == '}':
        depth -= 1
        if started and depth == 0:
            j += 1
            break
    j += 1

old_block = s[i:j]

new_block = '''    /** 修改某个平台的根目录（改成选择目录，不再手打路径） */
    void showDlPlatEditDialog(final String plat){
        curDark = isDarkMode();
        final String key = "dl_dir_" + dlPlatKeyOf(plat);
        final String def = new java.io.File(dlRootDir(), plat).getAbsolutePath();
        String cur = getSharedPreferences(SP, MODE_PRIVATE).getString(key, "");
        final boolean custom = cur != null && cur.length() > 0;
        final String shown = custom ? cur : def;

        // 两步弹窗：先说明，再进目录选择器。
        // 不用 setMessage + setItems 混写 —— 列表项会把 message 挤没。
        dypDlg()
            .setTitle("「" + plat + "」下载目录")
            .setMessage((custom ? "当前（自定义）：\\n" : "当前（默认）：\\n") + shown
                    + "\\n\\n"
                    + "这个目录下的「视频 / 图片 / 动图 / 音频 / 合集」由程序自动再分子目录。")
            .setPositiveButton("选择目录", new android.content.DialogInterface.OnClickListener(){
                public void onClick(android.content.DialogInterface dd, int w){
                    showDirPicker(key, def, "选择「" + plat + "」目录");
                }
            })
            .setNeutralButton("恢复默认", new android.content.DialogInterface.OnClickListener(){
                public void onClick(android.content.DialogInterface dd, int w){
                    getSharedPreferences(SP, MODE_PRIVATE).edit()
                        .putString(key, "").apply();
                    Log.i("DYP", "DIR_PICK plat=" + plat + " -> (恢复默认)");
                    buildSettingsPage();
                    Toast.makeText(MainActivity.this,
                        plat + " 已恢复默认目录", Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("取消", null)
            .create().show();
    }'''

s = s[:i] + new_block + s[j:]
io.open(P, 'w', encoding='utf-8').write(s)
print('OK showDlPlatEditDialog 已改为目录选择器')