# -*- coding: utf-8 -*-
"""
【第 1 项】启动强制停在解析页。

现状：onInit 里已经有 switchTab(0)，但有几条延迟回调可能在之后把页签切走：
  · loadBatchList()（600ms 后）
  · loadListPools()（同一个 600ms 任务里）
  · refreshHistory()
  · applyTheme 触发的 refreshThemeDependents() → switchTab(settingsOrCurrentTab)

用户反馈「每次启动都在别的页」就是这个。

改法：加一个启动保护窗口（2 秒），
      窗口内除了用户主动点页签，其他任何 switchTab 都拒绝，
      并且窗口结束时强制再 switchTab(0) 一次兜底。
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


# ============================================================
# 1. 加启动窗口字段 + switchTab 里拦截
# ============================================================
rep('1-switchTab拦截',
    '''    void switchTab(int idx){
        try{''',
    '''    /** 启动保护窗口的结束时间。
     *
     *  为什么需要：启动时有一串延迟回调
     *      loadBatchList / loadListPools（600ms）
     *      refreshHistory / applyTheme → refreshThemeDependents
     *  它们内部可能调 switchTab，把用户本来该看到的解析页切走。
     *  用户反馈「每次启动都不在解析页」就是这个原因。
     *
     *  窗口内只允许用户主动点击切页（userTapTab 置 true）。
     */
    volatile long bootGuardUntil = 0L;
    /** true = 本次 switchTab 是用户点页签触发的，不受启动窗口限制 */
    volatile boolean userTapTab = false;

    void switchTab(int idx){
        // 启动窗口内：程序触发的切页一律拒绝，避免被延迟回调带偏
        if(!userTapTab && System.currentTimeMillis() < bootGuardUntil){
            Log.i("DYP", "TAB_BOOT_GUARD 拒绝切换到 idx=" + idx);
            return;
        }
        userTapTab = false;
        try{''')

# ============================================================
# 2. 启动时开窗 + 结束强制回解析页
# ============================================================
rep('2-启动开窗',
    '''        switchTab(0);
        refreshHistory();
        buildSettingsPage();
    }''',
    '''        // 启动保护：先开 2 秒窗口，挡住所有程序触发的切页，
        // 窗口结束再强制落回解析页一次。
        bootGuardUntil = System.currentTimeMillis() + 2000L;
        switchTab(0);
        refreshHistory();
        buildSettingsPage();
        h.postDelayed(new Runnable(){ public void run(){
            try{
                bootGuardUntil = 0L;
                // 再确认一次：如果被别的逻辑切走了，这里拉回来
                switchTab(0);
                Log.i("DYP", "BOOT_TAB_FORCE parse");
            }catch(Throwable t){}
        }}, 2100);
    }''')

# ============================================================
# 3. 用户点页签时置 userTapTab
# ============================================================
rep('3-点页签放行',
    '''        tabParse.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ switchTab(0); }});
        tabList.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ switchTab(1); }});
        tabDownload.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ switchTab(2); }});
        tabHistory.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ switchTab(3); }});
        tabSettings.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ switchTab(4); }});''',
    '''        // 用户主动点页签：先置 userTapTab，绕过启动保护窗口
        tabParse.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){
            userTapTab = true; switchTab(0); }});
        tabList.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){
            userTapTab = true; switchTab(1); }});
        tabDownload.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){
            userTapTab = true; switchTab(2); }});
        tabHistory.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){
            userTapTab = true; switchTab(3); }});
        tabSettings.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){
            userTapTab = true; switchTab(4); }});''')

io.open(P, 'w', encoding='utf-8').write(s)
print('成功:', ok)
print('失败:', miss)