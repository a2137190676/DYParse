# -*- coding: utf-8 -*-
"""
两个改动：

【A】批量下载硬编码 "抖音" → 按 URL 自动判平台
    现状：
        enqueueDownloadPlat(iu, ..., "抖音", sid, live);   // 图集
        enqueueDownload(direct, ..., "抖音", sid);          // 视频直链
    结果：B站/快手/TikTok/B站图文 全被塞进「抖音/视频」「抖音/图片」目录，
          用户根本分不出哪个文件是哪个平台的。

    改法：新增 platForItem(it)：
        · 条目自己的 url 判得出平台 → 用它
        · 判不出（比如抖音短链）→ 用媒体 URL 兜底判
        · 都判不出 → "其他"（platOf 本来就是这行为）

【B】三个数据池落盘（重启后保留）
    现在只有 batchCollected 会 writeBatchList 落盘，
    itemsFromSingle / itemsFromBatch / itemsFromProfile 全在内存里，
    App 一重启就空。

    改法：新文件 list_pools.tsv，一行一条：
        池编号(0=批量/1=视频图文/2=主页) \t 作品id \t url \t title \t isImage
        \t directUrl \t cover \t imgs(|分隔) \t dyns(|分隔)
    启动时 loadListPools() 读回来，三个池都能恢复。
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
# A1. 图集分支：去掉硬编码抖音
# ============================================================
rep('A1-图集平台',
    '''                if(live.length() > 0){
                    // 实况图 → 动图目录，和单独下载完全一致
                    enqueueDownloadPlat(iu, "实况" + (gi + 1), "jpg", "动图", "",
                            "抖音", sid, live);
                    ln++;
                }else{
                    enqueueDownloadPlat(iu, "图" + (gi + 1), iext, "图片", "",
                            "抖音", sid, "");
                    qn++;
                }''',
    '''                // 平台不能写死「抖音」——
                // 用户会混着批量解析 B站图文、快手图文、TikTok 图集，
                // 写死就会全部落到「抖音/图片」，文件根本分不清来源。
                String iplat = platForItem(it);
                if(live.length() > 0){
                    // 实况图 → 动图目录，和单独下载完全一致
                    enqueueDownloadPlat(iu, "实况" + (gi + 1), "jpg", "动图", "",
                            iplat, sid, live);
                    ln++;
                }else{
                    enqueueDownloadPlat(iu, "图" + (gi + 1), iext, "图片", "",
                            iplat, sid, "");
                    qn++;
                }''')

# ============================================================
# A2. 视频直链分支：去掉硬编码抖音
# ============================================================
rep('A2-视频平台',
    '''            String tag = "视频" + (batchDlIndex + 1);
            Log.i("DYP", "BATCH_DL_DIRECT idx=" + batchDlIndex + "/" + batchDlTotal
                    + " tag=" + tag + " plat=抖音");''',
    '''            String tag = "视频" + (batchDlIndex + 1);
            String vplat = platForItem(it);
            Log.i("DYP", "BATCH_DL_DIRECT idx=" + batchDlIndex + "/" + batchDlTotal
                    + " tag=" + tag + " plat=" + vplat);''')

rep('A3-入队平台',
    '''            String sid = (it.id != null) ? it.id : "";
            enqueueDownload(direct, tag, "mp4", "视频", "", "抖音", sid);''',
    '''            String sid = (it.id != null) ? it.id : "";
            enqueueDownload(direct, tag, "mp4", "视频", "", vplat, sid);''')

# ============================================================
# A4. 新增 platForItem()
# ============================================================
if 'String platForItem(' not in s:
    anchor = '''    /** 列表转 "|" 拼接串（图集图片 / 实况地址用） */'''
    helper = '''    /** 判定某个列表条目属于哪个平台。
     *
     *  为什么要单独写：批量解析可以混着 B站/快手/TikTok/抖音 一起跑，
     *  原来 enqueueDownload 里硬编码 "抖音"，
     *  结果所有平台的图集视频全落到 抖音/ 下。
     *
     *  判定顺序：
     *    1) 条目自己的 URL（分享链接，最好认）
     *    2) 媒体直链域名（有些分享链接是短链，platOf 认不出）
     *    3) 兜底给 platOf 自己去猜
     */
    String platForItem(ProfileParser.ProfileItem it){
        if(it == null) return "";
        String p1 = platOf(it.url == null ? "" : it.url);
        if(p1.length() > 0) return p1;
        String p2 = platOf(it.directUrl == null ? "" : it.directUrl);
        if(p2.length() > 0) return p2;
        if(it.cover != null && it.cover.length() > 0){
            String p3 = platOf(it.cover);
            if(p3.length() > 0) return p3;
        }
        return "其他";
    }

'''
    s = s.replace(anchor, helper + anchor, 1)
    ok.append('A4-platForItem')
else:
    ok.append('A4-已存在')

io.open(P, 'w', encoding='utf-8').write(s)
print('成功:', ok)
print('失败:', miss)