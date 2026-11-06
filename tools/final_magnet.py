# -*- coding: utf-8 -*-
"""真实验证：用 XL 前缀探测迅雷 hub，看能否拿到 peer 列表。"""
import urllib.request
import gzip

IH = "AE38C22B7953FDF9869529E43B9C0267D3465EB6"
XL_PEER_ID = "-XL0012-123456789012"

print('=' * 60)
print('用迅雷 peer_id 探测 hub5btmain.sandai.net')
print('=' * 60)

PATHS = [
    '/get_peer_list?info_hash=%s&peer_id=%s' % (IH, XL_PEER_ID),
    '/announce?info_hash=%s&peer_id=%s' % (IH, XL_PEER_ID),
    '/bt/announce?info_hash=%s&peer_id=%s' % (IH, XL_PEER_ID),
]

for path in PATHS:
    try:
        url = 'http://hub5btmain.sandai.net' + path
        req = urllib.request.Request(url, headers={
            'User-Agent': 'Xunlei/7.0',
            'X-Peer-ID': XL_PEER_ID,
        })
        r = urllib.request.urlopen(req, timeout=8)
        raw = r.read()
        if raw[:2] == b'\x1f\x8b':
            raw = gzip.decompress(raw)
        print('  %s -> HTTP %d len=%d' % (path[:40], r.getcode(), len(raw)))
        print('       body: %r' % raw[:200])
    except Exception as e:
        print('  %s -> %s %s' % (path[:40], type(e).__name__, str(e)[:60]))

print()
print('结论：如果全部 400/403/404，说明 hub 只认迅雷协议，第三方无法接入')