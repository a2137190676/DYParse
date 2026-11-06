# -*- coding: utf-8 -*-
"""B 方案深挖：精确定位 n-sig 函数 + sig 变换函数"""
import urllib.request, gzip, re, json

UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

def get(url, timeout=40):
    h = {"User-Agent": UA, "Accept": "*/*", "Accept-Encoding": "gzip",
         "Accept-Language": "en-US,en;q=0.9", "Connection": "close"}
    r = urllib.request.urlopen(urllib.request.Request(url, headers=h), timeout=timeout)
    raw = r.read()
    if "gzip" in r.headers.get("Content-Encoding", ""):
        try: raw = gzip.decompress(raw)
        except Exception: pass
    return raw.decode("utf-8", "ignore")

js = get("https://www.youtube.com/s/player/a5c2aafe/player_es6.vflset/en_US/base.js")
print("js len:", len(js))

print()
print("=" * 70)
print("=== 1. 定位 .get(\"n\") 的上下文（yt-dlp 就是靠这个找 n 函数）===")
for m in re.finditer(r'\.get\("n"\)', js):
    s = max(0, m.start() - 500)
    print(js[s:m.end() + 200].replace("\n", " "))
    print()

print("=" * 70)
print("=== 2. 定位 sig 相关的 split(\"\") ===")
cnt = 0
for m in re.finditer(r'([a-zA-Z_$][\w$]{0,4})=([a-zA-Z_$][\w$]{0,4})\.split\(""\)', js):
    s = max(0, m.start() - 200)
    frag = js[s:m.end() + 400].replace("\n", " ")
    print("  #%d %s" % (cnt, frag[:560]))
    print()
    cnt += 1
    if cnt >= 5: break
print("  总 split(\"\") 赋值:", len(re.findall(r'\.split\(""\)', js)))

print("=" * 70)
print("=== 3. 找 wz / Xg 这类 sig 解密对象定义 ===")
# 典型：var Xg={xx:function(a,b){...},...};
for m in re.finditer(r'var ([a-zA-Z_$][\w$]{1,3})=\{([^{}]{200,2000}?)\};', js):
    body = m.group(2)
    if "reverse" in body and ("splice" in body or "length" in body):
        print("  对象名:", m.group(1))
        print("  定义:", body[:600].replace("\n", " "))
        print()
        break