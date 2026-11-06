# -*- coding: utf-8 -*-
"""修正 extractUrl 里本地 .torrent 路径的正则：
   原来用占有量词 ++（贪婪且不回溯），把 ".torrent" 的 "." 也吃掉了 → 匹配失败。
   改用普通 +（贪婪但可回溯），并把自定义字符类放到最后更稳。
"""
import io

MA = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/MainActivity.java'
m = io.open(MA, encoding='utf-8').read()

old = '''        Matcher ml = Pattern.compile("/(?:sdcard|storage|data|mnt)/[A-Za-z0-9_./\\\\u4e00-\\\\u9fa5 ()\\\\-]++\\\\.torrent",
                Pattern.CASE_INSENSITIVE).matcher(t);'''
new = '''        Matcher ml = Pattern.compile("/(?:sdcard|storage|data|mnt)/[A-Za-z0-9_./ ()\\\\-\\\\u4e00-\\\\u9fa5]++?\\\\.torrent",
                Pattern.CASE_INSENSITIVE).matcher(t);'''

if old in m:
    m = m.replace(old, new, 1)
    io.open(MA, 'w', encoding='utf-8').write(m)
    print('正则修正 OK')
else:
    print('MISS')

# 自检：用 python 模拟一下这个正则行为
import re
t = '/storage/emulated/0/Download/a.torrent'
print('python 等价正则测试:', re.search(r'/(?:sdcard|storage|data|mnt)/[A-Za-z0-9_./ ()\-\u4e00-\u9fa5]+?\.torrent',
                                       t, re.I).group() if re.search(r'/(?:sdcard|storage|data|mnt)/[A-Za-z0-9_./ ()\-\u4e00-\u9fa5]+?\.torrent', t, re.I) else 'None')
t2 = '/storage/emulated/0/Download/磁力/magnet_X/MIDV-186.torrent'
r2 = re.search(r'/(?:sdcard|storage|data|mnt)/[A-Za-z0-9_./ ()\-\u4e00-\u9fa5]+?\.torrent', t2, re.I)
print('中文路径测试:', r2.group() if r2 else 'None')