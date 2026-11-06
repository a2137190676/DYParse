# -*- coding: utf-8 -*-
# 加 progBusy()：真正的「不确定态」（转圈），替代 progSet(-1f) 那个假货
import io

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/MainActivity.java'
d = io.open(P, encoding='utf-8').read()

anchor = '''    void progSet(final float ratio, final String label){'''
method = '''    /**
     * 进度条切成「不确定态」（转圈）。
     * 用于磁力拿到元数据之前：那时候根本没有总大小，算不出百分比。
     * 拿到元数据后调 progSet(f) 会切回确定态。
     */
    void progBusy(final String label){
        h.post(new Runnable(){ public void run(){
            try{
                if(progressBar != null){
                    progressBar.setIndeterminate(true);
                }
                if(progressPct != null) progressPct.setText("--");
                if(label != null && progressLabel != null) progressLabel.setText(label);
            }catch(Throwable t){}
        }});
        Log.i("DYP", "PROG -- " + label);
        try{
            DownloadService.updateProgress("DYParse 下载", label, -1);
        }catch(Throwable t){}
    }

''' + anchor

if anchor in d and 'void progBusy' not in d:
    d = d.replace(anchor, method, 1)
    # progSet 里要能切回确定态
    old_set = '''            if(progressBar != null) progressBar.setProgress(p);'''
    new_set = '''            if(progressBar != null){
                // 从「不确定态」切回确定态
                if(progressBar.isIndeterminate()) progressBar.setIndeterminate(false);
                progressBar.setProgress(p);
            }'''
    if old_set in d:
        d = d.replace(old_set, new_set, 1)
        print('progSet 切回确定态 OK')
    io.open(P, 'w', encoding='utf-8').write(d)
    print('progBusy OK')
else:
    print('MISS or exists')

# 把磁力起始的 progSet(-1f) 换成 progBusy
d2 = io.open(P, encoding='utf-8').read()
d2 = d2.replace('''        progSet(-1f, "BT 引擎启动中…");''',
                '''        progBusy("BT 引擎启动中…");''', 1)
io.open(P, 'w', encoding='utf-8').write(d2)
print('替换起始态 OK')