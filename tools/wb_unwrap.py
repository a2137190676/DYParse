# -*- coding: utf-8 -*-
# 根因：微博 statuses/show 返回 {"ok":1,"data":{...}}，代码把外层当实体解析了。
# 修法：拿到 root 后先解包 data 层。
import io

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/WeiboParser.java'
d = io.open(P, encoding='utf-8').read()

old = '''            v.id = mid;
            JSONObject user = root.optJSONObject("user");'''

new = '''            // 微博返回的是 {"ok":1,"data":{...}}，必须先解包 data 层
            JSONObject inner = root.optJSONObject("data");
            if(inner != null){
                Log.i(TAG, "WB_UNWRAP data 层");
                root = inner;
            }
            v.id = mid;
            JSONObject user = root.optJSONObject("user");'''

if old in d:
    d = d.replace(old, new, 1)
    io.open(P, 'w', encoding='utf-8').write(d)
    print('解包 data 层 OK')
else:
    print('MISS')