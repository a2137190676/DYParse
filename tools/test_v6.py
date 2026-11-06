# -*- coding: utf-8 -*-
"""验证 IPv6 peer 是否可连通 —— tracker 返回的活跃 peer 是 IPv6。"""
import socket

PEER_V6 = '2409:893d:3640:3ba:70c5:ff:fe39:f2f4'
PORT = 6881

print('=== 1) 本机是否支持 IPv6 ===')
try:
    s6 = socket.socket(socket.AF_INET6, socket.SOCK_STREAM)
    s6.settimeout(3)
    s6.bind(('::', 0))
    print('  IPv6 socket 创建 OK, 本地端口:', s6.getsockname()[1])
    s6.close()
except Exception as e:
    print('  IPv6 不可用:', e)

print('')
print('=== 2) 测试 IPv6 peer 连通性（多个端口）===')
for port in [6881, 51413, 15000]:
    s = socket.socket(socket.AF_INET6, socket.SOCK_STREAM)
    s.settimeout(8)
    try:
        s.connect((PEER_V6, port))
        print('  ALIVE [%s]:%d' % (PEER_V6, port))
    except Exception as e:
        print('  dead  [%s]:%d (%s)' % (PEER_V6, port, type(e).__name__))
    finally:
        try:
            s.close()
        except Exception:
            pass

print('')
print('=== 3) 本机 IPv6 地址 ===')
try:
    import subprocess
    out = subprocess.run(['ip', '-6', 'addr'], capture_output=True, text=True, timeout=10)
    for ln in out.stdout.split('\n'):
        if 'inet6' in ln:
            print(' ', ln.strip())
except Exception as e:
    print('  获取失败:', e)

print('')
print('=== 4) 测试 IPv6 出网（能否访问 v6 互联网）===')
try:
    s = socket.socket(socket.AF_INET6, socket.SOCK_STREAM)
    s.settimeout(8)
    s.connect(('ipv6.google.com', 80))
    print('  IPv6 出网 OK')
    s.close()
except Exception as e:
    print('  IPv6 出网失败:', type(e).__name__, e)