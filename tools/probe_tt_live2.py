# -*- coding: utf-8 -*-
"""看这条到底是不是 photo 作品，tikwm 完整返回 + 页面结构"""
import urllib.request, urllib.parse, gzip, json, re

UA = "Mozilla/5.0 (Linux; Android 12; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
ITEM = "7617454202062720274"
USER = "silkysmooth425"

def get(url, hdr=None, timeout=30):
    h = {"User-Agent": UA, "Accept": "*/*", "Accept-Encoding": "gzip",
         "Referer": "https://www.tiktok.com/", "Connection": "close"}
    if hdr: h.update(hdr)
    try:
        r = urllib.request.urlopen(urllib.request.Request(url, headers=h), timeout=timeout)
        raw = r.read()
        if "gzip" in r.headers.get("Content-Encoding", ""):
            try: raw = gzip.decompress(raw)
            except Exception: pass
        return r.status, raw.decode("utf-8", "ignore")
    except Exception as e:
        return -1, "ERR " + str(e)[:100]

print("=== tikwm 完整返回 ===")
q = urllib.parse.quote("https://www.tiktok.com/@%s/video/%s" % (USER, ITEM), safe="")
st, b = get("https://www.tikwm.com/api/?hd=1&url=%s" % q)
j = json.loads(b)
d = j.get("data") or {}
for k, v in d.items():
    if k in ("author", "music_info", "music"):
        print("  %-18s %s" % (k, str(v)[:100]))
    else:
        print("  %-18s %s" % (k, str(v)[:140]))

print()
print("=== 页面里有没有 imagePost / photomode ===")
st, h = get("https://www.tiktok.com/@%s/photo/%s" % (USER, ITEM))
print("  photo 路径 len=%d ip=%s photoMode=%s" % (len(h), "imagePost" in h, "photomode" in h.lower()))
m = re.search(r'id="api-data"[^>]*>(.*?)</script>', h, re.S)
if m:
    print("  apidata:", m.group(1).strip()[:200])

st, h2 = get("https://www.tiktok.com/@%s/video/%s" % (USER, ITEM))
print("  video 路径 len=%d ip=%s" % (len(h2), "imagePost" in h2))
m2 = re.search(r'id="api-data"[^>]*>(.*?)</script>', h2, re.S)
if m2:
    api = m2.group(1).strip()
    print("  apidata len=%d" % len(api))
    try:
        dd = json.loads(api).get("videoDetail", {})
        print("    statusCode:", dd.get("statusCode"))
        it = (dd.get("itemInfo") or {}).get("itemStruct") or {}
        print("    id:", it.get("id"))
        print("    keys:", list(it.keys()))
        print("    imagePost:", str(it.get("imagePost"))[:200])
        print("    video.duration:", (it.get("video") or {}).get("duration"))
    except Exception as e:
        print("    err", e, api[:150])