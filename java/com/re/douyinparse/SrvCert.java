package com.re.douyinparse;

/**
 * 内置服务器 TLS 证书（开源占位版）。
 *
 * 真实证书（DER 混淆切段）存放在本机的 secret_src/ 目录，不进开源仓库。
 * 构建时 build.sh 会检查 secret_src/ 并自动用真版覆盖本文件。
 *
 * 无 secret_src/ 时四个段都返回空串，SrvClient 会自动禁用 pin 与内置服务器。
 */
public final class SrvCert {
    public static String part0() { return ""; }
    public static String part1() { return ""; }
    public static String part2() { return ""; }
    public static String part3() { return ""; }
}