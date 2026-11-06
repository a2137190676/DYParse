# -*- coding: utf-8 -*-
"""
主页批量下载 0/N 的真相说明 + 让用户知情。

【实测结论】
  抖音主页接口（web 版 aweme/post）现在把真视频流剥离了，只给背景音乐：
      PROF_DBG  id=... bit_rate=null
      PROF_ITEM_AUDIO id=... 拿到的是音频地址
           url=https://sf11-cdn-tos.douyinstatic.com/obj/ies-music/7210597696699206455.mp3
  36 条里只有极个别还有 douyinvod 真链，其余全是 douyinstatic 的 mp3/tos 音频。

  于是 directUrl 被置空 → 批量下载回落 detail → detail 要 uifid → 403
  → BATCH_DL_DONE ok=0。

  这不是代码 bug，是抖音主页接口本身不再下发视频流。

【改法】
  不改解析逻辑（改不动，接口不给就是不给），
  但要让用户知道「为什么 0 条」，而不是干等：
    ① 主页解析完成时，统计有多少条没拿到直链，写进状态栏
    ② 批量下载结束时，如果有条目是「无直链且 detail 失败」，
       提示具体原因，而不是只有一句「成功入队 0/N」
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


# ============================================================
# ① 批量下载结束：说清为什么是 0
# ============================================================
rep('1-结束提示',
    '''            DypLog.i("BATCH_DL_DONE ok=" + okc);
            return;''',
    '''            DypLog.i("BATCH_DL_DONE ok=" + okc + " noUrl=" + batchDlNoUrl
                    + " detail403=" + batchDlDetailBlocked);
            // 一条都没入队时，要把原因说清楚，别让用户干等。
            // 实测：抖音主页接口现在只给 douyinstatic 的背景音乐，
            // 真视频流被剥离，directUrl 全空 → 回落 detail → 403。
            if(okc == 0 && batchDlTotal > 0){
                if(batchDlDetailBlocked > 0 && batchDlNoUrl > 0){
                    s("批量下载失败：列表里这些作品主页接口没给视频直链，"
                            + "走 detail 又被抖音风控拦了（要登录 Cookie）。"
                            + "建议：先在设置里登录抖音，再逐条点「解析」重试。");
                }else if(batchDlNoUrl > 0){
                    s("批量下载失败：这些条目都没有视频直链（"
                            + batchDlNoUrl + " 条），请逐条点「解析」获取。");
                }else{
                    s("批量下载完成：成功入队 0/" + batchDlTotal + " 条");
                }
            }
            batchDlNoUrl = 0;
            batchDlDetailBlocked = 0;
            return;''')

# ============================================================
# ② 计数器
# ============================================================
rep('2-计数器',
    '''    void stepBatchProfileDl(){''',
    '''    /** 本次批量下载里：没有直链的条数 / detail 被风控的条数（用于结束提示） */
    volatile int batchDlNoUrl = 0;
    volatile int batchDlDetailBlocked = 0;

    void stepBatchProfileDl(){''')

io.open(P, 'w', encoding='utf-8').write(s)
print('成功:', ok)
print('失败:', miss)