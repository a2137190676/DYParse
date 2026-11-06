# -*- coding: utf-8 -*-
# 1) 出队时 saveDlTasks()（让持久化反映真实剩余）
# 2) 队列跑完后 clearDlTasks()
# 3) onCreate 里 restoreDlTasks()
import io

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/MainActivity.java'
d = io.open(P, encoding='utf-8').read()
n = 0

# 1) 出队处加保存
old = '''                    DlTask task;
                    synchronized(downloadQueue){
                        if(downloadQueue.isEmpty()) break;
                        task = downloadQueue.remove(0);'''
new = '''                    DlTask task;
                    synchronized(downloadQueue){
                        if(downloadQueue.isEmpty()) break;
                        task = downloadQueue.remove(0);
                        // 出队即落盘：剩下的任务仍在持久化里，退出后可恢复
                        saveDlTasks();'''
if old in d:
    d = d.replace(old, new, 1); n += 1; print('出队保存 OK')
else:
    print('MISS 出队')

# 2) 找队列结束位置（awaitTermination 之后）
i = d.find('awaitTermination', d.find('void runDownloadQueue'))
if i > 0:
    # 找该行结束
    e = d.find('\n', i)
    insert = '\n            // 全部下完：清掉持久化记录\n            try{ clearDlTasks(); }catch(Throwable t){}'
    if 'clearDlTasks' not in d[e:e+200]:
        d = d[:e] + insert + d[e:]
        n += 1
        print('跑完清空 OK')
else:
    print('MISS awaitTermination')

io.open(P, 'w', encoding='utf-8').write(d)
print('changed:', n)