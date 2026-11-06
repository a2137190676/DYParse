# -*- coding: utf-8 -*-
# 并发上限 8 -> 16
import io

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/MainActivity.java'
d = io.open(P, encoding='utf-8').read()
n = 0

# 1) 上限常量
old = '''    static final int DL_CONC_MAX = 8;'''
new = '''    static final int DL_CONC_MAX = 16;'''
if old in d:
    d = d.replace(old, new, 1); n += 1; print('上限 16 OK')
else:
    print('MISS 上限')

# 2) 弹窗档位列表
i = d.find('void showDlConcurrencyDialog()')
j = d.find('final int cur = dlConcurrency();', i)
print('--- 弹窗段 ---')
print(d[j:j+1600])