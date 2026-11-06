# -*- coding: utf-8 -*-
"""
修「批量下载按钮点了没反应 / 提示列表是空的」。

根因：
showProfileList(list) 只负责把 list 渲染成界面行，
从来没有写进 batchCollected。
而 startBatchDlFromList() 读的是 batchCollected：

    synchronized(batchCollected){
        snapshot = new ArrayList(batchCollected);
    }
    if(snapshot.isEmpty()){
        showHintToast("列表是空的，先解析一些作品");
        return;
    }

主页解析 480 条正常显示，但 batchCollected 是空的，
所以点「批量下载」永远走 empty 分支（提示一闪而过，看着像没反应）。

修法：
showProfileList 开头把 list 同步进 batchCollected，
这样列表页的三个按钮（批量解析 / 主页解析 / 批量下载）用的是同一份数据。
"""
import io

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/MainActivity.java'
s = io.open(P, encoding='utf-8').read()

old = '''    void showProfileList(final java.util.List<ProfileParser.ProfileItem> list){
        Log.i("DYP", "SHOW_PROFILE_LIST n=" + (list != null ? list.size() : 0) + " wrap=" + (listWrap != null));
        if(listWrap == null) return;'''

new = '''    void showProfileList(final java.util.List<ProfileParser.ProfileItem> list){
        Log.i("DYP", "SHOW_PROFILE_LIST n=" + (list != null ? list.size() : 0) + " wrap=" + (listWrap != null));
        if(listWrap == null) return;
        // 关键：把渲染的数据同步进 batchCollected。
        // 以前这里只渲染界面、不写 batchCollected，导致列表页「批量下载」
        // 读到空列表 → 提示「列表是空的」一闪而过，看着像按钮没反应。
        // 列表页「批量解析 / 主页解析 / 批量下载」三个入口现在共用这一份数据。
        try{
            synchronized(batchCollected){
                batchCollected.clear();
                if(list != null) batchCollected.addAll(list);
            }
            Log.i("DYP", "BATCH_COLLECT_SYNC n=" + batchCollected.size());
        }catch(Throwable t){
            Log.i("DYP", "BATCH_COLLECT_SYNC_ERR " + t);
        }'''

if old in s:
    s = s.replace(old, new, 1)
    print('已修复：showProfileList 同步 batchCollected')
else:
    print('未匹配')

io.open(P, 'w', encoding='utf-8').write(s)
print('BATCH_COLLECT_SYNC 计数:', s.count('BATCH_COLLECT_SYNC'))