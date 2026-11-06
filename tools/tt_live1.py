# -*- coding: utf-8 -*-
# TikTok 实况图：tikwm 的 live_images 是动图轨，images 是静态图轨
# 之前只读了 images，live_images 完全没用上 → 实况图退化成普通图集
import io

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/MainActivity.java'
d = io.open(P, encoding='utf-8').read()

old = '''                curImgs = ib.toString();
                curPlay = "";
            }'''

new = '''                curImgs = ib.toString();

                // ===== TikTok 实况图：live_images 是动图轨 =====
                // tikwm 返回的 live_images 与 images 一一对应（同下标 = 同一张实况图）
                // 不处理的话实况图会退化成普通静态图集
                try{
                    String liveJson = jobjRaw(body, "live_images");
                    if(liveJson != null && liveJson.length() > 2){
                        String[] liveUrls = jarrUrls(liveJson);
                        String[] imgUrls2 = jarrUrls(imgJson);
                        Log.i("DYP", "TT_LIVE_IMAGES n=" + liveUrls.length
                                + " imgs=" + imgUrls2.length);
                        if(liveUrls.length > 0){
                            StringBuilder lb = new StringBuilder();
                            int cnt = Math.min(liveUrls.length, Math.max(imgUrls2.length, liveUrls.length));
                            for(int i = 0; i < cnt; i++){
                                String lv = (i < liveUrls.length) ? liveUrls[i] : "";
                                String st = (i < imgUrls2.length) ? imgUrls2[i] : "";
                                if(lb.length() > 0) lb.append("|");
                                // 格式：静态图URL@@动图URL，跟抖音实况图一致
                                lb.append(st).append("@@").append(lv);
                            }
                            ttLivePairs = lb.toString();
                            Log.i("DYP", "TT_LIVE_PAIRS n=" + cnt);
                        }
                    }
                }catch(Throwable t){
                    Log.i("DYP", "TT_LIVE_ERR " + t);
                }

                curPlay = "";
            }'''

if old in d:
    d = d.replace(old, new, 1); print('live_images 解析 OK')
else:
    print('MISS')

io.open(P, 'w', encoding='utf-8').write(d)

# 检查 jobjRaw 是否存在
print('jobjRaw 存在:', 'jobjRaw' in d)
i = d.find('static String jobjRaw')
if i > 0: print(d[i:i+100])