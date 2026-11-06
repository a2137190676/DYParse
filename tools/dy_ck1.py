# -*- coding: utf-8 -*-
# 修复抖音登录抓不到 Cookie 的三个问题：
#   1) checkDyCookie 只存 UIFID，从不存完整 Cookie（KEY_DY_COOKIE 没写过）
#   2) 从不检测 sessionid/sid_tt，所以登录成功也不会退出
#   3) 轮询只有 90 秒，扫码登录经常超时
import io

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/MainActivity.java'
d = io.open(P, encoding='utf-8').read()
n = 0

# ---------- 1) 重写 checkDyCookie ----------
old = '''            // 只在内容有变化时打日志，避免刷屏
            if(cm != null && cm.equals(dyLastCkSnap)) return;
            dyLastCkSnap = cm;
            Log.i("DYP","DY_CK[" + tag + "] len=" + (cm==null?-1:cm.length())
                + " TEMP=" + ufTemp.length() + " UIFID=" + ufPlain.length()
                + " names=" + nm);
            // 优先用 UIFID_TEMP；没有时退回 UIFID
            String uf = ufTemp.length() >= 32 ? ufTemp : ufPlain;
            String src = ufTemp.length() >= 32 ? "TEMP" : "UIFID";
            if(uf.length() >= 32 && !dyUifidGot){
                dyUifidGot = true;
                dyCookieLast = cm;
                getSharedPreferences(SP, MODE_PRIVATE).edit().putString(KEY_UIFID, uf).apply();
                Log.i("DYP","DY_UIFID_SAVED firstGotAt=" + tag + " src=" + src + " len=" + uf.length());
            }
        }catch(Throwable t5){ Log.i("DYP","DY_CK_ERR " + t5); }'''

new = '''            // 只在内容有变化时打日志，避免刷屏
            if(cm != null && cm.equals(dyLastCkSnap)) return;
            dyLastCkSnap = cm;

            // ---- 解析关键 Cookie ----
            String sessId = "", sidTt = "", uidTt = "";
            if(cm != null){
                for(String kv : cm.split(";")){
                    int e4 = kv.indexOf('=');
                    if(e4 <= 0) continue;
                    String k4 = kv.substring(0, e4).trim();
                    String v4 = kv.substring(e4 + 1).trim();
                    if(k4.equals("sessionid")) sessId = v4;
                    else if(k4.equals("sessionid_ss")) { if(sessId.length()==0) sessId = v4; }
                    else if(k4.equals("sid_tt")) sidTt = v4;
                    else if(k4.equals("uid_tt")) uidTt = v4;
                }
            }
            boolean loggedIn = sessId.length() >= 8 || sidTt.length() >= 8;

            Log.i("DYP","DY_CK[" + tag + "] len=" + (cm==null?-1:cm.length())
                + " TEMP=" + ufTemp.length() + " UIFID=" + ufPlain.length()
                + " sessionid=" + sessId.length() + " sid_tt=" + sidTt.length()
                + " loggedIn=" + loggedIn
                + " names=" + nm);

            // 优先用 UIFID_TEMP；没有时退回 UIFID
            String uf = ufTemp.length() >= 32 ? ufTemp : ufPlain;
            String src = ufTemp.length() >= 32 ? "TEMP" : "UIFID";
            if(uf.length() >= 32 && !dyUifidGot){
                dyUifidGot = true;
                dyCookieLast = cm;
                getSharedPreferences(SP, MODE_PRIVATE).edit().putString(KEY_UIFID, uf).apply();
                Log.i("DYP","DY_UIFID_SAVED firstGotAt=" + tag + " src=" + src + " len=" + uf.length());
            }

            // ---- 关键修复：把完整 Cookie 持久化（之前从不写 KEY_DY_COOKIE）----
            if(cm != null && cm.length() > 40){
                saveDyCookieFull(cm, "probe:" + tag);
            }

            // ---- 检测到登录态：自动退出登录页并提示 ----
            if(loggedIn && !dyCookieLoggedIn){
                dyCookieLoggedIn = true;
                Log.i("DYP","DY_LOGIN_OK sessionid=" + sessId.length()
                    + " sid_tt=" + sidTt.length());
                h.post(new Runnable(){ public void run(){
                    s("抖音登录成功！Cookie 已保存（sessionid 已捕获）");
                    showHintToast("抖音登录成功");
                    stopDyLogin();
                }});
            }
        }catch(Throwable t5){ Log.i("DYP","DY_CK_ERR " + t5); }'''

if old in d:
    d = d.replace(old, new, 1); n += 1; print('checkDyCookie 重写 OK')
else:
    print('MISS checkDyCookie')

io.open(P, 'w', encoding='utf-8').write(d)
print('changed:', n)

# 检查已有工具函数
for fn in ['void saveDyCookieFull', 'void stopDyLogin', 'KEY_DY_COOKIE', 'dyCookieLoggedIn']:
    print(fn, '存在:', fn in d)