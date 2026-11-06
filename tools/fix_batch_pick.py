# -*- coding: utf-8 -*-
"""
修：列表页「批量下载」只有「全部下载」，没法挑。

现在：
    点「批量下载」→ 说明弹窗（只有「全部下载」「取消」）
                     → 直接 480 条全进队列

用户要的是：先问「全部」还是「挑几条」，挑的时候按页勾选。

修法（两步弹窗，符合项目已有的「不要 setMessage+setItems 混用」约定）：
    点击 → 说明弹窗：positive=选择下载，neutral=全部下载，negative=取消
         → 选择下载 → showProfilePickDialog(当前页) 勾选
"""
import io

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/MainActivity.java'
s = io.open(P, encoding='utf-8').read()

old = '''    void startBatchDlFromList(){
        java.util.List<ProfileParser.ProfileItem> snapshot;
        synchronized(batchCollected){
            snapshot = new java.util.ArrayList<ProfileParser.ProfileItem>(batchCollected);
        }
        if(snapshot.isEmpty()){
            showHintToast("列表是空的，先解析一些作品");
            return;
        }
        final java.util.List<ProfileParser.ProfileItem> all = snapshot;
        curDark = isDarkMode();
        dypDlg()
            .setTitle("批量下载")
            .setMessage("列表里共 " + all.size() + " 条，全部加入下载队列？\\n\\n"
                      + "会先逐条解析出真实直链，再按并发数（" + dlConcurrency() + " 个）同时下载。")
            .setPositiveButton("全部下载", new android.content.DialogInterface.OnClickListener(){
                public void onClick(android.content.DialogInterface d, int w){
                    startBatchProfileDl(all);
                }})
            .setNegativeButton("取消", null)
            .show();
    }'''

new = '''    void startBatchDlFromList(){
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

if old in s:
    s = s.replace(old, new, 1)
    print('已改：批量下载支持选择')
else:
    print('未匹配')

io.open(P, 'w', encoding='utf-8').write(s)
print('showProfilePickDialog 调用数:', s.count('showProfilePickDialog('))