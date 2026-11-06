# -*- coding: utf-8 -*-
import json, io, subprocess
t = io.open('/tmp/tx.json', encoding='utf-8').read()
t = t[t.find('{'):]
d, _ = json.JSONDecoder().raw_decode(t)
v = d['vl']['vi'][0]
base = v['ul']['ui'][0]['url']
fn = v['fn']
fvkey = v['fvkey']
url = base + fn + '?vkey=' + fvkey
print('URL:', url[:160])
io.open('/tmp/txurl.txt', 'w').write(url)