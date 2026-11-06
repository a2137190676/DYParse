# -*- coding: utf-8 -*-
"""
给 detail 优先路径补上「图集 / 实况图」处理。

【上一版的漏洞】
  只解析了 aweme_detail.video：
      · 图集作品 video 可能不存在，或存在但没有真视频轨
      · 于是 vurl 为空、music=false → 直接退回整页解析 → 又撞风控
  用户实测「前五个是图文，但是解析不出来」就是这个。

【图集在 detail 里的真实结构】
  aweme_detail.images[]         每项 {url_list:[...], video:{play_addr:{...}}}
                                其中 images[i].video.play_addr 就是实况动轨
  aweme_detail.image_post_info.images[]  同上（另一种形态）

【改法】
  detail 解析里加图集分支：
    · 先判 images 非空 → 收集全部 url_list[0] + 每张的动轨
    · 有图就入图片队列（动图走「实况」分类），不再退回整页解析
    · 直链 / 图集都拿不到，才退回 loadForParse
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


OLD = '''                                    Object vObj = get(adM, "video");
                                    if(vObj instanceof Map){'''

NEW = '''                                    // ============ 图集优先 ============
                                    // 图文作品的资源全在 images[] 里，
                                    // video 可能压根不存在。上一版只看 video，
                                    // 图集就被判成「解析不出来」了。
                                    Object imgsObj = get(adM, "images");
                                    if(!(imgsObj instanceof List) || ((List) imgsObj).isEmpty()){
                                        Object ipi = get(adM, "image_post_info");
                                        if(ipi instanceof Map){
                                            Object ipiImgs = get((Map) ipi, "images");
                                            if(ipiImgs instanceof List && !((List) ipiImgs).isEmpty()){
                                                imgsObj = ipiImgs;
                                            }
                                        }
                                    }
                                    if(imgsObj instanceof List && !((List) imgsObj).isEmpty()){
                                        java.util.List<String> im = new java.util.ArrayList<String>();
                                        java.util.List<String> dv = new java.util.ArrayList<String>();
                                        for(Object imo : (List) imgsObj){
                                            if(!(imo instanceof Map)) continue;
                                            Map imm = (Map) imo;
                                            String iu = firstUrl(get(imm, "url_list"));
                                            if(iu.length() > 0 && !iu.equals("?")) im.add(iu);
                                            // 实况动轨：images[i].video.play_addr
                                            String lv = "";
                                            Object lvo = get(imm, "video");
                                            if(lvo instanceof Map){
                                                Object lpa = get((Map) lvo, "play_addr");
                                                if(lpa instanceof Map) lv = firstUrl(get((Map) lpa, "url_list"));
                                                if(lv.length() == 0){
                                                    Object lda = get((Map) lvo, "download_addr");
                                                    if(lda instanceof Map) lv = firstUrl(get((Map) lda, "url_list"));
                                                }
                                            }
                                            dv.add(lv == null ? "" : lv);
                                        }
                                        if(!im.isEmpty()){
                                            int liveN = 0;
                                            for(int g = 0; g < im.size(); g++){
                                                String iu = im.get(g);
                                                String lv = (g < dv.size()) ? dv.get(g) : "";
                                                String sid = fdid + "_" + (g + 1);
                                                if(lv.length() > 0){
                                                    liveN++;
                                                    String ld = liveDirect(lv);
                                                    enqueueDownloadPlat(iu, "实况" + (g + 1), "jpg", "动图",
                                                            "", "抖音", sid);
                                                    if(ld.length() > 0){
                                                        enqueueDownloadPlat(ld, "实况" + (g + 1), "mp4", "动图",
                                                                "", "抖音", sid + "_dyn");
                                                    }
                                                }else{
                                                    enqueueDownloadPlat(iu, "图" + (g + 1), imgExtOf(iu), "图片",
                                                            "", "抖音", sid);
                                                }
                                            }
                                            DypLog.i("LIST_DETAIL_IMGS id=" + fdid
                                                    + " n=" + im.size() + " live=" + liveN);
                                            s("已加入下载队列：第 " + (fix + 1)
                                                    + " 条（图集 " + im.size() + " 张"
                                                    + (liveN > 0 ? "，含 " + liveN + " 张实况" : "") + "）");
                                            showHintToast("图集已入队 " + im.size() + " 张");
                                            return;
                                        }
                                    }
                                    Object vObj = get(adM, "video");
                                    if(vObj instanceof Map){'''

rep('1-图集分支', OLD, NEW)

io.open(P, 'w', encoding='utf-8').write(s)
print('成功:', ok)
print('失败:', miss)