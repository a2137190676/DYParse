# -*- coding: utf-8 -*-
"""
修：「主页解析」「批量解析」子页签点了没反应，用户不知道从哪发起解析。

【现象】
  点「主页解析」→ 切到主页解析子页签 → 「主页解析 · 共 0 条」，
  然后就没了。没有任何「粘贴链接」的入口。

【根因】
  showProfileInputDialog()（第 17804 行）早就写好了，
  但全文件搜不到任何调用点 —— 是个死方法。
  之前「子页签自动弹输入框」被用户嫌吵去掉了，
  结果就是：子页签只能看，不能发起解析。

【改法】
  子页签点击时判断：
    · 池子为空 → 弹输入框（用户明显是想在这里解析）
    · 池子非空 → 只切换显示，不打扰

  这个策略两边都照顾到了：
    · 第一次进来（空池）能直接粘贴链接，不用先去别处找入口
    · 已经有数据时点页签只是看列表，不会被打断

  同样给「批量解析」子页签接上 showBatchInputDialog()。
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
# 1. 主页解析子页签：空池时弹输入框
# ============================================================
rep('1-主页解析入口',
    '''        if(subTabProfileParse != null){
            subTabProfileParse.setOnClickListener(new View.OnClickListener(){
                public void onClick(View v){ switchListSub(2); }});
        }''',
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
        }''')

# ============================================================
# 2. 批量解析子页签：空池时弹输入框
# ============================================================
rep('2-批量解析入口',
    '''        if(subTabBatchParse != null){
            subTabBatchParse.setOnClickListener(new View.OnClickListener(){
                public void onClick(View v){ switchListSub(0); }});
        }''',
    '''        if(subTabBatchParse != null){
            subTabBatchParse.setOnClickListener(new View.OnClickListener(){
                public void onClick(View v){
                    boolean empty;
                    synchronized(itemsFromBatch){ empty = itemsFromBatch.isEmpty(); }
                    switchListSub(0);
                    if(empty) showBatchInputDialog();
                }});
        }''')

io.open(P, 'w', encoding='utf-8').write(s)
print('成功:', ok)
print('失败:', miss)