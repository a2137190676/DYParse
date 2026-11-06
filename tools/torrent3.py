# -*- coding: utf-8 -*-
"""修：输入框粘贴本地种子路径（/sdcard/.../x.torrent）时被判「未找到链接」。
extractUrl 只认 http(s)://...torrent，不认本地绝对路径。
"""
import io

MA = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/MainActivity.java'
m = io.open(MA, encoding='utf-8').read()
n = 0

# 1) extractUrl：本地 .torrent 绝对路径整段保留
old = '''        // .torrent 直链
        Matcher mt = Pattern.compile("https?://[A-Za-z0-9_./?=&%#:+-]+\\\\.torrent").matcher(t);
        if(mt.find()) return mt.group();'''
new = '''        // .torrent 直链
        Matcher mt = Pattern.compile("https?://[A-Za-z0-9_./?=&%#:+-]+\\\\.torrent").matcher(t);
        if(mt.find()) return mt.group();
        // 本地种子文件：/sdcard/.../x.torrent、/storage/emulated/0/.../x.torrent
        // （文件选择器或手动粘贴路径时走这里，不能当普通链接丢掉）
        Matcher ml = Pattern.compile("/(?:sdcard|storage|data|mnt)/[A-Za-z0-9_./\\\\u4e00-\\\\u9fa5 ()\\\\-]++\\\\.torrent",
                Pattern.CASE_INSENSITIVE).matcher(t);
        if(ml.find()) return ml.group().trim();'''
if old in m:
    m = m.replace(old, new, 1); n += 1; print('1) extractUrl 本地路径 OK')
else:
    print('MISS 1')

# 2) 剪贴板自动填也会被 extractUrl 拦，extractUrls 里补一条本地路径识别
old = '''            if(u != null && u.length() >= 10 && !out.contains(u)) out.add(u);'''
new = '''            if(u != null && u.length() >= 10 && !out.contains(u)) out.add(u);'''
n += 0

io.open(MA, 'w', encoding='utf-8').write(m)
print('changed:', n)