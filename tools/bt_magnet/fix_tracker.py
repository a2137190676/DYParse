# -*- coding: utf-8 -*-
"""修复 tracker 注入的类型：trackers() 返回 AnnounceEntry，addTracker 也需 AnnounceEntry"""
import io

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/BtDownloader.java'
d = io.open(P, encoding='utf-8').read()

old = '''                            java.util.List<String> tks = new java.util.ArrayList<String>();
                            for (String t : EXTRA_TRACKERS) tks.add(t);
                            // 保留种子自带的 tracker
                            try{
                                java.util.List<String> own = h.trackers();
                                if(own != null) for(String t : own) if(!tks.contains(t)) tks.add(t);
                            }catch(Throwable t){}
                            // 用 announceEntry 逐条加（addTrackers 在部分版本不存在）
                            for(String t : tks){
                                try{ h.addTracker(t); }catch(Throwable t2){}
                            }
                            Log.i(TAG, "BT_TRACKERS_ADDED n=" + tks.size());'''

new = '''                            java.util.List<String> tks = new java.util.ArrayList<String>();
                            for (String t : EXTRA_TRACKERS) tks.add(t);
                            // 保留种子自带的 tracker（trackers() 返回 AnnounceEntry，取 url）
                            try{
                                java.util.List<org.libtorrent4j.AnnounceEntry> own = h.trackers();
                                if(own != null){
                                    for(org.libtorrent4j.AnnounceEntry ae : own){
                                        try{
                                            String us = ae.url();
                                            if(us != null && us.length() > 0 && !tks.contains(us)) tks.add(us);
                                        }catch(Throwable t2){}
                                    }
                                }
                            }catch(Throwable t){}
                            // 逐条转成 AnnounceEntry 加进去
                            int added = 0;
                            for(String t : tks){
                                try{
                                    h.addTracker(new org.libtorrent4j.AnnounceEntry(t));
                                    added++;
                                }catch(Throwable t2){}
                            }
                            Log.i(TAG, "BT_TRACKERS_ADDED n=" + added + "/" + tks.size());'''

if old in d:
    d = d.replace(old, new, 1)
    io.open(P, 'w', encoding='utf-8').write(d)
    print('AnnounceEntry 修复 OK')
else:
    print('MISS')