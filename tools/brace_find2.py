# -*- coding: utf-8 -*-
# 找 depth 回到 1 之前，最后一次异常增长的位置
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
last_one = 0
for k, raw in enumerate(lines):
    s = strip(raw)
    before = depth
    depth += s.count('{') - s.count('}')
    if depth == 1 and before != 1:
        last_one = k + 1
    # 4781 前最后一个 depth==1 的行
    if k + 1 >= 4700 and k + 1 <= 4800:
        print('%d| depth=%d | %s' % (k + 1, depth, raw.strip()[:60]))

print()
print('最后一次 depth==1 的行:', last_one)
print('最终 depth =', depth)