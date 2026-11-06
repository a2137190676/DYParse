# -*- coding: utf-8 -*-
# MainActivity：磁力文件选择 + 种子保存 + 边下边播
import io

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/MainActivity.java'
d = io.open(P, encoding='utf-8').read()

# 1) Cb 匿名类补三个新方法（插在 onError 之前）
old = '''            @Override public void onError(final String msg){
                h.post(new Runnable(){ public void run(){
                    progHide();
                    dlRowUpdate(rowKey, -1, "✗ " + msg);
                    s("磁力下载失败：" + msg);
                    showHintToast("磁力下载失败：" + msg);
                }});
                Log.i("DYP", "BT_FAIL " + msg);
            }
        });'''

new = '''            @Override public void onError(final String msg){
                h.post(new Runnable(){ public void run(){
                    progHide();
                    dlRowUpdate(rowKey, -1, "✗ " + msg);
                    s("磁力下载失败：" + msg);
                    showHintToast("磁力下载失败：" + msg);
                }});
                Log.i("DYP", "BT_FAIL " + msg);
            }

            /** 种子文件已导出：记到日志 + 提示用户 */
            @Override public void onTorrentSaved(final File tp){
                if (tp == null) return;
                Log.i("DYP", "BT_TORRENT_SAVED " + tp.getAbsolutePath());
                h.post(new Runnable(){ public void run(){
                    s("【种子已保存】" + tp.getAbsolutePath());
                }});
            }

            /** 边下边播：第一个视频文件可读了，自动拉起播放器 */
            @Override public void onStreamable(final File vp){
                if (vp == null) return;
                Log.i("DYP", "BT_STREAMABLE " + vp.getAbsolutePath());
                h.post(new Runnable(){ public void run(){
                    try{
                        curPlay = vp.getAbsolutePath();
                        curUrl = vp.getAbsolutePath();
                        curCover = "";
                        imgUrls.clear();
                        img.setVisibility(View.GONE);
                        videoBox.setVisibility(View.VISIBLE);
                        actions.setVisibility(View.VISIBLE);
                        doPlay();
                        showHintToast("边下边播：文件还在下载，播放会边下边读");
                    }catch(Throwable t){
                        Log.i("DYP", "BT_STREAM_ERR " + t);
                    }
                }});
            }

            /** 让用户选择要下载哪些文件（在主线程弹窗，BT 线程等结果） */
            @Override public int[] onSelectFiles(final java.util.List<String> files,
                                                  final java.util.List<Long> sizes,
                                                  final long totalBytes){
                final java.util.concurrent.CountDownLatch latch =
                        new java.util.concurrent.CountDownLatch(1);
                final int[] result = new int[]{ -2 };   // -2 = 还没选
                h.post(new Runnable(){ public void run(){
                    try{
                        final int n = files.size();
                        final boolean[] checked = new boolean[n];
                        for (int i = 0; i < n; i++) checked[i] = true;   // 默认全选
                        final String[] labels = new String[n];
                        for (int i = 0; i < n; i++){
                            String nm = files.get(i);
                            if (nm != null && nm.length() > 46) nm = "…" + nm.substring(nm.length() - 46);
                            labels[i] = InnerDownloader.sizeText(sizes.get(i)) + "  " + nm;
                        }
                        dypDlg()
                            .setTitle("选择要下载的文件（共 " + n + " 个，"
                                    + InnerDownloader.sizeText(totalBytes) + "）")
                            .setMultiChoiceItems(labels, checked,
                                new android.content.DialogInterface.OnMultiChoiceClickListener(){
                                    public void onClick(android.content.DialogInterface dd, int which, boolean isChecked){
                                        checked[which] = isChecked;
                                    }
                                })
                            .setPositiveButton("开始下载", new android.content.DialogInterface.OnClickListener(){
                                public void onClick(android.content.DialogInterface dd, int w){
                                    java.util.List<Integer> sel = new java.util.ArrayList<Integer>();
                                    for (int i = 0; i < n; i++) if (checked[i]) sel.add(i);
                                    int[] arr = new int[sel.size()];
                                    for (int i = 0; i < arr.length; i++) arr[i] = sel.get(i);
                                    result[0] = 0;
                                    btSelected = arr;
                                    Log.i("DYP", "BT_SELECT_USER picked=" + arr.length + " of " + n);
                                    latch.countDown();
                                }
                            })
                            .setNeutralButton("全选", new android.content.DialogInterface.OnClickListener(){
                                public void onClick(android.content.DialogInterface dd, int w){
                                    int[] arr = new int[n];
                                    for (int i = 0; i < n; i++) arr[i] = i;
                                    result[0] = 0;
                                    btSelected = arr;
                                    Log.i("DYP", "BT_SELECT_USER all n=" + n);
                                    latch.countDown();
                                }
                            })
                            .setNegativeButton("取消下载", new android.content.DialogInterface.OnClickListener(){
                                public void onClick(android.content.DialogInterface dd, int w){
                                    result[0] = -1;
                                    btSelected = new int[]{ -1 };
                                    Log.i("DYP", "BT_SELECT_USER cancel");
                                    latch.countDown();
                                }
                            })
                            .setCancelable(false)
                            .create().show();
                    }catch(Throwable t){
                        Log.i("DYP", "BT_SELECT_DLG_ERR " + t);
                        latch.countDown();
                    }
                }});
                try{
                    // 等用户选（最多 5 分钟，超时当全下）
                    latch.await(300, java.util.concurrent.TimeUnit.SECONDS);
                }catch(Throwable t){}
                int[] r = btSelected;
                if (r == null) return null;   // 没选成 -> 全下
                return r;
            }
        });'''

if old in d:
    d = d.replace(old, new, 1); print('Cb 实现 OK')
else:
    print('MISS Cb')

io.open(P, 'w', encoding='utf-8').write(d)
print('done')