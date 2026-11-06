# -*- coding: utf-8 -*-
"""
补：collectSingleItem 收集完没落盘。

日志证据：
    SINGLE_COLLECT img=true imgs=5 live=0 total=4     ← 收进了 itemsFromSingle
    但没有 POOLS_SAVE                                  ← 没写盘

后果：单条解析的结果只在内存里，App 一重启就没了。
（用户要的「也落盘」没真正做到）

改法：两个出口（新增条目 / 更新已有条目）都调 saveListPools()。
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


# 出口 1：更新已有条目
rep('1-更新后落盘',
    '''                        Log.i("DYP", "SINGLE_COLLECT_UPD img=" + hasImgs
                                + " imgs=" + o.imgs.size() + " url=" + u);
                        return;''',
    '''                        Log.i("DYP", "SINGLE_COLLECT_UPD img=" + hasImgs
                                + " imgs=" + o.imgs.size() + " url=" + u);
                        // 更新完立刻落盘，否则重启后这条改动丢了
                        saveListPools();
                        return;''')

# 出口 2：新增条目
rep('2-新增后落盘',
    '''                Log.i("DYP", "SINGLE_COLLECT img=" + hasImgs
                        + " imgs=" + it.imgs.size() + " live=" + liveOf(it.dyns)
                        + " total=" + itemsFromSingle.size() + " url=" + u);''',
    '''                Log.i("DYP", "SINGLE_COLLECT img=" + hasImgs
                        + " imgs=" + it.imgs.size() + " live=" + liveOf(it.dyns)
                        + " total=" + itemsFromSingle.size() + " url=" + u);
                // 新增后落盘（用户明确要求：单条解析也要保存，重启还在）
                saveListPools();''')

# 出口 3：collectBatchItem 里也有新增/更新，同样补上
rep('3-批量收集落盘',
    '''            Log.i("DYP", "BATCH_COLLECT now=" + n + " url=" + u);''',
    '''            Log.i("DYP", "BATCH_COLLECT now=" + n + " url=" + u);
            saveListPools();''')

io.open(P, 'w', encoding='utf-8').write(s)
print('成功:', ok)
print('失败:', miss)