# -*- coding: utf-8 -*-
import io

B = '/storage/emulated/0/Download/MainActivity_broken_backup.java'
d = io.open(B, 'r', encoding='utf-8', errors='replace').read()

CHECKS = [
 ('磁力：progBusy', 'progBusy'),
 ('磁力：onSelectFiles 文件选择', 'onSelectFiles'),
 ('磁力：exportTorrent 调用', 'exportTorrent'),
 ('磁力：findCachedTorrent 调用', 'findCachedTorrent'),
 ('磁力：BT_CACHE_HIT', 'BT_CACHE_HIT'),
 ('磁力：setSequentialRange 边下边播', 'setSequentialRange'),
 ('磁力：prioritizeFiles', 'prioritizeFiles'),
 ('主题：themeMode', 'themeMode'),
 ('主题：themeButtonLabel', 'themeButtonLabel'),
 ('主题：showThemeDialog', 'showThemeDialog'),
 ('主题：KEY_THEME_MODE', 'KEY_THEME_MODE'),
 ('主题：onConfigurationChanged', 'onConfigurationChanged'),
 ('缩略图：loadNetThumb', 'loadNetThumb'),
 ('缩略图：loadThumb', 'loadThumb'),
 ('缩略图：thumbCache', 'thumbCache'),
 ('缩略图：makeThumbView', 'makeThumbView'),
 ('缩略图：videoFirstFrame', 'videoFirstFrame'),
 ('缩略图：decodeScaled', 'decodeScaled'),
 ('单下载：silent', 'silent'),
 ('单下载：dlSilentKeys', 'dlSilentKeys'),
 ('持久化：saveDlTasks', 'saveDlTasks'),
 ('持久化：loadDlTasks', 'loadDlTasks'),
 ('持久化：restoreDlTasks', 'restoreDlTasks'),
 ('平台目录：dlPlatDir', 'dlPlatDir'),
 ('平台目录：showDlPlatDialog', 'showDlPlatDialog'),
 ('平台目录：DL_PLATS', 'DL_PLATS'),
 ('并发：concToIndex', 'concToIndex'),
 ('并发：dlConcurrency', 'dlConcurrency'),
 ('并发：DL_CONC_MAX', 'DL_CONC_MAX'),
 ('腾讯视频：TxParser', 'TxParser'),
 ('微博：WB_UNWRAP', 'WB_UNWRAP'),
 ('TikTok：strArr', 'strArr'),
 ('TikTok：liveImages', 'liveImages'),
 ('抖音：sidTt', 'sidTt'),
 ('抖音：appendDyProfileItems', 'appendDyProfileItems'),
 ('抖音：refererFor douyinpic', 'douyinpic'),
 ('抖音：DYPROF_DUMP', 'DYPROF_DUMP'),
 ('抖音：buildSimpleQuery', 'buildSimpleQuery'),
]

for name, k in CHECKS:
    n = d.count(k)
    print('%-32s %s (n=%d)' % (name, 'OK ' if n else 'MISS', n))