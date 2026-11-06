# -*- coding: utf-8 -*-
# 关键修正：tracker 必须一开始就拼进 magnet URI（而不是拿到元数据后才加）
#   magnet 里带 tr= 参数，libtorrent 一开始就会去 announce，能快速拿到 peer
#   拿到元数据后仍保留 addTrackers 作为补充
import io

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/BtDownloader.java'
d = io.open(P, encoding='utf-8').read()

# 1) 加一个把 tracker 拼进 magnet 的方法
anchor = '    /** 取当前 session 里所有 torrent handle */'
method = '''    /**
     * 把公共 tracker 拼进磁力链接。
     *
     * 这是磁力提速最关键的一步：magnet 自带 tr= 参数后，libtorrent 一开始
     * 就会去 announce，能立刻拿到大量 peer；否则只能靠 DHT 慢慢找，
     * 遇到 DHT 节点少（20 个左右）时就会长期卡在「获取种子信息」。
     */
    static String withTrackers(String magnet) {
        if (magnet == null || !magnet.startsWith("magnet:")) return magnet;
        StringBuilder sb = new StringBuilder(magnet);
        int added = 0;
        for (String t : EXTRA_TRACKERS) {
            String enc;
            try {
                enc = java.net.URLEncoder.encode(t, "UTF-8");
            } catch (Throwable e) {
                enc = t;
            }
            // 已经带过就不重复加
            if (magnet.indexOf(enc) >= 0 || magnet.indexOf(t) >= 0) continue;
            sb.append("&tr=").append(enc);
            added++;
        }
        Log.i(TAG, "BT_MAGNET_TRACKERS added=" + added);
        return sb.toString();
    }

''' + anchor

if anchor in d:
    d = d.replace(anchor, method, 1)
    print('withTrackers 方法 OK')
else:
    print('MISS anchor')

# 2) download 调用时用它
old2 = '''                if (magnet.startsWith("magnet:")) {
                    sm.download(magnet, saveDir, torrent_flags_t.from_int(0));
                } else {'''
new2 = '''                if (magnet.startsWith("magnet:")) {
                    // 把公共 tracker 拼进 magnet，提速关键
                    String m2 = withTrackers(magnet);
                    Log.i(TAG, "BT_START_FULL " + m2);
                    sm.download(m2, saveDir, torrent_flags_t.from_int(0));
                } else {'''
d2 = io.open(P, encoding='utf-8').read()
if old2 in d2:
    d2 = d2.replace(old2, new2, 1)
    print('download 调用 OK')
else:
    print('MISS download')

io.open(P, 'w', encoding='utf-8').write(d2)
print('done')