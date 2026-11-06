# -*- coding: utf-8 -*-
# 1) 入队时 saveDlTasks()
# 2) 任务完成/失败移除后 saveDlTasks()
# 3) onCreate 里 restoreDlTasks()
import io

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/MainActivity.java'
d = io.open(P, encoding='utf-8').read()
n = 0

# 1) enqueueDownload 里加保存
old1 = '''        downloadQueue.add(new DlTask(url, tag, ext, cat, mixDir));
        Log.i("DYP", "DL_QUEUE + " + tag + " cat=" + cat + " 总计=" + downloadQueue.size());'''
new1 = '''        downloadQueue.add(new DlTask(url, tag, ext, cat, mixDir));
        Log.i("DYP", "DL_QUEUE + " + tag + " cat=" + cat + " 总计=" + downloadQueue.size());
        saveDlTasks();   // 持久化：退出软件也不丢'''
if old1 in d:
    d = d.replace(old1, new1, 1); n += 1; print('enqueueDownload 保存 OK')
else:
    print('MISS enqueueDownload')

# 2) enqueueSingle / enqueueSingleWithAudio / enqueueDownloadWithAudio
for fn_marker, logmark in [
    ('DL_QUEUE_SINGLE + ', 'enqueueSingle'),
    ('DL_QUEUE_SINGLE_AUDIO + ', 'enqueueSingleWithAudio'),
]:
    idx = d.find(logmark)
    if idx > 0:
        # 找该行结尾，插入 saveDlTasks
        line_end = d.find('\n', idx)
        insert = '\n        saveDlTasks();'
        if 'saveDlTasks' not in d[line_end:line_end + 60]:
            d = d[:line_end] + insert + d[line_end:]
            n += 1
            print(logmark, 'OK')

# enqueueDownloadWithAudio
i2 = d.find('void enqueueDownloadWithAudio(')
if i2 > 0:
    seg_end = d.find('\n    }', i2)
    seg = d[i2:seg_end]
    if 'saveDlTasks' not in seg:
        d = d[:seg_end] + '\n        saveDlTasks();' + d[seg_end:]
        n += 1
        print('enqueueDownloadWithAudio OK')

io.open(P, 'w', encoding='utf-8').write(d)
print('changed:', n)