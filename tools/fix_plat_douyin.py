# -*- coding: utf-8 -*-
"""
修：批量下载的 4 条成功文件散落到「其他/视频」「视频」根目录，
而不是「抖音/视频」。

用户实测截图：
    dl_..._视频2.mp4   在 抖音/视频   ← 唯一放对的
    dy_..._视频1.mp4   在 其他/视频   ← 错
    dy_..._视频2.mp4   在 其他/视频   ← 错
    dy_..._视频3.mp4   在 其他/视频   ← 错
    dy_..._视频4.mp4   在 视频/        ← 更错（连平台层都没有）

根因：
    DlTask 的 plat 由 platOf(url) 从 URL 域名推断。
    但主页列表给出的 play_addr 直链是 CDN 域名：
        v3-web.douyinvod.com / v26-web.douyinvod.com
        p3-sign.douyinpic.com / v5-dy.byteicdn.com / zjcdn...
    这些不在 platOf 的判定表里，全部落到 return "其他"。

    讽刺的是：批量下载这个流程明明知道自己在下抖音，
    却把「平台」这个已知信息丢了，让下游去猜 URL 域名。

改法：
    enqueueDownload 增加一个显式 plat 参数（可空）。
    批量下载直链时直接传 "抖音"，不再让 platOf 猜。
    同时把 douyinvod / byteicdn / zjcdn / douyinpic 加进 platOf，
    这样任何地方拿到的抖音 CDN 直链都能正确归档。
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
# 1) platOf 补上抖音 CDN 域名
# ============================================================
rep('1-platOf补抖音CDN',
    '''        if(u.contains("douyin.com") || u.contains("iesdouyin.com") || u.contains("amemv.com")
                || u.contains("snssdk.com")) return "抖音";''',
    '''        // 抖音：除了网页域名，还要覆盖 CDN。
        // 主页列表的 play_addr 直链都是 CDN 域名，
        // 不补这些会被判成「其他」，文件就落到 其他/视频 里。
        if(u.contains("douyin.com") || u.contains("iesdouyin.com") || u.contains("amemv.com")
                || u.contains("snssdk.com")
                || u.contains("douyinvod") || u.contains("douyinpic")
                || u.contains("byteicdn") || u.contains("byteimg")
                || u.contains("zjcdn") || u.contains("dycdn")
                || u.contains("pstatp.com") || u.contains("ixigua.com/obj")) return "抖音";''')

# ============================================================
# 2) enqueueDownload 增加带 plat 的重载
# ============================================================
rep('2-加带plat重载',
    '''    void enqueueDownload(String url, String tag, String ext, String cat){
        enqueueDownload(url, tag, ext, cat, "");
    }''',
    '''    void enqueueDownload(String url, String tag, String ext, String cat){
        enqueueDownload(url, tag, ext, cat, "");
    }

    /** 带显式平台名的入队：调用方已经知道平台时用这个，别让 platOf 去猜 URL 域名。
     *  典型场景：主页/合集列表拿到的 CDN 直链（douyinvod 等），
     *  platOf 认不出来会落到「其他」，文件就存错目录。 */
    void enqueueDownload(String url, String tag, String ext, String cat, String platOverride){
        enqueueDownloadPlat(url, tag, ext, cat, "", platOverride);
    }''')

io.open(P, 'w', encoding='utf-8').write(s)
print('成功:', ok)
print('失败:', miss)