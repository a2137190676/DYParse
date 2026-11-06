# -*- coding: utf-8 -*-
"""看 nitter 返回的实际内容"""
import urllib.request, re
UA = ("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
      "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36")
req = urllib.request.Request("https://nitter.net/gomimata", headers={"User-Agent": UA})
b = urllib.request.urlopen(req, timeout=18).read().decode("utf-8", "ignore")
print("len", len(b))
print("---- title ----")
m = re.search(r"<title>(.*?)</title>", b, re.S)
print(m.group(1).strip() if m else "(none)")
print("---- body 前 1200 ----")
body = re.sub(r"<script.*?</script>", "", b, flags=re.S)
body = re.sub(r"<style.*?</style>", "", body, flags=re.S)
body = re.sub(r"<[^>]+>", " ", body)
body = re.sub(r"\s+", " ", body)
print(body[:1200])