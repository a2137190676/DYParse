# -*- coding: utf-8 -*-
# exportTorrent 最终方案：get_info_section() 拿原始 info 字典，手工拼 bencode 的 .torrent
#   .torrent 结构： d 8:announce <tracker> 4:info <info_dict> e
#   有了 info 字典（含 pieces），导出的是合法完整种子，可用于续传
import io

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/BtDownloader.java'
d = io.open(P, encoding='utf-8').read()

old = '''            // 用 create_torrent(torrent_info) 生成，再 to_string() 拿 bencode
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

new = '''            // 拿原始 info 字典（含 pieces），手工拼成完整 .torrent
            // 结构： d 4:info <info_dict> 8:announce <tracker> e
            byte[] infoSec = null;
            try {
                org.libtorrent4j.swig.byte_span bs = ti.swig().get_info_section();
                if (bs != null) {
                    int len = (int) bs.size();
                    if (len > 0) {
                        infoSec = new byte[len];
                        for (int i = 0; i < len; i++) infoSec[i] = bs.get(i);
                    }
                }
            } catch (Throwable t1) {
                Log.i(TAG, "BT_INFO_SECTION_ERR " + t1);
            }
            if (infoSec == null || infoSec.length == 0) {
                Log.i(TAG, "BT_INFO_SECTION_EMPTY");
                return null;
            }
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            try {
                bos.write("d4:info".getBytes("UTF-8"));
                bos.write(infoSec);
                // 附第一个 tracker，方便再次下载时能 announce
                String tr = EXTRA_TRACKERS.length > 0 ? EXTRA_TRACKERS[0] : "";
                if (tr.length() > 0) {
                    byte[] tb = tr.getBytes("UTF-8");
                    bos.write("8:announce".getBytes("UTF-8"));
                    bos.write(String.valueOf(tb.length).getBytes("UTF-8"));
                    bos.write(":".getBytes("UTF-8"));
                    bos.write(tb);
                }
                bos.write("e".getBytes("UTF-8"));
            } catch (Throwable t2) {
                Log.i(TAG, "BT_TORRENT_BUILD_ERR " + t2);
                return null;
            }
            byte[] raw = bos.toByteArray();
            if (raw == null || raw.length == 0) return null;'''

if old in d:
    d = d.replace(old, new, 1)
    io.open(P, 'w', encoding='utf-8').write(d)
    print('exportTorrent 用 get_info_section OK')
else:
    print('MISS')