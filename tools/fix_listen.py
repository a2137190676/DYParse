# -*- coding: utf-8 -*-
"""修磁力 listen=[] —— peer 连不进来的根因。

现象：BT_SETTINGS_APPLIED listen=[] 一直是空，说明监听端口没起来。
     （已验证：本机 UDP/TCP 6881-6900 都能 bind 成功，不是系统权限问题）

根因：libtorrent 的 listen_interfaces 属于「启动时读取」的设置，
     通过 SettingsPack 在 session start 之后再 applySettings，
     很多版本不会重新 open listen socket，结果 listen 保持空。

修法：在 applySettings 之后，再显式调一次 sm.listenInterfaces(端口串)，
     这是 SessionManager 直接暴露的 API，会真正触发监听；
     并打印返回值确认是否生效。
"""
import io

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/BtDownloader.java'
s = io.open(P, encoding='utf-8').read()
n = 0

# 1) applySettings 之后补一次显式监听
old = '''                    sm.applySettings(sp);
                    Log.i(TAG, "BT_SETTINGS_APPLIED listen=" + sm.listenEndpoints());'''
new = '''                    sm.applySettings(sp);

                    // ---- 关键：applySettings 之后必须再显式 listen 一次 ----
                    // listen_interfaces 在 libtorrent 里是「启动期」设置，
                    // 只通过 applySettings 往往不会重新 open socket，
                    // 结果 listen=[] —— 外部 peer 连不进来，只能主动外连，速度很差。
                    try {
                        String want = "0.0.0.0:6881-6999,[::]:6881-6999";
                        sm.listenInterfaces(want);
                        Log.i(TAG, "BT_LISTEN_SET " + want);
                    } catch (Throwable te) {
                        Log.i(TAG, "BT_LISTEN_SET_ERR " + te);
                    }
                    // 给 listen socket 一点起来的时间，再读一次
                    try { Thread.sleep(400); } catch (Throwable te) {}
                    java.util.List<String> eps = null;
                    try { eps = sm.listenEndpoints(); } catch (Throwable te) {}
                    Log.i(TAG, "BT_SETTINGS_APPLIED listen=" + eps
                            + " iface=" + (eps == null || eps.isEmpty() ? "EMPTY" : "OK"));'''
if old in s:
    s = s.replace(old, new, 1); n += 1; print('1) 显式 listen OK')
else:
    print('MISS 1')

# 2) session start 之后再补一次（防止 applySettings 分支没走到）
old = '''            sSession = sm;
            Log.i(TAG, "BT_SESSION_OK dhtNodes=" + sm.dhtNodes()
                    + " listen=" + sm.listenEndpoints());
            return sm;'''
new = '''            sSession = sm;
            // 最后再确保一次监听（有些 ROM/libtorrent 版本会在 applySettings 时重置监听）
            try {
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
                    + " listen=" + sm.listenEndpoints());
            return sm;'''
if old in s:
    s = s.replace(old, new, 1); n += 1; print('2) 启动后兜底 listen OK')
else:
    print('MISS 2')

io.open(P, 'w', encoding='utf-8').write(s)
print('changed:', n)