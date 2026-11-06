# -*- coding: utf-8 -*-
"""深挖 webapp.reflow.video.detail 里的 itemStruct，看图片字段在哪"""
import urllib.request, gzip, json, re

ITEM = "7637720726061026578"
USER = "lex.8889"
UA = "Mozilla/5.0 (Linux; Android 12; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

def get(url, ua=UA):
    h = {"User-Agent": ua, "Accept": "text/html,*/*", "Accept-Language": "en-US,en;q=0.9",
         "Accept-Encoding": "gzip", "Referer": "https://www.tiktok.com/", "Connection": "close"}
    try:
        r = urllib.request.urlopen(urllib.request.Request(url, headers=h), timeout=30)
        raw = r.read()
        if "gzip" in r.headers.get("Content-Encoding", ""):
            try: raw = gzip.decompress(raw)
            except Exception: pass
        return raw.decode("utf-8", "ignore")
    except Exception as e:
        return "ERR " + str(e)

def dump_scope(html, tag):
    m = re.search(r'id="__UNIVERSAL_DATA_FOR_REHYDRATION__"[^>]*>(.*?)</script>', html, re.S)
    if not m:
        print(tag, "NO UDR"); return
    try:
        d = json.loads(m.group(1).strip())
    except Exception as e:
        print(tag, "UDR_JSON_ERR", e); return
    scope = d.get("__DEFAULT_SCOPE__", {})
    print(tag, "len", len(html), "keys", len(scope))
    for k in scope:
        if "detail" not in k: continue
        cand = scope[k]
        cii = cand.get("itemInfo") if isinstance(cand, dict) else None
        cit = cii.get("itemStruct") if isinstance(cii, dict) else None
        if not isinstance(cit, dict):
            print("  ", k, "-> no itemStruct; subkeys=", list(cand.keys())[:8] if isinstance(cand, dict) else type(cand))
            continue
        print("  ", k, "-> id=", cit.get("id"), "keys=", list(cit.keys()))
        for f in ["imagePost", "imagePostInfo", "images", "photo", "slides", "slide"]:
            if f in cit:
                print("      HAS", f, "=", json.dumps(cit[f])[:300])
        # 有没有任何 https 图片
        txt = json.dumps(cit)
        print("      mime:", re.findall(r'"image/[a-z]+"', txt)[:3], "hasImageURL:", "imageURL" in txt)

print("=== m.tiktok ===")
h1 = get("https://m.tiktok.com/v/%s.html" % ITEM)
dump_scope(h1, "m.tiktok")
open("/tmp/tt_m.html", "w").write(h1)

print("=== www photo?_r=1 ===")
h2 = get("https://www.tiktok.com/@%s/photo/%s?_r=1&_t=abc" % (USER, ITEM))
dump_scope(h2, "www_r1")

print("=== 控制组：video 作品 ===")
h3 = get("https://www.tiktok.com/@united.state.3357/video/7688168881160080662")
dump_scope(h3, "ctrl_video")