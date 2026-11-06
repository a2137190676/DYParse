# -*- coding: utf-8 -*-
"""提取 article 块，看每条推文的文本/图片/视频在哪"""
import re

s = open("/tmp/x_profile.html", encoding="utf-8").read()

# 按 data-href="/user/status/id" 的 li 切块
blocks = re.split(r'(?=<li><div aria-labelledby)', s)
print("blocks", len(blocks))

n = 0
for b in blocks:
    m = re.search(r'data-href="/([A-Za-z0-9_]+)/status/(\d+)"', b)
    if not m:
        continue
    n += 1
    if n > 2:
        break
    print("=" * 60)
    print("id:", m.group(2), "user:", m.group(1), "blockLen:", len(b))
    # 文本
    txt = re.findall(r'<div[^>]*data-testid="tweetText"[^>]*>(.*?)</div>', b, re.S)
    print("tweetText blocks:", len(txt))
    if txt:
        t = re.sub(r'<[^>]+>', '', txt[0])
        print("  text:", t[:100])
    # 图片
    pics = re.findall(r'https://pbs\.twimg\.com/[^"\\\s]+', b)
    print("pbs:", len(pics))
    for p in pics[:3]:
        print("   ", p[:100])
    # 视频 mp4
    vids = re.findall(r'https://video\.twimg\.com/[^"\\\s]+\.mp4[^"\\\s]*', b)
    print("mp4:", len(vids))
    for v in vids[:3]:
        print("   ", v[:110])
    # 时长
    dur = re.findall(r'"video_info".{0,400}?"duration_millis"\s*:\s*(\d+)', b)
    print("duration:", dur[:2])
    # 播放量
    pv = re.findall(r'(\d[\d,\.]*[KMB]?)\s*(?:views|次观看|プレビュー)', b)
    print("views hint:", pv[:3])