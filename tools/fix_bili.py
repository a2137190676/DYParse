# -*- coding: utf-8 -*-
import io

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/MainActivity.java'
d = io.open(P, encoding='utf-8').read()

bad = '''                                String epAid = (p.aid != null && p.xAid != null) ? p.xAid
                                        : (p.aid != null && p.aid.length() > 0) ? p.aid : v.aid;'''
good = '''                                String epAid = (p.aid != null && p.aid.length() > 0) ? p.aid : v.aid;'''
if bad in d:
    d = d.replace(bad, good, 1)
    io.open(P, 'w', encoding='utf-8').write(d)
    print('还原 epAid OK')
else:
    print('MISS')