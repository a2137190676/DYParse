import re
# -*- coding: utf-8 -*-
"""编译前注入内置服务器地址。

读 secret_srv.txt（不进开源仓库，.gitignore 已忽略）：
  - 存在：把真实地址写进 SrvConfig.java
  - 不存在：注入空字符串（无内置服务器，全走回退）
build.sh 会自动调用本脚本；也可手动运行。
"""
import os, sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "java", "com", "re", "douyinparse", "SrvConfig.java")
SECRET = os.path.join(ROOT, "secret_srv.txt")

addr = ""
# PLAIN=1：原包构建（无内置服务器，纯源码，供防丢失逆向用）
if os.environ.get("PLAIN") != "1" and os.path.isfile(SECRET):
    with open(SECRET, encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if line:
                addr = line
                break

with open(SRC, encoding="utf-8") as f:
    code = f.read()

# 幂等：上次编译失败可能已注入真实地址，先还原占位符
code = re.sub(r'public static final String DEFAULT_SERVER = "[^"]*";',
              'public static final String DEFAULT_SERVER = "<INJECT_SERVER>";', code)

assert "<INJECT_SERVER>" in code, "SrvConfig.java 缺少 <INJECT_SERVER> 占位符"
code = code.replace("<INJECT_SERVER>", addr)

with open(SRC, "w", encoding="utf-8") as f:
    f.write(code)

if addr:
    print("inject_srv: built-in server -> %s" % addr)
else:
    print("inject_srv: no secret_srv.txt, built-in server disabled")
