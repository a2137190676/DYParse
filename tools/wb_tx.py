# -*- coding: utf-8 -*-
# 微博转发腾讯视频：stream_url 是 redirect_tencent_video.json?vid=xxx
# 该接口已失效（400），改为从 vid 直接走 TxParser 解析
import io

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/WeiboParser.java'
d = io.open(P, encoding='utf-8').read()

old = '''                    String su = mi.optString("stream_url", "");
                    String mp4 = mi.optString("mp4_720p_mp4", mi.optString("mp4_hd_url", ""));
                    if(su.length() > 0) v.playUrl = su;
                    else if(mp4.length() > 0) v.playUrl = mp4;'''

new = '''                    String su = mi.optString("stream_url", "");
                    String mp4 = mi.optString("mp4_720p_mp4", mi.optString("mp4_hd_url", ""));
                    // 微博转腾讯视频：stream_url 是 redirect_tencent_video.json?vid=xxx
                    // 该跳转接口已失效（400），必须拿 vid 走腾讯自己的 getinfo
                    if(su.indexOf("redirect_tencent_video") >= 0){
                        String vid = "";
                        int q = su.indexOf("vid=");
                        if(q >= 0){
                            String rest = su.substring(q + 4);
                            StringBuilder sb = new StringBuilder();
                            for(int k = 0; k < rest.length(); k++){
                                char c = rest.charAt(k);
                                if((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                                        || (c >= '0' && c <= '9')) sb.append(c);
                                else break;
                            }
                            vid = sb.toString();
                        }
                        Log.i(TAG, "WB_TENCENT vid=" + vid);
                        if(vid.length() >= 8){
                            TxParser.TxVideo tv = TxParser.parse(
                                    "https://v.qq.com/x/page/" + vid + ".html");
                            if(tv.ok && tv.playUrl.length() > 0){
                                v.playUrl = tv.playUrl;
                                v.durMs = (long)(tv.durSec * 1000);
                                for(String[] g : tv.gears){
                                    v.gears.add(new String[]{ "腾讯" + g[0], g[1], "0", "0" });
                                }
                                Log.i(TAG, "WB_TENCENT_OK td=" + tv.durSec + " gears=" + tv.gears.size());
                            } else {
                                Log.i(TAG, "WB_TENCENT_FAIL " + tv.err);
                            }
                        }
                    } else if(su.length() > 0){
                        v.playUrl = su;
                    } else if(mp4.length() > 0){
                        v.playUrl = mp4;
                    }'''

if old in d:
    d = d.replace(old, new, 1)
    io.open(P, 'w', encoding='utf-8').write(d)
    print('微博腾讯回落 OK')
else:
    print('MISS')