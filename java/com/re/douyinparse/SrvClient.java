package com.re.douyinparse;

import java.net.HttpURLConnection;
import java.net.URL;

/**
 * 内置服务器连接（开源占位版）。
 *
 * 真版实现（TLS pin + HMAC-SHA256 鉴权）存放在本机的 secret_src/ 目录，
 * 不进开源仓库。构建时 build.sh 会检查 secret_src/ 并自动用真版覆盖本文件。
 *
 * 无 secret_src/ 时本类为直连：内置服务器禁用，App 全走本地签名 + Gitee 回退，
 * 用户自填的服务器地址也正常直连使用。
 */
public final class SrvClient {

    /** 内置地址（占位版永远为空 → 内置服务器禁用） */
    public static String builtinBase() {
        return "";
    }

    /** 该地址是不是内置服务器（占位版永远 false） */
    public static boolean isBuiltin(String url) {
        return false;
    }

    /** 内置 URL 的 http 形式转 https 形式（占位版原样返回） */
    public static String toHttps(String url) {
        return url;
    }

    /** HMAC 鉴权头（占位版不动作） */
    public static void sign(HttpURLConnection c, String url) {
    }

    /** 开连接（占位版：普通直连，无 pin） */
    public static HttpURLConnection open(String url, java.net.Proxy px) throws Exception {
        return (HttpURLConnection) (px != null
                ? new URL(url).openConnection(px)
                : new URL(url).openConnection());
    }
}