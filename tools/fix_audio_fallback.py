# -*- coding: utf-8 -*-
"""
关键改进：不只是「拦音频」，还要「拿不到视频就回头走 WebView 解析」，
并且把选中的地址打印出来，方便判断到底是哪一级出的问题。

现状：
    appendDyProfileItems 三级回退后，部分作品（视频2/视频4）拿到的
    还是 douyinstatic.com 的 audio/mp4 —— 说明这三级的地址全是音频，
    不是选错档，是这个作品在「主页列表接口」里就没有给视频流。

    而 WebView 打开作品页解析（parseSingle 路径）是能拿到真视频的
    （单视频解析一直是好的）。

所以策略应当是：
    · 主页列表能给出**可验证的视频直链** → 直接用（快、无风控）
    · 给不出（只有音频） → 回落到 WebView 逐条解析（慢但正确）

要做到「可验证」，得有一个便宜的判据。抖音的直链域名本身能区分：
    · douyinvod.com / byteicdn / zjcdn / douyinpic  → 视频/图 CDN
    · douyinstatic.com / obj/tos-cn-ve-2774         → 静态资源（music/audio）
音频几乎都走 douyinstatic.com。

改法：
  1. 加 isLikelyAudioUrl(url)：命中 douyinstatic / music / audio 判为音频
  2. appendDyProfileItems 里，选中后如果是疑似音频，
     就把 directUrl 置空 —— 让批量下载自动回落到 WebView 解析。
  3. 打日志把选中地址的一小段打出来，便于定位。
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
# 1) 新增 isLikelyAudioUrl
# ============================================================
rep('1-加音频域名判定',
    '''    /**
     * 判断是不是「终态拒绝」—— 抖音彻底不给机会了。''',
    '''    /**
     * 粗判一条直链是不是音频（而不是视频）。
     *
     * 抖音的 CDN 域名分工很明确：
     *   视频流   v3-web.douyinvod.com / v5-dy.byteicdn.com / zjcdn ...
     *   图片     p3-sign.douyinpic.com ...
     *   音频/静态 sf6-cdn-tos.douyinstatic.com/obj/tos-cn-ve-2774 ...
     *
     * 实测：主页列表里部分作品的 play_addr 直接给的是
     *   sf6-cdn-tos.douyinstatic.com/obj/tos-cn-ve-2774/xxx
     * 响应 Content-Type: audio/mp4，
     * 存成 .mp4 后相册能播但只有声音（用户反馈 4 条里 3 条中招）。
     *
     * 这里只做域名级粗判，用于「入队前决定要不要回落到 WebView 解析」。
     * 真正的把关在 InnerDownloader 的 Content-Type 检查（已加 audio 拦截）。
     */
    static boolean isLikelyAudioUrl(String url){
        if(url == null || url.length() == 0) return false;
        String u = url.toLowerCase();
        if(u.indexOf("douyinstatic") >= 0) return true;      // 抖音静态资源（音乐库）
        if(u.indexOf("/music/") >= 0 || u.indexOf("music.mp3") >= 0) return true;
        if(u.indexOf("audio/") >= 0) return true;
        // 明确是视频 CDN 的一律放行，避免误杀
        if(u.indexOf("douyinvod") >= 0 || u.indexOf("byteicdn") >= 0
                || u.indexOf("zjcdn") >= 0 || u.indexOf("dycdn") >= 0) return false;
        return false;
    }

    /**
     * 判断是不是「终态拒绝」—— 抖音彻底不给机会了。''')

# ============================================================
# 2) appendDyProfileItems 里，疑似音频就不填 directUrl
# ============================================================
rep('2-疑似音频置空',
    '''                    if(best.length() > 0 && !best.equals("?")) it.directUrl = best;
                    // 记下视频时长（秒），0 说明可能拿到了非视频轨
                    try{
                        long dur = parseLong(get(vm, "duration"), 0);
                        if(dur > 1000) dur = dur / 1000;   // 有的接口给的是毫秒
                        it.durSec = dur;
                    }catch(Throwable t){}''',
    '''                    // 【关键】拿到的如果疑似音频，就别当成视频直链。
                    //
                    // 实测：部分作品在主页列表接口里，三级回退拿到的
                    // 全是 douyinstatic.com 的 audio/mp4（背景音乐），
                    // 不是视频流。这种条目如果直接入队，
                    // 下下来的 .mp4 里只有音频，相册能播但没画面。
                    //
                    // 置空后，批量下载会发现 directUrl 为空，
                    // 自动回落到 WebView 打开作品页解析 —— 那条路能拿到真视频。
                    if(best.length() > 0 && !best.equals("?")){
                        if(isLikelyAudioUrl(best)){
                            Log.i("DYP", "PROF_ITEM_AUDIO id=" + awemeId
                                    + " 拿到的是音频地址，改为回落解析 url="
                                    + best.substring(0, Math.min(80, best.length())));
                            best = "";
                        }
                    }
                    if(best.length() > 0){
                        it.directUrl = best;
                        Log.i("DYP", "PROF_ITEM_URL id=" + awemeId
                                + " url=" + best.substring(0, Math.min(80, best.length())));
                    }
                    // 记下视频时长（秒），0 说明可能拿到了非视频轨
                    try{
                        long dur = parseLong(get(vm, "duration"), 0);
                        if(dur > 1000) dur = dur / 1000;   // 有的接口给的是毫秒
                        it.durSec = dur;
                    }catch(Throwable t){}''')

io.open(P, 'w', encoding='utf-8').write(s)
print('成功:', ok)
print('失败:', miss)