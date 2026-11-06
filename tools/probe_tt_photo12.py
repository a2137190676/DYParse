# -*- coding: utf-8 -*-
"""换多个 photo 作品，看是否都 10222"""
import urllib.request, http.cookiejar, gzip, json, re

UA = "Mozilla/5.0 (Linux; Android 12; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
cj = http.cookiejar.CookieJar()
op = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(cj))

def get(url, timeout=30):
    h = {"User-Agent": UA, "Accept": "text/html,*/*", "Accept-Language": "en-US,en;q=0.9",
         "Accept-Encoding": "gzip", "Referer": "https://www.tiktok.com/", "Connection": "close"}
    try:
        r = op.open(urllib.request.Request(url, headers=h), timeout=timeout)
        raw = r.read()
        if "gzip" in r.headers.get("Content-Encoding", ""):
            try: raw = gzip.decompress(raw)
            except Exception: pass
        return r.status, raw.decode("utf-8", "ignore")
    except Exception as e:
        return -1, "ERR " + str(e)[:100]

get("https://www.tiktok.com/")

def probe(tag, url):
    st, h = get(url)
    api = ""
    m2 = re.search(r'id="api-data"[^>]*>(.*?)</script>', h, re.S)
    if m2: api = m2.group(1).strip()
    sc = ""
    if api:
        try:
            sc = str(json.loads(api).get("videoDetail", {}).get("statusCode"))
        except Exception: sc = "?"
    print("  %-46s len=%-7d api=%-6d sc=%-6s ip=%s" % (tag, len(h), len(api), sc, "imagePost" in h))
    return h

# 找一个热门账号的 photo 作品：先用 TikTok 搜索页太麻烦，直接试几个已知 id 格式
# 从上一轮主页解析里已经有 TT 作品，先拿一个账号的作品列表缓存
print("=== 试多个 photo 作品 ID ===")
ids = [
  ("lex.8889", "7637720726061026578"),
]
for u, i in ids:
    probe("%s/photo/%s" % (u, i), "https://www.tiktok.com/@%s/photo/%s" % (u, i))

print("=== 试 photo 作品但用 /video/ 路径 ===")
probe("lex.8889/video/7637...", "https://www.tiktok.com/@lex.8889/video/7637720726061026578")

print("=== 试 explore / discover 页拿 photo id ===")
st, h = get("https://www.tiktok.com/explore")
print("  explore len", len(h), "status", st)
photo_ids = re.findall(r'/@([\w.\-]+)/photo/(\d{15,25})', h)
print("  found photo ids:", photo_ids[:5])

print("=== 试 tag 页 ===")
st, h2 = get("https://www.tiktok.com/tag/photography")
print("  tag len", len(h2))
photo_ids2 = re.findall(r'/@([\w.\-]+)/photo/(\d{15,25})', h2)
print("  found photo ids:", photo_ids2[:5])