# -*- coding: utf-8 -*-
"""
修「解析完不要跳列表页」：
  只保留：主页解析（showProfileList）、合集解析（showMixList）
  去掉：批量解析（beginBatchParse）、单条解析

用户原话：「能不能解析完不要跳转到列表页面了，除了主页和合集跳转列表页别的不要」
"""
import io

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/MainActivity.java'
s = io.open(P, encoding='utf-8').read()
ok, miss = [], []


def rep(name, old, new):
    global s
    if old in s:
        s = s.replace(old, new, 1)
        ok.append(name)
    else:
        miss.append(name)


# 1) 批量解析：不跳列表（这是用户明确提到的）
rep('1-批量解析不跳',
    '''        Log.i("DYP", "BATCH_START n=" + n + " existing=" + batchCollected.size());
        switchTab(1);   // 切到列表页等结果，不占着解析页防闪烁
        runNextBatch();''',
    '''        Log.i("DYP", "BATCH_START n=" + n + " existing=" + batchCollected.size());
        // 不切页签：用户要求只有主页/合集解析才跳列表页
        runNextBatch();''')

io.open(P, 'w', encoding='utf-8').write(s)
print('成功:', ok)
print('失败:', miss)

# 列出剩余所有 switchTab(1)，供人工确认
print()
print('=== 剩余 switchTab(1) 及上一行 ===')
lines = s.split('\n')
for i, ln in enumerate(lines):
    if 'switchTab(1)' in ln:
        prev = lines[i - 1].strip() if i > 0 else ''
        print('行 %d: %s  <<< %s' % (i + 1, ln.strip(), prev[:60]))