# -*- coding: utf-8 -*-
"""对照实验：
  A) ubuntu-22.04 磁力（之前实测能下到 100%，作为「正常种子」基准）
  B) MIDV-186 磁力（用户报「拿不到东西」）
用同一方法拉 peer 列表 + 逐个 TCP 探测，看差异在哪。
"""
import urllib.request, socket, struct

def q(s):
    return ''.join('%%%02X' % b for b in s)

def probe(name, ih_hex):
    print('=' * 68)
    print('【%s】 infoHash=%s' % (name, ih_hex))
    ih = bytes.fromhex(ih_hex)
    peers = []
    trackers = [
        'http://tracker.opentrackr.org:1337/announce',
        'http://tracker.bittor.pw:1337/announce',
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
            print('  %-44s 200  seeds=%s leechers=%s peers=%d'
                  % (tr.replace('http://', ''), seeds, leech, n_p))
        except Exception as ex:
            print('  %-44s ERR %s' % (tr.replace('http://', ''), ex))
    peers = list(dict.fromkeys(peers))
    alive = 0
    for ip, port in peers:
        s = socket.socket()
        s.settimeout(5)
        try:
            s.connect((ip, port))
            print('    ALIVE %s:%d' % (ip, port))
            alive += 1
        except Exception as ex:
            print('    dead  %s:%d (%s)' % (ip, port, type(ex).__name__))
        finally:
            try:
                s.close()
            except Exception:
                pass
    print('  >>> 存活 %d / %d' % (alive, len(peers)))
    return alive, len(peers)


probe('A 对照组 ubuntu-22.04（之前能下完）',
      '2C6B6858F0E1D2C6F2E3A3E3F4E5A6B7C8D9E0F1')
print('')
probe('B 问题种子 MIDV-186',
      'E4F8ED9F17D7082B418BF17573ECB5E621A794B5')