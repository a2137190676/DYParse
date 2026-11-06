# -*- coding: utf-8 -*-
import io

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/BtDownloader.java'
lines = io.open(P, encoding='utf-8').read().split('\n')

# 找到 cb.onState("获取种子信息… 这一行，在它前面插入 cachedTorrent 声明
target = None
for k in range(85, 100):
    if 'cb.onState(' in lines[k] and '获取种子信息' in lines[k]:
        target = k
        break

print('目标行:', target + 1 if target is not None else 'MISS')

if target is not None:
    block = '''                // 优先复用本地已导出的 .torrent：重启后秒开，不再等 DHT
                final File cachedTorrent = findCachedTorrent(saveDir);
                if (cachedTorrent != null) {
                    Log.i(TAG, "BT_CACHE_HIT " + cachedTorrent.getAbsolutePath());
                    lines_PLACEHOLDER
                }'''.split('\n')
    # 构造：把原 onState 行改成 if/else 两个分支
    stateLine = lines[target]
    # 原行内容作为 else 分支
    indent = '                '
    newLines = [
        indent + '// 优先复用本地已导出的 .torrent：重启后秒开，不再等 DHT',
        indent + 'final File cachedTorrent = findCachedTorrent(saveDir);',
        indent + 'if (cachedTorrent != null) {',
        indent + '    Log.i(TAG, "BT_CACHE_HIT " + cachedTorrent.getAbsolutePath());',
        indent + '    cb.onState("使用本地种子文件…");',
        indent + '} else {',
        '    ' + stateLine,
        indent + '}',
    ]
    lines = lines[:target] + newLines + lines[target + 1:]
    io.open(P, 'w', encoding='utf-8').write('\n'.join(lines))
    print('插入 OK')
    for l in newLines:
        print('  ' + l)