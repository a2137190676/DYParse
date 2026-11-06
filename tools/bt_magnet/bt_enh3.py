# -*- coding: utf-8 -*-
# 补 exportTorrent + 边下边播探测
import io

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/BtDownloader.java'
d = io.open(P, encoding='utf-8').read()

anchor = '    /**\n     * 把公共 tracker 拼进磁力链接。'
if anchor not in d:
    anchor = '    static String withTrackers(String magnet) {'
    # 找它前面的注释开头
    i = d.find(anchor)
    j = d.rfind('    /**', 0, i)
    if j > 0:
        anchor = d[j:j+40]

method = '''    /**
     * 把 torrent 元数据导出成 .torrent 文件，跟资源存在一起。
     * 用户以后可以直接用这个种子重新下载（不用再找磁力）。
     */
    static File exportTorrent(TorrentInfo ti, File saveDir) {
        try {
            if (ti == null) return null;
            // TorrentInfo 内部就是 bencode，直接写出来就是合法 .torrent
            byte[] raw = ti.bencode();
            if (raw == null || raw.length == 0) return null;
            String name = ti.name();
            if (name == null || name.length() == 0) name = "torrent";
            // 文件名安全化
            name = name.replaceAll("[\\\\\\\\/:*?\\"<>|]", "_");
            if (name.length() > 60) name = name.substring(0, 60);
            File out = new File(saveDir, name + ".torrent");
            java.io.FileOutputStream fos = new java.io.FileOutputStream(out);
            try {
                fos.write(raw);
            } finally {
                try { fos.close(); } catch (Throwable t) {}
            }
            Log.i(TAG, "BT_TORRENT_EXPORTED " + out.getAbsolutePath() + " " + raw.length + "B");
            return out;
        } catch (Throwable t) {
            Log.i(TAG, "BT_TORRENT_EXPORT_ERR " + t);
            return null;
        }
    }

    /** 按扩展名判断是不是可直接播放的视频 */
    public static boolean isVideoFile(String path) {
        if (path == null) return false;
        String p = path.toLowerCase(Locale.US);
        return p.endsWith(".mp4") || p.endsWith(".mkv") || p.endsWith(".avi")
                || p.endsWith(".mov") || p.endsWith(".wmv") || p.endsWith(".flv")
                || p.endsWith(".webm") || p.endsWith(".m4v") || p.endsWith(".ts")
                || p.endsWith(".rmvb") || p.endsWith(".mpg") || p.endsWith(".mpeg");
    }

''' + anchor

if anchor in d:
    d = d.replace(anchor, method, 1)
    io.open(P, 'w', encoding='utf-8').write(d)
    print('exportTorrent + isVideoFile OK')
else:
    print('MISS anchor')

# 补 Cb 空实现基类，方便 MainActivity 只重写需要的
d2 = io.open(P, encoding='utf-8').read()
if 'class SimpleCb' not in d2:
    anchor2 = '    /**\n     * 启动一个磁力下载任务。'
    simple = '''    /** Cb 的空实现基类：MainActivity 只覆盖需要的几个 */
    public static abstract class SimpleCb implements Cb {
        @Override public void onMeta(List<String> files, List<Long> sizes, long totalBytes) {}
        @Override public void onProgress(long done, long total, long speedBps, int peers) {}
        @Override public void onDone(File saveDir) {}
        @Override public void onError(String msg) {}
        @Override public void onState(String state) {}
        @Override public int[] onSelectFiles(List<String> files, List<Long> sizes, long totalBytes) { return null; }
        @Override public void onTorrentSaved(File torrentPath) {}
        @Override public void onStreamable(File path) {}
    }

''' + anchor2
    if anchor2 in d2:
        d2 = d2.replace(anchor2, simple, 1)
        io.open(P, 'w', encoding='utf-8').write(d2)
        print('SimpleCb OK')
    else:
        print('MISS SimpleCb')
else:
    print('SimpleCb 已存在')