# -*- coding: utf-8 -*-
"""
appendDyProfileItems（签名翻页路径，实际生效的那条）补：
  · 图集全量 imgs / dyns 收集
  · 解析阶段 kind 判定

【为什么】
  这条路径以前对图集只做了：
      if(isImage && it.directUrl.length() == 0) it.directUrl = 第一张图
  也就是「图集当成只有一个文件」。
  于是图文作品：
      · it.imgs 为空 → 批量下载走 directUrl（第一张）当 mp4 下 → 失败
      · 没有实况动轨信息 → live 图永远下不来
  用户反馈「前五个是图文，但是解析不出来」正是这个。

  对照 appendDyProfileItems 与 parseDyProfile 就知道：
  parseDyProfile 我上一轮已经补全了 imgs/dyns/kind，
  但实际生效的是 appendDyProfileItems（日志 DYPROF_REQ 走的就是它）。
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


rep('1-补图集全量+kind',
    '''            if(isImage && it.directUrl.length() == 0){
                Object firstImg = ((List) imgs).get(0);
                if(firstImg instanceof Map){
                    Object ul = get((Map) firstImg, "url_list");
                    String uu = firstUrl(ul);
                    if(uu.length() > 0 && !uu.equals("?")) it.directUrl = uu;
                }
            }''',
    '''            // ============ 图集：全量 imgs + 实况动轨 ============
            //
            // 老版本只取第一张塞进 directUrl，等于「图集当单文件」。
            // 结果批量下载拿它当 mp4 下 → 失败；实况图根本没动轨信息。
            int liveCnt = 0;
            if(isImage){
                try{
                    it.imgs.clear();
                    it.dyns.clear();
                    for(Object imObj : (List) imgs){
                        if(!(imObj instanceof Map)) continue;
                        Map im = (Map) imObj;
                        String iu = firstUrl(get(im, "url_list"));
                        it.imgs.add((iu == null || iu.equals("?")) ? "" : iu);
                        // 实况动轨：images[i].video.play_addr / download_addr
                        String lv = "";
                        Object lvo = get(im, "video");
                        if(lvo instanceof Map){
                            Object lpa = get((Map) lvo, "play_addr");
                            if(lpa instanceof Map) lv = firstUrl(get((Map) lpa, "url_list"));
                            if(lv.length() == 0){
                                Object lda = get((Map) lvo, "download_addr");
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
                        if(lv == null || lv.equals("?")) lv = "";
                        it.dyns.add(lv);
                        if(lv.length() > 0) liveCnt++;
                    }
                    // 第一张可用图作为 directUrl / cover
                    for(String iu : it.imgs){
                        if(iu != null && iu.length() > 0 && !iu.equals("?")){
                            if(it.directUrl.length() == 0) it.directUrl = iu;
                            break;
                        }
                    }
                    DypLog.i("PROF_IMGS id=" + awemeId + " n=" + it.imgs.size()
                            + " live=" + liveCnt);
                }catch(Throwable gt){
                    DypLog.i("PROF_IMGS_ERR " + gt);
                }
            }
            // ============ 解析阶段就把类型定死 ============
            try{
                if(isImage){
                    it.kind = (liveCnt > 0) ? ProfileParser.KIND_LIVE
                                            : ProfileParser.KIND_IMAGE;
                }else{
                    long at = parseLong(get(aw, "aweme_type"), 0);
                    long mt = parseLong(get(aw, "media_type"), 0);
                    if(at == 163 || mt == 43){
                        it.kind = ProfileParser.KIND_MUSIC;
                    }else if(it.directUrl != null && it.directUrl.length() > 0){
                        it.kind = ProfileParser.KIND_VIDEO;
                    }
                }
            }catch(Throwable kt){}
            DypLog.i("PROF_KIND id=" + awemeId + " kind=" + it.kind
                    + "(" + it.kindLabel() + ")");''')

io.open(P, 'w', encoding='utf-8').write(s)
print('成功:', ok)
print('失败:', miss)