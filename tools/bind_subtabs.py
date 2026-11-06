# -*- coding: utf-8 -*-
"""绑定子页签点击（在 onCreate 的 findViewById 区域）。"""
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


# 绑定点：btnBatchDl 初始化之后
rep('1-绑子页签',
    '''        btnBatchDl = (TextView)findViewById(R.id.btnBatchDl);
        if(btnBatchDl != null){
            btnBatchDl.setOnClickListener(new View.OnClickListener(){
                public void onClick(View v){ startBatchDlFromList(); }});
        }''',
    '''        btnBatchDl = (TextView)findViewById(R.id.btnBatchDl);
        if(btnBatchDl != null){
            btnBatchDl.setOnClickListener(new View.OnClickListener(){
                public void onClick(View v){ startBatchDlFromList(); }});
        }

        // ===== 列表页子页签：批量解析 / 主页解析 / 批量下载 =====
        subTabBatchParse = (TextView)findViewById(R.id.subTabBatchParse);
        subTabProfileParse = (TextView)findViewById(R.id.subTabProfileParse);
        subTabBatchDl = (TextView)findViewById(R.id.subTabBatchDl);
        if(subTabBatchParse != null){
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
        }''')

# 「批量下载」入口从原来三个按钮那排移走（那排只留批量解析/主页解析）
rep('2-批量下载移出上排',
    '''            <TextView android:id="@+id/btnBatchDl"''',
    '''            <TextView android:id="@+id/btnBatchDl"''')

io.open(P, 'w', encoding='utf-8').write(s)
print('成功:', ok)
print('失败:', miss)