# -*- coding: utf-8 -*-
"""
修 listen=[] 诊断问题。

根因怀疑：listenEndpoints() 在 libtorrent4j 里可能一直返回空，
但实际监听是成功的。用 SessionManager.isListening() / getListenPort()
这两个更可靠的方法交叉验证。

另外补上 IPv6：
  · listen_interfaces 用 "[::]:6881-6999" 才是 IPv6 通配
  · 之前写法 "0.0.0.0:6881-6999,[::]:6881-6999" 语法上是对的，
    但如果 libtorrent 编译时关了 IPv6，[::] 会静默失败并可能导致整个
    listen 配置失效 → 全部变空。
  · 所以改成：先只试 IPv4（保证能起来），再单独追加 IPv6。
"""
import io

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/BtDownloader.java'
s = io.open(P, encoding='utf-8').read()

# ---------- 1) 替换 BT_SETTINGS_APPLIED 那段诊断 ----------
old_diag = '''                    // 给 listen socket 一点起来的时间，再读一次
                    try { Thread.sleep(400); } catch (Throwable te) {}
                    java.util.List<String> eps = null;
                    try { eps = sm.listenEndpoints(); } catch (Throwable te) {}
                    Log.i(TAG, "BT_SETTINGS_APPLIED listen=" + eps
                            + " iface=" + (eps == null || eps.isEmpty() ? "EMPTY" : "OK"));'''

new_diag = '''                    // 给 listen socket 一点起来的时间，再读一次
                    try { Thread.sleep(400); } catch (Throwable te) {}
                    // 用三种方式交叉验证监听是否真的起来了：
                    //   listenEndpoints()  可能一直返回空（libtorrent4j 的已知怪癖）
                    //   isListening()      更权威
                    //   getListenPort()    能拿到实际端口才算真起来
                    java.util.List<String> eps = null;
                    try { eps = sm.listenEndpoints(); } catch (Throwable te) {}
                    boolean listening = false;
                    int listenPort = -1;
                    try { listening = sm.isListening(); } catch (Throwable te) {}
                    try { listenPort = sm.getListenPort(); } catch (Throwable te) {}
                    Log.i(TAG, "BT_SETTINGS_APPLIED listen=" + eps
                            + " iface=" + (eps == null || eps.isEmpty() ? "EMPTY" : "OK")
                            + " isListening=" + listening
                            + " port=" + listenPort
                            + " cfg=" + sm.listenInterfaces());'''

if old_diag not in s:
    raise SystemExit('MISS diag block')
s = s.replace(old_diag, new_diag, 1)

# ---------- 2) listen 配置：拆分 IPv4 / IPv6，避免 [::] 失败拖垮整条 ----------
old_listen1 = '''                    // 关键：显式监听，否则 listen=[] 时 peer 连不进来，只能主动外连，速度差。
                    // 6881 常被别的程序占；libtorrent 支持端口范围，会自己挑一个可用的。
                    sp.listenInterfaces("0.0.0.0:6881-6999,[::]:6881-6999");'''

new_listen1 = '''                    // 关键：显式监听，否则 listen=[] 时 peer 连不进来，只能主动外连，速度差。
                    // 6881 常被别的程序占；libtorrent 支持端口范围，会自己挑一个可用的。
                    //
                    // IPv4 + IPv6 分开写：
                    //   "0.0.0.0:6881-6999"  IPv4 通配
                    //   "[::]:6881-6999"     IPv6 通配（双栈）
                    // 某些 libtorrent 构建如果没编译 IPv6，[::] 会静默失败，
                    // 一起写有可能让整条 listen_interfaces 失效 → 全部变空。
                    // 所以先用最稳的 IPv4 起，IPv6 在下面单独 try 追加。
                    sp.listenInterfaces("0.0.0.0:6881-6999");'''

if old_listen1 not in s:
    raise SystemExit('MISS listen sp block')
s = s.replace(old_listen1, new_listen1, 1)

# ---------- 3) applySettings 之后那段 listen 重试 ----------
old_listen2 = '''                    try {
                        String want = "0.0.0.0:6881-6999,[::]:6881-6999";
                        sm.listenInterfaces(want);
                        Log.i(TAG, "BT_LISTEN_SET " + want);
                    } catch (Throwable te) {
                        Log.i(TAG, "BT_LISTEN_SET_ERR " + te);
                    }'''

