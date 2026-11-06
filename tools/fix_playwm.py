# -*- coding: utf-8 -*-
"""
修批量下载 403。

日志证据：
  IDL_HTTP 403 https://aweme.snssdk.com/aweme/v1/playwm/?video_id=v0d00fg10000dbjn3a7og65i7vv0ec9g&ratio=720p&line=0

根因（两层）：
  1) 这个地址是 render() 里的 i_ws 兜底地址（playwm 接口），
     playwm = 「带水印播放」，抖音已基本废弃，现在大量返回 403。
     正确地址应该是 play_addr.url_list[0]（真实 CDN 直链），或 /aweme/v1/play/。
  2) 即使换成 /play/，抖音 CDN 还要求 Referer，否则 403。

修法：
  在 InnerDownloader 发起请求前统一做一次 URL 规范化 + 补 Referer：
    · /aweme/v1/playwm/ → /aweme/v1/play/   （去水印接口）
    · 强制带 Referer: https://www.douyin.com/ （已有逻辑，确认覆盖 playwm 场景）
"""
import io

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/InnerDownloader.java'
s = io.open(P, encoding='utf-8').read()

ok, miss = [], []


def rep(name, old, new):
    global s
    if old in s:
        s = s.replace(old, new, 1)
        ok.append(name)
    else:
        miss.append(name)


# 1) 在 fetchOnce 开头做 URL 规范化
rep('1-URL规范化',
    '''    private static boolean fetchOnce(String url, File target, File part, Cb cb,
                                     Object ttSessionObj, android.content.Context ctx){
        HttpURLConnection c = null;''',
    '''    private static boolean fetchOnce(String url, File target, File part, Cb cb,
                                     Object ttSessionObj, android.content.Context ctx){
        // 抖音 playwm（带水印接口）已大量废弃，直接 403。
        // 换成 play 接口再试；真正的无水印直链应该在解析阶段就取 play_addr.url_list[0]。
        if(url != null && url.indexOf("/aweme/v1/playwm/") > 0){
            String fixed = url.replace("/aweme/v1/playwm/", "/aweme/v1/play/");
            Log.i(TAG, "IDL_FIX_PLAYXM -> " + fixed.substring(0, Math.min(90, fixed.length())));
            url = fixed;
        }
        HttpURLConnection c = null;''')

io.open(P, 'w', encoding='utf-8').write(s)
print('InnerDownloader:', ok, miss)

# 2) MainActivity 的 resolveToDirectUrl：抖音链接先规范化 + 如果拿到的是 playwm 就换掉
P2 = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/MainActivity.java'
s2 = io.open(P2, encoding='utf-8').read()
ok2, miss2 = [], []


def rep2(name, old, new):
    global s2
    if old in s2:
        s2 = s2.replace(old, new, 1)
        ok2.append(name)
    else:
        miss2.append(name)


rep2('2-collect时规范化',
     '''            it.directUrl = curPlay == null ? "" : curPlay;''',
     '''            // 抖音的 playwm 是「带水印」老接口，现已大量 403；
            // 收集进列表时换成 play 接口，下载才能成功。
            String purl = curPlay == null ? "" : curPlay;
            if(purl.indexOf("/aweme/v1/playwm/") > 0){
                purl = purl.replace("/aweme/v1/playwm/", "/aweme/v1/play/");
            }
            it.directUrl = purl;''')

io.open(P2, 'w', encoding='utf-8').write(s2)
print('MainActivity:', ok2, miss2)