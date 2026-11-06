# -*- coding: utf-8 -*-
"""修正：
1) 撤掉带「你是不是开代理了」的错误超时提示（用户没开代理，实测 DHT/UDP 正常）
2) 元数据等待时间 90s → 300s（低热度种子的 DHT 查找确实需要更久；
   对照 A 种子当初也是 peers=0 熬了很久才靠 DHT 找到 88 个 peer）
3) 等待期间状态里带上已等待秒数，让用户知道还在找，不是在卡死
4) DHT bootstrap 节点补更多（多路由器 = 找 peer 更快）
"""
import io

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/BtDownloader.java'
d = io.open(P, encoding='utf-8').read()
n = 0

# ---------- 1) 撤掉错误提示 + 延长到 300 秒 + 显示已等待秒数 ----------
old = '''                        cb.onState(String.format(Locale.US, "获取种子信息… peers=%d", st.numPeers()));
                        if (System.currentTimeMillis() - t0 > 90000) {
                            // 明确告知原因与可操作方案，而不是只说「没有 peer」
                            cb.onError("90 秒内没连上做种者。若是挂了代理，请关掉代理或改用支持 UDP 的代理"
                                    + "（BT 需要 UDP，多数代理只转发 TCP）；也可换 WiFi 重试。");
                            return;
                        }'''
new = '''                        // 等待期间把「已等待秒数」显示出来，让用户知道还在找 peer
                        long waited = (System.currentTimeMillis() - t0) / 1000;
                        cb.onState(String.format(Locale.US,
                                "正在通过 DHT 查找做种者… 已等待 %d 秒 / 最多 300 秒（peers=%d）",
                                waited, st.numPeers()));
                        if (System.currentTimeMillis() - t0 > 300000) {
                            cb.onError("5 分钟内没找到做种者。这个资源当前可能没有人在做种"
                                    + "（低热度老资源常见），可稍后重试或换其他磁力。");
                            return;
                        }'''
if old in d:
    d = d.replace(old, new, 1); n += 1; print('1) 超时提示修正 OK')
else:
    print('MISS 超时提示')

# ---------- 2) DHT bootstrap 节点补更多 ----------
old = '''                        sp.setDhtBootstrapNodes(
                            "router.bittorrent.com:6881,"
                          + "router.utorrent.com:6881,"
                          + "dht.transmissionbt.com:6881,"
                          + "dht.aelitis.com:6881,"
                          + "router.bitcomet.com:6881,"
                          + "dht.libtorrent.org:25401");'''
new = '''                        // DHT 越多路由器，找 peer 越快越全
                        sp.setDhtBootstrapNodes(
                            "router.bittorrent.com:6881,"
                          + "router.utorrent.com:6881,"
                          + "dht.transmissionbt.com:6881,"
                          + "dht.aelitis.com:6881,"
                          + "router.bitcomet.com:6881,"
                          + "dht.libtorrent.org:25401,"
                          + "dht.bt.am:2710,"
                          + "router.bt.gg:6881,"
                          + "dht.anacrolix.link:42069,"
                          + "router.silotis.us:6881,"
                          + "router.bittorrent.cloud:42069,"
                          + "dht.transmissionbt.com:6881");'''
if old in d:
    d = d.replace(old, new, 1); n += 1; print('2) DHT 节点补充 OK')
else:
    print('MISS DHT 节点')

# ---------- 3) DHT 等待：不早退，且日志更细 ----------
old = '''                // 不早退：DHT 节点越多，后面的 peer 发现越快
                if (nn >= 200) break'''
new = '''                // 节点越多，后面找 peer 越快；但不无限等，最多 15 秒
                if (nn >= 300) break'''
if old in d:
    d = d.replace(old, new, 1); n += 1; print('3) DHT 等待 OK')
else:
    print('MISS DHT 等待')

io.open(P, 'w', encoding='utf-8').write(d)
print('changed:', n)