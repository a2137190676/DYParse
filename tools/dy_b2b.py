# -*- coding: utf-8 -*-
import io

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/MainActivity.java'
d = io.open(P, encoding='utf-8').read()

if 'dyProfUseFullQuery' in d:
    print('已存在')
else:
    i = d.find('dyProfileSignedOwned')
    if i < 0:
        print('MISS anchor')
    else:
        j = d.rfind('\n', 0, i)
        ins = ('\n    /** true = 主页列表用完整指纹参数（默认 false，用极简参数） */'
               '\n    volatile boolean dyProfUseFullQuery = false;')
        d = d[:j] + ins + d[j:]
        io.open(P, 'w', encoding='utf-8').write(d)
        print('字段插入 OK')