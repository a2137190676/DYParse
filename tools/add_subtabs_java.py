# -*- coding: utf-8 -*-
"""
列表页子页签的 Java 实现。

设计：
  listSubTab = 0 → 批量解析结果
  listSubTab = 1 → 主页解析结果
  listSubTab = 2 → 批量下载（全部下载 / 批量下载 / 清空列表）

数据分源：
  profileItemsFromProfile  ← 主页解析出来的
  profileItemsFromBatch    ← 批量解析出来的

  showProfileList(list, source) 按 source 存进对应池子，
  并把界面切到对应子页签。
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
    '''    void showProfileList(final java.util.List<ProfileParser.ProfileItem> list){''',
    '''    // ============================================================
    // 列表页子页签（用户要求：子父页面）
    //
    //   列表页
    //     ├─ [批量解析]  ← 解析页「批量解析」的结果
    //     ├─ [主页解析]  ← 「主页解析」的结果
    //     └─ [批量下载]  ← 全部下载 / 批量下载 / 清空列表
    //
    // 以前三种来源混在同一份 profileCollected 里，
    // 用户分不清「这个列表是主页出来的还是批量出来的」。
    // ============================================================
    volatile int listSubTab = 1;   // 默认落在「主页解析」
    TextView subTabBatchParse, subTabProfileParse, subTabBatchDl;

    /** 主页解析的结果 */
    final java.util.List<ProfileParser.ProfileItem> itemsFromProfile =
            java.util.Collections.synchronizedList(new java.util.ArrayList<ProfileParser.ProfileItem>());
    /** 批量解析的结果 */
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
            // 显示哪一份数据
            if(idx == 0){
                renderListTo(itemsFromBatch, "批量解析");
            }else if(idx == 1){
                renderListTo(itemsFromProfile, "主页解析");
            }else{
                // 批量下载页签：显示当前可下载的那份（优先主页，其次批量）
                java.util.List<ProfileParser.ProfileItem> src =
                        itemsFromProfile.size() > 0 ? itemsFromProfile : itemsFromBatch;
                renderListTo(src, listSubTab == 2
                        ? (itemsFromProfile.size() > 0 ? "主页解析" : "批量解析") : "");
            }
            // 下载按钮组只在「批量下载」页签显示
            boolean dlTab = (idx == 2);
            try{
                if(btnDlAll != null) btnDlAll.setVisibility(dlTab && !listEmpty ? View.VISIBLE : View.GONE);
                if(btnBatchDl != null) btnBatchDl.setVisibility(dlTab ? View.VISIBLE : View.GONE);
                if(btnClearList != null) btnClearList.setVisibility(dlTab && !listEmpty ? View.VISIBLE : View.GONE);
            }catch(Throwable t){}
            Log.i("DYP", "LIST_SUB idx=" + idx);
        }catch(Throwable t){ Log.i("DYP", "LIST_SUB_ERR " + t); }
    }

    /** 当前列表是否空 */
    volatile boolean listEmpty = true;

    /** 把一份数据渲染到列表（不加副作用） */
    void renderListTo(final java.util.List<ProfileParser.ProfileItem> src, final String label){
        try{
            final java.util.List<ProfileParser.ProfileItem> cp;
            synchronized(src){ cp = new java.util.ArrayList<ProfileParser.ProfileItem>(src); }
            if(cp.isEmpty()){
                if(listWrap != null) listWrap.removeAllViews();
                listEmpty = true;
                if(listHead != null){
                    listHead.setText(label.length() > 0
                            ? (label + "：还没有结果，去上方点对应按钮")
                            : "还没有结果");
                }
                return;
            }
            listEmpty = false;
            if(listHead != null){
                listHead.setText(label + " · 共 " + cp.size() + " 条");
            }
            buildProfileRows(cp);
        }catch(Throwable t){ Log.i("DYP", "LIST_RENDER_ERR " + t); }
    }

    void showProfileList(final java.util.List<ProfileParser.ProfileItem> list){''')

io.open(P, 'w', encoding='utf-8').write(s)
print('成功:', ok)
print('失败:', miss)