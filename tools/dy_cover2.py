# -*- coding: utf-8 -*-
# 主页列表缩略图不显示：appendDyProfileItems 从未给 it.cover 赋值。
# 封面来源：video.cover.url_list[0]，图集用 images[0].url_list[0]。
import io

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/MainActivity.java'
d = io.open(P, encoding='utf-8').read()

old = '''            if(isImage && it.directUrl.length() == 0){
                Object firstImg = ((List) imgs).get(0);
                if(firstImg instanceof Map){
                    Object ul = get((Map) firstImg, "url_list");
                    String uu = firstUrl(ul);
                    if(uu.length() > 0 && !uu.equals("?")) it.directUrl = uu;
                }
            }
            it.url = "https://www.iesdouyin.com/share/video/" + awemeId + "/";'''

new = '''            if(isImage && it.directUrl.length() == 0){
                Object firstImg = ((List) imgs).get(0);
                if(firstImg instanceof Map){
                    Object ul = get((Map) firstImg, "url_list");
                    String uu = firstUrl(ul);
                    if(uu.length() > 0 && !uu.equals("?")) it.directUrl = uu;
                }
            }

            // ---- 封面（列表缩略图用，之前漏了这段导致列表全是图标）----
            try{
                if(isImage && imgs instanceof List && !((List) imgs).isEmpty()){
                    Object firstImg = ((List) imgs).get(0);
                    if(firstImg instanceof Map){
                        it.cover = firstUrl(get((Map) firstImg, "url_list"));
                    }
                }
                if(it.cover.length() == 0){
                    Object video = get(aw, "video");
                    if(video instanceof Map){
                        // 优先 cover，退 origin_cover
                        Object cv = get((Map) video, "cover");
                        if(cv instanceof Map){
                            it.cover = firstUrl(get((Map) cv, "url_list"));
                        }
                        if(it.cover.length() == 0){
                            Object oc = get((Map) video, "origin_cover");
                            if(oc instanceof Map){
                                it.cover = firstUrl(get((Map) oc, "url_list"));
                            }
                        }
                    }
                }
                if(it.cover != null && it.cover.equals("?")) it.cover = "";
            }catch(Throwable tc){ Log.i("DYP", "DYPROF_COVER_ERR " + tc); }

            it.url = "https://www.iesdouyin.com/share/video/" + awemeId + "/";'''

if old in d:
    d = d.replace(old, new, 1)
    io.open(P, 'w', encoding='utf-8').write(d)
    print('封面赋值 OK')
else:
    print('MISS')