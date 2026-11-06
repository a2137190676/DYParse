# -*- coding: utf-8 -*-
"""看看 api-h2 返回的 52KB 是什么"""
import urllib.request, gzip

ITEM = "7637720726061026578"
UA = "com.ss.android.ugc.trill/2613 (Linux; U; Android 12; zh_CN; Pixel 5; Build/SQ3A.220705.004; Cronet/TTNetVersion:b4d74d15 2023-04-19)"
COMMON = ("&aid=1180&app_name=trill&version_code=2613&version_name=26.1.3"
          "&device_platform=android&os=android&os_version=12&device_type=Pixel+5"
          "&channel=googleplay&app_language=en&region=US&language=en"
          "&iid=7318518857994389254&device_id=7318517321748022790"
          "&openudid=8f8c1b0a1b0c8b1a&resolution=1080*2340&dpi=440"
          "&ac=wifi&ssmix=a&manifest_version_code=261301&update_version_code=261301")

url = "https://api-h2.tiktokv.com/aweme/v1/feed/?aweme_id=%s%s" % (ITEM, COMMON)
h = {"User-Agent": UA, "Accept": "application/json", "Accept-Encoding": "gzip", "Connection": "close"}
r = urllib.request.urlopen(urllib.request.Request(url, headers=h), timeout=25)
raw = r.read()
if "gzip" in r.headers.get("Content-Encoding", ""):
    raw = gzip.decompress(raw)
txt = raw.decode("utf-8", "ignore")
print("len", len(txt))
print("head:", txt[:300].replace("\n", " "))
print("hasImagePost", "image_post_info" in txt, "imagePost" in txt)
print("hasAweme", "aweme_list" in txt, "aweme_detail" in txt)
open("/tmp/tt_feed.json", "w").write(txt)