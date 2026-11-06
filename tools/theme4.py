# -*- coding: utf-8 -*-
import io

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/MainActivity.java'
d = io.open(P, encoding='utf-8').read()

# 找一个确定存在的 anchor
anchor = None
for cand in ['    protected void onCreate(Bundle savedInstanceState){',
             '    public void onBackPressed(){',
             '    protected void onDestroy(){']:
    if cand in d:
        anchor = cand
        break

print('anchor:', anchor)

oc = '''    @Override
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

'''

if anchor and 'public void onConfigurationChanged' not in d:
    d = d.replace(anchor, oc + anchor, 1)
    io.open(P, 'w', encoding='utf-8').write(d)
    print('onConfigurationChanged 插入 OK')
else:
    print('MISS or exists')

# Manifest 加 uiMode
P2 = '/storage/emulated/0/MT2/mcp/DYParse/AndroidManifest.xml'
m = io.open(P2, encoding='utf-8').read()
old_m = 'android:configChanges="orientation|screenSize|keyboardHidden|screenLayout|smallestScreenSize"'
new_m = 'android:configChanges="orientation|screenSize|keyboardHidden|screenLayout|smallestScreenSize|uiMode"'
if old_m in m:
    m = m.replace(old_m, new_m)
    io.open(P2, 'w', encoding='utf-8').write(m)
    print('Manifest uiMode OK')
else:
    print('MISS Manifest')