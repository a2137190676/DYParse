# -*- coding: utf-8 -*-
"""扫 tikwm feed 找真正的多图/实况作品，验证 tikwm 现在还能不能解 photo"""
import urllib.request, urllib.parse, gzip, json, time

UA = "Mozilla/5.0 (Linux; Android 12; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

def get(url, timeout=35):
    h = {"User-Agent": UA, "Accept": "application/json, text/plain, */*",
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

# 扫多个 region 的 feed，找 images 长度 > 1 的
found = []
for region in ["US", "GB", "JP", "KR", "TW", "FR"]:
    st, b = get("https://www.tikwm.com/api/feed/list/?region=%s&count=30" % region)
    try:
        j = json.loads(b)
        items = j.get("data") or []
    except Exception:
        print(region, "err", b[:80]); continue
    for it in items:
        if not isinstance(it, dict): continue
        iid = str(it.get("video_id") or "")
        uid = str((it.get("author") or {}).get("unique_id") or "")
        imgs = it.get("images")
        if iid and isinstance(imgs, list) and len(imgs) > 1:
            found.append((uid, iid, len(imgs)))
    print("%-3s items=%-3d 累计多图=%d" % (region, len(items), len(found)))
    if len(found) >= 4: break
    time.sleep(1)

print()
print("=== 找到的多图作品 ===")
for u, i, n in found[:6]:
    print("  @%-20s %-20s imgs=%d" % (u, i, n))

print()
print("=== 逐条用 tikwm 精确解析 ===")
for u, i, n in found[:4]:
    target = "https://www.tiktok.com/@%s/photo/%s" % (u, i)
    q = urllib.parse.quote(target, safe="")
    st, b = get("https://www.tikwm.com/api/?hd=1&url=%s" % q)
    try:
        j = json.loads(b)
        if j.get("code") == 0:
            d = j.get("data") or {}
            print("  %-20s OK imgs=%d live=%d" % (i, len(d.get("images") or []),
                                                   len(d.get("live_images") or d.get("liveImages") or [])))
        else:
            print("  %-20s FAIL %s" % (i, j.get("msg", "")[:60]))
    except Exception:
        print("  %-20s ERR %s" % (i, b[:70]))
    # 也试 /video/ 路径
    target2 = "https://www.tiktok.com/@%s/video/%s" % (u, i)
    q2 = urllib.parse.quote(target2, safe="")
    st2, b2 = get("https://www.tikwm.com/api/?hd=1&url=%s" % q2)
    try:
        j2 = json.loads(b2)
        if j2.get("code") == 0:
            d2 = j2.get("data") or {}
            print("       (video路径) OK imgs=%d live=%d" % (len(d2.get("images") or []),
                                                              len(d2.get("live_images") or d2.get("liveImages") or [])))
        else:
            print("       (video路径) FAIL %s" % j2.get("msg", "")[:50])
    except Exception:
        print("       (video路径) ERR")