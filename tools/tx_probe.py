# -*- coding: utf-8 -*-
import json, io
t = io.open('/tmp/tx.json', encoding='utf-8').read()
t = t[t.find('{'):]
dec = json.JSONDecoder()
d, end = dec.raw_decode(t)
print('em=', d.get('em'), 'msg=', d.get('msg'), 'used=', end)
vi = d.get('vl', {}).get('vi', [])
print('vi n=', len(vi))
fl = d.get('fl', {}).get('fi', [])
print('清晰度档位:')
for f in fl:
    print('  ', f.get('name'), f.get('cname'), 'fs=', f.get('fs'), 'br=', f.get('br'))
if vi:
    v = vi[0]
    print('--- 第一档 ---')
    print('keys:', sorted(v.keys()))
    print('fn =', v.get('fn'))
    print('br =', v.get('br'))
    print('td =', v.get('td'))
    print('fvkey =', v.get('fvkey'))
    print('ul =', json.dumps(v.get('ul'), ensure_ascii=False)[:200])
    print('cl =', json.dumps(v.get('cl'), ensure_ascii=False)[:200])