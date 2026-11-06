# -*- coding: utf-8 -*-
"""
修：同一个视频被下载成多份（文件名里带 u+hash 那种）。

用户截图（抖音/视频 目录）：
    dy_u6d8b94ac_视频3.mp4   15.94M
    dy_u33f16b15_视频3.mp4   15.94M
    dy_u707f97a8_视频3.mp4   15.94M    ← 同一个视频三份
    dy_u110bec09_视频1.mp4    4.14M
    dy_ud2ab3db7_视频1_2.mp4  4.14M
    dy_ud2ab3db7_视频1.mp4    4.14M    ← 三份
    dy_u920f92d6_视频2_2.mp4 667.96K
    dy_u920f92d6_视频2.mp4   667.96K
    dy_u43d34184_视频2.mp4   667.96K   ← 三份

根因（taskStableName 第 17991 行）：

    String id = extractMediaId(u);          // CDN 直链抠不出作品 id
    if(id.length() == 0){
        id = (curId != null && curId.length() > 0) ? curId
             : ("u" + Integer.toHexString(u.hashCode()));   // ← 这里
    }

    批量直链场景下 curId 是空的（根本没走 WebView 解析，
    没有那个「当前作品」的概念），于是退化成
    对整条直链取 hashCode 当 id。

    而抖音 CDN 直链每次都带新签名：
        v3-web.douyinvod.com/.../video/tos/cn/tos-cn-ve-15/xxxx/?a=6383&br=...&x-expires=...
    同一个视频，每次请求的 URL 都不一样 → hashCode 不一样
    → 文件名不一样 → 「文件已存在就加 _2」的去重完全失效
    → 下 3 次就是 3 个文件。

改法：
  1. enqueueDownload 增加 6 参重载带 stableId，
     批量下载时直接把作品 id（19 位数字，恒定不变）传进去。
  2. taskStableName 里：从 URL 抠不到 id 时，
     优先用调用方给的 stableId，其次 curId，
     最后才退到 hashCode（真正无解的情况）。
  3. 批量下载调用处传 it.id。
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
# 1) taskStableName 支持显式 stableId
# ============================================================
rep('1-taskStableName加stableId',
    '''    String taskStableName(String url, String tag){
        try{
            String u = url == null ? "" : url;
            String id = extractMediaId(u);
            if(id.length() == 0){
                // 抠不到 id（CDN 直链等）：用入队时的作品 id，它在本作品内是稳定的
                id = (curId != null && curId.length() > 0) ? curId : ("u" + Integer.toHexString(u.hashCode()));
            }
            String t = tag == null ? "" : tag.replace("/", "_").replace("\\\\", "_");
            return mediaPfx(u) + id + "_" + t;
        }catch(Throwable t){ return ""; }
    }''',
    '''    String taskStableName(String url, String tag){
        return taskStableName(url, tag, "");
    }

    /**
     * 文件名主体（决定去重是否有效）。
     *
     * @param stableId 调用方已知的稳定 id（如主页列表的 aweme_id）。
     *                 非空时优先用它 —— 抖音 CDN 直链每次都带新签名，
     *                 拿整条 URL 的 hashCode 当 id 会导致同一个视频
     *                 每次算出不同文件名，去重失效、重复下载。
     */
    String taskStableName(String url, String tag, String stableId){
        try{
            String u = url == null ? "" : url;
            String id = extractMediaId(u);
            if(id.length() == 0){
                // 抠不到 id（CDN 直链等），按可靠性依次回退：
                //   1) 调用方给的 stableId（主页列表的 aweme_id，恒定）
                //   2) curId（当前解析出的作品 id）
                //   3) 整条 URL 的 hashCode —— 最不可靠，
                //      抖音直链带签名，同一视频每次都不一样
                String sid = (stableId == null) ? "" : stableId.trim();
                if(sid.length() > 0){
                    id = sid;
                }else if(curId != null && curId.length() > 0){
                    id = curId;
                }else{
                    id = "u" + Integer.toHexString(u.hashCode());
                }
            }
            String t = tag == null ? "" : tag.replace("/", "_").replace("\\\\", "_");
            return mediaPfx(u) + id + "_" + t;
        }catch(Throwable t){ return ""; }
    }''')

# ============================================================
# 2) enqueueDownloadPlat 支持 stableId
# ============================================================
rep('2-plat实现加stableId',
    '''    void enqueueDownloadPlat(String url, String tag, String ext, String cat,
                             String mixDir, String platOverride){
        if(BtDownloader.isMagnet(url) || (url != null && url.indexOf(".torrent") > 0)){
            final String mm = url;
            h.post(new Runnable(){ public void run(){ startMagnetDownload(mm); }});
            return;
        }
        DlTask t = new DlTask(url, tag, ext, cat, mixDir, "", "", "");''',
    '''    void enqueueDownloadPlat(String url, String tag, String ext, String cat,
                             String mixDir, String platOverride){
        enqueueDownloadPlat(url, tag, ext, cat, mixDir, platOverride, "");
    }

    void enqueueDownloadPlat(String url, String tag, String ext, String cat,
                             String mixDir, String platOverride, String stableId){
        if(BtDownloader.isMagnet(url) || (url != null && url.indexOf(".torrent") > 0)){
            final String mm = url;
            h.post(new Runnable(){ public void run(){ startMagnetDownload(mm); }});
            return;
        }
        DlTask t = new DlTask(url, tag, ext, cat, mixDir, "", "", "");''')

rep('3-plat实现用stableId',
    '''        try{ t.nameBase = taskStableName(url, tag); }catch(Throwable e){}
        downloadQueue.add(t);
        saveDlTasks();
        Log.i("DYP", "DL_QUEUE + " + tag + " cat=" + cat + " plat=" + t.plat
                + " 总计=" + downloadQueue.size());
        if(!dlRunning){
            dlRunning = true;
            runDownloadQueue();
        }
    }''',
    '''        try{ t.nameBase = taskStableName(url, tag, stableId); }catch(Throwable e){}
        downloadQueue.add(t);
        saveDlTasks();
        Log.i("DYP", "DL_QUEUE + " + tag + " cat=" + cat + " plat=" + t.plat
                + " name=" + t.nameBase + " 总计=" + downloadQueue.size());
        if(!dlRunning){
            dlRunning = true;
            runDownloadQueue();
        }
    }''')

# ============================================================
# 4) 加带 stableId 的 enqueueDownload 重载（7 参）
# ============================================================
rep('4-加7参重载',
    '''    /** 带显式平台名的入队：调用方已经知道平台时用这个，别让 platOf 去猜 URL 域名。
     *  典型场景：主页/合集列表拿到的 CDN 直链（douyinvod 等），
     *  platOf 认不出来会落到「其他」，文件就存错目录。
     *  注意用 6 参（带 mixDir），避免和已有的 5 参重载冲突。 */
    void enqueueDownload(String url, String tag, String ext, String cat,
                         String mixDir, String platOverride){
        enqueueDownloadPlat(url, tag, ext, cat, mixDir, platOverride);
    }''',
    '''    /** 带显式平台名的入队：调用方已经知道平台时用这个，别让 platOf 去猜 URL 域名。
     *  典型场景：主页/合集列表拿到的 CDN 直链（douyinvod 等），
     *  platOf 认不出来会落到「其他」，文件就存错目录。
     *  注意用 6 参（带 mixDir），避免和已有的 5 参重载冲突。 */
    void enqueueDownload(String url, String tag, String ext, String cat,
                         String mixDir, String platOverride){
        enqueueDownloadPlat(url, tag, ext, cat, mixDir, platOverride, "");
    }

    /** 带平台 + 稳定 id 的入队（批量直链专用）。
     *  stableId 用作品 id（aweme_id / 视频 id），保证同一作品每次文件名一致，
     *  否则抖音直链带签名的特性会让同一视频被重复下载多份。 */
    void enqueueDownload(String url, String tag, String ext, String cat,
                         String mixDir, String platOverride, String stableId){
        enqueueDownloadPlat(url, tag, ext, cat, mixDir, platOverride, stableId);
    }''')

# ============================================================
# 5) 批量直链调用处传 it.id
# ============================================================
rep('5-批量传stableId',
    '''            // 显式传平台：直链是 douyinvod/byteicdn 这类 CDN 域名，
            // platOf 认不出来会存到「其他/视频」，用户就找不到文件。
            enqueueDownload(direct, tag, "mp4", "视频", "", "抖音");''',
    '''            // 显式传平台：直链是 douyinvod/byteicdn 这类 CDN 域名，
            // platOf 认不出来会存到「其他/视频」，用户就找不到文件。
            //
            // 显式传 stableId = 作品 id：抖音直链每次带新签名，
            // 拿 URL hashCode 当 id 会让同一视频每次算出不同文件名，
            // 去重失效，结果同一个视频下 3 份。
            String sid = (it.id != null) ? it.id : "";
            enqueueDownload(direct, tag, "mp4", "视频", "", "抖音", sid);''')

io.open(P, 'w', encoding='utf-8').write(s)
print('成功:', ok)
print('失败:', miss)