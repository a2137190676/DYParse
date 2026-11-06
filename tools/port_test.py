# -*- coding: utf-8 -*-
"""测 BT 端口能否监听（UDP+TCP）。"""
import socket

for p in [6881, 6882, 6900, 51413]:
    for fam, nm, addr in ((socket.AF_INET, 'v4', '0.0.0.0'),
                          (socket.AF_INET6, 'v6', '::')):
        # UDP
        s = socket.socket(fam, socket.SOCK_DGRAM)
        try:
            s.bind((addr, p))
            print('UDP BIND OK  %s %d' % (nm, p))
        except Exception as e:
            print('UDP BIND FAIL %s %d -> %s' % (nm, p, e))
        finally:
            s.close()
        # TCP
        t = socket.socket(fam, socket.SOCK_STREAM)
        t.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        try:
            t.bind((addr, p))
            t.listen(1)
            print('TCP BIND OK  %s %d' % (nm, p))
        except Exception as e:
            print('TCP BIND FAIL %s %d -> %s' % (nm, p, e))
        finally:
            t.close()