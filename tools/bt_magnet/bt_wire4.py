# -*- coding: utf-8 -*-
# 按行号插入磁力拦截（缩进不匹配导致字符串替换失败）
import io

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/MainActivity.java'
lines = io.open(P, encoding='utf-8').read().split('\n')

# 找 "未找到链接" 那一行
idx = None
for i, ln in enumerate(lines):
    if '未找到链接' in ln and 'url==null' in ln:
        idx = i
        break

print('找到行:', idx + 1 if idx is not None else None)
for k in range(idx, idx + 4):
    print(k + 1, repr(lines[k]))

# 在 idx+1 位置插入（即那行之后）
snippet = [
    '        // ===== 磁力 / BT 种子 =====',
    '        // 放在所有平台校验之前：磁力链接不走任何平台解析，直接交给 BT 引擎',
    '        if(BtDownloader.isMagnet(url)){',
    '            Log.i("DYP", "BT_PARSE " + url);',
    '            startMagnetDownload(url);',
    '            return;',
    '        }',
]

lines[idx + 1:idx + 1] = snippet
io.open(P, 'w', encoding='utf-8').write('\n'.join(lines))
print('插入 OK')

# 校验
d2 = io.open(P, encoding='utf-8').read()
print('BT_PARSE 出现:', d2.count('BT_PARSE'))
print('isMagnet 出现:', d2.count('isMagnet'))
i = d2.find('BT_PARSE')
print('--- 上下文 ---')
print(d2[i - 200:i + 260])