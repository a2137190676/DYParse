# -*- coding: utf-8 -*-
"""补齐磁力多任务所需的辅助方法：
   btHashOf(src)      —— 从磁力链接或 .torrent 文件算出 infoHash（大写 hex）
   btFinishHash(src)  —— 任务结束/失败时把 hash 从运行集合里摘掉
   并把 targetHash 传给 BtDownloader.start
"""
import io

MA = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/MainActivity.java'
m = io.open(MA, encoding='utf-8').read()
n = 0

# ---------- 1) 两个辅助方法 ----------
old = '''    /** 磁力/种子的友好显示名（本地种子用文件名，不用路径） */'''
new = '''    /** 从磁力链接或本地 .torrent 算出 infoHash（大写 hex，取不到返回空串） */
    String btHashOf(String src){
        try{
            if(src == null || src.length() == 0) return "";
            if(src.toLowerCase().endsWith(".torrent")){
                String[] ins = BtDownloader.inspectTorrent(new java.io.File(src));
                if(ins != null && ins[0] != null) return ins[0].toUpperCase();
                return "";
            }
            int p = src.toLowerCase().indexOf("btih:");
            if(p < 0) return "";
            String h = src.substring(p + 5);
            int amp = h.indexOf('&');
            if(amp >= 0) h = h.substring(0, amp);
            // 去掉可能残留的空白
            h = h.trim();
            return h.length() >= 32 ? h.toUpperCase() : "";
        }catch(Throwable t){ return ""; }
    }

    /** 磁力任务结束：从「正在跑」集合里摘掉，并同步 btRunning 总标志 */
    void btFinishHash(String src){
        try{
            String h = btHashOf(src);
            if(h.length() > 0) btRunningHashes.remove(h);
            boolean any = false;
            synchronized(btRunningHashes){ any = !btRunningHashes.isEmpty(); }
            btRunning = any;
            Log.i("DYP", "BT_FINISH_HASH hash=" + h + " remain=" + btRunningHashes.size()
                    + " btRunning=" + btRunning);
        }catch(Throwable t){ Log.i("DYP", "BT_FINISH_HASH_ERR " + t); }
    }

    /** 磁力/种子的友好显示名（本地种子用文件名，不用路径） */'''
if old in m:
    m = m.replace(old, new, 1); n += 1; print('1) btHashOf / btFinishHash OK')
else:
    print('MISS 1')

# ---------- 2) 把 targetHash 传给 BtDownloader ----------
old = '''        BtDownloader.start(magnet, saveDir, new BtDownloader.SimpleCb(){'''
new = '''        BtDownloader.start(magnet, saveDir, new BtDownloader.SimpleCb(){'''
n += 0

# 找到 start 调用末尾的 `});`（SimpleCb 结束），改成 `}, myHash);`
# 用唯一锚点：onSelectFiles 结尾那段
old = '''                int[] out = new int[sel.size()];
                for(int i = 0; i < out.length; i++) out[i] = sel.get(i);
                return out;
            }
        });
    }'''
new = '''                int[] out = new int[sel.size()];
                for(int i = 0; i < out.length; i++) out[i] = sel.get(i);
                return out;
            }
        }, myHash);
    }'''
if old in m:
    m = m.replace(old, new, 1); n += 1; print('2) 传 targetHash OK')
else:
    print('MISS 2')

io.open(MA, 'w', encoding='utf-8').write(m)
print('changed:', n)