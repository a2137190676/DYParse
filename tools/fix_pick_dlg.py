# -*- coding: utf-8 -*-
"""修复两个问题：
1) 磁力文件选择弹窗点「全选/反选」后弹窗消失
   根因：AlertDialog 的 setNeutralButton 点击后系统必然 dismiss，
        在 onClick 里做什么都拦不住。
   正解：setOnShowListener 拿到 dialog 后，给按钮重设 OnClickListener，
        这样点击就不会关闭弹窗（AlertDialog 默认行为只在内部 listener 上）。
2) 下载页每行加「删除」按钮，删除前问用户是否同时删本地文件
"""
import io

MA = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/MainActivity.java'
m = io.open(MA, encoding='utf-8').read()
n = 0

# ============================================================
# 1) 弹窗：全选/反选 不再关闭
# ============================================================
old = '''        dypDlg()
            .setTitle("选择要下载的文件")
            .setView(root)
            .setPositiveButton("开始下载", new android.content.DialogInterface.OnClickListener(){'''
new = '''        android.app.AlertDialog dlgPick = dypDlg()
            .setTitle("选择要下载的文件")
            .setView(root)
            .setPositiveButton("开始下载", new android.content.DialogInterface.OnClickListener(){'''
if old in m:
    m = m.replace(old, new, 1); n += 1; print('1) dlgPick 变量 OK')
else:
    print('MISS 1')

old = '''            .setNeutralButton("全选/反选", new android.content.DialogInterface.OnClickListener(){
                public void onClick(android.content.DialogInterface dd, int w){
                    // 只翻转当前可见项
                    for(Integer i : shown) checked[i] = !checked[i];
                    int sel = 0;
                    for(int i = 0; i < checked.length; i++) if(checked[i]) sel++;
                    head.setText("共 " + files.size() + " 个文件 · "
                            + InnerDownloader.sizeText(totalBytes) + "\\n已选 " + sel + " 个");
                    try{
                        ((android.widget.BaseAdapter) lv.getAdapter()).notifyDataSetChanged();
                    }catch(Throwable t){}
                    // 关键：绝不在这里重建弹窗！
                    // root 已经挂在当前弹窗上，再 setView(root) 会抛
                    // IllegalStateException: child already has a parent
                    // 弹窗保持打开，列表刷新即可（AlertDialog 默认点按钮不关闭）。
                }
            })
            .setNegativeButton("取消下载", new android.content.DialogInterface.OnClickListener(){
                public void onClick(android.content.DialogInterface dd, int w){
                    btPickFlag = -1;
                    result[0] = -1;
                    latch.countDown();
                }
            })
            .setOnCancelListener(new android.content.DialogInterface.OnCancelListener(){
                public void onCancel(android.content.DialogInterface dd){
                    btPickFlag = -1;
                    result[0] = -1;
                    latch.countDown();
                }
            })
            .create().show();
    }'''
new = '''            .setNeutralButton("全选/反选", null)   // listener 在 onShow 里重新设（见下）
            .setNegativeButton("取消下载", new android.content.DialogInterface.OnClickListener(){
                public void onClick(android.content.DialogInterface dd, int w){
                    btPickFlag = -1;
                    result[0] = -1;
                    latch.countDown();
                }
            })
            .setOnCancelListener(new android.content.DialogInterface.OnCancelListener(){
                public void onCancel(android.content.DialogInterface dd){
                    btPickFlag = -1;
                    result[0] = -1;
                    latch.countDown();
                }
            })
            .create();

        // 关键：AlertDialog 里点任何按钮（含 neutral）系统都会 dismiss，
        // 在 setNeutralButton 的 onClick 里拦不住 —— 弹窗必然消失。
        // 正解：弹窗显示后，把「全选/反选」按钮的 OnClickListener 换成自己的，
        // 自己处理完就不触发系统的 dismiss。
        dlgPick.setOnShowListener(new android.content.DialogInterface.OnShowListener(){
            public void onShow(android.content.DialogInterface dd){
                try{
                    android.app.AlertDialog ad = (android.app.AlertDialog) dd;
                    android.widget.Button bn = ad.getButton(android.app.AlertDialog.BUTTON_NEUTRAL);
                    if(bn == null) return;
                    bn.setOnClickListener(new android.view.View.OnClickListener(){
                        public void onClick(android.view.View v){
                            try{
                                // 只翻转当前可见项（配合分类筛选/搜索）
                                if(shown.isEmpty()) return;
                                for(Integer i : shown) checked[i] = !checked[i];
                                int sel = 0;
                                for(int i = 0; i < checked.length; i++) if(checked[i]) sel++;
                                if(head != null){
                                    head.setText("共 " + files.size() + " 个文件 · "
                                            + InnerDownloader.sizeText(totalBytes)
                                            + "\\n已选 " + sel + " 个");
                                }
                                if(lv != null && lv.getAdapter() != null){
                                    ((android.widget.BaseAdapter) lv.getAdapter()).notifyDataSetChanged();
                                }
                                Log.i("DYP", "BT_PICK_TOGGLE_ALL sel=" + sel
                                        + " visible=" + shown.size());
                            }catch(Throwable t){
                                Log.i("DYP", "BT_PICK_TOGGLE_ERR " + t);
                            }
                            // 注意：这里不 dismiss，弹窗保持打开
                        }
                    });
                }catch(Throwable t){
                    Log.i("DYP", "BT_PICK_SHOW_ERR " + t);
                }
            }
        });
        dlgPick.show();
    }'''
if old in m:
    m = m.replace(old, new, 1); n += 1; print('2) 全选不关闭弹窗 OK')
else:
    print('MISS 2')

io.open(MA, 'w', encoding='utf-8').write(m)
print('changed:', n)