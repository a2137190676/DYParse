# -*- coding: utf-8 -*-
"""
接上三件事：
  ① onActivityResult 处理 REQ_DIR_TREE → 转路径 → 存 prefs
  ② 自建选择器补「⬆ 上一级」按钮（兜底用）
  ③ 「下载根目录」文案改成用户能懂的
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
# ① onActivityResult 处理目录选择结果
# ============================================================
rep('1-onActivityResult',
    '''    @Override protected void onActivityResult(int req, int res, android.content.Intent data){
        super.onActivityResult(req, res, data);
        if(req != REQ_TORRENT) return;''',
    '''    @Override protected void onActivityResult(int req, int res, android.content.Intent data){
        super.onActivityResult(req, res, data);

        // ---- 系统目录选择器 ----
        if(req == REQ_DIR_TREE){
            if(res != RESULT_OK || data == null || data.getData() == null){
                DypLog.i("DIR_TREE_CANCEL");
                return;
            }
            android.net.Uri tree = data.getData();
            String path = treeUriToPath(tree);
            DypLog.i("DIR_TREE_PICK uri=" + tree + " path=" + path);
            if(path.length() == 0){
                showHintToast("这个位置读不到真实路径，请换一个文件夹");
                return;
            }
            // 持久化授权（下次进来还能写）
            try{
                getContentResolver().takePersistableUriPermission(tree,
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                        | android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            }catch(Throwable t){}
            String key = pendingDirKey;
            if(key == null || key.length() == 0) return;
            // 选的恰好是默认目录 → 存空值走默认
            String v = path;
            try{
                if(pendingDirDef != null
                        && new java.io.File(pendingDirDef).getAbsolutePath()
                                .equals(new java.io.File(path).getAbsolutePath())){
                    v = "";
                }
            }catch(Throwable t){}
            getSharedPreferences(SP, MODE_PRIVATE).edit().putString(key, v).apply();
            // 探测可写性，不可写就提醒
            boolean writable = ScopedStore.canWriteDir(new java.io.File(path));
            DypLog.i("DIR_TREE_SAVE key=" + key + " -> "
                    + (v.length() == 0 ? "(默认)" : v) + " writable=" + writable);
            buildSettingsPage();
            showHintToast(v.length() == 0 ? "已恢复默认目录"
                    : ("已选择：" + path + (writable ? "" : "（这个位置可能不可写）")));
            pendingDirKey = null;
            pendingDirDef = null;
            return;
        }

        if(req != REQ_TORRENT) return;''')

# ============================================================
# ② 自建选择器补「⬆ 上一级」
# ============================================================
rep('2-上一级按钮',
    '''        TextView pathTv = new TextView(this);
        pathTv.setText(curPath);
        pathTv.setTextSize(11);
        pathTv.setTextColor(curDark ? 0xFF8A8F9C : 0xFF6B6F7A);
        pathTv.setPadding(0, 0, 0, dp(6));
        box.addView(pathTv);''',
    '''        // 「⬆ 上一级」——之前没有这个，用户进去就出不来了（截图反馈）
        TextView upTv = new TextView(this);
        upTv.setText("⬆  上一级");
        upTv.setTextSize(13);
        upTv.setTextColor(curDark ? 0xFF7FB0FF : 0xFF2F6FE0);
        upTv.setPadding(0, 0, 0, dp(8));
        upTv.setOnClickListener(new View.OnClickListener(){
            public void onClick(View v){
                java.io.File p = dir.getParentFile();
                if(p == null || !p.exists() || !p.isDirectory()){
                    showHintToast("已经是最顶层了");
                    return;
                }
                try{ if(dirPickerRef[0] != null) dirPickerRef[0].dismiss(); }catch(Throwable t){}
                showDirPickerAt(key, defAbs, title, p.getAbsolutePath());
            }
        });
        box.addView(upTv);

        TextView pathTv = new TextView(this);
        pathTv.setText(curPath);
        pathTv.setTextSize(11);
        pathTv.setTextColor(curDark ? 0xFF8A8F9C : 0xFF6B6F7A);
        pathTv.setPadding(0, 0, 0, dp(6));
        box.addView(pathTv);''')

# ============================================================
# ③ 文案改成人话
# ============================================================
rep('3-根目录文案',
    '''        addSettingRow("下载根目录", "点这里从手机里选目录，不用手打路径",
            new View.OnClickListener(){
            public void onClick(View v){
                String def = new java.io.File(
                        android.os.Environment.getExternalStorageDirectory(),
                        "Download/DYParse").getAbsolutePath();
                showDirPicker(KEY_DL_PATH, def, "选择下载根目录");
            }});''',
    '''        addSettingRow("下载保存位置",
            "所有平台的文件都放进这个文件夹，点这里选",
            new View.OnClickListener(){
            public void onClick(View v){
                String def = new java.io.File(
                        android.os.Environment.getExternalStorageDirectory(),
                        "Download/DYParse").getAbsolutePath();
                // 首选系统选择器（能返回上一级、能看全盘）
                pickDirWithSystem(KEY_DL_PATH, def, "选择下载保存位置");
            }});''')

# 平台目录也优先走系统选择器
rep('4-平台目录系统选择',
    '''            .setPositiveButton("选择目录", new android.content.DialogInterface.OnClickListener(){
                public void onClick(android.content.DialogInterface dd, int w){
                    showDirPicker(key, def, "选择「" + plat + "」目录");
                }
            })''',
    '''            .setPositiveButton("选择目录", new android.content.DialogInterface.OnClickListener(){
                public void onClick(android.content.DialogInterface dd, int w){
                    pickDirWithSystem(key, def, "选择「" + plat + "」的保存位置");
                }
            })''')

io.open(P, 'w', encoding='utf-8').write(s)
print('成功:', ok)
print('失败:', miss)