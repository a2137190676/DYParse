# -*- coding: utf-8 -*-
"""下载页每行加「删除」按钮：
   - 普通任务：从队列/待恢复清单移除，并询问是否删本地文件
   - 磁力任务：停止 BT + 清任务记录，并询问是否删整个磁力目录
"""
import io

MA = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/MainActivity.java'
m = io.open(MA, encoding='utf-8').read()
n = 0

# ---------- 1) 行 -> 本地路径 的映射（新字段） ----------
old = '''    /** 当前磁力/种子任务在「列表」页的条目 id（拿到元数据后按它更新标题） */
    volatile String btListItemId = "";'''
new = '''    /** 当前磁力/种子任务在「列表」页的条目 id（拿到元数据后按它更新标题） */
    volatile String btListItemId = "";

    /** 下载页每行对应的本地目录/文件路径（删除时用） */
    final java.util.Map<Integer, String> dlRowPath =
            java.util.Collections.synchronizedMap(new java.util.HashMap<Integer, String>());
    /** 下载页每行是否是磁力任务 */
    final java.util.Map<Integer, Boolean> dlRowIsBt =
            java.util.Collections.synchronizedMap(new java.util.HashMap<Integer, Boolean>());'''
if old in m:
    m = m.replace(old, new, 1); n += 1; print('1) dlRowPath 字段 OK')
else:
    print('MISS 1')

# ---------- 2) 在「暂停」按钮后插入「删除」按钮 ----------
old = '''                line.addView(btnPause);

                row.addView(line);'''
new = '''                line.addView(btnPause);

                // 每行「删除」：从列表移除，并问用户是否同时删本地文件
                TextView btnDel = new TextView(MainActivity.this);
                btnDel.setText("删除");
                btnDel.setTextSize(11);
                btnDel.setTextColor(curDark ? 0xFFFF8A8A : 0xFFD93B3B);
                btnDel.setPadding(dp(8), dp(3), dp(2), dp(3));
                btnDel.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){
                    confirmDeleteDlRow(key);
                }});
                line.addView(btnDel);

                row.addView(line);'''
if old in m:
    m = m.replace(old, new, 1); n += 1; print('2) 删除按钮 OK')
else:
    print('MISS 2')

# ---------- 3) 确认删除 + 真正删除的实现 ----------
old = '''    /** 下载列表：改行内文件名（拿到真实名字后刷新，比如去重后缀） */'''
new = '''    /** 下载页某行「删除」：先问是否同时删本地文件 */
    void confirmDeleteDlRow(final int key){
        curDark = isDarkMode();
        final boolean isBt = dlRowIsBt.containsKey(key)
                ? Boolean.TRUE.equals(dlRowIsBt.get(key)) : false;
        final String path = dlRowPath.get(key);
        boolean hasPath = path != null && path.length() > 0;
        String nm = "";
        try{
            TextView tv = dlRowName.get(key);
            if(tv != null) nm = tv.getText().toString();
        }catch(Throwable t){}

        String msg = "要从下载列表移除这条任务吗？\\n\\n" + nm;
        if(hasPath){
            msg += "\\n\\n本地位置：\\n" + path;
        }
        dypDlg()
            .setTitle(isBt ? "删除磁力任务" : "删除下载任务")
            .setMessage(msg)
            .setPositiveButton("同时删本地文件", new android.content.DialogInterface.OnClickListener(){
                public void onClick(android.content.DialogInterface d, int w){
                    doDeleteDlRow(key, isBt, path, true);
                }
            })
            .setNeutralButton("只从列表移除", new android.content.DialogInterface.OnClickListener(){
                public void onClick(android.content.DialogInterface d, int w){
                    doDeleteDlRow(key, isBt, path, false);
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }

    /** 真正执行删除（keepFile=true 表示保留磁盘文件） */
    void doDeleteDlRow(int key, boolean isBt, String path, boolean deleteFile){
        try{
            // 1) 从队列 / 待恢复清单里摘掉这条任务
            DlTask t = dlRowResume.get(key);
            if(t != null){
                synchronized(downloadQueue){ downloadQueue.remove(t); }
                synchronized(dlInFlight){ dlInFlight.remove(t); }
                synchronized(dlFailed){ dlFailed.remove(t); }
                synchronized(dlSavedPending){ dlSavedPending.remove(t); }
                saveDlTasks();
            }
            if(isBt){
                // 磁力：停引擎 + 清任务记录
                try{ BtDownloader.shutdown(); }catch(Throwable t2){}
                btRunning = false;
                clearBtTask();
                btSavedMagnet = "";
                // 列表页那条也删掉
                try{
                    final String lid = btListItemId;
                    if(lid != null && lid.length() > 0){
                        synchronized(batchCollected){
                            for(int i = batchCollected.size() - 1; i >= 0; i--){
                                ProfileParser.ProfileItem o = batchCollected.get(i);
                                if(o != null && lid.equals(o.id)) batchCollected.remove(i);
                            }
                        }
                        saveBatchList();
                        renderBatchList();
                    }
                }catch(Throwable t2){ Log.i("DYP", "DL_DEL_LIST_ERR " + t2); }
            }
            // 2) 删磁盘文件
            boolean delOk = false;
            if(deleteFile && path != null && path.length() > 0){
                try{
                    java.io.File f = new java.io.File(path);
                    delOk = deleteRecursive(f);
                    Log.i("DYP", "DL_DEL_FILE " + path + " ok=" + delOk);
                }catch(Throwable t2){ Log.i("DYP", "DL_DEL_FILE_ERR " + t2); }
            }
            // 3) 移除界面那一行
            removeDlRow(key);
            Log.i("DYP", "DL_ROW_DELETED key=" + key + " bt=" + isBt
                    + " fileDel=" + deleteFile + " ok=" + delOk);
            showHintToast(deleteFile
                    ? (delOk ? "任务已删除，本地文件已清理" : "任务已删除（本地文件没删掉）")
                    : "任务已从列表移除（本地文件保留）");
        }catch(Throwable e){ Log.i("DYP", "DL_ROW_DELETE_ERR " + e); }
    }

    /** 递归删除文件/目录 */
    boolean deleteRecursive(java.io.File f){
        try{
            if(f == null || !f.exists()) return false;
            if(f.isDirectory()){
                java.io.File[] cs = f.listFiles();
                if(cs != null){
                    for(java.io.File c : cs) deleteRecursive(c);
                }
            }
            return f.delete();
        }catch(Throwable t){ return false; }
    }

    /** 从下载页移除一行（含记录表清理） */
    void removeDlRow(final int key){
        h.post(new Runnable(){ public void run(){
            try{
                android.view.View row = dlRowView.get(key);
                if(row != null && dlTaskList != null) dlTaskList.removeView(row);
                dlRowName.remove(key);
                dlRowMeta.remove(key);
                dlRowBar.remove(key);
                dlRowView.remove(key);
                dlRowOrder.remove(key);
                dlRowResume.remove(key);
                dlRowPath.remove(key);
                dlRowIsBt.remove(key);
                dlRefreshHead();
            }catch(Throwable t){ Log.i("DYP", "DL_ROW_REMOVE_ERR " + t); }
        }});
    }

    /** 下载列表：改行内文件名（拿到真实名字后刷新，比如去重后缀） */'''
if old in m:
    m = m.replace(old, new, 1); n += 1; print('3) 删除实现 OK')
else:
    print('MISS 3')

io.open(MA, 'w', encoding='utf-8').write(m)
print('changed:', n)