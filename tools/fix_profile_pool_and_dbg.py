# -*- coding: utf-8 -*-
"""
两件事：
【A】showProfileList 写完池子没落盘 → 加 saveListPools()
【B】主页列表 directUrl 全空 → 加诊断日志，看 aweme 里到底有什么 key

【B 的现状证据】
  · PROF_ITEM_URL 日志：0 条
  · PROF_ITEM_AUDIO 日志：0 条
  → appendDyProfileItems 里 best 从头到尾都是空，
    三级回退（bit_rate / play_addr / vid拼接）全部失败。
  → 批量下载拿不到直链，全落到 detail 通道，
    而 detail 要 uifid → 403 → 0/4 全部失败。

  所以必须先搞清楚：主页接口返回的 aweme 里，
  video 字段到底还在不在？bit_rate 在不在？
  加一条诊断，把第一条的 key 集合和 video 结构打出来。
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
# A. 池子落盘
# ============================================================
rep('A-池子落盘',
    '''                DypLog.i("LIST_POOL_SAVE src=" + srcName + " n=" + pool.size());''',
    '''                DypLog.i("LIST_POOL_SAVE src=" + srcName + " n=" + pool.size());
                // 写完立刻落盘。
                // 之前漏了这一行：主页解析 36 条明明进了 itemsFromProfile，
                // 但 list_pools.tsv 里一条都没有，重启就全丢。
                saveListPools();''')

# ============================================================
# B. 诊断：主页 aweme 结构
# ============================================================
rep('B-诊断日志',
    '''            if(!isImage){
                Object video = get(aw, "video");
                if(video instanceof Map){''',
    '''            if(!isImage){
                Object video = get(aw, "video");
                // 【诊断】主页接口的 aweme 结构到底长什么样。
                // 现在的现象是 PROF_ITEM_URL 一条都不打，
                // 说明 video.play_addr / bit_rate / vid 全都没取到。
                // 先看看 key 集合，判断是「video 字段没了」还是「结构变了」。
                if(profileDbgLeft > 0){
                    profileDbgLeft--;
                    try{
                        StringBuilder kb = new StringBuilder();
                        for(Object k : aw.keySet()){
                            if(k == null) continue;
                            if(kb.length() > 0) kb.append(',');
                            kb.append(k);
                        }
                        String vin = (video instanceof Map) ? "Map" : String.valueOf(video);
                        String brInfo = "-";
                        if(video instanceof Map){
                            Object brs = ((Map) video).get("bit_rate");
                            brInfo = (brs instanceof List)
                                    ? ("List/" + ((List) brs).size()) : String.valueOf(brs);
                        }
                        DypLog.i("PROF_DBG id=" + awemeId + " isImg=" + isImage
                                + " video=" + vin + " bit_rate=" + brInfo
                                + " keys=[" + kb + "]");
                    }catch(Throwable t){
                        DypLog.i("PROF_DBG_ERR " + t);
                    }
                }
                if(video instanceof Map){''')

# 诊断计数器（只打前 3 条，避免刷屏）
rep('B-计数器',
    '''    int appendDyProfileItems(List list){''',
    '''    /** 主页结构诊断：只打前几条，避免刷屏 */
    volatile int profileDbgLeft = 0;

    int appendDyProfileItems(List list){''')

# 每次主页解析开始时重置
rep('B-重置',
    '''                added = appendDyProfileItems(list);''',
    '''                profileDbgLeft = 3;
                added = appendDyProfileItems(list);''')

io.open(P, 'w', encoding='utf-8').write(s)
print('成功:', ok)
print('失败:', miss)