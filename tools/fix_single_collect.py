# -*- coding: utf-8 -*-
"""
修：单条解析（解析页正常解析）的结果不进「视频/图文解析」子页签。

【现象】用户解析了 live 图和多图集，列表页里什么都没有。
【日志】
    BATCH_COLLECT_SKIP 非批量/主页模式，不写列表 url=https://v.douyin.com/lKmvINyCaf0/
    BATCH_COLLECT_SKIP 非批量/主页模式，不写列表 url=https://v.douyin.com/OcZmCIXlyf8/

【根因】collectBatchItem 开头：
    boolean shouldCollect = batchCollectMode || profileMode || mixMode;
    if(!shouldCollect){ ...; return; }
  单条解析三个都是 false，直接 return，一个池子都不进。

【为什么不能简单放开】
  collectBatchItem 最后会调 renderBatchList()，
  而 renderBatchList() 是拿 batchCollected 整个重绘的，
  batchCollected 又是批量解析/主页解析共用的那份数据。
  单条解析一放开，就会把批量池里已有的条目冲掉。

【改法】
  shouldCollect 为 false（=单条解析）时，不进 collectBatchItem 老流程，
  改为走 collectSingleItem()：
    · 只写 itemsFromSingle 这一个池
    · 不碰 batchCollected、不调 renderBatchList
    · 同时保留 imgs/dyns 全量收集，图集/实况才能被批量下载
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
# 1) shouldCollect 为 false 时改走 collectSingleItem()
# ============================================================
rep('1-单条走新流程',
    '''            boolean shouldCollect = batchCollectMode || profileMode || mixMode;
            if(!shouldCollect){
                Log.i("DYP", "BATCH_COLLECT_SKIP 非批量/主页模式，不写列表 url="
                        + (curUrl == null ? "" : curUrl));
                return;
            }''',
    '''            boolean shouldCollect = batchCollectMode || profileMode || mixMode;
            if(!shouldCollect){
                // 单条解析（解析页正常解析）：结果要进「视频/图文解析」子页签。
                //
                // 不能在这里直接 return —— 那样用户单独解析一条，
                // 列表页「视频/图文解析」里永远是空的。
                // 也不能放开往下走 —— 下面最后会 renderBatchList()，
                // 那是拿 batchCollected 整个重绘，
                // 会把批量解析/主页解析已经收集的结果冲掉。
                //
                // 所以单条走独立函数，只写 itemsFromSingle。
                collectSingleItem();
                return;
            }''')

# ============================================================
# 2) 新增 collectSingleItem()
# ============================================================
anchor = '''    void collectBatchItem(){'''
helper = '''    /** 单条解析（解析页正常解析）→ 只写「视频/图文解析」子页签的数据池。
     *
     *  和 collectBatchItem 的区别：
     *    · 不碰 batchCollected（那是批量/主页共用的渲染数据）
     *    · 不调 renderBatchList()（否则会把当前页刷掉）
     *    · 同一条重复解析只保留最新一份（刷新地址/标题）
     *
     *  但图集的全量图片 + 实况视频必须照收，
     *  否则「视频/图文解析」里的图集没法批量下载。
     */
    void collectSingleItem(){
        try{
            String u = curUrl == null ? "" : curUrl;
            boolean hasImgs = imgUrls != null && imgUrls.size() > 0;
            if(!hasImgs && curImgs != null && curImgs.length() > 0
                    && curImgs.indexOf("http") >= 0){
                try{
                    parseImgs();
                    hasImgs = imgUrls != null && imgUrls.size() > 0;
                }catch(Throwable t){}
            }
            String purl = curPlay == null ? "" : curPlay;
            if(purl.indexOf("/aweme/v1/playwm/") > 0){
                purl = purl.replace("/aweme/v1/playwm/", "/aweme/v1/play/");
            }
            if(purl.length() > 0 && isLikelyAudioUrl(purl)) purl = "";
            boolean hasVideo = purl.length() > 0;
            if(!hasImgs && !hasVideo){
                Log.i("DYP", "SINGLE_COLLECT_SKIP 无地址 url=" + u);
                return;
            }
            String sid = curId == null ? "" : curId;
            String ttl = curDesc == null ? "" : curDesc.trim();
            if(ttl.length() > 60) ttl = ttl.substring(0, 60) + "…";
            if(ttl.length() == 0) ttl = sid;

            synchronized(itemsFromSingle){
                // 同一条链接只留一份
                for(int i = 0; i < itemsFromSingle.size(); i++){
                    ProfileParser.ProfileItem o = itemsFromSingle.get(i);
                    if(o == null) continue;
                    if(u.length() > 0 && u.equals(o.url)){
                        o.title = ttl;
                        o.directUrl = hasImgs ? (imgUrls.get(0)) : purl;
                        o.isImage = hasImgs;
                        o.id = sid;
                        if(hasImgs){
                            o.cover = imgUrls.get(0);
                            o.imgs.clear();
                            o.dyns.clear();
                            for(int gi = 0; gi < imgUrls.size(); gi++){
                                String gu = imgUrls.get(gi);
                                if(gu == null || gu.length() == 0) continue;
                                o.imgs.add(gu);
                                String gd = (dynUrls != null && gi < dynUrls.size())
                                        ? dynUrls.get(gi) : "";
                                o.dyns.add(gd == null ? "" : gd);
                            }
                        }
                        Log.i("DYP", "SINGLE_COLLECT_UPD img=" + hasImgs
                                + " imgs=" + o.imgs.size() + " url=" + u);
                        return;
                    }
                }
                ProfileParser.ProfileItem it = new ProfileParser.ProfileItem();
                it.url = u;
                it.id = sid;
                it.title = ttl;
                it.isImage = hasImgs;
                it.directUrl = hasImgs ? imgUrls.get(0) : purl;
                if(hasImgs){
                    it.cover = imgUrls.get(0);
                    for(int gi = 0; gi < imgUrls.size(); gi++){
                        String gu = imgUrls.get(gi);
                        if(gu == null || gu.length() == 0) continue;
                        it.imgs.add(gu);
                        String gd = (dynUrls != null && gi < dynUrls.size())
                                ? dynUrls.get(gi) : "";
                        it.dyns.add(gd == null ? "" : gd);
                    }
                }else if(curCover != null && curCover.startsWith("http")){
                    it.cover = curCover;
                }
                itemsFromSingle.add(it);
                Log.i("DYP", "SINGLE_COLLECT img=" + hasImgs
                        + " imgs=" + it.imgs.size() + " live=" + liveOf(it.dyns)
                        + " total=" + itemsFromSingle.size() + " url=" + u);
            }
        }catch(Throwable t){
            Log.i("DYP", "SINGLE_COLLECT_ERR " + t);
        }
    }

'''
if 'void collectSingleItem()' not in s:
    s = s.replace(anchor, helper + anchor, 1)
    ok.append('2-collectSingleItem')
else:
    ok.append('2-已存在')

io.open(P, 'w', encoding='utf-8').write(s)
print('成功:', ok)
print('失败:', miss)