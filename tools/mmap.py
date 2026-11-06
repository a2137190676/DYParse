# -*- coding: utf-8 -*-
import io, re

W = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/MainActivity.java'
d = io.open(W, 'r', encoding='utf-8', errors='replace').read()
lines = d.split('\n')

# 列出所有方法签名行（含行号）
print('=== 工作区方法分布 ===')
prev = 0
for i, ln in enumerate(lines):
    m = re.match(r'^[ \t]*(?:public |private |protected |static |final |synchronized )*[A-Za-z_][\w\.<>\[\], ]*\s+([A-Za-z_]\w*)\s*\(.*', ln)
    if m and i > 65 and i < len(lines) - 277:
        gap = i - prev
        flag = ' <<< 断层 %d 行' % gap if gap > 120 else ''
        print('%5d | %s%s' % (i + 1, ln.strip()[:70], flag))
        prev = i