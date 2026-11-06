# -*- coding: utf-8 -*-
"""从作者主页找 photo 作品，批量测 statusCode"""
import urllib.request, http.cookiejar, gzip, json, re

UA = "Mozilla/5.0 (Linux; Android 12; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
cj = http.cookiejar.CookieJar()
op = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(cj))

def get(url, timeout=30):
    h = {"User-Agent": UA, "Accept": "text/html,*/*", "Accept-Language": "en-US,en;q=0.9",
         "Accept-Encoding": "gzip", "Referer": "https://www.tiktok.com/", "Connection": "close"}
    try:
        r = op.open(urllib.request.Request(url, headers=h), timeout=timeout)
        raw = r.read()
        if "gzip" in r.headers.get("Content-Encoding", ""):
            try: raw = gzip.decompress(raw)
            except Exception: pass
        return r.status, raw.decode("utf-8", "ignore")
    except Exception as e:
        return -1, "ERR " + str(e)[:100]

get("https://www.tiktok.com/")

st, h = get("https://www.tiktok.com/@lex.8889")
print("profile len", len(h))
photos = re.findall(r'/@([\w.\-]+)/photo/(\d{15,25})', h)
videos = re.findall(r'/@([\w.\-]+)/video/(\d{15,25})', h)
print("photo ids:", photos[:6])
print("video ids:", videos[:3])

# UDR 里 itemList 有 photo
m = re.search(r'id="__UNIVERSAL_DATA_FOR_REHYDRATION__"[^>]*>(.*?)</script>', h, re.S)
if m:
    try:
        d = json.loads(m.group(1).strip())
        scope = d.get("__DEFAULT_SCOPE__", {})
        print("scope keys:", list(scope.keys()))
        txt = m.group(1)
        # 找 imagePost 出现
        print("has imagePost in UDR:", "imagePost" in txt)
        ids = re.findall(r'"id":"(\d{15,25})"', txt)
        print("ids in UDR:", len(ids))
    except Exception as e:
        print("err", e)

# 测这些 photo
for u, i in photos[:5]:
    st2, h2 = get("https://www.tiktok.com/@%s/photo/%s" % (u, i))
    api = ""
    m2 = re.search(r'id="api-data"[^>]*>(.*?)</script>', h2, re.S)
    if m2: api = m2.group(1).strip()
    sc = "?"
    if api:
        try: sc = str(json.loads(api).get("videoDetail", {}).get("statusCode"))
        except Exception: pass
    print("  photo %s sc=%-6s len=%-7d ip=%s" % (i, sc, len(h2), "imagePost" in h2))