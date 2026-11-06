#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""单独推送大文件到 GitHub（绕开 contents API 的 1MB 限制）

背景：
  push_github.sh 走的是 PUT /repos/{owner}/{repo}/contents/{path}，
  这个接口在文件超过 1MB 时会报 `Body should be a JSON object`
  （实测 MainActivity.java 1172428 字节必失败）。

解法：
  改走 Git Data API 三步：
    1) POST /git/blobs          <- 直接传二进制内容，拿 blob sha（上限 100MB）
    2) GET  /git/ref/heads/main <- 拿当前 commit sha
    3) POST /git/trees          <- 用 blob 生成新 tree（base_tree 保留其余文件）
    4) POST /git/commits        <- 建新 commit
    5) PATCH /git/refs/heads/main <- 移动分支
"""
import base64
import json
import os
import sys
import urllib.request

ROOT = "/storage/emulated/0/MT2/mcp/DYParse"
TOKEN_FILE = "/storage/emulated/0/Download/github_token.txt"
OWNER = "a2137190676"
REPO = "DYParse"
BRANCH = "main"

TOKEN = open(TOKEN_FILE).read().strip()
API = "https://api.github.com"


def req(url, method="GET", data=None, timeout=180):
    h = {
        "Authorization": "token " + TOKEN,
        "Accept": "application/vnd.github+json",
        "X-GitHub-Api-Version": "2022-11-28",
    }
    body = None
    if data is not None:
        body = json.dumps(data).encode()
        h["Content-Type"] = "application/json"
    r = urllib.request.Request(url, data=body, headers=h, method=method)
    try:
        with urllib.request.urlopen(r, timeout=timeout) as resp:
            return resp.status, json.loads(resp.read().decode())
    except urllib.error.HTTPError as e:
        try:
            return e.code, json.loads(e.read().decode())
        except Exception:
            return e.code, {}
    except Exception as e:
        return -1, {"err": str(e)[:160]}


def push_blobs(files):
    """files: [(repo_path, local_path)]"""
    # 1) 逐个建 blob
    entries = []
    for repo_path, local_path in files:
        raw = open(local_path, "rb").read()
        st, r = req(API + "/repos/%s/%s/git/blobs" % (OWNER, REPO),
                    "POST", {"content": base64.b64encode(raw).decode(),
                             "encoding": "base64"})
        if st not in (200, 201):
            print("  BLOB_ERR %s status=%s %s" % (repo_path, st, str(r)[:140]))
            return False
        print("  blob OK %s sha=%s size=%d" % (repo_path, r["sha"][:10], len(raw)))
        entries.append({"path": repo_path, "mode": "100644", "type": "blob", "sha": r["sha"]})

    # 2) 拿分支当前 commit
    st, ref = req(API + "/repos/%s/%s/git/ref/heads/%s" % (OWNER, REPO, BRANCH))
    if st != 200:
        print("  REF_ERR", st, str(ref)[:140])
        return False
    base_sha = ref["object"]["sha"]
    print("  base commit =", base_sha[:10])

    # 3) 拿 base tree
    st, cm = req(API + "/repos/%s/%s/git/commits/%s" % (OWNER, REPO, base_sha))
    if st != 200:
        print("  COMMIT_ERR", st, str(cm)[:140])
        return False
    base_tree = cm["tree"]["sha"]

    # 4) 建新 tree
    st, tr = req(API + "/repos/%s/%s/git/trees" % (OWNER, REPO), "POST",
                 {"base_tree": base_tree, "tree": entries})
    if st not in (200, 201):
        print("  TREE_ERR", st, str(tr)[:160])
        return False
    print("  tree =", tr["sha"][:10])

    # 5) 建 commit
    st, nc = req(API + "/repos/%s/%s/git/commits" % (OWNER, REPO), "POST",
                 {"message": "chore: update large sources", "tree": tr["sha"],
                  "parents": [base_sha]})
    if st not in (200, 201):
        print("  NEWCOMMIT_ERR", st, str(nc)[:160])
        return False
    print("  commit =", nc["sha"][:10])

    # 6) 移动分支
    st, _ = req(API + "/repos/%s/%s/git/refs/heads/%s" % (OWNER, REPO, BRANCH),
                "PATCH", {"sha": nc["sha"]})
    if st not in (200, 201):
        print("  PATCH_ERR", st)
        return False
    print("  branch moved OK")
    return True


if __name__ == "__main__":
    targets = [
        ("java/com/re/douyinparse/MainActivity.java",
         os.path.join(ROOT, "java/com/re/douyinparse/MainActivity.java")),
    ]
    print("=== 推送大文件（Git Data API）===")
    ok = push_blobs(targets)
    print("=== DONE" if ok else "=== FAILED")
    sys.exit(0 if ok else 1)