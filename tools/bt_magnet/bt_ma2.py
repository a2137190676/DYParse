# -*- coding: utf-8 -*-
# 补 btSelected 字段 + onMeta 里挑第一个视频并启动边下边播轮询
import io

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/MainActivity.java'
d = io.open(P, encoding='utf-8').read()
n = 0

# 1) 字段
old_f = '''    /** 磁力任务在下载列表里的行号 */
    int btRowKey = 0;
    int btRowSeq = 90000;'''
new_f = '''    /** 磁力任务在下载列表里的行号 */
    int btRowKey = 0;
    int btRowSeq = 90000;
    /** 用户在文件选择弹窗里选中的索引；null=未选（全下），{-1}=取消 */
    volatile int[] btSelected = null;
    /** 边下边播轮询线程是否还在跑 */
    volatile boolean btStreamWatch = false;'''
if old_f in d:
    d = d.replace(old_f, new_f, 1); n += 1; print('字段 OK')
else:
    print('MISS 字段')

# 2) runMagnetDownload 开头重置 btSelected
old_r = '''    /** 真正的磁力下载执行 */
    void runMagnetDownload(final String magnet, final String name){
        final File dir = new File(dlDirFor("磁力"), name);'''
new_r = '''    /** 真正的磁力下载执行 */
    void runMagnetDownload(final String magnet, final String name){
        btSelected = null;        // 每次重置，避免上次的选择串到这次
        btStreamWatch = false;
        final File dir = new File(dlDirFor("磁力"), name);'''
if old_r in d:
    d = d.replace(old_r, new_r, 1); n += 1; print('重置 OK')
else:
    print('MISS 重置')

# 3) onMeta 里挑视频 + 启动边下边播轮询
old_m = '''                Log.i("DYP", "BT_META files=" + files.size() + " total=" + totalBytes);
            }'''
new_m = '''                Log.i("DYP", "BT_META files=" + files.size() + " total=" + totalBytes);

                // 挑第一个视频文件，准备边下边播
                int vIdx = -1;
                for (int i = 0; i < files.size(); i++){
                    if (BtDownloader.isVideoFile(files.get(i))) { vIdx = i; break; }
                }
                if (vIdx >= 0){
                    final File vf = new File(dir, files.get(vIdx));
                    Log.i("DYP", "BT_VIDEO_PICK idx=" + vIdx + " " + vf.getAbsolutePath());
                    btStreamWatch = true;
                    // 轮询：文件出现且有数据就拉起播放（边下边播）
                    new Thread(new Runnable(){ public void run(){
                        for (int i = 0; i < 600 && btStreamWatch; i++){   // 最多等 10 分钟
                            try{
                                if (vf.exists() && vf.length() > 1024 * 512){   // 512KB 就够起播
                                    Log.i("DYP", "BT_STREAM_READY size=" + vf.length());
                                    if (btCbRef != null){
                                        try{ btCbRef.onStreamable(vf); }catch(Throwable t){}
                                    }
                                    return;
                                }
                            }catch(Throwable t){}
                            try{ Thread.sleep(1000); }catch(Throwable t){ break; }
                        }
                    }}).start();
                }
            }'''
if old_m in d:
    d = d.replace(old_m, new_m, 1); n += 1; print('边下边播轮询 OK')
else:
    print('MISS 边下边播')

io.open(P, 'w', encoding='utf-8').write(d)
print('changed:', n)