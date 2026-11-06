# -*- coding: utf-8 -*-
"""抓 TikTok photo 作品页，看 __UNIVERSAL_DATA_FOR_REHYDRATION__ 里的 scope key"""
import urllib.request, gzip, io, json, sys

UA = "Mozilla/5.0 (Linux; Android 12; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
URL = "https://www.tiktok.com/@lex.8889/photo/7637720726061026578"

req = urllib.request.Request(URL, headers={
    "User-Agent": UA,
    "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
    "Accept-Language": "en-US,en;q=0.9,zh-CN;q=0.8",
    "Referer": "https://www.tiktok.com/",
})
try:
    r = urllib.request.urlopen(req, timeout=30)
    raw = r.read()
    if r.headers.get("Content-Encoding") == "gzip":
        raw = gzip.decompress(raw)
    html = raw.decode("utf-8", "ignore")
    print("HTTP", r.status, "len", len(html))
except Exception as e:
    print("ERR", e)
    sys.exit(1)

open("/tmp/tt_photo.html", "w", encoding="utf-8").write(html)
print("hasUniversal", "__UNIVERSAL_DATA_FOR_REHYDRATION__" in html)
print("hasApiData", 'id="api-data"' in html)
print("hasImagePost", "imagePost" in html)
print("hasImageURL", "imageURL" in html)
print("hasSlider", "slider" in html) 
print("hasPhotoDetail", "photo.detail" in html)
print("hasVideoDetail", "video.detail" in html)
print("hasPhotoMode", "photoMode" in html)
print("hasSigiState", "SIGI_STATE" in html)

# 抠 __UNIVERSAL_DATA_FOR_REHYDRATION__
import re
m = re.search(r'id="__UNIVERSAL_DATA_FOR_REHYDRATION__"[^>]*>(.*?)</script>', html, re.S)
if m:
    body = m.group(1).strip()
    print("UDR len", len(body))
    try:
        d = json.loads(body)
        scope = d.get("__DEFAULT_SCOPE__", {})
        print("SCOPE KEYS:", list(scope.keys()))
        for k in scope:
            if "detail" in k:
                it = scope[k].get("itemInfo", {}).get("itemStruct", {})
                print("  ", k, "-> id=", it.get("id"), "hasImagePost=", "imagePost" in it)
    except Exception as e:
        print("UDR_ERR", e, body[:200])
else:
    print("NO UDR")

m2 = re.search(r'id="api-data"[^>]*>(.*?)</script>', html, re.S)
if m2:
    body = m2.group(1).strip()
    print("APIDATA len", len(body))
    try:
        d = json.loads(body)
        it = d.get("itemInfo", {}).get("itemStruct", {})
        print("  id=", it.get("id"), "hasImagePost=", "imagePost" in it, "keys=", list(it.keys())[:20])
    except Exception as e:
        print("APIDATA_ERR", e, body[:200])
else:
    print("NO APIDATA")
