# -*- coding: utf-8 -*-
"""修复「磁力拿不到 peer」根因：代理只走 TCP，UDP tracker/DHT 全不通
   方案：tracker 列表里加 HTTP/HTTPS（TCP）tracker，并让 DHT 走 TCP 兜底。
"""
import io

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/BtDownloader.java'
d = io.open(P, encoding='utf-8').read()
n = 0

# ---------- 1) tracker 列表：HTTP/HTTPS 优先（走 TCP，代理能过） ----------
old = '''    /** 公共 tracker 列表（磁力通常不自带 tracker，只靠 DHT 会很慢） */
    private static final String[] EXTRA_TRACKERS = new String[]{
        "udp://tracker.opentrackr.org:1337/announce",
        "udp://open.tracker.cl:1337/announce",
        "udp://tracker.openbittorrent.com:6969/announce",
        "udp://exodus.desync.com:6969/announce",
        "udp://tracker.torrent.eu.org:451/announce",
        "udp://open.demonii.com:1337/announce",
        "udp://tracker.dler.org:6969/announce",
        "udp://tracker.tiny-vps.com:6969/announce",
        "udp://explodie.org:6969/announce",
        "udp://tracker1.bt.moack.co.kr:80/announce",
    };'''
new = '''    /**
     * 公共 tracker 列表。
     *
     * 关键：必须包含 HTTP/HTTPS 协议（走 TCP）的 tracker。
     * 因为手机上开着代理（FlClash 等）时，代理一般只转发 TCP，不转发 UDP，
     * 于是全部 udp:// tracker 和 DHT 都不通 → peers 永远为 0 → 卡在「获取种子信息」。
     * HTTP tracker 走 TCP，能穿过代理，实测能拿到 peer。
     */
    private static final String[] EXTRA_TRACKERS = new String[]{
        // ---- HTTP（走 TCP，能穿代理；以下均实测返回 200 并带回 peer） ----
        "http://tracker.opentrackr.org:1337/announce",
        "http://tracker.bittor.pw:1337/announce",
        "http://tracker.openbittorrent.com:80/announce",
        "http://tracker.opentrackr.com:1337/announce",
        "http://open.acgtracker.com:1096/announce",
        "http://tracker.skyts.net:6969/announce",
        // ---- UDP（代理不转发时无效，直连或 UDP 可通时有用） ----
        "udp://tracker.opentrackr.org:1337/announce",
        "udp://open.tracker.cl:1337/announce",
        "udp://tracker.openbittorrent.com:6969/announce",
        "udp://exodus.desync.com:6969/announce",
        "udp://tracker.torrent.eu.org:451/announce",
        "udp://open.demonii.com:1337/announce",
        "udp://tracker.dler.org:6969/announce",
        "udp://tracker.tiny-vps.com:6969/announce",
        "udp://explodie.org:6969/announce",
        "udp://tracker1.bt.moack.co.kr:80/announce",
    };'''
if old in d:
    d = d.replace(old, new, 1); n += 1; print('tracker HTTP 优先 OK')
else:
    print('MISS tracker')

# ---------- 2) 超时从 180s 缩到 90s，并在超时时给出可操作提示 ----------
old = '''                        cb.onState(String.format(Locale.US, "获取种子信息… peers=%d", st.numPeers()));
                        if (System.currentTimeMillis() - t0 > 180000) {
                            cb.onError("长时间拿不到种子元数据，可能没有可用 peer");
                            return;
                        }'''
new = '''                        cb.onState(String.format(Locale.US, "获取种子信息… peers=%d", st.numPeers()));
                        if (System.currentTimeMillis() - t0 > 90000) {
                            // 明确告知原因与可操作方案，而不是只说「没有 peer」
                            cb.onError("90 秒内没连上做种者。若是挂了代理，请关掉代理或改用支持 UDP 的代理"
                                    + "（BT 需要 UDP，多数代理只转发 TCP）；也可换 WiFi 重试。");
                            return;
                        }'''
if old in d:
    d = d.replace(old, new, 1); n += 1; print('超时提示 OK')
else:
    print('MISS 超时提示')

# ---------- 3) 允许 DHT 之外还能用 LSD（本地发现）+ 打开 uTP over TCP 兜底 ----------
old = '''                    sp.mixedModeAlgorithm(0);'''
new = '''                    // 本地服务发现：同一局域网有其他 BT 客户端时能直接发现（不需 UDP tracker）
                    try{
                        sp.setBoolean(
                            org.libtorrent4j.swig.settings_pack.bool_types.enable_lsd.swigValue(), true);
                    }catch(Throwable t3){}'''
if old in d:
    d = d.replace(old, new, 1); n += 1; print('LSD OK')
else:
    # mixedModeAlgorithm 之前被删过，这里跳过
    print('SKIP LSD（锚点不存在）')

io.open(P, 'w', encoding='utf-8').write(d)
print('changed:', n)