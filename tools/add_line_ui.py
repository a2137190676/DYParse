# -*- coding: utf-8 -*-
"""MainActivity 侧：Tracker 线路设置入口

1) 新增 KEY_BT_LINE 常量
2) 新增 btLine() / setBtLine() 读写
3) 新增 showBtLineDialog() 三选一弹窗
4) buildSettingsPage 里在「本地种子文件」下面加一行
5) App 启动时把已存的值同步给 BtDownloader.setTrackMode()
"""
import io

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/MainActivity.java'
s = io.open(P, encoding='utf-8').read()

# ---------- 1) KEY 常量 ----------
anchor = '    static final String KEY_PROXY_ON = "proxy_on";       // 内置代理（服务器中转）开关，默认关\n'
if anchor not in s:
    raise SystemExit('找不到 KEY_PROXY_ON 锚点')
s = s.replace(anchor, anchor + '''    /** BT tracker 线路：0 极简 / 1 全部TCP / 2 全量（见 BtDownloader.setTrackMode） */
    static final String KEY_BT_LINE = "bt_line";
''', 1)

# ---------- 2) 读写 + 弹窗，插到 buildSettingsPage 前 ----------
anchor2 = '    void buildSettingsPage(){\n'
if anchor2 not in s:
    raise SystemExit('找不到 buildSettingsPage 锚点')

