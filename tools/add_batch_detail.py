# -*- coding: utf-8 -*-
"""实现 fetchDetailForBatch + detail 回调入队。"""
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
# 1) 字段 + fetchDetailForBatch
# ============================================================
rep('1-加字段和fetch',
    '''    /** 下载任务：cat = 分类目录（视频/图片/音频/合集），mixDir = 合集专属子目录（可为空） */''',
    '''    // ============================================================
    // 批量下载的 detail 通道
    //
    // 为什么要它：主页列表接口返回的 video 字段是残缺的，
    // 38 条里 30 多条只带 music 轨（douyinstatic/ies-music/*.mp3），
    // 拿这种地址下载就是「只有音频没有画面」。
    //
    // 而 /aweme/v1/web/aweme/detail/ 返回的 aweme_detail.video 是完整的，
    // bit_rate[] 每档都带真正的视频 CDN（xxx.douyinvod.com）。
    // 只发一次 XHR，比打开整页快得多，也不会触发页面级风控。
    // ============================================================
    /** 是否正在等批量 detail 结果 */
    volatile boolean batchDetailWant = false;
    /** 正在等哪个作品的 detail */
    volatile String batchDetailId = "";

    /** 批量用：按作品 id 请求 detail，拿到真视频地址后入队 */
    void fetchDetailForBatch(final String awemeId){
        if(web == null || !webReady){
            Log.i("DYP", "BATCH_DETAIL_SKIP web_not_ready");
            batchDetailWant = false;
            return;
        }
        // 参数形态必须和页面自身请求一致，否则抖音返回 "Blocked by..." HTML。
        // 这段照搬 fetchDetailMobile() 里已经验证过的那套参数。
        String url = "/aweme/v1/web/aweme/detail/?device_platform=webapp&aid=6383"
            + "&channel=channel_pc_web&aweme_id=" + awemeId
            + "&pc_client_type=1&version_code=190500&version_name=19.5.0"
            + "&cookie_enabled=true&screen_width=1536&screen_height=864"
            + "&browser_language=zh-CN&browser_platform=Win32&browser_name=Chrome"
            + "&browser_version=120.0.0.0&browser_online=true";
        StringBuilder sb = new StringBuilder();
        sb.append("(function(){try{");
        sb.append("fetch('").append(url).append("',{credentials:'include'})");
        sb.append(".then(function(r){return r.text();})");
        sb.append(".then(function(t){");
        sb.append("if(!t||(t.charAt(0)!='{'&&t.charAt(0)!='[')){");
        sb.append("window.DyBridge.onBatchDetail(JSON.stringify({err:'BLOCKED:'+t.slice(0,60)}));return;}");
        sb.append("try{var j=JSON.parse(t);var ad=j.aweme_detail;");
        sb.append("if(!ad){window.DyBridge.onBatchDetail(JSON.stringify({err:'no_detail'}));return;}");
        sb.append("var v=ad.video||{};var brs=v.bit_rate||[];var best=null;var bw=0;");
        sb.append("for(var i=0;i<brs.length;i++){var b=brs[i];var pa=b.play_addr;");
        sb.append("if(!pa)continue;var ul=pa.url_list||[];if(!ul.length)continue;");
        sb.append("var ww=(pa.width||0)*(pa.height||0);if(ww>=bw){bw=ww;best=ul[0];}}");
        // bit_rate 没给就用顶层 play_addr / download_addr
        sb.append("if(!best){var pa2=v.play_addr;if(pa2&&pa2.url_list&&pa2.url_list.length)best=pa2.url_list[0];}");
        sb.append("if(!best){var da=v.download_addr;if(da&&da.url_list&&da.url_list.length)best=da.url_list[0];}");
        sb.append("window.DyBridge.onBatchDetail(JSON.stringify({id:'")
          .append(awemeId).append("',url:best||'',n:brs.length}));");
        sb.append("}catch(e){window.DyBridge.onBatchDetail(JSON.stringify({err:'p'+e}));}");
        sb.append("}).catch(function(e){");
        sb.append("window.DyBridge.onBatchDetail(JSON.stringify({err:'f'+e}));});");
        sb.append("}catch(e){window.DyBridge.onBatchDetail(JSON.stringify({err:'x'+e}));}})()");
        Log.i("DYP", "BATCH_DETAIL_START id=" + awemeId);
        try{ web.evaluateJavascript(sb.toString(), null); }
        catch(Throwable t){ Log.i("DYP", "BATCH_DETAIL_EVAL_ERR " + t); batchDetailWant = false; }
    }

    /** JS 回传的批量 detail 结果 */
    void onBatchDetailFromWeb(final String body){
        final String b = body == null ? "" : body;
        new Thread(new Runnable(){ public void run(){
            try{
                Object root = new JP(b).parse();
                if(!(root instanceof Map)){
                    Log.i("DYP", "BATCH_DETAIL_BAD " + b);
                    batchDetailWant = false;
                    h.post(new Runnable(){ public void run(){ advanceBatchAfterDetail(false); }});
                    return;
                }
                Map m = (Map) root;
                String err = gs(get(m, "err"));
                String vurl = gs(get(m, "url"));
                String id = gs(get(m, "id"));
                if(err.length() > 0 || vurl.length() == 0){
                    Log.i("DYP", "BATCH_DETAIL_FAIL id=" + id + " err=" + err
                            + " url_len=" + vurl.length());
                    batchDetailWant = false;
                    h.post(new Runnable(){ public void run(){ advanceBatchAfterDetail(false); }});
                    return;
                }
                // 音频也拒掉（detail 极少出现，但不能不防）
                if(isLikelyAudioUrl(vurl)){
                    Log.i("DYP", "BATCH_DETAIL_AUDIO id=" + id + " url="
                            + vurl.substring(0, Math.min(80, vurl.length())));
                    batchDetailWant = false;
                    h.post(new Runnable(){ public void run(){ advanceBatchAfterDetail(false); }});
                    return;
                }
                Log.i("DYP", "BATCH_DETAIL_OK id=" + id + " url="
                        + vurl.substring(0, Math.min(80, vurl.length())));
                batchDetailWant = false;
                final String vv = vurl;
                h.post(new Runnable(){ public void run(){
                    String tag = "视频" + (batchDlIndex + 1);
                    Log.i("DYP", "BATCH_DL_ENQ_DETAIL tag=" + tag + " plat=抖音");
                    enqueueDownload(vv, tag, "mp4", "视频", "", "抖音", id);
                    batchDlOk++;
                    advanceBatchAfterDetail(true);
                }});
            }catch(Throwable t){
                Log.i("DYP", "BATCH_DETAIL_PARSE_ERR " + t);
                batchDetailWant = false;
                h.post(new Runnable(){ public void run(){ advanceBatchAfterDetail(false); }});
            }
        }}).start();
    }

    /** detail 拿到（或失败）后推进批量队列 */
    void advanceBatchAfterDetail(boolean ok){
        if(batchDlIndex < 0 || batchDlIndex >= batchDlQueue.size()) return;
        batchDlIndex++;
        if(batchDlIndex >= batchDlTotal){
            s("批量下载完成：成功入队 " + batchDlOk + "/" + batchDlTotal + " 条");
            showHintToast("批量完成：" + batchDlOk + "/" + batchDlTotal + " 条已入队");
            int okc = batchDlOk;
            batchDlIndex = -1;
            batchDlQueue.clear();
            batchDlTotal = 0;
            batchDlOk = 0;
            Log.i("DYP", "BATCH_DL_DONE ok=" + okc);
            return;
        }
        s("批量下载 " + (batchDlIndex + 1) + "/" + batchDlTotal + "…");
        h.postDelayed(new Runnable(){ public void run(){ stepBatchProfileDl(); }}, 300);
    }

    /** 下载任务：cat = 分类目录（视频/图片/音频/合集），mixDir = 合集专属子目录（可为空） */''')

# ============================================================
# 2) 注册 JS 接口 onBatchDetail
# ============================================================
rep('2-注册JS接口',
    '''        @JavascriptInterface
        public void onDetailData(final String body){''',
    '''        @JavascriptInterface
        public void onBatchDetail(final String body){
            onBatchDetailFromWeb(body);
        }

        @JavascriptInterface
        public void onDetailData(final String body){''')

io.open(P, 'w', encoding='utf-8').write(s)
print('成功:', ok)
print('失败:', miss)