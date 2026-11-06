# -*- coding: utf-8 -*-
import io

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/MainActivity.java'
d = io.open(P, encoding='utf-8').read()

old = '''        // 磁力 / BT 种子：直接交给 BtDownloader，不走平台解析
        if(BtDownloader.isMagnet(u)){
            startMagnetDownload(u);
            return;
        }'''
new = '''        // 磁力 / BT 种子：直接交给 BtDownloader，不走平台解析
        if(BtDownloader.isMagnet(url)){
            startMagnetDownload(url);
            return;
        }'''
if old in d:
    d = d.replace(old, new, 1)
    io.open(P, 'w', encoding='utf-8').write(d)
    print('参数名修正 OK')
else:
    print('MISS')