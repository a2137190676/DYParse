# -*- coding: utf-8 -*-
"""从 iesdouyin share 页面的 _ROUTER_DATA 里抠出作品数据。"""
import urllib.request
import gzip
import json
import re

AWEME_ID = "7700780886261779763"
UA_MOBILE = ("Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) "
             "AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1")


def fetch(url, ua):
    req = urllib.request.Request(url, headers={'User-Agent': ua,
                                               'Referer': 'https://www.douyin.com/'})
    r = urllib.request.urlopen(req, timeout=15)
    raw = r.read()
    if raw[:2] == b'\x1f\x8b':
        raw = gzip.decompress(raw)
    return raw.decode('utf-8', 'ignore')


print('=' * 62)
print('抓 share 页面，抠 _ROUTER_DATA')
print('=' * 62)
html = fetch('https://www.iesdouyin.com/share/video/%s/' % AWEME_ID, UA_MOBILE)
print('  页面长度:', len(html))

i = html.find('_ROUTER_DATA')
print('  _ROUTER_DATA 位置:', i)

if i > 0:
    # 形式通常是: window._ROUTER_DATA = {...};
    seg = html[i:i + 400]
    print('  前后文:', repr(seg[:250]))
    print()

    # 找到 = 后面第一个 {
    eq = html.find('=', i)
    if eq > 0:
        brace = html.find('{', eq)
        if brace > 0:
            # 括号配平
            depth = 0
            end = -1
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
                        end = k + 1
                        break
            print('  JSON 区间: %d ~ %d (长 %d)' % (brace, end, end - brace))
            raw_json = html[brace:end]
            try:
                data = json.loads(raw_json)
                print('  JSON 解析成功! 顶层 key:', list(data.keys())[:8])
                # 找 loaderData
                ld = data.get('loaderData') or {}
                print('  loaderData keys:', list(ld.keys())[:8])
                for k, v in ld.items():
                    if isinstance(v, dict):
                        print('    [%s] keys: %s' % (k, list(v.keys())[:10]))
                        # 找 videoInfoRes / item_list
                        for kk in ['videoInfoRes', 'videoInfo', 'aweme_detail']:
                            if kk in v:
                                print('      !! %s 存在' % kk)
                                vi = v[kk]
                                if isinstance(vi, dict):
                                    il = vi.get('item_list') or vi.get('aweme_list')
                                    if il:
                                        it = il[0]
                                        print('      描述:', str(it.get('desc'))[:60])
                                        au = it.get('author') or {}
                                        print('      作者:', au.get('nickname'))
                                        vd = it.get('video') or {}
                                        print('      时长ms:', vd.get('duration'))
                                        pa = vd.get('play_addr') or {}
                                        urls = pa.get('url_list') or []
                                        print('      play_addr 条数:', len(urls))
                                        if urls:
                                            print('      第一条:', urls[0][:100])
                                        # 过滤列表
                                        fl = vi.get('filter_list')
                                        print('      filter_list:', fl)
            except Exception as e:
                print('  JSON 解析失败:', type(e).__name__, str(e)[:100])
                print('  原始前 300:', repr(raw_json[:300]))