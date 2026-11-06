# -*- coding: utf-8 -*-
"""测 TikTok photo 作品的多条取数路径"""
import urllib.request, gzip, json, re, sys

ITEM = "7637720726061026578"
USER = "lex.8889"

UAS = {
 "android_chrome": "Mozilla/5.0 (Linux; Android 12; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36",
 "iphone_safari": "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1",
 "desktop": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
 "googlebot": "Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)",
 "bingbot": "Mozilla/5.0 (compatible; bingbot/2.0; +http://www.bing.com/bingbot.htm)",
}

def get(url, ua, extra=None, timeout=25):
    h = {
        "User-Agent": ua,
        "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
        "Accept-Language": "en-US,en;q=0.9",
        "Accept-Encoding": "gzip, deflate",
        "Referer": "https://www.tiktok.com/",
        "Connection": "close",
    }
    if extra: h.update(extra)
    try:
        r = urllib.request.urlopen(urllib.request.Request(url, headers=h), timeout=timeout)
        raw = r.read()
        enc = r.headers.get("Content-Encoding", "")
        if "gzip" in enc:
            try: raw = gzip.decompress(raw)
            except Exception: pass
        return r.status, raw.decode("utf-8", "ignore")
    except Exception as e:
        return -1, "ERR " + str(e)

def check(tag, html):
    has_udr = "__UNIVERSAL_DATA_FOR_REHYDRATION__" in html
    has_ip = "imagePost" in html
    has_det = "photo.detail" in html
    has_api = "api-data" in html
    scope = ""
    m = re.search(r'id="__UNIVERSAL_DATA_FOR_REHYDRATION__"[^>]*>(.*?)</script>', html, re.S)
    if m:
        try:
            d = json.loads(m.group(1).strip())
            keys = list(d.get("__DEFAULT_SCOPE__", {}).keys())
            hits = [k for k in keys if "detail" in k]
            scope = "detailKeys=%s" % hits
        except Exception as e:
            scope = "udrErr=%s" % e
    print("  %-40s len=%-7d udr=%s ip=%s photoDetail=%s apidata=%s %s"
          % (tag, len(html), has_udr, has_ip, has_det, has_api, scope))
    return has_ip

print("=== A. photo 页不同 UA ===")
for name, ua in UAS.items():
    st, html = get("https://www.tiktok.com/@%s/photo/%s" % (USER, ITEM), ua)
    check("photo/" + name, html)

print("=== B. 带分享参数 ===")
for q in ["?is_copy_url=1&is_from_webapp=v1", "?lang=en", "?_r=1&_t=abc"]:
    st, html = get("https://www.tiktok.com/@%s/photo/%s%s" % (USER, ITEM, q), UAS["android_chrome"])
    check("photo" + q, html)

print("=== C. 官方 API ===")
api_list = [
    ("photo/detail", "https://www.tiktok.com/api/photo/detail/?itemId=%s&aid=1988" % ITEM),
    ("aweme/detail", "https://www.tiktok.com/aweme/v1/web/aweme/detail/?aweme_id=%s&aid=1988&device_platform=webapp" % ITEM),
    ("item/detail",  "https://www.tiktok.com/api/item/detail/?itemId=%s&aid=1988" % ITEM),
]
for name, u in api_list:
    st, body = get(u, UAS["android_chrome"], {"Accept": "application/json, text/plain, */*"})
    print("  %-14s status=%s len=%s head=%s" % (name, st, len(body), body[:160].replace("\n", " ")))

print("=== D. m 站 / embed ===")
for name, u in [
    ("m.tiktok", "https://m.tiktok.com/v/%s.html" % ITEM),
    ("embed",    "https://www.tiktok.com/embed/v2/%s" % ITEM),
    ("embed_old","https://www.tiktok.com/embed/%s" % ITEM),
]:
    st, html = get(u, UAS["android_chrome"])
    check(name, html)