new_listen2 = '''                    try {
                        // 先 IPv4（双栈系统上 [::] 也能收 IPv4，但分开写更保险）
                        sm.listenInterfaces("0.0.0.0:6881-6999");
                        Log.i(TAG, "BT_LISTEN_SET ipv4=0.0.0.0:6881-6999");
                        // 再尝试追加 IPv6：[::] 双栈写法
                        try {
                            Thread.sleep(200);
                            sm.listenInterfaces("0.0.0.0:6881-6999,[::]:6881-6999");
                            Log.i(TAG, "BT_LISTEN_SET_V6 ipv4+ipv6");
                        } catch (Throwable te6) {
                            Log.i(TAG, "BT_LISTEN_V6_UNSUPPORTED " + te6);
                            // IPv6 不支持就退回纯 IPv4
                            try { sm.listenInterfaces("0.0.0.0:6881-6999"); } catch (Throwable t9) {}
                        }
                    } catch (Throwable te) {
                        Log.i(TAG, "BT_LISTEN_SET_ERR " + te);
                    }'''

if old_listen2 not in s:
    raise SystemExit('MISS listen retry block')
s = s.replace(old_listen2, new_listen2, 1)

# ---------- 4) ensureSession 末尾的兜底重试也改 ----------
old_listen3 = '''            try {
                java.util.List<String> ep0 = sm.listenEndpoints();
                if (ep0 == null || ep0.isEmpty()) {
                    sm.listenInterfaces("0.0.0.0:6881-6999,[::]:6881-6999");
                    try { Thread.sleep(300); } catch (Throwable te) {}
                    Log.i(TAG, "BT_LISTEN_RETRY -> " + sm.listenEndpoints());
                }
            } catch (Throwable te) {
                Log.i(TAG, "BT_LISTEN_RETRY_ERR " + te);
            }
            Log.i(TAG, "BT_SESSION_OK dhtNodes=" + sm.dhtNodes()
                    + " listen=" + sm.listenEndpoints());'''

new_listen3 = '''            try {
                // 用 isListening()/getListenPort() 判断，而不是只看 listenEndpoints()
                // （后者可能一直返回空，造成「看起来没监听」的假象）
                boolean ok = false;
                int prt = -1;
                try { ok = sm.isListening(); } catch (Throwable te) {}
                try { prt = sm.getListenPort(); } catch (Throwable te) {}
                if (!ok || prt <= 0) {
                    sm.listenInterfaces("0.0.0.0:6881-6999");
                    try { Thread.sleep(300); } catch (Throwable te) {}
                    try { ok = sm.isListening(); } catch (Throwable te) {}
                    try { prt = sm.getListenPort(); } catch (Throwable te) {}
                    Log.i(TAG, "BT_LISTEN_RETRY -> listening=" + ok + " port=" + prt);
                }
            } catch (Throwable te) {
                Log.i(TAG, "BT_LISTEN_RETRY_ERR " + te);
            }
            Log.i(TAG, "BT_SESSION_OK dhtNodes=" + sm.dhtNodes()
                    + " listening=" + safeListening(sm)
                    + " port=" + safeListenPort(sm)
                    + " listen=" + sm.listenEndpoints());'''

if old_listen3 not in s:
    raise SystemExit('MISS listen fallback block')
s = s.replace(old_listen3, new_listen3, 1)

# ---------- 5) 加两个安全取值小工具 ----------
anchor = '''    /** 确保 session 已启动（带速度调优） */'''
helpers = '''    /** 安全读 isListening（异常时返回 false，不抛） */
    private static boolean safeListening(SessionManager sm){
        try { return sm.isListening(); } catch (Throwable t) { return false; }
    }

    /** 安全读监听端口（异常时返回 -1） */
    private static int safeListenPort(SessionManager sm){
        try { return sm.getListenPort(); } catch (Throwable t) { return -1; }
    }

'''
if anchor not in s:
    raise SystemExit('MISS ensureSession anchor')
s = s.replace(anchor, helpers + anchor, 1)

io.open(P, 'w', encoding='utf-8').write(s)
print('BtDownloader listen 诊断已修')
print('isListening 出现次数:', s.count('isListening'))