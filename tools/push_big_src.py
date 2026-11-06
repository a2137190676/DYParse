#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""推送超过 1MB 的源文件到 GitHub / Gitee（绕开 contents API 的 JSON 体积限制）

背景（2026-11-06 实测）：
  push_github.sh / push_gitee.sh 都走 PUT /contents/{path}，
  请求体是 {"content": "<base64>"} 的 JSON。
  MainActivity.java 现在 1172428 字节，base64 后约 1563240 字节，
  加上 JSON 外壳后整个请求体超过 1MB，两端都会失败：
    GitHub: `Body should be a JSON object`（其实是 body 被截断）
    Gitee : `UPD FAIL`

解法：
  GitHub 走 Git Data API（blobs -> trees -> commits -> refs），
  单 blob 上限 100MB，完全够用。

  Gitee 的 v5 API 没有等价的 blobs 接口对外文档化，
  但它同样支持 POST /repos/{owner}/{repo}/git/blobs，
  实测可用；若不可用则退化为「分片提交」——把大文件拆成
  MainActivity.java.part1 / .part2 ... 并额外提交一个还原脚本。
  这是最后一招，优先保证能用。

用法：
  python3 tools/push_big_src.py github
  python3 tools/push_big_src.py gitee
"""
import base64
import json
import os
import sys
import time
import urllib.request

ROOT = "/storage/emulated/0/MT2/mcp/DYParse"
GH_TOKEN_FILE = "/storage/emulated/0/Download/github_token.txt"
GT_TOKEN_FILE = "/storage/emulated/0/Download/gitee_token.txt"

GH_OWNER, GH_REPO, GH_BRANCH = "a2137190676", "DYParse", "main"
GT_OWNER, GT_REPO, GT_BRANCH = "adaaixianzun", "dyparse", "master"

# 需要走大文件通道的文件（相对仓库根）
BIG_FILES = [
    "java/com/re/douyinparse/MainActivity.java",
]


def http(url, method="GET", data=None, token=None, timeout=240, accept=None):
    h = {"Accept": accept or "application/json"}
    if token:
        h["Authorization"] = "token " + token
    body = None
    if data is not None:
        body = json.dumps(data).encode()
        h["Content-Type"] = "application/json"
    r = urllib.request.Request(url, data=body, headers=h, method=method)
    try:
        with urllib.request.urlopen(r, timeout=timeout) as resp:
            return resp.status, json.loads(resp.read().decode() or "{}")
    except urllib.error.HTTPError as e:
        try:
            return e.code, json.loads(e.read().decode() or "{}")
        except Exception:
            return e.code, {}
    except Exception as e:
        return -1, {"err": str(e)[:180]}


# ---------------- GitHub ----------------

def push_github():
    token = open(GH_TOKEN_FILE).read().strip()
    api = "https://api.github.com/repos/%s/%s" % (GH_OWNER, GH_REPO)
    print("=== GitHub Git Data API ===")

    entries = []
    for rel in BIG_FILES:
        lp = os.path.join(ROOT, rel)
        raw = open(lp, "rb").read()
        st, r = http(api + "/git/blobs", "POST",
                     {"content": base64.b64encode(raw).decode(), "encoding": "base64"},
                     token=token)
        if st not in (200, 201):
            print("  BLOB_ERR", rel, st, str(r)[:160])
            return False
        print("  blob OK %s sha=%s size=%d" % (rel, r["sha"][:10], len(raw)))
        entries.append({"path": rel, "mode": "100644", "type": "blob", "sha": r["sha"]})
        time.sleep(2)

    st, ref = http(api + "/git/ref/heads/" + GH_BRANCH, token=token)
    if st != 200:
        print("  REF_ERR", st, str(ref)[:160])
        return False
    base = ref["object"]["sha"]
    print("  base commit =", base[:10])

    st, cm = http(api + "/git/commits/" + base, token=token)
    if st != 200:
        print("  COMMIT_ERR", st)
        return False
    base_tree = cm["tree"]["sha"]

    st, tr = http(api + "/git/trees", "POST",
                  {"base_tree": base_tree, "tree": entries}, token=token)
    if st not in (200, 201):
        print("  TREE_ERR", st, str(tr)[:160])
        return False
    print("  tree =", tr["sha"][:10])
    time.sleep(2)

    st, nc = http(api + "/git/commits", "POST",
                  {"message": "chore: 更新 MainActivity（大文件通道）",
                   "tree": tr["sha"], "parents": [base]}, token=token)
    if st not in (200, 201):
        print("  NEWCOMMIT_ERR", st, str(nc)[:160])
        return False
    print("  commit =", nc["sha"][:10])
    time.sleep(2)

    st, _ = http(api + "/git/refs/heads/" + GH_BRANCH, "PATCH",
                 {"sha": nc["sha"]}, token=token)
    if st not in (200, 201):
        print("  PATCH_ERR", st)
        return False
    print("  branch moved OK")
    return True


# ---------------- Gitee ----------------

def push_gitee():
    token = open(GT_TOKEN_FILE).read().strip()
    api = "https://gitee.com/api/v5/repos/%s/%s" % (GT_OWNER, GT_REPO)
    print("=== Gitee 尝试 blobs 接口 ===")

    entries = []
    for rel in BIG_FILES:
        lp = os.path.join(ROOT, rel)
        raw = open(lp, "rb").read()
        st, r = http(api + "/git/blobs", "POST",
                     {"access_token": token,
                      "content": base64.b64encode(raw).decode(),
                      "encoding": "base64"}, token=token)
        if st in (200, 201) and r.get("sha"):
            print("  blob OK %s sha=%s size=%d" % (rel, r["sha"][:10], len(raw)))
            entries.append({"path": rel, "mode": "100644", "type": "blob", "sha": r["sha"]})
        else:
            print("  blob 不可用（status=%s），改走分片方案" % st)

    if not entries:
        return push_gitee_split(token, api)

    st, ref = http(api + "/git/refs/heads/" + GT_BRANCH, token=token)
    if st != 200:
        # Gitee 用 /branches/{branch}
        st, ref = http(api + "/branches/" + GT_BRANCH, token=token)
        if st != 200:
            print("  REF_ERR", st, str(ref)[:160])
            return push_gitee_split(token, api)
    base = (ref.get("commit") or {}).get("sha") or (ref.get("object") or {}).get("sha")
    print("  base commit =", str(base)[:10])

    st, tr = http(api + "/git/trees", "POST",
                  {"access_token": token, "base_tree": base, "tree": entries}, token=token)
    if st not in (200, 201):
        print("  TREE_ERR", st, str(tr)[:160])
        return push_gitee_split(token, api)
    print("  tree =", tr["sha"][:10])

    st, nc = http(api + "/git/commits", "POST",
                  {"access_token": token, "message": "chore: 更新 MainActivity（大文件通道）",
                   "tree": tr["sha"], "parents": [base]}, token=token)
    if st not in (200, 201):
        print("  NEWCOMMIT_ERR", st, str(nc)[:160])
        return push_gitee_split(token, api)
    print("  commit =", nc["sha"][:10])

    st, _ = http(api + "/git/refs/heads/" + GT_BRANCH, "PATCH",
                 {"access_token": token, "sha": nc["sha"]}, token=token)
    if st not in (200, 201):
        print("  PATCH_ERR", st)
        return push_gitee_split(token, api)
    print("  branch moved OK")
    return True


def push_gitee_split(token, api):
    """Gitee 兜底：把大文件按 700KB 分片提交，附还原脚本。

    这是最后手段。仓库里会出现
      java/com/re/douyinparse/MainActivity.java.partN
      tools/join_mainactivity.sh
    读者克隆后跑一次 join_mainactivity.sh 即可还原完整源码。
    """
    print("=== Gitee 分片兜底 ===")
    CHUNK = 700 * 1024
    for rel in BIG_FILES:
        lp = os.path.join(ROOT, rel)
        raw = open(lp, "rb").read()
        parts = [raw[i:i + CHUNK] for i in range(0, len(raw), CHUNK)]
        print("  %s 共 %d 片" % (rel, len(parts)))
        for idx, chunk in enumerate(parts):
            prel = "%s.part%d" % (rel, idx + 1)
            ok = _put_small(token, api, prel, chunk,
                            "chore: %s 分片 %d/%d" % (os.path.basename(rel), idx + 1, len(parts)))
            print("    part%d %s" % (idx + 1, "OK" if ok else "FAIL"))
            time.sleep(4)
        # 还原脚本
        script = """#!/bin/sh
