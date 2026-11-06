# -*- coding: utf-8 -*-
"""测试新发现的 peer 是否可连。"""
import socket

CANDIDATES = [
    ('188.132.131.131', 80, 'durukanbal 返回'),
    ('77.92.134.149', 8800, 'durukanbal 返回'),
    ('188.132.131.131', 6881, 'durukanbal 常见端口'),
    ('197.185.152.249', 49886, 'stealth.si 返回'),
    ('183.141.23.18', 15000, 'itzmx 返回'),
    ('175.118.107.22', 51413, 'opentrackr 返回'),
]

print('=' * 66)
print('候选 peer 逐个 TCP 探测')
print('=' * 66)
alive = []
for ip, port, note in CANDIDATES:
    s = socket.socket()
    s.settimeout(8)
    try:
        s.connect((ip, port))
        print('  ALIVE  %-16s:%-6d  (%s)' % (ip, port, note))
        alive.append((ip, port))
    except Exception as e:
        print('  dead   %-16s:%-6d  (%s) %s' % (ip, port, note, type(e).__name__))
    finally:
        try:
            s.close()
        except Exception:
            pass

print('')
print('存活 %d / %d' % (len(alive), len(CANDIDATES)))

# 对活的做一次 BT handshake，确认是不是真 BT peer
if alive:
    print('')
    print('=== 对存活 peer 做 BT handshake 验证 ===')
    INFO_HASH = bytes.fromhex('AE38C22B7953FDF9869529E43B9C0267D3465EB6')
    for ip, port in alive:
        s = socket.socket()
        s.settimeout(10)
        try:
            s.connect((ip, port))
            hs = (b'\x13BitTorrent protocol'
                  + b'\x00' * 8
                  + INFO_HASH
                  + b'-DY0001-AAAAAAAAAAAA')
            s.sendall(hs)
            resp = s.recv(68)
            if len(resp) >= 48 and resp[0] == 19:
                peer_id = resp[28:48]
                print('  %s:%d 是真 BT peer!  peer_id=%s'
                      % (ip, port, peer_id.decode('latin1')))
            else:
                print('  %s:%d 握手响应异常 len=%d' % (ip, port, len(resp)))
        except Exception as e:
            print('  %s:%d 握手失败 %s' % (ip, port, type(e).__name__))
        finally:
            try:
                s.close()
            except Exception:
                pass