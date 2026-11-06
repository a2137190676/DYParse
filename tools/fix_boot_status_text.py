# -*- coding: utf-8 -*-
"""
【1】启动时解析页挂着「主页解析完成」的状态文字。

【现象】用户截图：一打开 App，解析页状态栏就写着
        「主页解析完成 · N 条 · 已切到列表页」，
        但用户这次根本没解析任何东西。

【根因】showProfileList() 末尾：
        if(restoringList){ 不写 }
        else{ s("主页解析完成 · " + n + " 条 · 已切到列表页"); switchTab(1); }

        restoringList 只在 loadBatchList() 里被置 true，
        而启动时还有别的入口会调 showProfileList：
          · switchListSub()（启动后默认切到 idx=1）
          · loadListPools()
        这些路径 restoringList 已经是 false，于是又写了一遍状态文字。

【改法】再加一道时间闸：启动后 3 秒内的 showProfileList 一律不写状态文字。
        这个窗口和之前的 bootGuardUntil（2 秒）配合，
        覆盖所有启动期的延迟回调。
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


# 1) 加启动时间戳字段
rep('1-启动时间戳',
    '''    volatile boolean restoringList = false;''',
    '''    volatile boolean restoringList = false;
    /** App 启动时间（毫秒）。用来判断「这是不是启动期的渲染」。 */
    volatile long appBootAt = 0L;''')

# 2) onCreate 里记录
rep('2-记录启动时间',
    '''        DiagLog.init(this);''',
    '''        appBootAt = System.currentTimeMillis();
        DiagLog.init(this);''')

# 3) showProfileList 末尾加时间闸
rep('3-时间闸',
    '''        if(restoringList){
            // 开屏恢复：只把内容渲染出来，不切页签、不写「解析完成」，
            // 否则用户一打开 App 就被拽到列表页，解析页还挂着上一轮的状态文字。
            DypLog.i("LIST_RESTORE_RENDER n=" + n + " (不切页签)");
        }else{
            s("主页解析完成 · " + n + " 条 · 已切到列表页");
            switchTab(1);
        }''',
    '''        // 启动期渲染：只画内容，不写状态文字、不切页签。
        //
        // 两个条件任一成立就认为是启动期：
        //   · restoringList（loadBatchList 正在恢复）
        //   · 距 App 启动不到 3 秒（switchListSub / loadListPools 等路径）
        //
        // 为什么需要第二个条件：restoringList 只在 loadBatchList 里置 true，
        // 而启动时 switchListSub(1) 也会走 showProfileList，
        // 那时 restoringList 已经是 false，于是又写了一遍
        // 「主页解析完成 · N 条 · 已切到列表页」——用户一开 App 就看到这句，
        // 明明什么都没解析。
        boolean bootPeriod = (System.currentTimeMillis() - appBootAt) < 3000L;
        if(restoringList || bootPeriod){
            DypLog.i("LIST_RESTORE_RENDER n=" + n
                    + " (不切页签 restoring=" + restoringList + " boot=" + bootPeriod + ")");
        }else{
            s("主页解析完成 · " + n + " 条 · 已切到列表页");
            switchTab(1);
        }''')

io.open(P, 'w', encoding='utf-8').write(s)
print('成功:', ok)
print('失败:', miss)