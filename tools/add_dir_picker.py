# -*- coding: utf-8 -*-
"""
【第 3 项】下载路径改成「选择」而不是「输入」。

【为什么不用系统 SAF（ACTION_OPEN_DOCUMENT_TREE）】
  SAF 返回的是 content:// 树 URI，不是真实文件路径。
  而项目里下载全链路用的都是 File：
      dlDirFor2() -> new File(base, cat) -> ScopedStore.canWriteDir()
      -> InnerDownloader 写 .part -> renameTo
  换成 content:// 要把整个下载层改成 DocumentFile/OutputStream，
  改动面太大、风险高（而且是刚稳定下来的下载链路）。

  本 App 已申请 MANAGE_EXTERNAL_STORAGE（AppInfo 里有 isExternalStorageManager 判断），
  有它就能直接读写 /storage/emulated/0 下任意目录，
  所以自己弹一个「目录浏览器」让用户点选，拿到的就是真实路径，零兼容成本。

【交互】
  弹窗显示：当前位置路径
  列表：子目录（点进去）+ 「就用这个目录」+ 「恢复默认」
  只列目录，不列文件，避免几百个文件刷屏。
  隐藏目录（.开头）和 Android 目录跳过。

【落点】
  选完后往同一个 prefs key 写值（dl_dir_xxx / KEY_DL_PATH），
  和原来的输入框方案完全兼容，已有用户的自定义路径不用迁移。
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
# 1. 新增目录选择器
# ============================================================
anchor = '''    /** 下载目录设置弹窗（独立对话框，直接输入路径） */'''

picker = '''    /** 目录选择器的当前对话框引用（切目录时要 dismiss 旧的） */
    final android.app.AlertDialog[] dirPickerRef = new android.app.AlertDialog[1];

    /** 目录选择器：浏览并选一个真实目录。
     *
     *  为什么自己写而不是用系统 SAF：
     *      SAF 给的是 content:// 树，下载层全是 File，改造成本太高。
     *      本 App 有 MANAGE_EXTERNAL_STORAGE，可以直接读写真实路径，
     *      自己弹个目录浏览器最省事，选完直接是 File 可用的绝对路径。
     *
     *  @param key     写进哪个 prefs key
     *  @param defAbs  默认绝对路径（用户点「恢复默认」时把这个 key 置空）
     *  @param title   标题
     */
    void showDirPicker(final String key, final String defAbs, final String title){
        curDark = isDarkMode();
        // 起点：已存的自定义路径 > 默认路径 > 外部存储根
        String saved = getSharedPreferences(SP, MODE_PRIVATE).getString(key, "");
        String start = (saved != null && saved.length() > 0) ? saved : defAbs;
        java.io.File f0 = new java.io.File(start);
        if(!f0.exists() || !f0.isDirectory()) f0 = new java.io.File(defAbs);
        if(!f0.exists() || !f0.isDirectory()){
            f0 = android.os.Environment.getExternalStorageDirectory();
        }
        showDirPickerAt(key, defAbs, title, f0.getAbsolutePath());
    }

    /** 目录选择器的实际渲染（点进子目录时递归调用） */
    void showDirPickerAt(final String key, final String defAbs,
                         final String title, final String curPath){
        curDark = isDarkMode();
        final java.io.File dir = new java.io.File(curPath);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        box.setPadding(pad, dp(6), pad, dp(4));

        TextView pathTv = new TextView(this);
        pathTv.setText(curPath);
        pathTv.setTextSize(11);
        pathTv.setTextColor(curDark ? 0xFF8A8F9C : 0xFF6B6F7A);
        pathTv.setPadding(0, 0, 0, dp(6));
        box.addView(pathTv);

        java.io.File[] subs = dir.listFiles();
        java.util.List<java.io.File> dirs = new java.util.ArrayList<java.io.File>();
        if(subs != null){
            for(java.io.File f : subs){
                if(f == null || !f.isDirectory()) continue;
                String nm = f.getName();
                if(nm.startsWith(".")) continue;      // 跳过隐藏目录
                if(nm.equals("Android")) continue;    // Android/data 不可写，跳过
                dirs.add(f);
            }
        }
        java.util.Collections.sort(dirs, new java.util.Comparator<java.io.File>(){
            public int compare(java.io.File a, java.io.File b){
                String x = a.getName().toLowerCase();
                String y = b.getName().toLowerCase();
                return x.compareTo(y);
            }
        });

        android.widget.ScrollView sv = new android.widget.ScrollView(this);
        LinearLayout listBox = new LinearLayout(this);
        listBox.setOrientation(LinearLayout.VERTICAL);
        sv.addView(listBox);
        LinearLayout.LayoutParams svLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(240));
        box.addView(sv, svLp);

        if(dirs.isEmpty()){
            TextView empty = new TextView(this);
            empty.setText("（这个目录下没有子目录）");
            empty.setTextSize(12);
            empty.setTextColor(curDark ? 0xFF8A8F9C : 0xFF9AA0AC);
            empty.setPadding(0, dp(8), 0, dp(8));
            listBox.addView(empty);
        }
        for(final java.io.File d : dirs){
            TextView row = new TextView(this);
            row.setText("📁 " + d.getName());
            row.setTextSize(13);
            row.setTextColor(curDark ? 0xFFE8EAF0 : 0xFF1A1C22);
            row.setPadding(dp(6), dp(11), dp(6), dp(11));
            row.setOnClickListener(new View.OnClickListener(){
                public void onClick(View v){
                    try{ if(dirPickerRef[0] != null) dirPickerRef[0].dismiss(); }catch(Throwable t){}
                    showDirPickerAt(key, defAbs, title, d.getAbsolutePath());
                }
            });
            listBox.addView(row);
        }

        final String target = curPath;
        String warn = "";
        try{
            if(!android.os.Environment.isExternalStorageManager()){
                warn = "\\n\\n⚠ 未开启「所有文件访问权限」，选任意目录可能写入失败。";
            }
        }catch(Throwable t){}

        final android.app.AlertDialog dlg = dypDlg()
            .setTitle(title)
            .setView(box)
            .setMessage("当前：\\n" + target + "\\n\\n"
                    + "点文件夹进下一级，点「就用这个目录」确定。" + warn)
            .setPositiveButton("就用这个目录", new android.content.DialogInterface.OnClickListener(){
                public void onClick(android.content.DialogInterface dd, int w){
                    // 选的恰好是默认目录 → 存空值走默认，
                    // 这样以后改「下载根目录」时不会因为写死绝对路径而失效
                    String v = target;
                    try{
                        String defNorm = new java.io.File(defAbs).getAbsolutePath();
                        String curNorm = new java.io.File(target).getAbsolutePath();
                        if(defNorm.equals(curNorm)) v = "";
                    }catch(Throwable t){}
                    getSharedPreferences(SP, MODE_PRIVATE).edit()
                        .putString(key, v).apply();
                    Log.i("DYP", "DIR_PICK key=" + key + " -> "
                            + (v.length() == 0 ? "(默认)" : v));
                    buildSettingsPage();
                    Toast.makeText(MainActivity.this,
                        v.length() == 0 ? "已恢复默认目录" : ("已选择：" + v),
                        Toast.LENGTH_SHORT).show();
                }
            })
            .setNeutralButton("恢复默认", new android.content.DialogInterface.OnClickListener(){
                public void onClick(android.content.DialogInterface dd, int w){
                    getSharedPreferences(SP, MODE_PRIVATE).edit()
                        .putString(key, "").apply();
                    Log.i("DYP", "DIR_PICK key=" + key + " -> (恢复默认)");
                    buildSettingsPage();
                    Toast.makeText(MainActivity.this, "已恢复默认目录", Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("取消", null)
            .create();
        dirPickerRef[0] = dlg;
        dlg.show();
    }

    /** 下载目录设置弹窗（独立对话框，直接输入路径） */'''

if 'void showDirPicker(' not in s:
    s = s.replace(anchor, picker, 1)
    ok.append('1-目录选择器')
else:
    ok.append('1-已存在')

# ============================================================
# 2. 「下载根目录」改用选择器
# ============================================================
rep('2-根目录改选择',
    '''        addSettingRow("下载根目录", "所有平台目录的上级目录，点这里修改", new View.OnClickListener(){
            public void onClick(View v){ showDlDirDialog("根目录", KEY_DL_PATH, "Download/DYParse"); }});''',
    '''        addSettingRow("下载根目录", "点这里从手机里选目录，不用手打路径",
            new View.OnClickListener(){
            public void onClick(View v){
                String def = new java.io.File(
                        android.os.Environment.getExternalStorageDirectory(),
                        "Download/DYParse").getAbsolutePath();
                showDirPicker(KEY_DL_PATH, def, "选择下载根目录");
            }});''')

io.open(P, 'w', encoding='utf-8').write(s)
print('成功:', ok)
print('失败:', miss)