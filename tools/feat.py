# -*- coding: utf-8 -*-
import io

G = '/storage/emulated/0/Download/MainActivity.java'
W = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/MainActivity.java'

FEATS = {
 '三态主题 themeMode': 'themeMode',
 '主题按钮 themeButtonLabel': 'themeButtonLabel',
 '磁力文件选择 onSelectFiles': 'onSelectFiles',
 '磁力种子导出 exportTorrent': 'exportTorrent',
 '磁力缓存 findCachedTorrent': 'findCachedTorrent',
 '边下边播 setSequentialRange': 'setSequentialRange',
 '并发 progBusy': 'progBusy',
 '缩略图 loadNetThumb': 'loadNetThumb',
 '缩略图 thumbCache': 'thumbCache',
 '任务持久化 saveDlTasks': 'saveDlTasks',
 '单下载 silent': 'silent',
 '腾讯视频 TxParser': 'TxParser',
 '微博 data 解包': 'WB_UNWRAP',
 'TikTok 实况 strArr': 'strArr',
 '抖音封面 cover': 'appendDyProfileItems',
 '登录 sessionid': 'sidTt',
 '平台目录 dlPlatDir': 'dlPlatDir',
 '主题弹窗 showThemeDialog': 'showThemeDialog',
 '并发弹窗 showDlConcurrencyDialog': 'showDlConcurrencyDialog',
 'PLATFORM_QS 汽水': 'PLATFORM_QS',
}

for name, path in (('GITEE', G), ('工作区', W)):
    d = io.open(path, 'r', encoding='utf-8', errors='replace').read()
    print('===== %s =====' % name)
    import re
    m = re.search(r'parseVer\s*=\s*(\d+)', d)
    print('parseVer:', m.group(1) if m else 'MISS')
    for k, v in FEATS.items():
        print('  %-24s %s' % (k, 'OK' if v in d else 'MISS'))
    print()