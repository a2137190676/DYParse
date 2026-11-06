# -*- coding: utf-8 -*-
"""B 方案调研：抓 player.js，看 sig 变换函数和 n 函数的结构"""
import urllib.request, gzip, re, json

VID = "Do_LjDsisIE"
UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

def get(url, timeout=30):
    h = {"User-Agent": UA, "Accept": "*/*", "Accept-Encoding": "gzip",
         "Accept-Language": "en-US,en;q=0.9", "Connection": "close"}
    r = urllib.request.urlopen(urllib.request.Request(url, headers=h), timeout=timeout)
    raw = r.read()
    if "gzip" in r.headers.get("Content-Encoding", ""):
        try: raw = gzip.decompress(raw)
        except Exception: pass
    return raw.decode("utf-8", "ignore")

html = get("https://www.youtube.com/watch?v=%s" % VID)
print("page len:", len(html))

# 1. 找 js url
js_urls = re.findall(r'"jsUrl":"([^"]+)"', html)
if not js_urls:
    js_urls = re.findall(r'(/s/player/[a-zA-Z0-9_\-]+/(?:player_ias|player)[a-zA-Z0-9_\-]*\.vflset/[a-zA-Z0-9_\-]*?base\.js)', html)
print("jsUrl:", js_urls[:3])

# 2. 从 playerResponse 的 assets.js
m = re.search(r'"assets":\{"js":"([^"]+)"', html)
if m: print("assets.js:", m.group(1))

# 3. 页面里所有 /s/player/ 路径
alljs = re.findall(r'/s/player/[a-zA-Z0-9_\-]+/[a-zA-Z0-9_\-./]*?\.js', html)
print("候选 js:", list(set(alljs))[:5])

target = ""
if m:
    target = m.group(1)
    if target.startswith("//"): target = "https:" + target
    elif target.startswith("/"): target = "https://www.youtube.com" + target
elif js_urls:
    target = js_urls[0]
    if target.startswith("/"): target = "https://www.youtube.com" + target

print()
print("=== 下载 player js ===", target[:110])
if target:
    js = get(target)
    print("  js len:", len(js))
    open("/tmp/yt_player.js", "w").write(js)

    # 找 sig 变换：形如 .split("") 或 a=a.split("") 后面接若干操作
    print()
    print("  === sig 函数候选 ===")
    for pat, name in [
        (r'([a-zA-Z0-9_$]{2,})=function\(([a-z])\)\{\2=\2\.split\(""\)', "split(\"\")型"),
        (r'\.sig\|\|([a-zA-Z0-9_$]+)\(', "调用点"),
    ]:
        hits = re.findall(pat, js)
        print("   %-14s 命中 %d: %s" % (name, len(hits), str(hits[:4])[:140]))

    print()
    print("  === n 函数候选（形如 xxx=function(a){...a.length...}）===")
    # 常见：var Xg={...};  function n(a){...}
    cands = re.findall(r'([a-zA-Z0-9_$]{1,6})=function\(([a-z])\)\{var [a-z]=[a-z]\.split\(""\)', js)
    print("   split型:", cands[:5])

    # 找 enhanced_except / 典型 n 函数特征
    for marker in ["enhanced_except", "getRandomValues", "join(\"\")"]:
        print("   %-18s 出现 %d 次" % (marker, js.count(marker)))

    # 找 a.split("")[后接.push/reverse/splice 的模式
    print()
    print("  === 所有含 .split(\"\") 的函数体片段 ===")
    for mm in re.finditer(r'function\(([a-z])\)\{[^}]{0,40}\1=\1\.split\(""\)', js):
        s = mm.start()
        print("   @%d: %s" % (s, js[s:s + 260].replace("\n", " ")[:250]))
        print()
else:
    print("  没找到 js url")