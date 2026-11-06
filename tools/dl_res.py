# -*- coding: utf-8 -*-
"""从损坏文件里提取下载页相关方法的残留片段"""
import io, re

B = '/storage/emulated/0/Download/MainActivity_broken_backup.java'
d = io.open(B, 'r', encoding='utf-8', errors='replace').read()
lines = d.split('\n')

# 找所有含 dlRow / dlSetOverall / dlRefreshHead / dlTaskList 的行
keys = ['dlRowAdd', 'dlRowUpdate', 'dlRowRename', 'dlRowClear', 'dlSetOverall',
        'dlRefreshHead', 'dlTaskList', 'dlHead', 'dlCount', 'dlTotalBar', 'btnDlCancel']
print('=== 残留引用 ===')
for i, ln in enumerate(lines):
    for k in keys:
        if re.search(r'\b' + k + r'\b', ln):
            print('%5d| %s' % (i + 1, ln.strip()[:120]))
            break