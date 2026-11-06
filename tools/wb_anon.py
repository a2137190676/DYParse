# -*- coding: utf-8 -*-
# 微博：statuses/show 先不带 Cookie（anon 才有完整 page_info），失败再带 visitor
import io

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/WeiboParser.java'
d = io.open(P, encoding='utf-8').read()

# 1) resolveStatus 入口：把 api 请求改成先无 Cookie
old = '''            String api = "https://m.weibo.cn/statuses/show?id=" + mid;
            String body = httpGet(api, "https://m.weibo.cn/", cookie);
            if(body == null){ v.err = "微博接口无返回"; return; }
            JSONObject root = new JSONObject(body);'''

new = '''            String api = "https://m.weibo.cn/statuses/show?id=" + mid;
            // 关键：visitor SUB Cookie 会让微博返回「精简版」（page_info 直接没有）。
            // 实测匿名请求才给完整 page_info，所以先用空 Cookie。
            String body = httpGet(api, "https://m.weibo.cn/status/", "");
            Log.i(TAG, "WB_STATUS_ANON len=" + (body == null ? 0 : body.length()));
            JSONObject root = null;
            if(body != null && body.trim().startsWith("{")){
                try{ root = new JSONObject(body); }catch(Throwable t){}
            }
            // 匿名拿不到（HTML 或解析失败）再带 visitor Cookie 重试
            if(root == null){
                Log.i(TAG, "WB_STATUS_ANON_FAIL，改用 visitor Cookie 重试");
                String b2 = httpGet(api, "https://m.weibo.cn/", cookie);
                if(b2 != null && b2.trim().startsWith("{")){
                    try{ root = new JSONObject(b2); }catch(Throwable t){}
                }
                if(root == null){
                    v.err = "微博接口无返回（可能触发了风控）";
                    return;
                }
            }'''

if old in d:
    d = d.replace(old, new, 1); print('匿名优先 OK')
else:
    print('MISS 匿名优先')

io.open(P, 'w', encoding='utf-8').write(d)
print('done')