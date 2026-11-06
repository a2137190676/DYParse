# -*- coding: utf-8 -*-
"""把 124 个公开 tracker 全部写进 BtDownloader。

设计：
  ALL_TRACKERS    —— 124 个全量，拿到元数据后逐个 addTracker（不影响 magnet 长度）
  MAGNET_TRACKERS —— 精选 30 个，只用于拼进 magnet 链接的 tr= 参数
                     （magnet 太长会被部分 tracker 拒绝，也影响解析速度）

原来只有一个 EXTRA_TRACKERS（28 个），现在拆成两个常量。
"""
import io

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/BtDownloader.java'
s = io.open(P, encoding='utf-8').read()

# 读全量列表
raw = io.open('/storage/emulated/0/MT2/mcp/DYParse/tools/trackers_raw.txt',
              encoding='utf-8').read().split('\n')
allt = []
seen = set()
for line in raw:
    t = line.strip()
    if not t or t in seen:
        continue
    if not t.startswith(('http://', 'https://', 'udp://')):
        continue
    seen.add(t)
    allt.append(t)

print('全量 tracker:', len(allt))

# 精选：优先 HTTP/HTTPS（能穿普通代理），再补 UDP 里实测返回 peer 的
PRIORITY = [
    'http://tracker.opentrackr.org:1337/announce',
    'http://tracker.internetwarriors.net:1337/announce',
    'http://tracker2.itzmx.com:6961/announce',
    'http://tracker.qu.ax:6969/announce',
    'http://tracker.renfei.net:8080/announce',
    'http://open.tracker.cl:1337/announce',
    'http://004430.xyz:80/announce',
    'https://004430.xyz:443/announce',
    'https://1337.abcvg.info:443/announce',
    'https://tracker.nekomi.cn:443/announce',
    'https://tracker.pmman.tech:443/announce',
    'https://tracker.yume-hatsuyuki.moe:443/announce',
    'https://tracker.zhuqiy.com:443/announce',
    'udp://torrent.tracker.durukanbal.com:6969/announce',
    'udp://open.stealth.si:80/announce',
    'udp://tracker.opentrackr.org:1337/announce',
    'udp://open.tracker.cl:1337/announce',
    'udp://93.158.213.92:6969/announce',
    'udp://tracker2.itzmx.com:6961/announce',
    'udp://tracker3.itzmx.com:6961/announce',
    'udp://tracker4.itzmx.com:2710/announce',
    'udp://tracker.openbittorrent.com:6969/announce',
    'udp://exodus.desync.com:6969/announce',
    'udp://tracker.torrent.eu.org:451/announce',
    'udp://open.demonii.com:1337/announce',
    'udp://tracker.dler.org:6969/announce',
    'udp://explodie.org:6969/announce',
    'udp://tracker1.bt.moack.co.kr:80/announce',
]
magnet_list = [t for t in PRIORITY if t in seen]
# 补充剩余 HTTP/HTTPS 里没在精选中的
for t in allt:
    if len(magnet_list) >= 40:
        break
    if t.startswith(('http://', 'https://')) and t not in magnet_list:
        magnet_list.append(t)

print('magnet 用:', len(magnet_list))


def java_arr(name, items, indent='        '):
    out = []
    for t in items:
        esc = t.replace('\\', '\\\\').replace('"', '\\"')
        out.append('%s"%s",' % (indent, esc))
    return '\n'.join(out)


# 找旧的 EXTRA_TRACKERS 块整体替换
start = s.find('    /**\n     * 公共 tracker 列表。')
if start < 0:
    print('MISS 起点，用另一锚点')
    start = s.find('    private static final String[] EXTRA_TRACKERS')
end_marker = '    };\n'
end = s.find(end_marker, start)
if start < 0 or end < 0:
    raise SystemExit('找不到 EXTRA_TRACKERS 块')

new_block = '''    /**
     * 全量公共 tracker 列表（%d 个）。
     *
     * 来源：trackerslist.com 的 all + best 合并去重。
     * 用途：拿到种子元数据后逐个 addTracker。
     *
     * 为什么要全量：tracker 的效果是「跟着资源走」的——
     * 某条磁力在这几个 tracker 上没人，在另外几个上可能有人。
     * 只保留「实测对某个种子有效」的列表，等于把其他种子的活路堵死了。
     * tracker announce 失败只是超时，不会拖慢下载，所以全加没有副作用。
     */
    private static final String[] ALL_TRACKERS = new String[]{
%s
    };

    /**
     * 拼进 magnet 链接的精选列表（%d 个）。
     *
     * 为什么不把全量都拼进 magnet：magnet URI 太长会让部分 tracker
     * 直接拒绝请求，也拖慢首次拿到元数据的速度。
     * 这里只放 HTTP/HTTPS（走 TCP，普通代理也能穿）+ 少数实测最好用的 UDP。
     */
    private static final String[] MAGNET_TRACKERS = new String[]{
%s
    };
''' % (len(allt), java_arr('ALL_TRACKERS', allt), len(magnet_list),
       java_arr('MAGNET_TRACKERS', magnet_list))

s = s[:start] + new_block + s[end + len(end_marker):]

# withTrackers 改用 MAGNET_TRACKERS
s = s.replace(
    '''        for (String t : EXTRA_TRACKERS) {
            String enc;''',
    '''        for (String t : MAGNET_TRACKERS) {
            String enc;''', 1)

# addTracker 那段改用 ALL_TRACKERS
s = s.replace(
    '''                            for (String t : EXTRA_TRACKERS) tks.add(t);''',
    '''                            for (String t : ALL_TRACKERS) tks.add(t);''', 1)

# exportTorrent 里的 EXTRA_TRACKERS 引用也换掉
s = s.replace(
    '''            String tr = EXTRA_TRACKERS.length > 0 ? EXTRA_TRACKERS[0] : "";''',
    '''            String tr = MAGNET_TRACKERS.length > 0 ? MAGNET_TRACKERS[0] : "";''', 1)

io.open(P, 'w', encoding='utf-8').write(s)
print('已写入')
print('剩余 EXTRA_TRACKERS 引用数:', s.count('EXTRA_TRACKERS'))
