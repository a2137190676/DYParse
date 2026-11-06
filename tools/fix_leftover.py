# -*- coding: utf-8 -*-
"""补回被 shell 吞掉的那行：leftover 赋值"""
import io

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/MainActivity.java'
s = io.open(P, encoding='utf-8').read()

old = '''                boolean leftover = false;
                if(leftover){'''
new = '''                boolean leftover = false;
                synchronized(downloadQueue){ leftover = (downloadQueue.size() > 0); }
                if(leftover){'''

if old in s:
    s = s.replace(old, new, 1)
    io.open(P, 'w', encoding='utf-8').write(s)
    print('leftover 赋值补回 OK')
else:
    print('MISS')