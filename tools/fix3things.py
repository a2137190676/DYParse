# -*- coding: utf-8 -*-
"""三个修复：
1) 删除确认弹窗改成「勾选框」，默认不勾（用户要求）
2) dlRowAdd 不再顶起解析页的 progressBox（开屏恢复任务时的假「准备中」）
3) 开屏时强制隐藏 progressBox（兜底）
"""
import io

MA = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/MainActivity.java'
m = io.open(MA, encoding='utf-8').read()
n = 0

# ============================================================
# 1) 删除确认：改成勾选框，默认不勾
# ============================================================
old = '''    /** 下载页某行「删除」：先问是否同时删本地文件 */
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
    }'''
new = '''    /** 下载页某行「删除」：勾选框让用户选是否同时删本地文件（默认不勾） */
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

        // 用显式容器装勾选框（不用双大括号初始化，也不靠 findViewById(android.R.id.custom)）
        android.widget.LinearLayout box = new android.widget.LinearLayout(this);
        box.setOrientation(android.widget.LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), dp(4));

        TextView tvMsg = new TextView(this);
        String msg = "要从下载列表移除这条任务吗？\\n\\n" + nm;
        if(hasPath) msg += "\\n\\n本地位置：\\n" + path;
        tvMsg.setText(msg);
        tvMsg.setTextSize(13);
        tvMsg.setTextColor(curDark ? 0xFFE8EAF0 : 0xFF1A1C22);
        box.addView(tvMsg);

        // 勾选框：☐ 默认不勾；点一下变 ☑
        final boolean[] delFile = new boolean[]{ false };
        final TextView cb = new TextView(this);
        cb.setText("☐ 同时删除本地文件");
        cb.setTextSize(14);
        cb.setTextColor(curDark ? 0xFF8A8F9C : 0xFF6B7280);
        cb.setPadding(0, dp(12), 0, dp(6));
        cb.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){
            delFile[0] = !delFile[0];
            cb.setText((delFile[0] ? "☑" : "☐") + " 同时删除本地文件");
            cb.setTextColor(delFile[0] ? 0xFFFE2C55
                    : (curDark ? 0xFF8A8F9C : 0xFF6B7280));
        }});
        box.addView(cb);

        dypDlg()
            .setTitle(isBt ? "删除磁力任务" : "删除下载任务")
            .setView(box)
            .setPositiveButton("删除", new android.content.DialogInterface.OnClickListener(){
                public void onClick(android.content.DialogInterface d, int w){
                    doDeleteDlRow(key, isBt, path, delFile[0]);
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }'''
if old in m:
    m = m.replace(old, new, 1); n += 1; print('1) 勾选框式删除确认 OK')
else:
    print('MISS 1')

# ============================================================
# 2) dlRowAdd 不再顶起解析页的 progressBox
# ============================================================
old = '''                dlTaskList.setVisibility(View.VISIBLE);
                if(progressBox != null) progressBox.setVisibility(View.VISIBLE);
                dlRefreshHead();'''
new = '''                dlTaskList.setVisibility(View.VISIBLE);
                // 注意：progressBox 属于「解析」页，不能因为下载页加了一行就顶起来。
                // 否则开屏恢复未完成任务时，解析页会莫名冒出「准备中 0%」。
                dlRefreshHead();'''
if old in m:
    m = m.replace(old, new, 1); n += 1; print('2) dlRowAdd 不再顶 progressBox OK')
else:
    print('MISS 2')

# ============================================================
# 3) 开屏兜底：确保解析页进度框是隐藏的
# ============================================================
old = '''        progressBox=(LinearLayout)findViewById(R.id.progressBox);'''
new = '''        progressBox=(LinearLayout)findViewById(R.id.progressBox);
        // 开屏兜底：解析页进度框默认隐藏（只在真正下载/转码时由 progShow 打开）
        if(progressBox != null) progressBox.setVisibility(View.GONE);'''
if old in m:
    m = m.replace(old, new, 1); n += 1; print('3) 开屏隐藏 progressBox OK')
else:
    print('MISS 3')

io.open(MA, 'w', encoding='utf-8').write(m)
print('changed:', n)