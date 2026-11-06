# -*- coding: utf-8 -*-
"""从 watch 页的 ytInitialPlayerResponse 抽 streamingData，看能否直接拿到可播直链"""
import urllib.request, gzip, json, re

VID = "Do_LjDsisIE"
UA_DESK = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
UA_AND = "Mozilla/5.0 (Linux; Android 12; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

def get(url, ua, timeout=30, hdr=None):
    h = {"User-Agent": ua, "Accept": "text/html,*/*", "Accept-Encoding": "gzip",
         "Accept-Language": "en-US,en;q=0.9", "Connection": "close"}
    if hdr: h.update(hdr)
    r = urllib.request.urlopen(urllib.request.Request(url, headers=h), timeout=timeout)
    raw = r.read()
    if "gzip" in r.headers.get("Content-Encoding", ""):
        try: raw = gzip.decompress(raw)
        except Exception: pass
    return raw.decode("utf-8", "ignore")

def extract_json(html, marker):
    i = html.find(marker)
    if i < 0: return None
    i = html.find("{", i)
    if i < 0: return None
    depth = 0
    inq = False
    esc = False
    for k in range(i, len(html)):
        c = html[k]
        if inq:
            if esc: esc = False
            elif c == "\\": esc = True
            elif c == '"': inq = False
        else:
            if c == '"': inq = True
            elif c == "{": depth += 1
            elif c == "}":
                depth -= 1
                if depth == 0:
                    return html[i:k + 1]
    return None

for tag, ua in [("桌面", UA_DESK), ("安卓", UA_AND)]:
    print("=" * 60)
    print(tag, "UA")
    html = get("https://www.youtube.com/watch?v=%s" % VID, ua)
    print("  page len:", len(html))
    raw = extract_json(html, "ytInitialPlayerResponse")
    if not raw:
        print("  没抠到 ytInitialPlayerResponse")
        continue
    print("  playerResponse len:", len(raw))
    j = json.loads(raw)
    ps = j.get("playabilityStatus") or {}
    print("  playabilityStatus:", ps.get("status"), "|", str(ps.get("reason"))[:60])
    vd = j.get("videoDetails") or {}
    print("  title:", str(vd.get("title"))[:60])
    print("  author:", vd.get("author"), "lenSec:", vd.get("lengthSeconds"))
    sd = j.get("streamingData") or {}
    fmts = sd.get("formats") or []
    adapt = sd.get("adaptiveFormats") or []
    print("  formats:", len(fmts), " adaptive:", len(adapt))
    if fmts:
        for f in fmts[:3]:
            url = str(f.get("url") or "")
            print("    itag=%s %s q=%s url=%s" % (f.get("itag"), str(f.get("mimeType"))[:28],
                                                  f.get("qualityLabel"), url[:90] if url else "(无,需签名)"))
            if url:
                has_n = ("&n=" in url) or ("?n=" in url)
                print("       ip参数:", re.findall(r'[?&]ip=([^&]+)', url)[:1],
                      " sig:", "sig=" in url, " 有n参数:", has_n)
    if adapt:
        vids = [f for f in adapt if str(f.get("mimeType", "")).startswith("video")]
        auds = [f for f in adapt if str(f.get("mimeType", "")).startswith("audio")]
        print("  adaptive video:", len(vids), " audio:", len(auds))
        for f in vids[:2] + auds[:2]:
            url = str(f.get("url") or "")
            print("    itag=%s %s q=%s url=%s" % (f.get("itag"), str(f.get("mimeType"))[:26],
                                                  f.get("qualityLabel"), url[:80] if url else "(无)"))