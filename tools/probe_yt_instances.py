# -*- coding: utf-8 -*-
"""扫描 Piped / Invidious 公共实例，找能返回 YouTube 流地址的"""
import urllib.request, urllib.parse, gzip, json, socket, time

VID = "Do_LjDsisIE"
UA = "Mozilla/5.0 (Linux; Android 12; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

def get(url, timeout=12):
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
        return -1, "ERR " + str(e)[:80]

# Piped 实例（公开 API）
PIPED = [
    "https://pipedapi.kavin.rocks",
    "https://api.piped.private.coffee",
    "https://pipedapi.adminforge.de",
    "https://piped-api.lunar.icu",
    "https://pipedapi.reallyaweso.me",
    "https://api.piped.yt",
    "https://pipedapi.drgns.space",
    "https://pipedapi.leptons.xyz",
    "https://pipedapi.orangenet.cc",
]

# Invidious 实例
INVID = [
    "https://inv.nadeko.net",
    "https://yewtu.be",
    "https://invidious.nerdvpn.de",
    "https://inv.tux.pizza",
    "https://invidious.f5.si",
    "https://iv.melmac.space",
    "https://invidious.privacyredirect.com",
    "https://invidious.jing.rocks",
]

print("=" * 70)
print("=== Piped ===")
good_piped = []
for base in PIPED:
    st, b = get(base + "/streams/" + VID)
    ok = False
    info = ""
    try:
        j = json.loads(b)
        if j.get("title"):
            ok = True
            vs = j.get("videoStreams") or []
            has_url = sum(1 for x in vs if x.get("url"))
            info = "title=%s vstreams=%d 有url=%d" % (str(j.get("title"))[:26], len(vs), has_url)
            if has_url > 0: good_piped.append(base)
    except Exception:
        info = b[:60].replace("\n", " ")
    print("  %-42s st=%-4s %s" % (base.split("//")[1][:40], st, info))

print()
print("=" * 70)
print("=== Invidious ===")
good_inv = []
for base in INVID:
    st, b = get(base + "/api/v1/videos/" + VID)
    ok = False
    info = ""
    try:
        j = json.loads(b)
        if j.get("title"):
            ok = True
            af = j.get("adaptiveFormats") or []
            has_url = sum(1 for x in af if x.get("url"))
            info = "title=%s af=%d 有url=%d" % (str(j.get("title"))[:24], len(af), has_url)
            if has_url > 0: good_inv.append(base)
    except Exception:
        info = b[:60].replace("\n", " ")
    print("  %-42s st=%-4s %s" % (base.split("//")[1][:40], st, info))

print()
print("=" * 70)
print("=== 可用实例 ===")
print("  Piped:", good_piped if good_piped else "(无)")
print("  Invidious:", good_inv if good_inv else "(无)")
open("/tmp/yt_instances.txt", "w").write("PIPED:%s\nINVID:%s\n" % (good_piped, good_inv))