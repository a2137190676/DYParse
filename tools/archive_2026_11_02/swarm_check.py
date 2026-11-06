# -*- coding: utf-8 -*-
"""诊断：这个种子到底有没有活的 peer。"""
import urllib.request, socket, struct

IH = bytes.fromhex('E4F8ED9F17D7082B418BF17573ECB5E621A794B5')


def q(s):
    return ''.join('%%%02X' % b for b in s)


peers = []
trackers = [
    'http://tracker.opentrackr.org:1337/announce',
    'http://tracker.bittor.pw:1337/announce',
    'http://tracker.openbittorrent.com:80/announce',
]

for tr in trackers:
    try:
        url = (tr + '?info_hash=' + q(IH) + '&peer_id=-DY0001-123456789012'
               + '&port=6881&uploaded=0&downloaded=0&left=1000000&compact=1')
        r = urllib.request.urlopen(url, timeout=15)
        d = r.read()
        print('%-46s http=%d bytes=%d' % (tr, r.status, len(d)))
        i = d.find(b'5:peers')
        if i > 0:
            j = i + 7
            k = d.find(b':', j)
            n = int(d[j:k])
            blob = d[k + 1:k + 1 + n]
            print('   peers blob = %d bytes -> %d peers' % (n, n // 6))
            for x in range(0, n - 5, 6):
                ip = socket.inet_ntoa(blob[x:x + 4])
                port = struct.unpack('>H', blob[x + 4:x + 6])[0]
                peers.append((ip, port))
        ci = d.find(b'8:completei')
        if ci > 0:
            e = d.find(b'e', ci + 11)
            print('   seeds(complete) =', d[ci + 11:e].decode())
        ii = d.find(b'10:incompletei')
        if ii > 0:
            e = d.find(b'e', ii + 13)
            print('   leechers(incomplete) =', d[ii + 13:e].decode())
    except Exception as ex:
        print('%-46s ERR %s' % (tr, ex))

peers = list(dict.fromkeys(peers))
print('')
print('=== 去重后 %d 个 peer，逐个 TCP 探测 ===' % len(peers))
alive = 0
for ip, port in peers:
    s = socket.socket()
    s.settimeout(4)
    try:
        s.connect((ip, port))
        print('  ALIVE  %s:%d' % (ip, port))
        alive += 1
    except Exception as ex:
        print('  dead   %s:%d  (%s)' % (ip, port, type(ex).__name__))
    finally:
        try:
            s.close()
        except Exception:
            pass
print('')
print('存活 %d / %d' % (alive, len(peers)))