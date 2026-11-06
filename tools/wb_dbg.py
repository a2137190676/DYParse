# -*- coding: utf-8 -*-
import io

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/WeiboParser.java'
d = io.open(P, encoding='utf-8').read()

old = '''            JSONObject pageInfo = root.optJSONObject("page_info");
            if(pageInfo != null){
                String t = pageInfo.optString("type", "");
                JSONObject mi = pageInfo.optJSONObject("media_info");'''
new = '''            JSONObject pageInfo = root.optJSONObject("page_info");
            Log.i(TAG, "WB_PAGEINFO " + (pageInfo == null ? "NULL" : pageInfo.toString().substring(
                    0, Math.min(400, pageInfo.toString().length()))));
            if(pageInfo != null){
                String t = pageInfo.optString("type", "");
                JSONObject mi = pageInfo.optJSONObject("media_info");'''

if old in d:
    d = d.replace(old, new, 1)
    io.open(P, 'w', encoding='utf-8').write(d)
    print('加日志 OK')
else:
    print('MISS')