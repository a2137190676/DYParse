# -*- coding: utf-8 -*-
"""看 photo 页 api-data 的 102 字节是什么，并试更多变体"""
import urllib.request, http.cookiejar, gzip, json, re

UA = "Mozilla/5.0 (Linux; Android 12; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
cj = http.cookiejar.CookieJar()
op = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(cj))

ITEM = "7637720726061026578"
USER = "lex.8889"

def get(url, timeout=30, extra=None):
    h = {"User-Agent": UA, "Accept": "text/html,*/*", "Accept-Language": "en-US,en;q=0.9",
         "Accept-Encoding": "gzip", "Referer": "https://www.tiktok.com/", "Connection": "close"}
    if extra: h.update(extra)
    try:
        r = op.open(urllib.request.Request(url, headers=h), timeout=timeout)
        raw = r.read()
        if "gzip" in r.headers.get("Content-Encoding", ""):
            try: raw = gzip.decompress(raw)
            except Exception: pass
        return r.status, raw.decode("utf-8", "ignore")
    except Exception as e:
        return -1, "ERR " + str(e)[:100]

get("https://www.tiktok.com/")

base = "https://www.tiktok.com/@%s/photo/%s" % (USER, ITEM)

variants = [
  ("plain", ""),
  ("webapp_v1", "?is_from_webapp=1&sender_device=pc"),
  ("copy_url", "?is_copy_url=1&is_from_webapp=v1"),
  ("lang_en", "?lang=en"),
  ("_r", "?_r=1"),
  ("share", "?share_app_id=1233&share_link_id=abc123"),
  ("refer", "?refer=web&gd_label=click_wap"),
]
for tag, q in variants:
    st, h = get(base + q)
    m2 = re.search(r'id="api-data"[^>]*>(.*?)</script>', h, re.S)
    api = m2.group(1).strip() if m2 else ""
    udr_sc = ""
    m = re.search(r'id="__UNIVERSAL_DATA_FOR_REHYDRATION__"[^>]*>(.*?)</script>', h, re.S)
    if m:
        try:
            d = json.loads(m.group(1).strip())
            for k, v in (d.get("__DEFAULT_SCOPE__") or {}).items():
                if "detail" in k and isinstance(v, dict):
                    udr_sc = "sc=%s" % v.get("statusCode")
        except Exception: pass
    print("[%-10s] len=%-7d apidata=%-6d ip=%-5s %s" % (tag, len(h), len(api), "imagePost" in h, udr_sc))
    if 0 < len(api) < 400:
        print("     api:", api[:300])

print()
print("=== 用桌面 UA + 完整 header ===")
st, h = get(base, extra={
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
    "Sec-Fetch-Dest": "document", "Sec-Fetch-Mode": "navigate", "Sec-Fetch-Site": "none",
    "Sec-Fetch-User": "?1", "Upgrade-Insecure-Requests": "1",
    "sec-ch-ua": '"Chromium";v="131", "Not_A Brand";v="24"',
    "sec-ch-ua-mobile": "?0", "sec-ch-ua-platform": '"Windows"',
})
m2 = re.search(r'id="api-data"[^>]*>(.*?)</script>', h, re.S)
print("desktop len", len(h), "apidata", len(m2.group(1).strip()) if m2 else 0, "ip", "imagePost" in h)