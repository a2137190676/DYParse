# -*- coding: utf-8 -*-
"""
把 detail 返回的原始 body 落一份到文件，用来确认：
  · aweme_detail.video.bit_rate 到底在不在
  · play_addr 指向的是 douyinvod（真视频）还是 douyinstatic（音频）
  · 是否有 download_addr / play_addr_h264 / 其他可用字段

只 dump 一次，避免占空间。
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


rep('1-dump body',
    '''                DypLog.i("BATCH_DETAIL_REQ id=" + awemeId + " " + r);''',
    '''                DypLog.i("BATCH_DETAIL_REQ id=" + awemeId + " " + r);
                // 【一次性诊断】把 detail 原始 body 存下来，
                // 用来确认 bit_rate / play_addr 到底给了什么。
                if(r != null && r.ok && r.body != null && detailDumpLeft > 0){
                    detailDumpLeft--;
                    try{
                        java.io.File df = new java.io.File(
                                android.os.Environment.getExternalStorageDirectory(),
                                "Download/DYParse/detail_dump_" + awemeId + ".json");
                        df.getParentFile().mkdirs();
                        java.io.FileOutputStream fo = new java.io.FileOutputStream(df);
                        fo.write(r.body.getBytes("UTF-8"));
                        fo.close();
                        DypLog.i("BATCH_DETAIL_DUMP path=" + df.getAbsolutePath()
                                + " len=" + r.body.length());
                    }catch(Throwable dt){
                        DypLog.i("BATCH_DETAIL_DUMP_ERR " + dt);
                    }
                }''')

rep('2-dump计数器',
    '''    void fetchDetailForBatch(final String awemeId){''',
    '''    /** detail body 落盘诊断：只存前 2 条 */
    volatile int detailDumpLeft = 2;

    void fetchDetailForBatch(final String awemeId){''')

io.open(P, 'w', encoding='utf-8').write(s)
print('成功:', ok)
print('失败:', miss)