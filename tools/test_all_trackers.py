# -*- coding: utf-8 -*-
"""把 125 个 tracker 全部对目标磁力做 announce，找出能返回 peer 的。
   同时实现 HTTP announce 与 UDP announce（BEP-15）。
"""
import urllib.request, urllib.parse, socket, struct, random, time

IH = bytes.fromhex('AE38C22B7953FDF9869529E43B9C0267D3465EB6')
MY_IP = '223.104.195.73'
LEFT = 1286311970

with open('/storage/emulated/0/MT2/mcp/DYParse/tools/trackers_raw.txt') as f:
    TRACKERS = [l.strip() for l in f if l.strip()]

print('待测 tracker: %d 个' % len(TRACKERS))
print('')


def enc(b):
    return ''.join('%%%02X' % x for x in b)


def parse_http(d):
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
        # peers6
        i6 = d.find(b'6:peers6')
        if i6 > 0:
            j = i6 + 8
            k = d.find(b':', j)
            n = int(d[j:k])
            blob = d[k + 1:k + 1 + n]
            for x in range(0, n - 17, 18):
                ip = socket.inet_ntop(socket.AF_INET6, blob[x:x + 16])
                peers.append((ip, struct.unpack('>H', blob[x + 16:x + 18])[0]))
    except Exception:
        pass
    return seeds, leech, peers


def test_http(tr):
    pid = '-DY0001-' + 'A' * 12
    url = (tr + '?info_hash=' + enc(IH)
           + '&peer_id=' + urllib.parse.quote(pid)
           + '&port=6881&uploaded=0&downloaded=0&left=%d' % LEFT
           + '&compact=1&numwant=200&event=started&key=12345678')
    req = urllib.request.Request(url, headers={'User-Agent': 'Transmission/3.00'})
    r = urllib.request.urlopen(req, timeout=8)
    return parse_http(r.read())


def test_udp(tr):
    """BEP-15 UDP tracker announce"""
    body = tr[len('udp://'):]
    host, _, rest = body.partition(':')
    port_s, _, _ = rest.partition('/')
    port = int(port_s)
    addr = (socket.gethostbyname(host), port)
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.settimeout(6)
    conn_id = struct.unpack('>Q', b'\x00\x00\x04\x17\x27\x10\x19\x80')[0]
    txn = random.randint(0, 0x7FFFFFFF)
    pkt = struct.pack('>QII', conn_id, 0, txn)
    sock.sendto(pkt, addr)
    data, _ = sock.recvfrom(2048)
    if len(data) < 16:
        raise RuntimeError('short connect resp')
    _, rtxn = struct.unpack('>II', data[:8])
    if rtxn != txn:
        raise RuntimeError('txn mismatch')
    conn_id = struct.unpack('>Q', data[8:16])[0]
    # announce
    txn2 = random.randint(0, 0x7FFFFFFF)
    pkt2 = (struct.pack('>QII', conn_id, 1, txn2) + IH
            + b'-DY0001-AAAAAAAAAAAA'
            + struct.pack('>QQQIIIiH', 0, 0, LEFT, 2, 0, random.randint(0, 65535),
                          -1, 200))
    sock.sendto(pkt2, addr)
    data2, _ = sock.recvfrom(4096)
    sock.close()
    if len(data2) < 20:
        raise RuntimeError('short announce resp')
    act, rtxn2 = struct.unpack('>II', data2[:8])
    if act != 1:
        raise RuntimeError('action=%d' % act)
    interval, leech, seeds = struct.unpack('>III', data2[8:20])
    peers = []
    off = 20
    while off + 6 <= len(data2):
        peers.append((socket.inet_ntoa(data2[off:off + 4]),
                      struct.unpack('>H', data2[off + 4:off + 6])[0]))
        off += 6
    return seeds, leech, peers


hits = []
for idx, tr in enumerate(TRACKERS):
    is_udp = tr.startswith('udp://')
    try:
        if is_udp:
            seeds, leech, peers = test_udp(tr)
        elif tr.startswith(('http://', 'https://')):
            seeds, leech, peers = test_http(tr)
        else:
            continue
        real = [p for p in peers if p[0] != MY_IP]
        flag = ''
        if real:
            flag = '  <<< %d 个真实peer: %s' % (
                len(real), ', '.join('%s:%d' % p for p in real[:6]))
        elif peers:
            flag = '  (只有自己)'
        if peers or seeds > 0:
            print('[%3d] %-52s seeds=%-3d leech=%-3d peers=%-2d%s'
                  % (idx, tr[:52], seeds, leech, len(peers), flag))
        if real:
            hits.append((tr, real, seeds))
    except Exception as e:
        pass
    time.sleep(0.15)

print('')
print('=' * 70)
print('能拿到真实 peer 的 tracker: %d 个' % len(hits))
for tr, peers, seeds in hits:
    print('  %s' % tr)
    print('    seeds=%d peers=%s' % (seeds, ', '.join('%s:%d' % p for p in peers[:10])))

# 保存可用列表
with open('/storage/emulated/0/MT2/mcp/DYParse/tools/trackers_hit.txt', 'w') as f:
    for tr, peers, seeds in hits:
        f.write(tr + '\n')
print('')
print('已保存到 tools/trackers_hit.txt')