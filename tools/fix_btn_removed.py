# -*- coding: utf-8 -*-
"""修：btnBatchParse / btnProfileParse 已从布局删除，
   Java 里对它们的 findViewById 也要删掉，并把入口挂到子页签上：
      点「批量解析」子页签 → 若该池为空，弹批量输入框
      点「主页解析」子页签 → 若该池为空，弹主页输入框
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


rep('1-删旧findViewById',
    '''        // ===== 列表页功能入口：批量解析 / 主页解析 / 批量下载 =====
        btnBatchParse = (TextView)findViewById(R.id.btnBatchParse);
        if(btnBatchParse != null){
            btnBatchParse.setOnClickListener(new View.OnClickListener(){
                public void onClick(View v){ showBatchInputDialog(); }});
        }
        btnProfileParse = (TextView)findViewById(R.id.btnProfileParse);
        if(btnProfileParse != null){
            btnProfileParse.setOnClickListener(new View.OnClickListener(){
                public void onClick(View v){ showProfileInputDialog(); }});
        }
        btnBatchDl = (TextView)findViewById(R.id.btnBatchDl);''',
    '''        // ===== 列表页功能入口 =====
        // 布局里已删掉「批量解析 / 主页解析」两个大按钮（改成子页签了），
        // 这里不再 findViewById 它们，否则找不到 R.id 编译不过。
        btnBatchDl = (TextView)findViewById(R.id.btnBatchDl);''')

rep('2-子页签带动入口',
    '''        if(subTabBatchParse != null){
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
        }''',
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
        }''')

io.open(P, 'w', encoding='utf-8').write(s)
print('成功:', ok)
print('失败:', miss)