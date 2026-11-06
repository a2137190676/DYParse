# -*- coding: utf-8 -*-
# 磁力提速：把 libtorrent 里所有跟「并发拉取」相关的参数全部拉满
import io

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/BtDownloader.java'
d = io.open(P, encoding='utf-8').read()

old = '''                    sp.connectionsLimit(300);
                    sp.activeLimit(300);
                    // 同时活跃下载数（多文件种）
                    sp.activeDownloads(10);
                    sp.activeSeeds(10);
                    // peer 列表容量
                    sp.maxPeerlistSize(2000);
                    // DHT 节点上限
                    sp.activeDhtLimit(500);
                    // tracker 并发
                    sp.activeTrackerLimit(30);
                    // 不限制下载（0 = 不限）
                    sp.downloadRateLimit(0);'''

new = '''                    // ===== 连接数：拉满 =====
                    sp.connectionsLimit(1200);
                    sp.activeLimit(1200);
                    // 同时活跃下载数（多文件种）
                    sp.activeDownloads(50);
                    sp.activeSeeds(50);
                    sp.activeChecking(8);
                    // peer 列表容量
                    sp.maxPeerlistSize(4000);
                    // DHT 节点上限
                    sp.activeDhtLimit(2000);
                    sp.activeLsdLimit(200);
                    // tracker 并发
                    sp.activeTrackerLimit(100);
                    // 不限制下载（0 = 不限）
                    sp.downloadRateLimit(0);

                    // ===== 请求队列：这是单 peer 吞吐的关键 =====
                    // 默认 max_out_request_queue 只有 500，单个 peer 一次只能挂 500 个 block 请求，
                    // 提高到 2000 能让每个 peer 同时拉更多块，速度直接翻倍
                    try{
                        org.libtorrent4j.swig.settings_pack.int_types IT =
                            org.libtorrent4j.swig.settings_pack.int_types.max_out_request_queue;
                        sp.setInteger(IT.swigValue(), 2000);
                        sp.setInteger(
                            org.libtorrent4j.swig.settings_pack.int_types.max_allowed_in_request_queue.swigValue(), 2000);
                        // 请求超时缩短，卡住的请求尽快重发
                        sp.setInteger(
                            org.libtorrent4j.swig.settings_pack.int_types.request_timeout.swigValue(), 8);
                        sp.setInteger(
                            org.libtorrent4j.swig.settings_pack.int_types.peer_timeout.swigValue(), 60);
                        // 连接建立速度：默认 30 个/秒太保守
                        sp.setInteger(
                            org.libtorrent4j.swig.settings_pack.int_types.connection_speed.swigValue(), 200);
                        // 单次 unchoke 数量
                        sp.setInteger(
                            org.libtorrent4j.swig.settings_pack.int_types.unchoke_slots_limit.swigValue(), 200);
                        // 磁盘队列放宽，避免磁盘成为瓶颈
                        sp.setInteger(
                            org.libtorrent4j.swig.settings_pack.int_types.max_queued_disk_bytes.swigValue(),
                            512 * 1024 * 1024);
                        // DHT 找 peer 更积极
                        sp.setInteger(
                            org.libtorrent4j.swig.settings_pack.int_types.dht_max_peers_reply.swigValue(), 500);
                        sp.setInteger(
                            org.libtorrent4j.swig.settings_pack.int_types.dht_search_branching.swigValue(), 16);
                        sp.setInteger(
                            org.libtorrent4j.swig.settings_pack.int_types.dht_max_peers.swigValue(), 4000);
                        // tracker 一次多要 peer
                        sp.setInteger(
                            org.libtorrent4j.swig.settings_pack.int_types.num_want.swigValue(), 500);
                        Log.i(TAG, "BT_TURBO_OK requests=2000 conn=1200 unchoke=200");
                    }catch(Throwable tt){
                        Log.i(TAG, "BT_TURBO_ERR " + tt);
                    }

                    // ===== 布尔开关 =====
                    try{
                        org.libtorrent4j.swig.settings_pack.bool_types BT;
                        sp.setBoolean(
                            org.libtorrent4j.swig.settings_pack.bool_types.allow_multiple_connections_per_ip.swigValue(),
                            true);   // 同一 IP 允许多连接（NAT 后尤其重要）
                        sp.setBoolean(
                            org.libtorrent4j.swig.settings_pack.bool_types.enable_outgoing_utp.swigValue(),
                            true);
                        sp.setBoolean(
                            org.libtorrent4j.swig.settings_pack.bool_types.enable_incoming_utp.swigValue(),
                            true);
                        sp.setBoolean(
                            org.libtorrent4j.swig.settings_pack.bool_types.enable_outgoing_tcp.swigValue(),
                            true);
                        sp.setBoolean(
                            org.libtorrent4j.swig.settings_pack.bool_types.enable_incoming_tcp.swigValue(),
                            true);
                        sp.setBoolean(
                            org.libtorrent4j.swig.settings_pack.bool_types.prioritize_partial_pieces.swigValue(),
                            true);   // 优先续传已有的块
                        sp.setBoolean(
                            org.libtorrent4j.swig.settings_pack.bool_types.announce_to_all_trackers.swigValue(),
                            true);
                        sp.setBoolean(
                            org.libtorrent4j.swig.settings_pack.bool_types.announce_to_all_tiers.swigValue(),
                            true);
                        sp.setBoolean(
                            org.libtorrent4j.swig.settings_pack.bool_types.prefer_udp_trackers.swigValue(),
                            true);
                        sp.setBoolean(
                            org.libtorrent4j.swig.settings_pack.bool_types.enable_upnp.swigValue(),
                            true);   // 尝试自动打洞
                        sp.setBoolean(
                            org.libtorrent4j.swig.settings_pack.bool_types.enable_natpmp.swigValue(),
                            true);
                        sp.setBoolean(
                            org.libtorrent4j.swig.settings_pack.bool_types.smooth_connects.swigValue(),
                            true);
                        sp.setBoolean(
                            org.libtorrent4j.swig.settings_pack.bool_types.use_dht_as_fallback.swigValue(),
                            false);  // DHT 一直用，不是兜底
                        Log.i(TAG, "BT_TURBO_BOOL_OK utp=1 upnp=1 multiIP=1");
                    }catch(Throwable tt){
                        Log.i(TAG, "BT_TURBO_BOOL_ERR " + tt);
                    }'''

if old in d:
    d = d.replace(old, new, 1)
    io.open(P, 'w', encoding='utf-8').write(d)
    print('turbo OK')
else:
    print('MISS')
    i = d.find('sp.connectionsLimit(300);')
    print(repr(d[i-100:i+300]))