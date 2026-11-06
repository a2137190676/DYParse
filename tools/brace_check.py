# -*- coding: utf-8 -*-
# 逐行统计 { } 平衡，找出类提前闭合的位置
import io, re

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/MainActivity.java'
lines = io.open(P, encoding='utf-8').read().split('\n')

depth = 0
class_start = None
for k, raw in enumerate(lines):
    # 去掉字符串与注释（简化：只处理 " 和 //）
    s = raw
    # 去行注释
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
    s = ''.join(out)

    if 'class MainActivity' in s:
        class_start = k
    depth += s.count('{') - s.count('}')

    if class_start is not None and k == class_start:
        print('类起始行 %d，depth=%d' % (k + 1, depth))
    # 类开始后 depth 回到 0 就是类结束
    if class_start is not None and k > class_start and depth == 0:
        print('!! 类在第 %d 行闭合（异常，太早）: %s' % (k + 1, raw.strip()[:70]))
        # 打印附近
        for m in range(max(0, k - 6), min(len(lines), k + 3)):
            print('   %d| %s' % (m + 1, lines[m]))
        break

print('最终 depth =', depth)