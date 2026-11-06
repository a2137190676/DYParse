# -*- coding: utf-8 -*-
"""
在列表页加一排功能入口：
   [批量解析]  [主页解析]  [批量下载]  [清空列表]

用户要求：
  · 批量解析、主页解析的入口放到列表页
  · 批量下载单独做个按钮，不要塞在「全部下载」里
  · 全部下载（btnDlAll）保留原样
"""
import io

L = '/storage/emulated/0/MT2/mcp/DYParse/res/layout/activity_main.xml'
s = io.open(L, encoding='utf-8').read()

# 在 btnClearList 后面插入一排功能按钮（批量解析 / 主页解析 / 批量下载）
old = '''        <TextView android:id="@+id/btnClearList"
            android:layout_width="match_parent" android:layout_height="38dp"
            android:layout_marginLeft="18dp" android:layout_marginRight="18dp"
            android:layout_marginBottom="8dp"
            android:background="@drawable/input_bg" android:gravity="center"
            android:text="清空列表" android:textColor="@color/text_sub"
            android:textSize="13sp" android:visibility="gone"/>
'''

new = '''        <TextView android:id="@+id/btnClearList"
            android:layout_width="match_parent" android:layout_height="38dp"
            android:layout_marginLeft="18dp" android:layout_marginRight="18dp"
            android:layout_marginBottom="8dp"
            android:background="@drawable/input_bg" android:gravity="center"
            android:text="清空列表" android:textColor="@color/text_sub"
            android:textSize="13sp" android:visibility="gone"/>

        <!-- 功能入口：批量解析 / 主页解析 / 批量下载 -->
        <LinearLayout
            android:layout_width="match_parent" android:layout_height="wrap_content"
            android:orientation="horizontal"
            android:layout_marginLeft="18dp" android:layout_marginRight="18dp"
            android:layout_marginBottom="8dp">
            <TextView android:id="@+id/btnBatchParse"
                android:layout_width="0dp" android:layout_height="38dp"
                android:layout_weight="1" android:layout_marginRight="6dp"
                android:background="@drawable/input_bg" android:gravity="center"
                android:text="批量解析" android:textColor="@color/text_sub"
                android:textSize="13sp"/>
            <TextView android:id="@+id/btnProfileParse"
                android:layout_width="0dp" android:layout_height="38dp"
                android:layout_weight="1" android:layout_marginRight="6dp"
                android:background="@drawable/input_bg" android:gravity="center"
                android:text="主页解析" android:textColor="@color/text_sub"
                android:textSize="13sp"/>
            <TextView android:id="@+id/btnBatchDl"
                android:layout_width="0dp" android:layout_height="38dp"
                android:layout_weight="1"
                android:background="@drawable/btn_primary" android:gravity="center"
                android:text="批量下载" android:textColor="#FFFFFF"
                android:textSize="13sp" android:textStyle="bold"/>
        </LinearLayout>
'''

if old not in s:
    raise SystemExit('MISS btnClearList 块')
s = s.replace(old, new, 1)
io.open(L, 'w', encoding='utf-8').write(s)
print('layout 已插入 3 个按钮')
