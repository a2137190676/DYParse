# -*- coding: utf-8 -*-
# 补回两个缺失的 }：365 行只有一个 }，需要 3 个（关 ifB / ifA / try）
import io

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/MainActivity.java'
lines = io.open(P, encoding='utf-8').read().split('\n')

# 定位 365 行附近（}}); 之后的那一行）
target = None
for k in range(350, 380):
    if lines[k].strip() == '}});':
        target = k + 1
        break

print('}}); 行:', target, '内容:', repr(lines[target]) if target else 'MISS')

if target is not None and lines[target].strip() == '}':
    # 替换成 3 个 }
    lines[target] = '                }\n            }\n        }'
    io.open(P, 'w', encoding='utf-8').write('\n'.join(lines))
    print('补齐 } OK')
else:
    print('MISS')

# 校验
d = '\n'.join(lines)
i = d.find('YT_CK_GRAB_ERR')
print('--- 校验 ---')
print(d[max(0, i-300):i+120])