# -*- coding: utf-8 -*-
# 在 onCreate 前插入 onConfigurationChanged（1632 行之前）
import io

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/MainActivity.java'
lines = io.open(P, encoding='utf-8').read().split('\n')

target = None
for k in range(1620, 1645):
    if 'protected void onCreate(Bundle b){' in lines[k]:
        target = k
        break

print('onCreate 行:', target + 1 if target is not None else 'MISS')

if target is not None:
    # 向上找 @Override 行
    ins = target
    if '@Override' in lines[target - 1]:
        ins = target - 1
    block = '''    /** 系统配置变化（含深色模式切换）：跟随系统时实时同步 */
    @Override
    public void onConfigurationChanged(android.content.res.Configuration cfg){
        super.onConfigurationChanged(cfg);
        try{
            if (THEME_FOLLOW.equals(themeMode())){
                boolean now = systemDark();
                if (now != curDark){
                    Log.i("DYP", "THEME_FOLLOW_CHANGED dark=" + now);
                    curDark = now;
                    applyTheme(curDark);
                }
                refreshThemeDependents();
            } else {
                applyTheme(curDark);
            }
        }catch(Throwable t){
            Log.i("DYP", "THEME_CFG_ERR " + t);
        }
    }
'''.split('\n')
    lines = lines[:ins] + block + lines[ins:]
    io.open(P, 'w', encoding='utf-8').write('\n'.join(lines))
    print('插入 OK，位置', ins + 1)