# -*- coding: utf-8 -*-
"""
修主页批量下载：下回来的是 HTML 页面（相册里全是 00:00）。

根因：
  showProfilePickDialog 的「开始下载」按钮直接调
    enqueueDownload(it.url, ...)   ← it.url 是作品详情页 https://www.douyin.com/video/xxx
  下载器把它当直链下，拿到的是 HTML 不是视频。

  单条主页下载走的是对的路径：
    loadForParse(u) + pendingAutoDlTag → 解析成功后自动入队 curPlay（真直链）

修法：
  1) 新增 batchDlQueue / batchDlIndex 队列状态
  2) 新增 startBatchProfileDl()：串行触发，每条 loadForParse + pendingAutoDlTag
  3) 在解析完成回调里（行 8872 那段）如果 pendingAutoDlTag 清空了且队列还有下一条，
     自动接着解析下一条
  4) 把 showProfilePickDialog 的两处「开始下载」按钮都改成调 startBatchProfileDl

关键：pendingAutoDlIdx 已有的队列机制复用，不用重写解析流程。
"""
import io

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/MainActivity.java'
s = io.open(P, encoding='utf-8').read()

# ---------- 1) 新增队列状态（放在 pendingAutoDl 声明旁边） ----------
old_state = '''    String pendingAutoDlTag = "";
    int pendingAutoDlIdx = -1;'''
new_state = '''    String pendingAutoDlTag = "";
    int pendingAutoDlIdx = -1;
    /**
     * 主页批量下载队列。
     *
     * 之前批量下载直接 enqueueDownload(it.url)，it.url 是作品详情页地址
     * （https://www.douyin.com/video/xxx），下载器当直链下，拿到的是 HTML，
     * 相册里全是 00:00 打不开。
     *
     * 现在改成串行触发 loadForParse：每条先解析出 curPlay（真直链）再入队。
     * 解析完一条由 pendingAutoDl 回调（行 ~8872）自动接着下一条。
     */
    java.util.List<ProfileParser.ProfileItem> batchDlQueue = new java.util.ArrayList<ProfileParser.ProfileItem>();
    int batchDlIndex = -1;                 // 当前正在解析第几条（-1 = 没在跑）
    int batchDlTotal = 0;                  // 总条数，用于进度提示
    int batchDlOk = 0;                     // 已成功入队条数
'''
if old_state not in s:
    raise SystemExit('MISS pendingAutoDl 状态')
s = s.replace(old_state, new_state, 1)

# ---------- 2) 新增串行批量函数，插到 showProfilePickDialog 前 ----------
anchor = '    void showProfilePickDialog(final java.util.List<ProfileParser.ProfileItem> list,'
if anchor not in s:
    raise SystemExit('MISS showProfilePickDialog')

