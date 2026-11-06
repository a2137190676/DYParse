# -*- coding: utf-8 -*-
"""验证 tikwm 是否还活着：视频能过吗？photo 用各种路径能过吗？"""
import urllib.request, urllib.parse, gzip, json, time

UA = "Mozilla/5.0 (Linux; Android 12; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

def get(url, timeout=35, tries=2):
    h = {"User-Agent": UA, "Accept": "application/json, text/plain, */*",
         "Accept-Encoding": "gzip", "Referer": "https://www.tikwm.com/", "Connection": "close"}
    for k in range(tries):
        try:
            r = urllib.request.urlopen(urllib.request.Request(url, headers=h), timeout=timeout)
            raw = r.read()
            if "gzip" in r.headers.get("Content-Encoding", ""):
                try: raw = gzip.decompress(raw)
                except Exception: pass
            return r.status, raw.decode("utf-8", "ignore")
        except Exception as e:
            if k == tries - 1:
                return -1, "ERR " + str(e)[:110]
            time.sleep(2)

def probe(tag, target):
    q = urllib.parse.quote(target, safe="")
    st, b = get("https://www.tikwm.com/api/?hd=1&url=%s" % q)
    ok = '"code":0' in b
    print("  %-42s st=%-4s len=%-6d ok=%-5s %s" % (tag, st, len(b), ok, b[:110].replace("\n", " ")))
    if ok:
        try:
            d = json.loads(b).get("data") or {}
            print("      imgs=%s live=%s duration=%s keys=%s"
                  % (len(d.get("images") or []), len(d.get("live_images") or d.get("liveImages") or []),
                     d.get("duration"), list(d.keys())[:14]))
        except Exception as e:
            print("      parse err", e)
    return st, b

VIDEO_ITEM = "7688168881160080662"
VIDEO_USER = "united.state.3357"
PHOTO_ITEM = "7637720726061026578"
PHOTO_USER = "lex.8889"

print("=== A. tikwm 对普通视频（验证服务是否活着）===")
probe("video 完整链接", "https://www.tiktok.com/@%s/video/%s" % (VIDEO_USER, VIDEO_ITEM))
probe("video 只有 id", VIDEO_ITEM)

print()
print("=== B. photo 作品各种路径 ===")
probe("photo 原始路径", "https://www.tiktok.com/@%s/photo/%s" % (PHOTO_USER, PHOTO_ITEM))
probe("photo 换成 /video/", "https://www.tiktok.com/@%s/video/%s" % (PHOTO_USER, PHOTO_ITEM))
probe("photo @i/photo", "https://www.tiktok.com/@i/photo/%s" % PHOTO_ITEM)
probe("photo @i/video", "https://www.tiktok.com/@i/video/%s" % PHOTO_ITEM)
probe("photo 只有 id", PHOTO_ITEM)
probe("photo 短链样式", "https://www.tiktok.com/t/%s/" % PHOTO_ITEM)

print()
print("=== C. photo 作品主页里找它的另一个 photo 试 ===")
probe("同作者主页", "https://www.tiktok.com/@%s" % PHOTO_USER)