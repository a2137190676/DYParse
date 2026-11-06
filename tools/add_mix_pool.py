# -*- coding: utf-8 -*-
"""
加「合集」子页签 + 合集落盘。

【现状】
  抖音合集走 mixItems（String[]{id, desc, play, cover}）+ renderMixList()，
  这条路和「三个来源池」完全独立：
    · 合集结果不写任何 itemsFrom* 池
    · 合集也不落盘，App 一重启合集列表就没了
    · 列表页没有「合集」子页签，用户找不到已解析的合集

【改法】
  ① 布局：子页签从 3 个扩到 4 个
         [批量解析] [视频/图文解析] [主页解析] [合集]
     用 weight=1 平均分，4 个刚好一行。
  ② itemsFromMix 池（第 4 个）
  ③ renderMixList() 里把 itemList 写进 itemsFromMix + saveListPools()
  ④ switchListSub 支持 idx=3
  ⑤ 池编号 3 = 合集，落盘 / 读取都带上
"""
import io

ROOT = '/storage/emulated/0/MT2/mcp/DYParse'
XML = ROOT + '/res/layout/activity_main.xml'
MA = ROOT + '/java/com/re/douyinparse/MainActivity.java'

ok, miss = [], []

# ============================================================
# ① 布局：加第 4 个子页签
# ============================================================
x = io.open(XML, encoding='utf-8').read()

old_xml = '''            <TextView android:id="@+id/subTabProfileParse"
                android:layout_width="0dp" android:layout_height="34dp"
                android:layout_weight="1"
                android:background="@drawable/input_bg" android:gravity="center"
                android:text="主页解析" android:textColor="@color/text_sub"
                android:textSize="12sp"/>'''

new_xml = '''            <TextView android:id="@+id/subTabProfileParse"
                android:layout_width="0dp" android:layout_height="34dp"
                android:layout_weight="1" android:layout_marginRight="5dp"
                android:background="@drawable/input_bg" android:gravity="center"
                android:text="主页解析" android:textColor="@color/text_sub"
                android:textSize="12sp"/>
            <TextView android:id="@+id/subTabMixParse"
                android:layout_width="0dp" android:layout_height="34dp"
                android:layout_weight="1"
                android:background="@drawable/input_bg" android:gravity="center"
                android:text="合集" android:textColor="@color/text_sub"
                android:textSize="12sp"/>'''

if old_xml in x:
    x = x.replace(old_xml, new_xml, 1)
    ok.append('1-布局第4页签')
else:
    miss.append('1-布局第4页签')

io.open(XML, 'w', encoding='utf-8').write(x)

# ============================================================
# ②③④⑤ Java
# ============================================================
s = io.open(MA, encoding='utf-8').read()


def rep(name, old, new):
    global s
    if old in s:
        s = s.replace(old, new, 1)
        ok.append(name)
    else:
        miss.append(name)


# ② 加 itemsFromMix 池
rep('2-加itemsFromMix池',
    '''    final java.util.List<ProfileParser.ProfileItem> itemsFromBatch =
            java.util.Collections.synchronizedList(new java.util.ArrayList<ProfileParser.ProfileItem>());''',
    '''    final java.util.List<ProfileParser.ProfileItem> itemsFromBatch =
            java.util.Collections.synchronizedList(new java.util.ArrayList<ProfileParser.ProfileItem>());
    /** 合集解析的结果（抖音合集、B站合集等） */
    final java.util.List<ProfileParser.ProfileItem> itemsFromMix =
            java.util.Collections.synchronizedList(new java.util.ArrayList<ProfileParser.ProfileItem>());''')

# ④ 子页签视图数组（4 个）
rep('4-子页签数组',
    '''    TextView[] listSubViews(){
        return new TextView[]{ subTabBatchParse, subTabBatchDl, subTabProfileParse };
    }

    /** 子页签名字 */
    static String listSubName(int idx){
        if(idx == 0) return "批量解析";
        if(idx == 2) return "主页解析";
        return "视频/图文解析";
    }''',
    '''    TextView[] listSubViews(){
        return new TextView[]{ subTabBatchParse, subTabBatchDl,
                subTabProfileParse, subTabMixParse };
    }

    /** 子页签名字 */
    static String listSubName(int idx){
        if(idx == 0) return "批量解析";
        if(idx == 1) return "视频/图文解析";
        if(idx == 2) return "主页解析";
        if(idx == 3) return "合集";
        return "视频/图文解析";
    }''')