helpers = '''    /** 当前 BT tracker 线路（0 极简 / 1 全部TCP / 2 全量） */
    int btLine(){
        try{
            int v = getSharedPreferences(SP, MODE_PRIVATE).getInt(KEY_BT_LINE, 0);
            return (v < 0 || v > 2) ? 0 : v;
        }catch(Throwable t){ return 0; }
    }
    void setBtLine(int v){
        if(v < 0 || v > 2) v = 0;
        try{ getSharedPreferences(SP, MODE_PRIVATE).edit().putInt(KEY_BT_LINE, v).apply(); }
        catch(Throwable t){}
        try{ BtDownloader.setTrackMode(v); }catch(Throwable t){}
    }
    /** 把已保存的线路同步给 BtDownloader（每次启动都要做） */
    void syncBtLine(){
        try{ BtDownloader.setTrackMode(btLine()); }catch(Throwable t){}
    }
    /** 线路名（给设置页副标题用） */
    String btLineName(){
        int v = btLine();
        if(v == 2) return "全量 · HTTP+UDP 全部 " + BtDownloader.ALL_TRACKERS_COUNT + " 条";
        if(v == 1) return "全部 TCP · 只走 HTTP/HTTPS " + BtDownloader.HTTP_TRACKERS_COUNT + " 条";
        return "极简 · 精选 " + BtDownloader.MAGNET_TRACKERS_COUNT + " 条（默认，最快）";
    }

    /**
     * Tracker 线路选择弹窗。
     *
     * 用户反馈：100 多个 tracker 每次只用 40 个不合理，应该让用户自己选。
     * 这里做成三档，用单选列表，选中即生效并落盘。
     */
    void showBtLineDialog(){
        curDark = isDarkMode();
        final int bgDlg = curDark ? 0xFF17191F : 0xFFFFFFFF;
        final int textDlg = curDark ? 0xFFE6E8EE : 0xFF1A1A1F;
        final int subDlg = curDark ? 0xFF9AA0AC : 0xFF6B6F7A;
        final int accent = 0xFFFE2C55;

        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setBackgroundColor(bgDlg);
        wrap.setPadding(dp(18), dp(14), dp(18), dp(8));

        final String[] names = new String[]{
            "极简（推荐）",
            "全部 TCP",
            "全量"
        };
        final String[] descs = new String[]{
            "精选 " + BtDownloader.MAGNET_TRACKERS_COUNT + " 条高质量 tracker。\\n"
                + "magnet 最短、首次拿种子信息最快，日常够用。",
            "只走 HTTP/HTTPS，共 " + BtDownloader.HTTP_TRACKERS_COUNT + " 条。\\n"
                + "手机挂着代理、或运营商掐了 UDP 时用这个。",
            "HTTP + UDP 全部 " + BtDownloader.ALL_TRACKERS_COUNT + " 条。\\n"
                + "最全，但 magnet 很长，可能被个别 tracker 拒绝。\\n"
                + "极简线路找不到 peer 时切到这里再试。"
        };

        final int cur = btLine();
        final int[] sel = new int[]{ cur };
        final TextView[] rows = new TextView[3];

        for(int i = 0; i < 3; i++){
            final int idx = i;
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(14), dp(11), dp(14), dp(11));
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            clp.bottomMargin = dp(8);
            card.setLayoutParams(clp);

            TextView t = new TextView(this);
            t.setTextSize(15);
            t.setTextColor(textDlg);
            t.setSingleLine(false);

            TextView d = new TextView(this);
            d.setText(descs[i]);
            d.setTextSize(12);
            d.setTextColor(subDlg);
            d.setLineSpacing(dp(2), 1f);
            d.setPadding(0, dp(5), 0, 0);

            card.addView(t);
            card.addView(d);

            final String baseTitle = names[i];
            t.setText((i == cur ? "● " : "○ ") + baseTitle + "（" + BtDownloader.countOf(i) + " 条）");
            final TextView titleView = t;
            rows[i] = t;

            card.setBackground(makeRoundRect(
                i == cur ? (curDark ? 0xFF2A1A20 : 0xFFFFEEF2) : (curDark ? 0xFF23262E : 0xFFF5F6F8),
                i == cur ? accent : (curDark ? 0xFF333842 : 0xFFE1E4E9),
                12));

            card.setOnClickListener(new View.OnClickListener(){
                public void onClick(View v){
                    sel[0] = idx;
                    for(int k = 0; k < 3; k++){
                        rows[k].setText((k == idx ? "● " : "○ ") + names[k]
                            + "（" + BtDownloader.countOf(k) + " 条）");
                        rows[k].setTextColor(textDlg);
                        View p = (View) rows[k].getParent();
                        p.setBackground(makeRoundRect(
                            k == idx ? (curDark ? 0xFF2A1A20 : 0xFFFFEEF2) : (curDark ? 0xFF23262E : 0xFFF5F6F8),
                            k == idx ? accent : (curDark ? 0xFF333842 : 0xFFE1E4E9),
                            12));
                    }
                }});

            wrap.addView(card);
        }

        TextView tip = new TextView(this);
        tip.setText("说明：tracker 的效果跟着资源走——某条磁力在极简线路没人，"
                  + "换到全量可能就有了。改完对之后的新任务生效，正在下载的任务会按新线路重新 announce。");
        tip.setTextSize(11);
        tip.setTextColor(subDlg);
        tip.setLineSpacing(dp(2), 1f);
        tip.setPadding(0, dp(6), 0, dp(2));
        wrap.addView(tip);

        android.app.AlertDialog dlg = dypDlg()
            .setTitle("磁力 Tracker 线路")
            .setView(wrap)
            .setPositiveButton("保存", null)
            .setNegativeButton("取消", null)
            .create();
        dlg.setOnShowListener(new android.content.DialogInterface.OnShowListener(){
            public void onShow(android.content.DialogInterface d){
                dlg.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(
                    new View.OnClickListener(){ public void onClick(View vv){
                        setBtLine(sel[0]);
                        Toast.makeText(MainActivity.this,
                            "Tracker 线路已切换为「" + names[sel[0]] + "」", Toast.LENGTH_SHORT).show();
                        buildSettingsPage();
                        dlg.dismiss();
                    }});
            }});
        try{
            android.view.Window win = dlg.getWindow();
            if(win != null) win.setBackgroundDrawable(
                makeRoundRect(bgDlg, curDark ? 0xFF2B2F39 : 0xFFE3E5EA, 16));
        }catch(Throwable t){}
        dlg.show();
    }

'''
s = s.replace(anchor2, helpers + anchor2, 1)

# ---------- 3) 设置页加一行 ----------
row_anchor = '''        addSettingRow("本地种子文件", "选择手机里的 .torrent 种子，直接开始下载",
            new View.OnClickListener(){
                public void onClick(View v){ pickTorrentFile(); }});
'''
if row_anchor not in s:
    raise SystemExit('找不到 本地种子文件 行')
s = s.replace(row_anchor, row_anchor + '''        addSettingRow("磁力 Tracker 线路",
            "当前：" + btLineName(),
            new View.OnClickListener(){
                public void onClick(View v){ showBtLineDialog(); }});
''', 1)

io.open(P, 'w', encoding='utf-8').write(s)
print('MainActivity done')
print('showBtLineDialog:', s.count('showBtLineDialog'))
print('KEY_BT_LINE:', s.count('KEY_BT_LINE'))