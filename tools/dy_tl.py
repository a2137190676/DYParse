# -*- coding: utf-8 -*-
import json, io

d = json.load(io.open('/sdcard/Download/DYP_dyprof_p1.json', encoding='utf-8'))
tl = d.get('time_list') or []
print('time_list n =', len(tl))
for i, t in enumerate(tl):
    print(i, json.dumps(t, ensure_ascii=False)[:200])
print()
print('request_item_cursor =', d.get('request_item_cursor'))
print('min_cursor =', d.get('min_cursor'))
print('post_serial =', d.get('post_serial'))
print('status_code =', d.get('status_code'))