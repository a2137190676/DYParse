# -*- coding: utf-8 -*-
"""看 detail 接口到底返回什么（非 JSON），并试多个候选接口。"""
import urllib.request
import gzip
import io

AWEME_ID = "7700780886261779763"

UA_MOBILE = ("Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) "
             "AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1")
UA_DESKTOP = ("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
              "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")


def fetch(url, ua, referer=None, tag=''):
    h = {'User-Agent': ua, 'Accept': 'application/json, text/plain, */*'}
    if referer:
        h['Referer'] = referer
    try:
        req = urllib.request.Request(url, headers=h)
        r = urllib.request.urlopen(req, timeout=15)
        raw = r.read()
        # gzip 解压
        if raw[:2] == b'\x1f\x8b':
            raw = gzip.decompress(raw)
        txt = raw.decode('utf-8', 'ignore')
        print('  [%s] HTTP %d, len=%d' % (tag, r.getcode(), len(txt)))
        print('       前 200 字符: %r' % txt[:200])
        return txt
    except Exception as e:
        print('  [%s] FAIL %s %s' % (tag, type(e).__name__, str(e)[:80]))
        return None


print('=' * 62)
print('候选接口 1: iesdouyin iteminfo（老接口）')
print('=' * 62)
fetch('https://www.iesdouyin.com/web/api/v2/aweme/iteminfo/?item_ids=' + AWEME_ID,
      UA_MOBILE, 'https://www.douyin.com/', 'iteminfo')

print()
print('=' * 62)
print('候选接口 2: iesdouyin share 页面（HTML，看有没有 _ROUTER_DATA）')
print('=' * 62)
txt = fetch('https://www.iesdouyin.com/share/video/%s/' % AWEME_ID,
            UA_MOBILE, 'https://www.douyin.com/', 'share-html')
if txt:
    for kw in ['_ROUTER_DATA', 'RENDER_DATA', 'SSR_HYDRATED_DATA',
               'verify', '__ac_nonce', 'captcha']:
        i = txt.find(kw)
        print('       %-22s -> %s' % (kw, ('位置 %d' % i) if i > 0 else '不存在'))

print()
print('=' * 62)
print('候选接口 3: 桌面版 video 页面（拿 RENDER_DATA）')
print('=' * 62)
txt2 = fetch('https://www.douyin.com/video/%s' % AWEME_ID,
             UA_DESKTOP, 'https://www.douyin.com/', 'desktop-video')
if txt2:
    for kw in ['RENDER_DATA', '_ROUTER_DATA', 'aweme_detail', 'item_list',
               '__ac_nonce', 'verify', 'status_code']:
        i = txt2.find(kw)
        print('       %-22s -> %s' % (kw, ('位置 %d' % i) if i > 0 else '不存在'))

print()
print('=' * 62)
print('候选接口 4: 抖音 web API（带 ttwid 空试）')
print('=' * 62)
api = ('https://www.douyin.com/aweme/v1/web/aweme/detail/?aweme_id=%s'
       '&aid=6383&device_platform=webapp&version_code=170400' % AWEME_ID)
fetch(api, UA_DESKTOP, 'https://www.douyin.com/', 'web-detail')