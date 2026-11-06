# -*- coding: utf-8 -*-
"""为什么 Python 能连、App 连不上？对比 TLS 指纹 / SNI / HTTP 版本"""
import urllib.request, gzip, json, ssl, socket

HOST = "yt.chocolatemoo53.com"
VID = "Do_LjDsisIE"
UAS = {
  "python_default": None,
  "andy_ua": "Mozilla/5.0 (Linux; Android 12; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36",
  "desk_ua": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
}

def try_get(ua, path="/api/v1/videos/" + VID, timeout=20):
    url = "https://" + HOST + path
    h = {"Accept": "application/json,*/*", "Accept-Encoding": "gzip", "Connection": "close"}
    if ua: h["User-Agent"] = ua
    try:
        r = urllib.request.urlopen(urllib.request.Request(url, headers=h), timeout=timeout)
        raw = r.read()
        return r.status, len(raw), ""
    except Exception as e:
        return -1, 0, str(e)[:110]

print("=== 1. 不同 UA 访问 ===")
for name, ua in UAS.items():
    st, ln, err = try_get(ua)
    print("  %-16s st=%-4s len=%-7d %s" % (name, st, ln, err))

print()
print("=== 2. TLS 握手详情 ===")
try:
    ctx = ssl.create_default_context()
    with socket.create_connection((HOST, 443), timeout=15) as sock:
        with ctx.wrap_socket(sock, server_hostname=HOST) as ss:
            print("  TLS:", ss.version(), "cipher:", ss.cipher()[0])
            print("  cert CN:", dict(x[0] for x in ss.getpeercert()["subject"]).get("commonName"))
except Exception as e:
    print("  TLS ERR:", str(e)[:140])

print()
print("=== 3. 只握手不带 SNI（模拟某些客户端）===")
try:
    ctx = ssl.create_default_context()
    ctx.check_hostname = False
    ctx.verify_mode = ssl.CERT_NONE
    with socket.create_connection((HOST, 443), timeout=15) as sock:
        with ctx.wrap_socket(sock) as ss:
            print("  无SNI握手成功")
except Exception as e:
    print("  无SNI ERR:", str(e)[:120])

print()
print("=== 4. 换 API 路径试 ===")
for p in ["/api/v1/videos/" + VID,
          "/api/v1/videos/" + VID + "?fields=title,adaptiveFormats",
          "/latest_version?id=" + VID + "&itag=18"]:
    st, ln, err = try_get(UAS["andy_ua"], p)
    print("  %-52s st=%-4s len=%-7d %s" % (p[:50], st, ln, err))

print()
print("=== 5. 再扫一遍 known 实例（用安卓 UA，模拟 App 行为）===")
for h in ["yt.chocolatemoo53.com", "inv.nadeko.net", "invidious.nerdvpn.de",
          "yewtu.be", "invidious.privacyredirect.com", "invidious.jing.rocks",
          "iv.melmac.space", "invidious.f5.si", "inv.tux.pizza"]:
    try:
        url = "https://" + h + "/api/v1/videos/" + VID
        hh = {"User-Agent": UAS["andy_ua"], "Accept": "application/json",
              "Accept-Encoding": "gzip", "Connection": "close"}
        r = urllib.request.urlopen(urllib.request.Request(url, headers=hh), timeout=15)
        raw = r.read()
        if "gzip" in str(r.headers.get("Content-Encoding", "")):
            try: raw = gzip.decompress(raw)
            except Exception: pass
        j = json.loads(raw.decode("utf-8", "ignore"))
        af = j.get("adaptiveFormats") or []
        hu = sum(1 for x in af if x.get("url"))
        print("  %-34s ✅ af=%d 有url=%d" % (h, len(af), hu))
    except Exception as e:
        print("  %-34s ❌ %s" % (h, str(e)[:60]))