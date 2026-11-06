# -*- coding: utf-8 -*-
"""
撤回：子页签不该弹输入框。

【我上一版判断错了】
  我以为「主页解析」子页签需要自己的输入入口，
  就给它接了 showProfileInputDialog()。

  但用户纠正：所有解析都在「解析」页面完成 ——
  主页链接也是粘到解析框里点「解析」，
  批量链接是设置页的「批量解析」入口。
  列表页的子页签只是「看结果的分类」，不是入口。

  所以把上一版的 auto-dialog 撤掉，恢复成纯切换。

【顺带确认】
  showBatchInputDialog 本来就有设置页入口（第 4185 行），
  子页签再加一个就是重复入口，更该撤。
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


# 1. 批量解析子页签：撤掉弹窗
rep('1-批量解析纯切换',
    '''        if(subTabBatchParse != null){
            subTabBatchParse.setOnClickListener(new View.OnClickListener(){
                public void onClick(View v){
                    boolean empty;
                    synchronized(itemsFromBatch){ empty = itemsFromBatch.isEmpty(); }
                    switchListSub(0);
                    if(empty) showBatchInputDialog();
                }});
        }''',
    '''        // 子页签只负责切换显示。
        // 所有解析都在「解析」页发起（粘贴链接 → 点解析），
        // 批量解析走设置页的「批量解析」入口，主页链接也是粘到解析框。
        // 这里不要弹输入框 —— 用户只是来看分类结果，弹窗会打断。
        if(subTabBatchParse != null){
            subTabBatchParse.setOnClickListener(new View.OnClickListener(){
                public void onClick(View v){ switchListSub(0); }});
        }''')

# 2. 主页解析子页签：撤掉弹窗
rep('2-主页解析纯切换',
    '''        if(subTabProfileParse != null){
            subTabProfileParse.setOnClickListener(new View.OnClickListener(){
                public void onClick(View v){
                    boolean empty;
                    synchronized(itemsFromProfile){ empty = itemsFromProfile.isEmpty(); }
                    switchListSub(2);
                    // 池子是空的 → 用户显然想在这儿解析，直接弹输入框；
                    // 已有数据 → 只是看列表，不打扰。
                    // （showProfileInputDialog 之前是个死方法，没有任何调用点，
                    //   导致主页解析页签点了只有一片空白，用户不知道从哪下手。）
                    if(empty) showProfileInputDialog();
                }});
        }''',
    '''        if(subTabProfileParse != null){
            subTabProfileParse.setOnClickListener(new View.OnClickListener(){
                public void onClick(View v){ switchListSub(2); }});
        }''')

io.open(P, 'w', encoding='utf-8').write(s)
print('成功:', ok)
print('失败:', miss)