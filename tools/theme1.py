# -*- coding: utf-8 -*-
# 三态主题：跟随系统 / 日间 / 夜间
#   dark_mode 改为字符串：system / light / dark
#   兼容旧值（boolean true/false）
import io

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/MainActivity.java'
d = io.open(P, encoding='utf-8').read()
n = 0

# 1) 常量
old = '''    static final String KEY_DARK = "dark_mode";'''
new = '''    static final String KEY_DARK = "dark_mode";
    /** 主题档位：follow=跟随系统, light=日间, dark=夜间 */
    static final String KEY_THEME_MODE = "theme_mode";
    static final String THEME_FOLLOW = "follow";
    static final String THEME_LIGHT = "light";
    static final String THEME_DARK = "dark";'''
if old in d:
    d = d.replace(old, new, 1); n += 1; print('常量 OK')
else:
    print('MISS 常量')

# 2) isDarkMode 改三态
old2 = '''    boolean isDarkMode(){
        try{ return getSharedPreferences(SP, MODE_PRIVATE).getBoolean(KEY_DARK, false); }
        catch(Throwable t){ return false; }
    }'''
new2 = '''    /** 当前主题档位 */
    String themeMode(){
        try{
            String m = getSharedPreferences(SP, MODE_PRIVATE).getString(KEY_THEME_MODE, null);
            if (m != null && m.length() > 0) return m;
            // 兼容旧版：只有 boolean 的 dark_mode
            boolean old = getSharedPreferences(SP, MODE_PRIVATE).getBoolean(KEY_DARK, false);
            return old ? THEME_DARK : THEME_LIGHT;
        }catch(Throwable t){ return THEME_FOLLOW; }
    }

    /** 系统当前是不是深色 */
    boolean systemDark(){
        try{
            int mode = getResources().getConfiguration().uiMode
                    & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
            return mode == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        }catch(Throwable t){ return false; }
    }

    /** 实际应该用深色还是浅色 */
    boolean isDarkMode(){
        try{
            String m = themeMode();
            if (THEME_DARK.equals(m)) return true;
            if (THEME_LIGHT.equals(m)) return false;
            // follow：看系统
            return systemDark();
        }catch(Throwable t){ return false; }
    }

    /** 设置主题档位并立即生效 */
    void setThemeMode(String mode){
        try{
            getSharedPreferences(SP, MODE_PRIVATE).edit()
                .putString(KEY_THEME_MODE, mode)
                .putBoolean(KEY_DARK, THEME_DARK.equals(mode))
                .apply();
        }catch(Throwable t){}
        curDark = isDarkMode();
        // 让系统重新走一遍主题应用
        applyTheme(curDark);
        // 通知栏/导航栏也跟上
        try{
            if (android.os.Build.VERSION.SDK_INT >= 21){
                getWindow().setStatusBarColor(curDark ? 0xFF14161A : 0xFFF7F8FA);
                getWindow().setNavigationBarColor(curDark ? 0xFF14161A : 0xFFFFFFFF);
            }
        }catch(Throwable t){}
        refreshThemeDependents();
    }

    /** 主题变了要重建的 UI（设置页、列表、历史都重绘） */
    void refreshThemeDependents(){
        try{ buildSettingsPage(); }catch(Throwable t){}
        try{ refreshHistory(); }catch(Throwable t){}
        try{ updateTabColors(); }catch(Throwable t){}
        try{ if(btnTheme != null) btnTheme.setText(themeButtonLabel()); }catch(Throwable t){}
    }

    /** 主题按钮上的字：跟随/日/夜 */
    String themeButtonLabel(){
        String m = themeMode();
        if (THEME_DARK.equals(m)) return "夜";
        if (THEME_LIGHT.equals(m)) return "日";
        return "自动";
    }'''
if old2 in d:
    d = d.replace(old2, new2, 1); n += 1; print('isDarkMode 三态 OK')
else:
    print('MISS isDarkMode')

io.open(P, 'w', encoding='utf-8').write(d)
print('changed:', n)