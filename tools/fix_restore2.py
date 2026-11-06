# -*- coding: utf-8 -*-
"""A) 恢复任务不自动下载：按行号精确替换"""
import io, re

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/MainActivity.java'
s = io.open(P, encoding='utf-8').read()
lines = s.split('\n')

# 定位 restoreDlTasks 函数体里的 runDownloadQueue 块
start = None
for i, ln in enumerate(lines):
    if 'void restoreDlTasks()' in ln:
        start = i
        break

if start is None:
    print('未找到 restoreDlTasks')
    raise SystemExit(1)

# 在该函数范围内找 runDownloadQueue()
end = None
for i in range(start, min(start + 80, len(lines))):
    if 'DL_TASKS_RESTORE_ERR' in lines[i]:
        end = i
        break

block = lines[start:end]
# 从后往前找 'if(!dlRunning){' 并整块替换
idx = None
for i, ln in enumerate(block):
    if 'if(!dlRunning){' in ln:
        idx = i
        break

if idx is None:
    print('未找到 if(!dlRunning){')
    raise SystemExit(1)

# 打印原始块便于确认
print('--- 原始待删块 ---')
for ln in block[idx:idx + 6]:
    print(repr(ln))

indent = re.match(r'(\s*)', block[idx]).group(1)

new_block = [
    indent + '// 关键：恢复的未完成任务【不自动重新下载】。',
    indent + '// 之前这里直接 runDownloadQueue()，导致用户新下 1 个视频时，',
    indent + '// 上次失败的任务被一起塞进队列 → 下载页显示「1/2」，用户以为多下了一个；',
    indent + '// 而且失败任务的直链多半已过期，自动重下还是 403，白费流量。',
    indent + '// 现在只把任务渲染到「下载」页，由用户手动点「继续」再下。',
    indent + 'synchronized(dlSavedPending){',
    indent + '    dlSavedPending.clear();',
    indent + '    for(DlTask t : todo) dlSavedPending.add(t);',
    indent + '}',
    indent + 's("发现 " + todo.size() + " 个未完成任务，可在「下载」页点「继续」重下");',
    indent + 'showHintToast("上次有 " + todo.size() + " 个未完成任务，已放到下载页");',
    indent + 'dlRefreshHead();',
    indent + 'showSavedDlTasks();',
]

# 找到块的结束（遇到 dlRunning = true; 和 runDownloadQueue(); 和 }）
j = idx
while j < len(block) and 'runDownloadQueue();' not in block[j]:
    j += 1
j += 1
# 跳过紧跟的 }
while j < len(block) and block[j].strip() != '}':
    j += 1
j += 1

print('--- 删除 %d 行，替换为 %d 行 ---' % (j - idx, len(new_block)))
block = block[:idx] + new_block + block[j:]
lines[start:end] = block

s = '\n'.join(lines)
io.open(P, 'w', encoding='utf-8').write(s)
print('完成')