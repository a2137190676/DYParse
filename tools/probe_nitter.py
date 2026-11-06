# -*- coding: utf-8 -*-
"""探测 nitter 主页 HTML 结构，确认能抽到作品链接。"""
import urllib.request, re, sys

UA = ("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
      "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36")

names = sys.argv[1:] or ["gomimata"]

for nm in names:
    for base in ["https://nitter.net", "https://xcancel.com"]:
        u = base + "/" + nm
        try:
            req = urllib.request.Request(u, headers={"User-Agent": UA})
            b = urllib.request.urlopen(req, timeout=18).read().decode("utf-8", "ignore")
        except Exception as e:
            print("%-32s ERR %s" % (u, str(e)[:70]))
            continue
        print("=== %s  len=%d ===" % (u, len(b)))
        print("  timeline-item =", b.count("timeline-item"))
        print("  tweet-link    =", b.count("tweet-link"))
        ids = re.findall(r'href="(/[A-Za-z0-9_]+/status/(\d+))', b)
        uniq = []
        seen = set()
        for path, sid in ids:
            if sid in seen:
                continue
            seen.add(sid)
            uniq.append((path, sid))
        print("  status links  =", len(ids), "uniq =", len(uniq))
        for p, sid in uniq[:5]:
            print("    ", sid, "https://x.com" + p)
        # 媒体
        print("  video thumbs  =", b.count("amplify_video_thumb") + b.count("ext_tw_video_thumb"))
        print("  pics          =", len(re.findall(r'/pic/', b)))
