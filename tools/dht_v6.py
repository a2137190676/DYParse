# -*- coding: utf-8 -*-
"""
关键验证：IPv6 DHT 里这个种子有没有活的 peer？

背景：
  · 用户迅雷截图里 peer 是 IPv6（2409:...）
  · 我们客户端 listen=[] —— IPv6 监听完全没起来
  · 手机有 IPv6 地址 2409:893d:3640:3ba:70c5:ff:fe39:f2f
  · 之前测过 IPv6 部分可用（百度/淘宝/QQ 通）

如果 IPv6 DHT 里能找到 peer，那就是我们客户端缺 IPv6 支持导致的。
"""
import socket
import struct
import random
import time
import sys

IH = bytes.fromhex("AE38C22B7953FDF9869529E43B9C0267D3465EB6")


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


def node_id():
    return bytes([random.randint(0, 255) for _ in range(20)])


print('=' * 62)
print('先看本机 IPv6 地址')
print('=' * 62)
import subprocess
try:
    r = subprocess.run(['ip', '-6', 'addr'], capture_output=True, text=True, timeout=10)
    for line in r.stdout.split('\n'):
        if 'inet6' in line:
            print('  ' + line.strip())
except Exception as e:
    print('  查询失败:', e)

print()
print('=' * 62)
print('IPv6 DHT 引导（用 IPv6 的 DHT 节点）')
print('=' * 62)

# IPv6 DHT bootstrap 节点
BOOT6 = [
    ('dht.transmissionbt.com', 6881),
    ('router.bittorrent.com', 6881),
    ('dht.libtorrent.org', 25401),
    ('router.utorrent.com', 6881),
]

sock = socket.socket(socket.AF_INET6, socket.SOCK_DGRAM)
sock.bind(('::', 0))
sock.settimeout(5)
print('本地 IPv6 UDP 端口:', sock.getsockname()[1])

nodes6 = []
for host, port in BOOT6:
    try:
        infos = socket.getaddrinfo(host, port, socket.AF_INET6, socket.SOCK_DGRAM)
        if not infos:
            print('  %-28s 无 IPv6 地址' % host)
            continue
        addr = infos[0][4]
        msg = {'t': b'aa', 'y': 'q', 'q': 'find_node',
               'a': {'id': node_id(), 'target': node_id()}}
        sock.sendto(bencode(msg), addr)
        d, src = sock.recvfrom(65535)
        r, _ = bdecode(d)
        nb = r.get(b'r', {}).get(b'nodes6', b'')
        n = len(nb) // 38      # IPv6 节点: 20 id + 18 addr
        print('  %-28s [%s] -> %d 个 IPv6 节点' % (host, addr[0], n))
        for k in range(n):
            off = k * 38
            nodes6.append(nb[off + 20:off + 38])
    except Exception as e:
        print('  %-28s FAIL %s' % (host, type(e).__name__))

print()
print('IPv6 引导节点: %d' % len(nodes6))
if not nodes6:
    print('!! IPv6 DHT 引导失败')
    sock.close()
    sys.exit(0)

peers6 = set()
queried = set()
queue = list(nodes6)
t0 = time.time()
got = 0

while queue and time.time() - t0 < 90:
    addr = queue.pop(0)
    if addr in queried:
        continue
    queried.add(addr)
    # IPv6 地址: 16 字节 IP + 2 字节端口
    ipb = addr[:16]
    port = struct.unpack('>H', addr[16:18])[0]
    ip = socket.inet_ntop(socket.AF_INET6, ipb)
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
            got += 1
            for v in vals:
                if len(v) == 18:
                    peers6.add((socket.inet_ntop(socket.AF_INET6, v[:16]),
                                struct.unpack('>H', v[16:18])[0]))
                elif len(v) == 6:
                    peers6.add((socket.inet_ntoa(v[:4]),
                                struct.unpack('>H', v[4:6])[0]))
            print('  !! %s 返回 %d peer' % (ip, len(vals)))
        nb = rr.get(b'nodes6', b'')
        for k in range(len(nb) // 38):
            off = k * 38
            a2 = nb[off + 20:off + 38]
            if a2 not in queried:
                queue.append(a2)
    except Exception:
        pass

print()
print('IPv6 DHT 查询节点数: %d' % len(queried))
print('返回 values 的节点: %d' % got)
print('IPv6 DHT 找到 peer: %d 个' % len(peers6))
for p in list(peers6)[:20]:
    print('    [%s]:%d' % p if ':' in str(p[0]) else '    %s:%d' % p)

sock.close()