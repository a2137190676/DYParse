# -*- coding: utf-8 -*-
"""
修：批量下载下载不了图片（含实况图）。

【真实数据】batch_list.tsv 第 1 行（图文）逐字段：
    1: [习得吾师真妙法 荣华富贵万万年#常念师恩]
    2: [https://v.douyin.com/q6y-xl37itA/]
    3: []                    ← directUrl 是空的！
    4: [0]                   ← isImage 是 0！
    5: [7703551796506428390]
    6: [https://p3-pc-sign.douyinpic.com/tos-cn-i-0813c001/oA08...jpeg?]

  图片地址在 cover（第 6 列），directUrl 空、isImage=0。
  于是批量下载：direct 空 → 跳过直链 → isImage=false → 跳过图集分支
  → 有 19 位 id → 走 detail → detail 要 uifid → 403 → 这条就没了。

【为什么单独下载正常】
  单独下载走的是 17690/17693 行那套：
      new DlTask(imgUrls.get(i), "实况N"/"图N", "jpg", "动图"/"图片", "", live)
  它直接遍历 imgUrls + dynAt(i) 拿实况地址，根本不看 ProfileItem。
  批量下载没有这套逻辑，所以图文必失败。

【改法】
  ① ProfileItem 加两个字段：imgs（全部图片）、dyns（对应实况视频）
  ② collectBatchItem 里把 imgUrls/dynUrls 全量存进 it.imgs/it.dyns
  ③ saveBatchList/loadBatchList 存读这两列（|分隔，兼容旧文件）
  ④ stepBatchProfileDl 图集分支改成完整版：
     · 遍历 it.imgs 每一张
     · 有对应 dyns → cat="动图"、live 传实况地址（和单独下载一致）
     · 没有 → cat="图片"
  ⑤ 老数据兜底：directUrl 空但 cover 是图片 CDN 时，用 cover 当第一张
"""
import io

ROOT = '/storage/emulated/0/MT2/mcp/DYParse'
PP = ROOT + '/java/com/re/douyinparse/ProfileParser.java'
MA = ROOT + '/java/com/re/douyinparse/MainActivity.java'

ok, miss = [], []


def rep(s, name, old, new):
    if old in s:
        return s.replace(old, new, 1), ok.append(name) or s
    miss.append(name)
    return s


# ============================================================
# ① ProfileItem 加 imgs / dyns
# ============================================================
s = io.open(PP, encoding='utf-8').read()
if 'public java.util.List<String> imgs' not in s:
    s = s.replace(
        '''        /** 封面图 URL（列表缩略图用，可为空） */
        public String cover = "";''',
        '''        /** 封面图 URL（列表缩略图用，可为空） */
        public String cover = "";
        /** 图集的全部图片地址（批量下载遍历用；视频条目为空） */
        public java.util.List<String> imgs = new java.util.ArrayList<String>();
        /** 与 imgs 一一对应的实况视频地址（空串表示该张不是实况） */
        public java.util.List<String> dyns = new java.util.ArrayList<String>();''',
        1)
    ok.append('1-ProfileItem加字段')
else:
    ok.append('1-已存在')

io.open(PP, 'w', encoding='utf-8').write(s)

# ============================================================
# ② collectBatchItem 存全量图片 / 实况
# ============================================================
s = io.open(MA, encoding='utf-8').read()

old2 = '''            it.directUrl = purl;
            it.isImage = hasImgs;
            if(hasImgs) it.directUrl = imgUrls.get(0);
            // 封面：图集用第一张图，视频用解析出的封面
            if(hasImgs) it.cover = imgUrls.get(0);'''

new2 = '''            it.directUrl = purl;
            it.isImage = hasImgs;
            if(hasImgs) it.directUrl = imgUrls.get(0);
            // ============================================================
            // 图集：把「每一张」图 + 对应实况地址都存进列表。
            //
            // 只存 imgUrls.get(0) 是不够的 —— 一条图文可能有 9 张图，
            // 批量下载就只会下第一张，用户看到的就是「图片没下载」。
            // 实况图更要单独存 dynUrls，否则批量下不到实况视频。
            // ============================================================
            if(hasImgs){
                try{
                    it.imgs.clear();
                    it.dyns.clear();
                    for(int gi = 0; gi < imgUrls.size(); gi++){
                        String gu = imgUrls.get(gi);
                        if(gu == null || gu.length() == 0) continue;
                        it.imgs.add(gu);
                        String gd = (dynUrls != null && gi < dynUrls.size()) ? dynUrls.get(gi) : "";
                        it.dyns.add(gd == null ? "" : gd);
                    }
                    Log.i("DYP", "BATCH_COLLECT_IMGS n=" + it.imgs.size()
                            + " live=" + liveOf(it.dyns) + " id=" + it.id);
                }catch(Throwable t){
                    Log.i("DYP", "BATCH_COLLECT_IMGS_ERR " + t);
                }
            }
            // 封面：图集用第一张图，视频用解析出的封面
            if(hasImgs) it.cover = imgUrls.get(0);'''

