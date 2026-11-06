# -*- coding: utf-8 -*-
"""抓取公开 tracker 列表（含国内源），并逐个测试目标磁力能否拿到 peer。
HTTP tracker 用 urllib 测；UDP tracker 用 BEP-15 协议测。
"""
import urllib.request, socket, struct, time

IH = bytes.fromhex('AE38C22B7953FDF9869529E43B9C0267D3465EB6')
SOURCES = [
    'https://trackerslist.com/all_aria2.txt',
    'https://trackerslist.com/best_aria2.txt',
    'https://raw.githubusercontent.com/ngosang/trackerslist/master/trackers_all.txt',
    'https://raw.githubusercontent.com/XIU2/TrackersListCollection/master/all.txt',
]

found = []
for url in SOURCES:
    try:
        req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
        r = urllib.request.urlopen(req, timeout=20)
        txt = r.read().decode('utf-8', 'replace')
        lines = [l.strip() for l in txt.split('\n') if l.strip()]
        # all_aria2.txt 是逗号分隔单行
        if len(lines) <= 2:
            lines = [x.strip() for x in txt.replace('\n', ',').split(',') if x.strip()]
        got = [l for l in lines if l.startswith(('http://', 'https://', 'udp://'))]
        found.extend(got)
        print('%-58s 取到 %d 条' % (url.split('/')[2] + url.split('/')[-1][:20], len(got)))
    except Exception as e:
        print('%-58s FAIL %s' % (url.split('/')[2], str(e)[:40]))

# 去重
seen = set()
trackers = []
for t in found:
    if t in seen:
        continue
    seen.add(t)
    trackers.append(t)

print('')
print('去重后共 %d 个 tracker' % len(trackers))

with open('/storage/emulated/0/MT2/mcp/DYParse/tools/trackers_raw.txt', 'w') as f:
    f.write('\n'.join(trackers))
print('已保存到 tools/trackers_raw.txt')