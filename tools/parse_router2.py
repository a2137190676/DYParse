# -*- coding: utf-8 -*-
"""看 _ROUTER_DATA 的 errors 和 commonContext，找出为什么没有作品数据。"""
import urllib.request
import gzip
import json

AWEME_ID = "7700780886261779763"
UA_MOBILE = ("Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) "
             "AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1")
UA_ANDROID = ("Mozilla/5.0 (Linux; Android 13; SM-G991B) AppleWebKit/537.36 "
              "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")


def get(url, ua):
    req = urllib.request.Request(url, headers={'User-Agent': ua,
                                               'Referer': 'https://www.douyin.com/'})
    r = urllib.request.urlopen(req, timeout=15)
    raw = r.read()
    if raw[:2] == b'\x1f\x8b':
        raw = gzip.decompress(raw)
    return raw.decode('utf-8', 'ignore')


def parse_router(html):
    i = html.find('_ROUTER_DATA')
    if i < 0:
        return None
    eq = html.find('=', i)
    brace = html.find('{', eq)
    depth = 0
    in_str = False
    esc = False
    for k in range(brace, len(html)):
        c = html[k]
        if in_str:
            if esc:
                esc = False
            elif c == '\\':
                esc = True
            elif c == '"':
                in_str = False
            continue
        if c == '"':
            in_str = True
        elif c == '{':
            depth += 1
        elif c == '}':
            depth -= 1
            if depth == 0:
                return json.loads(html[brace:k + 1])
    return None


for ua_name, ua in [('iPhone', UA_MOBILE), ('Android', UA_ANDROID)]:
    print('=' * 62)
    print('UA =', ua_name)
    print('=' * 62)
    try:
        html = get('https://www.iesdouyin.com/share/video/%s/' % AWEME_ID, ua)
        d = parse_router(html)
        if d is None:
            print('  没有 _ROUTER_DATA')
            continue
        print('  errors:', json.dumps(d.get('errors'), ensure_ascii=False)[:300])
        ld = d.get('loaderData') or {}
        pg = ld.get('video_(id)/page') or {}
        print('  page keys:', list(pg.keys()))
        print('  isSpider:', pg.get('isSpider'))
        print('  renderInSSR:', pg.get('renderInSSR'))
        cc = pg.get('commonContext') or {}
        print('  commonContext keys:', list(cc.keys())[:12])
        # 有没有 videoInfoRes
        if 'videoInfoRes' in pg:
            vi = pg['videoInfoRes']
            print('  !! videoInfoRes 存在')
            il = vi.get('item_list') if isinstance(vi, dict) else None
            print('  item_list:', len(il) if il else 0)
            if il:
                it = il[0]
                print('  desc:', str(it.get('desc'))[:60])
                print('  author:', (it.get('author') or {}).get('nickname'))
        else:
            print('  没有 videoInfoRes，全 key:', list(pg.keys()))
        print()
    except Exception as e:
        print('  FAIL', type(e).__name__, str(e)[:100])
        print()

print('=' * 62)
print('对照：换一个正常的抖音 ID 试试（验证接口本身没坏）')
print('=' * 62)
# 用一个已知的公开视频做对照
TEST_ID = "7300000000000000000"
try:
    html = get('https://www.iesdouyin.com/share/video/%s/' % AWEME_ID, UA_MOBILE)
    d = parse_router(html)
    ld = (d or {}).get('loaderData') or {}
    pg = ld.get('video_(id)/page') or {}
    print('  同一 ID，Android UA 下 page 的 keys:')
    print('   ', list(pg.keys()))
except Exception as e:
    print('  FAIL', type(e).__name__)