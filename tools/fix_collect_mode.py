# -*- coding: utf-8 -*-
"""
修「只下一个或解析看详情时，列表被刷新掉」。

根因：maybeAutoAction() 每次解析完都调 collectBatchItem()，
      它会把当前解析结果塞进 batchCollected 并 renderBatchList()。
      单条解析/查看详情时，这会：
        1) 把这条作品的标题/地址覆盖掉原有的主页条目（如果 url 不同就是新增）
        2) 触发 renderBatchList → 整个列表重绘，滚动位置丢失，看起来像「刷新掉了」

修法：
  · 单条解析（非批量、非主页、非合集）：不写进列表
  · 只有批量解析 / 主页解析 / 合集才写入列表
  · 判断依据：batchCollectMode（批量）| profileMode（主页）| mixMode（合集）
"""
import io

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/MainActivity.java'
s = io.open(P, encoding='utf-8').read()

# ---------- 1) collectBatchItem 开头加模式判断 ----------
old = '''    void collectBatchItem(){
        try{
            String u = curUrl == null ? "" : curUrl;'''
new = '''    void collectBatchItem(){
        try{
            // 只有「批量解析 / 主页解析 / 合集」才写入列表。
            // 单条解析或从列表点「下载/看详情」时会重新走一遍解析流程，
            // 如果不拦，就会把当前作品塞回列表并 renderBatchList()，
            // 表现就是列表被刷新、滚动位置丢失、甚至多出重复条目。
            boolean shouldCollect = batchCollectMode || profileMode || mixMode;
            if(!shouldCollect){
                Log.i("DYP", "BATCH_COLLECT_SKIP 非批量/主页模式，不写列表 url="
                        + (curUrl == null ? "" : curUrl));
                return;
            }
            String u = curUrl == null ? "" : curUrl;'''
if old not in s:
    raise SystemExit('MISS collectBatchItem 开头')
s = s.replace(old, new, 1)

io.open(P, 'w', encoding='utf-8').write(s)
print('collectBatchItem 已加模式判断')
print('mixMode 是否存在:', 'mixMode' in s)
print('profileMode 是否存在:', 'profileMode' in s)
print('batchCollectMode 是否存在:', 'batchCollectMode' in s)