# -*- coding: utf-8 -*-
"""分析 OmeGFNOOlJA 短链：解析到真实 ID，再探测 detail 接口。"""
import urllib.request
import json
import re

SHORT = "https://v.douyin.com/OmeGFNOOlJA/"

UA_DESKTOP = ("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
              "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
UA_MOBILE = ("Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) "
             "AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1")

print('=' * 60)
print('1) 短链跳转追踪')
print('=' * 60)
for ua_name, ua in [('Desktop', UA_DESKTOP), ('Mobile', UA_MOBILE)]:
    try:
        req = urllib.request.Request(SHORT, headers={'User-Agent': ua})
        r = urllib.request.urlopen(req, timeout=15)
        final = r.geturl()
        print('  [%s] -> HTTP %d' % (ua_name, r.getcode()))
        print('         final: %s' % final)
        # 是否拿到内容
        body = r.read(3000).decode('utf-8', 'ignore')
        print('         body len: %d' % len(body))
        # 找 aweme_id
        ids = re.findall(r'"aweme_id"\s*:\s*"(\d+)"', body)
        ids2 = re.findall(r'/video/(\d+)', final)
        ids3 = re.findall(r'/note/(\d+)', final)
        allids = set(ids + ids2 + ids3)
        print('         找到 ID: %s' % (list(allids)[:5] if allids else '无'))
        # 找关键错误信息
        for kw in ['抱歉出错', '请尝试在抖音内观看', '作品不存在', '已删除', 'verify']:
            if kw in body:
                print('         !! 命中关键词: %s' % kw)
        print()
    except Exception as e:
        print('  [%s] FAIL %s %s' % (ua_name, type(e).__name__, str(e)[:60]))
        print()

print('=' * 60)
print('2) 直接请求 detail 接口（不签名，看返回什么）')
print('=' * 60)
# 先拿一个 aweme_id
aweme_id = None
try:
    req = urllib.request.Request(SHORT, headers={'User-Agent': UA_MOBILE})
    r = urllib.request.urlopen(req, timeout=15)
    final = r.geturl()
    body = r.read(5000).decode('utf-8', 'ignore')
    m = re.search(r'/video/(\d+)', final) or re.search(r'/note/(\d+)', final)
    if m:
        aweme_id = m.group(1)
    else:
        m2 = re.search(r'"aweme_id"\s*:\s*"(\d+)"', body)
        if m2:
            aweme_id = m2.group(1)
except Exception as e:
    print('  拿 ID 失败:', type(e).__name__)

print('  aweme_id =', aweme_id)
if aweme_id:
    api = ('https://www.iesdouyin.com/web/api/v2/aweme/iteminfo/?item_ids=' + aweme_id)
    try:
        req = urllib.request.Request(api, headers={
            'User-Agent': UA_MOBILE,
            'Referer': 'https://www.douyin.com/',
        })
        r = urllib.request.urlopen(req, timeout=15)
        d = json.loads(r.read().decode('utf-8', 'ignore'))
        print('  HTTP %d, status_code=%s' % (r.getcode(), d.get('status_code')))
        il = d.get('item_list') or []
        print('  item_list 长度: %d' % len(il))
        if il:
            it = il[0]
            print('  描述:', str(it.get('desc'))[:60])
            print('  作者:', (it.get('author') or {}).get('nickname'))
            v = it.get('video') or {}
            print('  时长(ms):', v.get('duration'))
            print('  有无 play_addr:', 'play_addr' in v)
    except Exception as e:
        print('  detail 失败: %s %s' % (type(e).__name__, str(e)[:80]))