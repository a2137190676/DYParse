# -*- coding: utf-8 -*-
"""照 App 的 TtParser 解析逻辑，对 @yingg433/7599804244505398550 做一次逐模板检查"""
import urllib.request, urllib.parse, gzip, json, re

UA = "Mozilla/5.0 (Linux; Android 12; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
REAL = "https://www.tiktok.com/@yingg433/photo/7599804244505398550"
ID = "7599804244505398550"

def get(url, timeout=30):
    h = {"User-Agent": UA, "Accept": "text/html,*/*", "Accept-Encoding": "gzip",
         "Referer": "https://www.tiktok.com/", "Connection": "close"}
    try:
        r = urllib.request.urlopen(urllib.request.Request(url, headers=h), timeout=timeout)
        raw = r.read()
        if "gzip" in r.headers.get("Content-Encoding", ""):
            try: raw = gzip.decompress(raw)
            except Exception: pass
        return r.status, raw.decode("utf-8", "ignore")
    except Exception as e:
        return -1, "ERR " + str(e)[:100]

def extract_script(html, sid):
    i = html.find('id="%s"' % sid)
    if i < 0: return None
    ls = html.rfind("<script", 0, i)
    if ls < 0: return None
    s = html.find(">", i)
    if s < 0: return None
    e = html.find("</script>", s)
    if e < 0: return None
    body = html[s + 1:e].strip()
    if not body: return None
    if body.startswith("{"): return body
    a0 = body.find("{")
    return body[a0:] if a0 >= 0 else body

print("=== 1. 拉页面 ===")
st, html = get(REAL)
print("  status=%s len=%d" % (st, len(html)))
print("  imagePost=%s imageURL=%s UDR=%s api-data=%s photomode=%s"
      % ("imagePost" in html, "imageURL" in html,
         "__UNIVERSAL_DATA_FOR_REHYDRATION__" in html, 'id="api-data"' in html,
         "photomode" in html.lower()))

print()
print("=== 2. 模板 A: __UNIVERSAL_DATA_FOR_REHYDRATION__ ===")
udr = extract_script(html, "__UNIVERSAL_DATA_FOR_REHYDRATION__")
if udr:
    print("  len=%d" % len(udr))
    try:
        d = json.loads(udr)
        scope = d.get("__DEFAULT_SCOPE__", {})
        print("  scope keys:", list(scope.keys()))
        for k in scope:
            if "detail" in k:
                v = scope[k]
                print("   ", k, "->", "sc=" + str(v.get("statusCode")) if isinstance(v, dict) else type(v))
                if isinstance(v, dict):
                    it = (v.get("itemInfo") or {}).get("itemStruct")
                    if isinstance(it, dict):
                        print("        itemStruct.id =", it.get("id"))
                        print("        imagePost 存在 =", "imagePost" in it)
                        if "imagePost" in it:
                            print("        imagePost =", json.dumps(it["imagePost"])[:400])
    except Exception as e:
        print("  JSON err:", e)
else:
    print("  没有 UDR")

print()
print("=== 3. 模板 B: api-data ===")
api = extract_script(html, "api-data")
if api:
    print("  len=%d" % len(api))
    print("  head:", api[:250])
    try:
        d = json.loads(api)
        print("  top keys:", list(d.keys()))
        for k, v in d.items():
            if isinstance(v, dict):
                print("   ", k, "sc=", v.get("statusCode"), "subkeys=", list(v.keys()))
                it = (v.get("itemInfo") or {}).get("itemStruct")
                if isinstance(it, dict):
                    print("        id=", it.get("id"), "imagePost=", "imagePost" in it)
                    if "imagePost" in it:
                        print("        imagePost=", json.dumps(it["imagePost"])[:500])
    except Exception as e:
        print("  JSON err:", e)
else:
    print("  没有 api-data")

print()
print("=== 4. 页面里 imagePost 具体出现在哪 ===")
for m in re.finditer(r'imagePost', html):
    a = max(0, m.start() - 80)
    print("  ...", html[a:m.start() + 200].replace("\n", " ")[:280])
    break
# 找所有 script id
print()
print("=== 5. 所有 script id ===")
print(" ", re.findall(r'<script[^>]*id="([^"]+)"', html))