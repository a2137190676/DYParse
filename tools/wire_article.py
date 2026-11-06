# -*- coding: utf-8 -*-
"""把「文章」接到两条下载链路上：detail 单条 + 批量队列。"""
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


# ============ 1. fetchDetailForBatch：文章优先 ============
rep('1-批量文章',
    '''                String best = "";
                // 【音乐作品识别】aweme_type=163 / media_type=43 是抖音「音乐/BGM 作品」，''',
    '''                // 【文章最先判】抖音文章也带 aweme_type=163/media_type=43，
                // 必须靠 article_info 区分，否则会被当音乐作品跳过。
                try{
                    Object artObj = get(adM, "article_info");
                    if(artObj instanceof Map){
                        String atitle = gs(get((Map) artObj, "article_title"));
                        String amd = dyArticleMd(artObj);
                        String saved = saveDyArticle(awemeId, atitle, amd);
                        DypLog.i("BATCH_DETAIL_ARTICLE id=" + awemeId
                                + " title=" + atitle + " mdLen=" + amd.length()
                                + " saved=" + saved);
                        batchDetailWant = false;
                        final int aidx = batchDlIndex;
                        final boolean aok = saved.length() > 0;
                        h.post(new Runnable(){ public void run(){
                            if(aok) batchDlOk++;
                            batchDlIndex = aidx;
                            advanceBatchAfterDetail(aok);
                        }});
                        return;
                    }
                }catch(Throwable t){}

                String best = "";
                // 【音乐作品识别】aweme_type=163 / media_type=43 是抖音「音乐/BGM 作品」，''')

# ============ 2. stepBatchProfileDl：文章直接走 detail ============
rep('2-队列文章',
    '''        if(it.kind == ProfileParser.KIND_MUSIC){''',
    '''        if(it.kind == ProfileParser.KIND_ARTICLE){
            // 文章正文只在 detail 里，必须走一次 detail。
            String aid = (it.id != null) ? it.id.trim() : "";
            if(aid.matches("\\\\d{15,20}")){
                DypLog.i("BATCH_DL_KIND_ARTICLE idx=" + batchDlIndex + " id=" + aid
                        + " 走 detail 取正文");
                batchDetailWant = true;
                pendingAutoDlTag = "文章" + (batchDlIndex + 1);
                pendingAutoDlIdx = batchDlIndex;
                final int cidx = batchDlIndex;
                s("批量下载 " + (batchDlIndex + 1) + "/" + batchDlTotal + "：取文章…");
                try{ fetchDetailForBatch(aid); }catch(Throwable t){}
                h.postDelayed(new Runnable(){ public void run(){
                    if(batchDlIndex == cidx && batchDetailWant){
                        DypLog.i("BATCH_DL_ARTICLE_TIMEOUT idx=" + cidx);
                        batchDetailWant = false;
                        advanceBatchAfterDetail(false);
                    }
                }}, 20000L);
                return;
            }
            batchDlIndex++;
            h.postDelayed(new Runnable(){ public void run(){ stepBatchProfileDl(); }}, 60);
            return;
        }
        if(it.kind == ProfileParser.KIND_MUSIC){''')

# ============ 3. parseProfileItem（解析看详情）：文章分支 ============
rep('3-详情文章',
    '''                    if(music){
                        s("第 " + (fix + 1) + " 条是抖音音乐作品（只有音频，没有视频）");
                        showHintToast("音乐作品，无视频流");
                        return;
                    }''',
    '''                    if(music){
                        s("第 " + (fix + 1) + " 条是抖音音乐作品（只有音频，没有视频）");
                        showHintToast("音乐作品，无视频流");
                        return;
                    }''')

io.open(P, 'w', encoding='utf-8').write(s)
print('成功:', ok)
print('失败:', miss)