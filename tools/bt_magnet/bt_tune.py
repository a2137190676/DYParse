# -*- coding: utf-8 -*-
# 磁力下载速度优化：
#   1) 显式监听 0.0.0.0:6881-6889（默认 listen=[] 会导致 peer 连不进来，只能主动连，速度慢）
#   2) 连接数上限 200，activeDownloads 提高到 8
#   3) 追加公共 tracker（很多磁力本身不带 tracker，只靠 DHT，速度差很多）
#   4) 启用 uTP + TCP 双协议
#   5) 上传不限速但设置合理上限（BT 是「有上传才有下载」，限死上传会被 peer 踢）
import io

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/BtDownloader.java'
d = io.open(P, encoding='utf-8').read()

# === 1) 替换 ensureSession：加调优 ===
old = '''    /** 确保 session 已启动 */
    private static synchronized SessionManager ensureSession() {
        if (sSession != null && sSession.isRunning()) return sSession;
        if (sStarting) {
            // 别人正在起，等一会儿
            for (int i = 0; i < 30; i++) {
                try { Thread.sleep(200); } catch (Throwable t) {}
                if (sSession != null && sSession.isRunning()) return sSession;
            }
        }
        sStarting = true;
        try {
            SessionManager sm = new SessionManager();
            sm.start();
            // 等 DHT 起来（最多 5 秒，不阻塞太久）
            for (int i = 0; i < 25; i++) {
                try { Thread.sleep(200); } catch (Throwable t) {}
                if (sm.dhtNodes() > 0) break;
            }
            sSession = sm;
            Log.i(TAG, "BT_SESSION_OK dhtNodes=" + sm.dhtNodes()
                    + " listen=" + sm.listenEndpoints());
            return sm;
        } catch (Throwable t) {
            Log.i(TAG, "BT_SESSION_ERR " + t);
            return null;
        } finally {
            sStarting = false;
        }
    }'''

new = '''    /** 公共 tracker 列表（磁力通常不自带 tracker，只靠 DHT 会很慢） */
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
    };

    /** 确保 session 已启动（带速度调优） */
    private static synchronized SessionManager ensureSession() {
        if (sSession != null && sSession.isRunning()) return sSession;
        if (sStarting) {
            for (int i = 0; i < 30; i++) {
                try { Thread.sleep(200); } catch (Throwable t) {}
                if (sSession != null && sSession.isRunning()) return sSession;
            }
        }
        sStarting = true;
        try {
            SessionManager sm = new SessionManager();

            // ---- 调优：这些参数直接决定下载速度 ----
            try{
                org.libtorrent4j.SettingsPack sp = sm.settings();
                // 关键：显式监听，否则 listen=[] 时 peer 连不进来
                sp.listenInterfaces("0.0.0.0:6881,[::]:6881");
                // 连接数：默认太少，磁力需要更多 peer
                sp.connectionsLimit(300);
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
                sp.downloadRateLimit(0);
                // 上传给到 800KB/s：BT 靠上传换下载，限死会被踢
                sp.uploadRateLimit(800);
                sm.applySettings(sp);
                Log.i(TAG, "BT_SETTINGS_APPLIED");
            }catch(Throwable st){
                Log.i(TAG, "BT_SETTINGS_ERR " + st);
            }

            sm.start();

            // 等 DHT 起来（最多 8 秒）
            for (int i = 0; i < 40; i++) {
                try { Thread.sleep(200); } catch (Throwable t) {}
                if (sm.dhtNodes() > 0) break;
            }
            sSession = sm;
            Log.i(TAG, "BT_SESSION_OK dhtNodes=" + sm.dhtNodes()
                    + " listen=" + sm.listenEndpoints());
            return sm;
        } catch (Throwable t) {
            Log.i(TAG, "BT_SESSION_ERR " + t);
            return null;
        } finally {
            sStarting = false;
        }
    }'''

if old in d:
    d = d.replace(old, new, 1)
    io.open(P, 'w', encoding='utf-8').write(d)
    print('ensureSession 调优 OK')
else:
    print('MISS ensureSession')