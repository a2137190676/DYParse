# -*- coding: utf-8 -*-
"""
修：App 内所有网络请求硬编码 Proxy.NO_PROXY，导致 FlClash 的 TUN/VPN 代理形同虚设。

【用户实测】
  · FlClash 代理开着，TikTok App 能正常刷
  · 本 App 解析 TikTok 一律 SocketTimeoutException
  · curl https://www.tiktok.com/ → 000（失败）

【根因】
  全工程 19 处都写着：
      new URL(url).openConnection(java.net.Proxy.NO_PROXY)

  Proxy.NO_PROXY 的含义是「不经过任何代理，直连」。
  这不是「跟随系统」，而是「显式绕过」。

  FlClash 在本机是 TUN/VPN 模式（系统里没有 http_proxy 设置，
  dumpsys 只有 VpnNetworkProvider）。这种情况下：
    · 普通 App 的流量走 TUN → 被转发到代理 → 通
    · 声明了 NO_PROXY 的 Java 连接 → 走 app 自身的网络栈，
      是否能被 TUN 抓到取决于实现，实测就是抓不到 → 超时

  所以「代理能用 TikTok 但解析不行」不是网络问题，是我们主动绕开了代理。

【改法】
  抽一个统一的连接方法 dypOpen(URL)，内部：
    · 读系统/环境代理（ProxySelector.getDefault()）
    · 有可用代理就用它，没有才直连
  然后把 19 处 NO_PROXY 全换成它。

  注意：不是简单改成 ProxySelector.getDefault() ——
  Java 默认的 ProxySelector 在 Android 上读的是系统 http_proxy 属性，
  而 FlClash 的 TUN 模式不设这个属性。所以更稳的做法是：
    一律「不显式指定 Proxy」，让底层 socket 走默认网络（这样 TUN 能接管）。
  即 openConnection() 不带参数，等价于走系统默认路由。

  实测对比：
    openConnection(Proxy.NO_PROXY)  → 显式直连，TUN 抓不到
    openConnection()                → 走默认 ProxySelector，TUN 能接管
"""
import io
import re

BASE = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/'

FILES = [
    'BiliParser.java',
    'InnerDownloader.java',
    'InsParser.java',
    'MainActivity.java',
    'ProfileParser.java',
    'TtParser.java',
    'XParser.java',
    'YtParser.java',
]

total = 0
report = []

for fn in FILES:
    p = BASE + fn
    try:
        s = io.open(p, encoding='utf-8').read()
    except Exception as e:
        report.append('%s 读失败 %s' % (fn, e))
        continue
    before = s.count('java.net.Proxy.NO_PROXY')
    if before == 0:
        continue
    # 把 openConnection(java.net.Proxy.NO_PROXY) 改成 openConnection()
    s = s.replace('openConnection(java.net.Proxy.NO_PROXY)', 'openConnection()')
    # 处理 return new ProxyHttpDataSource(java.net.Proxy.NO_PROXY, ...) 这种
    s = s.replace('new ProxyHttpDataSource(java.net.Proxy.NO_PROXY,',
                  'new ProxyHttpDataSource(null,')
    # 处理 final java.net.Proxy pxB = java.net.Proxy.NO_PROXY;
    s = s.replace('final java.net.Proxy pxB = java.net.Proxy.NO_PROXY;',
                  'final java.net.Proxy pxB = null;')
    after = s.count('java.net.Proxy.NO_PROXY')
    io.open(p, 'w', encoding='utf-8').write(s)
    total += (before - after)
    report.append('%s: %d → %d' % (fn, before, after))

print('\n'.join(report))
print('已移除 NO_PROXY 处数:', total)