#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""把 libtorrent4j 集成到 DYParse
1) Java jar -> libs/libtorrent4j.jar
2) 四个架构的 so 打包成一个 aar -> libs/libtorrent4j-native.aar
   （build.sh 会自动从 aars/*/jni/ 收集 so）
"""
import os, shutil, zipfile

ROOT = '/storage/emulated/0/MT2/mcp/DYParse'
DL = os.path.join(ROOT, 'bt_dl')
LIBS = os.path.join(ROOT, 'libs')

# 1) Java jar
src_jar = os.path.join(DL, 'libtorrent4j.jar')
dst_jar = os.path.join(LIBS, 'libtorrent4j.jar')
shutil.copy2(src_jar, dst_jar)
print('jar ->', dst_jar, os.path.getsize(dst_jar))

# 2) 打包 so 成 aar
aar_path = os.path.join(LIBS, 'libtorrent4j-native.aar')
so_root = os.path.join(DL, 'so', 'lib')

# aar 必须有 AndroidManifest.xml 和 classes.jar（即使是空的）
manifest = '''<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="org.libtorrent4j.native">
    <uses-sdk android:minSdkVersion="21"/>
</manifest>
'''

with zipfile.ZipFile(aar_path, 'w', zipfile.ZIP_DEFLATED) as z:
    # 空的 classes.jar
    import io as _io
    cj = _io.BytesIO()
    with zipfile.ZipFile(cj, 'w') as zc:
        zc.writestr('META-INF/MANIFEST.MF', 'Manifest-Version: 1.0\n')
    z.writestr('classes.jar', cj.getvalue())
    z.writestr('AndroidManifest.xml', manifest)
    # so
    count = 0
    for abi in sorted(os.listdir(so_root)):
        p = os.path.join(so_root, abi, 'libtorrent4j.so')
        if os.path.isfile(p):
            z.write(p, 'jni/%s/libtorrent4j.so' % abi)
            print('  + jni/%s/libtorrent4j.so  %d' % (abi, os.path.getsize(p)))
            count += 1
    print('so count =', count)

print('aar ->', aar_path, os.path.getsize(aar_path))

# 3) 校验 aar 结构
with zipfile.ZipFile(aar_path) as z:
    print('\n=== aar 内容 ===')
    for n in z.namelist():
        print('  ', n, z.getinfo(n).file_size)