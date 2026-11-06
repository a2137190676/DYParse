# -*- coding: utf-8 -*-
"""
按用户最新要求改列表页：

  子页签（三个）：
      [批量解析]   [视频/图文解析]   [主页解析]
                      ↑ 默认选中，位置在中间

  · 「批量下载」不再是子页签（不做单独子页）
  · 下面那两个大按钮「批量解析 / 主页解析」整行删掉
    （已经有子页签了，重复了）
  · 「视频/图文解析」= 单条解析（用户粘贴作品链接解析）的结果

用户原话：
    「批量下载不用特意做子页面，给那个改成视频/图文解析后的结果子页面
      然后放到中间，这个设置为默认，然后下面的批量解析和主页解析不保留」
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


# ------------------------------------------------------------
# 1) 子页签：三个改成「批量解析 / 视频·图文解析 / 主页解析」
#    id 保留但语义调整：
#      subTabBatchParse    → 批量解析   (idx 0)
#      subTabBatchDl       → 视频/图文解析 (idx 1) 默认
#      subTabProfileParse  → 主页解析   (idx 2)
#    为清晰，重新命名 id 会牵动 Java，这里直接复用现有 id，
#    只改文字和顺序，Java 里映射同步改。
# ------------------------------------------------------------
rep('1-子页签改三态',
    '''            <TextView android:id="@+id/subTabBatchParse"
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
                android:textSize="12sp"/>''',
    '''            <TextView android:id="@+id/subTabBatchParse"
                android:layout_width="0dp" android:layout_height="34dp"
                android:layout_weight="1" android:layout_marginRight="5dp"
                android:background="@drawable/input_bg" android:gravity="center"
                android:text="批量解析" android:textColor="@color/text_sub"
                android:textSize="12sp"/>
            <TextView android:id="@+id/subTabBatchDl"
                android:layout_width="0dp" android:layout_height="34dp"
                android:layout_weight="1" android:layout_marginRight="5dp"
                android:background="@drawable/input_bg" android:gravity="center"
                android:text="视频/图文解析" android:textColor="@color/text_sub"
                android:textSize="12sp"/>
            <TextView android:id="@+id/subTabProfileParse"
                android:layout_width="0dp" android:layout_height="34dp"
                android:layout_weight="1"
                android:background="@drawable/input_bg" android:gravity="center"
                android:text="主页解析" android:textColor="@color/text_sub"
                android:textSize="12sp"/>''')

# ------------------------------------------------------------
# 2) 删掉下面的「批量解析 / 主页解析」两个大按钮那一整行
# ------------------------------------------------------------
rep('2-删双大按钮',
    '''        <!-- 功能入口：批量解析 / 主页解析 / 批量下载 -->
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
                android:layout_weight="1"
                android:background="@drawable/input_bg" android:gravity="center"
                android:text="主页解析" android:textColor="@color/text_sub"
                android:textSize="13sp"/>
        </LinearLayout>
''',
    '''''')

io.open(P, 'w', encoding='utf-8').write(s)
print('成功:', ok)
print('失败:', miss)