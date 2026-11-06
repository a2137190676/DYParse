# -*- coding: utf-8 -*-
# 在 startMagnetDownload(raw); return; } 之后插入腾讯视频分支
import io

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/MainActivity.java'
lines = io.open(P, encoding='utf-8').read().split('\n')

target = None
for k in range(10580, 10640):
    if 'startMagnetDownload(raw);' in lines[k]:
        target = k
        break

print('磁力拦截行:', target + 1 if target is not None else 'MISS')
if target is not None:
    for k in range(target - 2, target + 5):
        print('  ', k + 1, repr(lines[k]))

    block = '''         // ===== 腾讯视频 =====
         if(TxParser.isTxUrl(raw)){
             Log.i("DYP", "TX_PARSE " + raw);
             parseTencentVideo(raw);
             return;
         }'''.split('\n')

    # 在其后（跳过可能存在的 }）插入。找 target 后面第一个 "}" 行
    ins = target + 1
    for k in range(target + 1, target + 6):
        if lines[k].strip() == '}':
            ins = k + 1
            break
    lines = lines[:ins] + block + lines[ins:]
    io.open(P, 'w', encoding='utf-8').write('\n'.join(lines))
    print('插入位置:', ins + 1)