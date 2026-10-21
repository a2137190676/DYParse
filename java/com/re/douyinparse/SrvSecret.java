package com.re.douyinparse;

/**
 * 内置服务器凭据（开源占位版）。
 *
 * 真实实现（含服务器地址与 HMAC key）存放在本机的 secret_src/ 目录，
 * 不进开源仓库。构建时 build.sh 会检查 secret_src/ 并自动用真版覆盖本文件。
 *
 * 无 secret_src/ 时本类返回空：内置服务器禁用，App 全走本地签名 + Gitee 回退。
 */
public final class SrvSecret {
    /** 内置服务器地址；无凭据时为空串（禁用内置服务器） */
    public static String serverAddr() {
        return "";
    }

    /** HMAC-SHA256 鉴权密钥（32 hex）；无凭据时为空串 */
    public static String hmacKey() {
        return "";
    }
}