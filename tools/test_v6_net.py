# -*- coding: utf-8 -*-
"""验证手机 IPv6 到底能不能出网。
手机有全局 IPv6: 2409:893d:3640:3ba:70c5:ff:fe39:f2f
刚才连 ipv6.google.com 超时，可能是路由/防火墙问题，换多个目标再测。
"""
import socket


def test(host, port, label):
    try:
        infos = socket.getaddrinfo(host, port, socket.AF_INET6, socket.SOCK_STREAM)
        if not infos:
            print('  %-28s 无 IPv6 地址' % label)
            return False
        addr = infos[0][4]
        s = socket.socket(socket.AF_INET6, socket.SOCK_STREAM)
        s.settimeout(8)
        try:
            s.connect(addr)
            print('  %-28s OK -> %s' % (label, addr[0]))
            return True
        finally:
            try:
                s.close()
            except Exception:
                pass
    except Exception as e:
        print('  %-28s FAIL %s' % (label, type(e).__name__))
        return False


print('=== IPv6 出网测试（多目标）===')
ok = 0
for host, port, label in [
    ('ipv6.google.com', 80, 'google v6'),
    ('ipv6.baidu.com', 80, 'baidu v6'),
    ('www.taobao.com', 443, 'taobao'),
    ('www.qq.com', 443, 'qq'),
    ('cloudflare.com', 443, 'cloudflare'),
    ('dht.transmissionbt.com', 6881, 'dht最活跃节点'),
]:
    if test(host, port, label):
        ok += 1

print('')
print('成功 %d 个' % ok)
if ok == 0:
    print('>>> 结论：本网络 IPv6 不通（有地址但出不去，常见于运营商只下发地址不开通）')
else:
    print('>>> 结论：IPv6 可用，那 BT 走 IPv6 是有价值的')

print('')
print('=== tracker 返回的 IPv6 peer 段 vs 本机 IPv6 ===')
print('  本机:  2409:893d:3640:3ba:70c5:ff:fe39:f2f')
print('  peer:  2409:893d:3640:3ba:70c5:ff:fe39:f2f4')
print('  前缀 2409:893d:3640:3ba 完全一致 -> 同一个 /64 网段，就是本机自己')