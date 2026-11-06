# -*- coding: utf-8 -*-
import io

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/MainActivity.java'
d = io.open(P, encoding='utf-8').read()

old = '''        if(_dc == 1) _dcTip = "串行下载，最稳但最慢";
        else if(_dc <= 3) _dcTip = "均衡，流量与速度兼顾";
        else if(_dc <= 5) _dcTip = "较快，上传带宽会被占满";
        else _dcTip = "极快，可能被平台限速或手机发热";'''

new = '''        if(_dc == 1) _dcTip = "串行下载，最稳但最慢";
        else if(_dc <= 3) _dcTip = "均衡，流量与速度兼顾";
        else if(_dc <= 5) _dcTip = "较快，带宽会被占满";
        else if(_dc <= 8) _dcTip = "极快，可能被平台限速或手机发热";
        else _dcTip = "拉满，最快但耗电发热明显";'''

if old in d:
    d = d.replace(old, new, 1)
    io.open(P, 'w', encoding='utf-8').write(d)
    print('副标题 OK')
else:
    print('MISS')