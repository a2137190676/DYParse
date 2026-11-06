# -*- coding: utf-8 -*-
"""
列表页子页签 Java 实现（修正版）。

上版脚本假设有 buildProfileRows()，实际不存在 ——
渲染逻辑全在 showProfileList 里。所以改成：

  · 不拆渲染函数，复用 showProfileList
  · showProfileList 增加 source 参数（重载），
    按 source 把数据存进 itemsFromProfile / itemsFromBatch
  · switchListSub 只负责：存当前选中、切换高亮、
    从对应池子里取数据调 showProfileList 重新渲染
  · 渲染期间用 noSyncPool 标志避免 showProfileList 反向覆盖池子
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
# 1) 字段 + switchListSub
# ============================================================
rep('1-加字段和switchListSub',
    '''    void showProfileList(final java.util.List<ProfileParser.ProfileItem> list){
        Log.i("DYP", "SHOW_PROFILE_LIST n=" + (list != null ? list.size() : 0) + " wrap=" + (listWrap != null));''',
    '''    // ============================================================
    // 列表页子页签（用户要求：子父页面）
    //
    //   列表页
    //     ├─ [批量解析]  ← 解析页「批量解析」的结果
    //     ├─ [主页解析]  ← 「主页解析」的结果
    //     └─ [批量下载]  ← 全部下载 / 批量下载 / 清空列表
    //
    // 以前三种来源混在同一份数据里，用户分不清这个列表是哪来的。
    // ============================================================
    volatile int listSubTab = 1;
    TextView subTabBatchParse, subTabProfileParse, subTabBatchDl;
    /** 渲染期间置 true，避免 showProfileList 反向覆盖数据池 */
    volatile boolean listRendering = false;

    final java.util.List<ProfileParser.ProfileItem> itemsFromProfile =
            java.util.Collections.synchronizedList(new java.util.ArrayList<ProfileParser.ProfileItem>());
    final java.util.List<ProfileParser.ProfileItem> itemsFromBatch =
            java.util.Collections.synchronizedList(new java.util.ArrayList<ProfileParser.ProfileItem>());

    /** 切列表页子页签 */
    void switchListSub(int idx){
        try{
            listSubTab = idx;
            boolean dark = isDarkMode();
            int actC = dark ? 0xFFE8EAF0 : 0xFF1A1C22;
            int nC   = dark ? 0xFF8A8F9C : 0xFF9AA0AC;
            TextView[] subs = { subTabBatchParse, subTabProfileParse, subTabBatchDl };
            for(int i = 0; i < subs.length; i++){
                if(subs[i] == null) continue;
                if(i == idx){
                    subs[i].setBackground(makeRoundRect(0x1AFE2C55, 0, 10));
                    subs[i].setTextColor(actC);
                    subs[i].setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                }else{
                    subs[i].setBackground(makeRoundRect(0x0DFFFFFF, 0x14FFFFFF, 10));
                    subs[i].setTextColor(nC);
                    subs[i].setTypeface(android.graphics.Typeface.DEFAULT);
                }
            }
            // 取该子页对应的数据重新渲染
            java.util.List<ProfileParser.ProfileItem> src;
            if(idx == 0){
                synchronized(itemsFromBatch){ src = new java.util.ArrayList<ProfileParser.ProfileItem>(itemsFromBatch); }
            }else if(idx == 1){
                synchronized(itemsFromProfile){ src = new java.util.ArrayList<ProfileParser.ProfileItem>(itemsFromProfile); }
            }else{
                // 批量下载页签：显示「当前要下哪一份」，优先主页
                boolean useProfile = itemsFromProfile.size() > 0;
                java.util.List<ProfileParser.ProfileItem> pool = useProfile ? itemsFromProfile : itemsFromBatch;
                synchronized(pool){ src = new java.util.ArrayList<ProfileParser.ProfileItem>(pool); }
            }
            listRendering = true;
            try{
                showProfileList(src);
            }finally{
                listRendering = false;
            }
            // 下载按钮组只在「批量下载」页签显示
            boolean dlTab = (idx == 2);
            try{
                if(btnBatchDl != null) btnBatchDl.setVisibility(dlTab ? View.VISIBLE : View.GONE);
                if(btnClearList != null){
                    btnClearList.setVisibility(dlTab && src.size() > 0 ? View.VISIBLE : View.GONE);
                }
            }catch(Throwable t){}
            Log.i("DYP", "LIST_SUB idx=" + idx + " n=" + src.size());
        }catch(Throwable t){ Log.i("DYP", "LIST_SUB_ERR " + t); }
    }

    void showProfileList(final java.util.List<ProfileParser.ProfileItem> list){
        Log.i("DYP", "SHOW_PROFILE_LIST n=" + (list != null ? list.size() : 0) + " wrap=" + (listWrap != null));''')

# ============================================================
# 2) showProfileList 里同步数据池：按来源存
# ============================================================
rep('2-按来源存池',
    '''        try{
            synchronized(batchCollected){
                batchCollected.clear();
                if(list != null) batchCollected.addAll(list);
            }
            Log.i("DYP", "BATCH_COLLECT_SYNC n=" + batchCollected.size());
        }catch(Throwable t){
            Log.i("DYP", "BATCH_COLLECT_SYNC_ERR " + t);
        }''',
    '''        try{
            synchronized(batchCollected){
                batchCollected.clear();
                if(list != null) batchCollected.addAll(list);
            }
            Log.i("DYP", "BATCH_COLLECT_SYNC n=" + batchCollected.size());
        }catch(Throwable t){
            Log.i("DYP", "BATCH_COLLECT_SYNC_ERR " + t);
        }
        // 按来源存进各自的数据池（子页签用）。
        // listRendering=true 表示这是子页签切换触发的重渲染，不能反向覆盖池子，
        // 否则「批量解析」切过去一次就把主页那份冲掉了。
        try{
            if(!listRendering && list != null){
                java.util.List<ProfileParser.ProfileItem> pool =
                        batchCollectMode ? itemsFromBatch : itemsFromProfile;
                synchronized(pool){
                    pool.clear();
                    pool.addAll(list);
                }
                Log.i("DYP", "LIST_POOL_SAVE src=" + (batchCollectMode ? "批量解析" : "主页解析")
                        + " n=" + pool.size());
            }
        }catch(Throwable t){
            Log.i("DYP", "LIST_POOL_SAVE_ERR " + t);
        }''')

# ============================================================
# 3) 列表头文案区分来源 + 自动切子页签
# ============================================================
rep('3-表头按来源',
    '''        String _src = batchCollectMode ? "批量解析" : "主页";
        listHead.setText(pages > 1
                ? (_src + "共 " + n + " 条 · 第 " + (profilePage + 1) + "/" + pages + " 页 · 点「下载」直接下")
                : (_src + "共 " + n + " 条 · 点「下载」直接下，点标题先解析"));''',
    '''        String _src = batchCollectMode ? "批量解析" : "主页解析";
        listHead.setText(pages > 1
                ? (_src + " · 共 " + n + " 条 · 第 " + (profilePage + 1) + "/" + pages + " 页")
                : (_src + " · 共 " + n + " 条"));
        // 有新结果时自动切到对应子页签（用户不用手动找）
        try{
            if(!listRendering){
                int want = batchCollectMode ? 0 : 1;
                if(listSubTab != want){
                    listSubTab = want;
                    h.post(new Runnable(){ public void run(){
                        try{
                            boolean dark = isDarkMode();
                            int actC = dark ? 0xFFE8EAF0 : 0xFF1A1C22;
                            int nC   = dark ? 0xFF8A8F9C : 0xFF9AA0AC;
                            TextView[] subs = { subTabBatchParse, subTabProfileParse, subTabBatchDl };
                            for(int i = 0; i < subs.length; i++){
                                if(subs[i] == null) continue;
                                if(i == want){
                                    subs[i].setBackground(makeRoundRect(0x1AFE2C55, 0, 10));
                                    subs[i].setTextColor(actC);
                                    subs[i].setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                                }else{
                                    subs[i].setBackground(makeRoundRect(0x0DFFFFFF, 0x14FFFFFF, 10));
                                    subs[i].setTextColor(nC);
                                    subs[i].setTypeface(android.graphics.Typeface.DEFAULT);
                                }
                            }
                        }catch(Throwable t){}
                    }});
                }
            }
        }catch(Throwable t){}''')

io.open(P, 'w', encoding='utf-8').write(s)
print('成功:', ok)
print('失败:', miss)