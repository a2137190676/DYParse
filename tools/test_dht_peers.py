# -*- coding: utf-8 -*-
"""测试 DHT 挖出来的两个真实外部 peer 能否连上，并做 BT handshake。"""
import socket
import struct
import time

IH = bytes.fromhex("AE38C22B7953FDF9869529E43B9C0267D3465EB6")

CANDIDATES = [
    ("197.185.152.249", 28862),
    ("175.118.107.22", 51413),
]

print('=' * 58)
print('TCP 探测 + BT handshake')
print('=' * 58)

for ip, port in CANDIDATES:
    print('\n--- %s:%d ---' % (ip, port))
    # 先 TCP
    try:
        t0 = time.time()
        s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        s.settimeout(8)
        s.connect((ip, port))
        print('  TCP 连接成功 (%.2fs)' % (time.time() - t0))
    except Exception as e:
        print('  TCP 失败: %s' % type(e).__name__)
        continue
    # 再 BT handshake
    try:
        # BitTorrent protocol handshake
        # 19 + "BitTorrent protocol" + 8 reserved + 20 infohash + 20 peerid
        peer_id = b'-DYP001-' + b'0' * 12
        hs = (b'\x13' + b'BitTorrent protocol' + b'\x00' * 8 + IH + peer_id)
        s.sendall(hs)
        s.settimeout(8)
        resp = s.recv(68)
        if len(resp) >= 68 and resp[1:20] == b'BitTorrent protocol':
            print('  BT handshake 成功！对方 peer_id: %r' % resp[48:68])
            print('  预留位: %r' % resp[20:28])
            # 读一条消息看对方发什么
            s.settimeout(5)
            try:
                d2 = s.recv(4096)
                print('  收到后续数据 %d 字节: %r' % (len(d2), d2[:60]))
            except Exception:
                print('  后续无数据（正常，需先发 interested）')
        else:
            print('  非 BT 服务，返回 %d 字节: %r' % (len(resp), resp[:40]))
        s.close()
    except Exception as e:
        print('  handshake 失败: %s' % type(e).__name__)
        try:
            s.close()
        except Exception:
            pass

print()
print('=' * 58)
print('这两个 peer 在 announce 里出现过吗？（交叉验证）')
print('=' * 58)
print('197.185.152.249  -> DHT 独有')
print('175.118.107.22   -> DHT 独有')
print()
print('之前 125 tracker 全测的结果：')
print('  188.132.131.131:80 / 77.92.134.149:8800  <- TCP 全部 TimeoutError')
print('说明：能拿到 IP 不等于能连上，tracker 里的记录可能是几年前的。')