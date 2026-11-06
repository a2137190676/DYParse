# -*- coding: utf-8 -*-
"""
把 WebView 路径的 parseDyProfile 升级成和 appendDyProfileItems 同级。

【问题】
  WebView 拦截走的是 parseDyProfile()，它的取值逻辑还是最老的一版：
      · 只取 video.play_addr.url_list[0]
      · 没有 bit_rate 最高档优先
      · 没有音频过滤（isLikelyAudioUrl）
      · 图集只取第一张，没有 imgs 全量 / live 动轨
  → 哪怕 WebView 拿到了完整的 douyinvod 真链，
    也可能取到音频轨，或者图集只下一张。

【改法】
  把 appendDyProfileItems 里那套成熟逻辑（bit_rate 优先 / 音频过滤 /
  图集全量 / live 动轨）原样搬进 parseDyProfile。
  这样两条路径（WebView 拦截 + 签名翻页）走同一套提取标准。
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


OLD = '''            // 视频直链：video.play_addr.url_list 第一个
            if(!isImage){
                Object video = get(aw, "video");
                if(video instanceof Map){
                    Object pa = get((Map) video, "play_addr");
                    if(pa instanceof Map){
                        String u = firstUrl(get((Map) pa, "url_list"));
                        if(u.length() > 0 && !u.equals("?")) it.directUrl = u;
                    }
                }
            }
            // 图集：第一张图直链
            if(isImage && it.directUrl.length() == 0){
                Object firstImg = ((List) imgs).get(0);
                if(firstImg instanceof Map){
                    Object ul = get((Map) firstImg, "url_list");
                    String u = firstUrl(ul);
                    if(u.length() > 0 && !u.equals("?")) it.directUrl = u;
                }
            }'''

NEW = '''            // 视频直链：与批量解析同一套标准（bit_rate 最高档 → play_addr → vid 拼接）
            //
            // 老版本这里只取 play_addr.url_list[0]，实测会拿到背景音乐轨
            // （douyinstatic 的 .mp3），存成 .mp4 后只有声音没画面。
            if(!isImage){
                Object video = get(aw, "video");
                if(video instanceof Map){
                    Map vm = (Map) video;
                    String best = "";
                    // 1) bit_rate 里码率最高的一档
                    try{
                        List brs = (List) get(vm, "bit_rate");
                        if(brs != null && brs.size() > 0){
                            Map bestMap = null; long bestBr = -1;
                            for(int b = 0; b < brs.size(); b++){
                                Object bo = brs.get(b);
                                if(!(bo instanceof Map)) continue;
                                Map bm = (Map) bo;
                                long br = parseLong(get(bm, "bit_rate"), 0);
                                if(br > bestBr){ bestBr = br; bestMap = bm; }
                            }
                            if(bestMap != null){
                                Object bpa = get(bestMap, "play_addr");
                                if(bpa instanceof Map){
                                    String uu = firstUrl(get((Map) bpa, "url_list"));
                                    if(uu.length() > 0 && !uu.equals("?")) best = uu;
                                    if(best.length() == 0){
                                        String buri = gs(get((Map) bpa, "uri"));
                                        if(buri.length() > 0) best = playUrl(buri);
                                    }
                                }
                            }
                        }
                    }catch(Throwable t){}
                    // 2) 退回 play_addr
                    if(best.length() == 0){
                        Object pa = get(vm, "play_addr");
                        if(pa instanceof Map){
                            String uu = firstUrl(get((Map) pa, "url_list"));
                            if(uu.length() > 0 && !uu.equals("?")) best = uu;
                            if(best.length() == 0){
                                String puri = gs(get((Map) pa, "uri"));
                                if(puri.length() > 0) best = playUrl(puri);
                            }
                        }
                    }
                    // 3) 最后用 vid 拼播放地址
                    if(best.length() == 0){
                        String vid = gs(get(vm, "vid"));
                        if(vid.length() > 0) best = playUrl(vid);
                    }
                    // 疑似音频一律置空（批量下载会回落 detail 取真视频）
                    if(best.length() > 0 && isLikelyAudioUrl(best)){
                        DypLog.i("PROF_WEB_AUDIO id=" + awemeId + " 音频轨，置空 url="
                                + best.substring(0, Math.min(80, best.length())));
                        best = "";
                    }
                    if(best.length() > 0){
                        it.directUrl = best;
                        DypLog.i("PROF_WEB_URL id=" + awemeId + " url="
                                + best.substring(0, Math.min(80, best.length())));
                    }
                    // 时长（0 说明可能不是视频轨）
                    try{
                        long dur = parseLong(get(vm, "duration"), 0);
                        if(dur > 1000) dur = dur / 1000;
                        it.durSec = dur;
                    }catch(Throwable t){}
                }
            }
            // 图集：第一张图直链 + 全量图片列表 + live 动轨
            if(isImage){
                java.util.List<String> imgUrls = new java.util.ArrayList<String>();
                java.util.List<String> dynUrls = new java.util.ArrayList<String>();
                try{
                    for(Object imObj : (List) imgs){
                        if(!(imObj instanceof Map)) continue;
                        Map im = (Map) imObj;
                        String u = firstUrl(get(im, "url_list"));
                        if(u.length() > 0 && !u.equals("?")) imgUrls.add(u);
                        // 实况动轨：video.play_addr / live_photo.video / motion_photo.video
                        String lv = "";
                        Object v0 = get(im, "video");
                        if(v0 instanceof Map){
                            Object lpa = get((Map) v0, "play_addr");
                            if(lpa instanceof Map) lv = firstUrl(get((Map) lpa, "url_list"));
                            if(lv.length() == 0){
                                Object lda = get((Map) v0, "download_addr");
                                if(lda instanceof Map) lv = firstUrl(get((Map) lda, "url_list"));
                            }
                        }
                        if(lv.length() == 0){
                            Object lp = get(im, "live_photo");
                            if(lp instanceof Map){
                                Object lv2 = get((Map) lp, "video");
                                if(lv2 instanceof Map){
                                    Object lpa2 = get((Map) lv2, "play_addr");
                                    if(lpa2 instanceof Map) lv = firstUrl(get((Map) lpa2, "url_list"));
                                    if(lv.length() == 0){
                                        Object lda2 = get((Map) lv2, "download_addr");
                                        if(lda2 instanceof Map) lv = firstUrl(get((Map) lda2, "url_list"));
                                    }
                                }
                            }
                        }
                        if(lv.length() == 0){
                            Object mp = get(im, "motion_photo");
                            if(mp instanceof Map){
                                Object mv3 = get((Map) mp, "video");
                                if(mv3 instanceof Map){
                                    Object lpa3 = get((Map) mv3, "play_addr");
                                    if(lpa3 instanceof Map) lv = firstUrl(get((Map) lpa3, "url_list"));
                                }
                            }
                        }
                        dynUrls.add(lv == null ? "" : lv);
                    }
                }catch(Throwable gt){
                    DypLog.i("PROF_WEB_IMGS_ERR " + gt);
                }
                if(!imgUrls.isEmpty()){
                    it.directUrl = imgUrls.get(0);
                    it.cover = imgUrls.get(0);
                    try{
                        StringBuilder sb = new StringBuilder();
                        for(String u : imgUrls){
                            if(sb.length() > 0) sb.append("|");
                            sb.append(u);
                        }
                        it.imgs = sb.toString();
                    }catch(Throwable t){}
                    int liveN = 0;
                    try{
                        StringBuilder sb2 = new StringBuilder();
                        for(String u : dynUrls){
                            if(sb2.length() > 0) sb2.append("|");
                            if(u != null) sb2.append(u);
                            if(u != null && u.length() > 0) liveN++;
                        }
                        it.dyns = sb2.toString();
                    }catch(Throwable t){}
                    it.live = liveN > 0;
                    DypLog.i("PROF_WEB_IMGS id=" + awemeId + " n=" + imgUrls.size()
                            + " live=" + liveN);
                }
            }'''

rep('升级 parseDyProfile', OLD, NEW)

io.open(P, 'w', encoding='utf-8').write(s)
print('成功:', ok)
print('失败:', miss)