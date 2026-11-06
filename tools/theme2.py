# -*- coding: utf-8 -*-
# 1) 右上角按钮改成三态循环：自动 -> 夜 -> 日 -> 自动
# 2) 设置页加「主题」选项
# 3) 跟随系统时，系统主题变化要跟着变（onConfigurationChanged）
import io

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/MainActivity.java'
d = io.open(P, encoding='utf-8').read()
n = 0

# 1) 按钮三态循环
old = '''        btnTheme=(TextView)findViewById(R.id.btnTheme);
        if(btnTheme != null){
            btnTheme.setText(isDarkMode() ? "亮" : "暗");
            btnTheme.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){
                boolean d = !isDarkMode();
                applyTheme(d);
                btnTheme.setText(d ? "亮" : "暗");
                buildSettingsPage();
                Toast.makeText(MainActivity.this, d ? "已切换夜间" : "已切换日间", Toast.LENGTH_SHORT).show();
            }});
        }'''
new = '''        btnTheme=(TextView)findViewById(R.id.btnTheme);
        if(btnTheme != null){
            btnTheme.setText(themeButtonLabel());
            btnTheme.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){
                // 三态循环：自动 -> 夜 -> 日 -> 自动
                String cur = themeMode();
                String next;
                String msg;
                if (THEME_FOLLOW.equals(cur)) { next = THEME_DARK; msg = "夜间模式"; }
                else if (THEME_DARK.equals(cur)) { next = THEME_LIGHT; msg = "日间模式"; }
                else { next = THEME_FOLLOW; msg = "跟随系统"; }
                setThemeMode(next);
                Toast.makeText(MainActivity.this, "主题：" + msg, Toast.LENGTH_SHORT).show();
            }});
        }'''
if old in d:
    d = d.replace(old, new, 1); n += 1; print('按钮三态 OK')
else:
    print('MISS 按钮')

# 2) 设置页加主题项（放在「同时下载数」前面）
old2 = '''        addSettingRow("同时下载数", _dc + " / " + DL_CONC_MAX + " · " + _dcTip,'''
new2 = '''        // 主题：跟随系统 / 日间 / 夜间
        String _tm = themeMode();
        String _tmLabel = THEME_DARK.equals(_tm) ? "夜间模式"
                : (THEME_LIGHT.equals(_tm) ? "日间模式" : "跟随系统");
        String _tmSub = THEME_FOLLOW.equals(_tm)
                ? (_tmLabel + " · 当前系统为" + (systemDark() ? "深色" : "浅色"))
                : (_tmLabel + " · 固定不随系统变化");
        addSettingRow("主题", _tmSub,
            new View.OnClickListener(){
                public void onClick(View v){ showThemeDialog(); }});
        addSettingRow("同时下载数", _dc + " / " + DL_CONC_MAX + " · " + _dcTip,'''
if old2 in d:
    d = d.replace(old2, new2, 1); n += 1; print('设置项 OK')
else:
    print('MISS 设置项')

# 3) 加 showThemeDialog
anchor = '''    void showDlConcurrencyDialog(){'''
dlg = '''    /** 主题选择弹窗：跟随系统 / 日间 / 夜间 */
    void showThemeDialog(){
        curDark = isDarkMode();
        String cur = themeMode();
        final String[] keys = { THEME_FOLLOW, THEME_LIGHT, THEME_DARK };
        String[] items = {
            "跟随系统 · 白天浅色、晚上深色（推荐）",
            "日间模式 · 始终浅色",
            "夜间模式 · 始终深色"
        };
        int sel = THEME_DARK.equals(cur) ? 2 : (THEME_LIGHT.equals(cur) ? 1 : 0);
        dypDlg()
            .setTitle("主题")
            .setSingleChoiceItems(items, sel, new android.content.DialogInterface.OnClickListener(){
                public void onClick(android.content.DialogInterface dd, int which){
                    setThemeMode(keys[which]);
                    try{ dd.dismiss(); }catch(Throwable t){}
                }
            })
            .setNegativeButton("取消", null)
            .create().show();
    }

''' + anchor
if anchor in d and 'void showThemeDialog' not in d:
    d = d.replace(anchor, dlg, 1); n += 1; print('showThemeDialog OK')
else:
    print('MISS showThemeDialog')

io.open(P, 'w', encoding='utf-8').write(d)
print('changed:', n)