# -*- coding: utf-8 -*-
# live_images 兼容：服务器可能裁剪字段，也可能嵌在不同层级。
# 改成直接扫 body 文本找 "live_images":[...] 片段。
import io

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/MainActivity.java'
d = io.open(P, encoding='utf-8').read()

old = '''                    org.json.JSONObject rr = new org.json.JSONObject(body);
                    // 服务器返回是扁平结构（没有 data 层），但要兼容两种
                    org.json.JSONObject dd = rr.optJSONObject("data");
                    if(dd == null) dd = rr;
                    org.json.JSONArray la = dd.optJSONArray("live_images");
                    org.json.JSONArray ia = dd.optJSONArray("images");
                    if(la != null && la.length() > 0){
                        StringBuilder sbImg = new StringBuilder();
                        StringBuilder sbDyn = new StringBuilder();
                        int cnt = Math.max(la.length(), ia == null ? 0 : ia.length());
                        for(int i = 0; i < cnt; i++){
                            String st = (ia != null && i < ia.length()) ? ia.optString(i, "") : "";
                            String lv = (i < la.length()) ? la.optString(i, "") : "";
                            if(i > 0){ sbImg.append('|'); sbDyn.append('|'); }
                            sbImg.append(st);
                            sbDyn.append(lv);
                        }
                        curImgs = sbImg.toString();
                        ttLiveDyn = sbDyn.toString();
                        Log.i("DYP", "TT_LIVE_OK n=" + cnt);
                    }'''

new = '''                    // 直接从 body 文本里抠 live_images 数组（服务器可能改结构/层级）
                    int lp = body.indexOf("\\"live_images\\"");
                    Log.i("DYP", "TT_LIVE_SCAN pos=" + lp + " bodyLen=" + body.length());
                    if(lp > 0){
                        int lb = body.indexOf('[', lp);
                        int le = (lb > 0) ? body.indexOf(']', lb) : -1;
                        if(lb > 0 && le > lb){
                            String liveArr = body.substring(lb, le + 1);
                            // 静态图数组：优先 images，退而求其次用已有的 curImgs
                            int ip = body.indexOf("\\"images\\"");
                            String imgArr = "";
                            if(ip > 0){
                                int ib2 = body.indexOf('[', ip);
                                int ie2 = (ib2 > 0) ? body.indexOf(']', ib2) : -1;
                                if(ib2 > 0 && ie2 > ib2) imgArr = body.substring(ib2, ie2 + 1);
                            }
                            String[] liveUrls = jarrUrls(liveArr);
                            String[] imgUrls2 = jarrUrls(imgArr);
                            Log.i("DYP", "TT_LIVE_FOUND live=" + liveUrls.length
                                    + " img=" + imgUrls2.length);
                            if(liveUrls.length > 0){
                                StringBuilder sbImg = new StringBuilder();
                                StringBuilder sbDyn = new StringBuilder();
                                int cnt = Math.max(liveUrls.length, imgUrls2.length);
                                for(int i = 0; i < cnt; i++){
                                    String st = (i < imgUrls2.length) ? imgUrls2[i] : "";
                                    String lv = (i < liveUrls.length) ? liveUrls[i] : "";
                                    if(i > 0){ sbImg.append('|'); sbDyn.append('|'); }
                                    sbImg.append(st);
                                    sbDyn.append(lv);
                                }
                                curImgs = sbImg.toString();
                                ttLiveDyn = sbDyn.toString();
                                Log.i("DYP", "TT_LIVE_OK n=" + cnt);
                            }
                        }
                    }'''

if old in d:
    d = d.replace(old, new, 1)
    io.open(P, 'w', encoding='utf-8').write(d)
    print('文本扫描版 OK')
else:
    print('MISS')