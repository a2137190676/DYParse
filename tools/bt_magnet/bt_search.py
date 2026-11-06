# -*- coding: utf-8 -*-
import urllib.parse
import json, urllib.request, io

out = []
def w(s=''):
    out.append(str(s))

def gh(url):
    req = urllib.request.Request(url, headers={'User-Agent': 'DYParse-research',
                                               'Accept': 'application/vnd.github+json'})
    with urllib.request.urlopen(req, timeout=30) as r:
        return json.load(r)

queries = [
    'bittorrent library java',
    'torrent android java',
    'magnet download android',
]

for q in queries:
    w('=' * 70)
    w('QUERY: ' + q)
    w('=' * 70)
    try:
        d = gh('https://api.github.com/search/repositories?q='
               + urllib.parse.quote(q) + '&sort=stars&per_page=10')
        for r in d.get('items', [])[:10]:
            w('%-46s %6d ★' % (r['full_name'], r['stargazers_count']))
            w('    ' + (r['description'] or '')[:90])
            w('    lang=%s  updated=%s' % (r.get('language'), r.get('updated_at', '')[:10]))
    except Exception as e:
        w('  ERR ' + str(e))
    w()

io.open('/tmp/bt_research.txt', 'w', encoding='utf-8').write('\n'.join(out))
print('written')