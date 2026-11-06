# -*- coding: utf-8 -*-
"""探测 TikTok 主页可用数据源
候选：
  1) tikwm user/posts  https://www.tikwm.com/api/user/posts?unique_id=xx&count=30&cursor=0
  2) tiktok 网页 HTML  __UNIVERSAL_DATA_FOR_REHYDRATION__
"""
import json, re, urllib.request, urllib.error, ssl

UA_DESK = ("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
           "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36")
UA_MOB = ("Mozilla/5.0 (Linux; Android 13; SM-S918B Build/TP1A.220624.014; wv) "
          "AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/126.0.0.0 Mobile Safari/537.36")

ctx = ssl.create_default_context()
ctx.check_hostname = False
ctx.verify_mode = ssl.CERT_NONE


def get(url, ua, timeout=25):
    req = urllib.request.Request(url, headers={
        "User-Agent": ua,
        "Accept": "*/*",
        "Accept-Language": "en-US,en;q=0.9",
    })
    try:
        with urllib.request.urlopen(req, timeout=timeout, context=ctx) as r:
            return r.status, r.read()
    except urllib.error.HTTPError as e:
        return e.code, e.read()
    except Exception as e:
        return -1, str(e).encode()


def show(tag, url, ua):
    print("=" * 20, tag)
    st, body = get(url, ua)
    print("  status", st, "len", len(body))
    if st != 200:
        print("  head:", body[:300])
        return
    txt = body.decode("utf-8", "ignore")
    for k in ["__UNIVERSAL_DATA_FOR_REHYDRATION__", "SIGI_STATE",
              "itemList", "itemStruct", "imagePost", "video.downloadAddr",
              "webapp.user-detail", "uniqueId"]:
        print("   %-38s %d" % (k, txt.count(k)))
    print("   head:", re.sub(r"\s+", " ", txt[:200]))


uid = "lightbertha"

show("tikwm user/posts (mobile UA)",
     "https://www.tikwm.com/api/user/posts?unique_id=%s&count=30&cursor=0" % uid, UA_MOB)

show("tikwm user/posts (desktop UA)",
     "https://www.tikwm.com/api/user/posts?unique_id=%s&count=30&cursor=0" % uid, UA_DESK)

show("tiktok profile html (desktop UA)",
     "https://www.tiktok.com/@%s" % uid, UA_DESK)

show("tiktok profile html (mobile UA)",
     "https://www.tiktok.com/@%s" % uid, UA_MOB)

show("tiktok oembed",
     "https://www.tiktok.com/oembed?url=https://www.tiktok.com/@%s" % uid, UA_MOB)
