# -*- coding: utf-8 -*-
"""从 RSC 数据流里提取推文：media_entities2 + full_text + video_info"""
import re, json, sys

s = open("/tmp/x_profile.html", encoding="utf-8").read()

# RSC 里字符串是多层转义的，先反转义一份方便正则
u = s.replace('\\\\"', '"').replace('\\"', '"')
print("len raw=%d unesc=%d" % (len(s), len(u)))

# 找 full_text
ft = re.findall(r'"full_text"\s*:\s*"((?:[^"\\]|\\.)*)"', u)
print("full_text n=", len(ft))
for t in ft[:5]:
    print("   ", t[:90])

# 找 video_info / variants
print("---- variants ----")
vs = re.findall(r'"content_type"\s*:\s*"video/mp4"[^}]*?"url"\s*:\s*"([^"]+)"', u)
print("mp4 urls:", len(vs))
for v in vs[:6]:
    print("   ", v[:110])

# duration
dur = re.findall(r'"duration_millis"\s*:\s*"?(?:(\d+)")?', u)
print("duration_millis:", dur[:6])

# media_url_https
mu = re.findall(r'"media_url_https"\s*:\s*"([^"]+)"', u)
print("media_url_https:", len(mu))
for m in mu[:5]:
    print("   ", m[:100])

# id_str
ids = re.findall(r'"id_str"\s*:\s*"(\d{15,25})"', u)
print("id_str n=", len(ids), ids[:6])