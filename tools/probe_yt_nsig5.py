# -*- coding: utf-8 -*-
"""找真正的 n-sig 求解器：Xn(38,3517,this) / ud(4,3533,this) 这两个混淆函数"""
import re

js = open("/tmp/pj.js").read()
print("js len:", len(js))

print("=" * 70)
print("=== 1. zs 类完整定义（含 Ue/get 实现）===")
i = js.find("g.zs=class")
print(js[i:i + 2000].replace("\n", " "))

print()
print("=" * 70)
print("=== 2. 找 Xn 定义 ===")
for m in re.finditer(r'\bXn\s*=\s*function', js):
    s = m.start()
    print("  @%d: %s" % (s, js[s:s + 900].replace("\n", " ")))
    print()
    break

print("=" * 70)
print("=== 3. 找 ud 定义 ===")
for m in re.finditer(r'\bud\s*=\s*function', js):
    s = m.start()
    print("  @%d: %s" % (s, js[s:s + 700].replace("\n", " ")))
    print()
    break

print("=" * 70)
print("=== 4. 找 w[] 的键名表（w 是混淆字符串表）===")
m = re.search(r'\bw\s*=\s*\[', js)
if m:
    s = m.start()
    print(js[s:s + 1500].replace("\n", " "))
else:
    print("  没找到 w 数组")