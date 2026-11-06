# -*- coding: utf-8 -*-
"""看 adaptiveFormats 里每条的具体结构：是 url 还是 signatureCipher"""
import urllib.request, gzip, json, re

VID = "Do_LjDsisIE"
UA_DESK = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

def get(url, ua, timeout=30):
    h = {"User-Agent": ua, "Accept": "text/html,*/*", "Accept-Encoding": "gzip",
         "Accept-Language": "en-US,en;q=0.9", "Connection": "close"}
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
    depth = 0; inq = False; esc = False
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
                if depth == 0: return html[i:k + 1]
    return None

html = get("https://www.youtube.com/watch?v=%s" % VID, UA_DESK)
j = json.loads(extract_json(html, "ytInitialPlayerResponse"))
sd = j.get("streamingData") or {}
adapt = sd.get("adaptiveFormats") or []

print("=== adaptiveFormats[0] 完整 key 列表 ===")
if adapt:
    print(list(adapt[0].keys()))
    print()
    print("=== adaptiveFormats[0] 全文 ===")
    print(json.dumps(adapt[0], indent=1)[:1500])

print()
print("=== 统计：有 url 的 / 有 signatureCipher 的 ===")
with_url = [f for f in adapt if f.get("url")]
with_cipher = [f for f in adapt if f.get("signatureCipher") or f.get("cipher")]
print("  总共:", len(adapt), " 有 url:", len(with_url), " 有 cipher:", len(with_cipher))

if with_cipher:
    print()
    print("=== cipher 示例 ===")
    c = with_cipher[0].get("signatureCipher") or with_cipher[0].get("cipher")
    print("  ", str(c)[:400])

print()
print("=== 页面里有没有 n 参数 / 需要解密的线索 ===")
print("  jsUrl:", ((j.get("assets") or {}).get("js"))[:130] if (j.get("assets") or {}).get("js") else "无")
print("  页面含 player js:", bool(re.search(r'/s/player/[a-z0-9]+/player_ias', html)))

print()
print("=== formats（带音视频合一的）===")
fmts = sd.get("formats") or []
print("  count:", len(fmts))
for f in fmts[:5]:
    print("   itag=%s url=%s cipher=%s" % (f.get("itag"), "有" if f.get("url") else "无",
                                          "有" if (f.get("signatureCipher") or f.get("cipher")) else "无"))

print()
print("=== 关键：单独请求 player API 用 WEB 客户端看能否拿到 url ===")
try:
    body = json.dumps({
        "videoId": VID,
        "context": {"client": {"clientName": "WEB", "clientVersion": "2.20241126.01.00",
                               "hl": "en", "gl": "US"}},
    }).encode()
    h2 = {"User-Agent": UA_DESK, "Content-Type": "application/json",
          "Accept-Encoding": "gzip", "X-Youtube-Client-Name": "1",
          "X-Youtube-Client-Version": "2.20241126.01.00", "Connection": "close"}
    r = urllib.request.urlopen(urllib.request.Request(
        "https://www.youtube.com/youtubei/v1/player?prettyPrint=false",
        data=body, headers=h2), timeout=25)
    raw = r.read()
    if "gzip" in r.headers.get("Content-Encoding", ""):
        try: raw = gzip.decompress(raw)
        except Exception: pass
    jj = json.loads(raw.decode("utf-8", "ignore"))
    print("  status:", (jj.get("playabilityStatus") or {}).get("status"))
    sd2 = jj.get("streamingData") or {}
    a2 = sd2.get("adaptiveFormats") or []
    print("  adaptive:", len(a2), " 有 url:", len([x for x in a2 if x.get("url")]),
          " 有 cipher:", len([x for x in a2 if x.get("signatureCipher") or x.get("cipher")]))
    if a2:
        print("  first keys:", list(a2[0].keys()))
except Exception as e:
    print("  ERR:", str(e)[:140])