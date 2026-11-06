# -*- coding: utf-8 -*-
"""
修：批量下载每条都 BATCH_DL_TIMEOUT（成功率 0）。

日志证据：
    18:56:21.810 BATCH_DL_PARSE idx=0/4 url=.../share/video/7703121486688570664/
    18:56:40.462 onPageFinished#4 .../share/video/7703121486688570664/   ← 页面这时才加载完
    18:56:41.812 BATCH_DL_TIMEOUT idx=0                                ← 20 秒超时已经先到

问题：
    1) 超时 20 秒太短。iesdouyin 分享页 + 抖音风控下，加载经常 20~40 秒。
    2) 超时就直接跳过，不重试 → 一条都下不到。
    3) 超时后 pendingAutoDlTag 被清掉，但 WebView 还在慢慢加载；
       加载完 RESULT_END 时 pendingAutoDlTag 已经是空，直链白解析了。

修法：
    · 超时从 20 秒放宽到 45 秒（分享页正常范围）。
    · 超时先不放弃，给一次重试机会（同一 URL 再 loadForParse 一次）。
    · 重试仍超时才跳过。
"""
import io

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/MainActivity.java'
s = io.open(P, encoding='utf-8').read()

old = '''        loadForParse(u);
        // 超时保护：20 秒没回调就跳过，避免整批卡死
        final int myIdx = batchDlIndex;
        h.postDelayed(new Runnable(){ public void run(){
            if(batchDlIndex == myIdx && pendingAutoDlTag.length() > 0){
                Log.i("DYP", "BATCH_DL_TIMEOUT idx=" + myIdx);
                pendingAutoDlTag = "";
                pendingAutoDlIdx = -1;
                batchDlIndex++;
                if(batchDlIndex >= batchDlTotal) kickBatchDownloads();
                else stepBatchProfileDl();
            }
        }}, 20000);'''

new = '''        loadForParse(u);
        // 超时保护。
        //
        // 原来是 20 秒硬超时，实测不够：iesdouyin 分享页在风控下经常要 20~40 秒才 load 完，
        // 结果每条都是 BATCH_DL_TIMEOUT，一条都下不到（日志：
        //   BATCH_DL_PARSE 18:56:21 → BATCH_DL_TIMEOUT 18:56:41 → onPageFinished 18:56:40）。
        // 现在放宽到 45 秒，并且超时后先重试一次再放弃。
        final int myIdx = batchDlIndex;
        final String myUrl = u;
        h.postDelayed(new Runnable(){ public void run(){
            if(batchDlIndex == myIdx && pendingAutoDlTag.length() > 0){
                int tried = (batchDlRetry.get(myIdx) == null ? 0 : batchDlRetry.get(myIdx));
                if(tried < 1){
                    batchDlRetry.put(myIdx, tried + 1);
                    Log.i("DYP", "BATCH_DL_TIMEOUT_RETRY idx=" + myIdx + " 重试一次 url=" + myUrl);
                    pendingAutoDlTag = "";
                    pendingAutoDlIdx = -1;
                    h.postDelayed(new Runnable(){ public void run(){
                        if(batchDlIndex == myIdx){ stepBatchProfileDl(); }
                    }}, 800);
                    return;
                }
                Log.i("DYP", "BATCH_DL_TIMEOUT idx=" + myIdx + " 重试后仍超时，跳过");
                pendingAutoDlTag = "";
                pendingAutoDlIdx = -1;
                batchDlIndex++;
                if(batchDlIndex >= batchDlTotal) kickBatchDownloads();
                else stepBatchProfileDl();
            }
        }}, 45000);'''

if old in s:
    s = s.replace(old, new, 1)
    print('已放宽超时 + 加重试')
else:
    print('未匹配')

# 加 batchDlRetry 记录表
old2 = '    boolean batchDlKicked = false;'
new2 = '''    boolean batchDlKicked = false;
    /** 批量下载每条已重试次数（key=条目下标），超时后给一次重试机会 */
    final java.util.Map<Integer, Integer> batchDlRetry =
            java.util.Collections.synchronizedMap(new java.util.HashMap<Integer, Integer>());'''
if old2 in s:
    s = s.replace(old2, new2, 1)
    print('已加 batchDlRetry')
else:
    print('batchDlRetry 未匹配')

io.open(P, 'w', encoding='utf-8').write(s)
print('batchDlRetry 引用:', s.count('batchDlRetry'))