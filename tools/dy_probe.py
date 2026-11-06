# -*- coding: utf-8 -*-
# 直接从 logcat 里抓到的完整签名 URL 再请求一次，看 aweme_list 到底几条
import io, json, re, urllib.request

LOG = '/tmp/dyurl.txt'
try:
    url = io.open(LOG, encoding='utf-8').read().strip()
except Exception:
    url = ''

if not url:
    print('MISS url')
else:
    req = urllib.request.Request(url, headers={
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 '
                      '(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
        'Referer': 'https://www.douyin.com/',
        'Accept': 'application/json, text/plain, */*',
    })
    try:
        r = urllib.request.urlopen(req, timeout=25)
        body = r.read().decode('utf-8', 'ignore')
        print('http=', r.status, 'len=', len(body))
        d = json.loads(body)
        al = d.get('aweme_list') or []
        print('aweme_list n=', len(al))
        print('has_more=', d.get('has_more'))
        print('max_cursor=', d.get('max_cursor'))
        for a in al[:10]:
            print('  ', a.get('aweme_id'), (a.get('desc') or '')[:30])
    except Exception as e:
        print('ERR', type(e).__name__, e)