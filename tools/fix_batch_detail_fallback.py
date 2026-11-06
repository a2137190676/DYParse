# -*- coding: utf-8 -*-
"""
批量下载：detail 失败时立刻降级到整页解析（loadForParse），而不是直接跳过。

【为什么】
  用户实测「单条解析和下载都正常，就是批量下载图片/视频加入不了下载队列」。

  单条解析走的是 loadForParse()：WebView 打开作品页，页面自己算签名，
  拿到完整数据（含 douyinvod 真链）→ 能下。

  批量下载走的是：
      directUrl 空 → fetchDetailForBatch()（自己拼 XHR）
      → 抖音只给壳数据 / uifid 缺失 → 403
      → advanceBatchAfterDetail(false) → 直接跳下一条

  detail 一失败就跳过，从来没试过 loadForParse。
  所以现在的现象就是「批量 0/N，但单条能下」。

【改法】
  fetchDetailForBatch 里所有失败分支，都不再 advanceBatchAfterDetail(false)，
  改成 fallbackToPageParse()：把当前条目交给 loadForParse 走整页解析。
  只有整页解析也失败，才真的跳过这一条。

  同时保留原 15 秒超时兜底（现在 detail 会很快返回，超时基本不会触发）。
"""
import io

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/MainActivity.java'
s = io.open(P, encoding='utf-8').read()
ok, miss = [], []


def rep(name, old, new, cnt=1):
    global s
    if old in s:
        s = s.replace(old, new, cnt)
        ok.append(name)
    else:
        miss.append(name)


# ============================================================
# 1. 加降级方法
# ============================================================
rep('1-降级方法',
    '''    void fetchDetailForBatch(final String awemeId){''',
    '''    /**
     * detail 失败 → 降级到整页解析（loadForParse）。
     *
     * 单条解析就是走这条路的，实测能拿到真视频直链；
     * detail 是我们自己拼 XHR，缺 uifid 就被 403。
     *
     * 每个 id 最多降级一次，避免无限循环。
     */
    void fallbackToPageParse(final int idx, final String why){
        if(idx < 0 || idx >= batchDlQueue.size()) return;
        Integer tried = batchDlPageTried.get(idx);
        if(tried != null && tried >= 1){
            DypLog.i("BATCH_DL_PAGE_GIVEUP idx=" + idx + " 整页解析也试过了");
            batchDlIndex = idx;
            h.post(new Runnable(){ public void run(){ advanceBatchAfterDetail(false); }});
            return;
        }
        batchDlPageTried.put(idx, 1);
        final ProfileParser.ProfileItem item = batchDlQueue.get(idx);
        String u = (item != null && item.url != null) ? item.url : "";
        DypLog.i("BATCH_DL_PAGE_FALLBACK idx=" + idx + " why=" + why + " url=" + u);
        if(u.length() == 0){
            batchDlIndex = idx;
            h.post(new Runnable(){ public void run(){ advanceBatchAfterDetail(false); }});
            return;
        }
        batchDetailWant = false;
        batchDlIndex = idx;
        pendingAutoDlTag = "视频" + (idx + 1);
        pendingAutoDlIdx = idx;
        s("批量下载 " + (idx + 1) + "/" + batchDlTotal + "：解析中…");
        h.post(new Runnable(){ public void run(){ loadForParse(u); }});
    }

    /** 整页解析试过的条目（每个只降级一次） */
    final java.util.Map<Integer, Integer> batchDlPageTried =
            new java.util.concurrent.ConcurrentHashMap<Integer, Integer>();

    void fetchDetailForBatch(final String awemeId){''')

# ============================================================
# 2. 五个失败分支全部改为降级
# ============================================================
OLD_FAIL = '''                    batchDetailWant = false;
                    h.post(new Runnable(){ public void run(){ advanceBatchAfterDetail(false); }});
                    return;'''

NEW_FAIL = '''                    batchDetailWant = false;
                    // 【关键】不再直接跳过，改成走整页解析。
                    // 单条解析就是这条路，实测能拿到真视频直链。
                    final int fidx = batchDlIndex;
                    h.post(new Runnable(){ public void run(){
                        fallbackToPageParse(fidx, "detail失败");
                    }});
                    return;'''

n = s.count(OLD_FAIL)
if n > 0:
    s = s.replace(OLD_FAIL, NEW_FAIL)
    ok.append('2-五处失败降级(%d处)' % n)
else:
    miss.append('2-五处失败降级')

io.open(P, 'w', encoding='utf-8').write(s)
print('成功:', ok)
print('失败:', miss)