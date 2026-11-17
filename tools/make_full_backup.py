#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
DYParse 全量源码打包（两份）
  A) DYParse_源码全量_含敏感.zip      —— 含服务器 IP / 证书 / 代理 / 凭据，仅本地留存
  B) DYParse_源码全量_已脱敏.zip      —— 剔除敏感目录并把明文替换成占位符，可外发

共同内容：
  java/            全部 Java 源码
  res/             资源
  libs/            依赖 jar/aar
  tools/           构建与运维脚本
  secret_src/      （仅 A）
  server/          （仅 A）
  build.sh 等根目录文件
  *.md             文档（含改动总汇）
"""
import os
import re
import shutil
import sys
import zipfile

ROOT = '/storage/emulated/0/MT2/mcp/DYParse'
OUT_DIR = '/storage/emulated/0/Download/DYParse_FullBackup'
DOC_MD = 'DYParse_全部改动与Bug修复总汇.md'
DOC_MD2 = 'DYParse_重做技术选型建议.md'
DOC_MDS = (DOC_MD, DOC_MD2)

# 一律不打包（体积大/无意义/纯中间产物）
SKIP_DIRS = {
    '.bak2', '.backup', 'build', 'releases', 'bt_dl', '__pycache__',
    '.git', '.gradle', '.idea',
}
SKIP_EXT = {'.apk', '.dex', '.class', '.pyc', '.log', '.bak'}
SKIP_NAMES = {'android.jar', 'debug.keystore'}  # 体积大且非源码；keystore 另处理

# 只有「含敏感版」才带上的目录
SENSITIVE_DIRS = {'secret_src', 'server'}

# 脱敏替换规则：(正则, 替换文本)
SCRUB_RULES = [
    # 先处理「转义形式」：安全扫描脚本把地址写成正则字面量，文本是 116\.205\.105\.44。
    # 必须排在明文规则之前，否则明文规则先跑会把里面的数字换掉，这条就匹配不到了。
    (re.compile(r'116\\\.205\\\.105\\\.44'), r'127\\.0\\.0\\.1'),
    (re.compile(r'172\\\.31\\\.14\\\.16'), r'127\\.0\\.0\\.1'),
    # 再处理明文
    (re.compile(r'116\.205\.105\.44'), '127.0.0.1'),
    (re.compile(r'172\.31\.14\.16'), '127.0.0.1'),
    (re.compile(r'hcss_ecs_9ee8'), 'REDACTED_HOST'),
    (re.compile(r'hcss_ecs'), 'REDACTED'),
    (re.compile(r'BT-cn-north-4-s0x1'), 'REDACTED_INSTANCE'),
    (re.compile(r'a2137190676\.'), 'REDACTED_PASS'),
    (re.compile(r'a2137190676'), 'REDACTED_USER'),
    # 32/40 位 token 形态（只在明确的 token 赋值上下文里替换，避免误伤 sha）
    (re.compile(r'(?i)(access_token|github_token|gitee_token|TOKEN)\s*=\s*["\']([0-9a-f]{32,40})["\']'),
     r'\1 = "REDACTED_TOKEN"'),
]

TEXT_EXT = {'.java', '.py', '.sh', '.md', '.json', '.xml', '.txt', '.cfg',
            '.ini', '.properties', '.yml', '.yaml', '.gradle', '.pro', '.mk'}


def is_text(path):
    return os.path.splitext(path)[1].lower() in TEXT_EXT


def should_skip(rel, name, is_sensitive_pack):
    parts = rel.split('/')
    for p in parts[:-1]:
        if p in SKIP_DIRS:
            return True
        if p in SENSITIVE_DIRS and not is_sensitive_pack:
            return True
    if name in SKIP_DIRS:
        return True
    if name in SENSITIVE_DIRS and not is_sensitive_pack:
        return True
    if name in SKIP_NAMES:
        return True
    ext = os.path.splitext(name)[1].lower()
    if ext in SKIP_EXT:
        return True
    return False


def scrub_bytes(data, rel):
    """对文本内容做脱敏，返回 (新内容, 命中次数)"""
    try:
        s = data.decode('utf-8')
    except UnicodeDecodeError:
        return data, 0
    hits = 0
    for pat, rep in SCRUB_RULES:
        s2, n = pat.subn(rep, s)
        if n:
            hits += n
            s = s2
    if hits == 0:
        return data, 0
    return s.encode('utf-8'), hits


def build(sensitive, out_zip):
    count = 0
    scrub_files = 0
    scrub_hits = 0
    with zipfile.ZipFile(out_zip, 'w', zipfile.ZIP_DEFLATED, compresslevel=6) as z:
        for dirpath, dirnames, filenames in os.walk(ROOT):
            rel_dir = os.path.relpath(dirpath, ROOT).replace(os.sep, '/')
            if rel_dir == '.':
                rel_dir = ''
            dirnames[:] = [d for d in dirnames if d not in SKIP_DIRS
                           and (sensitive or d not in SENSITIVE_DIRS)]
            for fn in filenames:
                full = os.path.join(dirpath, fn)
                rel = (rel_dir + '/' + fn).lstrip('/')
                if should_skip(rel, fn, sensitive):
                    continue
                try:
                    data = open(full, 'rb').read()
                except Exception:
                    continue
                if not sensitive and is_text(rel):
                    newdata, hits = scrub_bytes(data, rel)
                    if hits:
                        scrub_files += 1
                        scrub_hits += hits
                    data = newdata
                z.writestr(rel, data)
                count += 1

        # 附带文档（改动总汇 + 技术选型建议）
        for dn in DOC_MDS:
            doc_src = os.path.join(OUT_DIR, dn)
            if os.path.exists(doc_src):
                z.writestr(dn, open(doc_src, 'rb').read())
                count += 1

        # 打包说明
        note = []
        note.append('# DYParse 源码包说明\n')
        note.append('- 生成时间：2026-11-11\n')
        note.append('- 版本：v2.1.4（versionCode 2010400）\n')
        if sensitive:
            note.append('- 类型：**含敏感信息全量包**（仅本地留存，切勿上传/外发）\n')
            note.append('- 包含：`secret_src/`（服务器地址、证书、HMAC 密钥）、`server/`（服务端模块与证书私钥）\n')
        else:
            note.append('- 类型：**已脱敏包**（可外发）\n')
            note.append('- 已剔除：`secret_src/`、`server/`\n')
            note.append('- 已替换：服务器 IP → 127.0.0.1、主机名/实例名 → REDACTED、密码 → REDACTED_PASS、token → REDACTED_TOKEN\n')
            note.append('- 注意：脱敏后 `build.sh` 仍可编译（`secret_src` 缺失时自动用空壳类，内置服务器功能关闭）\n')
        note.append('\n## 目录\n')
        note.append('```\njava/        Java 源码\nres/         资源（layout/drawable/values）\nlibs/        依赖 jar/aar（13 项）\ntools/       构建与运维脚本\nbuild.sh     构建脚本（aapt→javac→r8→zipalign→apksigner）\nAndroidManifest.xml\nversion.json CHANGELOG.md README.md\n```\n')
        note.append('\n## 构建\n```bash\nbash build.sh            # 加密包\nPLAIN=1 bash build.sh    # 未加密原包\n```\n')
        note.append('\n## 未包含\n')
        note.append('- `android.jar`（25MB，编译期 API 声明，需自备）\n')
        note.append('- `debug.keystore`（签名文件，需自备）\n')
        note.append('- `build/` `releases/`（构建产物）\n')
        note.append('- `.backup/` `.bak2/`（历史备份，539MB）\n')
        note.append('- `bt_dl/`（BT 下载缓存，85MB）\n')
        z.writestr('README_包说明.md', ''.join(note).encode('utf-8'))
        count += 1

    size = os.path.getsize(out_zip)
    print('  -> %s' % os.path.basename(out_zip))
    print('     文件数 = %d   大小 = %.1f MB' % (count, size / 1024.0 / 1024.0))
    if not sensitive:
        print('     脱敏: %d 个文件 / %d 处替换' % (scrub_files, scrub_hits))
    return count, size


def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    a = os.path.join(OUT_DIR, 'DYParse_源码全量_含敏感.zip')
    b = os.path.join(OUT_DIR, 'DYParse_源码全量_已脱敏.zip')

    print('=== A) 含敏感信息全量包 ===')
    build(True, a)
    print('=== B) 已脱敏包 ===')
    build(False, b)

    print()
    print('=== 产物 ===')
    for f in (a, b):
        print('  %-42s %.1f MB' % (os.path.basename(f),
                                   os.path.getsize(f) / 1024.0 / 1024.0))


if __name__ == '__main__':
    main()