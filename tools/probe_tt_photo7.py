# -*- coding: utf-8 -*-
"""带 cookie（先访问首页拿 ttwid）再请求 photo 页"""
import urllib.request, http.cookiejar, gzip, json, re

ITEM = "7637720726061026578"
USER = "lex.8889"
UA = "Mozilla/5.0 (Linux; Android 12; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

cj = http.cookiejar.CookieJar()
op = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(cj))

def get(url, timeout=30):
    h = {"User-Agent": UA,
         "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
         "Accept-Language": "en-US,en;q=0.9",
         "Accept-Encoding": "gzip",
         "Referer": "https://www.tiktok.com/",
         "Connection": "close"}
    try:
        r = op.open(urllib.request.Request(url, headers=h), timeout=timeout)
        raw = r.read()
        if "gzip" in r.headers.get("Content-Encoding", ""):
            try: raw = gzip.decompress(raw)
            except Exception: pass
        return r.status, raw.decode("utf-8", "ignore")
    except Exception as e:
        return -1, "ERR " + str(e)[:120]

print("=== 1. 先访问首页拿 cookie ===")
st, h0 = get("https://www.tiktok.com/")
print("home", st, "len", len(h0), "cookies", [c.name for c in cj])

print("=== 2. 再请求 photo 页 ===")
st, h = get("https://www.tiktok.com/@%s/photo/%s" % (USER, ITEM))
print("photo", st, "len", len(h), "cookies", [c.name for c in cj])
print("hasImagePost", "imagePost" in h)

m = re.search(r'id="__UNIVERSAL_DATA_FOR_REHYDRATION__"[^>]*>(.*?)</script>', h, re.S)
if m:
    try:
        d = json.loads(m.group(1).strip())
        scope = d.get("__DEFAULT_SCOPE__", {})
        print("keys:", list(scope.keys()))
        for k in scope:
            if "detail" not in k: continue
            v = scope[k]
            print(" ", k, "sub=", list(v.keys()) if isinstance(v, dict) else type(v))
            if isinstance(v, dict):
                print("     statusCode=", v.get("statusCode"), "msg=", v.get("statusMessage"))
                it = (v.get("itemInfo") or {}).get("itemStruct")
                if isinstance(it, dict):
                    print("     id=", it.get("id"), "hasImagePost=", "imagePost" in it, "keys=", list(it.keys())[:18])
    except Exception as e:
        print("JSON_ERR", e)
else:
    print("NO UDR")

print("=== 3. 用 cookie 试 API ===")
st, b = get("https://www.tiktok.com/api/photo/detail/?itemId=%s&aid=1988&app_name=tiktok_web&device_platform=web_pc&channel=tiktok_web&cookie_enabled=true" % ITEM)
print("photo/detail", st, len(b), b[:300].replace("\n", " "))