# -*- coding: utf-8 -*-
# 找出 depth 异常增长的位置：在每个 "}" 之后，如果 depth 本应回到方法级(1)但没回，就是缺 }
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
prev_depth = 0
for k, raw in enumerate(lines):
    s = strip(raw)
    prev_depth = depth
    depth += s.count('{') - s.count('}')

# 重新走一遍：每行结束后如果 depth 突然变大很多，说明那里开了太多
depth = 0
for k, raw in enumerate(lines):
    s = strip(raw)
    before = depth
    depth += s.count('{') - s.count('}')
    # 一行内净增 >=2 且该行不是常见写法，可疑
    delta = depth - before
    if delta >= 2 and k < 400:
        print('%d| +%d  depth=%d  %s' % (k + 1, delta, depth, raw.strip()[:80]))

print('最终 depth =', depth)
print()
print('前 400 行 depth 采样:')
depth = 0
for k, raw in enumerate(lines):
    s = strip(raw)
    depth += s.count('{') - s.count('}')
    if k in (32, 100, 200, 300, 330, 367, 368, 400, 500, 600):
        print('  行%d depth=%d' % (k + 1, depth))