# -*- coding: utf-8 -*-
"""B 方案：定位 g.zs（URL 解析器）和它的 get("n") 实现链"""
import urllib.request, gzip, re

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
open("/tmp/pj.js", "w").write(js)
print("js len:", len(js))

print()
print("=== 1. 找 zs 的定义（形如 zs=function(...) 或 zs:function）===")
for pat in [r'\bzs\s*=\s*function', r'\bzs\s*:\s*function', r'\bzs\s*=\s*\(', r'\.zs\s*=']:
    hits = [m.start() for m in re.finditer(pat, js)]
    print("  %-24s %d 处 %s" % (pat, len(hits), hits[:3]))

print()
print("=== 2. 打印 zs 定义上下文 ===")
for m in re.finditer(r'\bzs\s*[=:]\s*function', js):
    s = max(0, m.start() - 60)
    print(js[s:m.start() + 900].replace("\n", " "))
    print("---")
    break

print()
print("=== 3. 找 RT / get 方法里对 n 的处理 ===")
# 典型：get=function(a){var b=this.A[a];...}
for m in re.finditer(r'\.get\s*=\s*function\(([a-z])\)\{', js):
    s = m.start()
    frag = js[s:s + 500].replace("\n", " ")
    if '"n"' in frag or "n\"" in frag:
        print("  ", frag[:480])
        print()
        break

print()
print("=== 4. 找 sig / n 变换的入口函数（含 replace 到 sp/signature）===")
for kw in ["sp=", "signature", "/n/", "n-sig", "nsig"]:
    idx = js.find(kw)
    print("  %-12s @%d" % (kw, idx))

print()
print("=== 5. 所有 function(a){...split(\"\")...} 完整列出 ===")
cnt = 0
for m in re.finditer(r'function\(([a-zA-Z_$][\w$]*)\)\{(var |let |const )?([a-zA-Z_$][\w$]*)=\3\.split\(""\)', js):
    s = max(0, m.start() - 40)
    e = min(len(js), m.start() + 1200)
    print("  #%d @%d %s" % (cnt, m.start(), js[s:e].replace("\n", " ")[:900]))
    print()
    cnt += 1
    if cnt >= 4: break
print("  共", cnt, "个")