# -*- coding: utf-8 -*-
"""看 631817(sp=) 到 641824(/n/) 之间那段，这就是 sig+n 的核心"""
import re

js = open("/tmp/pj.js").read()
print("js len:", len(js))

print("=" * 70)
print("=== 631817 附近（sp= 签名处理）===")
print(js[630800:633200].replace("\n", " "))

print()
print("=" * 70)
print("=== 641824 附近（/n/ 处理）===")
print(js[641000:642600].replace("\n", " "))

print()
print("=" * 70)
print("=== 找 g.zs 三处 .zs= 上下文 ===")
for pos in [1660679, 1934008, 2437561]:
    print("--- @%d ---" % pos)
    print(js[pos - 200:pos + 400].replace("\n", " "))
    print()

print("=" * 70)
print("=== 找所有形如 XX=function(a){...a.length...reverse()...} 的 ===")
cnt = 0
for m in re.finditer(r'([a-zA-Z_$][\w$]{1,3})=function\(([a-z])\)\{', js):
    s = m.start()
    frag = js[s:s + 700]
    if "reverse()" in frag and "length" in frag and "split" in frag:
        print("--- %s @%d ---" % (m.group(1), s))
        print(frag.replace("\n", " ")[:650])
        print()
        cnt += 1
        if cnt >= 3: break
print("命中", cnt)