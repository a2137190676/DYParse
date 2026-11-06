# -*- coding: utf-8 -*-
"""探测 X 主页可用的数据源，看哪个真能拿到推文。

候选：
 1. syndication timeline-profile（已知 429）
 2. 官方 UserTweets（需 bearer + guest token）
 3. api.fxtwitter.com 用户时间线
 4. x.com 网页 i/api 游客态
"""
import urllib.request, json, re, sys

UA_DESKTOP = ("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
              "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36")
UA_ANDROID = ("Mozilla/5.0 (Linux; Android 12; Pixel 5) AppleWebKit/537.36 "
              "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
BEARER = ("AAAAAAAAAAAAAAAAAAAAANRILgAAAAAAnNwIzUejRCOuH5E6I8xnZz4puTs%3D"
          "1Zv7ttfk8LF81IUq16cHjhLTvJu4FA33AGWWjCpTnA")

name = sys.argv[1] if len(sys.argv) > 1 else "gomimata"


def get(url, headers, timeout=18):
    req = urllib.request.Request(url, headers=headers)
    r = urllib.request.urlopen(req, timeout=timeout)
    return r.status, r.read()


def show(tag, fn):
    try:
        fn()
    except Exception as e:
        print("%-22s ERR %s" % (tag, str(e)[:90]))


# ---- 1. guest token ----
def t1():
    st, b = get("https://api.x.com/1.1/guest/activate.json",
                {"User-Agent": UA_DESKTOP, "Authorization": "Bearer " + BEARER,
                 "Content-Type": "application/json"}, 20)
    d = json.loads(b)
    print("%-22s %s guest=%s" % ("guest/activate", st, d.get("guest_token", "?")[:24]))


# ---- 2. UserTweets with guest token ----
def t2():
    st, b = get("https://api.x.com/1.1/guest/activate.json",
                {"User-Agent": UA_DESKTOP, "Authorization": "Bearer " + BEARER}, 20)
    gt = json.loads(b).get("guest_token")
    if not gt:
        print("UserTweets              no guest token")
        return
    u = ("https://api.x.com/1.1/statuses/user_timeline.json?screen_name=" + name +
         "&count=20&tweet_mode=extended&include_rts=1")
    st2, b2 = get(u, {"User-Agent": UA_DESKTOP, "Authorization": "Bearer " + BEARER,
                      "x-guest-token": gt}, 25)
    arr = json.loads(b2)
    n = len(arr) if isinstance(arr, list) else 0
    print("%-22s %s n=%d" % ("user_timeline v1.1", st2, n))
    if n:
        print("   first:", str(arr[0].get("full_text", ""))[:70])


# ---- 3. fxtwitter ----
def t3():
    st, b = get("https://api.fxtwitter.com/%s" % name,
                {"User-Agent": UA_DESKTOP, "Accept": "application/json"}, 20)
    d = json.loads(b)
    print("%-22s %s keys=%s" % ("fxtwitter", st, list(d.keys())[:8]))
    print("   ", json.dumps(d, ensure_ascii=False)[:220])


# ---- 4. x.com 网页 __NEXT_DATA__ / 初始状态 ----
def t4():
    st, b = get("https://x.com/%s" % name,
                {"User-Agent": UA_DESKTOP,
                 "Accept": "text/html,application/xhtml+xml,*/*"}, 25)
    s = b.decode("utf-8", "ignore")
    print("%-22s %s len=%d" % ("x.com html", st, len(s)))
    for key in ["__NEXT_DATA__", "status/", "UserTweets", "guest_token", "auth_token"]:
        print("   %-16s %d" % (key, s.count(key)))


for tag, fn in [("1 guest", t1), ("2 user_timeline", t2),
                ("3 fxtwitter", t3), ("4 x.com html", t4)]:
    print("======== %s ========" % tag)
    show(tag, fn)