# -*- coding: utf-8 -*-
# Instagram 主页也补封面
import io

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/MainActivity.java'
d = io.open(P, encoding='utf-8').read()

old = '''                ProfileParser.ProfileItem it = new ProfileParser.ProfileItem();
                it.id = code;
                it.url = "https://www.instagram.com/p/" + code + "/";
                it.title = "Instagram " + code;
                synchronized(profileCollected){ profileCollected.add(it); }'''

new = '''                ProfileParser.ProfileItem it = new ProfileParser.ProfileItem();
                it.id = code;
                it.url = "https://www.instagram.com/p/" + code + "/";
                it.title = "Instagram " + code;
                // IG 缩略图可以靠 /p/<code>/media/?size=m 直接拿（无需登录）
                it.cover = "https://www.instagram.com/p/" + code + "/media/?size=m";
                synchronized(profileCollected){ profileCollected.add(it); }'''

if old in d:
    d = d.replace(old, new, 1)
    io.open(P, 'w', encoding='utf-8').write(d)
    print('INS 封面 OK')
else:
    print('MISS INS')