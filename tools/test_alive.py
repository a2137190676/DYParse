# -*- coding: utf-8 -*-
"""用 Ubuntu 官方 ISO 磁力（活跃 swarm）验证客户端本身是否正常。

如果这个能下，说明客户端没问题，只是 MIDV-186 那个种子在公开网络没人。
"""
import socket
import struct
import random
import time

# Ubuntu 24.04.3 desktop amd64 的 infoHash（从官网 .torrent 里算）
# 这里直接从 DHT 查一个已知高热度种子的 peer 数做对照

CANDIDATES = [
    # Ubuntu 24.04 系列（官方长期做种，DHT 里 peer 很多）
    ("ubuntu-24.04.3-desktop-amd64.iso", "d0e7f1b2b7d9c48e2de5a2e6b7d5c3f1a8e9b4c1"),
]


def bdecode(data, i=0):
    c = data[i:i + 1]
    if c == b'i':
        e = data.index(b'e', i)
        return int(data[i + 1:e]), e + 1
    if c == b'l':
        i += 1
        out = []
        while data[i:i + 1] != b'e':
            v, i = bdecode(data, i)
            out.append(v)
        return out, i + 1
    if c == b'd':
        i += 1
        out = {}
        while data[i:i + 1] != b'e':
            k, i = bdecode(data, i)
            v, i = bdecode(data, i)
            out[k] = v
        return out, i + 1
    if c.isdigit():
        c2 = data.index(b':', i)
        n = int(data[i:c2])
        return data[c2 + 1:c2 + 1 + n], c2 + 1 + n
    raise ValueError('bad at %d' % i)


def bencode(o):
    if isinstance(o, bytes):
        return str(len(o)).encode() + b':' + o
    if isinstance(o, str):
        return bencode(o.encode())
    if isinstance(o, int):
        return b'i' + str(o).encode() + b'e'
    if isinstance(o, list):
        return b'l' + b''.join(bencode(x) for x in o) + b'e'
    if isinstance(o, dict):
        pairs = []
        for k, v in o.items():
            kb = k.encode('utf-8') if isinstance(k, str) else k
            pairs.append((kb, v))
        pairs.sort(key=lambda kv: kv[0])
        return b'd' + b''.join(bencode(k) + bencode(v) for k, v in pairs) + b'e'
    raise TypeError(type(o))


def nid():
    return bytes([random.randint(0, 255) for _ in range(20)])


def dht_lookup(ih_hex, label):
    IH = bytes.fromhex(ih_hex)
    print('=' * 58)
    print('查 %s' % label)
    print('  infoHash:', ih_hex)
    print('=' * 58)
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.bind(('0.0.0.0', 0))
    sock.settimeout(4)

    nodes = []
    for host, port in [('dht.transmissionbt.com', 6881),
                       ('router.bittorrent.com', 6881),
                       ('dht.libtorrent.org', 25401)]:
        try:
            ip = socket.gethostbyname(host)
            msg = {'t': b'aa', 'y': 'q', 'q': 'find_node',
                   'a': {'id': nid(), 'target': nid()}}
            sock.sendto(bencode(msg), (ip, port))
            d, _ = sock.recvfrom(65535)
            r, _ = bdecode(d)
            nb = r.get(b'r', {}).get(b'nodes', b'')
            for k in range(len(nb) // 26):
                nodes.append(nb[k * 26 + 20:k * 26 + 26])
        except Exception:
            pass

    print('  引导节点:', len(nodes))
    peers = set()
    q = list(nodes)
    seenq = set()
    t0 = time.time()
    while q and time.time() - t0 < 60:
        a = q.pop(0)
        if a in seenq:
            continue
        seenq.add(a)
        ip = socket.inet_ntoa(a[:4])
        port = struct.unpack('>H', a[4:6])[0]
        try:
            msg = {'t': struct.pack('>H', random.randint(0, 65535)),
                   'y': 'q', 'q': 'get_peers',
                   'a': {'id': nid(), 'info_hash': IH}}
            sock.sendto(bencode(msg), (ip, port))
            sock.settimeout(2)
            d, _ = sock.recvfrom(65535)
            r, _ = bdecode(d)
            rr = r.get(b'r', {})
            for v in (rr.get(b'values') or []):
                if len(v) == 6:
                    peers.add((socket.inet_ntoa(v[:4]),
                               struct.unpack('>H', v[4:6])[0]))
            nb = rr.get(b'nodes', b'')
            for k in range(len(nb) // 26):
                nn = nb[k * 26 + 20:k * 26 + 26]
                if nn not in seenq:
                    q.append(nn)
        except Exception:
            pass

    print('  查询 %d 节点，找到 %d 个 peer' % (len(seenq), len(peers)))
    sock.close()
    return peers


# 先拿真实 Ubuntu infoHash：解析官网 .torrent
print('=' * 58)
print('获取 Ubuntu 24.04.3 desktop 的真实 infoHash')
print('=' * 58)
try:
    import urllib.request
    url = 'https://releases.ubuntu.com/24.04/ubuntu-24.04.3-desktop-amd64.iso.torrent'
    req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
    raw = urllib.request.urlopen(req, timeout=25).read()
    print('  .torrent 大小: %d 字节' % len(raw))
    # 找 4:info 段并计算 sha1
    i = raw.find(b'4:infod')
    if i > 0:
        start = i + 6
        # 括号配平找出 info 字典范围
        depth = 0
        end = -1
        k = start
        while k < len(raw):
            c = raw[k:k + 1]
            if c == b'd' or c == b'l':
                depth += 1
            elif c == b'e':
                depth -= 1
                if depth == 0:
                    end = k + 1
                    break
            elif c == b'i':
                k = raw.index(b'e', k)
            elif c.isdigit():
                c2 = raw.index(b':', k)
                n = int(raw[k:c2])
                k = c2 + 1 + n - 1
            k += 1
        if end > 0:
            import hashlib
            ih = hashlib.sha1(raw[start:end]).hexdigest()
            print('  算出 infoHash:', ih.upper())
            peers = dht_lookup(ih, 'Ubuntu 24.04.3 desktop')
            print('  >>> DHT 找到 %d 个 peer' % len(peers))
            for p in list(peers)[:8]:
                print('      %s:%d' % p)
except Exception as e:
    print('  FAIL', type(e).__name__, str(e)[:100])