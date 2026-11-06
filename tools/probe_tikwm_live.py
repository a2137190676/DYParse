# -*- coding: utf-8 -*-
"""扫 tikwm feed 找 live_images 非空的真·实况图作品，验证 tikwm 这条路通不通"""
import urllib.request, urllib.parse, gzip, json, time

UA = "Mozilla/5.0 (Linux; Android 12; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

def get(url, timeout=35):
    h = {"User-Agent": UA, "Accept": "application/json,*/*", "Accept-Encoding": "gzip",
         "Referer": "https://www.tikwm.com/", "Connection": "close"}
    try:
        r = urllib.request.urlopen(urllib.request.Request(url, headers=h), timeout=timeout)
        raw = r.read()
        if "gzip" in r.headers.get("Content-Encoding", ""):
            try: raw = gzip.decompress(raw)
            except Exception: pass
        return r.status, raw.decode("utf-8", "ignore")
    except Exception as e:
        return -1, "ERR " + str(e)[:110]

def tk_detail(target):
    q = urllib.parse.quote(target, safe="")
    st, b = get("https://www.tikwm.com/api/?hd=1&url=%s" % q)
    try:
        j = json.loads(b)
        if j.get("code") != 0: return None
        return j.get("data") or {}
    except Exception:
        return None

# 收集候选 id
cands = []
for region in ["US", "GB", "JP", "KR", "TW", "FR", "DE", "AU", "CA", "BR"]:
    st, b = get("https://www.tikwm.com/api/feed/list/?region=%s&count=30" % region)
    try:
        items = json.loads(b).get("data") or []
    except Exception:
        print(region, "err", b[:70]); continue
    for it in items:
        if isinstance(it, dict) and it.get("video_id"):
            uid = str((it.get("author") or {}).get("unique_id") or "i")
            cands.append(("https://www.tiktok.com/@%s/video/%s" % (uid, it["video_id"]),
                          "https://www.tiktok.com/@%s/photo/%s" % (uid, it["video_id"])))
    print("%-3s +%-3d 累计 %d" % (region, len(items), len(cands)))
    if len(cands) >= 60: break
    time.sleep(0.5)

print()
print("=== 逐个查 live_images ===")
hit = 0
for i, (vurl, purl) in enumerate(cands[:40]):
    d = tk_detail(vurl)
    if not d: continue
    live = d.get("live_images") or d.get("liveImages") or []
    imgs = d.get("images") or []
    nonempty = sum(1 for x in live if x)
    if nonempty > 0:
        hit += 1
        print("  ✅ %s" % vurl)
        print("     imgs=%d live=%d 非空=%d duration=%s" % (len(imgs), len(live), nonempty, d.get("duration")))
        print("     img[0]:", str(imgs[0])[:110] if imgs else "(无)")
        print("     live[0]:", str([x for x in live if x][0])[:110])
        # 顺便测 photo 路径
        d2 = tk_detail(purl)
        if d2:
            l2 = d2.get("live_images") or []
            print("     photo路径 -> imgs=%d live非空=%d" % (len(d2.get("images") or []),
                                                            sum(1 for x in l2 if x)))
        if hit >= 3: break
    time.sleep(0.3)

if hit == 0:
    print("  前 40 条里没找到 live_images 非空的")