s, _ = rep(s, '2-collect存全量图', old2, new2)

# ============================================================
# ③ 存盘：加 imgs / dyns 两列
# ============================================================
old3 = '''                  .append(it.isImage ? "1" : "0").append('\\t')
                  .append(batchEsc(it.id)).append('\\t')
                  .append(batchEsc(it.cover)).append('\\n');'''
new3 = '''                  .append(it.isImage ? "1" : "0").append('\\t')
                  .append(batchEsc(it.id)).append('\\t')
                  .append(batchEsc(it.cover)).append('\\t')
                  .append(batchEsc(joinList(it.imgs))).append('\\t')
                  .append(batchEsc(joinList(it.dyns))).append('\\n');'''
s, _ = rep(s, '3-存盘加列', old3, new3)

# ============================================================
# ④ 读盘：读 imgs / dyns（兼容旧文件）
# ============================================================
old4 = '''                it.isImage = p.length > 3 && "1".equals(p[3]);'''
new4 = '''                it.isImage = p.length > 3 && "1".equals(p[3]);
                // 新格式第 7、8 列是全部图片 / 实况地址；
                // 旧文件只有 6 列，这里兼容读，不报错。
                if(p.length > 6) it.imgs = splitList(batchUnesc(p[6]));
                if(p.length > 7) it.dyns = splitList(batchUnesc(p[7]));
                // 老数据没有第 7 列，但 cover 里是图片 CDN 地址：
                // 补进 imgs，让老列表也能被批量下载（用户的痛点）。
                if(it.isImage && it.imgs.isEmpty() && it.cover != null
                        && it.cover.indexOf("http") == 0
                        && isImageCdnUrl(it.cover)){
                    it.imgs.add(it.cover);
                    it.dyns.add("");
                }'''
s, _ = rep(s, '4-读盘加列', old4, new4)

# ============================================================
# ⑤ 图集分支改完整版
# ============================================================
old5 = '''        if(it.isImage){
            String iu = (it.directUrl != null) ? it.directUrl : "";
            if(iu.length() > 0 && iu.startsWith("http")){
                String iext = "jpg";
                String low = iu.toLowerCase();
                if(low.indexOf(".png") > 0 || low.indexOf("format=png") > 0) iext = "png";
                else if(low.indexOf(".webp") > 0 || low.indexOf("format=webp") > 0) iext = "webp";
                else if(low.indexOf(".gif") > 0) iext = "gif";
                String tag = "图文" + (batchDlIndex + 1);
                Log.i("DYP", "BATCH_DL_IMAGE idx=" + batchDlIndex + "/" + batchDlTotal
                        + " tag=" + tag + " ext=" + iext);
                enqueueDownload(iu, tag, iext, "图片", "", "抖音",
                        (it.id != null ? it.id : ""));
                batchDlOk++;
                batchDlIndex++;
                s("批量下载 " + batchDlIndex + "/" + batchDlTotal + "：已入队（图片）");
                h.postDelayed(new Runnable(){ public void run(){ stepBatchProfileDl(); }}, 20);
                return;
            }
            Log.i("DYP", "BATCH_DL_IMAGE_NOURL idx=" + batchDlIndex);
        }
'''

