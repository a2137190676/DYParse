# -*- coding: utf-8 -*-
"""
把列表页两个按钮彻底拆成独立功能，不要弹窗里再套选项。

要求：
    「全部下载」 → 直接下载本页全部（一个确认弹窗就够）
    「批量下载」 → 直接打开勾选弹窗（挑第几个）

现状问题：
    btnDlAll（全部下载）点了先弹「选择下载 / 下载全部」两步，
    btnBatchDl（批量下载）又弹一个「选择下载 / 全部下载」，
    两个按钮功能重叠，用户分不清。

改法：
    btnDlAll  → 确认「下载本页 N 条」→ 直接入队
    btnBatchDl → 直接 showProfilePickDialog（勾选当前页）
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


# ========== A) 「全部下载」：直接下载本页全部 ==========
old_a = '''btnDlAll.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){
            // 两步：先选「下载全部」还是「挑第几个」（setMessage + setItems 会挤掉列表，必须分开）
            dypDlg()
                .setTitle("批量下载")
                .setMessage("【只处理当前页】本页共 " + (to - from) + " 条（全部共 " + n + " 条，"
                    + pages + " 页）。\\n"
                    + "换页请先用下方「上一页/下一页」切到目标页，再点这里。\\n\\n"
                    + "· 下载全部：把本页所有条目加入下载队列\\n"
                    + "· 选择下载：勾选本页要下载第几个（可多选）\\n\\n"
                    + "境外平台会走服务器中转，大视频较多时速度受限。")
                .setPositiveButton("选择下载", new android.content.DialogInterface.OnClickListener(){
                    public void onClick(android.content.DialogInterface d, int w){
                        showProfilePickDialog(list, from, to);
                    }
                })
                .setNeutralButton("下载全部", new android.content.DialogInterface.OnClickListener(){
                    public void onClick(android.content.DialogInterface d, int w){
                        int added = 0;
                        for(int i = from; i < to; i++){
                            ProfileParser.ProfileItem it = list.get(i);
                            String u = it.url.length() > 0 ? it.url
                                    : (it.id.length() > 0 && it.id.matches("\\\\d{15,20}") ? "https://www.tiktok.com/@/video/" + it.id : "");
                            if(u.length() == 0) continue;
                            String tag = "视频" + (i + 1);
                            enqueueDownload(u, tag, "mp4", "视频");
                            added++;
                        }
                        s("已加入下载队列：" + added + " 条");
                        showHintToast("已加入 " + added + " 条下载任务");
                        switchTab(0);
}
                })
                .setNegativeButton("取消", null)
                .show();
        }});'''

new_a = '''btnDlAll.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){
            // 「全部下载」= 只负责「本页全部」，不弹选项。
            // 想挑第几个请用旁边的「批量下载」按钮，两个按钮各管一件事。
            dypDlg()
                .setTitle("全部下载")
                .setMessage("【本页 " + (to - from) + " 条】全部加入下载队列？\\n\\n"
                    + "全部共 " + n + " 条（" + pages + " 页），换页请先用「上一页/下一页」切到目标页。\\n"
                    + "加进队列后会逐条解析出真实直链，再按并发数（"
                    + dlConcurrency() + " 个）同时下载。")
                .setPositiveButton("确定下载本页", new android.content.DialogInterface.OnClickListener(){
                    public void onClick(android.content.DialogInterface d, int w){
                        int added = 0;
                        for(int i = from; i < to; i++){
                            ProfileParser.ProfileItem it = list.get(i);
                            String u = it.url.length() > 0 ? it.url
                                    : (it.id.length() > 0 && it.id.matches("\\\\d{15,20}") ? "https://www.tiktok.com/@/video/" + it.id : "");
                            if(u.length() == 0) continue;
                            String tag = "视频" + (i + 1);
                            enqueueDownload(u, tag, "mp4", "视频");
                            added++;
                        }
                        s("已加入下载队列：" + added + " 条");
                        showHintToast("已加入 " + added + " 条下载任务");
                        switchTab(0);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
        }});'''

rep('A-全部下载直接下', old_a, new_a)

# ========== B) 「批量下载」：直接打开勾选弹窗 ==========
old_b = '''    void startBatchDlFromList(){
        java.util.List<ProfileParser.ProfileItem> snapshot;
        synchronized(batchCollected){
            snapshot = new java.util.ArrayList<ProfileParser.ProfileItem>(batchCollected);
        }
        if(snapshot.isEmpty()){
            showHintToast("列表是空的，先解析一些作品");
            return;
        }
        final java.util.List<ProfileParser.ProfileItem> all = snapshot;
        final int total = all.size();
        // 当前页范围：跟列表页「上一页/下一页」的口径一致（PROFILE_PAGE_SIZE 条/页）
        final int pages = Math.max(1, (total + PROFILE_PAGE_SIZE - 1) / PROFILE_PAGE_SIZE);
        int pg = profilePage;
        if(pg < 0) pg = 0;
        if(pg >= pages) pg = pages - 1;
        final int from = pg * PROFILE_PAGE_SIZE;
        final int to = Math.min(total, from + PROFILE_PAGE_SIZE);

        curDark = isDarkMode();
        // 第一步：先问「全部」还是「挑几条」
        // 注意：不能把 setMessage 和 setItems / multiChoice 混在一个弹窗里，
        // 否则列表项会被挤掉（项目里踩过这个坑），必须拆成两步。
        dypDlg()
            .setTitle("批量下载")
            .setMessage("列表里共 " + total + " 条（共 " + pages + " 页，当前第 " + (pg + 1) + " 页）。\\n\\n"
                      + "· 选择下载：勾选当前页里的第几个（可多选）\\n"
                      + "· 全部下载：把全部 " + total + " 条都加进队列\\n\\n"
                      + "两种方式都会先逐条解析出真实直链，再按并发数（"
                      + dlConcurrency() + " 个）同时下载。")
            .setPositiveButton("选择下载", new android.content.DialogInterface.OnClickListener(){
                public void onClick(android.content.DialogInterface d, int w){
                    showProfilePickDialog(all, from, to);
                }})
            .setNeutralButton("全部下载", new android.content.DialogInterface.OnClickListener(){
                public void onClick(android.content.DialogInterface d, int w){
                    dypDlg()
                        .setTitle("确认全部下载")
                        .setMessage("将把全部 " + total + " 条作品加入下载队列。\\n"
                                  + "数量较多时会持续较长时间，可随时在「下载」页暂停或取消。")
                        .setPositiveButton("确定全部下载", new android.content.DialogInterface.OnClickListener(){
                            public void onClick(android.content.DialogInterface dd, int ww){
                                startBatchProfileDl(all);
                            }})
                        .setNegativeButton("返回", null)
                        .show();
                }})
            .setNegativeButton("取消", null)
            .show();
    }'''

new_b = '''    void startBatchDlFromList(){
        // 「批量下载」= 只负责「挑第几个」，直接开勾选弹窗。
        // 「全部下载」是旁边那个独立按钮，两者不再互相嵌套。
        java.util.List<ProfileParser.ProfileItem> snapshot;
        synchronized(batchCollected){
            snapshot = new java.util.ArrayList<ProfileParser.ProfileItem>(batchCollected);
        }
        if(snapshot.isEmpty()){
            showHintToast("列表是空的，先解析一些作品");
            return;
        }
        final java.util.List<ProfileParser.ProfileItem> all = snapshot;
        final int total = all.size();
        // 当前页范围：跟列表页「上一页/下一页」口径一致（PROFILE_PAGE_SIZE 条/页）
        final int pages = Math.max(1, (total + PROFILE_PAGE_SIZE - 1) / PROFILE_PAGE_SIZE);
        int pg = profilePage;
        if(pg < 0) pg = 0;
        if(pg >= pages) pg = pages - 1;
        final int from = pg * PROFILE_PAGE_SIZE;
        final int to = Math.min(total, from + PROFILE_PAGE_SIZE);
        Log.i("DYP", "BATCH_DL_PICK total=" + total + " page=" + (pg + 1) + "/" + pages
                + " range=" + from + "-" + to);
        curDark = isDarkMode();
        showProfilePickDialog(all, from, to);
    }'''

rep('B-批量下载直接勾选', old_b, new_b)

io.open(P, 'w', encoding='utf-8').write(s)
print('成功:', ok)
print('失败:', miss)