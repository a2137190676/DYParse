# -*- coding: utf-8 -*-
# so 压缩策略优化：
#   现状：所有 .so 强制 ZIP_STORED（不压缩），4 个 libtorrent4j.so 共 64MB 原样进包
#   改法：libtorrent4j.so 用 DEFLATED（压缩后约 23MB），其余 so 保持 STORED
#
# 为什么可以压缩：
#   系统在 extractNativeLibs 未显式关闭时会解压到 app 的 nativeLibraryDir 再加载。
#   APK 里 manifest 没写 android:extractNativeLibs="false"，所以走解压路径，压缩没问题。
import io

P = '/storage/emulated/0/MT2/mcp/DYParse/build.sh'
d = io.open(P, encoding='utf-8').read()

old = '''# 必须 STORED 的：native so、classes.dex、resources.arsc
def must_store(name):
    return (name.startswith('lib/') and name.endswith('.so')) \\
        or name == 'classes.dex' or name == 'resources.arsc\''''

new = '''# 必须 STORED 的：classes.dex、resources.arsc
# 例外：libtorrent4j 的 so 体积巨大（4 架构共 64MB 未压缩），
#       压缩后约 23MB，能省 40MB。它不依赖 mmap 直接加载（manifest 未设
#       extractNativeLibs=false），压缩后系统会解压到 nativeLibraryDir 再 dlopen，功能正常。
def must_store(name):
    if name.startswith('lib/') and name.endswith('libtorrent4j.so'):
        return False
    return (name.startswith('lib/') and name.endswith('.so')) \\
        or name == 'classes.dex' or name == 'resources.arsc\''''

if old in d:
    d = d.replace(old, new, 1)
    io.open(P, 'w', encoding='utf-8').write(d)
    print('must_store 已改：libtorrent4j.so 走压缩')
else:
    print('MISS')
    i = d.find('def must_store')
    print(repr(d[i - 120:i + 340]))