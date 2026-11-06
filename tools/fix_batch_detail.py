# -*- coding: utf-8 -*-
"""
批量下载的音频问题——真正解法。

【实测证据】
加了 PROF_ITEM_AUDIO 诊断后，主页 38 条里 30 多条都打这个：
    PROF_ITEM_AUDIO id=7703531375761321039
        url=https://sf11-cdn-tos.douyinstatic.com/obj/ies-music/7210597696699206455.mp3
    PROF_ITEM_AUDIO id=7703121486688570664
        url=https://sf6-cdn-tos.douyinstatic.com/obj/tos-cn-ve-2774/okeVxKDgIBCEbTsZfaFAMM9F
    PROF_ITEM_URL  id=7702833793832422696
        url=https://v26-web.douyinvod.com/.../video/to        ← 只有少数是这种

结论：**主页列表接口返回的 video 字段本身是残缺的**，
大部分条目只带 music 轨，不带视频流。
不是我选错档（bit_rate 三级回退都试过了），是数据源没有。

【为什么 WebView 逐个解析能拿到】
项目里已有 fetchDetailMobile()，走页面内 fetch：
    /aweme/v1/web/aweme/detail/?...&aweme_id=<id>
响应里的 aweme_detail.video 是**完整**的，
bit_rate[] 每一档都带 play_addr（真正的视频 CDN）。
单条解析（用户一直用得好好的）走的就是这条路。

【所以批量下载应该这样】
    列表有可用视频直链 → 直接用（快，无请求）
    列表只有音频/没有  → 调 detail 接口按 id 取真视频（准）

这正是用户要的：「下载的必须是视频」。

改法：
  1. 新增 pendingBatchDetail 标记：批量流程进入「等 detail 结果」状态。
  2. 批量 stepBatchProfileDl 里，directUrl 不可用时，
     不调 loadForParse（那是整页解析，重且慢），
     改为 findDetailById(awemeId) → 直接拿真视频地址入队。
  3. detail 结果由 onDetailData 回调，解析到 vurl 后入队并推进下一条。
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
# 1) 批量：directUrl 不可用 → 用 detail 接口按 id 取真视频
# ============================================================
rep('1-批量走detail',
    '''        // ---- 回落：接口没给直链，才走 WebView 解析 ----
        pendingAutoDlTag = "视频" + (batchDlIndex + 1);
        pendingAutoDlIdx = batchDlIndex;
        s("批量下载 " + (batchDlIndex + 1) + "/" + batchDlTotal + "：解析中…");
        Log.i("DYP", "BATCH_DL_PARSE idx=" + batchDlIndex + "/" + batchDlTotal + " url=" + u);
        loadForParse(u);''',
    '''        // ---- 回落一：有作品 id → 调 detail 接口取真视频 ----
        //
        // 为什么不用 loadForParse（打开整页）：
        //  · 慢（每条 5~20 秒）
        //  · 重（整页渲染、签名、Cookie 都要过一遍）
        //  · 容易触发风控（37 条 = 37 次页面请求）
        //
        // detail 接口只发一次 XHR，返回的 aweme_detail.video 是完整的，
        // bit_rate[] 每档都带真正的视频 CDN 地址。
        String awemeId = (it.id != null) ? it.id.trim() : "";
        boolean hasId = awemeId.matches("\\\\d{15,20}");
        if(!hasId){
            // 从 url 里再抠一次
            String ex = extractMediaId(u);
            if(ex.matches("\\\\d{15,20}")){ awemeId = ex; hasId = true; }
        }
        if(hasId){
            pendingAutoDlTag = "视频" + (batchDlIndex + 1);
            pendingAutoDlIdx = batchDlIndex;
            batchDetailId = awemeId;
            batchDetailWant = true;
            s("批量下载 " + (batchDlIndex + 1) + "/" + batchDlTotal + "：取直链…");
            Log.i("DYP", "BATCH_DL_DETAIL idx=" + batchDlIndex + "/" + batchDlTotal
                    + " id=" + awemeId);
            try{ fetchDetailForBatch(awemeId); }catch(Throwable t){
                Log.i("DYP", "BATCH_DL_DETAIL_ERR " + t);
            }
            // 超时兜底：detail 15 秒没回来，退回整页解析
            final int myIdxD = batchDlIndex;
            h.postDelayed(new Runnable(){ public void run(){
                if(batchDetailWant && myIdxD == batchDlIndex){
                    Log.i("DYP", "BATCH_DL_DETAIL_TIMEOUT idx=" + myIdxD + " 退回整页解析");
                    batchDetailWant = false;
                    pendingAutoDlTag = "视频" + (myIdxD + 1);
                    pendingAutoDlIdx = myIdxD;
                    loadForParse(u);
                }
            }}, 15000L);
            return;
        }

        // ---- 回落二：连 id 都没有，只能走整页解析 ----
        pendingAutoDlTag = "视频" + (batchDlIndex + 1);
        pendingAutoDlIdx = batchDlIndex;
        s("批量下载 " + (batchDlIndex + 1) + "/" + batchDlTotal + "：解析中…");
        Log.i("DYP", "BATCH_DL_PARSE idx=" + batchDlIndex + "/" + batchDlTotal + " url=" + u);
        loadForParse(u);''')

io.open(P, 'w', encoding='utf-8').write(s)
print('成功:', ok)
print('失败:', miss)