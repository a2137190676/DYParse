# -*- coding: utf-8 -*-
"""验证 Invidious 返回的 url 能不能真的拉到数据"""
import urllib.request, gzip, json, re

VID = "Do_LjDsisIE"
BASE = "https://yt.chocolatemoo53.com"
UA = "Mozilla/5.0 (Linux; Android 12; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

def get(url, timeout=20, hdr=None, method="GET", data=None, limit=None):
    h = {"User-Agent": UA, "Accept": "*/*", "Accept-Encoding": "gzip", "Connection": "close"}
    if hdr: h.update(hdr)
    try:
        req = urllib.request.Request(url, headers=h, data=data, method=method)
        r = urllib.request.urlopen(req, timeout=timeout)
        if limit:
            raw = r.read(limit)
        else:
            raw = r.read()
            if "gzip" in str(r.headers.get("Content-Encoding", "")):
                try: raw = gzip.decompress(raw)
                except Exception: pass
        return r.status, raw, dict(r.headers)
    except Exception as e:
        return -1, b"", {"err": str(e)[:90]}

st, raw, hd = get(BASE + "/api/v1/videos/" + VID)
j = json.loads(raw.decode("utf-8", "ignore"))
print("=== 视频元数据 ===")
print("  title:", str(j.get("title"))[:70])
print("  author:", j.get("author"))
print("  lengthSeconds:", j.get("lengthSeconds"))
print("  viewCount:", j.get("viewCount"))
print("  liveNow:", j.get("liveNow"))
af = j.get("adaptiveFormats") or []
fmt = j.get("formatStreams") or []
print("  adaptiveFormats:", len(af), " formatStreams:", len(fmt))

print()
print("=== 挑最优档 ===")
def score(f):
    try:
        return int(f.get("bitrate") or 0)
    except Exception:
        return 0

vids = [f for f in af if str(f.get("type", "")).startswith("video")]
auds = [f for f in af if str(f.get("type", "")).startswith("audio")]
vids.sort(key=score, reverse=True)
auds.sort(key=score, reverse=True)
print("  视频轨:")
for f in vids[:4]:
    print("    itag=%-4s %-28s %-10s bitrate=%s len=%s" % (
        f.get("itag"), str(f.get("type"))[:26], f.get("resolution") or f.get("qualityLabel"),
        f.get("bitrate"), f.get("clen")))
print("  音频轨:")
for f in auds[:3]:
    print("    itag=%-4s %-40s bitrate=%s len=%s" % (
        f.get("itag"), str(f.get("type"))[:38], f.get("bitrate"), f.get("clen")))
print("  合一路径 formatStreams:")
for f in fmt[:3]:
    print("    itag=%-4s %-26s %-8s len=%s" % (
        f.get("itag"), str(f.get("type"))[:24], f.get("resolution"), f.get("clen")))

print()
print("=== 实测 Range 拉流（前 200KB）===")
def test_range(name, u):
    if not u: print("  %-12s (无 url)" % name); return
    st2, data, hd2 = get(u, timeout=25, hdr={"Range": "bytes=0-204799"}, limit=204800)
    print("  %-12s st=%-4s 收到=%-7d Content-Range=%s" % (
        name, st2, len(data), hd2.get("Content-Range") or hd2.get("err") or "-"))

if vids:
    test_range("最优视频", vids[0].get("url"))
if auds:
    test_range("最优音频", auds[0].get("url"))
if fmt:
    test_range("合一 mp4", fmt[0].get("url"))

print()
print("=== 该实例其他接口 ===")
for p in ["/api/v1/trending?region=US", "/api/v1/search?q=test"]:
    st3, d3, _ = get(BASE + p, timeout=15)
    print("  %-34s st=%s len=%d" % (p, st3, len(d3)))

print()
print("=== 再看 captions / 是否支持 ===")
print("  captions:", len(j.get("captions") or []))
print("  hlsUrl:", str(j.get("hlsUrl"))[:100] if j.get("hlsUrl") else "无")