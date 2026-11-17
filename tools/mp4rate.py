# -*- coding: utf-8 -*-
"""从 moov 里抠出 stsz(样本数/大小) 与 stts(时长)，算平均码率，用于对比画质。"""
import struct
import sys


def boxes(buf, start, end):
    i = start
    while i + 8 <= end:
        size = struct.unpack('>I', buf[i:i + 4])[0]
        typ = buf[i + 4:i + 8].decode('latin-1', 'ignore')
        hdr = 8
        if size == 1:
            if i + 16 > end:
                break
            size = struct.unpack('>Q', buf[i + 8:i + 16])[0]
            hdr = 16
        elif size == 0:
            size = end - i
        if size < hdr or i + size > end:
            break
        yield typ, i + hdr, i + size
        i += size


def walk_all(buf, want, start=0, end=None, depth=0, out=None):
    if out is None:
        out = []
    if end is None:
        end = len(buf)
    if depth > 8:
        return out
    for typ, cs, ce in boxes(buf, start, end):
        if typ == want:
            out.append((cs, ce))
        walk_all(buf, want, cs, ce, depth + 1, out)
    return out


def info(path):
    buf = open(path, 'rb').read()
    print('=== %s  (%d bytes) ===' % (path, len(buf)))

    # 时长
    for cs, ce in walk_all(buf, 'mvhd'):
        b = buf[cs:ce]
        ver = b[0]
        if ver == 0 and len(b) >= 20:
            ts = struct.unpack('>I', b[12:16])[0]
            du = struct.unpack('>I', b[16:20])[0]
        elif ver == 1 and len(b) >= 32:
            ts = struct.unpack('>I', b[20:24])[0]
            du = struct.unpack('>Q', b[24:32])[0]
        else:
            continue
        if ts:
            print('  duration = %.3f s (timescale %d)' % (du / float(ts), ts))
        break

    # 视频轨样本数 & 总字节
    for cs, ce in walk_all(buf, 'stsz'):
        b = buf[cs:ce]
        if len(b) < 12:
            continue
        sample_size = struct.unpack('>I', b[4:8])[0]
        count = struct.unpack('>I', b[8:12])[0]
        if sample_size:
            total = sample_size * count
        else:
            n = min(count, (len(b) - 12) // 4)
            total = sum(struct.unpack('>I', b[12 + 4 * k:16 + 4 * k])[0] for k in range(n))
        print('  samples = %d   total sample bytes = %d' % (count, total))
        break

    # stsd codec
    for cs, ce in walk_all(buf, 'stsd'):
        b = buf[cs:ce]
        if len(b) < 16:
            continue
        codec = buf[cs + 12:cs + 16].decode('latin-1', 'ignore')
        print('  codec = %s' % codec)
        break

    # tkhd 宽高
    for cs, ce in walk_all(buf, 'tkhd'):
        b = buf[cs:ce]
        if len(b) >= 84:
            w = struct.unpack('>I', b[-8:-4])[0] >> 16
            h = struct.unpack('>I', b[-4:])[0] >> 16
            print('  width x height = %d x %d' % (w, h))
            break


for p in sys.argv[1:]:
    info(p)
    print()