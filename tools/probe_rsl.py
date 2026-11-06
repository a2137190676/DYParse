# -*- coding: utf-8 -*-
"""排查 RSLomgJDVrs 这条链接的解析路径。"""
import urllib.request
import gzip
import json
import re

SHORT = "https://v.douyin.com/RSLomgJDVrs/"

UA_DESKTOP = ("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
              "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
UA_MOBILE = ("Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) "
             "AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1")

print('=' * 60)
print('1) 短链跳转')
print('=' * 60)
aweme_id = None
final_url = None
for ua_name, ua in [('Desktop', UA_DESKTOP), ('Mobile', UA_MOBILE)]:
    try:
        req = urllib.request.Request(SHORT, headers={'User-Agent': ua})
        r = urllib.request.urlopen(req, timeout=15)
        final = r.geturl()
        body = r.read(5000).decode('utf-8', 'ignore')
        print('  [%s] HTTP %d' % (ua_name, r.getcode()))
        print('    final:', final[:120])
        # 提取 ID
        for pattern in [r'/video/(\d+)', r'/note/(\d+)', r'"aweme_id"\s*:\s*"(\d+)"']:
            m = re.search(pattern, final) or re.search(pattern, body)
            if m:
                aweme_id = m.group(1)
                final_url = final
                break
        if aweme_id:
            print('    aweme_id:', aweme_id)
            break
        # 是否命中风控
        for kw in ['抱歉出错', '请尝试在抖音内观看', '作品不存在', '已删除', 'verify']:
            if kw in body:
                print('    !! 风控关键词:', kw)
    except Exception as e:
        print('  [%s] FAIL %s %s' % (ua_name, type(e).__name__, str(e)[:60]))

print()
print('  aweme_id =', aweme_id)

if aweme_id:
    print()
    print('=' * 60)
    print('2) share 页面 _ROUTER_DATA')
    print('=' * 60)
    try:
        req = urllib.request.Request(
            'https://www.iesdouyin.com/share/video/%s/' % aweme_id,
            headers={'User-Agent': UA_MOBILE, 'Referer': 'https://www.douyin.com/'})
        r = urllib.request.urlopen(req, timeout=15)
        raw = r.read()
        if raw[:2] == b'\x1f\x8b':
            raw = gzip.decompress(raw)
        html = raw.decode('utf-8', 'ignore')
        print('  页面长度:', len(html))
        i = html.find('_ROUTER_DATA')
        print('  _ROUTER_DATA 位置:', i)
        if i > 0:
            # 抠 JSON
            eq = html.find('=', i)
            brace = html.find('{', eq)
            depth = 0
            in_str = False
            esc = False
            end = -1
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
                        end = k + 1
                        break
            if end > 0:
                data = json.loads(html[brace:end])
                print('  顶层 keys:', list(data.keys()))
                ld = data.get('loaderData') or {}
                print('  loaderData keys:', list(ld.keys()))
                pg = ld.get('video_(id)/page') or {}
                print('  page keys:', list(pg.keys()))
                print('  renderInSSR:', pg.get('renderInSSR'))
                print('  有 videoInfoRes?', 'videoInfoRes' in pg)
                if 'videoInfoRes' in pg:
                    vi = pg['videoInfoRes']
                    il = vi.get('item_list') if isinstance(vi, dict) else None
                    print('  item_list:', len(il) if il else 0)
                    if il:
                        it = il[0]
                        print('  desc:', str(it.get('desc'))[:60])
                        print('  author:', (it.get('author') or {}).get('nickname'))
                        vd = it.get('video') or {}
                        print('  duration:', vd.get('duration'))
                        pa = vd.get('play_addr') or {}
                        urls = pa.get('url_list') or []
                        print('  play_addr urls:', len(urls))
                        if urls:
                            print('  第一条:', urls[0][:120])
    except Exception as e:
        print('  FAIL', type(e).__name__, str(e)[:100])

print()
print('=' * 60)
print('3) detail 接口（带 cookie）')
print('=' * 60)
import http.cookiejar
cj = http.cookiejar.CookieJar()
opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(cj))
try:
    req = urllib.request.Request('https://www.douyin.com/', headers={'User-Agent': UA_DESKTOP})
    opener.open(req, timeout=15).read()
    print('  cookie 数:', len(cj))
except Exception as e:
    print('  拿 cookie 失败:', type(e).__name__)

if len(cj) > 0 and aweme_id:
    api = ('https://www.douyin.com/aweme/v1/web/aweme/detail/?device_platform=webapp'
           '&aid=6383&channel=channel_pc_web&aweme_id=%s&version_code=170400'
           '&cookie_enabled=true&platform=PC&downlink=10' % aweme_id)
    try:
        req = urllib.request.Request(api, headers={
            'User-Agent': UA_DESKTOP,
            'Referer': 'https://www.douyin.com/video/%s' % aweme_id,
        })
        r = opener.open(req, timeout=15)
        raw = r.read()
        print('  detail HTTP %d len=%d' % (r.getcode(), len(raw)))
        print('  前 200:', raw[:200])
    except Exception as e:
        print('  detail FAIL:', type(e).__name__, str(e)[:100])