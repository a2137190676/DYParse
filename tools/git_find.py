# -*- coding: utf-8 -*-
# 在 .backup/objects 里找包含 MainActivity 内容的 blob（git loose object = zlib）
import os, zlib, io

ROOT = '/storage/emulated/0/MT2/mcp/DYParse/.backup/objects'
hits = []

for dirname in os.listdir(ROOT):
    d = os.path.join(ROOT, dirname)
    if not os.path.isdir(d) or len(dirname) != 2:
        continue
    for fn in os.listdir(d):
        p = os.path.join(d, fn)
        try:
            sz = os.path.getsize(p)
            if sz < 200000:   # MainActivity.java 约 900KB
                continue
            raw = open(p, 'rb').read()
            data = zlib.decompress(raw)
            # git object: "blob <size>\0<content>"
            if data.startswith(b'blob '):
                nul = data.find(b'\x00')
                content = data[nul + 1:]
                if b'public class MainActivity extends Activity' in content:
                    hits.append((sz, p, len(content), os.path.getmtime(p)))
        except Exception:
            pass

hits.sort(key=lambda x: -x[2])
print('找到', len(hits), '个候选：')
for sz, p, clen, mt in hits[:10]:
    import time
    print('  %s  blob=%d  size=%d  mtime=%s' % (
        p, sz, clen, time.strftime('%m-%d %H:%M', time.localtime(mt))))