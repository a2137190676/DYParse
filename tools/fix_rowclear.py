# -*- coding: utf-8 -*-
"""dlRowClear 里补 dlRowView.clear()"""
import io

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/MainActivity.java'
lines = io.open(P, encoding='utf-8').read().split('\n')

# 找 dlRowBar.clear() 那行，后面插一行
for i, ln in enumerate(lines):
    if 'dlRowBar.clear();' in ln and 'dlRowView' not in ln:
        indent = ln[:len(ln) - len(ln.lstrip())]
        lines.insert(i + 1, indent + 'dlRowView.clear();')
        print('插入到第', i + 2, '行，缩进=', repr(indent))
        break

io.open(P, 'w', encoding='utf-8').write('\n'.join(lines))
print('done')