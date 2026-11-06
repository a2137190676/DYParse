# -*- coding: utf-8 -*-
"""TikTok photo 作品：测第三方接口能否拿到图集 / 实况动轨"""
import urllib.request, urllib.parse, gzip, json

ITEM = "7637720726061026578"
USER = "lex.8889"
SHARE = "https://www.tiktok.com/@%s/photo/%s" % (USER, ITEM)
UA = "Mozilla/5.0 (Linux; Android 12; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

def get(url, ua=UA, timeout=30):
    h = {"User-Agent": ua, "Accept": "*/*", "Accept-Encoding": "gzip",
         "Referer": "https://www.tiktok.com/", "Connection": "close"}
    try:
        r = urllib.request.urlopen(urllib.request.Request(url, headers=h), timeout=timeout)
        raw = r.read()
        if "gzip" in r.headers.get("Content-Encoding", ""):
            try: raw = gzip.decompress(raw)
            except Exception: pass
        return r.status, raw.decode("utf-8", "ignore")
    except Exception as e:
        return -1, "ERR " + str(e)

def show(tag, st, body, limit=700):
    print("--- %s status=%s len=%s" % (tag, st, len(body)))
    print(body[:limit].replace("\n", " "))
    print()

q = urllib.parse.quote(SHARE, safe="")

# 1. tikwm
st, b = get("https://www.tikwm.com/api/?url=%s&hd=1" % q)
show("tikwm /api", st, b)
try:
    j = json.loads(b)
    d = j.get("data") or {}
    print("    tikwm keys:", list(d.keys()))
    print("    images:", len(d.get("images") or []))
    if d.get("images"):
        for i, im in enumerate(d["images"][:4]):
            print("      img[%d] %s" % (i, im[:110]))
finally:
    pass

# 2. tikwm 的 photo 专用
st, b = get("https://www.tikwm.com/api/photo/?url=%s" % q)
show("tikwm /api/photo", st, b, 400)

# 3. oembed
st, b = get("https://www.tiktok.com/oembed?url=%s" % q)
show("tiktok oembed", st, b, 500)

# 4. 备用聚合站
st, b = get("https://api.douyin.wtf/api/hybrid/video_data?url=%s" % q)
show("douyin.wtf hybrid", st, b, 400)