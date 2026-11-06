#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""DYParse 服务端自愈守卫。

背景：2026-10-25 发现 8443（HTTPS）会被单个「连上不发数据」的客户端拖死——
旧的 server.py 在 accept 循环里同步做 TLS 握手，一个卡住就全线阻塞，
表现为「端口在监听但所有连接超时」，用户端实况图退化成静态图。
握手问题已在 server.py 修掉，本守卫是第二道保险：

  · 每 60 秒探测 8080 / 8443
  · 连续 3 次失败（或监听队列 Recv-Q 积压超阈值）→ systemctl restart dyparse
  · 重启后冷却 5 分钟，避免反复抽风式重启
  · 全程写日志，方便事后查

用法：
  python3 dyparse_watchdog.py            # 前台跑（调试）
  systemctl start dyparse-watchdog       # 后台跑（生产）

日志： /var/log/dyparse-watchdog.log
"""
import os
import re
import socket
import ssl
import subprocess
import sys
import time
import traceback

SERVICE = "dyparse"
LOG = "/var/log/dyparse-watchdog.log"

HTTP_PORT = 8080
HTTPS_PORT = 8443

INTERVAL = 60          # 探测间隔（秒）
FAIL_THRESHOLD = 3     # 连续失败几次触发重启
CONNECT_TIMEOUT = 6    # 单次连接超时（秒）
COOLDOWN = 300         # 重启后冷却（秒），期间不再重启


def log(msg):
    line = "[%s] %s" % (time.strftime("%Y-%m-%d %H:%M:%S"), msg)
    print(line, flush=True)
    try:
        with open(LOG, "a", encoding="utf-8") as f:
            f.write(line + "\n")
    except Exception:
        pass


def _ssl_ctx():
    ctx = ssl.create_default_context()
    ctx.check_hostname = False
    ctx.verify_mode = ssl.CERT_NONE       # 自签证书，守卫只关心端口活没活
    return ctx


def probe(port, use_tls=False):
    """连一下 + 发一个最小请求，判断端口是否真的能服务。
    返回 (ok, detail)。不用 requests，避免额外依赖。"""
    s = None
    try:
        s = socket.create_connection(("127.0.0.1", port), timeout=CONNECT_TIMEOUT)
        if use_tls:
            s = _ssl_ctx().wrap_socket(s, server_hostname="127.0.0.1")
        s.settimeout(CONNECT_TIMEOUT)
        req = "GET / HTTP/1.0\r\nHost: 127.0.0.1\r\n\r\n"
        s.sendall(req.encode())
        data = s.recv(64)
        if not data:
            return False, "empty response"
        head = data.split(b"\r\n", 1)[0].decode("latin-1", "replace")
        m = re.match(r"HTTP/1\.[01]\s+(\d{3})", head)
        if not m:
            return False, "bad status line: %r" % head[:40]
        code = int(m.group(1))
        return (200 <= code < 500), "http=%d" % code
    except Exception as e:
        return False, "%s: %s" % (type(e).__name__, e)
    finally:
        try:
            if s:
                s.close()
        except Exception:
            pass


def listen_backlog(port):
    """读 ss 输出里的 Recv-Q（监听队列积压数）。
    判据：LISTEN 状态且本地端口匹配。返回 None 表示读不到（不阻断判断）。"""
    try:
        out = subprocess.run(
            ["ss", "-lntH", "sport = :%d" % port],
            capture_output=True, text=True, timeout=8
        ).stdout
        for line in out.splitlines():
            parts = line.split()
            if len(parts) >= 3 and parts[0] == "LISTEN":
                try:
                    return int(parts[1])
                except ValueError:
                    continue
    except Exception:
        pass
    return None


def service_active():
    try:
        r = subprocess.run(["systemctl", "is-active", SERVICE],
                           capture_output=True, text=True, timeout=8)
        return r.stdout.strip() == "active"
    except Exception:
        return False


def restart(reason):
    log("RESTART 触发：%s" % reason)
    try:
        subprocess.run(["systemctl", "restart", SERVICE],
                       capture_output=True, text=True, timeout=60)
    except Exception as e:
        log("RESTART 命令异常：%s" % e)
        return False
    # 等端口起来
    for i in range(20):
        time.sleep(1)
        ok_h, d_h = probe(HTTP_PORT, False)
        if ok_h:
            log("RESTART 完成，服务已恢复（耗时 %ds）" % (i + 1))
            return True
    log("RESTART 后仍未恢复，请人工检查：journalctl -u %s -n 50" % SERVICE)
    return False


def main():
    log("watchdog 启动：interval=%ds threshold=%d cooldown=%ds"
        % (INTERVAL, FAIL_THRESHOLD, COOLDOWN))
    fails = 0
    last_restart = 0.0

    while True:
        try:
            ok_h, d_h = probe(HTTP_PORT, False)
            ok_s, d_s = probe(HTTPS_PORT, True)
            q_h = listen_backlog(HTTP_PORT)
            q_s = listen_backlog(HTTPS_PORT)

            healthy = ok_h and ok_s

            # 队列积压也算不健康：>10 说明 accept 卡住了
            backlog_bad = (q_h is not None and q_h > 10) or (q_s is not None and q_s > 10)
            if backlog_bad:
                healthy = False

            if healthy:
                if fails > 0:
                    log("恢复正常（之前连续失败 %d 次）" % fails)
                fails = 0
                print("[%s] OK 8080:%s 8443:%s backlog(8080=%s,8443=%s)"
                      % (time.strftime("%H:%M:%S"), d_h, d_s, q_h, q_s), flush=True)
            else:
                fails += 1
                log("异常 %d/%d  8080=%s(%s)  8443=%s(%s)  backlog(8080=%s,8443=%s)"
                    % (fails, FAIL_THRESHOLD, ok_h, d_h, ok_s, d_s, q_h, q_s))

                if fails >= FAIL_THRESHOLD:
                    now = time.time()
                    if now - last_restart < COOLDOWN:
                        log("冷却中（还剩 %ds），本次不重启"
                            % int(COOLDOWN - (now - last_restart)))
                    else:
                        if not service_active():
                            restart("服务未运行")
                        else:
                            restart("连续 %d 次探测失败" % fails)
                        last_restart = time.time()
                        fails = 0

            time.sleep(INTERVAL)
        except KeyboardInterrupt:
            log("watchdog 收到中断，退出")
            return 0
        except Exception:
            log("主循环异常：\n" + traceback.format_exc())
            time.sleep(INTERVAL)


if __name__ == "__main__":
    sys.exit(main())
