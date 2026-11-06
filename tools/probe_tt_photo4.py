# -*- coding: utf-8 -*-
"""看 statusCode / statusMessage / extra_info，以及视频页的 scope keys 对比"""
import urllib.request, gzip, json, re

ITEM = "7637720726061026578"
UA = "Mozilla/5.0 (Linux; Android 12; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

def get(url, ua=UA):
    h = {"User-Agent": ua, "Accept": "text/html,*/*", "Accept-Language": "en-US,en;q=0.9",
         "Accept-Encoding": "gzip", "Referer": "https://www.tiktok.com/", "Connection": "close"}
    try:
        r = urllib.request.urlopen(urllib.request.Request(url, headers=h), timeout=30)
        raw = r.read()
        if "gzip" in r.headers.get("Content-Encoding", ""):
            try: raw = gzip.decompress(raw)
            except Exception: pass
        return raw.decode("utf-8", "ignore")
    except Exception as e:
        return "ERR " + str(e)

h = get("https://www.tiktok.com/@lex.8889/photo/%s?_r=1&_t=abc" % ITEM)
m = re.search(r'id="__UNIVERSAL_DATA_FOR_REHYDRATION__"[^>]*>(.*?)</script>', h, re.S)
d = json.loads(m.group(1).strip())
scope = d.get("__DEFAULT_SCOPE__", {})
print("ALL KEYS:", list(scope.keys()))
det = scope.get("webapp.reflow.video.detail", {})
print("statusCode:", det.get("statusCode"))
print("statusMessage:", det.get("statusMessage"))
print("extra_info:", json.dumps(det.get("extra_info"))[:500])

print()
print("=== 视频页 scope keys ===")
h3 = get("https://www.tiktok.com/@united.state.3357/video/7688168881160080662")
m3 = re.search(r'id="__UNIVERSAL_DATA_FOR_REHYDRATION__"[^>]*>(.*?)</script>', h3, re.S)
d3 = json.loads(m3.group(1).strip())
s3 = d3.get("__DEFAULT_SCOPE__", {})
print("KEYS:", list(s3.keys()))
for k in s3:
    if "detail" in k:
        v = s3[k]
        print(k, "subkeys=", list(v.keys()) if isinstance(v, dict) else type(v))

print()
print("=== photo 页里出现的其他数据块 ===")
for pat in ["__NEXT_DATA__", "SIGI_STATE", "__MODERN_ROUTER_DATA__", "webapp.reflow.video",
            "webapp.photo", "photoDetail", "ImagePost", "imagePostInfo"]:
    print("  %-28s %s" % (pat, pat in h))