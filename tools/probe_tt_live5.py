# -*- coding: utf-8 -*-
"""把 imagePost 完整字段 dump 出来，看动轨到底叫什么名字"""
import urllib.request, gzip, json, re

UA = "Mozilla/5.0 (Linux; Android 12; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
REAL = "https://www.tiktok.com/@yingg433/photo/7599804244505398550"

def get(url, timeout=30):
    h = {"User-Agent": UA, "Accept": "text/html,*/*", "Accept-Encoding": "gzip",
         "Referer": "https://www.tiktok.com/", "Connection": "close"}
    r = urllib.request.urlopen(urllib.request.Request(url, headers=h), timeout=timeout)
    raw = r.read()
    if "gzip" in r.headers.get("Content-Encoding", ""):
        try: raw = gzip.decompress(raw)
        except Exception: pass
    return raw.decode("utf-8", "ignore")

h = get(REAL)
i = h.find('id="api-data"')
s = h.find(">", i); e = h.find("</script>", s)
body = h[s + 1:e].strip()
d = json.loads(body)
it = d["videoDetail"]["itemInfo"]["itemStruct"]

print("=== itemStruct 顶层 keys ===")
print(list(it.keys()))

ip = it.get("imagePost")
print()
print("=== imagePost keys ===", list(ip.keys()))
imgs = ip.get("images") or []
print("images 数量:", len(imgs))
print()
for i2, im in enumerate(imgs):
    print("--- image[%d] keys: %s" % (i2, list(im.keys())))
    for k, v in im.items():
        if k == "imageURL":
            ul = (v or {}).get("urlList") or []
            print("    imageURL.urlList: %d 条, [0]=%s" % (len(ul), ul[0][:100] if ul else ""))
        else:
            print("    %s = %s" % (k, json.dumps(v)[:400]))

print()
print("=== 整个 imagePost 原文（前 3000 字）===")
print(json.dumps(ip, ensure_ascii=False)[:3000])