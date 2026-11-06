# -*- coding: utf-8 -*-
import io

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/MainActivity.java'
lines = io.open(P, encoding='utf-8').read().split('\n')

# 1) 删除错误插入的两行（在 enterProfileMode 里）
out = []
removed = 0
for k, l in enumerate(lines):
    if l.strip() == '/** true = 主页列表用完整指纹参数（默认 false，用极简参数） */':
        out.append(None); removed += 1
        continue
    if l.strip() == 'volatile boolean dyProfUseFullQuery = false;':
        out.append(None); removed += 1
        continue
    out.append(l)
lines = [l for l in out if l is not None]
print('删除', removed, '行')

# 2) 插到 curDark 附近（确定的字段区）
d = '\n'.join(lines)
anchor = '    boolean curDark = false;'
if anchor in d and 'dyProfUseFullQuery' not in d:
    d = d.replace(anchor, anchor + '\n    /** true = 主页列表用完整指纹参数（默认 false，用极简参数） */\n    volatile boolean dyProfUseFullQuery = false;', 1)
    print('字段插入 OK')
elif 'dyProfUseFullQuery' in d:
    print('已存在')
else:
    print('MISS anchor')

io.open(P, 'w', encoding='utf-8').write(d)
print('done')