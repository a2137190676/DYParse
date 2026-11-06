# -*- coding: utf-8 -*-
# 拿到元数据后：给 torrent 追加公共 tracker（显著提速）
import io

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/BtDownloader.java'
d = io.open(P, encoding='utf-8').read()

# 在 onMeta 回调之前插入 tracker 注入
old = '''                    // 文件列表（只发一次）
                    if (!metaSent[0]) {'''
new = '''                    // 拿到元数据后：追加公共 tracker（很多磁力不自带 tracker）
                    // 这是提速的关键一步：只靠 DHT 找 peer 很慢，tracker 能一次性拿到大量 peer
                    if (!trackerAdded[0]) {
                        trackerAdded[0] = true;
                        try {
                            java.util.List<String> tks = new java.util.ArrayList<String>();
                            for (String t : EXTRA_TRACKERS) tks.add(t);
                            // 保留种子自带的 tracker
                            try{
                                java.util.List<String> own = h.trackers();
                                if(own != null) for(String t : own) if(!tks.contains(t)) tks.add(t);
                            }catch(Throwable t){}
                            h.addTrackers(tks);
                            Log.i(TAG, "BT_TRACKERS_ADDED n=" + tks.size());
                        } catch (Throwable te) {
                            Log.i(TAG, "BT_TRACKERS_ERR " + te);
                        }
                    }

                    // 文件列表（只发一次）
                    if (!metaSent[0]) {'''

if old in d:
    d = d.replace(old, new, 1)
    print('tracker 注入 OK')
else:
    print('MISS tracker')

# 加 trackerAdded 标志
old2 = '''                final boolean[] metaSent = new boolean[]{ false };'''
new2 = '''                final boolean[] metaSent = new boolean[]{ false };
                final boolean[] trackerAdded = new boolean[]{ false };'''
d2 = io.open(P, encoding='utf-8').read()
if old2 in d2:
    d2 = d2.replace(old2, new2, 1)
    print('标志 OK')
else:
    print('MISS 标志')

io.open(P, 'w', encoding='utf-8').write(d2 if old2 in d2 else d)
print('done')