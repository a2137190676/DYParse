# -*- coding: utf-8 -*-
"""用 DHT get_peers 查 AE38C22B 到底有没有活着的做种者。"""
import socket, struct, random, time

TARGET = bytes.fromhex('AE38C22B7953FDF9869529E43B9C0267D3465EB6')
BOOTSTRAP = [
    ('dht.transmissionbt.com', 6881),
    ('router.bittorrent.com', 6881),
    ('router.utorrent.com', 6881),
    ('dht.libtorrent.org', 25401),
]

MY_ID = bytes(random.randint(0, 255) for _ in range(20))
sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
sock.settimeout(3)
sock.bind(('0.0.0.0', 0))


def bstr(x):
    x = x if isinstance(x, bytes) else x.encode()
    return str(len(x)).encode() + b':' + x


def bencode(o):
    if isinstance(o, (bytes, str)):
        return bstr(o)
    if isinstance(o, int):
        return b'i%de' % o
    if isinstance(o, list):
        return b'l' + b''.join(bencode(v) for v in o) + b'e'
    if isinstance(o, dict):
        out = b'd'
        for k in sorted(o.keys()):
            out += bstr(k) + bencode(o[k])
        return out + b'e'
    raise TypeError


def bdecode(d, i=0):
    c = d[i:i + 1]
    if c == b'i':
        j = d.index(b'e', i)
        return int(d[i + 1:j]), j + 1
    if c == b'l':
        i += 1
        out = []
        while d[i:i + 1] != b'e':
            v, i = bdecode(d, i)
            out.append(v)
        return out, i + 1
    if c == b'd':
        i += 1
        out = {}
        while d[i:i + 1] != b'e':
            k, i = bdecode(d, i)
            v, i = bdecode(d, i)
            out[k] = v
        return out, i + 1
    j = d.index(b':', i)
    n = int(d[i:j])
    return d[j + 1:j + 1 + n], j + 1 + n


nodes = []
peers = set()
txn = [0]


def query(ip, port, name, args, wait=3):
    txn[0] += 1
    t = str(txn[0]).encode()
    msg = bencode({b't': t, b'y': b'q', b'q': name.encode(), b'a': args})
    try:
        sock.sendto(msg, (ip, port))
    except Exception:
        return None
    end = time.time() + wait
    while time.time() < end:
        try:
            data, _ = sock.recvfrom(4096)
        except socket.timeout:
            return None
        try:
            r, _ = bdecode(data)
        except Exception:
            continue
        if r.get(b't') == t:
            return r
    return None


print('=== 引导 DHT ===')
for host, port in BOOTSTRAP:
    try:
        ip = socket.gethostbyname(host)
    except Exception as e:
        print('  %-26s 解析失败' % host)
        continue
    r = query(ip, port, 'find_node', {b'id': MY_ID, b'target': MY_ID})
    if r and b'r' in r and b'nodes' in r[b'r']:
        blob = r[b'r'][b'nodes']
        c = 0
        for x in range(0, len(blob) - 25, 26):
            nodes.append((socket.inet_ntoa(blob[x + 20:x + 24]),
                          struct.unpack('>H', blob[x + 24:x + 26])[0]))
            c += 1
        print('  %-26s OK %d 节点' % (host, c))
    else:
        print('  %-26s 无响应' % host)

print('引导节点:', len(nodes))
print('')
print('=== get_peers 迭代 ===')
seen = set()
for rnd in range(10):
    batch = []
    for n in nodes:
        if n in seen:
            continue
        seen.add(n)
        batch.append(n)
        if len(batch) >= 20:
            break
    if not batch:
        break
    for ip, port in batch:
        r = query(ip, port, 'get_peers', {b'id': MY_ID, b'info_hash': TARGET}, wait=2)
        if not r or b'r' not in r:
            continue
        rr = r[b'r']
        if b'values' in rr:
            for v in rr[b'values']:
                if len(v) == 6:
                    peers.add((socket.inet_ntoa(v[:4]),
                               struct.unpack('>H', v[4:6])[0]))
        if b'nodes' in rr:
            blob = rr[b'nodes']
            for x in range(0, len(blob) - 25, 26):
                nd = (socket.inet_ntoa(blob[x + 20:x + 24]),
                      struct.unpack('>H', blob[x + 24:x + 26])[0])
                if nd not in seen:
                    nodes.append(nd)
    print('  轮 %d: 已查 %d, peer=%d' % (rnd + 1, len(seen), len(peers)))

print('')
print('=== DHT 结果: %d 个 peer ===' % len(peers))
alive = 0
for ip, port in sorted(peers):
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
print('存活 %d / %d' % (alive, len(peers)))