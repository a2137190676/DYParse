# -*- coding: utf-8 -*-
# exportTorrent 改用 create_torrent(torrent_info).generate().to_string()
#   entry.to_string() 输出的是 bencode 文本，直接写文件就是合法 .torrent
import io

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/BtDownloader.java'
d = io.open(P, encoding='utf-8').read()

old = '''            // TorrentInfo 内部就是 bencode，直接写出来就是合法 .torrent
            byte[] raw = ti.bencode();
            if (raw == null || raw.length == 0) return null;'''

new = '''            // 用 create_torrent(torrent_info) 生成，再 to_string() 拿 bencode
            // 这样会保留原始 info 字典（含 piece hashes），导出的种子可用于续传
            String bencoded = null;
            try {
                org.libtorrent4j.swig.create_torrent ct =
                        new org.libtorrent4j.swig.create_torrent(ti.swig());
                org.libtorrent4j.swig.entry ent = ct.generate();
                bencoded = ent.to_string();
            } catch (Throwable t1) {
                Log.i(TAG, "BT_TORRENT_GEN_ERR " + t1);
            }
            if (bencoded == null || bencoded.length() == 0) return null;
            byte[] raw = bencoded.getBytes("UTF-8");
            if (raw == null || raw.length == 0) return null;'''

if old in d:
    d = d.replace(old, new, 1)
    io.open(P, 'w', encoding='utf-8').write(d)
    print('exportTorrent 改用 create_torrent OK')
else:
    print('MISS')

# 已经用了 raw，后面写文件那段不变
print('done')