# -*- coding: utf-8 -*-
# 修复磁力进度条抖动：
#   onState 不再调 progSet(-1f)，只更新文字；进度条完全交给 onProgress 管
import io

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/MainActivity.java'
d = io.open(P, encoding='utf-8').read()
n = 0

# 1) onState：不碰进度条，只改文字
old = '''            @Override public void onState(final String state){
                h.post(new Runnable(){ public void run(){
                    progSet(-1f, state);
                    dlRowUpdate(rowKey, -1, state);
                }});
            }'''
new = '''            @Override public void onState(final String state){
                // 只更新文字，绝不碰进度条和进度值
                // 否则和 onProgress 抢着写，进度条会一秒跳一次
                h.post(new Runnable(){ public void run(){
                    if(progressLabel != null) progressLabel.setText(state);
                }});
            }'''
if old in d:
    d = d.replace(old, new, 1); n += 1; print('onState 修复 OK')
else:
    print('MISS onState')

# 2) 拿到元数据前，进度条保持「不确定」态，拿到后切回确定态
old2 = '''        progShow();
        progSet(0f, "BT 引擎启动中…");'''
new2 = '''        progShow();
        // 磁力在拿到元数据之前根本没总量，用不确定态转圈；
        // 拿到元数据后 onMeta 里会切成确定态
        progSet(-1f, "BT 引擎启动中…");'''
if old2 in d:
    d = d.replace(old2, new2, 1); n += 1; print('起始态 OK')
else:
    print('MISS 起始态')

# 3) onMeta 里：拿到总量了，进度条切成确定态并从 0 开始
old3 = '''                Log.i("DYP", "BT_META files=" + files.size() + " total=" + totalBytes);'''
new3 = '''                h.post(new Runnable(){ public void run(){
                    // 拿到总量，进度条切成确定态（不再转圈）
                    progSet(0f, "开始下载…");
                    dlRowUpdate(rowKey, 0, "开始下载…");
                }});
                Log.i("DYP", "BT_META files=" + files.size() + " total=" + totalBytes);'''
if old3 in d:
    d = d.replace(old3, new3, 1); n += 1; print('onMeta 切确定态 OK')
else:
    print('MISS onMeta')

io.open(P, 'w', encoding='utf-8').write(d)
print('changed:', n)