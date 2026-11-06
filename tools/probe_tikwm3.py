# -*- coding: utf-8 -*-
"""确认 tikwm 对 photo 是整体不行，还是个别作品不行：多找几条 photo 作品"""
import urllib.request, urllib.parse, gzip, json, re, time

UA = "Mozilla/5.0 (Linux; Android 12; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

def get(url, timeout=35, hdr=None):
    h = {"User-Agent": UA, "Accept": "application/json, text/plain, */*",
         "Accept-Encoding": "gzip", "Referer": "https://www.tikwm.com/", "Connection": "close"}
    if hdr: h.update(hdr)
    try:
        r = urllib.request.urlopen(urllib.request.Request(url, headers=h), timeout=timeout)
        raw = r.read()
        if "gzip" in r.headers.get("Content-Encoding", ""):
            try: raw = gzip.decompress(raw)
            except Exception: pass
        return r.status, raw.decode("utf-8", "ignore")
    except Exception as e:
        return -1, "ERR " + str(e)[:110]

def tikwm(target):
    q = urllib.parse.quote(target, safe="")
    st, b = get("https://www.tikwm.com/api/?hd=1&url=%s" % q)
    try:
        j = json.loads(b)
        if j.get("code") == 0:
            d = j.get("data") or {}
            return True, "imgs=%d live=%d" % (len(d.get("images") or []),
                                              len(d.get("live_images") or d.get("liveImages") or []))
        return False, j.get("msg", "")[:50]
    except Exception:
        return False, b[:60]

print("=== 从 tikwm 的 hashtag / 搜索接口找 photo 作品 ===")
# tikwm 有 /api/search/ 之类，先试
for path in ["/api/search/general/?keyword=photodump&count=10",
             "/api/search/item/?keyword=photodump&count=10",
             "/api/hashtag/posts/?challengeID=photodump&count=10"]:
    st, b = get("https://www.tikwm.com" + path)
    print("  %-46s st=%s len=%s %s" % (path, st, len(b), b[:90].replace("\n", " ")))

print()
print("=== 用 www.tiktok.com/tag 页抓 photo 链接 ===")
st, h = get("https://www.tiktok.com/tag/photodump", hdr={"Accept": "text/html"})
ids = re.findall(r'/@([\w.\-]+)/photo/(\d{15,25})', h)
print("  tag 页 photo ids:", ids[:6], "len", len(h))
if not ids:
    # 用 tikwm 的 trending
    st2, b2 = get("https://www.tikwm.com/api/feed/list/?region=US&count=20")
    print("  tikwm feed st=%s len=%s %s" % (st2, len(b2), b2[:120]))
    try:
        j = json.loads(b2)
        for it in (j.get("data") or [])[:20]:
            if isinstance(it, dict):
                iid = str(it.get("video_id") or it.get("id") or "")
                uid = str(it.get("author", {}).get("unique_id") or "")
                if iid:
                    ok, info = tikwm("https://www.tiktok.com/@%s/video/%s" % (uid or "i", iid))
                    imgs = info if ok else ""
                    if ok and "imgs=0" not in imgs:
                        ids.append((uid, iid))
                        print("    found photo:", uid, iid, info)
                        if len(ids) >= 3: break
    except Exception as e:
        print("   feed parse err", e)

print()
print("=== 逐条测 photo ===")
for u, i in ids[:5]:
    ok, info = tikwm("https://www.tiktok.com/@%s/photo/%s" % (u, i))
    print("  @%-20s %-20s -> ok=%-5s %s" % (u, i, ok, info))
