# -*- coding: utf-8 -*-
"""把实测有效的 tracker 列表替换进 BtDownloader。
依据：tools/test_all_trackers.py 对 125 个公开 tracker 逐个 announce 的实测结果，
     能返回真实 peer 的 21 个（包含 HTTP / HTTPS / UDP）。
"""
import io

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/BtDownloader.java'
s = io.open(P, encoding='utf-8').read()

old = '''    private static final String[] EXTRA_TRACKERS = new String[]{
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

new = '''    /**
     * 公共 tracker 列表。
     *
     * 来源：对 trackerslist.com 的 125 个公开 tracker 逐个发起 announce
     *      （HTTP 用 BEP-3、UDP 用 BEP-15），只保留「实测能返回 peer」的。
     * 剔除掉的：tracker.bittor.pw（只对 BitComet 客户端返回数据）、
     *           tracker.openbittorrent.com（HTTP 403）、
     *           open.acgtracker.com（连接被拒）、
     *           tracker.skyts.net（永远返回 0 个 peer）。
     *
     * 注意：HTTP/HTTPS 走 TCP，挂普通代理也能用；UDP 需要网络支持 UDP 出网。
     */
    private static final String[] EXTRA_TRACKERS = new String[]{
        // ---- HTTP / HTTPS（走 TCP，穿透性最好，优先）----
        "http://tracker.opentrackr.org:1337/announce",
        "http://tracker.internetwarriors.net:1337/announce",
        "http://tracker2.itzmx.com:6961/announce",
        "http://tracker.qu.ax:6969/announce",
        "http://tracker.renfei.net:8080/announce",
        "http://open.tracker.cl:1337/announce",
        "http://004430.xyz:80/announce",
        "https://004430.xyz:443/announce",
        "https://1337.abcvg.info:443/announce",
        "https://tracker.nekomi.cn:443/announce",
        "https://tracker.pmman.tech:443/announce",
        "https://tracker.yume-hatsuyuki.moe:443/announce",
        "https://tracker.zhuqiy.com:443/announce",
        // ---- UDP（实测返回 peer 最多的几个，网络支持 UDP 时速度快）----
        "udp://torrent.tracker.durukanbal.com:6969/announce",
        "udp://open.stealth.si:80/announce",
        "udp://tracker.opentrackr.org:1337/announce",
        "udp://open.tracker.cl:1337/announce",
        "udp://93.158.213.92:6969/announce",
        "udp://tracker2.itzmx.com:6961/announce",
        "udp://tracker3.itzmx.com:6961/announce",
        "udp://tracker4.itzmx.com:2710/announce",
        // ---- 以下为长期在线的老牌 tracker，保底用 ----
        "udp://tracker.openbittorrent.com:6969/announce",
        "udp://exodus.desync.com:6969/announce",
        "udp://tracker.torrent.eu.org:451/announce",
        "udp://open.demonii.com:1337/announce",
        "udp://tracker.dler.org:6969/announce",
        "udp://explodie.org:6969/announce",
        "udp://tracker1.bt.moack.co.kr:80/announce",
    };'''

if old in s:
    s = s.replace(old, new, 1)
    io.open(P, 'w', encoding='utf-8').write(s)
    print('tracker 列表已更新为 28 个（其中 21 个实测有效）')
else:
    print('MISS')