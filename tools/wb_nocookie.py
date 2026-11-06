# -*- coding: utf-8 -*-
# 微博 page_info 为 NULL：visitor Cookie 让接口降级。
# 方案：先带 cookie 试，拿不到 page_info 就再不带 cookie 试一次。
import io

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/WeiboParser.java'
d = io.open(P, encoding='utf-8').read()

# resolveStatus 里：pageInfo 为 null 时用空 cookie 重试
old = '''            JSONObject pageInfo = root.optJSONObject("page_info");
            Log.i(TAG, "WB_PAGEINFO " + (pageInfo == null ? "NULL" : pageInfo.toString().substring(
                    0, Math.min(400, pageInfo.toString().length()))));'''

new = '''            JSONObject pageInfo = root.optJSONObject("page_info");
            Log.i(TAG, "WB_PAGEINFO " + (pageInfo == null ? "NULL" : pageInfo.toString().substring(
                    0, Math.min(400, pageInfo.toString().length()))));

            // 关键：带 visitor Cookie 时微博会返回「精简版」，page_info 直接没有。
            // 拿不到 page_info 就再发一次不带 Cookie 的请求（curl 实测能拿到完整数据）。
            if(pageInfo == null){
                try{
                    String body2 = httpGet(api, "https://m.weibo.cn/", "");
                    Log.i(TAG, "WB_RETRY_NOCOOKIE len=" + (body2 == null ? 0 : body2.length()));
                    if(body2 != null && body2.length() > 100){
                        JSONObject root2 = new JSONObject(body2);
                        JSONObject pi2 = root2.optJSONObject("page_info");
                        if(pi2 != null){
                            Log.i(TAG, "WB_RETRY_GOT_PAGEINFO");
                            pageInfo = pi2;
                            // 顺带补上用户信息
                            if(v.author.length() == 0){
                                JSONObject u2 = root2.optJSONObject("user");
                                if(u2 != null){
                                    v.author = u2.optString("screen_name", "");
                                    v.avatar = u2.optString("avatar_hd",
                                            u2.optString("profile_image_url", ""));
                                }
                            }
                        }
                    }
                }catch(Throwable t2){
                    Log.i(TAG, "WB_RETRY_ERR " + t2);
                }
            }'''

if old in d:
    d = d.replace(old, new, 1)
    io.open(P, 'w', encoding='utf-8').write(d)
    print('无 Cookie 重试 OK')
else:
    print('MISS')