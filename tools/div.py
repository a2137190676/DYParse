# -*- coding: utf-8 -*-
import io, difflib

W = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/MainActivity.java'
G = '/storage/emulated/0/Download/MainActivity.java'

w = io.open(W, 'r', encoding='utf-8', errors='replace').read().split('\n')
g = io.open(G, 'r', encoding='utf-8', errors='replace').read().split('\n')

print('工作区行数=%d  gitee行数=%d' % (len(w), len(g)))

# 找公共前缀
i = 0
while i < min(len(w), len(g)) and w[i] == g[i]:
    i += 1
print('公共前缀行数:', i)
print('  工作区第%d行: %r' % (i+1, w[i][:90] if i < len(w) else 'EOF'))
print('  gitee 第%d行: %r' % (i+1, g[i][:90] if i < len(g) else 'EOF'))

# 找公共后缀
j = 0
while j < min(len(w), len(g)) and w[-1-j] == g[-1-j]:
    j += 1
print('公共后缀行数:', j)

# 工作区中间段：从 i 到 len(w)-j
print('\n工作区中段: 第%d行 ~ 第%d行（共%d行）' % (i+1, len(w)-j, len(w)-j-i))
print('gitee 对应区间 第%d行 ~ 第%d行（共%d行）' % (i+1, len(g)-j, len(g)-j-i))

# 打印工作区中段的头尾
print('\n--- 工作区中段开头 5 行 ---')
for k in range(i, min(i+5, len(w)-j)):
    print('%5d| %s' % (k+1, w[k][:100]))
print('--- 工作区中段结尾 5 行 ---')
for k in range(max(i, len(w)-j-5), len(w)-j):
    print('%5d| %s' % (k+1, w[k][:100]))