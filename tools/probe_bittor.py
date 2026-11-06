# -*- coding: utf-8 -*-
"""深挖 tracker.bittor.pw —— 它是唯一返回 seeds=1 的 tracker。
猜测：国内 BT 客户端（比特球/比特彗星）的私有 tracker，可能对 peer_id 有白名单。
做法：用各种客户端 peer_id 前缀试探，看哪种能拿到 peers。
"""
import urllib.request, socket, struct, time

IH = bytes.fromhex('AE38C22B7953FDF9869529E43B9C0267D3465EB6')
TRACKERS = [
    'http://tracker.bittor.pw:1337/announce',
    'https://tracker.bittor.pw:1337/announce',
    'udp://tracker.bittor.pw:1337/announce',
]

# 各种客户端的 peer_id 前缀（BT 规范：-XX + 4位版本 + -）
PREFIXES = [
    ('-BT0001-', '通用 BitTorrent'),
    ('-BC0001-', 'BitComet 比特彗星'),
    ('-qB4660-', 'qBittorrent'),
    ('-TR3000-', 'Transmission'),
    ('-UT3550-', 'uTorrent'),
    ('-XL0012-', '迅雷 Xunlei'),
    ('-DE13F0-', 'Deluge'),
    ('-LT20A0-', 'libtorrent'),
    ('-DY0001-', '本 App'),
]


def enc(b):
    return ''.join('%%%02X' % x for x in b)


def parse(d):
    seeds = leech = -1
    peers = []
    try:
        ci = d.find(b'8:completei')
        if ci > 0:
            v = d[ci + 11:d.find(b'e', ci + 11)]
            if v.isdigit():
                seeds = int(v)
        ii = d.find(b'10:incompletei')
        if ii > 0:
            v = d[ii + 13:d.find(b'e', ii + 13)]
            if v.isdigit():
                leech = int(v)
        i = d.find(b'5:peers')
        if i > 0:
            j = i + 7
            k = d.find(b':', j)
            n = int(d[j:k])
            blob = d[k + 1:k + 1 + n]
            for x in range(0, n - 5, 6):
                peers.append((socket.inet_ntoa(blob[x:x + 4]),
                              struct.unpack('>H', blob[x + 4:x + 6])[0]))
    except Exception:
        pass
    return seeds, leech, peers


print('=' * 70)
print('目标 infoHash: AE38C22B7953FDF9869529E43B9C0267D3465EB6')
print('=' * 70)

for tr in TRACKERS:
    if tr.startswith('udp://'):
        continue   # UDP 单独测太复杂，先跳过
    print('')
    print('##### ' + tr)
    for pfx, cname in PREFIXES:
        pid = pfx + 'A' * (20 - len(pfx))
        pid = pid[:20]
        url = (tr + '?info_hash=' + enc(IH)
               + '&peer_id=' + urllib.parse.quote(pid)
               + '&port=6881&uploaded=0&downloaded=0&left=1286311970'
               + '&compact=1&numwant=200&event=started&key=12345678')
        try:
            req = urllib.request.Request(url, headers={
                'User-Agent': 'Transmission/3.00'})
            r = urllib.request.urlopen(req, timeout=12)
            d = r.read()
            seeds, leech, peers = parse(d)
            mark = ''
            if peers:
                mark = '  <<< 拿到 peer! ' + ', '.join('%s:%d' % p for p in peers[:8])
            print('  %-10s %-16s 200 len=%-4d seeds=%-3d leech=%-3d peers=%-3d%s'
                  % (pfx, cname, len(d), seeds, leech, len(peers), mark))
        except Exception as ex:
            print('  %-10s %-16s FAIL %s' % (pfx, cname, str(ex)[:40]))
        time.sleep(0.5)