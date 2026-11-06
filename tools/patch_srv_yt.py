# -*- coding: utf-8 -*-
"""服务器端 yt_module.py 补丁：
1) proxy_alive 改成真连通性验证
2) _run_ytdlp 加总预算，避免 240s x3 = 720s
"""
import sys

P = "/opt/dyparse/yt_module.py"
src = open(P, encoding="utf-8").read()
orig_len = len(src)

# ---------------- 1. proxy_alive ----------------
old_alive = '''def proxy_alive():
    try:
        import socket
        s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        s.settimeout(2)
        ok = s.connect_ex(("127.0.0.1", 10809)) == 0
        s.close()
        return ok
    except Exception:
        return False'''

new_alive = '''_alive_cache = {"t": 0, "ok": False}


def proxy_alive():
    # 2026-11 教训：原来只测 10809 端口在不在，只要 xray 进程活着就返回 True。
    # 但机场节点挂掉时 xray 仍然监听 10809，于是每次都用 240s x3 重试去白等
    # yt-dlp，App 端 10 分钟拿不到响应。
    # 现在改成「通过代理发一个真实 HTTPS 请求」，15 秒拿不到就判定不可用，
    # 结果缓存 60 秒，避免每次解析都拖 15 秒。
    import time as _t
    now = _t.time()
    if now - _alive_cache["t"] < 60:
        return _alive_cache["ok"]
    ok = False
    try:
        import urllib.request
        import ssl
        ctx = ssl.create_default_context()
        ctx.check_hostname = False
        ctx.verify_mode = ssl.CERT_NONE
        proxy = urllib.request.ProxyHandler({"http": PROXY_HTTP, "https": PROXY_HTTP})
        opener = urllib.request.build_opener(proxy, urllib.request.HTTPSHandler(context=ctx))
        req = urllib.request.Request("https://www.youtube.com/generate_204",
                                     headers={"User-Agent": "Mozilla/5.0"})
        r = opener.open(req, timeout=15)
        ok = r.status in (200, 204, 301, 302, 303)
        try:
            r.close()
        except Exception:
            pass
    except Exception:
        ok = False
    _alive_cache["t"] = now
    _alive_cache["ok"] = ok
    return ok'''

if old_alive in src:
    src = src.replace(old_alive, new_alive, 1)
    print("PATCH_OK proxy_alive")
elif "_alive_cache" in src:
    print("SKIP proxy_alive 已打过补丁")
else:
    print("FAIL proxy_alive 未匹配")
    sys.exit(1)

# ---------------- 2. _run_ytdlp 总预算 ----------------
old_loop = '''    for attempt in range(3):
        r = subprocess.run(
            ["python3", "-m", "yt_dlp", "--no-warnings",
             "--socket-timeout", "30"] + args,
            capture_output=True, text=True, timeout=timeout, env=env)'''

new_loop = '''    import time as _t
    deadline = _t.time() + _TOTAL_BUDGET
    for attempt in range(3):
        left = deadline - _t.time()
        if left < 10:
            return -1, "", "总预算耗尽(%ds)" % _TOTAL_BUDGET
        per = min(timeout, int(left))
        r = subprocess.run(
            ["python3", "-m", "yt_dlp", "--no-warnings",
             "--socket-timeout", "30"] + args,
            capture_output=True, text=True, timeout=per, env=env)'''

if old_loop in src:
    src = src.replace(old_loop, new_loop, 1)
    print("PATCH_OK _run_ytdlp")
elif "_TOTAL_BUDGET" in src:
    print("SKIP _run_ytdlp 已打过补丁")
else:
    print("FAIL _run_ytdlp 未匹配")
    sys.exit(1)

# ---------------- 3. 插入常量 ----------------
if "_TOTAL_BUDGET =" not in src:
    anchor = "def _run_ytdlp("
    if anchor in src:
        src = src.replace(
            anchor,
            "# yt-dlp 全流程总预算(秒)。App 端读超时 75s，这里留够余量\n_TOTAL_BUDGET = 70\n\n\n" + anchor,
            1)
        print("PATCH_OK _TOTAL_BUDGET")

open(P, "w", encoding="utf-8").write(src)
print("WROTE", orig_len, "->", len(src))

# 语法检查
import py_compile
try:
    py_compile.compile(P, doraise=True)
    print("SYNTAX_OK")
except Exception as e:
    print("SYNTAX_FAIL", e)