# ④ switchListSub 支持 idx=3
rep('4b-switchListSub合集',
    '''            if(idx == 0){
                synchronized(itemsFromBatch){ src = new java.util.ArrayList<ProfileParser.ProfileItem>(itemsFromBatch); }
            }else if(idx == 1){
                synchronized(itemsFromSingle){ src = new java.util.ArrayList<ProfileParser.ProfileItem>(itemsFromSingle); }
            }else{
                synchronized(itemsFromProfile){ src = new java.util.ArrayList<ProfileParser.ProfileItem>(itemsFromProfile); }
            }''',
    '''            if(idx == 0){
                synchronized(itemsFromBatch){ src = new java.util.ArrayList<ProfileParser.ProfileItem>(itemsFromBatch); }
            }else if(idx == 1){
                synchronized(itemsFromSingle){ src = new java.util.ArrayList<ProfileParser.ProfileItem>(itemsFromSingle); }
            }else if(idx == 3){
                synchronized(itemsFromMix){ src = new java.util.ArrayList<ProfileParser.ProfileItem>(itemsFromMix); }
            }else{
                synchronized(itemsFromProfile){ src = new java.util.ArrayList<ProfileParser.ProfileItem>(itemsFromProfile); }
            }''')

# ③ renderMixList 里写池子
rep('3-renderMix写池',
    '''                final java.util.List<ProfileParser.ProfileItem> items = itemList;

                listHead.setText("抖音合集 · 共 " + n + " 集''',
    '''                final java.util.List<ProfileParser.ProfileItem> items = itemList;
                // 合集结果也进「合集」子页签 + 落盘，
                // 否则 App 一重启合集列表就没了（用户实测痛点）。
                try{
                    synchronized(itemsFromMix){
                        itemsFromMix.clear();
                        itemsFromMix.addAll(itemList);
                    }
                    saveListPools();
                    Log.i("DYP", "MIX_POOL_SAVE n=" + itemList.size());
                }catch(Throwable t){
                    Log.i("DYP", "MIX_POOL_SAVE_ERR " + t);
                }

                listHead.setText("抖音合集 · 共 " + n + " 集''')

# ⑤ 落盘：appendPoolLine 支持 pool=3
rep('5-落盘合集',
    '''            synchronized(itemsFromProfile){
                for(ProfileParser.ProfileItem it : itemsFromProfile){
                    appendPoolLine(sb, 2, it);
                }
                n2 = itemsFromProfile.size();
            }''',
    '''            synchronized(itemsFromProfile){
                for(ProfileParser.ProfileItem it : itemsFromProfile){
                    appendPoolLine(sb, 2, it);
                }
                n2 = itemsFromProfile.size();
            }
            int n3;
            synchronized(itemsFromMix){
                for(ProfileParser.ProfileItem it : itemsFromMix){
                    appendPoolLine(sb, 3, it);
                }
                n3 = itemsFromMix.size();
            }''')

rep('5b-落盘日志',
    '''            Log.i("DYP", "POOLS_SAVE batch=" + n0 + " single=" + n1
                    + " profile=" + n2 + " bytes=" + f.length());''',
    '''            Log.i("DYP", "POOLS_SAVE batch=" + n0 + " single=" + n1
                    + " profile=" + n2 + " mix=" + n3 + " bytes=" + f.length());''')

# ⑤ 读取：pool=3 → itemsFromMix
rep('5c-读取合集',
    '''            java.util.List<ProfileParser.ProfileItem> l2 =
                    new java.util.ArrayList<ProfileParser.ProfileItem>();''',
    '''            java.util.List<ProfileParser.ProfileItem> l2 =
                    new java.util.ArrayList<ProfileParser.ProfileItem>();
            java.util.List<ProfileParser.ProfileItem> l3 =
                    new java.util.ArrayList<ProfileParser.ProfileItem>();''')

rep('5d-读取分流',
    '''                if(pool == 0) l0.add(it);
                else if(pool == 1) l1.add(it);
                else l2.add(it);''',
    '''                if(pool == 0) l0.add(it);
                else if(pool == 1) l1.add(it);
                else if(pool == 3) l3.add(it);
                else l2.add(it);''')

rep('5e-读取写回',
    '''            synchronized(itemsFromProfile){ itemsFromProfile.clear(); itemsFromProfile.addAll(l2); }
            Log.i("DYP", "POOLS_LOAD batch=" + l0.size() + " single=" + l1.size()
                    + " profile=" + l2.size());''',
    '''            synchronized(itemsFromProfile){ itemsFromProfile.clear(); itemsFromProfile.addAll(l2); }
            synchronized(itemsFromMix){ itemsFromMix.clear(); itemsFromMix.addAll(l3); }
            Log.i("DYP", "POOLS_LOAD batch=" + l0.size() + " single=" + l1.size()
                    + " profile=" + l2.size() + " mix=" + l3.size());''')

io.open(MA, 'w', encoding='utf-8').write(s)
print('成功:', ok)
print('失败:', miss)