# -*- coding: utf-8 -*-
"""绑定列表页三个新按钮的逻辑。"""
import io

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/MainActivity.java'
s = io.open(P, encoding='utf-8').read()

# 1) 声明字段
old_decl = '''    TextView btnClearList;'''
new_decl = '''    TextView btnClearList;
    TextView btnBatchParse, btnProfileParse, btnBatchDl;'''
if old_decl not in s:
    raise SystemExit('MISS btnClearList 声明')
s = s.replace(old_decl, new_decl, 1)

# 2) 绑定监听（跟在 btnClearList 的 findViewById 后面）
old_bind = '''        btnClearList = (TextView)findViewById(R.id.btnClearList);
        if(btnClearList != null){'''
new_bind = '''        btnClearList = (TextView)findViewById(R.id.btnClearList);
        // ===== 列表页功能入口：批量解析 / 主页解析 / 批量下载 =====
        btnBatchParse = (TextView)findViewById(R.id.btnBatchParse);
        if(btnBatchParse != null){
            btnBatchParse.setOnClickListener(new View.OnClickListener(){
                public void onClick(View v){ showBatchInputDialog(); }});
        }
        btnProfileParse = (TextView)findViewById(R.id.btnProfileParse);
        if(btnProfileParse != null){
            btnProfileParse.setOnClickListener(new View.OnClickListener(){
                public void onClick(View v){ showProfileInputDialog(); }});
        }
        btnBatchDl = (TextView)findViewById(R.id.btnBatchDl);
        if(btnBatchDl != null){
            btnBatchDl.setOnClickListener(new View.OnClickListener(){
                public void onClick(View v){ startBatchDlFromList(); }});
        }
        if(btnClearList != null){'''
if old_bind not in s:
    raise SystemExit('MISS btnClearList 绑定')
s = s.replace(old_bind, new_bind, 1)

# 3) 新增两个辅助方法：主页输入弹窗 + 从列表直接批量下载
#    插到 showProfilePickDialog 前
anchor = '    void showProfilePickDialog(final java.util.List<ProfileParser.ProfileItem> list,'
helpers = '''    /** 主页解析入口：让用户粘主页链接，然后走正常解析 */
    void showProfileInputDialog(){
        curDark = isDarkMode();
        final EditText et = new EditText(this);
        et.setHint("粘贴主页链接，例如\\nhttps://www.douyin.com/user/xxx\\nhttps://v.douyin.com/xxxxx/");
        et.setSingleLine(false);
        et.setMinLines(3);
        et.setTextSize(13);
        et.setTextColor(curDark ? 0xFFE6E8EC : 0xFF23262E);
        et.setHintTextColor(curDark ? 0xFF7A8194 : 0xFF9AA0AC);
        int pad = dp(16);
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setBackgroundColor(curDark ? 0xFF17191F : 0xFFFFFFFF);
        wrap.setPadding(pad, dp(8), pad, dp(4));
        wrap.addView(et, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        dypDlg()
            .setTitle("主页解析")
            .setMessage("粘贴博主主页链接，会翻完所有作品列进列表。\\n"
                      + "支持抖音 / 快手 / B站 / TikTok / YouTube / X / 小红书 / IG。")
            .setView(wrap)
            .setPositiveButton("开始解析", new android.content.DialogInterface.OnClickListener(){
                public void onClick(android.content.DialogInterface d, int w){
                    String u = et.getText() == null ? "" : et.getText().toString().trim();
                    u = extractUrl(u);
                    if(u == null || u.length() == 0){
                        showHintToast("没识别到链接");
                        return;
                    }
                    Log.i("DYP", "PROFILE_PARSE_FROM_LIST " + u);
                    switchTab(0);
                    input.setText(u);
                    parse();
                }})
            .setNegativeButton("取消", null)
            .show();
    }

    /**
     * 列表页「批量下载」：直接对列表里所有条目批量下载。
     *
     * 之前的「全部下载」是逐条问用户，这个是直接全下，且走并发。
     */
    void startBatchDlFromList(){
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
    }

'''
if anchor not in s:
    raise SystemExit('MISS showProfilePickDialog 锚点')
s = s.replace(anchor, helpers + anchor, 1)

io.open(P, 'w', encoding='utf-8').write(s)
print('按钮绑定 + 辅助方法 完成')
print('btnBatchParse:', s.count('btnBatchParse'))
print('showProfileInputDialog:', s.count('showProfileInputDialog'))
print('startBatchDlFromList:', s.count('startBatchDlFromList'))