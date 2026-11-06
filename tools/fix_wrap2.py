# -*- coding: utf-8 -*-
# 列表行：btns 挂到 rightWrap，rightWrap 挂到 row
import io

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/MainActivity.java'
lines = io.open(P, encoding='utf-8').read().split('\n')

# 目标：14569+90-1 = 14658 附近
target = None
for k in range(14640, 14680):
    if lines[k].strip() == 'row.addView(btns);':
        target = k
        break

print('row.addView(btns) 行:', target + 1 if target is not None else 'MISS')

if target is not None:
    print('  上下文:')
    for k in range(target - 4, target + 3):
        print('   ', k + 1, repr(lines[k]))
    # 替换这两行
    lines[target] = '            rightWrap.addView(btns);'
    # 下一行 listWrap.addView(row) 之前插入 row.addView(rightWrap)
    if 'listWrap.addView(row);' in lines[target + 1]:
        lines.insert(target + 1, '            row.addView(rightWrap);')
        print('  插入 row.addView(rightWrap) OK')
    io.open(P, 'w', encoding='utf-8').write('\n'.join(lines))
    print('done')

d = '\n'.join(lines)
print('rightWrap 声明:', d.count('LinearLayout rightWrap = new LinearLayout(this);'))
print('row.addView(rightWrap):', d.count('row.addView(rightWrap)'))