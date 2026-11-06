# -*- coding: utf-8 -*-
# 扫描 32..400 行，打印每行 depth 变化，找出 depth 不该 >1 的地方
import io

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/MainActivity.java'
lines = io.open(P, encoding='utf-8').read().split('\n')

def strip(s):
    q = False
    out = []
    i = 0
    while i < len(s):
        c = s[i]
        if c == '"' and (i == 0 or s[i-1] != '\\'):
            q = not q
            out.append(' ')
        elif c == '/' and i + 1 < len(s) and s[i+1] == '/' and not q:
            break
        elif q:
            out.append(' ')
        else:
            out.append(c)
        i += 1
    return ''.join(out)

depth = 0
for k, raw in enumerate(lines):
    if k < 31 or k > 420:
        continue
    s = strip(raw)
    before = depth
    depth += s.count('{') - s.count('}')
    if depth != before:
        print('%4d| %+d -> %d | %s' % (k + 1, depth - before, depth, raw.strip()[:70]))