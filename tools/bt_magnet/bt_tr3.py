# -*- coding: utf-8 -*-
# 按行号插入 withTrackers（放在 handles 方法之前）
import io

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/BtDownloader.java'
lines = io.open(P, encoding='utf-8').read().split('\n')

# 找 "取当前 session 里所有 torrent handle" 那行的注释开头
idx = None
for i, ln in enumerate(lines):
    if '取当前 session 里所有 torrent handle' in ln:
        idx = i
        break

print('anchor at line', idx + 1)
print(repr(lines[idx]))

method = '''    /**
     * 把公共 tracker 拼进磁力链接。
     *
     * 磁力提速最关键的一步：magnet 自带 tr= 参数后，libtorrent 一开始就会去
     * announce，能立刻拿到大量 peer；否则只能靠 DHT 慢慢找，DHT 节点少时
     * 会长期卡在「获取种子信息」。
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
            if (magnet.indexOf(t) >= 0) continue;
            sb.append("&tr=").append(enc);
            added++;
        }
        Log.i(TAG, "BT_MAGNET_TRACKERS added=" + added);
        return sb.toString();
    }

'''

lines[idx:idx] = method.split('\n')
io.open(P, 'w', encoding='utf-8').write('\n'.join(lines))
print('插入 OK')

# 校验
d = io.open(P, encoding='utf-8').read()
print('withTrackers 次数:', d.count('withTrackers'))