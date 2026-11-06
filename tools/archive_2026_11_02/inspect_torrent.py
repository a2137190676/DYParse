# -*- coding: utf-8 -*-
"""分析迅雷保存的 .torrent：读 announce / announce-list / nodes / infoHash。"""
import io, sys, socket, struct

P = '/storage/emulated/0/Download/MIDV-186-uncensored.torrent'
d = io.open(P, 'rb').read()
print('文件大小:', len(d))


def bdecode(x, i=0):
    c = x[i:i + 1]
    if c == b'i':
        j = x.index(b'e', i); return int(x[i + 1:j]), j + 1
    if c == b'l':
        i += 1; out = []
        while x[i:i + 1] != b'e':
            v, i = bdecode(x, i); out.append(v)
        return out, i + 1
    if c == b'd':
        i += 1; out = {}
        while x[i:i + 1] != b'e':
            k, i = bdecode(x, i); v, i = bdecode(x, i); out[k] = v
        return out, i + 1
    j = x.index(b':', i); n = int(x[i:j])
    return x[j + 1:j + 1 + n], j + 1 + n


root, _ = bdecode(d)
print('顶层键:', sorted([k.decode('utf-8', 'replace') for k in root.keys()]))

if b'announce' in root:
    print('announce:', root[b'announce'].decode('utf-8', 'replace'))
if b'announce-list' in root:
    print('announce-list:')
    for tier in root[b'announce-list']:
        for u in tier:
            print('   ', u.decode('utf-8', 'replace'))
if b'nodes' in root:
    print('nodes (DHT 引导):')
    for nd in root[b'nodes']:
        print('   ', nd[0].decode('utf-8', 'replace'), nd[1])
if b'comment' in root:
    print('comment:', root[b'comment'].decode('utf-8', 'replace'))
if b'created by' in root:
    print('created by:', root[b'created by'].decode('utf-8', 'replace'))

info = root[b'info']
nm = info.get(b'name', b'?').decode('utf-8', 'replace')
print('name:', nm)
pl = info.get(b'piece length', 0)
print('piece length:', pl)
if b'length' in info:
    print('单文件，长度:', info[b'length'])
elif b'files' in info:
    print('多文件，共 %d 个:' % len(info[b'files']))
    for f in info[b'files'][:12]:
        fp = '/'.join(p.decode('utf-8', 'replace') for p in f[b'path'])
        print('   ', fp, f[b'length'])
    if len(info[b'files']) > 12:
        print('    ... 还有', len(info[b'files']) - 12, '个')

# infoHash = sha1(bencode(info))
import hashlib
def bencode(o):
    if isinstance(o, bytes): return str(len(o)).encode() + b':' + o
    if isinstance(o, int): return b'i%de' % o
    if isinstance(o, list): return b'l' + b''.join(bencode(v) for v in o) + b'e'
    if isinstance(o, dict):
        out = b'd'
        for k in sorted(o.keys()):
            out += bencode(k) + bencode(o[k])
        return out + b'e'
    raise TypeError

ih = hashlib.sha1(bencode(info)).hexdigest().upper()
print('')
print('>>> infoHash =', ih)
print('>>> 对比用户给的磁力 E4F8ED9F17D7082B418BF17573ECB5E621A794B5')
print('>>> 一致?', ih == 'E4F8ED9F17D7082B418BF17573ECB5E621A794B5')