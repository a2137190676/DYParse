# -*- coding: utf-8 -*-
"""跟短链拿真实地址，再测 tikwm photo 解析"""
import urllib.request, urllib.parse, gzip, json

UA = "Mozilla/5.0 (Linux; Android 12; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
SHORT = "https://www.tiktok.com/t/ZPLH5K841/"

def resolve_short(u):
    h = {"User-Agent": UA, "Accept": "text/html,*/*", "Accept-Encoding": "gzip",
         "Referer": "https://www.tiktok.com/", "Connection": "close"}
    cur = u
    for i in range(8):
        req = urllib.request.Request(cur, headers=h)
        # 不自动跟随，手动看 Location
        class NoRedir(urllib.request.HTTPRedirectHandler):
            def redirect_request(self, *a, **k): return None
        op = urllib.request.build_opener(NoRedir)
        try:
            r = op.open(req, timeout=25)
            return cur, r.status
        except urllib.error.HTTPError as e:
            if 300 <= e.code < 400:
                loc = e.headers.get("Location")
                if not loc: return cur, e.code
                if loc.startswith("/"):
                    b = urllib.parse.urlparse(cur)
                    loc = b.scheme + "://" + b.netloc + loc
                print("  跳转 %d -> %s" % (e.code, loc[:120]))
                cur = loc
                continue
            return cur, e.code
        except Exception as e:
            return cur, "ERR " + str(e)[:60]
    return cur, "MAX"

print("=== 1. 跟短链 ===")
real, st = resolve_short(SHORT)
print("  真实地址:", real)
print("  status:", st)

item = ""
import re
m = re.search(r'/(?:video|photo)/(\d{15,25})', real)
if m: item = m.group(1)
print("  作品ID:", item)

print()
print("=== 2. 直接抓页面看 statusCode ===")
def get(url, hdr=None, timeout=30):
    h = {"User-Agent": UA, "Accept": "text/html,*/*", "Accept-Encoding": "gzip",
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

st2, html = get(real)
mm = re.search(r'id="api-data"[^>]*>(.*?)</script>', html, re.S)
api = mm.group(1).strip() if mm else ""
sc = ""
if api:
    try: sc = str(json.loads(api).get("videoDetail", {}).get("statusCode"))
    except Exception: pass
print("  len=%d apidata=%d sc=%s imagePost=%s" % (len(html), len(api), sc, "imagePost" in html))

print()
print("=== 3. tikwm 解析 ===")
def tk(target):
    q = urllib.parse.quote(target, safe="")
    h = {"User-Agent": UA, "Accept": "application/json,*/*", "Accept-Encoding": "gzip",
         "Referer": "https://www.tikwm.com/", "Connection": "close"}
    try:
        r = urllib.request.urlopen(urllib.request.Request(
            "https://www.tikwm.com/api/?hd=1&url=%s" % q, headers=h), timeout=35)
        raw = r.read()
        if "gzip" in r.headers.get("Content-Encoding", ""):
            try: raw = gzip.decompress(raw)
            except Exception: pass
        return raw.decode("utf-8", "ignore")
    except Exception as e:
        return "ERR " + str(e)[:100]

b = tk(real)
print("  原始:", b[:200].replace("\n", " "))
try:
    j = json.loads(b)
    if j.get("code") == 0:
        d = j.get("data") or {}
        imgs = d.get("images") or []
        lives = d.get("live_images") or d.get("liveImages") or []
        print("  ✅ code=0 imgs=%d live=%d" % (len(imgs), len(lives)))
        print("  live 非空数:", sum(1 for x in lives if x))
        for i, u in enumerate(imgs[:3]):
            print("    img[%d] %s" % (i, u[:120]))
        for i, u in enumerate(lives[:3]):
            print("    live[%d] %s" % (i, (u or "(空)")[:120]))
        print("  id:", d.get("id"), "author:", (d.get("author") or {}).get("unique_id"))
    else:
        print("  ❌", j.get("msg"))
except Exception as e:
    print("  parse err", e)