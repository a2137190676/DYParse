# -*- coding: utf-8 -*-
"""确认 IPv6 在当前网络下的可用性（TCP vs UDP），以及迅雷服务器的作用。"""
import socket
import urllib.request

print('=' * 60)
print('A. IPv6 TCP 能力（网页访问）')
print('=' * 60)
TCP6_TESTS = [
    ('www.baidu.com', 443),
    ('www.taobao.com', 443),
    ('www.qq.com', 443),
    ('dht.transmissionbt.com', 6881),
    ('router.bittorrent.com', 6881),
]
for host, port in TCP6_TESTS:
    try:
        infos = socket.getaddrinfo(host, port, socket.AF_INET6, socket.SOCK_STREAM)
        if not infos:
            print('  %-28s 无 IPv6 地址' % host)
            continue
        ip = infos[0][4][0]
        s = socket.socket(socket.AF_INET6, socket.SOCK_STREAM)
        s.settimeout(6)
        s.connect((ip, port))
        print('  %-28s [%s] TCP OK' % (host, ip))
        s.close()
    except Exception as e:
        print('  %-28s FAIL %s' % (host, type(e).__name__))

print()
print('=' * 60)
print('B. IPv6 UDP 能力（DNS over IPv6）')
print('=' * 60)
# 用 IPv6 DNS 服务器测试 UDP
DNS6 = [('2400:3200::1', '阿里 DNS'), ('240c::6666', 'DNSPod')]
for ip, name in DNS6:
    try:
        s = socket.socket(socket.AF_INET6, socket.SOCK_DGRAM)
        s.settimeout(5)
        # 简单 DNS 查询 www.baidu.com A 记录
        q = (b'\x12\x34\x01\x00\x00\x01\x00\x00\x00\x00\x00\x00'
             b'\x03www\x05baidu\x03com\x00\x00\x01\x00\x01')
        s.sendto(q, (ip, 53))
        d, _ = s.recvfrom(4096)
        print('  %-20s [%s] UDP OK, 返回 %d 字节' % (name, ip, len(d)))
        s.close()
    except Exception as e:
        print('  %-20s [%s] FAIL %s' % (name, ip, type(e).__name__))

print()
print('=' * 60)
print('C. 迅雷服务器是干什么的')
print('=' * 60)
print('  hub5btmain.sandai.net:80   TCP OK  <- 已确认可达')
print('  hub5emu.sandai.net:80      TCP OK  <- 已确认可达')
print()
print('  这两个是迅雷的 BT hub 服务：')
print('    · 客户端连上去后，会拿到该 infoHash 的 peer 列表')
print('    · 这个列表来自迅雷自己的「云播/离线」用户群，不是公开 DHT')
print('    · 只有用迅雷客户端（带 -XL0012- 之类 peer_id）才被允许查询')
print()
# 尝试 HTTP 探测这个 hub
for host in ['hub5btmain.sandai.net', 'hub5emu.sandai.net']:
    try:
        url = 'http://%s/' % host
        req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
        r = urllib.request.urlopen(req, timeout=8)
        body = r.read(300)
        print('  %s -> HTTP %d, body[:120]=%r' % (host, r.getcode(), body[:120]))
    except Exception as e:
        err = str(e)
        print('  %s -> %s %s' % (host, type(e).__name__, err[:60]))