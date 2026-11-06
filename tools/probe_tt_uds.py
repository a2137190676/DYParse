# -*- coding: utf-8 -*-
"""解析 TikTok 主页 mobile UA 的 __UNIVERSAL_DATA_FOR_REHYDRATION__"""
import json, re, urllib.request, ssl

UA_MOB = ("Mozilla/5.0 (Linux; Android 13; SM-S918B Build/TP1A.220624.014; wv) "
          "AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/126.0.0.0 Mobile Safari/537.36")
ctx = ssl.create_default_context()
ctx.check_hostname = False
ctx.verify_mode = ssl.CERT_NONE

uid = "lightbertha"
url = "https://www.tiktok.com/@%s" % uid
req = urllib.request.Request(url, headers={
    "User-Agent": UA_MOB,
    "Accept": "text/html,application/xhtml+xml",
    "Accept-Language": "en-US,en;q=0.9",
})
with urllib.request.urlopen(req, timeout=30, context=ctx) as r:
    html = r.read().decode("utf-8", "ignore")

print("len", len(html))
open("/tmp/tt_prof.html", "w").write(html)

m = re.search(r'<script[^>]*id="__UNIVERSAL_DATA_FOR_REHYDRATION__"[^>]*>(.*?)</script>', html, re.S)
print("uds found:", bool(m))
if not m:
    raise SystemExit
raw = m.group(1)
print("raw len", len(raw))
d = json.loads(raw)
print("top keys:", list(d.keys()))
default = d.get("__DEFAULT_SCOPE__", {})
print("scope keys:", list(default.keys())[:20])

ud = default.get("webapp.user-detail", {})
print("user-detail keys:", list(ud.keys()))
ui = ud.get("userInfo", {})
print("userInfo keys:", list(ui.keys())[:15])
user = ui.get("user", {})
print("nickname:", user.get("nickname"), "uniqueId:", user.get("uniqueId"))
stats = ui.get("stats", {})
print("stats:", stats)

# 遍历寻找 itemList
def walk(o, path=""):
    if isinstance(o, dict):
        for k, v in o.items():
            if k in ("itemList", "itemStruct", "item_list"):
                print("HIT", path + "/" + k, type(v).__name__,
                      (len(v) if isinstance(v, (list, dict)) else v))
                if isinstance(v, list) and v:
                    print("   first keys:", list(v[0].keys())[:25] if isinstance(v[0], dict) else v[0])
            walk(v, path + "/" + k)
    elif isinstance(o, list):
        for i, v in enumerate(o[:5]):
            walk(v, path + "[%d]" % i)

walk(d)
