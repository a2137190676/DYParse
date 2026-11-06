# -*- coding: utf-8 -*-
"""去掉「点子页签自动弹输入框」——只在真的没结果时弹，太吵。
   改成：切子页签只切显示；需要解析时用解析页或列表页底部入口。"""
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


rep('1-不自动弹框',
    '''        // 子页签点击：
        //   已有结果 → 只切显示
        //   还没有   → 顺手弹出对应的输入框（省得用户再找入口）
        if(subTabBatchParse != null){
            subTabBatchParse.setOnClickListener(new View.OnClickListener(){
                public void onClick(View v){
                    switchListSub(0);
                    if(itemsFromBatch.size() == 0) showBatchInputDialog();
                }});
        }
        if(subTabBatchDl != null){
            subTabBatchDl.setOnClickListener(new View.OnClickListener(){
                public void onClick(View v){ switchListSub(1); }});
        }
        if(subTabProfileParse != null){
            subTabProfileParse.setOnClickListener(new View.OnClickListener(){
                public void onClick(View v){
                    switchListSub(2);
                    if(itemsFromProfile.size() == 0) showProfileInputDialog();
                }});
        }''',
    '''        // 子页签：只负责切显示，不自动弹输入框。
        // （之前空池子会自动弹解析框，用户只是看一下列表就被弹窗打断）
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
        }''')

io.open(P, 'w', encoding='utf-8').write(s)
print('成功:', ok)
print('失败:', miss)