# -*- coding: utf-8 -*-
"""实测手机本地直连 YouTube 单视频的可能性"""
import urllib.request, urllib.parse, gzip, json, re, socket

VID = "Do_LjDsisIE"
UA_AND = "Mozilla/5.0 (Linux; Android 12; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
UA_DESK = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

def get(url, ua=UA_AND, timeout=20, hdr=None):
    h = {"User-Agent": ua, "Accept": "text/html,*/*", "Accept-Encoding": "gzip",
         "Accept-Language": "en-US,en;q=0.9", "Connection": "close"}
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

print("=== 1. DNS / 连通性 ===")
for host in ["www.youtube.com", "youtube.com", "youtu.be", "m.youtube.com", "i.ytimg.com"]:
    try:
        ip = socket.gethostbyname(host)
        print("  %-18s -> %s" % (host, ip))
    except Exception as e:
        print("  %-18s DNS失败 %s" % (host, str(e)[:60]))

print()
print("=== 2. 直接访问视频页 ===")
tests = [
  ("watch 页", "https://www.youtube.com/watch?v=%s" % VID),
  ("oembed",  "https://www.youtube.com/oembed?url=https://www.youtube.com/watch?v=%s&format=json" % VID),
  ("youtu.be", "https://youtu.be/%s" % VID),
  ("m.youtube", "https://m.youtube.com/watch?v=%s" % VID),
]
for tag, u in tests:
    st, body = get(u)
    extra = ""
    if st == 200:
        if "ytInitialPlayerResponse" in body:
            extra = "有 ytInitialPlayerResponse"
        elif "ytInitialData" in body:
            extra = "有 ytInitialData（无 playerResponse）"
        if "consent" in body[:3000].lower(): extra += " [consent 页]"
        if "not a bot" in body.lower() or "Sign in to confirm" in body: extra += " [BOT_CHECK]"
    print("  %-12s st=%-4s len=%-7d %s %s" % (tag, st, len(body), extra, body[:70].replace("\n", " ") if st != 200 else ""))

print()
print("=== 3. 桌面 UA 再试 watch 页 ===")
st, body = get("https://www.youtube.com/watch?v=%s" % VID, ua=UA_DESK)
print("  st=%s len=%d" % (st, len(body)))
if st == 200:
    print("  ytInitialPlayerResponse:", "ytInitialPlayerResponse" in body)
    print("  ytInitialData:", "ytInitialData" in body)
    print("  playabilityStatus:", re.findall(r'"playabilityStatus":\{"status":"([A-Z_]+)"', body)[:2])
    print("  streamingData:", "streamingData" in body)

print()
print("=== 4. innertube player API（免签名，可能被拒）===")
try:
    payload = json.dumps({
        "videoId": VID,
        "context": {"client": {"clientName": "ANDROID", "clientVersion": "19.09.37",
                               "androidSdkVersion": 30, "hl": "en", "gl": "US"}},
    }).encode("utf-8")
    h = {"User-Agent": "com.google.android.youtube/19.09.37 (Linux; U; Android 12) gzip",
         "Content-Type": "application/json", "Accept-Encoding": "gzip", "Connection": "close"}
    r = urllib.request.urlopen(urllib.request.Request(
        "https://www.youtube.com/youtubei/v1/player?key=AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8",
        data=payload, headers=h), timeout=25)
    raw = r.read()
    if "gzip" in r.headers.get("Content-Encoding", ""):
        try: raw = gzip.decompress(raw)
        except Exception: pass
    j = json.loads(raw.decode("utf-8", "ignore"))
    ps = (j.get("playabilityStatus") or {})
    print("  status:", ps.get("status"), "reason:", str(ps.get("reason"))[:80])
    sd = j.get("streamingData") or {}
    print("  formats:", len(sd.get("formats") or []), "adaptive:", len(sd.get("adaptiveFormats") or []))
    if sd.get("formats"):
        f = sd["formats"][0]
        print("  format[0] itag=%s mime=%s" % (f.get("itag"), str(f.get("mimeType"))[:40]))
        print("  url 前缀:", str(f.get("url"))[:100])
except Exception as e:
    print("  ERR", str(e)[:150])