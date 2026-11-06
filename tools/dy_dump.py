# -*- coding: utf-8 -*-
# 一次抓取：把签名请求拿到的 body 完整落盘，方便离线看 aweme_list 到底几条
import io

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/MainActivity.java'
d = io.open(P, encoding='utf-8').read()

old = '''            int added;
            boolean hasMore;
            long nextCursor;
            try{
                Object res = new JP(body).parse();'''

new = '''            // 诊断：把原始 body 落盘一份，便于核对 aweme_list 条数
            try{
                if(page <= 2){
                    java.io.File df = new java.io.File(
                            android.os.Environment.getExternalStoragePublicDirectory(
                                    android.os.Environment.DIRECTORY_DOWNLOADS),
                            "DYP_dyprof_p" + page + ".json");
                    java.io.FileOutputStream fo = new java.io.FileOutputStream(df);
                    try{ fo.write(body.getBytes("UTF-8")); }finally{ try{ fo.close(); }catch(Throwable t){} }
                    Log.i("DYP", "DYPROF_DUMP " + df.getAbsolutePath() + " len=" + body.length());
                }
            }catch(Throwable t){ Log.i("DYP", "DYPROF_DUMP_ERR " + t); }

            int added;
            boolean hasMore;
            long nextCursor;
            try{
                Object res = new JP(body).parse();'''

if old in d:
    d = d.replace(old, new, 1)
    io.open(P, 'w', encoding='utf-8').write(d)
    print('落盘诊断 OK')
else:
    print('MISS')