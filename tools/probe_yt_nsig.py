# -*- coding: utf-8 -*-
"""B 方案：调研 player.js 里 n-sig 函数的真实结构（新版是否还能静态提取）"""
import urllib.request, gzip, re, json

UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

def get(url, timeout=35):
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
print("=== 1. 找 n-sig 调用点 ===")
# yt-dlp 的定位方式：找 .get("n")) 或者 .set("n",
for pat in [r'\.get\("n"\)', r'\.set\("n",', r'enhanced_except', r'\bn\s*=\s*function']:
    print("  %-24s %d 次" % (pat, len(re.findall(pat, js))))

print()
print("=== 2. 找形如 X=function(a){...} 里出现 'enhanced' / split 的 ===")
# 新版常见：var Xy={...}; 然后 Z=(a)=>{...}
frags = re.findall(r'function\(([a-zA-Z_$][\w$]*)\)\{[^{}]{0,600}?\.join\(""\)', js)
print("  含 join(\"\") 的函数:", len(frags))

# 关键：找 "n" 参数处理器常见的 var 赋值模式
print()
print("=== 3. 搜 varEnh 之类特征 ===")
for key in ["a=a.split", "split(\"\")", "$.split", "getRandomValues", "PQ", "VarInt"]:
    print("  %-18s %d" % (key, js.count(key)))

print()
print("=== 4. 打印所有 function(x){ 后 500 字符内含 join(\"\") 的上下文 ===")
cnt = 0
for m in re.finditer(r'\)\{[^{}]{0,400}?\.join\(""\)', js):
    s = max(0, m.start() - 120)
    frag = js[s:m.end() + 60].replace("\n", " ")
    if "split" in frag and len(frag) > 100:
        print("   #%d: %s" % (cnt, frag[:330]))
        print()
        cnt += 1
        if cnt >= 4: break
if cnt == 0:
    print("  (无匹配)")

print()
print("=== 5. 看 js 头部，判断结构 ===")
print(js[:400])
print()
print("=== 6. 找 'sig' 相关 ===")
for m in re.finditer(r'.{60}sig.{100}', js):
    print("  ", m.group(0).replace("\n", " ")[:180])
    break