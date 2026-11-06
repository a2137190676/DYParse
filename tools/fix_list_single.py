# -*- coding: utf-8 -*-
"""修「单条解析后不进列表」：
   根因：collectBatchItem() 第一行 `if(!batchCollectMode) return;`
        → 只有批量解析才收集，单条解析永远不进列表。
   修法：去掉这个门槛，单条解析成功也把结果收进 batchCollected 并落盘。
        同时按 URL 去重，避免同一条反复解析堆一堆重复项。
"""
import io

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/MainActivity.java'
s = io.open(P, encoding='utf-8').read()
n = 0

# 1) 去掉 batchCollectMode 门槛 + 加去重 + 落盘
old = '''    void collectBatchItem(){
        if(!batchCollectMode) return;
        try{
            ProfileParser.ProfileItem it = new ProfileParser.ProfileItem();
            it.url = curUrl == null ? "" : curUrl;
            it.id = curId == null ? "" : curId;
            String t = curDesc == null ? "" : curDesc.trim();
            if(t.length() > 60) t = t.substring(0, 60) + "…";
            if(t.length() == 0) t = curId == null ? "" : curId;
            it.title = t;
            it.directUrl = curPlay == null ? "" : curPlay;
            boolean hasImgs = imgUrls != null && imgUrls.size() > 0;
            it.isImage = hasImgs;
            if(hasImgs) it.directUrl = imgUrls.get(0);
            // 封面：图集用第一张图，视频用解析出的封面
            if(hasImgs) it.cover = imgUrls.get(0);
            else if(curCover != null && curCover.startsWith("http")) it.cover = curCover;
            synchronized(batchCollected){ batchCollected.add(it); }
            Log.i("DYP", "BATCH_COLLECT now=" + batchCollected.size()
                + " img=" + hasImgs + " title=" + t.substring(0, Math.min(30, t.length())));
        }catch(Throwable t){
            Log.i("DYP", "BATCH_COLLECT_ERR " + t);
        }
    }'''

new = '''    /** 把当前解析结果收进「列表」页。
     *  单条解析和批量解析都收（之前只在 batchCollectMode 下收集，
     *  导致用户单独解析一条后列表里什么都没有）。 */
    void collectBatchItem(){
        try{
            String u = curUrl == null ? "" : curUrl;
            // 没解析出东西就不收（避免空条目）
            boolean hasImgs = imgUrls != null && imgUrls.size() > 0;
            boolean hasVideo = curPlay != null && curPlay.length() > 0;
            if(!hasImgs && !hasVideo){
                Log.i("DYP", "BATCH_COLLECT_SKIP 无地址 url=" + u);
                return;
            }
            ProfileParser.ProfileItem it = new ProfileParser.ProfileItem();
            it.url = u;
            it.id = curId == null ? "" : curId;
            String t = curDesc == null ? "" : curDesc.trim();
            if(t.length() > 60) t = t.substring(0, 60) + "…";
            if(t.length() == 0) t = curId == null ? "" : curId;
            it.title = t;
            it.directUrl = curPlay == null ? "" : curPlay;
            it.isImage = hasImgs;
            if(hasImgs) it.directUrl = imgUrls.get(0);
            // 封面：图集用第一张图，视频用解析出的封面
            if(hasImgs) it.cover = imgUrls.get(0);
            else if(curCover != null && curCover.startsWith("http")) it.cover = curCover;

            boolean dup = false;
            synchronized(batchCollected){
                // 同一条链接只保留一份（重复解析不重复堆叠）
                for(ProfileParser.ProfileItem o : batchCollected){
                    if(o == null) continue;
                    if(u.length() > 0 && u.equals(o.url)){
                        // 已存在：刷新地址/标题（链接内容可能变）
                        o.title = it.title;
                        o.directUrl = it.directUrl;
                        o.cover = it.cover;
                        o.isImage = it.isImage;
                        dup = true;
                        break;
                    }
                }
                if(!dup) batchCollected.add(0, it);
            }
            if(!dup) saveBatchList();   // 落盘：退出重进仍在
            Log.i("DYP", "BATCH_COLLECT now=" + batchCollected.size()
                + " img=" + hasImgs + " dup=" + dup
                + " title=" + t.substring(0, Math.min(30, t.length())));
            if(!dup){
                h.post(new Runnable(){ public void run(){
                    try{
                        renderBatchList();
                        Log.i("DYP", "LIST_REFRESH_AFTER_PARSE");
                    }catch(Throwable t2){
                        Log.i("DYP", "LIST_REFRESH_ERR " + t2);
                    }
                }});
            }
        }catch(Throwable t){
            Log.i("DYP", "BATCH_COLLECT_ERR " + t);
        }
    }'''

if old in s:
    s = s.replace(old, new, 1); n += 1; print('1) collectBatchItem 支持单条 OK')
else:
    print('MISS 1')

io.open(P, 'w', encoding='utf-8').write(s)
print('changed:', n)