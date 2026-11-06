# -*- coding: utf-8 -*-
"""
修两个 UI 错位 bug。

【bug 1】点「主页解析」高亮跑到中间「视频/图文解析」
   switchListSub 里的数组顺序是旧的：
       { subTabBatchParse, subTabProfileParse, subTabBatchDl }
   但布局现在的顺序是：
       批量解析(0) / 视频·图文解析(1) / 主页解析(2)
   也就是 subTabBatchDl 应该排第 2 个位置。
   数组没跟着改 → 高亮错位、点击映射也跟着错。

【bug 2】表头文案永远是「视频/图文解析 · 共 N 条」
   原来是：
       String _src = profileMode ? "主页解析" : (batchCollectMode ? "批量解析" : "视频/图文解析");
   profileMode / batchCollectMode 是「当前解析流程走到哪」的全局标志，
   不是「用户现在看的是哪个子页签」。
   子页签切换时这俩标志不会变，所以文案永远是最后一次解析来源的名。

   应该用 listSubTab 这个「当前选中子页签」来决定文案。
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
# 1) 统一定义子页签顺序（只有一处，避免再次错位）
# ============================================================
rep('1-加子页签顺序方法',
    '''    /** 切列表页子页签 */
    void switchListSub(int idx){''',
    '''    /**
     * 子页签在布局里的真实顺序。
     * 只在这里定义一次 —— 高亮、点击、表头文案都从这里取，
     * 避免「数组顺序和布局顺序不一致」导致高亮错位。
     *
     *   0 = 批量解析        subTabBatchParse
     *   1 = 视频/图文解析    subTabBatchDl
     *   2 = 主页解析        subTabProfileParse
     */
    TextView[] listSubViews(){
        return new TextView[]{ subTabBatchParse, subTabBatchDl, subTabProfileParse };
    }

    /** 子页签名字 */
    static String listSubName(int idx){
        if(idx == 0) return "批量解析";
        if(idx == 2) return "主页解析";
        return "视频/图文解析";
    }

    /** 切列表页子页签 */
    void switchListSub(int idx){''')

# ============================================================
# 2) 高亮用正确顺序
# ============================================================
rep('2-高亮顺序',
    '''            TextView[] subs = { subTabBatchParse, subTabProfileParse, subTabBatchDl };
            for(int i = 0; i < subs.length; i++){
                if(subs[i] == null) continue;
                if(i == idx){
                    subs[i].setBackground(makeRoundRect(0x1AFE2C55, 0, 10));
                    subs[i].setTextColor(actC);
                    subs[i].setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                }else{
                    subs[i].setBackground(makeRoundRect(0x0DFFFFFF, 0x14FFFFFF, 10));
                    subs[i].setTextColor(nC);
                    subs[i].setTypeface(android.graphics.Typeface.DEFAULT);
                }
            }
            // 取该子页对应的数据重新渲染''',
    '''            TextView[] subs = listSubViews();
            for(int i = 0; i < subs.length; i++){
                if(subs[i] == null) continue;
                if(i == idx){
                    subs[i].setBackground(makeRoundRect(0x1AFE2C55, 0, 10));
                    subs[i].setTextColor(actC);
                    subs[i].setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                }else{
                    subs[i].setBackground(makeRoundRect(0x0DFFFFFF, 0x14FFFFFF, 10));
                    subs[i].setTextColor(nC);
                    subs[i].setTypeface(android.graphics.Typeface.DEFAULT);
                }
            }
            // 取该子页对应的数据重新渲染''')

# ============================================================
# 3) 表头文案改用 listSubTab
# ============================================================
rep('3-表头用listSubTab',
    '''        String _src = profileMode ? "主页解析" : (batchCollectMode ? "批量解析" : "视频/图文解析");''',
    '''        // 【关键】文案跟「当前选中的子页签」走，不跟全局解析标志走。
        // 以前用 profileMode/batchCollectMode，切子页签时这俩不变，
        // 于是永远显示最后一次解析来源的名字（用户看到的是「视频/图文解析」）。
        String _src = listRendering ? listSubName(listSubTab)
                : (profileMode ? "主页解析"
                   : (batchCollectMode ? "批量解析" : "视频/图文解析"));''')

# ============================================================
# 4) 自动切子页那段的数组顺序也统一
# ============================================================
rep('4-自动切数组顺序',
    '''                            TextView[] subs = { subTabBatchParse, subTabProfileParse, subTabBatchDl };
                            for(int i = 0; i < subs.length; i++){
                                if(subs[i] == null) continue;
                                if(i == want){''',
    '''                            TextView[] subs = listSubViews();
                            for(int i = 0; i < subs.length; i++){
                                if(subs[i] == null) continue;
                                if(i == want){''')

io.open(P, 'w', encoding='utf-8').write(s)
print('成功:', ok)
print('失败:', miss)