# -*- coding: utf-8 -*-
"""
给 enqueueDownloadPlat 补一个带 liveUrl 的 8 参重载。

原因：
  DlTask 的构造链是
      DlTask(u, t, e, c, m, lv, nb, au)
                         ↑ 第 6 位才是 liveUrl
  实况图必须走这个参数（url=静态图，liveUrl=动轨视频，下载后封成一个 .jpg）。

  但 enqueueDownloadPlat 最多只有 7 参，没有 live 通道，
  所以批量下载根本没法下实况图。

改法：把现有 7 参实现改成 8 参，7 参和 6 参都转发到 8 参（live 传空）。
"""
import io

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/MainActivity.java'
s = io.open(P, encoding='utf-8').read()

old = '''    void enqueueDownloadPlat(String url, String tag, String ext, String cat,
                             String mixDir, String platOverride){
        enqueueDownloadPlat(url, tag, ext, cat, mixDir, platOverride, "");
    }

    void enqueueDownloadPlat(String url, String tag, String ext, String cat,
                             String mixDir, String platOverride, String stableId){
        if(BtDownloader.isMagnet(url) || (url != null && url.indexOf(".torrent") > 0)){'''

new = '''    void enqueueDownloadPlat(String url, String tag, String ext, String cat,
                             String mixDir, String platOverride){
        enqueueDownloadPlat(url, tag, ext, cat, mixDir, platOverride, "", "");
    }

    void enqueueDownloadPlat(String url, String tag, String ext, String cat,
                             String mixDir, String platOverride, String stableId){
        enqueueDownloadPlat(url, tag, ext, cat, mixDir, platOverride, stableId, "");
    }

    /** liveUrl 非空 = 实况图：url 是静态图，liveUrl 是动轨视频，
     *  下载后两者封成一个 .jpg，存到「动图」目录（和单独下载一致）。 */
    void enqueueDownloadPlat(String url, String tag, String ext, String cat,
                             String mixDir, String platOverride, String stableId,
                             String liveUrl){
        if(BtDownloader.isMagnet(url) || (url != null && url.indexOf(".torrent") > 0)){'''

if old not in s:
    print('FAIL 找不到 enqueueDownloadPlat 7参实现')
    raise SystemExit(1)

s = s.replace(old, new, 1)

# DlTask 构造要带上 liveUrl
old2 = '''        DlTask t = new DlTask(url, tag, ext, cat, mixDir, "", "", "");'''
new2 = '''        DlTask t = new DlTask(url, tag, ext, cat, mixDir,
                (liveUrl == null ? "" : liveUrl), "", "");'''
if old2 not in s:
    print('FAIL 找不到 DlTask 构造')
    raise SystemExit(1)
s = s.replace(old2, new2, 1)

io.open(P, 'w', encoding='utf-8').write(s)
print('OK enqueueDownloadPlat 8参重载 + liveUrl 已接上')