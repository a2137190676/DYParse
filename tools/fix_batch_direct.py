# -*- coding: utf-8 -*-
"""
核心修法：批量下载直接用主页列表里已有的 play_addr 直链，不再逐条开分享页。

【为什么这是关键】
  主页列表接口返回的 aweme_list 里，每一条本来就有
      video.play_addr.url_list[0]
  也就是无水印视频直链。appendDyProfileItems()（第 6213 行）早就把它
  存进了 it.directUrl。

  但 stepBatchProfileDl() 完全没用它，而是对每一条都
      loadForParse("https://www.iesdouyin.com/share/video/<id>/")
  也就是「为了拿一个已经有的直链，重新开一次分享页」。

  37 条 = 37 次页面请求 = 37 次风控机会。
  这就是「批量下载一直弹验证」的根本原因 —— 自己把自己搞进风控。

【改法】
  1. stepBatchProfileDl 里：如果 it.directUrl 可用（过 rejectNonMediaUrl 校验），
     直接 enqueueDownload 入队，根本不碰 WebView。
  2. 只有 directUrl 为空（少数条目接口没给）才回落到 loadForParse 老路径。
  3. 直链批量不再需要 batchDlKicked 那些提前踢的逻辑，直接进队列，
     由下载队列按并发数自己跑。

  风控日志里的「后面解析的视频都弹这个」也会随之消失 ——
  因为根本不解析了。
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
# stepBatchProfileDl：优先用 directUrl 直接入队
# ============================================================
rep('1-批量优先用直链',
    '''        pendingAutoDlTag = "视频" + (batchDlIndex + 1);
        pendingAutoDlIdx = batchDlIndex;
        s("批量下载 " + (batchDlIndex + 1) + "/" + batchDlTotal + "：解析中…");
        Log.i("DYP", "BATCH_DL_PARSE idx=" + batchDlIndex + "/" + batchDlTotal + " url=" + u);
        loadForParse(u);''',
    '''        // ============================================================
        // 【关键优化】优先用主页列表接口已经给出的 play_addr 直链。
        //
        // aweme_list 里每条都带 video.play_addr.url_list[0]，
        // appendDyProfileItems() 早就把它存进了 it.directUrl。
        //
        // 旧逻辑却对每条都 loadForParse 一次分享页 ——
        // 相当于「为了拿一个手里已经有的直链，去重新请求一次抖音」。
        // 37 条就是 37 次请求，必然触发风控，
        // 表现就是「批量下载每条都弹验证，只能成功一两条」。
        //
        // 现在：有直链就直接入队，一次网络请求都不发，风控无从触发。
        // 只有极少数条目没给 directUrl 时，才回落老路径。
        // ============================================================
        String direct = (it.directUrl != null) ? it.directUrl : "";
        String why = rejectNonMediaUrl(direct);
        if(direct.length() > 0 && why.length() == 0){
            String tag = "视频" + (batchDlIndex + 1);
            Log.i("DYP", "BATCH_DL_DIRECT idx=" + batchDlIndex + "/" + batchDlTotal
                    + " tag=" + tag);
            enqueueDownload(direct, tag, "mp4", "视频");
            batchDlOk++;
            batchDlIndex++;
            s("批量下载 " + batchDlIndex + "/" + batchDlTotal + "：已入队（直链）");
            // 全部走直链，不需要等 WebView，直接推进下一条
            h.postDelayed(new Runnable(){ public void run(){ stepBatchProfileDl(); }}, 20);
            return;
        }
        if(direct.length() > 0 && why.length() > 0){
            Log.i("DYP", "BATCH_DL_DIRECT_REJECT idx=" + batchDlIndex + " why=" + why);
        }

        // ---- 回落：接口没给直链，才走 WebView 解析 ----
        pendingAutoDlTag = "视频" + (batchDlIndex + 1);
        pendingAutoDlIdx = batchDlIndex;
        s("批量下载 " + (batchDlIndex + 1) + "/" + batchDlTotal + "：解析中…");
        Log.i("DYP", "BATCH_DL_PARSE idx=" + batchDlIndex + "/" + batchDlTotal + " url=" + u);
        loadForParse(u);''')

io.open(P, 'w', encoding='utf-8').write(s)
print('成功:', ok)
print('失败:', miss)