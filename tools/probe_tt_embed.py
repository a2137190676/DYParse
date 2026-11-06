# -*- coding: utf-8 -*-
"""分析 tiktok embed 页结构"""
import json, re, urllib.request, ssl

UA_MOB = ("Mozilla/5.0 (Linux; Android 13; SM-S918B Build/TP1A.220624.014; wv) "
          "AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/126.0.0.0 Mobile Safari/537.36")
ctx = ssl.create_default_context()
ctx.check_hostname = False
ctx.verify_mode = ssl.CERT_NONE

uid = "lightbertha"
url = "https://www.tiktok.com/embed/@%s" % uid
req = urllib.request.Request(url, headers={
    "User-Agent": UA_MOB, "Accept-Language": "en-US,en;q=0.9",
    "Referer": "https://www.tiktok.com/",
})
with urllib.request.urlopen(req, timeout=30, context=ctx) as r:
    html = r.read().decode("utf-8", "ignore")
print("len", len(html))
open("/tmp/tt_embed.html", "w").write(html)

for k in ["__UNIVERSAL_DATA_FOR_REHYDRATION__", "itemStruct", "playAddr",
          "cover", "video/", "aweme_id", "id_str", "embedItemStruct"]:
    print("  %-38s %d" % (k, html.count(k)))

print("\n--- script id 列表 ---")
for m in re.finditer(r'<script[^>]*id="([^"]+)"[^>]*>', html):
    print("  ", m.group(1))

print("\n--- playAddr 上下文 ---")
i = html.find("playAddr")
if i > 0:
    print(html[max(0, i - 600):i + 400].replace("\\", "\\\\")[:1000])

print("\n--- 作品链接 ---")
links = set(re.findall(r'/video/(\d{15,25})', html))
print("ids:", len(links), list(links)[:8])
links2 = set(re.findall(r'/photo/(\d{15,25})', html))
print("photo ids:", len(links2), list(links2)[:8])