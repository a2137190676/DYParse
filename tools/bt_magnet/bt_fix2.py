# -*- coding: utf-8 -*-
# 修：sm.settings() 在 start() 之前返回 null（NullPointerException）
# 正确做法：先 start()，再 settings() 拿当前配置，改完 applySettings()
import io

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/BtDownloader.java'
d = io.open(P, encoding='utf-8').read()

old = '''            SessionManager sm = new SessionManager();

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

            sm.start();'''

new = '''            SessionManager sm = new SessionManager();
            // 必须先 start，settings() 在 start 之前返回 null
            sm.start();

            // ---- 调优：这些参数直接决定下载速度 ----
            try{
                org.libtorrent4j.SettingsPack sp = sm.settings();
                if (sp != null) {
                    // 关键：显式监听，否则 listen=[] 时 peer 连不进来，只能主动外连，速度差
                    sp.listenInterfaces("0.0.0.0:6881,[::]:6881");
                    // 连接数：默认太少，磁力需要更多 peer 并行拉取
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
                    // 上传给到 800KB/s：BT 靠上传换下载，限死会被 peer 踢
                    sp.uploadRateLimit(800);
                    sm.applySettings(sp);
                    Log.i(TAG, "BT_SETTINGS_APPLIED listen=" + sm.listenEndpoints());
                } else {
                    Log.i(TAG, "BT_SETTINGS_NULL");
                }
            }catch(Throwable st){
                Log.i(TAG, "BT_SETTINGS_ERR " + st);
            }'''

if old in d:
    d = d.replace(old, new, 1)
    io.open(P, 'w', encoding='utf-8').write(d)
    print('start 顺序修正 OK')
else:
    print('MISS')
    i = d.find('SessionManager sm = new SessionManager();')
    print(repr(d[i:i + 900]))