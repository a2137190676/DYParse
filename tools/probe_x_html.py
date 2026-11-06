# -*- coding: utf-8 -*-
"""确认 x.com 网页 HTML 里的 status 链接能不能用来提取主页作品"""
import urllib.request, re, json, sys

UA = ("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
      "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36")
name = sys.argv[1] if len(sys.argv) > 1 else "gomimata"

req = urllib.request.Request("https://x.com/%s" % name,
                             headers={"User-Agent": UA,
                                      "Accept": "text/html,application/xhtml+xml,*/*"})
s = urllib.request.urlopen(req, timeout=25).read().decode("utf-8", "ignore")
print("len", len(s))

# 所有 status 链接
ids = re.findall(r'/[A-Za-z0-9_]+/status/(\d{10,25})', s)
uniq = []
seen = set()
for i in ids:
    if i not in seen:
        seen.add(i)
        uniq.append(i)
print("status ids: total=%d uniq=%d" % (len(ids), len(uniq)))
for i in uniq[:12]:
    print("  ", i, "https://x.com/%s/status/%s" % (name, i))

# 媒体
print("video thumbs:", s.count("ext_tw_video_thumb") + s.count("amplify_video_thumb"))
print("pbs.twimg   :", s.count("pbs.twimg.com"))
print("video.twimg :", s.count("video.twimg.com"))

# 找 playable / mp4
for m in re.findall(r'https://[^"\\\s]+\.mp4[^"\\\s]*', s)[:3]:
    print("  mp4:", m[:110])

# 保存一份供人工检查
open("/tmp/x_profile.html", "w", encoding="utf-8").write(s)
print("saved /tmp/x_profile.html")