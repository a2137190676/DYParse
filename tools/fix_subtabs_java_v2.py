# -*- coding: utf-8 -*-
"""
Java 侧同步：子页签新语义

    idx 0 → 批量解析      (subTabBatchParse)
    idx 1 → 视频/图文解析  (subTabBatchDl，复用旧 id，默认选中)
    idx 2 → 主页解析      (subTabProfileParse)

数据池三份：
    itemsFromBatch    批量解析结果
    itemsFromSingle   视频/图文（单条）解析结果  ← 新增
    itemsFromProfile  主页解析结果
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
# 1) 点击绑定：调整映射 + 默认 idx=1
# ============================================================
rep('1-点击映射',
    '''        if(subTabBatchParse != null){
            subTabBatchParse.setOnClickListener(new View.OnClickListener(){
                public void onClick(View v){ switchListSub(0); }});
        }
        if(subTabProfileParse != null){
            subTabProfileParse.setOnClickListener(new View.OnClickListener(){
                public void onClick(View v){ switchListSub(1); }});
        }
        if(subTabBatchDl != null){
            subTabBatchDl.setOnClickListener(new View.OnClickListener(){
                public void onClick(View v){ switchListSub(2); }});
        }''',
    '''        // 子页签语义（用户定的顺序）：
        //   idx 0 = 批量解析   idx 1 = 视频/图文解析(默认)   idx 2 = 主页解析
        if(subTabBatchParse != null){
            subTabBatchParse.setOnClickListener(new View.OnClickListener(){
                public void onClick(View v){ switchListSub(0); }});
        }
        if(subTabBatchDl != null){
            subTabBatchDl.setOnClickListener(new View.OnClickListener(){
                public void onClick(View v){ switchListSub(1); }});
        }
        if(subTabProfileParse != null){
            subTabProfileParse.setOnClickListener(new View.OnClickListener(){
                public void onClick(View v){ switchListSub(2); }});
        }
        // 默认落在「视频/图文解析」
        listSubTab = 1;
        h.post(new Runnable(){ public void run(){ try{ switchListSub(1); }catch(Throwable t){} }});''')

# ============================================================
# 2) switchListSub 重写：三份数据池
# ============================================================
rep('2-switchListSub三池',
    '''            // 取该子页对应的数据重新渲染
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
            Log.i("DYP", "LIST_SUB idx=" + idx + " n=" + src.size());''',
    '''            // 取该子页对应的数据重新渲染
            java.util.List<ProfileParser.ProfileItem> src;
            if(idx == 0){
                synchronized(itemsFromBatch){ src = new java.util.ArrayList<ProfileParser.ProfileItem>(itemsFromBatch); }
            }else if(idx == 1){
                synchronized(itemsFromSingle){ src = new java.util.ArrayList<ProfileParser.ProfileItem>(itemsFromSingle); }
            }else{
                synchronized(itemsFromProfile){ src = new java.util.ArrayList<ProfileParser.ProfileItem>(itemsFromProfile); }
            }
            listRendering = true;
            try{
                showProfileList(src);
            }finally{
                listRendering = false;
            }
            Log.i("DYP", "LIST_SUB idx=" + idx + " n=" + src.size());''')

# ============================================================
# 3) 新增 itemsFromSingle 池
# ============================================================
rep('3-加single池',
    '''    final java.util.List<ProfileParser.ProfileItem> itemsFromProfile =
            java.util.Collections.synchronizedList(new java.util.ArrayList<ProfileParser.ProfileItem>());''',
    '''    final java.util.List<ProfileParser.ProfileItem> itemsFromProfile =
            java.util.Collections.synchronizedList(new java.util.ArrayList<ProfileParser.ProfileItem>());
    /** 视频/图文（单条）解析的结果 */
    final java.util.List<ProfileParser.ProfileItem> itemsFromSingle =
            java.util.Collections.synchronizedList(new java.util.ArrayList<ProfileParser.ProfileItem>());''')

# ============================================================
# 4) 存池：批量 / 主页 / 单条 三种来源
# ============================================================
rep('4-三源存池',
    '''        try{
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
        }''',
    '''        try{
            if(!listRendering && list != null){
                java.util.List<ProfileParser.ProfileItem> pool;
                String srcName;
                if(profileMode){
                    pool = itemsFromProfile;  srcName = "主页解析";
                }else if(batchCollectMode){
                    pool = itemsFromBatch;    srcName = "批量解析";
                }else{
                    pool = itemsFromSingle;   srcName = "视频/图文解析";
                }
                synchronized(pool){
                    pool.clear();
                    pool.addAll(list);
                }
                Log.i("DYP", "LIST_POOL_SAVE src=" + srcName + " n=" + pool.size());
            }
        }catch(Throwable t){
            Log.i("DYP", "LIST_POOL_SAVE_ERR " + t);
        }''')

# ============================================================
# 5) 表头文案 + 自动切子页：三种来源
# ============================================================
rep('5-表头三源',
    '''        String _src = batchCollectMode ? "批量解析" : "主页解析";''',
    '''        String _src = profileMode ? "主页解析" : (batchCollectMode ? "批量解析" : "视频/图文解析");''')

rep('6-自动切三源',
    '''                int want = batchCollectMode ? 0 : 1;''',
    '''                int want = profileMode ? 2 : (batchCollectMode ? 0 : 1);''')

io.open(P, 'w', encoding='utf-8').write(s)
print('成功:', ok)
print('失败:', miss)