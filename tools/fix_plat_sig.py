# -*- coding: utf-8 -*-
"""修签名冲突：已有 5 参 (String,String,String,String,String) 重载，
   所以带平台的重载改成 6 参：url,tag,ext,cat,mixDir,plat。"""
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


# 改掉冲突的 5 参重载
rep('1-改6参',
    '''    /** 带显式平台名的入队：调用方已经知道平台时用这个，别让 platOf 去猜 URL 域名。
     *  典型场景：主页/合集列表拿到的 CDN 直链（douyinvod 等），
     *  platOf 认不出来会落到「其他」，文件就存错目录。 */
    void enqueueDownload(String url, String tag, String ext, String cat, String platOverride){
        enqueueDownloadPlat(url, tag, ext, cat, "", platOverride);
    }''',
    '''    /** 带显式平台名的入队：调用方已经知道平台时用这个，别让 platOf 去猜 URL 域名。
     *  典型场景：主页/合集列表拿到的 CDN 直链（douyinvod 等），
     *  platOf 认不出来会落到「其他」，文件就存错目录。
     *  注意用 6 参（带 mixDir），避免和已有的 5 参重载冲突。 */
    void enqueueDownload(String url, String tag, String ext, String cat,
                         String mixDir, String platOverride){
        enqueueDownloadPlat(url, tag, ext, cat, mixDir, platOverride);
    }''')

# 批量直链入队处传平台
rep('2-批量传抖音',
    '''            String tag = "视频" + (batchDlIndex + 1);
            Log.i("DYP", "BATCH_DL_DIRECT idx=" + batchDlIndex + "/" + batchDlTotal
                    + " tag=" + tag);
            enqueueDownload(direct, tag, "mp4", "视频");''',
    '''            String tag = "视频" + (batchDlIndex + 1);
            Log.i("DYP", "BATCH_DL_DIRECT idx=" + batchDlIndex + "/" + batchDlTotal
                    + " tag=" + tag + " plat=抖音");
            // 显式传平台：直链是 douyinvod/byteicdn 这类 CDN 域名，
            // platOf 认不出来会存到「其他/视频」，用户就找不到文件。
            enqueueDownload(direct, tag, "mp4", "视频", "", "抖音");''')

io.open(P, 'w', encoding='utf-8').write(s)
print('成功:', ok)
print('失败:', miss)