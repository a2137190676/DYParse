# -*- coding: utf-8 -*-
# 用 CookieStore 存完整 Cookie（对齐现有架构），并补 dyCookieLoggedIn 字段
import io

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/MainActivity.java'
d = io.open(P, encoding='utf-8').read()
n = 0

# 1) 替换 saveDyCookieFull 调用为 CookieStore.save
old = '''            // ---- 关键修复：把完整 Cookie 持久化（之前从不写 KEY_DY_COOKIE）----
            if(cm != null && cm.length() > 40){
                saveDyCookieFull(cm, "probe:" + tag);
            }'''
new = '''            // ---- 关键修复：把完整 Cookie 落盘（CookieStore 是本地私有目录，不上传）----
            if(cm != null && cm.length() > 40 && loggedIn){
                try{
                    CookieStore.save(MainActivity.this, CookieStore.PLATFORM_DY, cm);
                    Log.i("DYP", "DY_CK_SAVED len=" + cm.length());
                }catch(Throwable tt){ Log.i("DYP", "DY_CK_SAVE_ERR " + tt); }
            }'''
if old in d:
    d = d.replace(old, new, 1); n += 1; print('CookieStore 落盘 OK')
else:
    print('MISS 落盘')

# 2) 补 dyCookieLoggedIn 字段（放在 curDark 后）
anchor = '    boolean curDark = false;'
if 'boolean dyCookieLoggedIn' not in d:
    d = d.replace(anchor, anchor + '\n    /** 本次登录流程里是否已检测到 sessionid（防重复提示） */\n    volatile boolean dyCookieLoggedIn = false;', 1)
    n += 1
    print('dyCookieLoggedIn 字段 OK')

io.open(P, 'w', encoding='utf-8').write(d)
print('changed:', n)