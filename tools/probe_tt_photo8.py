# -*- coding: utf-8 -*-
"""试 TikTok 移动端 API 拿 photo 作品数据"""
import urllib.request, gzip, json

ITEM = "7637720726061026578"
UA = "com.ss.android.ugc.trill/2613 (Linux; U; Android 12; zh_CN; Pixel 5; Build/SQ3A.220705.004; Cronet/TTNetVersion:b4d74d15 2023-04-19)"

COMMON = ("&aid=1180&app_name=trill&version_code=2613&version_name=26.1.3"
          "&device_platform=android&os=android&os_version=12&device_type=Pixel+5"
          "&channel=googleplay&app_language=en&region=US&language=en"
          "&iid=7318518857994389254&device_id=7318517321748022790"
          "&openudid=8f8c1b0a1b0c8b1a&resolution=1080*2340&dpi=440"
          "&ac=wifi&ssmix=a&manifest_version_code=261301&update_version_code=261301")

HOSTS = [
    "https://api16-normal-c-useast1a.tiktokv.com",
    "https://api19-normal-c-useast1a.tiktokv.com",
    "https://api22-normal-c-useast2a.tiktokv.com",
    "https://api-h2.tiktokv.com",
]

def get(url, timeout=20):
    h = {"User-Agent": UA, "Accept": "application/json", "Accept-Encoding": "gzip",
         "Connection": "close"}
    try:
        r = urllib.request.urlopen(urllib.request.Request(url, headers=h), timeout=timeout)
        raw = r.read()
        if "gzip" in r.headers.get("Content-Encoding", ""):
            try: raw = gzip.decompress(raw)
            except Exception: pass
        return r.status, raw.decode("utf-8", "ignore")
    except Exception as e:
        return -1, "ERR " + str(e)[:100]

paths = [
  ("aweme/detail", "/aweme/v1/aweme/detail/?aweme_id=%s" % ITEM),
  ("aweme/post",   "/aweme/v1/feed/?aweme_id=%s" % ITEM),
  ("multi/aweme",  "/aweme/v1/multi/aweme/detail/?aweme_ids=%%5B%s%%5D" % ITEM),
]
for host in HOSTS:
    for name, p in paths:
        st, b = get(host + p + COMMON)
        print("--- %s %s status=%s len=%s" % (host.split("//")[1].split(".")[0], name, st, len(b)))
        print("   ", b[:220].replace("\n", " "))
        if st == 200 and len(b) > 500:
            try:
                j = json.loads(b)
                det = j.get("aweme_detail") or (j.get("aweme_list") or [{}])[0]
                if isinstance(det, dict) and det.get("aweme_id"):
                    print("    *** GOT aweme_id=", det.get("aweme_id"))
                    print("    keys:", list(det.keys())[:30])
                    print("    image_post_info:", json.dumps(det.get("image_post_info"))[:600])
            except Exception as e:
                print("    parse err", e)
        print()