# -*- coding: utf-8 -*-
"""测新实况图短链 https://www.tiktok.com/t/ZPLHaK3hd/ ；并复测昨天那条"""
import urllib.request, urllib.parse, gzip, json, re

UA = "Mozilla/5.0 (Linux; Android 12; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

def raw_get(url, hdr=None, timeout=30, follow=True):
    h = {"User-Agent": UA, "Accept": "text/html,*/*", "Accept-Encoding": "gzip",
         "Referer": "https://www.tiktok.com/", "Connection": "close"}
    if hdr: h.update(hdr)
    try:
        r = urllib.request.urlopen(urllib.request.Request(url, headers=h), timeout=timeout)
        raw = r.read()
        if "gzip" in r.headers.get("Content-Encoding", ""):
            try: raw = gzip.decompress(raw)
            except Exception: pass
        return r.status, raw.decode("utf-8", "ignore"), r.geturl()
    except urllib.error.HTTPError as e:
        return e.code, "", url
    except Exception as e:
        return -1, "ERR " + str(e)[:110], url

def resolve(u):
    h = {"User-Agent": UA, "Accept": "text/html,*/*", "Accept-Encoding": "gzip",
         "Referer": "https://www.tiktok.com/", "Connection": "close"}
    cur = u
    for i in range(8):
        class NR(urllib.request.HTTPRedirectHandler):
            def redirect_request(self, *a, **k): return None
        op = urllib.request.build_opener(NR)
        try:
            r = op.open(urllib.request.Request(cur, headers=h), timeout=25)
            return cur
        except urllib.error.HTTPError as e:
            if 300 <= e.code < 400:
                loc = e.headers.get("Location")
                if not loc: return cur
                if loc.startswith("/"):
                    b = urllib.parse.urlparse(cur)
                    loc = b.scheme + "://" + b.netloc + loc
                cur = loc
                continue
            return cur
        except Exception:
            return cur
    return cur

def tikwm(target):
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
        return json.loads(raw.decode("utf-8", "ignore"))
    except Exception as e:
        return {"code": -99, "msg": str(e)[:90]}

def probe(tag, short):
    print("=" * 70)
    print(tag, short)
    real = resolve(short)
    print("  真实地址:", real)
    m = re.search(r'/(video|photo)/(\d{15,25})', real)
    kind, item = (m.group(1), m.group(2)) if m else ("?", "")
    print("  类型:", kind, "id:", item)

    # 页面
    st, html, _ = raw_get(real)
    mm = re.search(r'id="api-data"[^>]*>(.*?)</script>', html, re.S)
    api = mm.group(1).strip() if mm else ""
    sc = ""
    it = {}
    if api:
        try:
            dd = json.loads(api).get("videoDetail", {})
            sc = str(dd.get("statusCode"))
            it = (dd.get("itemInfo") or {}).get("itemStruct") or {}
        except Exception: pass
    print("  页面 len=%d apidata=%d sc=%s imagePost=%s duration=%s"
          % (len(html), len(api), sc, "imagePost" in html, (it.get("video") or {}).get("duration")))
    if it:
        print("  itemStruct keys:", list(it.keys())[:22])

    # tikwm video 路径
    for path in ("video", "photo"):
        if not item: continue
        url = "https://www.tiktok.com/@%s/%s/%s" % (
            (re.search(r'/@([\w.\-]+)/', real).group(1) if re.search(r'/@([\w.\-]+)/', real) else "i"),
            path, item)
        j = tikwm(url)
        if j.get("code") == 0:
            d = j.get("data") or {}
            imgs = d.get("images") or []
            lives = d.get("live_images") or d.get("liveImages") or []
            print("  tikwm %-6s ✅ imgs=%d live=%d 非空=%d dur=%s"
                  % (path, len(imgs), len(lives), sum(1 for x in lives if x), d.get("duration")))
            if imgs:
                print("      img[0]:", str(imgs[0])[:115])
            ne = [x for x in lives if x]
            if ne:
                print("      live[0]:", str(ne[0])[:115])
        else:
            print("  tikwm %-6s ❌ %s" % (path, str(j.get("msg"))[:70]))

probe("A. 昨天成功的那条（现在私密）", "https://vm.tiktok.com/ZGdXLoj4g/")
probe("B. 新的实况图", "https://www.tiktok.com/t/ZPLHaK3hd/")