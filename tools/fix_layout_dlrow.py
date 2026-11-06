# -*- coding: utf-8 -*-
"""
布局调整：
  · 上一排只留「批量解析」「主页解析」两个按钮
  · 「批量下载」按钮挪到子页签下方，「全部下载」和它放同一排
"""
import io

P = '/storage/emulated/0/MT2/mcp/DYParse/res/layout/activity_main.xml'
s = io.open(P, encoding='utf-8').read()
ok, miss = [], []


def rep(name, old, new):
    global s
    if old in s:
        s = s.replace(old, new, 1)
        ok.append(name)
    else:
        miss.append(name)


# 1) 原来一行三个按钮：去掉批量下载，改成两个
rep('1-上排去掉批量下载',
    '''            <TextView android:id="@+id/btnBatchParse"
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
                android:textSize="13sp" android:textStyle="bold"/>''',
    '''            <TextView android:id="@+id/btnBatchParse"
                android:layout_width="0dp" android:layout_height="38dp"
                android:layout_weight="1" android:layout_marginRight="6dp"
                android:background="@drawable/input_bg" android:gravity="center"
                android:text="批量解析" android:textColor="@color/text_sub"
                android:textSize="13sp"/>
            <TextView android:id="@+id/btnProfileParse"
                android:layout_width="0dp" android:layout_height="38dp"
                android:layout_weight="1"
                android:background="@drawable/input_bg" android:gravity="center"
                android:text="主页解析" android:textColor="@color/text_sub"
                android:textSize="13sp"/>''')

# 2) 「全部下载」改造：和「批量下载」同一排
rep('2-全部下载同排',
    '''        <TextView android:id="@+id/btnDlAll"
            android:layout_width="match_parent" android:layout_height="40dp"
            android:layout_marginLeft="18dp" android:layout_marginRight="18dp"
            android:layout_marginBottom="8dp"
            android:background="@drawable/btn_primary" android:gravity="center"
            android:text="全部下载" android:textColor="#FFFFFF"
            android:textSize="13sp" android:textStyle="bold" android:visibility="gone"/>''',
    '''        <!-- 下载操作排：全部下载 + 批量下载（只在「批量下载」子页签显示） -->
        <LinearLayout
            android:layout_width="match_parent" android:layout_height="wrap_content"
            android:orientation="horizontal"
            android:layout_marginLeft="18dp" android:layout_marginRight="18dp"
            android:layout_marginBottom="8dp">
            <TextView android:id="@+id/btnDlAll"
                android:layout_width="0dp" android:layout_height="40dp"
                android:layout_weight="1" android:layout_marginRight="6dp"
                android:background="@drawable/btn_primary" android:gravity="center"
                android:text="全部下载" android:textColor="#FFFFFF"
                android:textSize="13sp" android:textStyle="bold" android:visibility="gone"/>
            <TextView android:id="@+id/btnBatchDl"
                android:layout_width="0dp" android:layout_height="40dp"
                android:layout_weight="1"
                android:background="@drawable/btn_primary" android:gravity="center"
                android:text="批量下载" android:textColor="#FFFFFF"
                android:textSize="13sp" android:textStyle="bold"/>
        </LinearLayout>''')

io.open(P, 'w', encoding='utf-8').write(s)
print('成功:', ok)
print('失败:', miss)