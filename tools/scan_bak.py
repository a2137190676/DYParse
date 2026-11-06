# -*- coding: utf-8 -*-
import os, io, re

BASE = '/storage/emulated/0/MT2/mcp/DYParse/.backup/objects'
best = []

for root, dirs, files in os.walk(BASE):
    for fn in files:
        p = os.path.join(root, fn)
        try:
            sz = os.path.getsize(p)
            if sz < 300000:
                continue
            with io.open(p, 'rb') as f:
                head = f.read(64)
            if not head.startswith(b'package com.re.douyinparse;'):
                continue
            d = io.open(p, 'r', encoding='utf-8', errors='replace').read()
            n = d.count('\n')
            has = ('void progSet' in d) + ('appendDyProfileItems' in d) + ('parseKsProfile' in d) + ('onSelectFiles' in d) + ('themeMode' in d) + ('saveDlTasks' in d) + ('loadNetThumb' in d)
            best.append((n, sz, has, p))
        except Exception:
            pass

best.sort(reverse=True)
print('候选数:', len(best))
for n, sz, has, p in best[:12]:
    print('行数=%d 字节=%d 特征=%d %s' % (n, sz, has, p))
