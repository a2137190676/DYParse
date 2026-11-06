# -*- coding: utf-8 -*-
import json, io

d = json.load(io.open('/sdcard/Download/DYP_dyprof_p1.json', encoding='utf-8'))
al = d.get('aweme_list') or []
if not al:
    print('no aweme_list')
else:
    a = al[0]
    print('aweme keys:', sorted(a.keys()))
    print()
    v = a.get('video') or {}
    print('video keys:', sorted(v.keys()))
    for k in ('cover', 'origin_cover', 'dynamic_cover', 'animate_cover'):
        o = v.get(k)
        if isinstance(o, dict):
            ul = o.get('url_list') or []
            print(k, '->', (ul[0][:100] if ul else 'EMPTY'))
        else:
            print(k, '->', type(o).__name__)
    print()
    print('images:', len(a.get('images') or []))
    im = a.get('images') or []
    if im:
        print('img[0] keys:', sorted(im[0].keys()))
        ul = im[0].get('url_list') or []
        print('img[0] url:', ul[0][:100] if ul else 'EMPTY')