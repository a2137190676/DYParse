# -*- coding: utf-8 -*-
import os, io

BASE = '/storage/emulated/0/MT2/mcp/DYParse/.backup/objects'
# 本轮改动特征
FEATS = ['themeMode', 'saveDlTasks', 'onSelectFiles', 'loadNetThumb',
         'dlPlatDir', 'progBusy', 'strArr', 'dlSilentKeys', 'setSequentialRange']
res = []
for root, dirs, files in os.walk(BASE):
    for fn in files:
        p = os.path.join(root, fn)
        try:
            sz = os.path.getsize(p)
            if sz < 300000: continue
            with io.open(p, 'rb') as f:
                if not f.read(30).startswith(b'package com.re.douyinparse;'):
                    continue
            d = io.open(p, 'r', encoding='utf-8', errors='replace').read()
            hits = [x for x in FEATS if x in d]
            if len(hits) >= 3:
                res.append((len(hits), d.count('\n'), sz, p, ','.join(hits)))
        except Exception:
            pass

res.sort(reverse=True)
print('含 3+ 特征的候选:', len(res))
for h, n, sz, p, f in res[:10]:
    print('特征=%d 行=%d 字节=%d %s' % (h, n, sz, f))
    print('   ', p)