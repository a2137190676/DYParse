# -*- coding: utf-8 -*-
"""
修：批量解析里，图文（图集）条目没被识别成图片，导致不下载。

用户实测：3 条链接（2 视频 + 1 图文），只下了 2 个视频，图文没下。

证据（batch_list.tsv 第 3 行）：
    习得吾师真妙法...  https://v.douyin.com/q6y-xl37itA/
        <第3列空>   0  7703551796506428390
        https://p3-pc-sign.douyinpic.com/tos-cn-i-0813c001/oA08fABdkg...
        ↑ isImage=0        ↑ id          ↑ directUrl 其实是图片 CDN 地址

  isImage 应该是 1，却是 0。
  于是批量下载把它当视频处理 → 拿图片地址去下「视频」→ 被 media 校验拒掉 → 没下。

根因（render 的静默批量分支）：

    if(batchSilent && batchRunning){
        collectBatchItem();          // ← 这里就收集了
        ...
    }
    ...
    if(isImg){
        parseImgs(); imgIdx=0;       // ← imgUrls 是在这之后才填的
        ...
    }

  关键：parseImgs() 负责把 curImgs 拆成 imgUrls。
  但在静默批量模式下 collectBatchItem() 先执行，
  那时 imgUrls 还是空的 / 还是上一条的，
  于是 collectBatchItem 里 hasImgs=false → it.isImage=false。

改法：
  1. 静默批量分支里，collectBatchItem() 之前先 parseImgs()。
  2. collectBatchItem 里再加一层判断：
     即使 imgUrls 为空，只要 curImgs 里有 http 地址，也算图集。
     （防御：万一别处又出现在 parseImgs 之前收集）
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
# 1) 静默批量：先 parseImgs 再 collectBatchItem
# ============================================================
rep('1-静默先parseImgs',
    '''                if(batchSilent && batchRunning){
                    Log.i("DYP", "RENDER_SILENT skip 图集=" + isImg);
                    collectBatchItem();''',
    '''                if(batchSilent && batchRunning){
                    Log.i("DYP", "RENDER_SILENT skip 图集=" + isImg);
                    // 【关键顺序】必须先 parseImgs() 把 curImgs 拆进 imgUrls，
                    // 再 collectBatchItem()。
                    //
                    // 原来直接 collectBatchItem()，那时 imgUrls 还是空的，
                    // 于是 hasImgs=false → it.isImage=false，
                    // 图集条目被当成视频：批量下载拿图片地址去下「视频」，
                    // 必然失败，表现就是「图文没下载」。
                    try{
                        parseImgs();
                        Log.i("DYP", "BATCH_PRE_IMGS n=" + imgUrls.size()
                                + " curImgsLen=" + (curImgs == null ? 0 : curImgs.length()));
                    }catch(Throwable t){
                        Log.i("DYP", "BATCH_PRE_IMGS_ERR " + t);
                    }
                    collectBatchItem();''')

# ============================================================
# 2) collectBatchItem 里加防御：curImgs 有地址也算图集
# ============================================================
rep('2-collect防御图集',
    '''            // 没解析出东西就不收（避免空条目）
            boolean hasImgs = imgUrls != null && imgUrls.size() > 0;
            boolean hasVideo = curPlay != null && curPlay.length() > 0;''',
    '''            // 没解析出东西就不收（避免空条目）
            boolean hasImgs = imgUrls != null && imgUrls.size() > 0;
            // 防御：万一 parseImgs() 还没跑（imgUrls 空），
            // 但 curImgs 里已经有 http 图片地址，也应当算图集。
            // 不这么兜的话，图集会被误判成视频，批量下载就会拿图片地址
            // 去下 mp4，最后被 media 校验拒掉 —— 表现是「图文没下载」。
            if(!hasImgs && curImgs != null && curImgs.length() > 0
                    && curImgs.indexOf("http") >= 0){
                try{
                    parseImgs();
                    hasImgs = imgUrls != null && imgUrls.size() > 0;
                    Log.i("DYP", "BATCH_LATE_IMGS n=" + (imgUrls == null ? 0 : imgUrls.size()));
                }catch(Throwable t){}
            }
            boolean hasVideo = curPlay != null && curPlay.length() > 0;''')

io.open(P, 'w', encoding='utf-8').write(s)
print('成功:', ok)
print('失败:', miss)