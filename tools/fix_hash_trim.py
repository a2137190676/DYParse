# -*- coding: utf-8 -*-
"""btHashOf：磁力链接可能被拼接过，只取前 40 位标准 BTIH-HEX 长度"""
import io

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/MainActivity.java'
lines = io.open(P, encoding='utf-8').read().split('\n')

for i, ln in enumerate(lines):
    if 'h = h.trim();' in ln:
        indent = ln[:len(ln) - len(ln.lstrip())]
        lines.insert(i + 1, indent + '// 磁力链接可能被拼接过（&dn 被吃），只取前 40 位标准 BTIH-HEX 长度')
        lines.insert(i + 2, indent + 'if(h.length() > 40) h = h.substring(0, 40);')
        print('插入到第', i + 2, '行')
        break

io.open(P, 'w', encoding='utf-8').write('\n'.join(lines))
print('done')