new5 = '''        if(it.isImage){
            // 图集地址来源优先级：
            //   1) it.imgs（新格式，全量）
            //   2) directUrl（旧格式 / 单图）
            //   3) cover（旧格式兜底，实测图文就是这个情况）
            java.util.List<String> srcImgs = new java.util.ArrayList<String>();
            if(it.imgs != null && !it.imgs.isEmpty()){
                srcImgs.addAll(it.imgs);
            }else{
                String d0 = (it.directUrl != null) ? it.directUrl : "";
                if(d0.indexOf("http") == 0){
                    srcImgs.add(d0);
                }else if(it.cover != null && it.cover.indexOf("http") == 0
                        && isImageCdnUrl(it.cover)){
                    srcImgs.add(it.cover);
                }
            }
            if(srcImgs.isEmpty()){
                Log.i("DYP", "BATCH_DL_IMAGE_NOURL idx=" + batchDlIndex
                        + " cover=" + (it.cover == null ? "" : it.cover.length())
                        + " directLen=" + (it.directUrl == null ? 0 : it.directUrl.length()));
                batchDlIndex++;
                h.postDelayed(new Runnable(){ public void run(){ stepBatchProfileDl(); }}, 20);
                return;
            }
            int qn = 0, ln = 0;
            for(int gi = 0; gi < srcImgs.size(); gi++){
                String iu = srcImgs.get(gi);
                if(iu == null || iu.indexOf("http") != 0) continue;
                String iext = imgExtOf(iu);
                String live = "";
                if(it.dyns != null && gi < it.dyns.size()){
                    String dv = it.dyns.get(gi);
                    if(dv != null && dv.length() > 0) live = liveDirect(dv);
                }
                String sid = (it.id != null && it.id.length() > 0)
                        ? (it.id + "_" + (gi + 1)) : "";
                if(live.length() > 0){
                    // 实况图 → 动图目录，和单独下载完全一致
                    enqueueDownloadPlat(iu, "实况" + (gi + 1), "jpg", "动图", "",
                            "抖音", sid, live);
                    ln++;
                }else{
                    enqueueDownloadPlat(iu, "图" + (gi + 1), iext, "图片", "",
                            "抖音", sid, "");
                    qn++;
                }
            }
            Log.i("DYP", "BATCH_DL_IMAGE idx=" + batchDlIndex + "/" + batchDlTotal
                    + " imgs=" + qn + " live=" + ln);
            batchDlOk++;
            batchDlIndex++;
            s("批量下载 " + batchDlIndex + "/" + batchDlTotal + "：已入队（图片 "
                    + qn + " 张 / 实况 " + ln + " 张）");
            h.postDelayed(new Runnable(){ public void run(){ stepBatchProfileDl(); }}, 20);
            return;
        }
'''
s, _ = rep(s, '5-图集分支完整版', old5, new5)

# ============================================================
# ⑥ 工具方法
# ============================================================
anchor = '''    void stepBatchProfileDl(){'''
helpers = '''    /** 列表转 "|" 拼接串（图集图片 / 实况地址用） */
    String joinList(java.util.List<String> a){
        if(a == null || a.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for(int i = 0; i < a.size(); i++){
            if(i > 0) sb.append('|');
            String v = a.get(i);
            // 地址里不可能有 |，但保险起见剔掉
            sb.append(v == null ? "" : v.replace("|", ""));
        }
        return sb.toString();
    }

    /** "|" 拼接串转列表 */
    java.util.List<String> splitList(String s){
        java.util.List<String> r = new java.util.ArrayList<String>();
        if(s == null || s.length() == 0) return r;
        String[] a = s.split("[|]");
        for(int i = 0; i < a.length; i++){
            String v = a[i].trim();
            if(v.length() > 0) r.add(v);
            else r.add("");
        }
        return r;
    }

    /** 至少一张实况 */
    int liveOf(java.util.List<String> a){
        if(a == null) return 0;
        int n = 0;
        for(int i = 0; i < a.size(); i++){
            String v = a.get(i);
            if(v != null && v.length() > 0) n++;
        }
        return n;
    }

    /** 图片 CDN 判定（抖音图片走 douyinpic / 签名域） */
    boolean isImageCdnUrl(String u){
        if(u == null) return false;
        String l = u.toLowerCase();
        if(l.indexOf("douyinpic.com") >= 0) return true;
        if(l.indexOf("byteimg.com") >= 0) return true;
        if(l.indexOf("pstatp.com") >= 0) return true;
        if(l.indexOf("kuaishou.com/") >= 0 && l.indexOf("jpg") > 0) return true;
        // 明显的图片后缀
        if(l.indexOf(".jpeg") > 0 || l.indexOf(".jpg") > 0
                || l.indexOf(".png") > 0 || l.indexOf(".webp") > 0
                || l.indexOf(".gif") > 0) return true;
        return false;
    }

    /** 按 URL 推断图片扩展名 */
    String imgExtOf(String u){
        if(u == null) return "jpg";
        String l = u.toLowerCase();
        if(l.indexOf(".png") > 0 || l.indexOf("format=png") > 0) return "png";
        if(l.indexOf(".webp") > 0 || l.indexOf("format=webp") > 0) return "webp";
        if(l.indexOf(".gif") > 0) return "gif";
        if(l.indexOf(".heic") > 0) return "heic";
        return "jpg";
    }

'''
if 'String joinList(java.util.List<String> a)' not in s:
    s = s.replace(anchor, helpers + anchor, 1)
    ok.append('6-工具方法')
else:
    ok.append('6-已存在')

io.open(MA, 'w', encoding='utf-8').write(s)
print('成功:', ok)
print('失败:', miss)