# 还原被分片的大文件（Gitee 单文件请求体限制 1MB 的规避方案）
# 用法：在仓库根目录执行 sh tools/join_mainactivity.sh
set -e
SRC="java/com/re/douyinparse/MainActivity.java"
rm -f "$SRC"
for f in "$SRC".part*; do
  cat "$f" >> "$SRC"
done
echo "已还原 $SRC"
ls -l "$SRC"
"""
        ok = _put_small(token, api, "tools/join_mainactivity.sh",
                        script.encode("utf-8"), "chore: 加大文件还原脚本")
        print("  join 脚本 %s" % ("OK" if ok else "FAIL"))
    return True


def _put_small(token, api, rel, raw, msg):
    """用 contents API 提交一个小于 1MB 的文件"""
    b64 = base64.b64encode(raw).decode()
    st, cur = http(api + "/contents/" + rel + "?access_token=" + token, token=token)
    payload = {"access_token": token, "content": b64, "message": msg, "branch": GT_BRANCH}
    if st == 200 and cur.get("sha"):
        payload["sha"] = cur["sha"]
        st2, _ = http(api + "/contents/" + rel, "PUT", payload, token=token)
    else:
        st2, _ = http(api + "/contents/" + rel, "POST", payload, token=token)
    return st2 in (200, 201)


if __name__ == "__main__":
    who = (sys.argv[1] if len(sys.argv) > 1 else "").lower()
    if who == "github":
        sys.exit(0 if push_github() else 1)
    elif who == "gitee":
        sys.exit(0 if push_gitee() else 1)
    else:
        print("用法: python3 tools/push_big_src.py github|gitee")
        sys.exit(2)