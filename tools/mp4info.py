# -*- coding: utf-8 -*-
"""解析 MP4 前几个 MB 的 moov/stsd，拿 codec / 宽高 / 采样信息。
不依赖 ffprobe，直接按 ISO BMFF 盒子结构走。"""
import struct
import sys


def boxes(buf, start, end):
    i = start
    while i + 8 <= end:
        size = struct.unpack('>I', buf[i:i + 4])[0]
        typ = buf[i + 4:i + 8].decode('latin-1')
        hdr = 8
        if size == 1:
            size = struct.unpack('>Q', buf[i + 8:i + 16])[0]
            hdr = 16
        elif size == 0:
            size = end - i
        if size < hdr or i + size > end:
            break
        yield typ, i + hdr, i + size
        i += size


def find(buf, path, start=0, end=None):
    """按路径（如 ['moov','trak','mdia']）递归找第一个匹配盒子"""
    if end is None:
        end = len(buf)
    want = path[0]
    for typ, cs, ce in boxes(buf, start, end):
        if typ == want:
            if len(path) == 1:
                return cs, ce
            got = find(buf, path[1:], cs, ce)
            if got:
                return got
    return None


def walk_find(buf, want, start=0, end=None, depth=0):
    """深度优先找任意层级里的某个盒子"""
    if end is None:
        end = len(buf)
    if depth > 8:
        return None
    for typ, cs, ce in boxes(buf, start, end):
        if typ == want:
            return cs, ce
        got = walk_find(buf, want, cs, ce, depth + 1)
        if got:
            return got
    return None


def parse(path):
    buf = open(path, 'rb').read()
    print('=== %s  (%d bytes) ===' % (path, len(buf)))

    r = walk_find(buf, 'stsd')
    if not r:
        print('  stsd 未找到（moov 可能在文件尾部）')
        return
    cs, ce = r
    # stsd: version(1)+flags(3)+entry_count(4) 之后是 sample entry
    p = cs + 8
    if p + 8 > len(buf):
        return
    esize = struct.unpack('>I', buf[p:p + 4])[0]
    etyp = buf[p + 4:p + 8].decode('latin-1')
    print('  codec box = %s  entry_size = %d' % (etyp, esize))
    # VisualSampleEntry: 6 reserved + 2 data_ref_index + 16 pre_defined/reserved
    #   + width(2) + height(2) ...
    q = p + 8
    if q + 24 <= len(buf):
        w = struct.unpack('>H', buf[q + 16:q + 18])[0]
        h = struct.unpack('>H', buf[q + 18:q + 20])[0]
        print('  width x height = %d x %d' % (w, h))

    # 外层 trak 的 tkhd 里也有宽高（用于确认）
    t = walk_find(buf, 'tkhd')
    if t:
        ts, te = t
        body = buf[ts:te]
        if len(body) >= 84:
            # version 0: ... 最后 8 字节是 16.16 定点宽高
            tw = struct.unpack('>I', body[-8:-4])[0] >> 16
            th = struct.unpack('>I', body[-4:])[0] >> 16
            print('  tkhd w x h = %d x %d' % (tw, th))

    m = walk_find(buf, 'mvhd')
    if m:
        ms, me = m
        body = buf[ms:me]
        ver = body[0]
        if ver == 0 and len(body) >= 20:
            ts = struct.unpack('>I', body[12:16])[0]
            du = struct.unpack('>I', body[16:20])[0]
            if ts:
                print('  duration = %.3f s' % (du / float(ts)))
        elif ver == 1 and len(body) >= 32:
            ts = struct.unpack('>I', body[20:24])[0]
            du = struct.unpack('>Q', body[24:32])[0]
            if ts:
                print('  duration = %.3f s' % (du / float(ts)))


for p in sys.argv[1:]:
    parse(p)
    print()
