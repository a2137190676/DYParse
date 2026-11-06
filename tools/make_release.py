#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""建 Gitee / GitHub Release 并上传 APK（加密版）"""
import json, os, sys, urllib.request, urllib.parse

ROOT = "/storage/emulated/0/MT2/mcp/DYParse"
VER = "v2.1.1"
NAME = "v2.1.1 YouTube 解析恢复"
APK = os.path.join(ROOT, "releases/encrypted/DYParse-v2.1.1.apk")

d = json.load(open(os.path.join(ROOT, "version.json"), encoding="utf-8"))
BODY = d["body"]


def api(url, token, method="GET", data=None, ctype="application/json"):
    hh = {"Authorization": "token " + token, "Accept": "application/json"}
    if data is not None:
        hh["Content-Type"] = ctype
    req = urllib.request.Request(url, data=data, headers=hh, method=method)
    try:
        with urllib.request.urlopen(req, timeout=120) as r:
            return r.status, r.read()
    except urllib.error.HTTPError as e:
        return e.code, e.read()
    except Exception as e:
        return -1, str(e).encode()


def gitee():
    token = open("/storage/emulated/0/Download/gitee_token.txt").read().strip()
    base = "https://gitee.com/api/v5/repos/adaaixianzun/dyparse"
    print("=== Gitee 建 Release ===")
    payload = json.dumps({
        "access_token": token,
        "tag_name": VER,
        "name": NAME,
        "body": BODY,
        "target_commitish": "master",
        "prerelease": False,
    }, ensure_ascii=False).encode("utf-8")
    st, body = api(base + "/releases", token, "POST", payload)
    print("  create", st, body[:200])
    if st not in (200, 201):
        return
    rid = json.loads(body)["id"]
    print("  release id =", rid)

    # 上传 APK
    print("=== Gitee 上传 APK ===")
    q = urllib.parse.urlencode({"access_token": token})
    up = "%s/releases/%d/attach_files?%s" % (base, rid, q)
    boundary = "----dypboundary7MA4YWxkTrZu0gW"
    fn = os.path.basename(APK)
    with open(APK, "rb") as f:
        content = f.read()
    parts = []
    parts.append(("--" + boundary + "\r\n").encode())
    parts.append(('Content-Disposition: form-data; name="file"; filename="%s"\r\n' % fn).encode())
    parts.append(b"Content-Type: application/octet-stream\r\n\r\n")
    parts.append(content)
    parts.append(("\r\n--" + boundary + "--\r\n").encode())
    data = b"".join(parts)
    st2, body2 = api(up, token, "POST", data,
                     "multipart/form-data; boundary=" + boundary)
    print("  upload", st2, body2[:300])


def github():
    token = open("/storage/emulated/0/Download/github_token.txt").read().strip()
    base = "https://api.github.com/repos/a2137190676/DYParse"
    print("=== GitHub 建 Release ===")
    payload = json.dumps({
        "tag_name": VER,
        "name": NAME,
        "body": BODY,
        "target_commitish": "main",
        "draft": False,
        "prerelease": False,
    }, ensure_ascii=False).encode("utf-8")
    st, body = api(base + "/releases", token, "POST", payload)
    print("  create", st, body[:200])
    if st not in (200, 201):
        return
    j = json.loads(body)
    rid = j["id"]
    upload_url = j["upload_url"].split("{")[0]
    print("  release id =", rid)

    print("=== GitHub 上传 APK ===")
    fn = os.path.basename(APK)
    up = upload_url + "?name=" + urllib.parse.quote(fn)
    hh = {"Authorization": "token " + token,
          "Content-Type": "application/octet-stream",
          "Accept": "application/vnd.github+json"}
    with open(APK, "rb") as f:
        content = f.read()
    req = urllib.request.Request(up, data=content, headers=hh, method="POST")
    try:
        with urllib.request.urlopen(req, timeout=600) as r:
            print("  upload", r.status, r.read()[:200])
    except urllib.error.HTTPError as e:
        print("  upload ERR", e.code, e.read()[:300])
    except Exception as e:
        print("  upload ERR", e)


if __name__ == "__main__":
    which = sys.argv[1] if len(sys.argv) > 1 else "both"
    if which in ("gitee", "both"):
        gitee()
    if which in ("github", "both"):
        github()
