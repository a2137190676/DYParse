# -*- coding: utf-8 -*-
"""探测 tiktok 主页作品列表接口"""
import json, re, urllib.request, urllib.error, ssl

UA_MOB = ("Mozilla/5.0 (Linux; Android 13; SM-S918B Build/TP1A.220624.014; wv) "
          "AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/126.0.0.0 Mobile Safari/537.36")
ctx = ssl.create_default_context()
ctx.check_hostname = False
ctx.verify_mode = ssl.CERT_NONE

SUID = "MS4wLjABAAAAxWBBclHF_QvMNvZJGw8B-fLM3TkJW3eIneHQ8efv1zctpDgcp4QUvbj3uY0wzqFk"


def get(url, ua=UA_MOB, extra=None, timeout=25):
    hh = {"User-Agent": ua, "Accept": "application/json, text/plain, */*",
          "Accept-Language": "en-US,en;q=0.9",
          "Referer": "https://www.tiktok.com/"}
    if extra:
        hh.update(extra)
    req = urllib.request.Request(url, headers=hh)
    try:
        with urllib.request.urlopen(req, timeout=timeout, context=ctx) as r:
            return r.status, r.read()
    except urllib.error.HTTPError as e:
        return e.code, e.read()
    except Exception as e:
        return -1, str(e).encode()


def show(tag, url, **kw):
    st, body = get(url, **kw)
    txt = body.decode("utf-8", "ignore")
    print("=" * 12, tag, "status", st, "len", len(body))
    print("   itemList:", txt.count("itemList"), "itemStruct:", txt.count("itemStruct"),
          "playAddr:", txt.count("playAddr"), "cover:", txt.count("cover"))
    print("   head:", re.sub(r"\s+", " ", txt[:220]))


show("item_list no-sign",
     "https://www.tiktok.com/api/post/item_list/?aid=1988&count=30&cursor=0&secUid=%s" % SUID)

show("item_list app-v1 no-sign",
     "https://www.tiktok.com/api/post/item_list/?aid=1988&app_language=en&app_name=tiktok_web"
     "&browser_language=en-US&browser_name=Mozilla&browser_online=true&browser_platform=Win32"
     "&channel=tiktok_web&cookie_enabled=true&count=30&cursor=0&device_platform=web"
     "&secUid=%s&userId=0" % SUID)

show("embed page", "https://www.tiktok.com/embed/@lightbertha")

show("api user detail",
     "https://www.tiktok.com/api/user/detail/?aid=1988&uniqueId=lightbertha")

show("tikwm user/posts with referer",
     "https://www.tikwm.com/api/user/posts?unique_id=lightbertha&count=30&cursor=0",
     extra={"Referer": "https://www.tikwm.com/"})

show("tikwm user/info",
     "https://www.tikwm.com/api/user/info?unique_id=lightbertha",
     extra={"Referer": "https://www.tikwm.com/"})