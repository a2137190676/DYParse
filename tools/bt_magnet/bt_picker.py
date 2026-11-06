# -*- coding: utf-8 -*-
# 把 onSelectFiles 的 setMultiChoiceItems 换成自建弹窗：
#   顶部：搜索框 + 分类快捷按钮（视频/音频/图片/文档/全部）
#   中间：ListView（每行 = 图标 + 文件名 + 大小）
#   底部：已选统计 + 全选/反选/取消/开始
import io

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/MainActivity.java'
d = io.open(P, encoding='utf-8').read()

start = d.find('            /** 让用户选择要下载哪些文件（在主线程弹窗，BT 线程等结果） */')
end = d.find('        };\n        btCbRef = _cb;')
if start < 0 or end < 0:
    print('MISS 范围', start, end)
    raise SystemExit

old_block = d[start:end]

new_block = '''            /** 让用户选择要下载哪些文件（主线程弹窗，BT 线程等结果） */
            @Override public int[] onSelectFiles(final java.util.List<String> files,
                                                  final java.util.List<Long> sizes,
                                                  final long totalBytes){
                final java.util.concurrent.CountDownLatch latch =
                        new java.util.concurrent.CountDownLatch(1);
                h.post(new Runnable(){ public void run(){
                    try{
                        final int n = files.size();
                        final boolean[] checked = new boolean[n];
                        for (int i = 0; i < n; i++) checked[i] = true;

                        // ---- 分类统计 ----
                        final int[] kindCount = new int[6];
                        final long[] kindSize = new long[6];
                        for (int i = 0; i < n; i++){
                            int k = BtDownloader.fileKind(files.get(i));
                            kindCount[k]++;
                            kindSize[k] += sizes.get(i);
                        }

                        // ---- 弹窗根容器 ----
                        LinearLayout root = new LinearLayout(MainActivity.this);
                        root.setOrientation(LinearLayout.VERTICAL);
                        int pad = dp(14);
                        root.setPadding(pad, dp(10), pad, 0);

                        // 标题摘要
                        TextView head = new TextView(MainActivity.this);
                        head.setText("共 " + n + " 个文件 · " + InnerDownloader.sizeText(totalBytes));
                        head.setTextSize(12);
                        head.setTextColor(curDark ? 0xFF9AA1AE : 0xFF6B7280);
                        root.addView(head);

                        // 分类快捷行
                        HorizontalScrollView hsv = new HorizontalScrollView(MainActivity.this);
                        hsv.setHorizontalScrollBarEnabled(false);
                        LinearLayout chipRow = new LinearLayout(MainActivity.this);
                        chipRow.setOrientation(LinearLayout.HORIZONTAL);
                        int cpad = dp(6);
                        chipRow.setPadding(0, dp(8), 0, dp(6));
                        hsv.addView(chipRow);
                        root.addView(hsv);

                        // 搜索框
                        final EditText etSearch = new EditText(MainActivity.this);
                        etSearch.setHint("搜索文件名…");
                        etSearch.setSingleLine(true);
                        etSearch.setTextSize(13);
                        etSearch.setTextColor(curDark ? 0xFFE6E8EC : 0xFF23262E);
                        etSearch.setHintTextColor(curDark ? 0xFF7A8194 : 0xFF9AA0AC);
                        root.addView(etSearch);

                        // 列表
                        final ListView lv = new ListView(MainActivity.this);
                        LinearLayout.LayoutParams lvp = new LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
                        lvp.topMargin = dp(6);
                        lv.setLayoutParams(lvp);
                        root.addView(lv);

                        // ---- ListView 数据：按当前过滤条件重建 ----
                        final java.util.List<Integer> shown = new java.util.ArrayList<Integer>();
                        final String[] filterKind = new String[]{ "-1" };   // -1=全部, 否则 kind 字符串
                        final String[] filterTxt = new String[]{ "" };

                        final android.widget.BaseAdapter adapter =
                                new android.widget.BaseAdapter(){
                            public int getCount(){ return shown.size(); }
                            public Object getItem(int p){ return shown.get(p); }
                            public long getItemId(int p){ return p; }

                            public View getView(int p, View convert, android.view.ViewGroup parent){
                                LinearLayout row = new LinearLayout(MainActivity.this);
                                row.setOrientation(LinearLayout.HORIZONTAL);
                                row.setGravity(android.view.Gravity.CENTER_VERTICAL);
                                row.setPadding(dp(4), dp(9), dp(4), dp(9));

                                int realIdx = shown.get(p);
                                String path = files.get(realIdx);
                                int k = BtDownloader.fileKind(path);

                                // 勾选状态
                                TextView ck = new TextView(MainActivity.this);
                                ck.setText(checked[realIdx] ? "☑" : "☐");
                                ck.setTextSize(17);
                                ck.setTextColor(checked[realIdx] ? 0xFF4C8DFF
                                        : (curDark ? 0xFF6B7280 : 0xFFB0B6C0));
                                ck.setWidth(dp(30));
                                row.addView(ck);

                                // 图标
                                TextView ic = new TextView(MainActivity.this);
                                ic.setText(BtDownloader.kindIcon(k));
                                ic.setTextSize(15);
                                ic.setWidth(dp(26));
                                row.addView(ic);

                                // 文件名 + 大小（竖排）
                                LinearLayout mid = new LinearLayout(MainActivity.this);
                                mid.setOrientation(LinearLayout.VERTICAL);
                                LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(0,
                                        LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                                mid.setLayoutParams(mp);

                                String nm = path;
                                if (nm != null) {
                                    int sl = nm.lastIndexOf('/');
                                    if (sl >= 0 && sl < nm.length() - 1) nm = nm.substring(sl + 1);
                                }
                                TextView t1 = new TextView(MainActivity.this);
                                t1.setText(nm);
                                t1.setTextSize(12);
                                t1.setSingleLine(true);
                                t1.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
                                t1.setTextColor(curDark ? 0xFFE6E8EC : 0xFF23262E);
                                mid.addView(t1);

                                TextView t2 = new TextView(MainActivity.this);
                                t2.setText(BtDownloader.kindName(k) + " · "
                                        + InnerDownloader.sizeText(sizes.get(realIdx)));
                                t2.setTextSize(10);
                                t2.setTextColor(curDark ? 0xFF7A8194 : 0xFF9AA0AC);
                                mid.addView(t2);

                                row.addView(mid);
                                return row;
                            }
                        };
                        lv.setAdapter(adapter);

                        // 刷新可见列表
                        final Runnable refresh = new Runnable(){ public void run(){
                            shown.clear();
                            String kw = filterTxt[0] == null ? "" : filterTxt[0].trim().toLowerCase();
                            String fk = filterKind[0];
                            for (int i = 0; i < files.size(); i++){
                                String pth = files.get(i);
                                if (!"-1".equals(fk)){
                                    if (String.valueOf(BtDownloader.fileKind(pth)).equals(fk)) {
                                        // ok
                                    } else continue;
                                }
                                if (kw.length() > 0){
                                    String low = pth == null ? "" : pth.toLowerCase();
                                    if (low.indexOf(kw) < 0) continue;
                                }
                                shown.add(i);
                            }
                            try { adapter.notifyDataSetChanged(); } catch(Throwable t){}
                        }};

                        // 点行切换勾选
                        lv.setOnItemClickListener(new AdapterView.OnItemClickListener(){
                            public void onItemClick(AdapterView<?> av, View v, int p, long id){
                                int realIdx = shown.get(p);
                                checked[realIdx] = !checked[realIdx];
                                try { adapter.notifyDataSetChanged(); } catch(Throwable t){}
                            }
                        });

                        // 分类 chip
                        String[] chipNames = { "全部", "视频", "音频", "图片", "文档", "压缩包", "其他" };
                        String[] chipKinds = { "-1", "0", "1", "2", "3", "4", "5" };
                        for (int ci = 0; ci < chipNames.length; ci++){
                            final String ck2 = chipKinds[ci];
                            TextView chip = new TextView(MainActivity.this);
                            String lbl = chipNames[ci];
                            if (!"-1".equals(ck2)){
                                int kk = Integer.parseInt(ck2);
                                if (kindCount[kk] > 0) lbl += " " + kindCount[kk];
                            }
                            chip.setText(lbl);
                            chip.setTextSize(11);
                            chip.setPadding(dp(11), dp(5), dp(11), dp(5));
                            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                                    LinearLayout.LayoutParams.WRAP_CONTENT,
                                    LinearLayout.LayoutParams.WRAP_CONTENT);
                            cp.rightMargin = dp(6);
                            chip.setLayoutParams(cp);
                            final boolean hasType = ("-1".equals(ck2)) || kindCount[Integer.parseInt(ck2)] > 0;
                            chip.setTextColor(hasType
                                    ? (curDark ? 0xFFD6DAE2 : 0xFF3A3F4A)
                                    : (curDark ? 0xFF4A4F5A : 0xFFC5CAD3));
                            chip.setBackground(makeRoundRect(
                                    curDark ? 0xFF23262E : 0xFFF2F4F8, dp(14)));
                            chip.setOnClickListener(new View.OnClickListener(){
                                public void onClick(View v){
                                    filterKind[0] = ck2;
                                    refresh.run();
                                }
                            });
                            chipRow.addView(chip);
                        }

                        // 搜索联动
                        etSearch.addTextChangedListener(new android.text.TextWatcher(){
                            public void beforeTextChanged(CharSequence s, int a, int b, int c){}
                            public void onTextChanged(CharSequence s, int a, int b, int c){
                                filterTxt[0] = s == null ? "" : s.toString();
                                refresh.run();
                            }
                            public void afterTextChanged(android.text.Editable s){}
                        });

                        refresh.run();

                        // ---- 底部按钮 ----
                        final int[] result = new int[]{ -2 };

                        dypDlg()
                            .setTitle("选择要下载的文件")
                            .setView(root)
                            .setPositiveButton("开始下载", new android.content.DialogInterface.OnClickListener(){
                                public void onClick(android.content.DialogInterface dd, int w){
                                    java.util.List<Integer> sel = new java.util.ArrayList<Integer>();
                                    for (int i = 0; i < n; i++) if (checked[i]) sel.add(i);
                                    int[] arr = new int[sel.size()];
                                    for (int i = 0; i < arr.length; i++) arr[i] = sel.get(i);
                                    btSelected = arr;
                                    Log.i("DYP", "BT_SELECT_USER picked=" + arr.length + " of " + n);
                                    latch.countDown();
                                }
                            })
                            .setNeutralButton("全选/反选", new android.content.DialogInterface.OnClickListener(){
                                public void onClick(android.content.DialogInterface dd, int w){
                                    // 只翻转当前可见（已过滤）的项
                                    boolean allOn = true;
                                    for (int i = 0; i < shown.size(); i++){
                                        if (!checked[shown.get(i)]) { allOn = false; break; }
                                    }
                                    for (int i = 0; i < shown.size(); i++){
                                        checked[shown.get(i)] = !allOn;
                                    }
                                    btSelected = null;   // 先不清，等用户点开始
                                    try { adapter.notifyDataSetChanged(); } catch(Throwable t){}
                                    Log.i("DYP", "BT_SELECT_TOGGLE visible=" + shown.size() + " to=" + (!allOn));
                                }
                            })
                            .setNegativeButton("取消下载", new android.content.DialogInterface.OnClickListener(){
                                public void onClick(android.content.DialogInterface dd, int w){
                                    btSelected = new int[]{ -1 };
                                    Log.i("DYP", "BT_SELECT_USER cancel");
                                    latch.countDown();
                                }
                            })
                            .setCancelable(false)
                            .create().show();
                    }catch(Throwable t){
                        Log.i("DYP", "BT_SELECT_DLG_ERR " + t);
                        btSelected = null;
                        latch.countDown();
                    }
                }});
                try{
                    latch.await(300, java.util.concurrent.TimeUnit.SECONDS);
                }catch(Throwable t){}
                int[] r = btSelected;
                if (r == null) return null;
                return r;
            }
'''

d = d[:start] + new_block + d[end:]
io.open(P, 'w', encoding='utf-8').write(d)
print('onSelectFiles 重建完成')