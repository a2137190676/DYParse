# -*- coding: utf-8 -*-
import io, re

G = '/storage/emulated/0/Download/MainActivity.java'
gd = io.open(G, 'r', encoding='utf-8', errors='replace').read()
gl = gd.split('\n')

KEY = ['dlDirFor', 'dlRootDir', 'showDlDirDialog', 'curDlDirLabel', 'dlKeyOf',
       'dlCatCustom', 'saveDlPath', 'doDownload', 'enqueueDownload', 'runDownloadQueue',
       'runOneTask', 'progSet', 'progShow', 'progHide', 'onProgress', 'onState',
       'loadImage', 'render', 'appendDyProfileItems', 'parseKsProfile', 'parseBiliProfile',
       'refererFor', 'jarrUrls', 'isDarkMode', 'applyTheme', 'buildSettingsPage',
       'showSettings', 'onCreate', 'dypDlg', 'curDark', 'KEY_DARK', 'silent',
       'showUpdateDialog', 'checkDyCookie', 'appendMixItems']

print('=== Gitee 版关键符号位置 ===')
for k in KEY:
    hits = [i + 1 for i, ln in enumerate(gl) if re.search(r'\b' + re.escape(k) + r'\b', ln)]
    if hits:
        print('%-24s 首次=%-6d 次数=%d  示例: %s' % (k, hits[0], len(hits), gl[hits[0]-1].strip()[:80]))
    else:
        print('%-24s MISS' % k)