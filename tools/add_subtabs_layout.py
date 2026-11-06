# -*- coding: utf-8 -*-
"""
列表页内部再分三个子页签（子父页面）：

    列表页
      ├─ [批量解析]  ← 解析页「批量解析」出来的结果存这里
      ├─ [主页解析]  ← 「主页解析」出来的结果存这里
      └─ [批量下载]  ← 「全部下载」+「批量下载」两个按钮放这里

用户原话：
    「把列表页的批量下载放到全部下载下面，或者和全部下载放在一排，
      然后批量解析的作用是用户在解析页面批量解析后放到那里，
      主页解析同理，用户解析主页后解析结果放到主页解析里面，
      就相当于子父页面」

所以：
  1. 布局先加一条子页签行（三个 TextView），放在 listHead 下方。
  2. 下面按子页签切换三块内容区：
       subBatchParse → 批量解析结果
       subProfileParse → 主页解析结果
       subBatchDl    → 全部下载 / 批量下载 / 清空列表 + 列表本体
  3. Java 侧：
       - listSubTab 记录当前子页签（0/1/2）
       - switchListSub(idx) 切显示
       - showProfileList(list, source) 按来源自动切到对应子页并渲染
       - 解析页「批量解析」→ source=BATCH
       - 主页解析 → source=PROFILE
"""
import io

P = '/storage/emulated/0/MT2/mcp/DYParse/res/layout/activity_main.xml'
s = io.open(P, encoding='utf-8').read()

# ------------------------------------------------------------
# 插入子页签行 + 各子页内容区
# ------------------------------------------------------------
anchor = '''        <TextView android:id="@+id/listHead"
            android:layout_width="match_parent" android:layout_height="wrap_content"
            android:text="粘贴主页链接后点解析，列表会出现在这里"
            android:textColor="@color/text_hint" android:textSize="12sp"
            android:paddingLeft="18dp" android:paddingRight="18dp"
            android:paddingTop="6dp" android:paddingBottom="6dp"/>'''

new_head = '''        <TextView android:id="@+id/listHead"
            android:layout_width="match_parent" android:layout_height="wrap_content"
            android:text="粘贴主页链接后点解析，列表会出现在这里"
            android:textColor="@color/text_hint" android:textSize="12sp"
            android:paddingLeft="18dp" android:paddingRight="18dp"
            android:paddingTop="6dp" android:paddingBottom="6dp"/>

        <!-- 列表页子页签：批量解析 / 主页解析 / 批量下载 -->
        <LinearLayout
            android:layout_width="match_parent" android:layout_height="wrap_content"
            android:orientation="horizontal"
            android:layout_marginLeft="18dp" android:layout_marginRight="18dp"
            android:layout_marginBottom="8dp">
            <TextView android:id="@+id/subTabBatchParse"
                android:layout_width="0dp" android:layout_height="34dp"
                android:layout_weight="1" android:layout_marginRight="5dp"
                android:background="@drawable/input_bg" android:gravity="center"
                android:text="批量解析" android:textColor="@color/text_sub"
                android:textSize="12sp"/>
            <TextView android:id="@+id/subTabProfileParse"
                android:layout_width="0dp" android:layout_height="34dp"
                android:layout_weight="1" android:layout_marginRight="5dp"
                android:background="@drawable/input_bg" android:gravity="center"
                android:text="主页解析" android:textColor="@color/text_sub"
                android:textSize="12sp"/>
            <TextView android:id="@+id/subTabBatchDl"
                android:layout_width="0dp" android:layout_height="34dp"
                android:layout_weight="1"
                android:background="@drawable/input_bg" android:gravity="center"
                android:text="批量下载" android:textColor="@color/text_sub"
                android:textSize="12sp"/>
        </LinearLayout>'''

if anchor in s:
    s = s.replace(anchor, new_head, 1)
    io.open(P, 'w', encoding='utf-8').write(s)
    print('OK 子页签行已插入')
else:
    print('FAIL 找不到 listHead 锚点')