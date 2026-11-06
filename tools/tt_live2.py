# -*- coding: utf-8 -*-
# 用 JSONObject 读 live_images，并补 ttLivePairs 字段
import io

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/MainActivity.java'
d = io.open(P, encoding='utf-8').read()
n = 0

# 1) 替换 jobjRaw 那行
old = '''                    String liveJson = jobjRaw(body, "live_images");
                    if(liveJson != null && liveJson.length() > 2){
                        String[] liveUrls = jarrUrls(liveJson);
                        String[] imgUrls2 = jarrUrls(imgJson);'''
new = '''                    org.json.JSONObject rr = new org.json.JSONObject(body);
                    org.json.JSONObject dd = rr.optJSONObject("data");
                    org.json.JSONArray la = (dd == null) ? null : dd.optJSONArray("live_images");
                    org.json.JSONArray ia = (dd == null) ? null : dd.optJSONArray("images");
                    if(la != null && la.length() > 0){
                        String[] liveUrls = new String[la.length()];
                        for(int li = 0; li < la.length(); li++) liveUrls[li] = la.optString(li, "");
                        String[] imgUrls2 = new String[ia == null ? 0 : ia.length()];
                        for(int ii = 0; ii < imgUrls2.length; ii++) imgUrls2[ii] = ia.optString(ii, "");'''
if old in d:
    d = d.replace(old, new, 1); n += 1; print('jobjRaw 替换 OK')
else:
    print('MISS jobjRaw')

# 2) 补字段 ttLivePairs
anchor = '    // 抖音主页：签名翻页接管'
if 'ttLivePairs' not in d.split('void ttSrvRender')[0][:200000]:
    # 找合适位置：curImgs 字段附近
    i = d.find('    String curImgs')
    if i < 0:
        i = d.find('String curImgs')
    print('curImgs 字段位置:', d[:i].count('\n') + 1 if i > 0 else 'MISS')
    if i > 0:
        line_end = d.find('\n', i)
        d = d[:line_end] + '\n    /** TikTok 实况图配对：静态图@@动图，多个用 | 分隔 */\n    String ttLivePairs = "";' + d[line_end:]
        n += 1
        print('ttLivePairs 字段 OK')

io.open(P, 'w', encoding='utf-8').write(d)
print('changed:', n)