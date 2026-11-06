# -*- coding: utf-8 -*-
"""诊断 tracker announce：看返回的 peer 数量、是否包含自己、event 处理差异。"""
import urllib.request, socket, struct

IH = bytes.fromhex('AE38C22B7953FDF9869529E43B9C0267D3465EB6')
MY_IP = '223.104.195.73'


def enc(b):
    return ''.join('%%%02X' % x for x in b)


def parse(d):
    """返回 (seeds, leech, peers list)"""
    seeds = leech = -1
    peers = []
    ci = d.find(b'8:completei')
    if ci > 0:
        seeds = int(d[ci + 11:d.find(b'e', ci + 11)])
    ii = d.find(b'10:incompletei')
    if ii > 0:
        leech = int(d[ii + 13:d.find(b'e', ii + 13)])
    i = d.find(b'5:peers')
    if i > 0:
        j = i + 7
        k = d.find(b':', j)
        n = int(d[j:k])
        blob = d[k + 1:k + 1 + n]
        for x in range(0, n - 5, 6):
            peers.append((socket.inet_ntoa(blob[x:x + 4]),
                          struct.unpack('>H', blob[x + 4:x + 6])[0]))
    return seeds, leech, peers


trackers = [
    'http://tracker.opentrackr.org:1337/announce',
    'http://tracker.bittor.pw:1337/announce',
    'http://tracker.skyts.net:6969/announce',
    'http://tracker.opentrackr.com:1337/announce',
    'http://open.acgtracker.com:1096/announce',
    'http://t.acg.rip:6699/announce',
    'http://tr.bangumi.moe:6969/announce',
    'http://tracker.moeking.me:6969/announce',
    'http://tracker.openbittorrent.com:80/announce',
    'http://bt.unionpeer.org:6969/announce',
]

print('本机出口 IP:', MY_IP)
print('')

total_peers = set()
for tr in trackers:
    # 用「started」事件，模拟真实客户端首次 announce，能拿到最多 peer
    url = (tr + '?info_hash=' + enc(IH)
           + '&peer_id=-DY0001-' + 'A' * 12
           + '&port=6881&uploaded=0&downloaded=0&left=1286311970'
           + '&compact=1&numwant=200&event=started&key=12345678')
    try:
        req = urllib.request.Request(url, headers={
            'User-Agent': 'Transmission/3.00',
            'Accept': '*/*',
        })
        r = urllib.request.urlopen(req, timeout=15)
        d = r.read()
        seeds, leech, peers = parse(d)
        self_in = any(p[0] == MY_IP for p in peers)
        print('%-42s 200 seeds=%-3d leech=%-3d peers=%-3d 含自己=%s'
              % (tr.split('/')[2], seeds, leech, len(peers), self_in))
        for p in peers:
            total_peers.add(p)
    except Exception as ex:
        msg = str(ex)[:40]
        print('%-42s FAIL %s' % (tr.split('/')[2], msg))

print('')
print('=== 全部 tracker 汇总：%d 个不重复 peer ===' % len(total_peers))
excl_self = [p for p in total_peers if p[0] != MY_IP]
print('排除自己后：%d 个' % len(excl_self))
print('')
print('=== 逐个 TCP 探测（排除自己）===')
alive = 0
for ip, port in sorted(excl_self):
    s = socket.socket()
    s.settimeout(6)
    try:
        s.connect((ip, port))
        print('  ALIVE %s:%d' % (ip, port))
        alive += 1
    except Exception as ex:
        print('  dead  %s:%d (%s)' % (ip, port, type(ex).__name__))
    finally:
        try:
            s.close()
        except Exception:
            pass
print('存活 %d / %d' % (alive, len(excl_self)))