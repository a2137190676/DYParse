# -*- coding: utf-8 -*-
"""分析 x_profile.html 里推文块的边界，找一个稳定的正则锚点"""
import re

s = open("/tmp/x_profile.html", encoding="utf-8").read()
print("len", len(s))

# 找 status 出现的上下文
for m in list(re.finditer(r'/status/(\d{10,25})', s))[:6]:
    a = max(0, m.start() - 200)
    b = min(len(s), m.end() + 200)
    print("---- pos", m.start(), "id", m.group(1))
    print(repr(s[a:b])[:420])
    print()