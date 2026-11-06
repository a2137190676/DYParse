# -*- coding: utf-8 -*-
"""
批量下载卡死的两个真 bug。

【证据链】
  08:15:51 detail 200 成功 → 拿到 ies-music/xxx.mp3
  → BATCH_DETAIL_AUDIO → PAGE_FALLBACK → loadForParse
  → 抖音风控页 → 后面再无任何日志，队列永久卡在 idx=0

【Bug A：音乐作品被当视频】
  dump 出来的 truth：
      aweme_type  = 163
      media_type  = 43
      duration    = 0
      video.play_addr = https://.../ies-music/7210597696699206455.mp3
      video.bit_rate  = null
      video.keys = [audio, big_thumbs, bit_rate_audio, cover, duration,
                    has_watermark, height, is_h265, meta, origin_cover,
                    play_addr, ratio, width]
  → 这是抖音「音乐/BGM 作品」，天生没有视频流。
    硬当视频下必然失败，还白耗一次风控额度。
    正确做法：识别出来直接跳过并计数，不要触发整页解析。

【Bug B：PAGE_FALLBACK 没有超时】
  fallbackToPageParse → loadForParse → 风控页 → 没有回调
  → batchDlIndex 不再推进 → 「批量下载 x/N」永久卡住。
  修法：降级时挂一个 25 秒看门狗，到点还没推进就强推下一条。
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
# A. detail 里识别「音乐作品」，直接跳过，不走整页解析
# ============================================================
rep('A-音乐作品跳过',
    '''                // 音频也拒掉（detail 极少出现，但不能不防）
                if(isLikelyAudioUrl(vurl)){''',
    '''                // 【音乐作品识别】aweme_type=163 / media_type=43 是抖音的
                // 「音乐/BGM 作品」：天生没有视频流，play_addr 直接就是 ies-music 的 mp3。
                // 这种条目当视频下必然失败，而且白白消耗一次风控额度，
                // 所以直接跳过（单独计一类数，最后在结束提示里说清楚）。
                try{
                    long at = parseLong(get(adM, "aweme_type"), 0);
                    long mt = parseLong(get(adM, "media_type"), 0);
                    long dur0 = parseLong(get(vm, "duration"), 0);
                    boolean musicOnly = (at == 163 || mt == 43)
                            || (dur0 == 0 && isLikelyAudioUrl(vurl));
                    if(musicOnly){
                        batchDlMusic++;
                        DypLog.i("BATCH_DETAIL_MUSIC id=" + id + " aweme_type=" + at
                                + " media_type=" + mt + " dur=" + dur0
                                + " 纯音乐作品，跳过");
                        batchDetailWant = false;
                        final int midx = batchDlIndex;
                        h.post(new Runnable(){ public void run(){
                            batchDlIndex = midx;
                            advanceBatchAfterDetail(false);
                        }});
                        return;
                    }
                }catch(Throwable t){}

                // 音频也拒掉（detail 极少出现，但不能不防）
                if(isLikelyAudioUrl(vurl)){''')

# ============================================================
# B. PAGE_FALLBACK 超时看门狗
# ============================================================
rep('B-降级看门狗',
    '''        s("批量下载 " + (idx + 1) + "/" + batchDlTotal + "：解析中…");
        h.post(new Runnable(){ public void run(){ loadForParse(u); }});
    }''',
    '''        s("批量下载 " + (idx + 1) + "/" + batchDlTotal + "：解析中…");
        h.post(new Runnable(){ public void run(){ loadForParse(u); }});
        // 【看门狗】整页解析被抖音风控时，loadForParse 不会回调，
        // batchDlIndex 就永远卡在这一条（实测卡死在 idx=0 十几分钟）。
        // 25 秒还没推进，就强推下一条。
        h.postDelayed(new Runnable(){ public void run(){
            if(batchDlIndex == idx && "视频" .concat(String.valueOf(idx + 1)).equals(pendingAutoDlTag)){
                DypLog.i("BATCH_DL_PAGE_TIMEOUT idx=" + idx + " 整页解析超时，跳过");
                pendingAutoDlTag = "";
                pendingAutoDlIdx = -1;
                batchDlPageTried.put(idx, 9);
                advanceBatchAfterDetail(false);
            }
        }}, 25000L);
    }''')

# ============================================================
# C. 计数器 + 结束提示
# ============================================================
rep('C-计数器',
    '''    /** detail body 落盘诊断：只存前 2 条 */''',
    '''    /** 本次批量下载里「纯音乐作品」的条数（aweme_type=163 / media_type=43） */
    volatile int batchDlMusic = 0;

    /** detail body 落盘诊断：只存前 2 条 */''')

rep('C-结束提示',
    '''                }else if(batchDlNoUrl > 0){
                    s("批量下载失败：这些条目都没有视频直链（"
                            + batchDlNoUrl + " 条），请逐条点「解析」获取。");''',
    '''                }else if(batchDlMusic > 0){
                    s("这批里有 " + batchDlMusic + " 条是抖音音乐作品（没有视频），"
                            + "已跳过。其余 " + (batchDlTotal - batchDlMusic)
                            + " 条需要风控冷却后再试。");
                }else if(batchDlNoUrl > 0){
                    s("批量下载失败：这些条目都没有视频直链（"
                            + batchDlNoUrl + " 条），请逐条点「解析」获取。");''')

rep('C-清零',
    '''            batchDlNoUrl = 0;
            batchDlDetailBlocked = 0;''',
    '''            batchDlNoUrl = 0;
            batchDlDetailBlocked = 0;
            batchDlMusic = 0;''')

io.open(P, 'w', encoding='utf-8').write(s)
print('成功:', ok)
print('失败:', miss)