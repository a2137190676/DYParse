# -*- coding: utf-8 -*-
"""对比视频页 / photo 页的 api-data 与 UDR statusCode；多试几个 photo 作品"""
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

get("https://www.tiktok.com/")  # 拿 cookie

def probe(tag, url):
    st, h = get(url)
    api_len = 0
    m2 = re.search(r'id="api-data"[^>]*>(.*?)</script>', h, re.S)
    if m2: api_len = len(m2.group(1).strip())
    udr_status = ""
    m = re.search(r'id="__UNIVERSAL_DATA_FOR_REHYDRATION__"[^>]*>(.*?)</script>', h, re.S)
    if m:
        try:
            d = json.loads(m.group(1).strip())
            for k, v in (d.get("__DEFAULT_SCOPE__") or {}).items():
                if "detail" in k and isinstance(v, dict):
                    udr_status = "%s:sc=%s" % (k.split(".")[-2] if k.count(".") > 1 else k, v.get("statusCode"))
        except Exception as e:
            udr_status = "jsonErr"
    print("  %-52s len=%-7d apidata=%-7d ip=%-5s %s"
          % (tag, len(h), api_len, "imagePost" in h, udr_status))

print("=== 对照：视频作品 ===")
probe("@united.state.3357/video", "https://www.tiktok.com/@united.state.3357/video/7688168881160080662")

print("=== 多个 photo 作品 ===")
cases = [
  ("@lex.8889/photo", "https://www.tiktok.com/@lex.8889/photo/7637720726061026578"),
]
# 从之前日志里再找
print("=== api-data 内容（视频页）===")
st, h = get("https://www.tiktok.com/@united.state.3357/video/7688168881160080662")
m2 = re.search(r'id="api-data"[^>]*>(.*?)</script>', h, re.S)
if m2:
    body = m2.group(1).strip()
    print("  len", len(body), "head", body[:200])
    try:
        d = json.loads(body)
        print("  top keys:", list(d.keys()))
    except Exception as e:
        print("  err", e)