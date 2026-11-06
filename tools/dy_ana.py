# -*- coding: utf-8 -*-
import json, io

d = json.load(io.open('/sdcard/Download/DYP_dyprof_p1.json', encoding='utf-8'))
print('top keys:', sorted(d.keys()))
al = d.get('aweme_list') or []
print('aweme_list n =', len(al))
print('has_more =', d.get('has_more'))
print('max_cursor =', d.get('max_cursor'))
print()
for i, a in enumerate(al):
    print(i, a.get('aweme_id'), '|', (a.get('desc') or '')[:40],
          '| is_top', a.get('is_top'), '| images', bool(a.get('images')))

# 看看有没有其他可能的列表字段
for k in d.keys():
    v = d.get(k)
    if isinstance(v, list) and len(v) > 0 and k != 'aweme_list':
        print('other list field:', k, 'n=', len(v))