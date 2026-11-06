# -*- coding: utf-8 -*-
"""从官方实例列表拉最新的，再扫一遍"""
import urllib.request, gzip, json

VID = "Do_LjDsisIE"
UA = "Mozilla/5.0 (Linux; Android 12; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

def get(url, timeout=15):
    h = {"User-Agent": UA, "Accept": "application/json,*/*", "Accept-Encoding": "gzip",
         "Connection": "close"}
    try:
        r = urllib.request.urlopen(urllib.request.Request(url, headers=h), timeout=timeout)
        raw = r.read()
        if "gzip" in r.headers.get("Content-Encoding", ""):
            try: raw = gzip.decompress(raw)
            except Exception: pass
        return r.status, raw.decode("utf-8", "ignore")
    except Exception as e:
        return -1, "ERR " + str(e)[:70]

print("=== 拉 Piped 官方实例列表 ===")
st, b = get("https://piped-instances.kavin.rocks/")
piped = []
if st == 200:
    try:
        arr = json.loads(b)
        for it in arr:
            api = it.get("api_url") or ""
            if api and it.get("uptime_24h", 0) > 60:
                piped.append((api, it.get("uptime_24h")))
        piped.sort(key=lambda x: -x[1])
        print("  在线实例:", len(piped))
        for a, u in piped[:12]:
            print("    %-45s uptime=%.1f%%" % (a, u))
    except Exception as e:
        print("  parse err", e, b[:100])
else:
    print("  列表拉不到:", b[:80])

print()
print("=== 拉 Invidious 官方实例列表 ===")
st2, b2 = get("https://api.invidious.io/instances.json?sort_by=health")
invid = []
if st2 == 200:
    try:
        arr = json.loads(b2)
        for name, info in arr:
            if info.get("type") != "https": continue
            if not info.get("api"): continue
            uri = info.get("uri")
            if uri: invid.append(uri)
        print("  标记 api 可用的:", len(invid))
        for u in invid[:12]:
            print("    ", u)
    except Exception as e:
        print("  parse err", e, b2[:100])
else:
    print("  列表拉不到:", b2[:80])

print()
print("=== 逐个实测 Piped（前 10 个）===")
good = []
for api, up in piped[:10]:
    st3, b3 = get(api + "/streams/" + VID, timeout=12)
    info = ""
    try:
        j = json.loads(b3)
        vs = j.get("videoStreams") or []
        hu = sum(1 for x in vs if x.get("url"))
        info = "vstreams=%d 有url=%d title=%s" % (len(vs), hu, str(j.get("title"))[:22])
        if hu > 0: good.append(api)
    except Exception:
        info = b3[:50].replace("\n", " ")
    print("  %-42s st=%-4s %s" % (api.split("//")[1][:40], st3, info))

print()
print("=== 逐个实测 Invidious（前 10 个）===")
good2 = []
for u in invid[:10]:
    st4, b4 = get(u + "/api/v1/videos/" + VID, timeout=12)
    info = ""
    try:
        j = json.loads(b4)
        af = j.get("adaptiveFormats") or []
        hu = sum(1 for x in af if x.get("url"))
        info = "af=%d 有url=%d title=%s" % (len(af), hu, str(j.get("title"))[:22])
        if hu > 0: good2.append(u)
    except Exception:
        info = b4[:50].replace("\n", " ")
    print("  %-42s st=%-4s %s" % (u.split("//")[1][:40], st4, info))

print()
print("=" * 60)
print("  可用 Piped:", good if good else "(无)")
print("  可用 Invidious:", good2 if good2 else "(无)")