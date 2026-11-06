# -*- coding: utf-8 -*-
# 简化版：不加 startFromTorrent，改为在 start() 里用 .torrent 替代 magnet 字符串
#   拿到本地 .torrent 就用 sm.download(ti, saveDir)，否则走磁力
import io

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/BtDownloader.java'
d = io.open(P, encoding='utf-8').read()

# 先回滚上一次加的 startFromTorrent 块（如果存在）
i = d.find('    /**\n     * 从本地 .torrent 文件直接开始下载（跳过 DHT 找元数据）。')
if i > 0:
    j = d.find('    /** 从 session 里按 infoHash 找 handle */', i)
    k = d.find('    /** Cb 的空实现基类', j)
    if j > 0 and k > j:
        d = d[:i] + d[k:]
        print('回滚 startFromTorrent OK')

# 现在改 start()：用 .torrent 优先
old = '''                // ---- 关键：先找本地已导出的 .torrent，有就直接用 ----
                // 这样重启 App 后再解析同一个磁力，不用再等 DHT 找元数据（几秒 vs 几十秒）
                File cachedTorrent = findCachedTorrent(saveDir);
                if (cachedTorrent != null) {
                    try {
                        Log.i(TAG, "BT_CACHE_HIT " + cachedTorrent.getAbsolutePath());
                        cb.onState("使用本地种子文件…");
                        startFromTorrent(cachedTorrent, saveDir, cb);
                        return;
                    } catch (Throwable te) {
                        Log.i(TAG, "BT_CACHE_USE_FAIL " + te);
                        // 失败就继续走磁力流程
                    }
                }

                cb.onState("获取种子信息…（磁力链接需要连 DHT，可能要 10~60 秒）");'''

new = '''                // ---- 关键：先找本地已导出的 .torrent，有就直接用 ----
                // 重启 App 后再解析同一个磁力，不用再等 DHT 找元数据（几秒 vs 几十秒）
                final File cachedTorrent = findCachedTorrent(saveDir);
                if (cachedTorrent != null) {
                    Log.i(TAG, "BT_CACHE_HIT " + cachedTorrent.getAbsolutePath());
                    cb.onState("使用本地种子文件…");
                } else {
                    cb.onState("获取种子信息…（磁力链接需要连 DHT，可能要 10~60 秒）");
                }'''

if old in d:
    d = d.replace(old, new, 1); print('缓存调用 OK')
else:
    print('MISS 缓存调用')

# 替换 sm.download 那段：有本地 torrent 优先
old2 = '''                if (magnet.startsWith("magnet:")) {
                    // 把公共 tracker 拼进 magnet，提速关键
                    String m2 = withTrackers(magnet);
                    Log.i(TAG, "BT_START_FULL " + m2);
                    sm.download(m2, saveDir, torrent_flags_t.from_int(0));
                } else {
                    // .torrent 文件
                    File tf = new File(magnet);
                    TorrentInfo ti = new TorrentInfo(tf);
                    sm.download(ti, saveDir);
                }'''

new2 = '''                boolean started = false;
                // 优先本地 .torrent（元数据已在本地，秒开）
                if (cachedTorrent != null) {
                    try {
                        TorrentInfo ti = new TorrentInfo(cachedTorrent);
                        Log.i(TAG, "BT_DL_BY_TORRENT " + cachedTorrent.getName()
                                + " files=" + ti.numFiles() + " total=" + ti.totalSize());
                        sm.download(ti, saveDir);
                        started = true;
                    } catch (Throwable te) {
                        Log.i(TAG, "BT_TORRENT_LOAD_FAIL " + te);
                    }
                }
                if (!started) {
                    if (magnet.startsWith("magnet:")) {
                        // 把公共 tracker 拼进 magnet，提速关键
                        String m2 = withTrackers(magnet);
                        Log.i(TAG, "BT_START_FULL " + m2);
                        sm.download(m2, saveDir, torrent_flags_t.from_int(0));
                    } else {
                        // .torrent 文件路径
                        File tf = new File(magnet);
                        TorrentInfo ti = new TorrentInfo(tf);
                        sm.download(ti, saveDir);
                    }
                }'''

if old2 in d:
    d = d.replace(old2, new2, 1); print('download 分支 OK')
else:
    print('MISS download 分支')

io.open(P, 'w', encoding='utf-8').write(d)
print('done')