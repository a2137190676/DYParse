# -*- coding: utf-8 -*-
"""
修：批量解析收进列表的条目，directUrl 是音频（mp3），
   批量下载照着下，结果 .mp4 里只有声音。

实测日志（用户给的 3 条链接，批量解析→批量下载）：
    BATCH_DL_START total=3 dropped=0
    BATCH_DL_DIRECT idx=0/3 tag=视频1 plat=抖音     ← 走了直链（本应是 detail）
    IDL_AUDIO_NOT_VIDEO ctype=audio/mpeg
        url=https://sf6-cdn-tos.douyinstatic.com/obj/ies-music/7498705409323141897.mp3

根因（collectBatchItem 第 3152~3156 行）：

    String purl = curPlay == null ? "" : curPlay;
    if(purl.indexOf("/aweme/v1/playwm/") > 0){
        purl = purl.replace("/aweme/v1/playwm/", "/aweme/v1/play/");
    }
    it.directUrl = purl;          // ← 直接塞，不判断是不是音频

  批量解析走的是 doParseResolved() → 整页解析（render），
  这条路径**不经过** appendDyProfileItems()（那个是主页列表专用），
  所以之前加的「疑似音频就置空」的过滤对它完全没生效。

  于是 curPlay 拿到的是背景音乐地址（ies-music/*.mp3），
  被原样存进列表 → 批量下载拿去下单 → 下回来是纯音频。

改法：
  collectBatchItem 里加同样的音频判定：
  疑似音频就把 directUrl 置空（但保留 url 和 id）。
  批量下载发现 directUrl 为空，会自动回落到 detail 通道
  （BATCH_DL_DETAIL），那条路拿到的才是真视频流。
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


rep('1-批量收集过滤音频',
    '''            it.directUrl = purl;
            it.isImage = hasImgs;
            if(hasImgs) it.directUrl = imgUrls.get(0);''',
    '''            // 【音频过滤】批量解析走的是整页解析（render），
            // 拿到的 curPlay 有时是背景音乐地址（ies-music/*.mp3、
            // douyinstatic 的 obj/tos-cn-ve-2774 等），不是视频流。
            // 直接存进列表 → 批量下载下回来就是「只有声音没有画面」。
            // 判定为音频就置空，让批量下载自动回落到 detail 通道取真视频。
            if(purl.length() > 0 && isLikelyAudioUrl(purl)){
                Log.i("DYP", "BATCH_COLLECT_AUDIO 丢弃音频地址 url="
                        + purl.substring(0, Math.min(80, purl.length())));
                purl = "";
            }
            it.directUrl = purl;
            it.isImage = hasImgs;
            if(hasImgs) it.directUrl = imgUrls.get(0);''')

# 同时：批量下载里，directUrl 命中音频也要走 detail（双保险）
rep('2-批量下载双保险',
    '''        String direct = (it.directUrl != null) ? it.directUrl : "";
        String why = rejectNonMediaUrl(direct);''',
    '''        String direct = (it.directUrl != null) ? it.directUrl : "";
        // 双保险：即使列表里残留了音频地址，这里也不能拿去下单
        if(direct.length() > 0 && isLikelyAudioUrl(direct)){
            Log.i("DYP", "BATCH_DL_SKIP_AUDIO idx=" + batchDlIndex
                    + " url=" + direct.substring(0, Math.min(80, direct.length())));
            direct = "";
        }
        String why = rejectNonMediaUrl(direct);''')

io.open(P, 'w', encoding='utf-8').write(s)
print('成功:', ok)
print('失败:', miss)