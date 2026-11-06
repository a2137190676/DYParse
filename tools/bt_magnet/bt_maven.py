# -*- coding: utf-8 -*-
import json, urllib.request, io

out = []
def w(s=''):
    out.append(str(s))

def get(url):
    req = urllib.request.Request(url, headers={'User-Agent': 'DYParse'})
    with urllib.request.urlopen(req, timeout=30) as r:
        return r.read().decode('utf-8', 'replace')

try:
    txt = get('https://search.maven.org/solrsearch/select?q=g:org.libtorrent4j&rows=30&wt=json')
    d = json.loads(txt)
    w('=== org.libtorrent4j 制品 ===')
    for x in d['response']['docs']:
        w('%-44s %s' % (x['a'], x['latestVersion']))
except Exception as e:
    w('ERR search.maven ' + str(e))

# 直接看 android 制品的目录
w()
w('=== 直接探测 android 制品 ===')
for art in ['libtorrent4j-android-arm64', 'libtorrent4j-android-arm', 'libtorrent4j']:
    url = ('https://repo1.maven.org/maven2/org/libtorrent4j/%s/2.1.0-35/%s-2.1.0-35.pom'
           % (art, art))
    try:
        t = get(url)
        w('[OK] ' + art + '  (pom ' + str(len(t)) + ' bytes)')
    except Exception as e:
        w('[--] ' + art + '  ' + str(e)[:60])

io.open('/tmp/mv2.txt', 'w', encoding='utf-8').write('\n'.join(out))
print('done')