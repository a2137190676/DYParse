# -*- coding: utf-8 -*-
"""
补「合集」子页签的点击绑定 + 自动切换。

上一版只加了池子和视图数组，还没接：
  · subTabMixParse 的 setOnClickListener
  · mixMode 解析完成后自动切到 idx=3
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


# 1) 点击绑定
rep('1-点击绑定',
    '''        if(subTabProfileParse != null){
            subTabProfileParse.setOnClickListener(new View.OnClickListener(){
                public void onClick(View v){ switchListSub(2); }});
        }
        // 默认落在「视频/图文解析」''',
    '''        if(subTabProfileParse != null){
            subTabProfileParse.setOnClickListener(new View.OnClickListener(){
                public void onClick(View v){ switchListSub(2); }});
        }
        if(subTabMixParse != null){
            subTabMixParse.setOnClickListener(new View.OnClickListener(){
                public void onClick(View v){ switchListSub(3); }});
        }
        // 默认落在「视频/图文解析」''')

# 2) 自动切到对应子页签：加 mixMode → 3
rep('2-自动切换合集',
    '''                int want = profileMode ? 2 : (batchCollectMode ? 0 : 1);''',
    '''                // 合集解析完自动落到「合集」页签
                int want = mixMode ? 3 : (profileMode ? 2 : (batchCollectMode ? 0 : 1));''')

# 3) 表头文案：加合集
rep('3-表头文案合集',
    '''        String _src = listRendering ? listSubName(listSubTab)
                : (profileMode ? "主页解析"
                   : (batchCollectMode ? "批量解析" : "视频/图文解析"));''',
    '''        String _src = listRendering ? listSubName(listSubTab)
                : (mixMode ? "合集"
                   : (profileMode ? "主页解析"
                      : (batchCollectMode ? "批量解析" : "视频/图文解析")));''')

io.open(P, 'w', encoding='utf-8').write(s)
print('成功:', ok)
print('失败:', miss)