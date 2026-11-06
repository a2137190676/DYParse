# -*- coding: utf-8 -*-
"""给 batchDlDetailBlocked / batchDlNoUrl 接上累加点。"""
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


# 1) detail 失败（DyDetail.fetch 版本，19748 附近）
rep('1-detail失败计数',
    '''                    DypLog.i("BATCH_DETAIL_FAIL id=" + awemeId''',
    '''                    batchDlDetailBlocked++;
                    DypLog.i("BATCH_DETAIL_FAIL id=" + awemeId''')

# 2) 没有直链时计数（进 detail 之前）
rep('2-无直链计数',
    '''        // ---- 回落一：有作品 id → 调 detail 接口取真视频 ----''',
    '''        // 走到这里说明没有可用直链
        batchDlNoUrl++;

        // ---- 回落一：有作品 id → 调 detail 接口取真视频 ----''')

io.open(P, 'w', encoding='utf-8').write(s)
print('成功:', ok)
print('失败:', miss)