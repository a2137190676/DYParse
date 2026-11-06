# -*- coding: utf-8 -*-
import io, re

B = '/storage/emulated/0/Download/MainActivity_broken_backup.java'
d = io.open(B, 'r', encoding='utf-8', errors='replace').read()
lines = d.split('\n')

TARGETS = ['dlPlatDir', 'dlPlatKeyOf', 'dlPlatSummary', 'showDlPlatDialog',
           'concToIndex', 'dlConcurrency', 'showDlConcurrencyDialog',
           'platOf', 'themeMode', 'systemDark', 'isDarkMode', 'setThemeMode',
           'themeButtonLabel', 'showThemeDialog', 'saveDlTasks', 'loadDlTasks',
           'restoreDlTasks', 'clearDlTasks', 'runOneTask', 'enqueueSingle',
           'enqueueSingleWithAudio', 'onConfigurationChanged']

out = []
for i, ln in enumerate(lines):
    for t in TARGETS:
        if re.search(r'\b' + t + r'\s*\(', ln) and ('void ' in ln or 'String ' in ln or 'boolean ' in ln or 'int ' in ln or 'static ' in ln or 'public ' in ln or 'private ' in ln):
            out.append((i + 1, ln.strip()[:110]))
            break

seen = set()
for n, ln in out:
    k = ln[:60]
    if k in seen: continue
    seen.add(k)
    print('%5d | %s' % (n, ln))