# -*- coding: utf-8 -*-
"""补 dlRowView 映射（删除按钮要靠它找到行 View 并移除）"""
import io

MA = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/MainActivity.java'
m = io.open(MA, encoding='utf-8').read()
n = 0

# 1) 声明
old = '''    final java.util.HashMap<Integer, ProgressBar> dlRowBar = new java.util.HashMap<Integer, ProgressBar>();'''
new = '''    final java.util.HashMap<Integer, ProgressBar> dlRowBar = new java.util.HashMap<Integer, ProgressBar>();
    /** 行 key -> 整行 View（删除某行时用） */
    final java.util.HashMap<Integer, android.view.View> dlRowView =
            new java.util.HashMap<Integer, android.view.View>();'''
if old in m:
    m = m.replace(old, new, 1); n += 1; print('1) dlRowView 声明 OK')
else:
    print('MISS 1')

# 2) dlRowAdd 里登记 row
old = '''                dlRowOrder.add(key);
                dlTaskList.addView(row);'''
new = '''                dlRowOrder.add(key);
                dlRowView.put(key, row);
                dlTaskList.addView(row);'''
if old in m:
    m = m.replace(old, new, 1); n += 1; print('2) dlRowAdd 登记 OK')
else:
    print('MISS 2')

# 3) dlRowClear 里清理
old = '''                 dlRowName.clear();
                 dlRowMeta.clear();
                 dlRowBar.clear();
                 dlRowOrder.clear();'''
new = '''                 dlRowName.clear();
                 dlRowMeta.clear();
                 dlRowBar.clear();
                 dlRowView.clear();
                 dlRowOrder.clear();'''
if old in m:
    m = m.replace(old, new, 1); n += 1; print('3) dlRowClear 清理 OK')
else:
    print('MISS 3')

io.open(MA, 'w', encoding='utf-8').write(m)
print('changed:', n)