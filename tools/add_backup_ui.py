# -*- coding: utf-8 -*-
"""设置页加「备份 / 恢复」入口 + 备份/恢复三个对话框。"""
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
# 1) 设置页入口（放在「支持的平台」之前，比较显眼）
# ============================================================
rep('1-设置入口',
    '''        addSettingRow("支持的平台", PLATFORM_LIST.length + " 个平台 · 点看完整清单", new View.OnClickListener(){
            public void onClick(View v){ showPlatforms(); }});''',
    '''        addSettingRow("备份 / 恢复", bakSubLabel(), new View.OnClickListener(){
            public void onClick(View v){ showBackupMenu(); }});
        addSettingRow("支持的平台", PLATFORM_LIST.length + " 个平台 · 点看完整清单", new View.OnClickListener(){
            public void onClick(View v){ showPlatforms(); }});''')

# ============================================================
# 2) 备份/恢复的方法组，插在 buildSettingsPage 前
# ============================================================
rep('2-加重备份方法',
    '''    void buildSettingsPage(){''',
    '''    // ============================================================
    // 备份 / 恢复
    //
    // 为什么必须有这个东西：
    //   Cookie 存在 Android/data/{包名}/files/cookies/，
    //   设置存在 /data/data/{包名}/shared_prefs/dyp_pref.xml，
    //   这俩都是 App 私有数据 —— **清数据 / 卸载重装一定全没**，
    //   这是 Android 系统行为，App 拦不住。
    //   唯一办法是把它们导出到公共目录（Download/DYParse/备份/），
    //   重装后再读回来。
    //
    // 备份是全量的：所有平台 Cookie + 全部 SharedPreferences
    //（下载路径、代理、主题、并发、磁力线路、更新源、访客凭据…）
    // 以后新增设置项也自动带上，不用改代码。
    // ============================================================

    String bakSubLabel(){
        try{
            java.util.List<File> ls = BackupMgr.list();
            if(ls.isEmpty()) return "导出/导入 Cookie 与全部设置（重装后可恢复）";
            File f = ls.get(0);
            java.text.SimpleDateFormat f2 = new java.text.SimpleDateFormat(
                    "MM-dd HH:mm", java.util.Locale.US);
            return "最近备份 " + f2.format(new java.util.Date(f.lastModified()))
                    + " · " + BackupMgr.sizeOf(f) + " · 共 " + ls.size() + " 份";
        }catch(Throwable t){
            return "导出/导入 Cookie 与全部设置";
        }
    }

    void showBackupMenu(){
        dypDlg()
            .setTitle("备份 / 恢复")
            .setMessage("备份会把「所有平台 Cookie + 全部设置」打包到：\\n"
                + BackupMgr.BAK_DIR + "\\n\\n"
                + "这个位置在公共 Download 目录，"
                + "清数据、卸载重装都不会被删。\\n"
                + "重装后进这里点「恢复配置」选回那个 zip 就行。\\n\\n"
                + "当前登录状态：\\n"
                + "· 抖音 " + ckN(CookieStore.PLATFORM_DY) + " 项\\n"
                + "· B站 " + ckN(CookieStore.PLATFORM_BILI) + " 项\\n"
                + "· YouTube " + ckN(CookieStore.PLATFORM_YT) + " 项\\n"
                + "· Instagram " + ckN(CookieStore.PLATFORM_INS) + " 项\\n"
                + "· 小红书 " + ckN(CookieStore.PLATFORM_XHS) + " 项\\n"
                + "· X " + ckN(CookieStore.PLATFORM_X) + " 项")
            .setPositiveButton("立即备份", new android.content.DialogInterface.OnClickListener(){
                public void onClick(android.content.DialogInterface d, int w){ doBackup(); }})
            .setNeutralButton("恢复配置", new android.content.DialogInterface.OnClickListener(){
                public void onClick(android.content.DialogInterface d, int w){ showRestorePicker(); }})
            .setNegativeButton("关闭", null)
            .show();
    }

    String ckN(String plat){
        try{ return String.valueOf(CookieStore.count(this, plat)); }catch(Throwable t){ return "0"; }
    }

    void doBackup(){
        try{
            File f = BackupMgr.backup(this);
            if(f == null){
                showHintToast("备份失败，看一下有没有存储权限");
                s("备份失败");
                return;
            }
            s("已备份");
            buildSettingsPage();   // 刷新副标题
            dypDlg()
                .setTitle("备份完成")
                .setMessage("文件：" + f.getAbsolutePath() + "\\n\\n"
                    + "大小：" + BackupMgr.sizeOf(f) + "\\n\\n"
                    + "里面包含所有平台的 Cookie 和全部设置项。\\n"
                    + "这个文件在公共目录，卸载重装不会丢。\\n\\n"
                    + "⚠ 含登录凭据，别发给别人。")
                .setPositiveButton("知道了", null)
                .setNeutralButton("分享/导出", new android.content.DialogInterface.OnClickListener(){
                    public void onClick(android.content.DialogInterface d, int w){
                        try{
                            Uri u = androidx.core.content.FileProvider.getUriForFile(
                                    MainActivity.this, getPackageName() + ".fileprovider", f);
                            android.content.Intent it = new android.content.Intent(
                                    android.content.Intent.ACTION_SEND);
                            it.setType("application/zip");
                            it.putExtra(android.content.Intent.EXTRA_STREAM, u);
                            it.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            startActivity(android.content.Intent.createChooser(it, "导出备份"));
                        }catch(Throwable t){
                            // 没配 FileProvider 就降级为「打开文件管理器看」
                            showHintToast("备份在：" + BackupMgr.BAK_DIR);
                        }
                    }})
                .show();
        }catch(Throwable t){
            Log.i("DYP", "BAK_UI_ERR " + t);
        }
    }

    void showRestorePicker(){
        try{
            final java.util.List<File> ls = BackupMgr.list();
            if(ls.isEmpty()){
                dypDlg()
                    .setTitle("没有找到备份")
                    .setMessage("在 " + BackupMgr.BAK_DIR + " 里没有 .zip 备份文件。\\n\\n"
                        + "如果你之前备份过，确认一下文件还在不在；\\n"
                        + "如果还没备份过，先点「立即备份」。")
                    .setPositiveButton("知道了", null)
                    .show();
                return;
            }
            String[] items = new String[ls.size()];
            java.text.SimpleDateFormat f2 = new java.text.SimpleDateFormat(
                    "yyyy-MM-dd HH:mm", java.util.Locale.US);
            for(int i = 0; i < ls.size(); i++){
                File f = ls.get(i);
                items[i] = f2.format(new java.util.Date(f.lastModified()))
                        + "  ·  " + BackupMgr.sizeOf(f);
            }
            dypDlg()
                .setTitle("选择要恢复的备份")
                .setItems(items, new android.content.DialogInterface.OnClickListener(){
                    public void onClick(android.content.DialogInterface d, int which){
                        confirmRestore(ls.get(which));
                    }})
                .setNegativeButton("取消", null)
                .show();
        }catch(Throwable t){
            Log.i("DYP", "RST_UI_ERR " + t);
        }
    }

    void confirmRestore(final File f){
        dypDlg()
            .setTitle("确认恢复")
            .setMessage("将从这份备份恢复：\\n" + f.getName() + "\\n\\n"
                + "会覆盖当前的 Cookie 和设置项。\\n"
                + "（下载好的视频/音频不受影响）\\n\\n"
                + "恢复后可能需要重启 App 才能全部生效。")
            .setPositiveButton("恢复", new android.content.DialogInterface.OnClickListener(){
                public void onClick(android.content.DialogInterface d, int w){
                    String r = BackupMgr.restore(MainActivity.this, f);
                    if(r == null){
                        showHintToast("恢复失败，文件可能损坏");
                        s("恢复失败");
                    }else{
                        showHintToast(r);
                        s(r);
                        buildSettingsPage();
                        // Cookie 变了，重灌进 WebView，并清掉风控节流状态
                        try{ restoreDyCookieToWebView(); }catch(Throwable t){}
                        try{
                            curRiskBlocked = false;
                            riskRetryUrl = "";
                            riskAutoRetry = 0;
                            riskLastRetryMs = 0L;
                        }catch(Throwable t){}
                        dypDlg()
                            .setTitle("恢复完成")
                            .setMessage(r + "\\n\\n"
                                + "建议把 App 完全退出再打开一次，"
                                + "让 Cookie 和设置全部生效。")
                            .setPositiveButton("知道了", null)
                            .show();
                    }
                }})
            .setNegativeButton("取消", null)
            .show();
    }

    void buildSettingsPage(){''')

io.open(P, 'w', encoding='utf-8').write(s)
print('成功:', ok)
print('失败:', miss)