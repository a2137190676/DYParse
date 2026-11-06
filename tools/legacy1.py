# -*- coding: utf-8 -*-
# 遗留项 1：单个下载不进「下载」列表（只批量/全部才进）
#   做法：DlTask 加 silent 标记；单个下载传 silent=true，runOneTask 里不建行
import io

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/MainActivity.java'
d = io.open(P, encoding='utf-8').read()
n = 0

# 1) DlTask 加字段
old = '''        /** YouTube direct：独立音轨直链，非空时下载后与视频轨本地 mux */
        final String audioUrl;'''
new = '''        /** YouTube direct：独立音轨直链，非空时下载后与视频轨本地 mux */
        final String audioUrl;
        /** true = 单个下载，不进「下载」页列表（只在解析页进度条显示） */
        boolean silent = false;'''
if old in d:
    d = d.replace(old, new, 1); n += 1; print('DlTask 字段 OK')
else:
    print('MISS DlTask')

# 2) 加一个 silent 版的 enqueue
anchor = '''    /** 带独立音轨入队（YouTube direct 双轨下载） */'''
helper = '''    /** 单个下载入队：不进下载列表，只在解析页进度条显示 */
    void enqueueSingle(String url, String tag, String ext, String cat, String mixDir){
        DlTask t = new DlTask(url, tag, ext, cat, mixDir);
        t.silent = true;
        downloadQueue.add(t);
        Log.i("DYP", "DL_QUEUE_SINGLE + " + tag + " cat=" + cat);
        if(!dlRunning){
            dlRunning = true;
            runDownloadQueue();
        }
    }

''' + anchor
if anchor in d and 'void enqueueSingle' not in d:
    d = d.replace(anchor, helper, 1); n += 1; print('enqueueSingle OK')
else:
    print('MISS enqueueSingle')

io.open(P, 'w', encoding='utf-8').write(d)
print('changed:', n)