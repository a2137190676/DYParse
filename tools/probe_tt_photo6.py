# -*- coding: utf-8 -*-
"""TikTok photo：逐个接口打，错误不影响后面"""
import urllib.request, urllib.parse, gzip, json

ITEM = "7637720726061026578"
USER = "lex.8889"
SHARE = "https://www.tiktok.com/@%s/photo/%s" % (USER, ITEM)
UA = "Mozilla/5.0 (Linux; Android 12; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
q = urllib.parse.quote(SHARE, safe="")

def get(url, ua=UA, timeout=25, ref="https://www.tiktok.com/"):
    h = {"User-Agent": ua, "Accept": "*/*", "Accept-Encoding": "gzip",
         "Referer": ref, "Connection": "close"}
    try:
        r = urllib.request.urlopen(urllib.request.Request(url, headers=h), timeout=timeout)
        raw = r.read()
        if "gzip" in r.headers.get("Content-Encoding", ""):
            try: raw = gzip.decompress(raw)
            except Exception: pass
        return r.status, raw.decode("utf-8", "ignore")
    except Exception as e:
        return -1, "ERR " + str(e)[:120]

def show(tag, st, body, limit=450):
    print("--- %s status=%s len=%s" % (tag, st, len(body)))
    print("   ", body[:limit].replace("\n", " "))
    return body

tests = [
  ("oembed",        "https://www.tiktok.com/oembed?url=%s" % q),
  ("tikwm_api",     "https://www.tikwm.com/api/?url=%s&hd=1" % q),
  ("tikwm_photo",   "https://www.tikwm.com/api/photo/?url=%s" % q),
  ("tikwm_nohd",    "https://tikwm.com/api/?url=%s" % q),
  ("douyinwtf",     "https://api.douyin.wtf/api/hybrid/video_data?url=%s" % q),
  ("snapany",       "https://api.snapany.com/v1/extract?link=%s" % q),
]
for tag, u in tests:
    st, b = get(u)
    show(tag, st, b)