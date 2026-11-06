# -*- coding: utf-8 -*-
"""重测 tikwm：昨天日志是成功的，看现在为什么不行；并看完整返回字段"""
import urllib.request, urllib.parse, gzip, json

ITEM = "7637720726061026578"
USER = "lex.8889"
SHARE = "https://www.tiktok.com/@%s/photo/%s" % (USER, ITEM)
IDS = "7637720726061026578"

UAS = {
  "app_android": "Mozilla/5.0 (Linux; Android 12; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36",
  "iphone": "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1",
  "desktop": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
  "tikwm_ua": "TikTok/26.1.3 (Linux; U; Android 12; zh_CN; Pixel 5)",
}

def get(url, ua, extra=None, timeout=30):
    h = {"User-Agent": ua, "Accept": "application/json, text/plain, */*",
         "Accept-Encoding": "gzip", "Accept-Language": "en-US,en;q=0.9",
         "Connection": "close"}
    if extra: h.update(extra)
    try:
        r = urllib.request.urlopen(urllib.request.Request(url, headers=h), timeout=timeout)
        raw = r.read()
        if "gzip" in r.headers.get("Content-Encoding", ""):
            try: raw = gzip.decompress(raw)
            except Exception: pass
        return r.status, raw.decode("utf-8", "ignore")
    except Exception as e:
        return -1, "ERR " + str(e)[:150]

q = urllib.parse.quote(SHARE, safe="")
print("=== A. tikwm 带不同 UA ===")
for name, ua in UAS.items():
    st, b = get("https://www.tikwm.com/api/?hd=1&url=%s" % q, ua, {"Referer": "https://www.tikwm.com/"})
    print("  %-12s status=%s len=%s %s" % (name, st, len(b), b[:130].replace("\n", " ")))

print()
print("=== B. 试 id 直传 / 不同域名 / 不同路径 ===")
variants = [
  ("www_hd_id", "https://www.tikwm.com/api/?hd=1&url=%s" % ITEM),
  ("www_noscheme", "https://www.tikwm.com/api/?url=%s" % q.replace("%3A%2F%2F", "%3A%2F%2F")),
  ("tikwm_nohd", "https://tikwm.com/api/?url=%s" % q),
  ("tikwm_www2", "https://www.tikwm.com/api/feed/?url=%s" % q),
  ("tm_result", "https://www.tikwm.com/api/?url=%s&count=12&cursor=0" % q),
  ("tkapi", "https://tikwm.com/api/?url=%s&hd=1" % q),
]
for name, u in variants:
    st, b = get(u, UAS["app_android"], {"Referer": "https://www.tikwm.com/"})
    print("  %-14s status=%s len=%s %s" % (name, st, len(b), b[:130].replace("\n", " ")))

print()
print("=== C. 成功的那个：完整字段 ===")
st, b = get("https://www.tikwm.com/api/?hd=1&url=%s" % q, UAS["app_android"],
            {"Referer": "https://www.tiktok.com/"})
if st == 200:
    try:
        j = json.loads(b)
        print("  top:", list(j.keys()))
        d = j.get("data") or {}
        print("  code:", j.get("code"), "msg:", j.get("msg"))
        print("  data keys:", list(d.keys()))
        for k in ["images", "live_images", "liveImages", "duration", "music_info"]:
            v = d.get(k)
            if isinstance(v, list):
                print("   %s: %d items" % (k, len(v)))
                for i, x in enumerate(v[:3]): print("      [%d] %s" % (i, str(x)[:110]))
            elif v is not None:
                print("   %s: %s" % (k, str(v)[:120]))
    except Exception as e:
        print("  parse err", e)