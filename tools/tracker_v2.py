# -*- coding: utf-8 -*-
"""诊断 tracker announce v2：容错解析 + 对比不同 event/numwant 参数的影响。"""
import urllib.request, socket, struct

IH = bytes.fromhex('AE38C22B7953FDF9869529E43B9C0267D3465EB6')
MY_IP = '223.104.195.73'


def enc(b):
    return ''.join('%%%02X' % x for x in b)


def parse(d):
    seeds = leech = -1
    peers = []
    try:
        ci = d.find(b'8:completei')
        if ci > 0:
            e = d.find(b'e', ci + 11)
            v = d[ci + 11:e]
            if v.isdigit():
                seeds = int(v)
        ii = d.find(b'10:incompletei')
        if ii > 0:
            e = d.find(b'e', ii + 13)
            v = d[ii + 13:e]
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
    except Exception as ex:
        print('    parse 警告:', ex)
    return seeds, leech, peers


trackers = [
    'http://tracker.opentrackr.org:1337/announce',
    'http://tracker.bittor.pw:1337/announce',
    'http://tracker.skyts.net:6969/announce',
]

# 对比三组参数：无 event / started / numwant 大
variants = [
    ('无event', '&compact=1'),
    ('started', '&compact=1&event=started&numwant=200&key=12345678'),
    ('numwant200', '&compact=1&numwant=200'),
]

all_peers = set()
for tr in trackers:
    print('')
    print('##### ' + tr)
    for name, extra in variants:
        url = (tr + '?info_hash=' + enc(IH)
               + '&peer_id=-DY0001-' + 'A' * 12
               + '&port=6881&uploaded=0&downloaded=0&left=1286311970'
               + extra)
        try:
            req = urllib.request.Request(url, headers={
                'User-Agent': 'Transmission/3.00'})
            r = urllib.request.urlopen(req, timeout=15)
            d = r.read()
            seeds, leech, peers = parse(d)
            self_in = any(p[0] == MY_IP for p in peers)
            print('  %-12s 200 len=%-4d seeds=%-3d leech=%-3d peers=%-3d 含自己=%s'
                  % (name, len(d), seeds, leech, len(peers), self_in))
            for p in peers:
                all_peers.add(p)
            if peers:
                print('     peers:', ', '.join('%s:%d' % p for p in peers[:10]))
        except Exception as ex:
            print('  %-12s FAIL %s' % (name, str(ex)[:50]))

print('')
print('=' * 60)
print('汇总 %d 个 peer，排除自己后 %d 个'
      % (len(all_peers), len([p for p in all_peers if p[0] != MY_IP])))
excl = sorted([p for p in all_peers if p[0] != MY_IP])
if excl:
    print('')
    print('=== 逐个 TCP 探测 ===')
    alive = 0
    for ip, port in excl:
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
    print('存活 %d / %d' % (alive, len(excl)))