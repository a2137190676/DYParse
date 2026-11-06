# -*- coding: utf-8 -*-
import io

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/MainActivity.java'
d = io.open(P, encoding='utf-8').read()

# 1) 检查 enqueueSingleWithAudio 有没有 saveDlTasks
i = d.find('void enqueueSingleWithAudio(')
j = d.find('\n    }', i)
seg = d[i:j]
print('enqueueSingleWithAudio 有 saveDlTasks:', 'saveDlTasks' in seg)
if 'saveDlTasks' not in seg:
    d = d[:j] + '\n        saveDlTasks();' + d[j:]
    print('补上 OK')

io.open(P, 'w', encoding='utf-8').write(d)

# 2) 看 runOneTask 里任务结束时怎么移除队列
d2 = io.open(P, encoding='utf-8').read()
k = d2.find('void runOneTask(')
print('--- runOneTask 尾部 ---')
seg2 = d2[k:k + 200]
m = d2.find('downloadQueue.remove', k)
print('remove 位置:', d2[:m].count('\n') + 1 if m > 0 else 'MISS')
if m > 0:
    print(repr(d2[m-300:m+150]))