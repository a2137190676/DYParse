# -*- coding: utf-8 -*-
"""测新磁力 AE38C22B... 的 swarm 是否活着。"""
import urllib.request, socket, struct

IH_HEX = 'AE38C22B7953FDF9869529E43B9C0267D3465EB6'


def q(s):
    return ''.join('%%%02X' % b for b in s)


ih = bytes.fromhex(IH_HEX)
peers = []
trackers = [
    'http://tracker.opentrackr.org:1337/announce',
    'http://tracker.bittor.pw:1337/announce',
    'http://open.acgtracker.com:1096/announce',
    'http://tracker.skyts.net:6969/announce',
    'http://tracker.opentrackr.com:1337/announce',
]

for tr in trackers:
    try:
        url = (tr + '?info_hash=' + q(ih) + '&peer_id=-DY0001-123456789012'
               + '&port=6881&uploaded=0&downloaded=0&left=1000000&compact=1')
        r = urllib.request.urlopen(url, timeout=15)
        d = r.read()
        i = d.find(b'5:peers')
        n_p = 0
        if i > 0:
            j = i + 7
            k = d.find(b':', j)
            n = int(d[j:k])
            blob = d[k + 1:k + 1 + n]
            n_p = n // 6
            for x in range(0, n - 5, 6):
                ip = socket.inet_ntoa(blob[x:x + 4])
                port = struct.unpack('>H', blob[x + 4:x + 6])[0]
                peers.append((ip, port))
        ci = d.find(b'8:completei')
        seeds = d[ci + 11:d.find(b'e', ci + 11)].decode() if ci > 0 else '?'
        ii = d.find(b'10:incompletei')
        leech = d[ii + 13:d.find(b'e', ii + 13)].decode() if ii > 0 else '?'
        print('%-44s 200 seeds=%s leech=%s peers=%d'
              % (tr.replace('http://', ''), seeds, leech, n_p))
    except Exception as ex:
        print('%-44s ERR %s' % (tr.replace('http://', ''), ex))

peers = list(dict.fromkeys(peers))
print('')
print('=== 去重 %d 个 peer，TCP 探测 ===' % len(peers))
alive = 0
for ip, port in peers:
    s = socket.socket()
    s.settimeout(5)
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
print('')
print('存活 %d / %d' % (alive, len(peers)))