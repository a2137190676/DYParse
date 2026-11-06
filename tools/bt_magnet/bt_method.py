# -*- coding: utf-8 -*-
# 加 startMagnetDownload：磁力链接的完整下载流程（进度 + 文件列表 + 完成提示）
import io

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/MainActivity.java'
d = io.open(P, encoding='utf-8').read()

anchor = '    void loadForParse(final String url){'
method = '''    /**
     * 磁力 / BT 种子下载。
     *
     * 流程：建会话 -> 等元数据（10~60s）-> 展示文件列表 -> 下载 -> 完成提示
     * 全部本机完成，不经过任何服务器。
     */
    void startMagnetDownload(final String magnet){
        if(!ensureStoragePerm()){
            showHintToast("需要存储权限才能保存文件");
            return;
        }
        final String name = BtDownloader.magnetName(magnet);
        Log.i("DYP", "BT_CLICK name=" + name + " uri=" + magnet);

        // 确认弹窗（磁力不能预知内容，先说清楚）
        dypDlg()
            .setTitle("磁力下载")
            .setMessage("链接：" + (magnet.length() > 90 ? magnet.substring(0, 90) + "…" : magnet)
                + "\\n\\n预计名称：" + name
                + "\\n\\n【说明】\\n"
                + "· 磁力链接需要先连 DHT 拿种子信息，可能要 10~60 秒\\n"
                + "· 速度取决于有没有人做种，冷门资源可能下不动\\n"
                + "· 本软件不提供、不索引任何资源，仅作下载工具\\n"
                + "· 请仅用于下载你有权获取的内容\\n\\n"
                + "保存到：" + dlDirFor("磁力"))
            .setPositiveButton("开始下载", new android.content.DialogInterface.OnClickListener(){
                public void onClick(android.content.DialogInterface dd, int w){
                    runMagnetDownload(magnet, name);
                }
            })
            .setNegativeButton("取消", null)
            .create().show();
    }

    /** 真正的磁力下载执行 */
    void runMagnetDownload(final String magnet, final String name){
        final File dir = new File(dlDirFor("磁力"), name);
        if(!dir.exists()) dir.mkdirs();
        progShow();
        progSet(0f, "BT 引擎启动中…");
        s("磁力下载开始：" + name);

        // 下载页：建一行
        btRowKey = btRowSeq++;
        dlRowAdd(btRowKey, name);
        dlRowUpdate(btRowKey, 0, "引擎启动…");

        final int rowKey = btRowKey;

        BtDownloader.start(magnet, dir, new BtDownloader.Cb(){
            @Override public void onState(final String state){
                h.post(new Runnable(){ public void run(){
                    progSet(-1f, state);
                    dlRowUpdate(rowKey, -1, state);
                }});
            }
            @Override public void onMeta(final java.util.List<String> files,
                                          final java.util.List<Long> sizes, final long totalBytes){
                h.post(new Runnable(){ public void run(){
                    StringBuilder sb = new StringBuilder();
                    sb.append("【磁力种子】").append(name).append("\\n");
                    sb.append("【文件数】").append(files.size()).append(" 个\\n");
                    sb.append("【总大小】").append(InnerDownloader.sizeText(totalBytes)).append("\\n");
                    sb.append("【保存位置】").append(dir.getAbsolutePath()).append("\\n");
                    int show = Math.min(files.size(), 30);
                    for(int i = 0; i < show; i++){
                        sb.append("  ").append(files.get(i)).append("  ")
                          .append(InnerDownloader.sizeText(sizes.get(i))).append("\\n");
                    }
                    if(files.size() > show){
                        sb.append("  …共 ").append(files.size()).append(" 个文件\\n");
                    }
                    sb.append("\\n【提示】下载完成后文件在保存位置里");
                    s(sb.toString());
                }});
                Log.i("DYP", "BT_META files=" + files.size() + " total=" + totalBytes);
            }
            @Override public void onProgress(final long done, final long total,
                                             final long speedBps, final int peers){
                final float ratio = total > 0 ? (float)((double) done / total) : 0f;
                final String label = String.format(java.util.Locale.US,
                        "%s  %s / %s  %s/s  peers=%d",
                        ratio >= 1f ? "完成" : "下载中",
                        InnerDownloader.sizeText(done),
                        total > 0 ? InnerDownloader.sizeText(total) : "?",
                        InnerDownloader.sizeText(speedBps), peers);
                h.post(new Runnable(){ public void run(){
                    progSet(ratio, label);
                    dlRowUpdate(rowKey, (int)(ratio * 1000), label);
                }});
            }
            @Override public void onDone(final File saveDir){
                h.post(new Runnable(){ public void run(){
                    progSet(1f, "磁力下载完成");
                    dlRowUpdate(rowKey, 1000, "✓ 完成");
                    s("磁力下载完成：" + saveDir.getAbsolutePath());
                    showHintToast("磁力下载完成，文件在 " + saveDir.getAbsolutePath());
                    try{ savedDirs.add(saveDir.getAbsolutePath()); }catch(Throwable t){}
                    scanMedia(saveDir);
                    h.postDelayed(new Runnable(){ public void run(){ progHide(); }}, 1500);
                }});
            }
            @Override public void onError(final String msg){
                h.post(new Runnable(){ public void run(){
                    progHide();
                    dlRowUpdate(rowKey, -1, "✗ " + msg);
                    s("磁力下载失败：" + msg);
                    showHintToast("磁力下载失败：" + msg);
                }});
                Log.i("DYP", "BT_FAIL " + msg);
            }
        });
    }

''' + anchor

if anchor in d:
    d = d.replace(anchor, method, 1)
    io.open(P, 'w', encoding='utf-8').write(d)
    print('startMagnetDownload OK')
else:
    print('MISS anchor')

# 需要两个字段：btRowKey / btRowSeq
d2 = io.open(P, encoding='utf-8').read()
if 'int btRowSeq' not in d2:
    old_f = '''    /** 并发下载任务列表容器（每个任务一行） */
    LinearLayout dlTaskList;'''
    new_f = '''    /** 并发下载任务列表容器（每个任务一行） */
    LinearLayout dlTaskList;
    /** 磁力任务在下载列表里的行号 */
    int btRowKey = 0;
    int btRowSeq = 90000;'''
    if old_f in d2:
        d2 = d2.replace(old_f, new_f, 1)
        io.open(P, 'w', encoding='utf-8').write(d2)
        print('字段 OK')
    else:
        print('MISS 字段')
else:
    print('字段已存在')