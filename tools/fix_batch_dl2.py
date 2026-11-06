# -*- coding: utf-8 -*-
"""
主页批量下载：串行解析 + 跳过耗时优化。

根因：批量下载直接 enqueueDownload(it.url)，it.url 是详情页地址，
      下载器当直链下，拿到的是 HTML，相册里全是 00:00。

修法：
  1) 新增 batchDlQueue 队列
  2) startBatchProfileDl()：串行触发 loadForParse
  3) 解析完成回调里自动入队 curPlay（真直链），然后下一条
  4) 跳过 pickOriginalQuality / fetchDetailMobile（批量时不做 4K 优化）

为什么串行：loadForParse 是 WebView 异步的，curPlay/curUrl 是全局的，
            并发会互相覆盖。串行虽然慢，但能保证每条拿到正确的直链。
"""
import io

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/MainActivity.java'
s = io.open(P, encoding='utf-8').read()

# ---------- 1) 新增队列状态 ----------
old_state = '''    String pendingAutoDlTag = "";
    int pendingAutoDlIdx = -1;'''
new_state = '''    String pendingAutoDlTag = "";
    int pendingAutoDlIdx = -1;
    /** 主页批量下载队列（串行解析，避免 curPlay 互相覆盖） */
    java.util.List<ProfileParser.ProfileItem> batchDlQueue = new java.util.ArrayList<ProfileParser.ProfileItem>();
    int batchDlIndex = -1;
    int batchDlTotal = 0;
    int batchDlOk = 0;
'''
if old_state not in s:
    raise SystemExit('MISS pendingAutoDl 状态')
s = s.replace(old_state, new_state, 1)

# ---------- 2) 新增串行批量函数 ----------
anchor = '    void showProfilePickDialog(final java.util.List<ProfileParser.ProfileItem> list,'
if anchor not in s:
    raise SystemExit('MISS showProfilePickDialog')

helpers = '''    /** 主页批量下载入口：串行解析，每条拿到真直链再入队 */
    void startBatchProfileDl(java.util.List<ProfileParser.ProfileItem> picked){
        if(picked == null || picked.isEmpty()){
            showHintToast("没有勾选任何作品");
            return;
        }
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

    /** 批量队列推进 */
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
            Log.i("DYP", "BATCH_DL_SKIP_NO_URL idx=" + batchDlIndex);
            batchDlIndex++;
            stepBatchProfileDl();
            return;
        }
        pendingAutoDlTag = "视频" + (batchDlIndex + 1);
        pendingAutoDlIdx = batchDlIndex;
        s("批量下载 " + (batchDlIndex + 1) + "/" + batchDlTotal + "：解析中…");
        Log.i("DYP", "BATCH_DL_PARSE idx=" + batchDlIndex + "/" + batchDlTotal + " url=" + u);
        loadForParse(u);
    }

'''
s = s.replace(anchor, helpers + anchor, 1)

# ---------- 3) 解析完成回调：入队 + 下一条 ----------
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
                        Log.i("DYP", "BATCH_DL_PARSE_FAIL idx=" + batchDlIndex);
                        pendingAutoDlTag = "";
                        pendingAutoDlIdx = -1;
                    }
                    if(batchDlIndex >= 0){
                        batchDlIndex++;
                        h.postDelayed(new Runnable(){ public void run(){ stepBatchProfileDl(); }}, 800);
                    }
                }'''
if old_done not in s:
    raise SystemExit('MISS pendingAutoDl 完成回调')
s = s.replace(old_done, new_done, 1)

# ---------- 4) 把 showProfilePickDialog 的两处「开始下载」改成调 startBatchProfileDl ----------
old_btn1 = '''                    int added = 0;
                    for(int k = 0; k < checked.length; k++){
                        if(!checked[k]) continue;
                        int i = from + k;
                        ProfileParser.ProfileItem it = list.get(i);
                        String u = it.url != null && it.url.length() > 0 ? it.url
                                : (it.id != null && it.id.matches("\\\\d{15,20}")
                                    ? "https://www.tiktok.com/@/video/" + it.id : "");
                        if(u.length() == 0) continue;
                        enqueueDownload(u, "视频" + (i + 1), "mp4", "视频");
                        added++;
                    }
                    if(added == 0){ showHintToast("没有勾选任何作品"); return; }
                    Log.i("DYP", "PROFILE_PICK_DL added=" + added);
                    s("已加入下载队列：" + added + " 条");
                    showHintToast("已加入 " + added + " 条下载任务");
                    switchTab(0);'''
new_btn1 = '''                    java.util.List<ProfileParser.ProfileItem> picked = new java.util.ArrayList<ProfileParser.ProfileItem>();
                    for(int k = 0; k < checked.length; k++){
                        if(!checked[k]) continue;
                        int i = from + k;
                        picked.add(list.get(i));
                    }
                    startBatchProfileDl(picked);'''
if old_btn1 not in s:
    raise SystemExit('MISS 开始下载按钮 1')
s = s.replace(old_btn1, new_btn1, 1)

# 第二处（全选本页弹窗里的）
old_btn2 = '''                                int added = 0;
                                for(int k = 0; k < checked.length; k++){
                                    if(!checked[k]) continue;
                                    int i = from + k;
                                    ProfileParser.ProfileItem it = list.get(i);
                                    String u = it.url != null && it.url.length() > 0 ? it.url
                                            : (it.id != null && it.id.matches("\\\\d{15,20}")
                                                ? "https://www.tiktok.com/@/video/" + it.id : "");
                                    if(u.length() == 0) continue;
                                    enqueueDownload(u, "视频" + (i + 1), "mp4", "视频");
                                    added++;
                                }
                                if(added == 0){ showHintToast("没有勾选任何作品"); return; }
                                s("已加入下载队列：" + added + " 条");
                                showHintToast("已加入 " + added + " 条下载任务");
                                switchTab(0);'''
new_btn2 = '''                                java.util.List<ProfileParser.ProfileItem> picked = new java.util.ArrayList<ProfileParser.ProfileItem>();
                                for(int k = 0; k < checked.length; k++){
                                    if(!checked[k]) continue;
                                    int i = from + k;
                                    picked.add(list.get(i));
                                }
                                startBatchProfileDl(picked);'''
if old_btn2 not in s:
    raise SystemExit('MISS 开始下载按钮 2')
s = s.replace(old_btn2, new_btn2, 1)

io.open(P, 'w', encoding='utf-8').write(s)
print('done')
print('startBatchProfileDl:', s.count('startBatchProfileDl'))
print('stepBatchProfileDl:', s.count('stepBatchProfileDl'))