helpers = '''    /**
     * 主页批量下载入口：把勾选的条目塞进队列，开始串行解析。
     *
     * 为什么串行：loadForParse 是 WebView 异步的，curPlay/curUrl/pendingAutoDlTag
     * 都是全局的，并发会互相覆盖。串行虽然慢，但能保证每条拿到正确的直链。
     */
    void startBatchProfileDl(java.util.List<ProfileParser.ProfileItem> picked){
        if(picked == null || picked.isEmpty()){
            showHintToast("没有勾选任何作品");
            return;
        }
        // 正在跑就直接忽略，防重复触发
        if(batchDlIndex >= 0 && batchDlIndex < batchDlQueue.size()){
            showHintToast("批量下载进行中，请稍候");
            return;
        }
        batchDlQueue.clear();
        batchDlQueue.addAll(picked);
        batchDlIndex = 0;
        batchDlTotal = picked.size();
        batchDlOk = 0;
        s("批量下载：共 " + batchDlTotal + " 条，逐条解析中…");
        showHintToast("开始批量下载 " + batchDlTotal + " 条（逐条解析）");
        switchTab(0);
        stepBatchProfileDl();
    }

    /** 批量队列推进：解析当前条，完成后由 pendingAutoDl 回调再调本函数 */
    void stepBatchProfileDl(){
        if(batchDlIndex < 0 || batchDlIndex >= batchDlQueue.size()){
            int ok = batchDlOk;
            int tot = batchDlTotal;
            batchDlIndex = -1;
            batchDlQueue.clear();
            batchDlTotal = 0;
            batchDlOk = 0;
            s("批量下载完成：成功入队 " + ok + "/" + tot + " 条");
            showHintToast("批量完成：" + ok + "/" + tot + " 条已入队");
            return;
        }
        ProfileParser.ProfileItem it = batchDlQueue.get(batchDlIndex);
        String u = it.url != null && it.url.length() > 0 ? it.url
                : (it.id != null && it.id.matches("\\\\d{15,20}")
                    ? "https://www.tiktok.com/@/video/" + it.id : "");
        if(u.length() == 0){
            // 无链接直接跳过，进下一条
            Log.i("DYP", "BATCH_DL_SKIP_NO_URL idx=" + batchDlIndex);
            batchDlIndex++;
            stepBatchProfileDl();
            return;
        }
        // 设置 pendingAutoDlTag，解析成功后（行 ~8872）会自动 enqueueDownload(curPlay)
        pendingAutoDlTag = "视频" + (batchDlIndex + 1);
        pendingAutoDlIdx = batchDlIndex;
        s("批量下载 " + (batchDlIndex + 1) + "/" + batchDlTotal + "：解析中…");
        Log.i("DYP", "BATCH_DL_PARSE idx=" + batchDlIndex + "/" + batchDlTotal + " url=" + u);
        // 走正常解析流程（不 stopProfileMode，避免影响主页状态）
        loadForParse(u);
    }

'''
s = s.replace(anchor, helpers + anchor, 1)

# ---------- 3) 在解析完成回调里接上「下一条」 ----------
old_done = '''                // 主页列表「下载」入口：解析成功后自动把高清直链入下载队列
                if(!fail && pendingAutoDlTag.length() > 0 && curPlay != null && curPlay.length() > 0){
                    final String dl = curPlay;
                    final String tag = pendingAutoDlTag;
                    pendingAutoDlTag = "";
                    pendingAutoDlIdx = -1;
                    enqueueDownload(dl, tag, "mp4", "视频");
                    showHintToast("已加入下载队列：" + tag);
                    s("已加入下载队列：" + tag);
                }'''
new_done = '''                // 主页列表「下载」入口：解析成功后自动把高清直链入下载队列
                if(pendingAutoDlTag.length() > 0){
                    if(!fail && curPlay != null && curPlay.length() > 0){
                        final String dl = curPlay;
                        final String tag = pendingAutoDlTag;
                        pendingAutoDlTag = "";
                        pendingAutoDlIdx = -1;
                        enqueueDownload(dl, tag, "mp4", "视频");
                        showHintToast("已加入下载队列：" + tag);
                        s("已加入下载队列：" + tag);
                        batchDlOk++;
                    } else {
                        // 解析失败：清掉 pending 标志，跳到下一条，别让队列卡住
                        Log.i("DYP", "BATCH_DL_PARSE_FAIL idx=" + batchDlIndex
                                + " fail=" + fail + " curPlayLen="
                                + (curPlay == null ? -1 : curPlay.length()));
                        pendingAutoDlTag = "";
                        pendingAutoDlIdx = -1;
                    }
                    // 批量队列推进：继续下一条
                    if(batchDlIndex >= 0){
                        batchDlIndex++;
                        // 稍等一下再解析下一条，避免触发风控
                        h.postDelayed(new Runnable(){ public void run(){ stepBatchProfileDl(); }}, 1500);
                    }
                }'''
if old_done not in s:
    raise SystemExit('MISS pendingAutoDl 完成回调')
s = s.replace(old_done, new_done, 1)

io.open(P, 'w', encoding='utf-8').write(s)
print('done')
print('startBatchProfileDl:', s.count('startBatchProfileDl'))
print('stepBatchProfileDl:', s.count('stepBatchProfileDl'))