# -*- coding: utf-8 -*-
# 放宽 so 校验：允许 libtorrent4j.so 压缩
import io

P = '/storage/emulated/0/MT2/mcp/DYParse/build.sh'
d = io.open(P, encoding='utf-8').read()

old = '''# 自检：确认 so 是 STORED
z = zipfile.ZipFile(dst_apk)
bad = [i.filename for i in z.infolist()
       if i.filename.endswith('.so') and i.compress_type != zipfile.ZIP_STORED]
z.close()
if bad:
    print('[ERR] 以下 so 未按 STORED 写入：' + ', '.join(bad))
    sys.exit(41)'''

new = '''# 自检：确认 so 是 STORED（libtorrent4j.so 例外，它压缩后能省 40MB）
z = zipfile.ZipFile(dst_apk)
bad = [i.filename for i in z.infolist()
       if i.filename.endswith('.so')
       and not i.filename.endswith('libtorrent4j.so')
       and i.compress_type != zipfile.ZIP_STORED]
z.close()
if bad:
    print('[ERR] 以下 so 未按 STORED 写入：' + ', '.join(bad))
    sys.exit(41)'''

if old in d:
    d = d.replace(old, new, 1)
    io.open(P, 'w', encoding='utf-8').write(d)
    print('校验放宽 OK')
else:
    print('MISS')