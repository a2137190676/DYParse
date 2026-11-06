# -*- coding: utf-8 -*-
import io

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/BtDownloader.java'
d = io.open(P, encoding='utf-8').read()

if 'File findCachedTorrent' in d:
    print('已存在')
else:
    anchor = '''    /** Cb 的空实现基类：MainActivity 只覆盖需要的几个 */'''
    m = '''    /**
     * 在保存目录里找已导出的 .torrent 文件。
     * 有就说明这个磁力之前解析过，可以直接复用元数据，不用再等 DHT。
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

''' + anchor
    if anchor in d:
        d = d.replace(anchor, m, 1)
        io.open(P, 'w', encoding='utf-8').write(d)
        print('findCachedTorrent OK')
    else:
        print('MISS anchor')