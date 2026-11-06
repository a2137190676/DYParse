#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""幂等源码推送：比对远端 blob sha，只推有差异的文件

为什么需要这个：
  push_gitee.sh / push_github.sh 每次跑都从头逐个 PUT，
  一旦终端会话结束被连带 kill，重启就得从第 1 个文件重来，
  tools 有 331 个文件，永远推不完。

本脚本：
  1) 拉远端完整 tree（一次 GET），拿到 path -> sha
  2) 本地算 git blob sha1（sha1("blob <len>\\0" + content)）
  3) 只推 sha 不一致的
  4) 大文件（>1MB）自动走 Git Data API

用法：
  python3 tools/sync_src.py github
  python3 tools/sync_src.py gitee
  python3 tools/sync_src.py github tools    # 只同步 tools 目录
"""
import base64
import hashlib
import json
import os
import sys
import time
import urllib.parse
import urllib.request

ROOT = "/storage/emulated/0/MT2/mcp/DYParse"
GH_TOKEN_FILE = "/storage/emulated/0/Download/github_token.txt"
GT_TOKEN_FILE = "/storage/emulated/0/Download/gitee_token.txt"

GH_OWNER, GH_REPO, GH_BRANCH = "a2137190676", "DYParse", "main"
GT_OWNER, GT_REPO, GT_BRANCH = "adaaixianzun", "dyparse", "master"

# 不推的东西
SKIP_NAMES = {"deploy_tt_live.py"}
SKIP_PAT = ("deploy_", "srv_probe")
SKIP_EXT = (".apk", ".jar", ".keystore", ".bak", ".log", ".pyc")

# 参与同步的根目录/文件
INCLUDE_DIRS = ["java", "res", "tools"]
INCLUDE_FILES = ["README.md", "CHANGELOG.md", "version.json", "build.sh",
                 ".gitignore", "CONTRIBUTING.md", "LICENSE",
                 "AndroidManifest.xml", "fetch_libs.sh"]


def http(url, method="GET", data=None, token=None, timeout=240):
    h = {"Accept": "application/json", "User-Agent": "DYParse-sync"}
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
            return e.code, {"err": "http %s" % e.code}
    except Exception as e:
        return -1, {"err": str(e)[:160]}


def blob_sha(data):
    return hashlib.sha1(b"blob %d\0" % len(data) + data).hexdigest()


def skipped(rel):
    base = os.path.basename(rel)
    if base in SKIP_NAMES:
        return True
    for p in SKIP_PAT:
        if base.startswith(p):
            return True
    return rel.endswith(SKIP_EXT)


def collect(only=None):
    out = []
    for d in INCLUDE_DIRS:
        if only and not d.startswith(only):
            continue
        for dirpath, _, files in os.walk(os.path.join(ROOT, d)):
            for f in sorted(files):
                lp = os.path.join(dirpath, f)
                rel = os.path.relpath(lp, ROOT)
                if skipped(rel):
                    continue
                out.append(rel)
    if not only:
        for f in INCLUDE_FILES:
            lp = os.path.join(ROOT, f)
            if os.path.isfile(lp):
                out.append(f)
    return sorted(set(out))


# ---------- GitHub ----------

def remote_gh(token):
    api = "https://api.github.com/repos/%s/%s" % (GH_OWNER, GH_REPO)
    st, tr = http(api + "/git/trees/%s?recursive=1" % GH_BRANCH, token=token)
    if st != 200:
        print("  TREE_ERR", st, str(tr)[:160])
        return None, None
    m = {}
    for e in tr.get("tree", []):
        if e.get("type") == "blob":
            m[e["path"]] = e["sha"]
    return m, tr["sha"]


def sync_gh(only=None, gap=6):
    token = open(GH_TOKEN_FILE).read().strip()
    api = "https://api.github.com/repos/%s/%s" % (GH_OWNER, GH_REPO)
    print("=== GitHub 同步 ===")
    rmap, _ = remote_gh(token)
    if rmap is None:
        return False
    print("  远端文件数 =", len(rmap))

    todo = []
    for rel in collect(only):
        lp = os.path.join(ROOT, rel)
        raw = open(lp, "rb").read()
        if rmap.get(rel) != blob_sha(raw):
            todo.append((rel, raw))
    print("  待推 = %d / %d" % (len(todo), len(collect(only))))
    if not todo:
        print("  已全部一致")
        return True

    ok = fail = 0
    for i, (rel, raw) in enumerate(todo, 1):
        if len(raw) > 1024 * 1024:
            good = _gh_big(api, token, rel, raw)
        else:
            good = _gh_small(api, token, rel, raw)
        if good:
            ok += 1
            print("  [%d/%d] OK   %s" % (i, len(todo), rel))
        else:
            fail += 1
            print("  [%d/%d] FAIL %s" % (i, len(todo), rel))
        time.sleep(gap)
    print("  === 完成 ok=%d fail=%d" % (ok, fail))
    return fail == 0


def _gh_small(api, token, rel, raw):
    for attempt in range(3):
        st, cur = http(api + "/contents/" + urllib.parse.quote(rel) + "?ref=" + GH_BRANCH, token=token)
        payload = {"message": "docs: 同步 " + rel, "branch": GH_BRANCH,
                   "content": base64.b64encode(raw).decode()}
        if st == 200 and isinstance(cur, dict) and cur.get("sha"):
            payload["sha"] = cur["sha"]
        st2, r2 = http(api + "/contents/" + urllib.parse.quote(rel), "PUT", payload, token=token)
        if st2 in (200, 201):
            return True
        msg = str(r2.get("message", ""))[:80]
        if attempt == 2:
            print("       err:", st2, msg)
        time.sleep(5)
    return False


def _gh_big(api, token, rel, raw):
    """>1MB 走 Git Data API 单文件提交（blob -> tree -> commit -> ref）"""
    print("       大文件通道 %s (%d bytes)" % (rel, len(raw)))
    st, b = http(api + "/git/blobs", "POST",
                 {"content": base64.b64encode(raw).decode(), "encoding": "base64"}, token=token)
    if st not in (200, 201):
        print("       BLOB_ERR", st, str(b)[:120])
        return False
    st, ref = http(api + "/git/ref/heads/" + GH_BRANCH, token=token)
    if st != 200:
        return False
    base = ref["object"]["sha"]
    st, cm = http(api + "/git/commits/" + base, token=token)
    if st != 200:
        return False
    st, tr = http(api + "/git/trees", "POST",
                  {"base_tree": cm["tree"]["sha"],
                   "tree": [{"path": rel, "mode": "100644", "type": "blob", "sha": b["sha"]}]},
                  token=token)
    if st not in (200, 201):
        return False
    st, nc = http(api + "/git/commits", "POST",
                  {"message": "docs: 同步 " + rel, "tree": tr["sha"], "parents": [base]}, token=token)
    if st not in (200, 201):
        return False
    st, _ = http(api + "/git/refs/heads/" + GH_BRANCH, "PATCH", {"sha": nc["sha"]}, token=token)
    return st in (200, 201)


# ---------- Gitee ----------

def remote_gt(token):
    api = "https://gitee.com/api/v5/repos/%s/%s" % (GT_OWNER, GT_REPO)
    st, br = http(api + "/branches/" + GT_BRANCH + "?access_token=" + token, token=token)
    if st != 200:
        print("  BRANCH_ERR", st, str(br)[:160])
        return None
    sha = br["commit"]["sha"]
    st, tr = http(api + "/git/trees/" + sha + "?recursive=1&access_token=" + token, token=token)
    if st != 200:
        print("  TREE_ERR", st, str(tr)[:160])
        return None
    m = {}
    for e in tr.get("tree", []):
        if e.get("type") == "blob":
            m[e["path"]] = e["sha"]
    return m


def sync_gt(only=None, gap=4):
    token = open(GT_TOKEN_FILE).read().strip()
    api = "https://gitee.com/api/v5/repos/%s/%s" % (GT_OWNER, GT_REPO)
    print("=== Gitee 同步 ===")
    rmap = remote_gt(token)
    if rmap is None:
        return False
    print("  远端文件数 =", len(rmap))

    allf = collect(only)
    todo = []
    for rel in allf:
        lp = os.path.join(ROOT, rel)
        raw = open(lp, "rb").read()
        if rmap.get(rel) != blob_sha(raw):
            todo.append((rel, raw))
    print("  待推 = %d / %d" % (len(todo), len(allf)))
    if not todo:
        print("  已全部一致")
        return True

    ok = fail = 0
    for i, (rel, raw) in enumerate(todo, 1):
        good = _gt_put(api, token, rel, raw)
        if good:
            ok += 1
            print("  [%d/%d] OK   %s" % (i, len(todo), rel))
        else:
            fail += 1
            print("  [%d/%d] FAIL %s" % (i, len(todo), rel))
        time.sleep(gap)
    print("  === 完成 ok=%d fail=%d" % (ok, fail))
    return fail == 0


def _gt_put(api, token, rel, raw):
    """Gitee contents API；>1MB 自动按 700KB 分片（Gitee 单文件硬限制）"""
    if len(raw) > 900 * 1024:
        return _gt_split(api, token, rel, raw)
    for attempt in range(3):
        q = "?access_token=" + token
        st, cur = http(api + "/contents/" + urllib.parse.quote(rel) + q, token=token)
        payload = {"access_token": token, "message": "docs: 同步 " + rel,
                   "branch": GT_BRANCH, "content": base64.b64encode(raw).decode()}
        if st == 200 and isinstance(cur, dict) and cur.get("sha"):
            payload["sha"] = cur["sha"]
            st2, _ = http(api + "/contents/" + urllib.parse.quote(rel), "PUT", payload, token=token)
        else:
            st2, _ = http(api + "/contents/" + urllib.parse.quote(rel), "POST", payload, token=token)
        if st2 in (200, 201):
            return True
        time.sleep(5)
    return False


def _gt_split(api, token, rel, raw):
    print("       分片通道 %s (%d bytes)" % (rel, len(raw)))
    CHUNK = 700 * 1024
    parts = [raw[i:i + CHUNK] for i in range(0, len(raw), CHUNK)]
    for idx, chunk in enumerate(parts):
        if not _gt_put(api, token, "%s.part%d" % (rel, idx + 1), chunk):
            return False
        time.sleep(3)
    # 删掉旧的完整文件（如果存在），避免同时存在半截版本
    q = "?access_token=" + token
    st, cur = http(api + "/contents/" + urllib.parse.quote(rel) + q, token=token)
    if st == 200 and isinstance(cur, dict) and cur.get("sha"):
        http(api + "/contents/" + urllib.parse.quote(rel), "DELETE",
             {"access_token": token, "sha": cur["sha"],
              "message": "chore: " + rel + " 改为分片存储", "branch": GT_BRANCH}, token=token)
    script = ('#!/bin/sh\n'
              '# 还原被分片的大文件（Gitee 单文件请求体限制）\n'
              '# 用法：仓库根目录执行  sh tools/join_parts.sh\n'
              'set -e\n'
              'cd "$(dirname "$0")/.."\n'
              'for base in $(find java res -name "*.part1" 2>/dev/null | sed "s/\\.part1$//"); do\n'
              '  rm -f "$base"\n'
              '  for p in "$base".part*; do cat "$p" >> "$base"; done\n'
              '  echo "已还原 $base"\n'
              'done\n')
    _gt_put(api, token, "tools/join_parts.sh", script.encode("utf-8"))
    return True


if __name__ == "__main__":
    who = (sys.argv[1] if len(sys.argv) > 1 else "").lower()
    only = sys.argv[2] if len(sys.argv) > 2 else None
    if who == "github":
        sys.exit(0 if sync_gh(only) else 1)
    elif who == "gitee":
        sys.exit(0 if sync_gt(only) else 1)
    else:
        print("用法: python3 tools/sync_src.py github|gitee [目录]")
        sys.exit(2)