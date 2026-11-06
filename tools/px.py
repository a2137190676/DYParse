# -*- coding: utf-8 -*-
"""分析 syndication timeline-profile 里 entries 的完整结构"""
import re, json, io, sys

p = sys.argv[1] if len(sys.argv) > 1 else '/storage/emulated/0/ex.html'
h = io.open(p, encoding='utf-8', errors='ignore').read()
m = re.search(r'<script id="__NEXT_DATA__" type="application/json">(.*?)</script>', h, re.S)
if not m:
    print('NO NEXT_DATA, len=', len(h))
    sys.exit(0)
j = json.loads(m.group(1))
pp = j.get('props', {}).get('pageProps', {})
tl = pp.get('timeline', {})
en = tl.get('entries', [])
print('entries n=', len(en))
if en:
    e0 = en[0]
    print('entry0 keys:', list(e0.keys()))
    c = e0.get('content', {})
    print('content keys:', list(c.keys())[:20])
    tw = c.get('tweet', {})
    print('tweet keys:', list(tw.keys())[:25])
    print('  id_str:', tw.get('id_str'))
    print('  permalink:', tw.get('permalink'))
    print('  full_text head:', str(tw.get('full_text'))[:80])
    print('  created_at:', tw.get('created_at'))
    eu = tw.get('entities', {})
    print('  entities keys:', list(eu.keys()))
    me = eu.get('media', [])
    print('  media n:', len(me))
    if me:
        print('  media0 keys:', list(me[0].keys()))
        print('  media0 url:', str(me[0].get('media_url_https'))[:120])
        print('  media0 type:', me[0].get('type'))
        vi = me[0].get('video_info')
        if vi:
            print('  video variants:', len(vi.get('variants', [])))
            for v in vi.get('variants', [])[:4]:
                print('    ', v.get('content_type'), str(v.get('url'))[:100])
    print('  has extended_entities:', 'extended_entities' in tw)
else:
    print('EMPTY entries（账号被过滤 / 敏感内容需登录）')
