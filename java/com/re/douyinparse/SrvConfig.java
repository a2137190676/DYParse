package com.re.douyinparse;

/**
 * 内置服务器地址（DYParse 官方加速层）。
 *
 * 编译期由 tools/inject_srv.py 从本地 secret_srv.txt（不进开源仓库）注入：
 *   - 有 secret_srv.txt  -> 注入真实地址，用户开箱即用（零配置）
 *   - 无 secret_srv.txt  -> 空字符串 = 无内置服务器，全走本地/Gitee 回退
 *   - 用户在「代理设置」里自填的地址永远优先于内置值
 *
 * 开源仓库只保留 &lt;INJECT_SERVER&gt; 占位符，不暴露真实服务器地址。
 */
public final class SrvConfig {
    /**
     * 已废弃：不再使用明文注入。
     * 内置服务器地址请用 SrvSecret.serverAddr()（字节异或混淆，防 strings 提取）。
     * 这里恒为占位符，避免任何构建路径把真实 IP 写进 DEX 明文字符串表。
     */
    public static final String DEFAULT_SERVER = "";
}
