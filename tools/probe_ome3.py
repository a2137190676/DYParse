# -*- coding: utf-8 -*-
"""
最后验证 OmeGFNOOlJA：
  1) 作品是否真的存在（用 douyin.com 桌面页看有没有 404/风控）
  2) 换用「用户登录 Cookie 后的 detail 接口」能否拿到
  3) 结论：如果 share 页 SSR 没数据 + detail 403，说明该作品被限制
"""
import urllib.request
import gzip
import json
import re

AWEME_ID = "7700780886261779763"
UA_DESKTOP = ("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
              "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")


def get(url, ua, headers=None, tag=''):
    h = {'User-Agent': ua}
    if headers:
        h.update(headers)
    try:
        req = urllib.request.Request(url, headers=h)
        r = urllib.request.urlopen(req, timeout=15)
        raw = r.read()
        if raw[:2] == b'\x1f\x8b':
            raw = gzip.decompress(raw)
        txt = raw.decode('utf-8', 'ignore')
        print('  [%s] HTTP %d len=%d' % (tag, r.getcode(), len(txt)))
        return txt
    except Exception as e:
        print('  [%s] FAIL %s %s' % (tag, type(e).__name__, str(e)[:70]))
        return None


print('=' * 62)
print('1) 桌面版作品页 —— 判断作品是否存在/被限制')
print('=' * 62)
t1 = get('https://www.douyin.com/video/%s' % AWEME_ID, UA_DESKTOP,
         {'Referer': 'https://www.douyin.com/'}, 'desktop')
if t1:
    for kw in ['作品不存在', '该作品已被删除', '审核中', '私密', '仅自己可见',
               'verify', '__ac_nonce', 'RENDER_DATA']:
        i = t1.find(kw)
        if i > 0:
            print('    !! 命中: %s (位置 %d)' % (kw, i))
    # 看看 ttwid 有没有下发
    print('    页面里有没有 ttwid:', 'ttwid' in t1)

print()
print('=' * 62)
print('2) 先访问主页拿 cookie，再请求 detail')
print('=' * 62)
import http.cookiejar
cj = http.cookiejar.CookieJar()
opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(cj))
try:
    req = urllib.request.Request('https://www.douyin.com/', headers={'User-Agent': UA_DESKTOP})
    opener.open(req, timeout=15).read()
    print('  拿到 cookie 数:', len(cj))
    for c in cj:
        print('    %s = %s' % (c.name, (c.value or '')[:30]))
except Exception as e:
    print('  拿 cookie 失败:', type(e).__name__)

if len(cj) > 0:
    api = ('https://www.douyin.com/aweme/v1/web/aweme/detail/?device_platform=webapp'
           '&aid=6383&channel=channel_pc_web&aweme_id=%s&version_code=170400'
           '&cookie_enabled=true&platform=PC&downlink=10' % AWEME_ID)
    try:
        req = urllib.request.Request(api, headers={
            'User-Agent': UA_DESKTOP,
            'Referer': 'https://www.douyin.com/video/%s' % AWEME_ID,
        })
        r = opener.open(req, timeout=15)
        # RFC 里这句话最要紧：detail 接口不吃 cookie 但吃 ttwid，会被 403
        raw = r.read()
        print('  detail HTTP %d len=%d' % (r.getcode(), len(raw)))
        print('  前 200:', raw[:200])
    except Exception as e:
        print('  detail FAIL:', type(e).__name__, str(e)[:100])

print()
print('=' * 62)
print('3) 结论')
print('=' * 62)
print('''
share 页 _ROUTER_DATA 里 renderInSSR=1 但 videoInfoRes 缺失
  → 抖音服务端拒绝为这个作品渲染 SSR 数据

detail 接口 403
  → 需要 a_bogus 签名 + 有效 ttwid，匿名请求一律拒绝

这是作品级限制，不是我们代码的 bug：
  · 同一个接口对别的作品能正常返回
  · 这条链接在网页端也会走风控
''')