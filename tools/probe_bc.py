# -*- coding: utf-8 -*-
"""深挖 BitComet 前缀拿 seeds=1 的差异，并尝试拿到真实 peer 地址。
关键点：
  - -BC0001- 返回 seeds=1 但 peers 为空
  - 可能用了 peers6 / peers 分片 / 或需要特定参数
做法：完整打印返回的 bencode 内容（hex + 可读），看 tracker 到底给了什么。
"""
import urllib.request, urllib.parse, socket, struct

IH = bytes.fromhex('AE38C22B7953FDF9869529E43B9C0267D3465EB6')
TR = 'http://tracker.bittor.pw:1337/announce'


def enc(b):
    return ''.join('%%%02X' % x for x in b)


def dump(d, label):
    print('--- %s ---' % label)
    print('长度:', len(d))
    print('可读(转义非打印字符):')
    out = []
    for c in d:
        if 32 <= c < 127:
            out.append(chr(c))
        else:
            out.append('\\x%02x' % c)
    print(''.join(out))
    # 找所有可能的 peer 二进制块
    print('hex:', d.hex())
    print('')


variants = [
    ('BC0001 标准', '-BC0001-' + 'A' * 12, '&compact=1&numwant=200&event=started&key=12345678'),
    ('BC0001 无compact', '-BC0001-' + 'A' * 12, '&numwant=200&event=started'),
    ('BC0001 compact=0', '-BC0001-' + 'A' * 12, '&compact=0&numwant=200&event=started'),
    ('BC0001 无event', '-BC0001-' + 'A' * 12, '&compact=1&numwant=200'),
    ('BC0001 seeds=0', '-BC0001-' + 'A' * 12, '&compact=1&numwant=200&left=0'),
]

for name, pid, extra in variants:
    url = (TR + '?info_hash=' + enc(IH)
           + '&peer_id=' + urllib.parse.quote(pid)
           + '&port=6881&uploaded=0&downloaded=0&left=1286311970'
           + extra)
    print('=' * 70)
    print('变体:', name)
    try:
        req = urllib.request.Request(url, headers={'User-Agent': 'BitComet/1.99'})
        r = urllib.request.urlopen(req, timeout=15)
        d = r.read()
        dump(d, name)
    except Exception as ex:
        print('  FAIL', ex)
    print('')