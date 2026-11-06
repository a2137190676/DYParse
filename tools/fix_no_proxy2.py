# -*- coding: utf-8 -*-
"""
安全版：移除 Proxy.NO_PROXY。

【注意两个坑】
  1. HttpURLConnection.openConnection(Proxy) 传 null 会 NPE。
     Media3 的 ProxyHttpDataSource 内部是 openConnection(proxy)，
     所以播放器那两处不能简单改成 null，要改成「有 proxy 用 proxy，没有就不传」。

  2. 普通 openConnection(Proxy.NO_PROXY) 直接改成 openConnection() 即可
     —— 不带参数 = 走默认 ProxySelector = 让系统/TUN 接管。
"""
import io

BASE = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/'

SIMPLE = ['BiliParser.java', 'InnerDownloader.java', 'InsParser.java',
          'ProfileParser.java', 'TtParser.java', 'XParser.java', 'YtParser.java']

for fn in SIMPLE:
    p = BASE + fn
    s = io.open(p, encoding='utf-8').read()
    n0 = s.count('openConnection(java.net.Proxy.NO_PROXY)')
    if n0 == 0:
        print(fn, '无需改')
        continue
    s = s.replace('openConnection(java.net.Proxy.NO_PROXY)', 'openConnection()')
    io.open(p, 'w', encoding='utf-8').write(s)
    print(fn, '改了', n0, '处')

# ---- MainActivity 特殊处理 ----
p = BASE + 'MainActivity.java'
s = io.open(p, encoding='utf-8').read()

# 1) 普通 NO_PROXY
n1 = s.count('openConnection(java.net.Proxy.NO_PROXY)')
s = s.replace('openConnection(java.net.Proxy.NO_PROXY)', 'openConnection()')
print('MainActivity 普通 NO_PROXY:', n1)

# 2) ProxyHttpDataSource 的两处：不能传 null
s = s.replace('new ProxyHttpDataSource(java.net.Proxy.NO_PROXY,', 'new ProxyHttpDataSource((java.net.Proxy) null,')
s = s.replace('final java.net.Proxy pxB = java.net.Proxy.NO_PROXY;',
              'final java.net.Proxy pxB = null;')

# 3) ProxyHttpDataSource.open 里的 openConnection(proxy) 改成安全写法
s = s.replace('conn = (java.net.HttpURLConnection) u.openConnection(proxy);',
              '''// proxy 为 null 时不能调 openConnection(null)（会 NPE）。
                // 传 null 的语义是「跟随系统代理」，即让 TUN/VPN 接管。
                conn = (proxy == null)
                        ? (java.net.HttpURLConnection) u.openConnection()
                        : (java.net.HttpURLConnection) u.openConnection(proxy);''')

io.open(p, 'w', encoding='utf-8').write(s)

left = s.count('java.net.Proxy.NO_PROXY')
print('MainActivity 剩余 NO_PROXY:', left)