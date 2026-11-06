# -*- coding: utf-8 -*-
# 1) switchTab 里记录当前页
# 2) onConfigurationChanged：跟随系统时跟着变
import io

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/MainActivity.java'
d = io.open(P, encoding='utf-8').read()
n = 0

# 1) switchTab 记录
old = '''    void switchTab(int idx){
        try{'''
new = '''    void switchTab(int idx){
        settingsOrCurrentTab = idx;
        try{'''
if old in d:
    d = d.replace(old, new, 1); n += 1; print('switchTab 记录 OK')
else:
    print('MISS switchTab')

# 2) onConfigurationChanged
anchor = '''    @Override
    protected void onResume(){'''
oc = '''    @Override
    public void onConfigurationChanged(android.content.res.Configuration cfg){
        super.onConfigurationChanged(cfg);
        // 跟随系统时，系统切深浅色要跟着变
        try{
            if (THEME_FOLLOW.equals(themeMode())){
                boolean now = systemDark();
                if (now != curDark){
                    Log.i("DYP", "THEME_FOLLOW_CHANGED dark=" + now);
                    curDark = now;
                    applyTheme(curDark);
                    refreshThemeDependents();
                } else {
                    // 即使没变，也刷新一下页签（字体/尺寸可能变了）
                    refreshThemeDependents();
                }
            } else {
                // 固定模式：系统变了但我们要保持，重新套一次
                applyTheme(curDark);
            }
        }catch(Throwable t){
            Log.i("DYP", "THEME_CFG_ERR " + t);
        }
    }

''' + anchor
if anchor in d and 'onConfigurationChanged' not in d:
    d = d.replace(anchor, oc, 1); n += 1; print('onConfigurationChanged OK')
else:
    print('MISS/EXISTS onConfig')

io.open(P, 'w', encoding='utf-8').write(d)
print('changed:', n)