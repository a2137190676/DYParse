# -*- coding: utf-8 -*-
# TikTok 实况图最终方案：跟抖音一致，用 i_imgs + i_dyn 两个字段
import io

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/MainActivity.java'
d = io.open(P, encoding='utf-8').read()

# 1) 简化之前那段（去掉 ttLivePairs，改成直接存两个串）
old = '''                // ===== TikTok 实况图：live_images 是动图轨 =====
                // tikwm 返回的 live_images 与 images 一一对应（同下标 = 同一张实况图）
                // 不处理的话实况图会退化成普通静态图集
                try{
                    org.json.JSONObject rr = new org.json.JSONObject(body);
                    org.json.JSONObject dd = rr.optJSONObject("data");
                    org.json.JSONArray la = (dd == null) ? null : dd.optJSONArray("live_images");
                    org.json.JSONArray ia = (dd == null) ? null : dd.optJSONArray("images");
                    if(la != null && la.length() > 0){
                        String[] liveUrls = new String[la.length()];
                        for(int li = 0; li < la.length(); li++) liveUrls[li] = la.optString(li, "");
                        String[] imgUrls2 = new String[ia == null ? 0 : ia.length()];
                        for(int ii = 0; ii < imgUrls2.length; ii++) imgUrls2[ii] = ia.optString(ii, "");
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
'''

new = '''                // ===== TikTok 实况图：live_images 是动图轨，跟 images 一一对应 =====
                // 用抖音同一套字段：i_imgs（静态图）+ i_dyn（动图轨）
                try{
                    org.json.JSONObject rr = new org.json.JSONObject(body);
                    org.json.JSONObject dd = rr.optJSONObject("data");
                    org.json.JSONArray la = (dd == null) ? null : dd.optJSONArray("live_images");
                    org.json.JSONArray ia = (dd == null) ? null : dd.optJSONArray("images");
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
                    }
                }catch(Throwable t){
                    Log.i("DYP", "TT_LIVE_ERR " + t);
                }
'''
if old in d:
    d = d.replace(old, new, 1); print('live 段简化 OK')
else:
    print('MISS live 段')

# 2) 字段改名
d = d.replace('    String ttLivePairs = "";', '    String ttLiveDyn = "";')

# 3) 输出 i_dyn
old2 = '''            if(curImgs.length() > 0) TtParser.Kv(b, "i_imgs", curImgs);'''
new2 = '''            if(curImgs.length() > 0) TtParser.Kv(b, "i_imgs", curImgs);
            if(ttLiveDyn.length() > 0){ TtParser.Kv(b, "i_dyn", ttLiveDyn); TtParser.Kv(b, "i_live", "1"); }'''
if old2 in d:
    d = d.replace(old2, new2, 1); print('i_dyn 输出 OK')
else:
    print('MISS i_dyn')

# 4) 每次解析前重置
old3 = '''            curImgs = "";'''
new3 = '''            curImgs = "";
            ttLiveDyn = "";'''
if old3 in d:
    d = d.replace(old3, new3, 1); print('重置 OK')

io.open(P, 'w', encoding='utf-8').write(d)
print('done')