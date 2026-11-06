# -*- coding: utf-8 -*-
# 单下载分离：doDownload 里的 4 处入队改成 silent
import io

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/MainActivity.java'
d = io.open(P, encoding='utf-8').read()

# 1) 加 enqueueSingleWithAudio
anchor = '''    /** 单个下载入队：不进下载列表，只在解析页进度条显示 */'''
helper = '''    /** 单个下载（带音轨）：不进下载列表 */
    void enqueueSingleWithAudio(String url, String tag, String ext, String cat,
                                String mixDir, String audioUrl){
        DlTask t = new DlTask(url, tag, ext, cat, mixDir, "", "", audioUrl);
        t.silent = true;
        downloadQueue.add(t);
        Log.i("DYP", "DL_QUEUE_SINGLE_AUDIO + " + tag + " cat=" + cat);
        if(!dlRunning){
            dlRunning = true;
            runDownloadQueue();
        }
    }

''' + anchor
if anchor in d and 'enqueueSingleWithAudio' not in d:
    d = d.replace(anchor, helper, 1); print('enqueueSingleWithAudio OK')
else:
    print('MISS/EXISTS')

# 2) doDownload 内 4 处替换（只在 doDownload 范围内）
i = d.find('    void doDownload(){')
j = d.find('\n    void ', i + 10)
seg = d[i:j]
before = seg.count('enqueueDownloadWithAudio(')
seg2 = seg.replace('enqueueDownloadWithAudio(', 'enqueueSingleWithAudio(')
d = d[:i] + seg2 + d[j:]
print('doDownload 内替换', before, '处')

io.open(P, 'w', encoding='utf-8').write(d)
print('done')