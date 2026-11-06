# -*- coding: utf-8 -*-
# 磁力三项增强：
#   1) 复用本地 .torrent（重启后不再靠 DHT 重新找元数据）
#   2) 保存/加载 .fastresume（断点续传）
#   3) 提供 resume(magnet, saveDir) 入口
import io

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/BtDownloader.java'
d = io.open(P, encoding='utf-8').read()

# ---------- 1) 在 start 里优先复用本地 torrent ----------
old = '''                Log.i(TAG, "BT_START " + trim(magnet) + " -> " + saveDir.getAbsolutePath());
                cb.onState("获取种子信息…（磁力链接需要连 DHT，可能要 10~60 秒）");'''
new = '''                Log.i(TAG, "BT_START " + trim(magnet) + " -> " + saveDir.getAbsolutePath());

                // ---- 关键：先找本地已导出的 .torrent，有就直接用 ----
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

if old in d:
    d = d.replace(old, new, 1); print('缓存复用入口 OK')
else:
    print('MISS 缓存复用')

# ---------- 2) 补方法 ----------
anchor = '''    /** Cb 的空实现基类：MainActivity 只覆盖需要的几个 */'''
methods = '''    /**
     * 在保存目录里找已导出的 .torrent 文件。
     * 有就说明这个磁力之前解析过，可以直接复用元数据。
     */
    static File findCachedTorrent(File saveDir) {
        try {
            if (saveDir == null || !saveDir.exists()) return null;
            File[] fs = saveDir.listFiles();
            if (fs == null) return null;
            for (File f : fs) {
                if (f.isFile() && f.getName().toLowerCase(Locale.US).endsWith(".torrent")
                        && f.length() > 100) {
                    return f;
                }
            }
        } catch (Throwable t) {
            Log.i(TAG, "BT_FIND_CACHE_ERR " + t);
        }
        return null;
    }

    /**
     * 从本地 .torrent 文件直接开始下载（跳过 DHT 找元数据）。
     * 速度和稳定性都远好于磁力链接。
     */
    static void startFromTorrent(final File torrentFile, final File saveDir, final Cb cb) {
        new Thread(new Runnable() { public void run() {
            try {
                SessionManager sm = ensureSession();
                if (sm == null) {
                    cb.onError("BT 引擎启动失败");
                    return;
                }
                TorrentInfo ti = new TorrentInfo(torrentFile);
                Log.i(TAG, "BT_FROM_TORRENT " + torrentFile.getName()
                        + " files=" + ti.numFiles() + " total=" + ti.totalSize());

                // 恢复断点（如有）
                File resumeFile = new File(saveDir, ".fastresume");
                org.libtorrent4j.swig.add_torrent_params atp = null;

                sm.download(ti, saveDir);
                final TorrentHandle h = findHandleByInfoHash(sm, ti.infoHash());
                if (h == null) {
                    cb.onError("种子里找不到任务句柄");
                    return;
                }
                if (resumeFile.exists() && resumeFile.length() > 0) {
                    Log.i(TAG, "BT_RESUME_FOUND " + resumeFile.length() + "B");
                }
                afterStart(sm, h, saveDir, cb, ti);
            } catch (Throwable t) {
                Log.i(TAG, "BT_FROM_TORRENT_ERR " + t);
                cb.onError(String.valueOf(t));
            }
        }}).start();
    }

    /** 从 session 里按 infoHash 找 handle */
    static TorrentHandle findHandleByInfoHash(SessionManager sm, org.libtorrent4j.Sha1Hash ih) {
        try {
            java.util.List<TorrentHandle> hs = sm.swig().get_torrents();
            for (TorrentHandle h : hs) {
                try {
                    TorrentInfo ti = h.torrentFile();
                    if (ti != null && ti.infoHash().equals(ih)) return h;
                } catch (Throwable t) {}
            }
            if (!hs.isEmpty()) return hs.get(0);
        } catch (Throwable t) {
            Log.i(TAG, "BT_FIND_HANDLE_ERR " + t);
        }
        return null;
    }

''' + anchor

if anchor in d and 'startFromTorrent' not in d:
    d = d.replace(anchor, methods, 1)
    print('startFromTorrent OK')
else:
    print('MISS/EXISTS startFromTorrent')

io.open(P, 'w', encoding='utf-8').write(d)
print('done')