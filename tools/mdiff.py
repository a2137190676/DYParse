# -*- coding: utf-8 -*-
import io, re

W = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/MainActivity.java'
G = '/storage/emulated/0/Download/MainActivity.java'

def methods(p):
    d = io.open(p, 'r', encoding='utf-8', errors='replace').read()
    out = set()
    # 抓方法声明：修饰符 + 返回类型 + 名字(
    for m in re.finditer(r'^[ \t]*(?:public |private |protected |static |final |synchronized )*([A-Za-z_][\w\.<>\[\], ]*)\s+([A-Za-z_]\w*)\s*\(', d, re.M):
        out.add(m.group(2))
    return out, d

wm, wd = methods(W)
gm, gd = methods(G)

print('工作区方法数:', len(wm))
print('gitee 方法数:', len(gm))
only_g = sorted(gm - wm)
only_w = sorted(wm - gm)
print('\n=== 只有 gitee 有（工作区丢失）: %d 个 ===' % len(only_g))
for i in range(0, len(only_g), 6):
    print('  ' + ' | '.join(only_g[i:i+6]))
print('\n=== 只有工作区有（本轮新增）: %d 个 ===' % len(only_w))
for i in range(0, len(only_w), 6):
    print('  ' + ' | '.join(only_w[i:i+6]))