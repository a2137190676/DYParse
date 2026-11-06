#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""给已存在的 Gitee Release 上传 APK"""
import json, os, urllib.request, urllib.parse

ROOT = "/storage/emulated/0/MT2/mcp/DYParse"
RID = 1269912
APK = os.path.join(ROOT, "releases/encrypted/DYParse-v2.1.0.apk")
token = open("/storage/emulated/0/Download/gitee_token.txt").read().strip()
base = "https://gitee.com/api/v5/repos/adaaixianzun/dyparse"
q = urllib.parse.urlencode({"access_token": token})
url = "%s/releases/%d/attach_files?%s" % (base, RID, q)

boundary = "----dypboundary7MA4YWxkTrZu0gW"
fn = os.path.basename(APK)
content = open(APK, "rb").read()
parts = []
parts.append(("--" + boundary + "\r\n").encode())
parts.append(('Content-Disposition: form-data; name="file"; filename="%s"\r\n' % fn).encode())
parts.append(b"Content-Type: application/octet-stream\r\n\r\n")
parts.append(content)
parts.append(("\r\n--" + boundary + "--\r\n").encode())
data = b"".join(parts)
print("payload MB", round(len(data) / 1048576, 1))

req = urllib.request.Request(url, data=data, method="POST",
                             headers={"Content-Type": "multipart/form-data; boundary=" + boundary})
try:
    with urllib.request.urlopen(req, timeout=900) as r:
        print("upload", r.status, r.read()[:400])
except urllib.error.HTTPError as e:
    print("upload ERR", e.code, e.read()[:400])
except Exception as e:
    print("upload ERR", e)