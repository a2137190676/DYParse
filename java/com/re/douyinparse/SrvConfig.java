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
    public static final String DEFAULT_SERVER = "<INJECT_SERVER>";
}
