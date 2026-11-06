# -*- coding: utf-8 -*-
"""实现 enqueueDownloadPlat：接受显式平台名，覆盖 platOf 的猜测。"""
import io

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/MainActivity.java'
s = io.open(P, encoding='utf-8').read()

anchor = '''    /** 下载任务：cat = 分类目录（视频/图片/音频/合集），mixDir = 合集专属子目录（可为空） */'''

block = '''    /** 入队核心：platOverride 非空时用它当平台，不再让 platOf 去猜 URL 域名。
     *
     *  为什么需要：主页/合集列表拿到的 play_addr 直链是 CDN 域名
     *  （v3-web.douyinvod.com / v5-dy.byteicdn.com / zjcdn ...），
     *  platOf 认不出来会落到「其他」，文件就存到 其他/视频 而不是 抖音/视频。
     *  调用方明明知道平台，直接传进来比事后猜可靠得多。 */
    void enqueueDownloadPlat(String url, String tag, String ext, String cat,
                             String mixDir, String platOverride){
        if(BtDownloader.isMagnet(url) || (url != null && url.indexOf(".torrent") > 0)){
            final String mm = url;
            h.post(new Runnable(){ public void run(){ startMagnetDownload(mm); }});
            return;
        }
        DlTask t = new DlTask(url, tag, ext, cat, mixDir, "", "", "");
        try{ InnerDownloader.clearCancel(t.url); dlDeleted.remove(dlTaskKey(t)); }catch(Throwable te){}
        // 平台：优先用调用方给的，没有再让 platOf 猜
        String p = (platOverride == null) ? "" : platOverride.trim();
        if(p.length() > 0){
            t.plat = p;
            Log.i("DYP", "DL_PLAT_FORCE plat=" + p + " tag=" + tag);
        }else{
            t.plat = platOf(url);
        }
        try{ t.nameBase = taskStableName(url, tag); }catch(Throwable e){}
        downloadQueue.add(t);
        saveDlTasks();
        Log.i("DYP", "DL_QUEUE + " + tag + " cat=" + cat + " plat=" + t.plat
                + " 总计=" + downloadQueue.size());
        if(!dlRunning){
            dlRunning = true;
            runDownloadQueue();
        }
    }

'''

if anchor in s:
    s = s.replace(anchor, block + anchor, 1)
    io.open(P, 'w', encoding='utf-8').write(s)
    print('OK enqueueDownloadPlat')
else:
    print('FAIL')