# -*- coding: utf-8 -*-
# 检查抖音「其他作品」接口：/aweme/v1/web/aweme/post/ 只给 4 条，
# 试 /aweme/v1/web/aweme/favorite/ 和 user/profile 里的 aweme_count 对照
import json, io

d = json.load(io.open('/sdcard/Download/DYP_dyprof_p1.json', encoding='utf-8'))

# 找所有含 aweme_id 的地方（递归）
found = []
def walk(o, path=''):
    if isinstance(o, dict):
        if 'aweme_id' in o and isinstance(o.get('aweme_id'), str):
            found.append((path, o.get('aweme_id'), (o.get('desc') or '')[:30]))
        for k, v in o.items():
            walk(v, path + '/' + k)
    elif isinstance(o, list):
        for i, v in enumerate(o):
            walk(v, path + '[' + str(i) + ']')

walk(d)
print('全部 aweme_id 出现次数:', len(found))
seen = set()
for p, aid, desc in found:
    if aid in seen: continue
    seen.add(aid)
    print(' ', aid, '|', desc, '|', p[:60])
print()
print('唯一 aweme_id 数:', len(seen))