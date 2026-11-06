# -*- coding: utf-8 -*-
# DHT 提速：手动喂 bootstrap 节点
#   默认 dhtNodes=3 太少（正常情况下应该是几百个），导致找不到足够 peer
import io

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/BtDownloader.java'
d = io.open(P, encoding='utf-8').read()

# 在 settings 调优块末尾加 DHT bootstrap
old = '''                    sm.applySettings(sp);
                    Log.i(TAG, "BT_SETTINGS_APPLIED listen=" + sm.listenEndpoints());'''

new = '''                    // DHT bootstrap 节点：默认自动的就几个，手动加一批
                    try{
                        sp.setDhtBootstrapNodes(
                            "router.bittorrent.com:6881,"
                          + "router.utorrent.com:6881,"
                          + "dht.transmissionbt.com:6881,"
                          + "dht.aelitis.com:6881,"
                          + "router.bitcomet.com:6881,"
                          + "dht.libtorrent.org:25401");
                    }catch(Throwable t2){
                        Log.i(TAG, "BT_DHT_BOOT_ERR " + t2);
                    }
                    sm.applySettings(sp);
                    Log.i(TAG, "BT_SETTINGS_APPLIED listen=" + sm.listenEndpoints());'''

if old in d:
    d = d.replace(old, new, 1)
    io.open(P, 'w', encoding='utf-8').write(d)
    print('DHT bootstrap OK')
else:
    print('MISS')

# 同时延长等 DHT 的时间（从 8 秒到 15 秒）并打印更详细
old2 = '''            // 等 DHT 起来（最多 8 秒）
            for (int i = 0; i < 40; i++) {
                try { Thread.sleep(200); } catch (Throwable t) {}
                if (sm.dhtNodes() > 0) break;
            }'''
new2 = '''            // 等 DHT 起来（最多 15 秒，节点越多找 peer 越快）
            int lastNodes = 0;
            for (int i = 0; i < 75; i++) {
                try { Thread.sleep(200); } catch (Throwable t) {}
                int nn = sm.dhtNodes();
                if (nn != lastNodes) {
                    lastNodes = nn;
                    Log.i(TAG, "BT_DHT_PROGRESS nodes=" + nn);
                }
                if (nn >= 50) break;   // 50 个节点够用了，不必等满
            }'''
d2 = io.open(P, encoding='utf-8').read()
if old2 in d2:
    d2 = d2.replace(old2, new2, 1)
    io.open(P, 'w', encoding='utf-8').write(d2)
    print('等待逻辑 OK')
else:
    print('MISS 等待逻辑')