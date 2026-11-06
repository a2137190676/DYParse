# -*- coding: utf-8 -*-
"""单作品页在手机网络下能否拿到（TtParser 报「页面为空」的复核）"""
import urllib.request, urllib.error, ssl, re

UA = ("Mozilla/5.0 (Linux; Android 12; Pixel 5) AppleWebKit/537.36 "
      "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
UA2 = ("Mozilla/5.0 (Linux; Android 13; SM-S918B Build/TP1A.220624.014; wv) "
       "AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/126.0.0.0 Mobile Safari/537.36")
ctx = ssl.create_default_context()
ctx.check_hostname = False
ctx.verify_mode = ssl.CERT_NONE

ID = "7703768208632777998"
urls = [
    "https://www.tiktok.com/@lightbertha/video/%s" % ID,
    "https://www.tiktok.com/@i/video/%s" % ID,
    "https://m.tiktok.com/v/%s.html" % ID,
]


def fetch(url, ua):
    req = urllib.request.Request(url, headers={
        "User-Agent": ua, "Accept-Language": "en-US,en;q=0.9",
        "Referer": "https://www.tiktok.com/",
    })
    try:
        with urllib.request.urlopen(req, timeout=30, context=ctx) as r:
            return r.status, r.read()
    except urllib.error.HTTPError as e:
        return e.code, e.read()
    except Exception as e:
        return -1, str(e).encode()


for ua_name, ua in (("Pixel5", UA), ("SM-S918B", UA2)):
    for u in urls:
        st, body = fetch(u, ua)
        txt = body.decode("utf-8", "ignore")
        print("%-9s %-58s st=%s len=%s itemStruct=%d playAddr=%d imagePost=%d"
              % (ua_name, u[-58:], st, len(body),
                 txt.count("itemStruct"), txt.count("playAddr"), txt.count("imagePost")))