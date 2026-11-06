# -*- coding: utf-8 -*-
"""
为什么迅雷能下载，我们不能？

已确认的事实：
  1) 这个磁力原始链接只有 xt=urn:btih，没有任何 tr=（无 tracker）
  2) 我们导出的 .torrent 里 announce-list 不存在，DHT/PEX 是唯一 peer 来源
  3) 我们客户端 DHT nodes 能到 96，但 get_peers 返回 peer 数为 0
  4) 125 个公开 tracker 全测，能返回非本机 peer 的只有 2 个，且 TCP 全部超时

本脚本做最后一组验证：
  A. 冷启动 DHT，完整跑 get_peers，看能不能拿到任何 peer
  B. 探测迅雷公开的 peer 来源端口
  C. 看自己的公网出口 IP（判断 NAT 类型）
"""
import socket
import struct
import random
import time
import sys

INFO_HASH_HEX = "AE38C22B7953FDF9869529E43B9C0267D3465EB6"
IH = bytes.fromhex(INFO_HASH_HEX)


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
    raise ValueError('bad bencode at %d' % i)


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
        # 全部 key 先转 bytes 再排序，避免 str/bytes 混排报 TypeError
        pairs = []
        for k, v in o.items():
            kb = k.encode('utf-8') if isinstance(k, str) else k
            pairs.append((kb, v))
        pairs.sort(key=lambda kv: kv[0])
        return b'd' + b''.join(bencode(k) + bencode(v) for k, v in pairs) + b'e'
    raise TypeError(type(o))


def node_id():
    return bytes([random.randint(0, 255) for _ in range(20)])


BOOT = [
    ('router.bittorrent.com', 6881),
    ('router.utorrent.com', 6881),
    ('dht.transmissionbt.com', 6881),
    ('router.bitcomet.com', 6881),
    ('dht.libtorrent.org', 25401),
    ('dht.aelitis.com', 6881),
    ('router.bittorrent.cloud', 42069),
]

print('=' * 62)
print('实验 A: DHT get_peers 全流程')
print('=' * 62)

sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
sock.bind(('0.0.0.0', 0))
sock.settimeout(4)
print('本地 UDP 端口:', sock.getsockname()[1])

seed = node_id()
nodes = []
for host, port in BOOT:
    try:
        ip = socket.gethostbyname(host)
        msg = {'t': b'aa', 'y': 'q', 'q': 'find_node',
               'a': {'id': seed, 'target': node_id()}}
        sock.sendto(bencode(msg), (ip, port))
        d, _ = sock.recvfrom(65535)
        r, _ = bdecode(d)
        nb = r.get(b'r', {}).get(b'nodes', b'')
        n = len(nb) // 26
        print('  %-30s -> %d 节点' % (host, n))
        for k in range(n):
            off = k * 26
            nodes.append(nb[off + 20:off + 26])
    except Exception as e:
        print('  %-30s -> FAIL %s' % (host, type(e).__name__))

print('')
print('引导节点: %d' % len(nodes))
if not nodes:
    print('!! DHT 完全连不上')
    sock.close()
    sys.exit(0)

all_peers = set()
queried = set()
queue = list(nodes)
t0 = time.time()
got_values = 0

while queue and time.time() - t0 < 100:
    addr = queue.pop(0)
    if addr in queried:
        continue
    queried.add(addr)
    ip = socket.inet_ntoa(addr[:4])
    port = struct.unpack('>H', addr[4:6])[0]
    try:
        msg = {'t': struct.pack('>H', random.randint(0, 65535)),
               'y': 'q', 'q': 'get_peers',
               'a': {'id': node_id(), 'info_hash': IH}}
        sock.sendto(bencode(msg), (ip, port))
        sock.settimeout(2)
        d, _ = sock.recvfrom(65535)
        r, _ = bdecode(d)
        rr = r.get(b'r', {})
        vals = rr.get(b'values')
        if vals:
            got_values += 1
            for v in vals:
                if len(v) == 6:
                    all_peers.add((socket.inet_ntoa(v[:4]),
                                   struct.unpack('>H', v[4:6])[0]))
            print('  !! %s:%d 返回 %d peer' % (ip, port, len(vals)))
        nb = rr.get(b'nodes', b'')
        for k in range(len(nb) // 26):
            off = k * 26
            a2 = nb[off + 20:off + 26]
            if a2 not in queried:
                queue.append(a2)
    except Exception:
        pass

print('')
print('查询节点数: %d' % len(queried))
print('返回 values 的节点: %d' % got_values)
print('DHT 找到 peer: %d 个' % len(all_peers))
for p in list(all_peers)[:15]:
    print('    %s:%d' % p)

sock.close()

print('')
print('=' * 62)
print('实验 B: 迅雷 peer 来源端口连通性')
print('=' * 62)
XL_HOSTS = [
    ('hub5btmain.sandai.net', 80),
    ('hub5emu.sandai.net', 80),
    ('bt.tracker.xunlei.com', 80),
    ('dht.xunlei.com', 6881),
]
for host, port in XL_HOSTS:
    try:
        ip = socket.gethostbyname(host)
        s2 = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        s2.settimeout(5)
        s2.connect((ip, port))
        print('  %-28s %s:%d  TCP OK' % (host, ip, port))
        s2.close()
    except Exception as e:
        print('  %-28s FAIL %s' % (host, type(e).__name__))

print('')
print('=' * 62)
print('实验 C: 公网出口 IP（判断 NAT）')
print('=' * 62)
try:
    import urllib.request
    pub = urllib.request.urlopen('http://ip.3322.net', timeout=8).read().decode().strip()
    print('公网出口 IP:', pub)
except Exception as e:
    print('拿公网 IP 失败:', type(e).__name__)