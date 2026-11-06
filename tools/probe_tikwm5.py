# -*- coding: utf-8 -*-
"""对比成功案例 @yc_x218 / 7702815583246552341 与失败案例 @lex.8889 / 7637720726061026578"""
import urllib.request, urllib.parse, gzip, json

UA = "Mozilla/5.0 (Linux; Android 12; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

def get(url, timeout=35, ua=UA):
    h = {"User-Agent": ua, "Accept": "application/json, text/plain, */*",
         "Accept-Encoding": "gzip", "Referer": "https://www.tikwm.com/", "Connection": "close"}
    try:
        r = urllib.request.urlopen(urllib.request.Request(url, headers=h), timeout=timeout)
        raw = r.read()
        if "gzip" in r.headers.get("Content-Encoding", ""):
            try: raw = gzip.decompress(raw)
            except Exception: pass
        return r.status, raw.decode("utf-8", "ignore")
    except Exception as e:
        return -1, "ERR " + str(e)[:110]

def tk(target):
    q = urllib.parse.quote(target, safe="")
    st, b = get("https://www.tikwm.com/api/?hd=1&url=%s" % q)
    return b

OK = ("yc_x218", "7702815583246552341")
BAD = ("lex.8889", "7637720726061026578")

print("=== 成功案例完整返回 ===")
b = tk("https://www.tiktok.com/@%s/photo/%s" % OK)
j = json.loads(b)
d = j.get("data") or {}
print("code", j.get("code"), "imgs", len(d.get("images") or []), "live", len(d.get("live_images") or []))
print("data keys:", list(d.keys()))
print("images[0]:", str((d.get("images") or [""])[0])[:130])
if d.get("live_images"):
    print("live_images[0]:", str(d["live_images"][0])[:130])
    print("live_images 里非空数:", sum(1 for x in d["live_images"] if x))
print("duration:", d.get("duration"), "author:", str(d.get("author"))[:80])

print()
print("=== 失败案例：直接用 tiktok 页面对比 ===")
for tag, (u, i) in [("OK", OK), ("BAD", BAD)]:
    st, h = get("https://www.tiktok.com/@%s/photo/%s" % (u, i), ua=UA)
    # 但这里 referer 不对，改一下
    h2 = {"User-Agent": UA, "Accept": "text/html,*/*", "Accept-Encoding": "gzip",
          "Referer": "https://www.tiktok.com/", "Connection": "close"}
    try:
        r = urllib.request.urlopen(urllib.request.Request("https://www.tiktok.com/@%s/photo/%s" % (u, i), headers=h2), timeout=30)
        raw = r.read()
        if "gzip" in r.headers.get("Content-Encoding", ""):
            try: raw = gzip.decompress(raw)
            except Exception: pass
        html = raw.decode("utf-8", "ignore")
        import re
        m = re.search(r'id="api-data"[^>]*>(.*?)</script>', html, re.S)
        api = m.group(1).strip() if m else ""
        sc = ""
        if api:
            try: sc = str(json.loads(api).get("videoDetail", {}).get("statusCode"))
            except Exception: pass
        print("  %-4s len=%-7d apidata=%-6d sc=%-6s ip=%s" % (tag, len(html), len(api), sc, "imagePost" in html))
    except Exception as e:
        print("  %-4s ERR %s" % (tag, str(e)[:80]))

print()
print("=== 失败案例再用 tikwm 试一次（可能是偶发）===")
for k in range(3):
    b = tk("https://www.tiktok.com/@%s/photo/%s" % BAD)
    print("  try%d: %s" % (k + 1, b[:110].replace("\n", " ")))

print()
print("=== 失败案例用 id 直接给 tikwm ===")
b = tk(BAD[1])
print("  :", b[:150].replace("\n", " "))