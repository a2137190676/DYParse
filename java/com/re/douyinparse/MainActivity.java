package com.re.douyinparse;
import android.app.Activity;
import android.app.Dialog;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.view.View;
import android.widget.*;
import java.io.*;
import java.io.File;
import java.net.*;
import java.util.*;
import java.util.regex.*;

public class MainActivity extends Activity {
    EditText input; TextView btn; TextView status; LinearLayout out; WebView web;
    TextView btnPlay, btnDl, btnFull, btnAudio, btnMusic; LinearLayout actions; ImageView img;
    /** 抖音 detail 里 music.play_url 的完整 MP3（BGM），非空时显示 BGM 下载按钮 */
    String curBgm = "";
    String curBgmTitle = "";
    /** 实况角标（LIVE），图集里标出哪张能存成 MP4 */
    TextView liveBadge;
    /** X（Twitter）多视频：一条推可带 2~4 个视频，下载前让用户选第几个 */
    final java.util.List<String> curXVideos = new java.util.ArrayList<String>();
    final java.util.List<String> curXVidDur = new java.util.ArrayList<String>();
    final java.util.List<String> curXVidSize = new java.util.ArrayList<String>();
    /** 服务器机场流量是否可用（false = 需要用户自己挂代理） */
    boolean xSrvUsable = true;
    String xSrvReason = "";
    /** X 直连播放失败 → 已回退过服务器中转，防止无限循环 */
    boolean xSrvFallbackDone = false;
    /** YouTube 直连播放失败 → 已回退过服务器流式中转，防止无限循环 */
    boolean ytSrvFallbackDone = false;
    boolean biliSrvFallbackDone = false;
    // 小窗播放器（ExoPlayer + TextureView）
    android.view.TextureView video;
    android.view.Surface videoSurface;
    androidx.media3.exoplayer.ExoPlayer miniPlayer;
    // 播放器当前的代理/站点标记，配置变了就重建播放器
    String miniProxyKey = null;
    boolean miniWasTT = false;
    View videoBox, miniBar;
    TextView miniTimeNow, miniTimeAll, miniPlay, miniSpeed, miniFull;
    SeekBar miniSeek;
    // 下载/转码进度
    LinearLayout progressBox;
    TextView progressLabel, progressPct;
    ProgressBar progressBar;
    volatile boolean progCancel = false;
    int miniVideoW = 0, miniVideoH = 0;
    boolean miniPrepared = false, miniSeeking = false, miniZoomedLongPress = false;
    float miniDownX = 0, miniDownY = 0;
    static final float[] MINI_SPEEDS = {0.5f, 1.0f, 1.25f, 1.5f, 2.0f};
    static final String[] MINI_SPEED_LABELS = {"0.5X", "1.0X", "1.25X", "1.5X", "2.0X"};
    int miniSpeedIdx = 1;
    long miniLastTapTime = 0;
    float miniLastTapX = 0;
    String curPlay="", curCover="", curWs="", curImgs="", curId="", curDesc="";
    /** YouTube direct：独立音轨直链（itag 140 m4a），与 curPlay 合并播放/下载 */
    String curAudio="";
    /** YouTube 下载专用：解析拿到的 googlevideo 直链（播放可能 403 回退，下载仍优先直连） */
    String ytDirectPlay="";
    String ytDirectAudio="";
    /** 快手：分享页给的独立音轨 m4a，非空时优先用它转 MP3 */
    String curMusic="";
    String curShowImg="";
    // ===== 主题与关于 =====
    TextView btnTheme;
    boolean curDark = false;
    String curResult = "";
    static final String SP = "dyp_pref";
    static final String KEY_DARK = "dark_mode";
    static final String KEY_PROXY_ON = "proxy_on";       // 内置代理（服务器中转）开关，默认关
    /** 抖音访客凭据 UIFID_TEMP：由真实浏览器通过挑战后签发，缺失时 detail 接口一律 403 */
    static final String KEY_UIFID = "dy_uifid_temp";
    /** YouTube Cookie：WebView 登录后自动抓取（含 HttpOnly），服务器 yt-dlp 带登录态解析 */
    static final String KEY_YT_COOKIE = "yt_cookie";
    /** 自建服务器地址（可配置）：用户自填值 > 内置默认 > 本地/Gitee 回退 */
    static final String KEY_DYP_SRV = "dyp_srv";
    /** 用户点了「下个版本再提醒」：记下跳过的版本号，该版本及以下不再弹窗 */
    static final String KEY_SKIP_VER = "skip_ver";
    /** 强制更新：version.json 里带 "force":1 或 "min_version"，低于 min_version 必须更新 */
    static final String KEY_FORCE_MIN = "force_min_ver";
    /** 用户已同意免责声明（不同意无法使用） */
    static final String KEY_AGREE = "agree_declare_v1";
    /** 分类下载路径（空 = 用默认 Download/DYParse/<分类>） */
    static final String KEY_DL_VIDEO = "dl_video";
    static final String KEY_DL_IMG   = "dl_img";
    static final String KEY_DL_AUDIO = "dl_audio";
    static final String KEY_DL_MIX   = "dl_mix";
    /** 动图（实况 GIF / WebP 动图）单独目录 */
    static final String KEY_DL_ANIM  = "dl_anim";
    /** 解析后自动播放（默认关闭） */
    static final String KEY_AUTO_PLAY = "auto_play";
    /** 解析后自动下载（默认关闭） */
    static final String KEY_AUTO_DL   = "auto_dl";
    /** 本次解析是否已触发过自动播放/下载，防止一次解析重复触发 */
    volatile boolean autoFired = false;
    /** 解析成功后自动清空输入框（默认关闭） */
    static final String KEY_CLEAR_INPUT = "clear_input";
    /** 兼容旧版单一自定义路径 key */
    static final String KEY_DL_PATH  = "dl_path";
    /** 内置服务器默认地址。
     *  取值来自 SrvSecret.serverAddr()（字节异或混淆，防 strings 直接提取），
     *  不再使用 SrvConfig 的明文注入 —— 明文会让 `strings classes.dex`
     *  直接暴露服务器 IP，等于把加速层地址公开。
     *  开源仓库里 SrvSecret 是空壳（返回 ""），所以开源版天然无内置服务器。 */
    static final String DYP_SRV_DEFAULT = SrvSecret.serverAddr();

    /** 该 URL 是否属于 TikTok 域（含其 CDN） */
    static boolean isTikTokUrl(String url){
        if(url == null) return false;
        String u = url.toLowerCase();
        return u.contains("tiktok.com") || u.contains("tiktokcdn") || u.contains("tiktokv")
            || u.contains("byteoversea") || u.contains("ibytedtos") || u.contains("muscdn");
    }

    /** 该 URL 是否属于 X / Twitter 域（含其 CDN 与解析镜像） */
    static boolean isXDomain(String url){
        return XParser.isXUrl(url)
            || (url != null && (url.contains("twimg.com") || url.contains("fxtwitter.com")));
    }

    /** 是否为海外平台链接（TikTok/X/YouTube/IG + CDN） */
    static boolean isOverseasUrl(String url){
        return isTikTokUrl(url) || isXDomain(url) || YtParser.isYtUrl(url)
            || InsParser.isInsUrl(url)
            || (url != null && (url.contains("googlevideo.com")
                || url.contains("videoplayback")));
    }

    /** YouTube 登录模式（WebView 登录 → 抓 Cookie） */
    volatile boolean ytLoginMode = false;
    /** Instagram 登录模式（WebView 登录 → 抓 Cookie） */
    volatile boolean insLoginMode = false;
    /** B 站登录模式（WebView 登录 → 抓 SESSDATA） */
    volatile boolean biliLoginMode = false;
    /** 抖音登录/探测模式（WebView 打开抖音 → 抓 UIFID_TEMP） */
    volatile boolean dyLoginMode = false;
    /** 临时探测开关（非 null 时记录抖音请求头 Cookie） */
    String dyProbe = null;
    String dyCookieLast = "";   // 最近一次抓到的抖音完整 Cookie
    /** Cookie 快照，用来判断内容是否变化（避免刷屏） */
    String dyLastCkSnap = null;
    /** 是否已经拿到过 UIFID_TEMP（只记录首次出现的时机） */
    volatile boolean dyUifidGot = false;
    volatile boolean dyWatchRun = false;
    Thread dyWatchThread = null;
    /** 静默自动获取凭据：是否正在跑 / 完成后回调 */
    volatile boolean dyAutoRunning = false;
    Runnable dyAutoCallback = null;
    String biliCookieLast = "";  // 最近一次抓到的 B站 完整 Cookie
    volatile boolean ytCkUploading = false;
    String ytCookieLast = "";   // 最近一次抓到的完整 Cookie（含 HttpOnly）
    String insCookieLast = "";  // 最近一次抓到的 IG 完整 Cookie

    /** 设备唯一标识：上传 Cookie 时按 uid 归档 */
    static String ytUid(Context ctx){
        try{
            android.content.SharedPreferences p = ctx.getSharedPreferences(SP, android.content.Context.MODE_PRIVATE);
            String u = p.getString("yt_uid", "");
            if(u.length() < 8){
                u = java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 16);
                p.edit().putString("yt_uid", u).apply();
            }
            return u;
        }catch(Throwable t){ return "anon"; }
    }

    /** 从 WebView 请求头里抓 YouTube 完整 Cookie（CookieManager 拿不到 HttpOnly 的 SID/HSID） */
    void maybeGrabYtCookie(String url, java.util.Map<String,String> headers){
        if(!ytLoginMode) return;
        try{
            if(url == null || !(url.contains("youtube.com") || url.contains("google.com")
                    || url.contains("accounts.google") || url.contains("youtubei"))) return;
            if(headers == null) return;
            String ck = headers.get("Cookie");
            if(ck == null) return;
            String cm = null;
            try{ cm = CookieManager.getInstance().getCookie("https://www.youtube.com/"); }catch(Throwable t){}
            String full = ck;
            // 合并 CookieManager 里可见的部分（SAPISID 等）
            if(cm != null && cm.length() > 0){
                String remain = cm;
                for(String kv : ck.split(";")){
                    int e = kv.indexOf('=');
                    if(e > 0){
                        String k = kv.substring(0, e).trim();
                        if(!remain.contains(k + "=")) remain = remain + "; " + kv;
                    }
                }
                full = remain;
            }
            // 登录成功判定：有 SAPISID + __Secure-* 之一
            if(full.contains("SAPISID") && (full.contains("__Secure-3PAPISID")
                    || full.contains("SID") || full.contains("LOGIN_INFO"))){
                if(full.length() > ytCookieLast.length() || !full.equals(ytCookieLast)){
                    ytCookieLast = full;
                    getSharedPreferences(SP, MODE_PRIVATE).edit().putString(KEY_YT_COOKIE, full).apply();
                    saveYtCookieLocal(full);
                    Log.i("DYP", "YT_CK_GRAB len=" + full.length() + " head=" + full.substring(0, Math.min(60, full.length())));
                    uploadYtCookie(full);
                    h.post(new Runnable(){ public void run(){
                        s("YouTube 登录成功！Cookie 已保存，解析时自动使用");
                        showHintToast("YouTube 登录成功");
                        stopYtLogin();
                    }});
                }
            }
        }catch(Throwable t){ Log.i("DYP", "YT_CK_GRAB_ERR " + t); }
    }

    // ================= Instagram Cookie（WebView 登录 → 抓取 → 本地直连） =================

    /** 从 WebView 请求头里抓 Instagram 完整 Cookie（sessionid 常是 HttpOnly） */
    void maybeGrabInsCookie(String url, java.util.Map<String,String> headers){
        if(!insLoginMode) return;
        try{
            if(url == null) return;
            String u = url.toLowerCase();
            if(!(u.contains("instagram.com") || u.contains("cdninstagram.com")
                    || u.contains("facebook.com"))) return;
            if(headers == null) return;
            String ck = headers.get("Cookie");
            if(ck == null || ck.length() == 0) return;

            // 合并 CookieManager 可见部分
            String cm = null;
            try{ cm = CookieManager.getInstance().getCookie("https://www.instagram.com/"); }catch(Throwable t){}
            String full = ck;
            if(cm != null && cm.length() > 0){
                String remain = cm;
                for(String kv : ck.split(";")){
                    int e = kv.indexOf('=');
                    if(e > 0){
                        String k = kv.substring(0, e).trim();
                        if(!remain.contains(k + "=")) remain = remain + "; " + kv;
                    }
                }
                full = remain;
            }
            // 登录成功判定：必须有 sessionid
            if(!full.contains("sessionid=")) return;
            if(full.equals(insCookieLast)) return;
            insCookieLast = full;

            int n = CookieStore.save(MainActivity.this, CookieStore.PLATFORM_INS, full);
            Log.i("DYP", "INS_CK_GRAB len=" + full.length() + " saved=" + n
                    + " file=" + CookieStore.filePath(MainActivity.this, CookieStore.PLATFORM_INS));
            // 只保存，不退出登录页——用户可能还要在页面里完成安全验证 / 解除风控
            h.post(new Runnable(){ public void run(){
                s("Cookie 已保存（" + n + " 项），页面保留：需要的话先完成安全验证，再点右上「完成」");
                updateInsLoginBar();
            }});
        }catch(Throwable t){ Log.i("DYP", "INS_CK_GRAB_ERR " + t); }
    }

    // ================= 抖音主页：桌面 UA + 登录态（唯一能拿全量列表的路径） =================
    // 实测结论：安卓 UA 只给 15 条；桌面 UA 不登录会要求登录；桌面 UA + 登录可翻到全部。

    volatile boolean dyProfileLoginMode = false;

    /** 设置页入口：抖音主页登录（用于解析完整作品列表） */
    void showDyProfileLoginMenu(){
        curDark = isDarkMode();
        boolean logged = dyProfileCookiesLikelyLoggedIn();
        int n = CookieStore.count(this, CookieStore.PLATFORM_DY);
        dypDlg()
            .setTitle("抖音主页登录")
            .setMessage("解析主页时要看到全部作品，需要登录抖音账号。\n\n"
                + "实测结论：\n"
                + "· 未登录：主页接口只回第一页，约 21 条，后面直接返回空\n"
                + "· 已登录：可以逐页翻完（实测 49 条作品 3 页拿全）\n"
                + "· 不需要访客凭据 UIFID，只要登录 Cookie + 签名就够\n\n"
                + "说明：抖音没有移动版网页登录入口，网页端登录会要求手机 App 扫码验证，"
                + "这是平台风控，无法绕过。\n"
                + "推荐「手动填 Cookie」：电脑浏览器登录后复制过来，一次搞定。\n\n"
                + "Cookie 只存手机本地，不上传服务器，也不发给任何第三方。\n"
                + (logged ? "\n当前状态：已检测到登录票据。" : "\n当前状态：还没有登录。"))
            .setPositiveButton("选择操作", new android.content.DialogInterface.OnClickListener(){
                public void onClick(android.content.DialogInterface d, int w){
                    showDyLoginActions();
                }})
            .setNegativeButton("关闭", null)
            .show();
    }

    /** 第二步：选择具体操作（用独立弹窗，避免 setMessage + setItems 把列表挤掉） */
    void showDyLoginActions(){
        curDark = isDarkMode();
        int n = CookieStore.count(this, CookieStore.PLATFORM_DY);
        final String[] items = new String[]{
            "页面内登录（网页端，可能要求扫码）",
            "手动填 Cookie（电脑浏览器复制，最稳）",
            (n > 0 ? "查看当前 Cookie 状态（已有 " + n + " 项）" : "查看当前 Cookie 状态（本地为空）"),
            "清空已保存的抖音 Cookie"
        };
        dypDlg()
            .setTitle("选择操作")
            .setItems(items, new android.content.DialogInterface.OnClickListener(){
                public void onClick(android.content.DialogInterface d, int w){
                    if(w == 0){
                        startDyProfileLogin();
                    } else if(w == 1){
                        showCookieDialog(CookieStore.PLATFORM_DY, "抖音");
                    } else if(w == 2){
                        showDyCookieStatus();
                    } else if(w == 3){
                        clearDyProfileLogin();
                    }
                }})
            .setNegativeButton("返回", null)
            .show();
    }

    /** 查看抖音 Cookie 当前状态（只显示名称，不显示值，避免泄露） */
    void showDyCookieStatus(){
        curDark = isDarkMode();
        StringBuilder b = new StringBuilder();
        String file = CookieStore.filePath(this, CookieStore.PLATFORM_DY);
        int n = CookieStore.count(this, CookieStore.PLATFORM_DY);
        b.append("文件：").append(file).append("\n\n");
        if(n == 0){
            b.append("本地还没有保存任何抖音 Cookie。\n\n");
        } else {
            java.util.List<String> names = CookieStore.names(this, CookieStore.PLATFORM_DY);
            boolean hasSid = names.contains("sessionid");
            boolean hasSidTt = names.contains("sid_tt");
            boolean hasUidTt = names.contains("uid_tt");
            b.append("已保存 ").append(n).append(" 项\n\n");
            b.append("登录票据：\n");
            b.append("  sessionid  ").append(hasSid ? "有" : "无").append("\n");
            b.append("  sid_tt     ").append(hasSidTt ? "有" : "无").append("\n");
            b.append("  uid_tt     ").append(hasUidTt ? "有" : "无").append("\n\n");
            if(hasSid || hasSidTt || hasUidTt){
                b.append("判断：登录态可用，主页可以翻完所有作品。\n\n");
            } else {
                b.append("判断：缺登录票据，主页只能拿到约 21 条。\n\n");
            }
            StringBuilder nm = new StringBuilder();
            for(String k : names){
                if(nm.length() > 0) nm.append(", ");
                nm.append(k);
            }
            b.append("全部名称：\n").append(nm);
        }
        dypDlg().setTitle("抖音 Cookie 状态").setMessage(b.toString())
            .setPositiveButton("知道了", null).create().show();
    }

    /** 打开抖音 PC 版登录页（桌面 UA），登录后 Cookie 自动进 CookieManager 并落盘到本地 */
    void startDyProfileLogin(){
        switchTab(0);
        setupWebView();
        ytLoginMode = false;
        insLoginMode = false;
        dyProfileLoginMode = true;
        dyMobileLogin = false;
        dyMobileHomeBlocked = false;
        profileMode = false;
        try{
            pageParse.setVisibility(View.GONE);
            web.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        }catch(Throwable t){}
        web.setVisibility(View.VISIBLE);
        web.bringToFront();
        try{
            WebSettings ws = web.getSettings();
            ws.setUserAgentString(DyDetail.UA);   // 桌面 UA 必须，否则拿不到全量
            if(android.os.Build.VERSION.SDK_INT >= 29){
                ws.setForceDark(WebSettings.FORCE_DARK_OFF);
            }
            if(android.os.Build.VERSION.SDK_INT >= 33){
                ws.setAlgorithmicDarkeningAllowed(false);
            }
        }catch(Throwable t){}
        showDyLoginBar();
        DiagLog.i(DiagLog.M_PARSE, "打开抖音登录页（桌面 UA，登录后 Cookie 自动存本地）");
        web.loadUrl("https://www.douyin.com/");
    }

    /** 是否处于移动版登录模式 */
    volatile boolean dyMobileLogin = false;
    /** 移动版登录时已拦过一次 PC 跳转（只拦一次，避免死循环） */
    volatile boolean dyMobileHomeBlocked = false;
    /** 主页翻页是否夹带 uifid。默认 true：带 uifid 时第 1 页能拿到 18 条；不带会连第 1 页都空 */
    volatile boolean dyWithUifid = true;
    /** WebView 自己发出的那条 aweme/post 请求的原始 URL（翻页参数以它为模板） */
    volatile String dyWebReqUrl = "";
    /** 主页解析时是否已切到桌面 UA 并跳到 PC 主页 */
    volatile boolean dyProfilePcUA = false;
    /** 是否已拦过一次打回手机版的跳转 */
    volatile boolean dyBlockedMobileShare = false;
    /** PC 主页是否正在自动滚动收集 */
    volatile boolean dyAutoScrolling = false;

    /** 在 onPageStarted 时注入桌面伪装（必须每页都注入，且要早于页面脚本执行） */
    void injectDesktopSpoof(android.webkit.WebView wv){
        try{
            String js =
                "(function(){try{"
                + "var def=function(o,k,v){try{Object.defineProperty(o,k,{get:function(){return v;},configurable:true});}catch(e){}};"
                + "def(navigator,'platform','Win32');"
                + "def(navigator,'vendor','Google Inc.');"
                + "def(navigator,'maxTouchPoints',0);"
                + "def(navigator,'hardwareConcurrency',8);"
                + "try{def(screen,'width',1920);def(screen,'height',1080);}catch(e){}"
                + "try{def(screen,'availWidth',1920);def(screen,'availHeight',1040);def(screen,'colorDepth',24);}catch(e){}"
                + "try{if(navigator.userAgentData){def(navigator.userAgentData,'platform','Windows');}}catch(e){}"
                + "window.__dySpoofed=1;"
                + "}catch(e){}})();";
            wv.evaluateJavascript(js, null);
        }catch(Throwable t){
            DiagLog.e(DiagLog.M_PARSE, "注入桌面伪装失败", t);
        }
    }

    void clearDyProfileLogin(){
        try{
            android.webkit.CookieManager cm = android.webkit.CookieManager.getInstance();
            String ck = cm.getCookie("https://www.douyin.com/");
            if(ck != null){
                for(String kv : ck.split(";")){
                    String k = kv.trim();
                    int e = k.indexOf('=');
                    if(e > 0) k = k.substring(0, e);
                    cm.setCookie("https://www.douyin.com/", k + "=; Max-Age=0; path=/");
                    cm.setCookie("https://www.iesdouyin.com/", k + "=; Max-Age=0; path=/");
                }
                cm.flush();
            }
            // 本地文件也一起清掉，否则 dyEffectiveCookie 还会读出来继续用
            CookieStore.clear(this, CookieStore.PLATFORM_DY);
            dyCookieLast = "";
            DiagLog.i(DiagLog.M_PARSE, "已清空抖音登录 Cookie（WebView + 本地文件）");
            showHintToast("已清空抖音登录状态");
        }catch(Throwable t){
            DiagLog.e(DiagLog.M_PARSE, "清空抖音登录失败", t);
        }
    }

/** 退出抖音登录页：和 YouTube 一样，直接回到解析页，不保留网页 */
    void stopDyProfileLogin(){
        if(!dyProfileLoginMode) return;
        dyProfileLoginMode = false;
        dyMobileLogin = false;
        dyMobileHomeBlocked = false;
        try{
            if(web != null){
                // 清掉登录页，避免下次解析时残留页面和 hook
                web.loadUrl("about:blank");
                web.setVisibility(View.GONE);
                // 恢复成 1x1 占位（YouTube 也是这么收起的）
                web.setLayoutParams(new LinearLayout.LayoutParams(1, 1));
            }
        }catch(Throwable t){}
        try{
            if(pageParse != null) pageParse.setVisibility(View.VISIBLE);
        }catch(Throwable t){}
        // 恢复成手机 UA，避免影响后续正常解析
        try{
            if(web != null && webReady){
                WebSettings ws = web.getSettings();
                ws.setUserAgentString(
                    "Mozilla/5.0 (Linux; Android 12; Pixel 5) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36");
                if(android.os.Build.VERSION.SDK_INT >= 29){
                    ws.setForceDark(WebSettings.FORCE_DARK_AUTO);
                }
                if(android.os.Build.VERSION.SDK_INT >= 33){
                    ws.setAlgorithmicDarkeningAllowed(true);
                }
            }
        }catch(Throwable t){}
        int n = CookieStore.count(this, CookieStore.PLATFORM_DY);
        DiagLog.i(DiagLog.M_PARSE, "退出抖音登录页 | 本地 Cookie " + n + " 项 | 已切回解析页");
        showHintToast(n > 0 ? ("登录完成，已回到解析页（Cookie " + n + " 项）")
                            : "已回到解析页（未抓到 Cookie）");
        try{ refreshSettingRows(); }catch(Throwable t){}
    }
    /** 手动填写的抖音 Cookie 是否可用 */
    boolean dyManualCookieOk(){
        try{
            String m = CookieStore.dump(this, CookieStore.PLATFORM_DY);
            return m != null && m.trim().length() > 0;
        }catch(Throwable t){ return false; }
    }

    /** 实际用的抖音 Cookie。
     *  优先级：CookieManager（WebView 实时，会自动刷新 msToken / __ac_signature / ttwid）
     *        → 本地文件（App 重启、WebView 数据被清后的兜底）
     *  注意：顺序不能反。本地文件是登录那一刻的快照，里面的 msToken 等签名字段不会更新，
     *  用它去翻页第 2 页就被服务端拒（这也是之前「第一次能拿 49 条、后面只 18 条」的原因）。 */
    String dyEffectiveCookie(){
        try{
            String cm = CookieManager.getInstance().getCookie("https://www.douyin.com/");
            if(cm != null && cm.length() > 0
                    && (cm.indexOf("sessionid=") >= 0 || cm.indexOf("sid_tt=") >= 0)){
                Log.i("DYP", "DY_CK_SOURCE webview(live) len=" + cm.length());
                return cm;
            }
        }catch(Throwable t){}
        try{
            String local = CookieStore.header(this, CookieStore.PLATFORM_DY);
            if(local != null && local.trim().length() > 0){
                Log.i("DYP", "DY_CK_SOURCE local(fallback) len=" + local.length());
                return local;
            }
        }catch(Throwable t){}
        try{
            return CookieManager.getInstance().getCookie("https://www.douyin.com/");
        }catch(Throwable t){ return null; }
    }

    /** App 启动时：把本地保存的抖音 Cookie 灌回 CookieManager（WebView 数据被清后仍能用） */
    void restoreDyCookieToWebView(){
        try{
            String local = CookieStore.header(this, CookieStore.PLATFORM_DY);
            if(local == null || local.length() < 32) return;
            String cm = CookieManager.getInstance().getCookie("https://www.douyin.com/");
            boolean liveOk = cm != null && (cm.indexOf("sessionid=") >= 0 || cm.indexOf("sid_tt=") >= 0);
            if(liveOk) {
                Log.i("DYP", "DY_CK_RESTORE skip webview_already_ok");
                return;
            }
            android.webkit.CookieManager c = android.webkit.CookieManager.getInstance();
            int n = 0;
            for(String kv : local.split(";")){
                String s = kv.trim();
                int e = s.indexOf('=');
                if(e <= 0) continue;
                String name = s.substring(0, e).trim();
                String val = s.substring(e + 1).trim();
                if(name.length() == 0 || val.length() == 0) continue;
                c.setCookie("https://www.douyin.com/", name + "=" + val + "; path=/");
                c.setCookie("https://www.iesdouyin.com/", name + "=" + val + "; path=/");
                n++;
            }
            c.flush();
            Log.i("DYP", "DY_CK_RESTORE ok n=" + n);
        }catch(Throwable t){
            Log.i("DYP", "DY_CK_RESTORE_ERR " + t);
        }
    }

    /** 粗略判断是否像登录状态：有 sessionid 之类的登录票据 */
    boolean dyProfileCookiesLikelyLoggedIn(){
        try{
            String ck = dyEffectiveCookie();
            if(ck == null) return false;
            return ck.indexOf("sessionid") >= 0 || ck.indexOf("sid_tt") >= 0
                    || ck.indexOf("uid_tt") >= 0;
        }catch(Throwable t){ return false; }
    }

    /** Instagram 登录 WebView：加载 IG 登录页，抓 Cookie 后保持页面，等用户点「完成」 */
    void startInsLogin(){
        switchTab(0);
        setupWebView();
        ytLoginMode = false;
        insLoginMode = true;
        try{
            pageParse.setVisibility(View.GONE);
            web.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        }catch(Throwable t){}
        web.setVisibility(View.VISIBLE);
        web.bringToFront();
        showInsLoginBar();
        try{
            WebSettings ws = web.getSettings();
            if(android.os.Build.VERSION.SDK_INT >= 29){
                ws.setForceDark(WebSettings.FORCE_DARK_OFF);
            }
            if(android.os.Build.VERSION.SDK_INT >= 33){
                ws.setAlgorithmicDarkeningAllowed(false);
            }
            ws.setUserAgentString(
                "Mozilla/5.0 (Linux; Android 12; Pixel 5) AppleWebKit/537.36 "
                + "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36");
        }catch(Throwable t){}
        web.loadUrl("https://www.instagram.com/accounts/login/");
    }

    /** IG 登录顶栏：悬浮在 WebView 上方（保存状态 + 完成按钮） */
    TextView insBarStatus;
    android.widget.Button insBarDone;

    void showInsLoginBar(){
        try{
            if(insBarDone != null && insBarDone.getParent() != null) return;   // 已显示
            android.widget.FrameLayout host = (android.widget.FrameLayout) web.getParent();
            if(host == null) return;

            LinearLayout bar = new LinearLayout(this);
            bar.setOrientation(LinearLayout.HORIZONTAL);
            bar.setBackgroundColor(curDark ? 0xFF22262E : 0xFFFFFFFF);
            bar.setPadding(dp(12), dp(8), dp(8), dp(8));
            bar.setId(900001);

            insBarStatus = new TextView(this);
            insBarStatus.setTextSize(12);
            insBarStatus.setTextColor(curDark ? 0xFFB8BEC9 : 0xFF4B5563);
            insBarStatus.setText("等待登录…登录后可继续在页面里完成安全验证");
            LinearLayout.LayoutParams lp1 = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            bar.addView(insBarStatus, lp1);

            insBarDone = new android.widget.Button(this);
            insBarDone.setText("完成");
            insBarDone.setTextSize(13);
            insBarDone.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){
                int n = CookieStore.count(MainActivity.this, CookieStore.PLATFORM_INS);
                if(n <= 0){
                    showHintToast("还没抓到 Cookie，请先在页面里登录");
                    return;
                }
                showHintToast("Instagram Cookie 已保存（" + n + " 项）");
                hideInsLoginBar();
                stopInsLogin();
                buildSettingsPage();
            }});
            bar.addView(insBarDone);

            android.widget.FrameLayout.LayoutParams flp = new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT);
            flp.gravity = android.view.Gravity.TOP;
            host.addView(bar, flp);
            bar.bringToFront();
        }catch(Throwable t){ Log.i("DYP", "INS_BAR_ERR " + t); }
    }

    void hideInsLoginBar(){
        try{
            if(insBarDone != null && insBarDone.getParent() != null){
                ((android.view.ViewGroup) insBarDone.getParent().getParent()).removeView(
                    (View) insBarDone.getParent());
            }
        }catch(Throwable t){}
        insBarStatus = null;
        insBarDone = null;
    }

    void updateInsLoginBar(){
        try{
            if(insBarStatus == null) return;
            int n = CookieStore.count(this, CookieStore.PLATFORM_INS);
            insBarStatus.setText(n > 0
                ? "Cookie 已保存 " + n + " 项 · 可继续完成安全验证，好了点「完成」"
                : "等待登录…");
        }catch(Throwable t){}
    }

    /** 退出 Instagram 登录模式 */
    void stopInsLogin(){
        insLoginMode = false;
        hideInsLoginBar();
        try{
            if(web != null && webReady){
                WebSettings ws = web.getSettings();
                ws.setUserAgentString(
                    "Mozilla/5.0 (Linux; Android 12; Pixel 5) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36");
            }
        }catch(Throwable t){}
        try{ if(pageParse != null) pageParse.setVisibility(View.VISIBLE); }catch(Throwable t){}
        try{
            web.setLayoutParams(new LinearLayout.LayoutParams(1, 1));
        }catch(Throwable t){}
        web.setVisibility(View.GONE);
    }

    // ================= B站 Cookie（WebView 登录 → 抓 SESSDATA → 本地直连） =================

    /** 从 WebView 请求头里抓 B站 完整 Cookie（SESSDATA 是 HttpOnly，只能从请求头拿） */
    void maybeGrabBiliCookie(String url, java.util.Map<String,String> headers){
        if(!biliLoginMode) return;
        try{
            if(url == null) return;
            String u = url.toLowerCase();
            if(!(u.contains("bilibili.com") || u.contains("bilivideo.com"))) return;
            if(headers == null) return;
            String ck = headers.get("Cookie");
            if(ck == null || ck.length() == 0) return;

            // 合并 CookieManager 可见部分（buvid3 等非 HttpOnly 的）
            String cm = null;
            try{ cm = CookieManager.getInstance().getCookie("https://www.bilibili.com/"); }catch(Throwable t){}
            String full = ck;
            if(cm != null && cm.length() > 0){
                String remain = cm;
                for(String kv : ck.split(";")){
                    int e = kv.indexOf('=');
                    if(e > 0){
                        String k = kv.substring(0, e).trim();
                        if(!remain.contains(k + "=")) remain = remain + "; " + kv;
                    }
                }
                full = remain;
            }
            // 登录成功判定：必须有 SESSDATA
            if(!full.contains("SESSDATA=")) return;
            if(full.equals(biliCookieLast)) return;
            biliCookieLast = full;

            int n = CookieStore.save(MainActivity.this, CookieStore.PLATFORM_BILI, full);
            // 抓到新的 SESSDATA，作废旧的 buvid3 缓存，让下次请求重新拼
            BiliParser.buvid3 = "";
            BiliParser.biliLoginLogged = false;
            Log.i("DYP", "BILI_CK_GRAB len=" + full.length() + " saved=" + n
                    + " file=" + CookieStore.filePath(MainActivity.this, CookieStore.PLATFORM_BILI));
            h.post(new Runnable(){ public void run(){
                s("B站 Cookie 已保存（" + n + " 项），页面保留：需要的话先完成验证，再点右上「完成」");
                updateBiliLoginBar();
            }});
        }catch(Throwable t){ Log.i("DYP", "BILI_CK_GRAB_ERR " + t); }
    }

    /** 抖音登录/凭据抓取模式：WebView 打开抖音网页，从请求头抓 UIFID_TEMP（HttpOnly） */
    void startDyLogin(){
        switchTab(0);
        setupWebView();
        ytLoginMode = false;
        insLoginMode = false;
        biliLoginMode = false;
        dyLoginMode = true;
        dyProbe = "";
        try{
            pageParse.setVisibility(View.GONE);
            web.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        }catch(Throwable t){}
        web.setVisibility(View.VISIBLE);
        web.bringToFront();
        try{
            WebSettings ws = web.getSettings();
            if(android.os.Build.VERSION.SDK_INT >= 29){
                ws.setForceDark(WebSettings.FORCE_DARK_OFF);
            }
            if(android.os.Build.VERSION.SDK_INT >= 33){
                ws.setAlgorithmicDarkeningAllowed(false);
            }
            // 桌面 UA：抖音 PC 站点才走 detail 接口那套
            ws.setUserAgentString(DyDetail.UA);
        }catch(Throwable t){}
        web.loadUrl("https://www.douyin.com/");
        startDyCookieWatch();
        Log.i("DYP", "DY_LOGIN_START");
    }

    /** 探测指定页面的 Cookie 下发时机（--es dyurl <url> 启动） */
    void startDyProbeUrl(String target){
        switchTab(0);
        setupWebView();
        ytLoginMode = false;
        insLoginMode = false;
        biliLoginMode = false;
        dyLoginMode = true;
        dyUifidGot = false;
        dyLastCkSnap = null;
        try{
            pageParse.setVisibility(View.GONE);
            web.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        }catch(Throwable t){}
        web.setVisibility(View.VISIBLE);
        web.bringToFront();
        try{
            WebSettings ws = web.getSettings();
            if(android.os.Build.VERSION.SDK_INT >= 29){
                ws.setForceDark(WebSettings.FORCE_DARK_OFF);
            }
            if(android.os.Build.VERSION.SDK_INT >= 33){
                ws.setAlgorithmicDarkeningAllowed(false);
            }
            ws.setUserAgentString(DyDetail.UA);
        }catch(Throwable t){}
        Log.i("DYP", "DY_PROBE_URL " + target);
        web.loadUrl(target);
        startDyCookieWatch();
    }

    /** 退出抖音凭据抓取模式 */
    void stopDyLogin(){
        dyLoginMode = false;
        dyProbe = null;
        stopDyCookieWatch();
        Log.i("DYP", "DY_LOGIN_STOP");
    }

    /** 当前是否已有抖音访客凭据 */
    boolean dyHasUifid(){
        try{
            String v = getSharedPreferences(SP, MODE_PRIVATE).getString(KEY_UIFID, "");
            return v != null && v.length() >= 32;
        }catch(Throwable t){ return false; }
    }

    /** 取当前访客凭据值（没有返回空串） */
    /** 当前 uifid：优先用 WebView 实时 Cookie 里的（登录后下发，最新最准），
     *  没有再回落到本地文件，最后才用 SharedPreferences 里的访客凭据 */
    String dyCurUifid(){
        try{
            // 1) WebView 实时 Cookie
            try{
                String ck = CookieManager.getInstance().getCookie("https://www.douyin.com/");
                String v = pickUifid(ck);
                if(v.length() >= 32){ Log.i("DYP", "DY_UID_SRC webview len=" + v.length()); return v; }
            }catch(Throwable t){}
            // 2) 本地文件
            try{
                String ck = CookieStore.header(this, CookieStore.PLATFORM_DY);
                String v = pickUifid(ck);
                if(v.length() >= 32){ Log.i("DYP", "DY_UID_SRC local len=" + v.length()); return v; }
            }catch(Throwable t){}
            // 3) 访客凭据
            if(!dyHasUifid()) return "";
            String v2 = getSharedPreferences(SP, MODE_PRIVATE).getString(KEY_UIFID, "");
            if(v2 != null && v2.length() > 0) Log.i("DYP", "DY_UID_SRC sp len=" + v2.length());
            return v2 == null ? "" : v2;
        }catch(Throwable t){ return ""; }
    }

    /** 从 cookie 串里挑 UIFID_TEMP / UIFID / uifid */
    static String pickUifid(String ck){
        if(ck == null) return "";
        String v = cookieValueOf(ck, "UIFID_TEMP");
        if(v.length() < 32) v = cookieValueOf(ck, "UIFID");
        if(v.length() < 32) v = cookieValueOf(ck, "uifid");
        return v;
    }

    /** 抖音凭据状态文案（设置页副标题用） */
    String curDyUifidLabel(){
        if(dyHasUifid()){
            String v = getSharedPreferences(SP, MODE_PRIVATE).getString(KEY_UIFID, "");
            return "已有凭据（" + v.length() + " 字符）  实况图可本地解析";
        }
        return "未获取  点击开始自动获取（约 5-10 秒）";
    }

    /** 静默自动获取抖音访客凭据：后台开一个隐藏 WebView 过挑战，拿到就存，不打扰用户。
     *  返回 true 表示已发起；结果通过 dyAutoCallback 回报。 */
    void autoFetchDyUifid(final Runnable onDone){
        if(dyHasUifid()){
            if(onDone != null) onDone.run();
            return;
        }
        if(dyAutoRunning){
            if(onDone != null) dyAutoCallback = onDone;
            return;
        }
        dyAutoRunning = true;
        dyAutoCallback = onDone;
        Log.i("DYP", "DY_AUTO_START 开始静默获取访客凭据");

        h.post(new Runnable(){ public void run(){
            try{
                final WebView w = new WebView(MainActivity.this);
                // 隐藏但必须可见才能过 JS 挑战：挪到屏幕外、1x1
                android.widget.FrameLayout.LayoutParams lp = new android.widget.FrameLayout.LayoutParams(1, 1);
                lp.leftMargin = -10; lp.topMargin = -10;
                w.setLayoutParams(lp);
                w.setAlpha(0.01f);
                try{
                    WebSettings ws = w.getSettings();
                    ws.setJavaScriptEnabled(true);
                    ws.setDomStorageEnabled(true);
                    ws.setUserAgentString(DyDetail.UA);
                    if(android.os.Build.VERSION.SDK_INT >= 21){
                        ws.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
                    }
                }catch(Throwable t){}
                CookieManager.getInstance().setAcceptCookie(true);
                try{ CookieManager.getInstance().setAcceptThirdPartyCookies(w, true); }catch(Throwable t){}

                final android.view.ViewGroup rootVg = (android.view.ViewGroup) findViewById(R.id.root);
                if(rootVg != null) rootVg.addView(w);

                w.setWebViewClient(new WebViewClient(){
                    public void onPageFinished(WebView vw, String u){
                        Log.i("DYP", "DY_AUTO_PAGE " + u);
                    }
                });

                // 轮询 Cookie，最多 20 秒
                final long t0 = System.currentTimeMillis();
                final Runnable[] poll = new Runnable[1];
                poll[0] = new Runnable(){ public void run(){
                    boolean done = false;
                    String got = "";
                    try{
                        String cm = CookieManager.getInstance().getCookie("https://www.douyin.com/");
                        if(cm != null){
                            for(String kv : cm.split(";")){
                                int e4 = kv.indexOf('=');
                                if(e4 <= 0) continue;
                                String k4 = kv.substring(0, e4).trim();
                                if(k4.equals("UIFID_TEMP")){ got = kv.substring(e4 + 1).trim(); }
                            }
                        }
                    }catch(Throwable t){}

                    if(got.length() >= 32){
                        getSharedPreferences(SP, MODE_PRIVATE).edit().putString(KEY_UIFID, got).apply();
                        Log.i("DYP", "DY_AUTO_OK len=" + got.length()
                            + " cost=" + (System.currentTimeMillis() - t0) + "ms");
                        done = true;
                    } else if(System.currentTimeMillis() - t0 > 20000){
                        Log.i("DYP", "DY_AUTO_TIMEOUT 20s 未拿到凭据");
                        done = true;
                    }

                    if(done){
                        dyAutoRunning = false;
                        try{ if(rootVg != null) rootVg.removeView(w); }catch(Throwable t){}
                        try{ w.destroy(); }catch(Throwable t){}
                        Runnable cb = dyAutoCallback;
                        dyAutoCallback = null;
                        if(cb != null){ try{ cb.run(); }catch(Throwable t){} }
                    } else {
                        h.postDelayed(poll[0], 1500);
                    }
                }};
                h.postDelayed(poll[0], 2500);
                w.loadUrl("https://www.douyin.com/");
            }catch(Throwable t){
                Log.i("DYP", "DY_AUTO_ERR " + t);
                dyAutoRunning = false;
                Runnable cb = dyAutoCallback;
                dyAutoCallback = null;
                if(cb != null){ try{ cb.run(); }catch(Throwable t2){} }
            }
        }});
    }

    /** 读一次抖音 Cookie，打印全貌并记录 UIFID_TEMP 首次出现的时机。
     *  tag 用来区分是「主页加载完」还是「点开视频后」下发的。 */
    void checkDyCookie(String tag){
        try{
            String cm = CookieManager.getInstance().getCookie("https://www.douyin.com/");
            String ufTemp = "";
            String ufPlain = "";
            StringBuilder nm = new StringBuilder();
            if(cm != null){
                for(String kv : cm.split(";")){
                    int e3 = kv.indexOf('=');
                    if(e3 <= 0) continue;
                    String k3 = kv.substring(0, e3).trim();
                    nm.append(k3).append(",");
                    String v3 = kv.substring(e3 + 1).trim();
                    if(k3.equals("UIFID_TEMP")) ufTemp = v3;
                    else if(k3.equals("UIFID") || k3.equals("uifid")) ufPlain = v3;
                }
            }
            // 只在内容有变化时打日志，避免刷屏
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
        }catch(Throwable t5){ Log.i("DYP","DY_CK_ERR " + t5); }
    }

    /** 启动 Cookie 轮询（抖音凭据抓取期间每 3 秒查一次） */
    void startDyCookieWatch(){
        stopDyCookieWatch();
        dyWatchRun = true;
        dyWatchThread = new Thread(new Runnable(){ public void run(){
            int sec = 0;
            while(dyWatchRun && sec < 90){
                try{ Thread.sleep(3000); }catch(Throwable t){ break; }
                sec += 3;
                if(!dyWatchRun) break;
                final int s2 = sec;
                h.post(new Runnable(){ public void run(){ checkDyCookie("t" + s2 + "s"); }});
            }
            Log.i("DYP","DY_WATCH_END");
        }});
        dyWatchThread.start();
    }

    void stopDyCookieWatch(){
        dyWatchRun = false;
        dyWatchThread = null;
    }

    void startBiliLogin(){
        switchTab(0);
        setupWebView();
        ytLoginMode = false;
        insLoginMode = false;
        biliLoginMode = true;
        try{
            pageParse.setVisibility(View.GONE);
            web.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        }catch(Throwable t){}
        web.setVisibility(View.VISIBLE);
        web.bringToFront();
        showBiliLoginBar();
        try{
            WebSettings ws = web.getSettings();
            if(android.os.Build.VERSION.SDK_INT >= 29){
                ws.setForceDark(WebSettings.FORCE_DARK_OFF);
            }
            if(android.os.Build.VERSION.SDK_INT >= 33){
                ws.setAlgorithmicDarkeningAllowed(false);
            }
            // B站 登录页用手机 UA 正常，但 CDN 拉流用桌面 UA（在 BiliParser.UA_DESKTOP）
            ws.setUserAgentString(
                "Mozilla/5.0 (Linux; Android 12; Pixel 5) AppleWebKit/537.36 "
                + "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36");
        }catch(Throwable t){}
        web.loadUrl("https://passport.bilibili.com/login");
    }

    /** B站 登录顶栏 */
    TextView biliBarStatus;
    android.widget.Button biliBarDone;

    /** 抖音登录页顶部操作条：显示 Cookie 抓取状态 + 完成/取消 */
    void showDyLoginBar(){
        try{
            if(dyBarDone != null && dyBarDone.getParent() != null) return;
            // web 的父容器可能是 LinearLayout / FrameLayout / RelativeLayout，不能硬转 FrameLayout
            android.view.ViewGroup host = (web == null) ? null
                    : (web.getParent() instanceof android.view.ViewGroup
                        ? (android.view.ViewGroup) web.getParent() : null);
            if(host == null) return;

            LinearLayout bar = new LinearLayout(this);
            bar.setOrientation(LinearLayout.HORIZONTAL);
            bar.setBackgroundColor(curDark ? 0xFF22262E : 0xFFFFFFFF);
            bar.setPadding(dp(12), dp(8), dp(8), dp(8));
            bar.setId(900004);

            dyBarStatus = new TextView(this);
            dyBarStatus.setTextSize(12);
            dyBarStatus.setTextColor(curDark ? 0xFFB8BEC9 : 0xFF4B5563);
            int n0 = CookieStore.count(this, CookieStore.PLATFORM_DY);
            dyBarStatus.setText(n0 > 0
                ? ("已登录（" + n0 + " 项），可点「完成」")
                : "登录成功后自动回解析页…");
            LinearLayout.LayoutParams lp1 = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            bar.addView(dyBarStatus, lp1);

            dyBarDone = new android.widget.Button(this);
            dyBarDone.setText("完成");
            dyBarDone.setTextSize(13);
            dyBarDone.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){
                int n = CookieStore.count(MainActivity.this, CookieStore.PLATFORM_DY);
                hideDyLoginBar();
                stopDyProfileLogin();
                if(n <= 0) showHintToast("还没抓到 Cookie，可以改用「手动填 Cookie」");
            }});
            bar.addView(dyBarDone);

            dyBarCancel = new android.widget.Button(this);
            dyBarCancel.setText("取消");
            dyBarCancel.setTextSize(13);
            dyBarCancel.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){
                hideDyLoginBar();
                stopDyProfileLogin();
            }});
            bar.addView(dyBarCancel);

            // LayoutParams 类型必须和父容器匹配，否则 addView 会抛 ClassCastException
            android.view.ViewGroup.LayoutParams flp;
            if(host instanceof android.widget.FrameLayout){
                android.widget.FrameLayout.LayoutParams p =
                    new android.widget.FrameLayout.LayoutParams(
                        android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                        android.widget.FrameLayout.LayoutParams.WRAP_CONTENT);
                p.gravity = android.view.Gravity.TOP;
                flp = p;
            } else if(host instanceof LinearLayout){
                flp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            } else {
                flp = new android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
            }
            host.addView(bar, flp);
        }catch(Throwable t){ DiagLog.e(DiagLog.M_PARSE, "显示抖音登录操作条失败", t); }
    }

    void hideDyLoginBar(){
        try{
            if(dyBarDone != null && dyBarDone.getParent() instanceof android.view.ViewGroup){
                ((android.view.ViewGroup) dyBarDone.getParent().getParent()).removeView(
                    (View) dyBarDone.getParent());
            }
        }catch(Throwable t){}
        dyBarDone = null;
        dyBarStatus = null;
        dyBarCancel = null;
    }

    android.widget.Button dyBarDone = null;
    android.widget.Button dyBarCancel = null;
    TextView dyBarStatus = null;

    void showBiliLoginBar(){
        try{
            if(biliBarDone != null && biliBarDone.getParent() != null) return;
            // 同抖音：父容器类型不固定，不能硬转 FrameLayout
            android.view.ViewGroup host = (web == null) ? null
                    : (web.getParent() instanceof android.view.ViewGroup
                        ? (android.view.ViewGroup) web.getParent() : null);
            if(host == null) return;
            LinearLayout bar = new LinearLayout(this);
            bar.setOrientation(LinearLayout.HORIZONTAL);
            bar.setBackgroundColor(curDark ? 0xFF22262E : 0xFFFFFFFF);
            bar.setPadding(dp(12), dp(8), dp(8), dp(8));
            bar.setId(900002);

            biliBarStatus = new TextView(this);
            biliBarStatus.setTextSize(12);
            biliBarStatus.setTextColor(curDark ? 0xFFB8BEC9 : 0xFF4B5563);
            biliBarStatus.setText("等待登录…登录后自动抓 SESSDATA");
            LinearLayout.LayoutParams lp1 = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            bar.addView(biliBarStatus, lp1);

            biliBarDone = new android.widget.Button(this);
            biliBarDone.setText("完成");
            biliBarDone.setTextSize(13);
            biliBarDone.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){
                int n = CookieStore.count(MainActivity.this, CookieStore.PLATFORM_BILI);
                if(n <= 0){
                    showHintToast("还没抓到 Cookie，请先在页面里登录");
                    return;
                }
                showHintToast("B站 Cookie 已保存（" + n + " 项）");
                hideBiliLoginBar();
                stopBiliLogin();
                buildSettingsPage();
            }});
            bar.addView(biliBarDone);

            android.view.ViewGroup.LayoutParams flp;
            if(host instanceof android.widget.FrameLayout){
                android.widget.FrameLayout.LayoutParams p =
                    new android.widget.FrameLayout.LayoutParams(
                        android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                        android.widget.FrameLayout.LayoutParams.WRAP_CONTENT);
                p.gravity = android.view.Gravity.TOP;
                flp = p;
            } else if(host instanceof LinearLayout){
                flp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            } else {
                flp = new android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
            }
            host.addView(bar, flp);
            bar.bringToFront();
        }catch(Throwable t){ Log.i("DYP", "BILI_BAR_ERR " + t); }
    }

    void hideBiliLoginBar(){
        try{
            if(biliBarDone != null && biliBarDone.getParent() != null){
                ((android.view.ViewGroup) biliBarDone.getParent().getParent()).removeView(
                    (View) biliBarDone.getParent());
            }
        }catch(Throwable t){}
        biliBarStatus = null;
        biliBarDone = null;
    }

    void updateBiliLoginBar(){
        try{
            if(biliBarStatus == null) return;
            int n = CookieStore.count(this, CookieStore.PLATFORM_BILI);
            biliBarStatus.setText(n > 0
                ? "Cookie 已保存 " + n + " 项 · 好了点「完成」"
                : "等待登录…");
        }catch(Throwable t){}
    }

    /** 退出 B站 登录模式 */
    void stopBiliLogin(){
        biliLoginMode = false;
        hideBiliLoginBar();
        try{ if(pageParse != null) pageParse.setVisibility(View.VISIBLE); }catch(Throwable t){}
        try{
            web.setLayoutParams(new LinearLayout.LayoutParams(1, 1));
        }catch(Throwable t){}
        web.setVisibility(View.GONE);
    }

    /** 上传 Cookie 到服务器（POST /api/yt/cookie，HMAC 鉴权） */
    void uploadYtCookie(final String ck){
        if(ytCkUploading || ck == null || ck.length() < 16) return;
        ytCkUploading = true;
        new Thread(new Runnable(){ public void run(){
            HttpURLConnection c = null;
            try{
                String srv = effSrvS(MainActivity.this);
                if(srv.length() == 0){ ytCkUploading = false; return; }
                String body = "{\"uid\":\"" + ytUid(MainActivity.this) + "\",\"cookie\":\""
                        + ck.replace("\\", "\\\\").replace("\"", "\\\"") + "\"}";
                java.net.URL u = new java.net.URL(srv + "/api/yt/cookie");
                c = (HttpURLConnection) u.openConnection(java.net.Proxy.NO_PROXY);
                c.setRequestMethod("POST");
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 12; Pixel 5) AppleWebKit/537.36");
                // HMAC 鉴权（path=/api/yt/cookie）
                try{
                    String ts = String.valueOf(System.currentTimeMillis() / 1000);
                    String nonce = java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 12);
                    String path = "/api/yt/cookie";
                    String msg = path + "\n" + ts + "\n" + nonce;
                    String key = srvHmacKeyS(MainActivity.this);
                    if(key.length() > 0){
                        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
                        mac.init(new javax.crypto.spec.SecretKeySpec(key.getBytes("UTF-8"), "HmacSHA256"));
                        byte[] raw = mac.doFinal(msg.getBytes("UTF-8"));
                        StringBuilder sb = new StringBuilder();
                        for(byte b : raw) sb.append(String.format("%02x", b));
                        c.setRequestProperty("X-TS", ts);
                        c.setRequestProperty("X-NONCE", nonce);
                        c.setRequestProperty("X-SIGN", sb.toString());
                    }
                }catch(Throwable t){ Log.i("DYP", "YT_CK_SIGN_ERR " + t); }
                c.setConnectTimeout(15000);
                c.setReadTimeout(15000);
                java.io.OutputStream os = c.getOutputStream();
                os.write(body.getBytes("UTF-8"));
                os.close();
                int code = c.getResponseCode();
                Log.i("DYP", "YT_CK_UPLOAD http=" + code);
            }catch(Throwable t){ Log.i("DYP", "YT_CK_UPLOAD_ERR " + t); }
            finally{
                try{ if(c != null) c.disconnect(); }catch(Throwable t){}
                ytCkUploading = false;
            }
        }}).start();
    }

    /** YouTube 登录 WebView：加载 YouTube 首页让用户自己点「登录」（YouTube 域登录 Google 不拦） */
    void startYtLogin(){
        switchTab(0);
        setupWebView();
        ytLoginMode = true;
        // 登录页独占全屏
        try{
            pageParse.setVisibility(View.GONE);
            web.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        }catch(Throwable t){}
        web.setVisibility(View.VISIBLE);
        web.bringToFront();
        s("请点右上角 ⋮ → 登录");
        try{
            WebSettings ws = web.getSettings();
            if(android.os.Build.VERSION.SDK_INT >= 29){
                ws.setForceDark(WebSettings.FORCE_DARK_OFF);
            }
            if(android.os.Build.VERSION.SDK_INT >= 33){
                ws.setAlgorithmicDarkeningAllowed(false);
            }
            // 安卓 UA——YouTube 域登录不拦，accounts.google.com 才拦
            ws.setUserAgentString(
                "Mozilla/5.0 (Linux; Android 12; Pixel 5) AppleWebKit/537.36 "
                + "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36");
        }catch(Throwable t){}
        // 直接进 YouTube 首页，不跳 Google 登录页
        web.loadUrl("https://m.youtube.com/");
    }

    /** 退出 YouTube 登录模式（恢复 UA 与暗色策略） */
    void stopYtLogin(){
        ytLoginMode = false;
        try{
            if(web != null && webReady){
                WebSettings ws = web.getSettings();
                ws.setUserAgentString(
                    "Mozilla/5.0 (Linux; Android 12; Pixel 5) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36");
                if(android.os.Build.VERSION.SDK_INT >= 29){
                    ws.setForceDark(WebSettings.FORCE_DARK_AUTO);
                }
                if(android.os.Build.VERSION.SDK_INT >= 33){
                    ws.setAlgorithmicDarkeningAllowed(true);
                }
            }
            // 恢复解析页 + 缩回 web 占位
            if(pageParse != null) pageParse.setVisibility(View.VISIBLE);
            web.setLayoutParams(new LinearLayout.LayoutParams(1, 1));
        }catch(Throwable t){}
        web.setVisibility(View.GONE);
    }

    /** YouTube 登录抓到 cookie 后：拆成一行一个写本地文件（服务器到期也能用） */
    void saveYtCookieLocal(String full){
        try{
            int n = CookieStore.save(MainActivity.this, CookieStore.PLATFORM_YT, full);
            Log.i("DYP", "YT_CK_LOCAL n=" + n + " file="
                    + CookieStore.filePath(MainActivity.this, CookieStore.PLATFORM_YT));
        }catch(Throwable t){ Log.i("DYP", "YT_CK_LOCAL_ERR " + t); }
    }

    /** 本地是否已有 YouTube Cookie */
    String ytCookieLocal(){
        try{
            String c = getSharedPreferences(SP, MODE_PRIVATE).getString(KEY_YT_COOKIE, "");
            return c != null && c.length() >= 16 ? c : "";
        }catch(Throwable t){ return ""; }
    }

    /** 读代理配置：仅一个开关——内置代理（= 服务器中转）。
     *  开了=海外平台走服务器中转，关了=全部直连（用户自己挂的系统代理/TUN 自然生效，App 不干预）。 */
    Proxy curProxy(){
        return curProxyFor(null);
    }

    /** 内置代理是否开启 */
    static boolean builtinProxyOn(Context ctx){
        try{
            SharedPreferences p = ctx.getSharedPreferences(SP, MODE_PRIVATE);
            return p.getBoolean(KEY_PROXY_ON, false);
        }catch(Throwable t){ return false; }
    }

    Proxy curProxyFor(String url){
        return null; // 不再往用户流量上套代理；内置中转由各解析器自己决定走不走
    }

    /** 静态版代理读取：内置代理（服务器中转）开关。 */
    static Proxy staticProxy(Context ctx){
        return staticProxyFor(ctx, null);
    }

    static Proxy staticProxyFor(Context ctx, String url){
        return null; // 不再往用户流量上套代理
    }

    /** 静态版：按需套代理（内置服务器走 SrvClient pin + HMAC） */
    static HttpURLConnection openConn(Context ctx, String url) throws Exception{
        Proxy px = staticProxyFor(ctx, url);
        if(SrvClient.isBuiltin(url)){
            HttpURLConnection c = SrvClient.open(url, px);
            SrvClient.sign(c, url);
            return c;
        }
        URL u = new URL(url);
        return (HttpURLConnection)(px != null ? u.openConnection(px) : u.openConnection());
    }

    /** 强制把 host 解析成 IPv4（绕开设备 IPv6-only 导致的 UnknownHost） */
    static String ipv4Host(String host){
        try{
            java.net.InetAddress[] all = java.net.InetAddress.getAllByName(host);
            for(java.net.InetAddress a : all){
                if(a instanceof java.net.Inet4Address) return a.getHostAddress();
            }
        }catch(Throwable t){}
        return null;
    }

    /** 用 IPv4 重写 URL（仅当原 host 解析失败时使用） */
    static String forceIPv4(String url){
        try{
            URL u = new URL(url);
            String h = u.getHost();
            // 已经是 IP 就不动
            if(h.matches("^[0-9.]+$")) return url;
            String ip = ipv4Host(h);
            if(ip == null) return url;
            String port = u.getPort() > 0 ? (":" + u.getPort()) : "";
            String auth = u.getUserInfo() != null ? (u.getUserInfo() + "@") : "";
            return u.getProtocol() + "://" + auth + ip + port + u.getFile();
        }catch(Throwable t){ return url; }
    }

    /** 带 IPv4 回退的连接：先正常开，DNS 失败就用 IPv4 重试 */
    HttpURLConnection openConnRetry(String url) throws Exception{
        try{
            return openConn(url);
        }catch(java.net.UnknownHostException e){
            Log.i("DYP", "IPV4_FALLBACK " + url.substring(0, Math.min(80, url.length())));
            Proxy px = curProxyFor(url);
            URL u2 = new URL(url);
            String h = u2.getHost();
            String ip = ipv4Host(h);
            if(ip == null) throw e;
            String newUrl = forceIPv4(url);
            URL u3 = new URL(newUrl);
            HttpURLConnection c = (HttpURLConnection)(px != null ? u3.openConnection(px) : u3.openConnection());
            return c;
        }
    }

    /** 统一入口：按需套代理打开连接（按「仅 TikTok」开关过滤）。
     *  内置服务器 URL 走 SrvClient（TLS pin + HMAC 鉴权）。 */
    HttpURLConnection openConn(String url) throws Exception{
        Proxy px = curProxyFor(url);
        HttpURLConnection c;
        if(SrvClient.isBuiltin(url)){
            c = SrvClient.open(url, px);
            SrvClient.sign(c, url);
        } else {
            URL u = new URL(url);
            c = (HttpURLConnection)(px != null ? u.openConnection(px) : u.openConnection());
        }
        return c;
    }
    static final int C_LIGHT_BG      = 0xFFF5F6F8;
    static final int C_LIGHT_SURFACE = 0xFFFFFFFF;
    static final int C_LIGHT_ALT     = 0xFFF0F1F4;
    static final int C_LIGHT_STROKE  = 0xFFE3E5EA;
    static final int C_LIGHT_TEXT    = 0xFF1A1A1F;
    static final int C_LIGHT_SUB     = 0xFF6B6F7A;
    static final int C_LIGHT_HINT    = 0xFFA8ACB6;
    static final int C_DARK_BG       = 0xFF0E0F13;
    static final int C_DARK_SURFACE  = 0xFF191B21;
    static final int C_DARK_ALT      = 0xFF21242C;
    static final int C_DARK_STROKE   = 0xFF2B2F39;
    static final int C_DARK_TEXT     = 0xFFF2F3F5;
    static final int C_DARK_SUB      = 0xFF9BA1AE;
    static final int C_DARK_HINT     = 0xFF63697A;
    static final String APP_QQ = "2137190676";
    /** 反馈 QQ 群号 */
    static final String APP_QQ_GROUP = "995434160";
    /** 加群链接（跳转浏览器打开） */
    static final String APP_QQ_GROUP_URL = "https://qm.qq.com/q/MA71T24xO0";
    // ===== 双仓库：Gitee + GitHub（用户可在设置里切换更新源） =====
    static final String SRC_GITEE  = "gitee";
    static final String SRC_GITHUB = "github";
    /** 默认更新源；实际以 prefs 里的 KEY_UPD_SRC 为准 */
    static final String UPD_SOURCE = SRC_GITEE;
    static final String KEY_UPD_SRC = "upd_src";
    // Gitee 仓库
    static final String GITEE_OWNER = "adaaixianzun";
    static final String GITEE_REPO  = "dyparse";
    static final String GITEE_BRANCH = "master";
    // GitHub 仓库
    static final String GH_OWNER = "a2137190676";
    static final String GH_REPO  = "DYParse";
    static final String GH_BRANCH = "main";
    /** 静态版本文件（按源切换） */
    static final String UPD_STATIC_GITEE  = "https://gitee.com/" + GITEE_OWNER + "/" + GITEE_REPO
                                          + "/raw/" + GITEE_BRANCH + "/version.json";
    static final String UPD_STATIC_GITHUB = "https://raw.githubusercontent.com/" + GH_OWNER + "/" + GH_REPO
                                          + "/" + GH_BRANCH + "/version.json";

    // ===== GitHub 访问方式（国内直连 github.com / api.github.com / raw 基本超时）=====
    /** gh 直连 */
    static final String GH_MODE_DIRECT = "direct";
    /** gh 走镜像加速（国内可用） */
    static final String GH_MODE_MIRROR = "mirror";
    static final String KEY_GH_MODE = "gh_mode";
    /**
     * GitHub 镜像前缀列表：按顺序尝试，第一个成功就用。
     * 说明：这些都是社区公开的 GitHub 反代，只用于「取版本号 JSON / Release 信息 / 下载 APK」，
     * 不经过任何私密数据；全挂时会自动回退直连，不会因为镜像失效导致无法更新。
     */
    static final String[] GH_MIRRORS = {
        "https://ghfast.top/",          // 目前国内较稳
        "https://gh-proxy.com/",
        "https://ghproxy.net/",
        "https://mirror.ghproxy.com/"
    };
    Bundle savedState;

    java.util.List<String> imgUrls=new java.util.ArrayList<String>();
    /** 实况照片：与 imgUrls 一一对应的短视频地址，空串表示该图不是实况 */
    java.util.List<String> dynUrls=new java.util.ArrayList<String>();
    /** 所有图片都是实况（下载时逐张封装成 .jpg 实况照片） */
    boolean imgAllLive = false;
    int imgIdx=0; View scrollOut;
    boolean imgZoomed=false; float imgDownX=0, imgDownY=0;
    /** 渲染代次：每次 parse() 递增，render() 里只允许最新代次改界面 */
    volatile int renderGen = 0;
    /** detail 接口已成功渲染：标记后忽略 WebView SSR 回包（SSR 没有实况动轨） */
    volatile boolean detailRendered = false;
    android.widget.MediaController mc;
    volatile boolean shown=false;
    Handler h = new Handler(Looper.getMainLooper());
    volatile boolean webDone = false;
    volatile boolean storyBlocked = false;
    static final String HOOK_JS =
     "(function(){try{if(window.__dyHooked)return;window.__dyHooked=1;window.__dyCaptured=[];"
     +"function rec(u,b){try{if(u&&b&&(b.indexOf('aweme_detail')>=0||b.indexOf('slidesinfo')>=0||b.indexOf('aweme_list')>=0||b.indexOf('mix_id')>=0)&&b.length>200){window.__dyCaptured.push({u:u,b:b});}}catch(e){}}"
     +"var of=window.fetch;if(of){window.fetch=function(){var a=arguments;var url=(typeof a[0]==='string')?a[0]:(a[0]&&a[0].url);var pr=of.apply(this,a);try{pr.then(function(r){try{r.clone().text().then(function(t){rec(url,t);});}catch(e){}});}catch(e){}return pr;};}"
     +"var oo=XMLHttpRequest.prototype.open,os=XMLHttpRequest.prototype.send;"
     +"XMLHttpRequest.prototype.open=function(m,u){this.__du=u;return oo.apply(this,arguments);};"
     +"XMLHttpRequest.prototype.send=function(){var x=this;try{x.addEventListener('load',function(){try{rec(x.__du,x.responseText);}catch(e){}});}catch(e){}return os.apply(this,arguments);};"
     +"}catch(e){}})();";

    protected void onCreate(Bundle b){
        super.onCreate(b);
        savedState = b;
        // 日志系统：先初始化，后面所有失败信息才有地方落盘
        DiagLog.init(this);
        AppInfo.init(this);
        installCrashHandler();
        // 把本地保存的各平台 Cookie 灌回 WebView（不覆盖已有的实时 Cookie）
        try{ restoreDyCookieToWebView(); }catch(Throwable t){}
        DiagLog.i(DiagLog.M_APP, "=== App 启动 === " + AppInfo.versionLine() + " | " + AppInfo.deviceLine());
        Log.i("DYP", "SRV_DEBUG addr=[" + SrvSecret.serverAddr() + "] hmacLen=" + SrvSecret.hmacKey().length());
        appLog("启动", "v" + versionName() + " (build " + curVersionCode() + ") 内置服务器=" + (SrvSecret.serverAddr().length() > 0 ? "ON" : "OFF"));
        setContentView(R.layout.activity_main);
        // 列表内容恢复：批量解析结果退出重进仍在（受「保留解析列表」开关控制）
        h.postDelayed(new Runnable(){ public void run(){ loadBatchList(); }}, 600);
        // 启动动画：盖在内容之上，620ms 后自动移除；不影响解析流程
        if(b == null){
            try{ SplashOverlay.show(this, isDarkMode(), null); }catch(Throwable t){}
        }
        // 免责声明：首次启动必须同意（5 秒后可点），不同意直接退出
        if(b == null && !getSharedPreferences(SP, MODE_PRIVATE).getBoolean(KEY_AGREE, false)){
            showAgreementDialog();
        }
        input=(EditText)findViewById(R.id.input);
        btn=(TextView)findViewById(R.id.btn);
        status=(TextView)findViewById(R.id.status);
        out=(LinearLayout)findViewById(R.id.out);
        // WebView 改为懒加载：抖音解析时才初始化，避免拖慢冷启动
        web=(WebView)findViewById(R.id.web);
        actions=(LinearLayout)findViewById(R.id.actions);
        // 双页签：解析 / 历史
        setupTabs();
        btnPlay=(TextView)findViewById(R.id.btnPlay);
        btnDl=(TextView)findViewById(R.id.btnDl);
        btnFull=(TextView)findViewById(R.id.btnFull);
        btnAudio=(TextView)findViewById(R.id.btnAudio);
        btnAudio.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ extractAudio(); }});
        btnMusic=(TextView)findViewById(R.id.btnMusic);
        btnMusic.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ downloadBgm(); }});
        progressBox=(LinearLayout)findViewById(R.id.progressBox);
        progressLabel=(TextView)findViewById(R.id.progressLabel);
        progressPct=(TextView)findViewById(R.id.progressPct);
        progressBar=(ProgressBar)findViewById(R.id.progressBar);
        video=(android.view.TextureView)findViewById(R.id.video);
        // 右上角「设置」按钮已移除：设置走「设置」页签（switchTab(3) 仍在 tab 栏）
        btnTheme=(TextView)findViewById(R.id.btnTheme);
        if(btnTheme != null){
            btnTheme.setText(isDarkMode() ? "亮" : "暗");
            btnTheme.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){
                boolean d = !isDarkMode();
                applyTheme(d);
                btnTheme.setText(d ? "亮" : "暗");
                buildSettingsPage();
                Toast.makeText(MainActivity.this, d ? "已切换夜间" : "已切换日间", Toast.LENGTH_SHORT).show();
            }});
        }
        applyTheme(isDarkMode());
        // 启动自动检测更新（静默，仅首次启动）
        if(savedState == null){
            h.postDelayed(new Runnable(){ public void run(){ checkUpdate(true); } }, 1500);
        }
        videoBox=findViewById(R.id.videoBox);
        miniBar=findViewById(R.id.miniBar);
        miniTimeNow=(TextView)findViewById(R.id.miniTimeNow);
        miniTimeAll=(TextView)findViewById(R.id.miniTimeAll);
        miniPlay=(TextView)findViewById(R.id.miniPlay);
        miniSpeed=(TextView)findViewById(R.id.miniSpeed);
        miniFull=(TextView)findViewById(R.id.miniFull);
        miniSeek=(SeekBar)findViewById(R.id.miniSeek);
        img=(ImageView)findViewById(R.id.img);
        liveBadge=(TextView)findViewById(R.id.liveBadge);
        // 注意：WebView 的 WebSettings / WebViewClient / 注入脚本
        //       统一在 setupWebView() 里按需初始化，不在 onCreate 里跑，
        //       这样冷启动不必等 WebView 加载整个 Chromium 内核。
        setupUiEarly();
    }

    /** WebView 是否已初始化 */
    boolean webReady = false;

    /**
     * 懒初始化 WebView（抖音解析需要真实浏览器算签名）。
     * 只在真正要解析抖音时才调用，避免冷启动被 Chromium 拖慢。
     */
    void setupWebView(){
        if(webReady || web == null) return;
        webReady = true;
        WebSettings ws=web.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setDatabaseEnabled(true);
        // 性能相关：关掉不需要的能力，减少首屏渲染负担
        try{
            ws.setCacheMode(WebSettings.LOAD_DEFAULT);
            ws.setLoadsImagesAutomatically(true);
            ws.setBlockNetworkImage(false);
            ws.setSupportZoom(false);
            ws.setBuiltInZoomControls(false);
            ws.setDisplayZoomControls(false);
            ws.setMediaPlaybackRequiresUserGesture(true);
            ws.setAllowFileAccess(false);
            ws.setAllowContentAccess(false);
            ws.setGeolocationEnabled(false);
            ws.setSaveFormData(false);
            // 混合内容：抖音/TikTok 页面有 http 资源
            if(android.os.Build.VERSION.SDK_INT >= 21){
                ws.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
            }
        }catch(Throwable t){}
        ws.setUserAgentString("Mozilla/5.0 (Linux; Android 12; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36");
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);
        try{
            String ck = CookieManager.getInstance().getCookie("https://www.douyin.com/");
            StringBuilder names = new StringBuilder();
            if(ck != null){
                for(String kv : ck.split(";")){
                    int e2 = kv.indexOf('=');
                    if(e2 > 0) names.append(kv.substring(0, e2).trim()).append(",");
                }
            }
            Log.i("DYP", "COOKIES_AT_SETUP len=" + (ck == null ? -1 : ck.length()) + " names=" + names);
        }catch(Throwable t){ Log.i("DYP", "COOKIES_ERR " + t); }
        web.setWebViewClient(new WebViewClient(){
            int pageHits=0;
            public void onPageStarted(WebView v,String u,android.graphics.Bitmap f){
                v.evaluateJavascript(HOOK_JS,null);
                // 抖音主页登录模式：PC 版才注入桌面伪装（移动版不需要，注入了反而可能出问题）
                if(dyProfileLoginMode && !dyMobileLogin && u != null && u.indexOf("douyin.com") >= 0){
                    injectDesktopSpoof(v);
                }
                // 抖音主页解析模式：已跳到 PC 主页时也要伪装，否则前端检测到 Android 会退回手机版
                if(profileMode && dyProfilePcUA && u != null && u.indexOf("douyin.com") >= 0){
                    injectDesktopSpoof(v);
                }
                // 【临时探测】抖音首页加载后，把 cookie 里是否有 UIFID_TEMP 打出来
                if(u != null && u.contains("douyin.com") && u.indexOf("/video/") < 0
                        && u.indexOf("/note/") < 0 && u.indexOf("share/") < 0){
                    v.evaluateJavascript(
                        "(function(){try{var c=document.cookie||'';"
                        +"return 'DCK len='+c.length+' hasUIFID='+(c.indexOf('UIFID')>=0?1:0)"
                        +"+' hasTtwid='+(c.indexOf('ttwid')>=0?1:0);}catch(e){return 'DCK_ERR '+e;}})()",
                        new android.webkit.ValueCallback<String>(){ public void onReceiveValue(String r){
                            Log.i("DYP","WS_PROBE_JS "+r);
                            // CookieManager 能拿到 HttpOnly（UIFID_TEMP 就是 HttpOnly）
                            try{
                                String cm = CookieManager.getInstance().getCookie("https://www.douyin.com/");
                                StringBuilder nm = new StringBuilder();
                                String uifidVal = "";
                                if(cm != null){
                                    for(String kv : cm.split(";")){
                                        int e2 = kv.indexOf('=');
                                        if(e2 <= 0) continue;
                                        String k = kv.substring(0, e2).trim();
                                        nm.append(k).append(",");
                                        if(k.equals("UIFID_TEMP") || k.equals("UIFID") || k.equals("uifid")){
                                            uifidVal = kv.substring(e2 + 1).trim();
                                        }
                                    }
                                }
                                Log.i("DYP","WS_PROBE_CM len=" + (cm == null ? -1 : cm.length())
                                    + " uifidLen=" + uifidVal.length() + " names=" + nm);
                            }catch(Throwable t3){ Log.i("DYP","WS_PROBE_CM_ERR " + t3); }
                        }});
                }
                if(profileMode){
                    v.evaluateJavascript(PROFILE_HOOK_JS,null);
                }
                // YouTube 页面解析：注入 JS 等 <video> 标签出现后取 src（Shorts 页 video 有完整直链）
                if(curUrl != null && YtParser.isYtUrl(curUrl) && !ytLoginMode && u != null && u.contains("youtube.com")){
                    v.evaluateJavascript(
                        "(function(){try{window.__ytProbeDone=0;window.__ytProbeInt=setInterval(function(){"
                        +"if(window.__ytProbeDone)return;"
                        +"var v=document.querySelector('video');if(!v||!v.src||v.src.indexOf('blob:')>=0||v.src.indexOf(window.__ytLastSrc||'')>=0)return;"
                        +"window.__ytProbeDone=1;window.__ytLastSrc=v.src;clearInterval(window.__ytProbeInt);"
                        +"var t='';try{t=document.title||'';}catch(e){}"
                        +"window.DyBridge.onYtData(v.src+'@@YT@@'+'@@YT@@'+t+'@@YT@@'+'@@YT@@0@@YT@@');"
                        +"},800);}catch(e){}})()", null);
                }
            }
            public boolean shouldOverrideUrlLoading(WebView v,String u){
                if(u==null) return false;
                // 抖音主页模式：阻止被打回手机版名片页（m.douyin.com/share/user），
                // 否则永远留在分享页 API 上，只能拿到一页数据
                if(profileMode && dyProfilePcUA && u != null
                        && u.indexOf("m.douyin.com") >= 0 && u.indexOf("/share/user/") >= 0){
                    if(!dyBlockedMobileShare){
                        dyBlockedMobileShare = true;
                        Log.i("DYP", "DYPROF_BLOCK_M " + u.substring(0, Math.min(90, u.length())));
                        DiagLog.i(DiagLog.M_PARSE, "阻止抖音把主页打回手机版，保持在 PC 主页");
                    }
                    return true;
                }
                // 抖音主页/登录模式：拦住所有唤起抖音 App 的 intent（snssdk / bytedance / aweme scheme）
                // 否则抖音 App 会被拉到前台，WebView 里的页面被切走，翻页直接断掉
                if((profileMode || dyProfileLoginMode) && u != null && (
                        u.startsWith("snssdk") || u.startsWith("aweme") || u.startsWith("bytedance")
                        || u.startsWith("intent:") || u.indexOf("snssdk1128") >= 0
                        || u.indexOf("openapp.jdmobile") >= 0 || u.indexOf("package=com.ss.android.ugc.aweme") >= 0)){
                    Log.i("DYP", "DYPROF_BLOCK_APP " + u.substring(0, Math.min(80, u.length())));
                    return true;
                }
                if(u.startsWith("http://")||u.startsWith("https://")) return false;
                Log.i("DYP","BLOCK_SCHEME "+u);
                return true;
            }
            public boolean shouldOverrideUrlLoading(WebView v,android.webkit.WebResourceRequest r){
                return shouldOverrideUrlLoading(v, r.getUrl().toString());
            }
            // native 层拦截：抓 slidesinfo 等接口响应（不依赖 JS 引擎，比 hook 可靠）
            public android.webkit.WebResourceResponse shouldInterceptRequest(WebView v, android.webkit.WebResourceRequest req){
                try{
                    final String u = req.getUrl().toString();
                    // 抖音登录/探测模式：从请求头抓完整 Cookie（含 HttpOnly 的 UIFID_TEMP）
                    if(dyLoginMode && u != null && u.contains("douyin.com")){
                        try{
                            java.util.Map<String,String> hh = req.getRequestHeaders();
                            String ckH = hh != null ? hh.get("Cookie") : null;
                            if(ckH != null && ckH.length() > 0){
                                boolean hasU = ckH.contains("UIFID");
                                Log.i("DYP","DY_HDR uifid=" + (hasU?1:0) + " len=" + ckH.length()
                                    + " url=" + u.substring(0, Math.min(70, u.length())));
                                if(hasU){
                                    java.util.regex.Matcher mm2 = java.util.regex.Pattern
                                        .compile("UIFID_TEMP=([^;]+)|UIFID=([^;]+)|uifid=([^;]+)")
                                        .matcher(ckH);
                                    if(mm2.find()){
                                        String ufVal = mm2.group(1) != null ? mm2.group(1)
                                                 : (mm2.group(2) != null ? mm2.group(2) : mm2.group(3));
                                        dyProbe = ufVal;
                                        Log.i("DYP","DY_UIFID_HIT len=" + (ufVal==null?0:ufVal.length()));
                                    }
                                }
                            }
                        }catch(Throwable t4){}
                    }
                    // YouTube 登录模式：抓请求头里的完整 Cookie（含 HttpOnly）
                    if(ytLoginMode && u != null){
                        try{ maybeGrabYtCookie(u, req.getRequestHeaders()); }catch(Throwable t2){}
                    }
                    // Instagram 登录模式：抓请求头里的完整 Cookie
                    if(insLoginMode && u != null){
                        try{ maybeGrabInsCookie(u, req.getRequestHeaders()); }catch(Throwable t2){}
                    }
                    // B站 登录模式：抓请求头里的完整 Cookie（SESSDATA 是 HttpOnly）
                    if(biliLoginMode && u != null){
                        try{ maybeGrabBiliCookie(u, req.getRequestHeaders()); }catch(Throwable t2){}
                    }
                    // 调试：记录所有 XHR/fetch 请求，用于发现合集接口
                    String ac = req.getRequestHeaders() != null ? req.getRequestHeaders().get("X-Requested-With") : null;
                    String acc = req.getRequestHeaders() != null ? req.getRequestHeaders().get("Accept") : null;
                    if(u != null && (u.indexOf("aweme") >= 0 || u.indexOf("mix") >= 0 || u.indexOf("collection") >= 0
                            || (acc != null && acc.indexOf("json") >= 0) || ac != null)){
                        Log.i("DYP", "REQ " + u.substring(0, Math.min(260, u.length())));
                    }
// 合集/短剧列表接口：拿到首页 URL 后由 fetchAllMixPages 自动翻页抓全
                     boolean isMix = u != null && u.indexOf("/aweme/v1/mix/aweme/") >= 0;
                     boolean isPlaylet = u != null && u.indexOf("/web/api/playlet/item/list/") >= 0;
if(isMix || isPlaylet){
                          if(!mixFetching){
                              mixFetching = true;
                              final String firstUrl = u;
                              final boolean playlet = isPlaylet;
                              Log.i("DYP", (playlet ? "PLAYLET_HIT " : "MIX_HIT ") + u.substring(0, Math.min(200, u.length())));
                              final String kind = playlet ? "短剧" : "合集";
                              // 合集/短剧会抓全量条目并清空列表：先确认一次
                              if(!mixFetchConfirmed){
                                  mixFetchConfirmed = true;
                                  h.post(new Runnable(){ public void run(){
                                      Log.i("DYP", "MIX_CONFIRM 弹出确认 kind=" + kind);
                                      confirmBigParse(kind, new Runnable(){ public void run(){
                                          new Thread(new Runnable(){ public void run(){
                                              try{ fetchAllMixPages(firstUrl); }
                                              catch(Throwable t){ Log.i("DYP", "MIX_ALL_ERR " + t); mixFetching = false; }
                                          }}).start();
                                      }}, new Runnable(){ public void run(){
                                          Log.i("DYP", "MIX_CANCEL 用户取消" + kind + "解析");
                                          mixFetching = false;
                                          s("已取消" + kind + "解析");
                                          showHintToast("已取消" + kind + "解析");
                                      }});
                                  }});
                              }else{
                                  new Thread(new Runnable(){ public void run(){
                                      try{
                                          fetchAllMixPages(firstUrl);
                                      }catch(Throwable t){
                                          Log.i("DYP", "MIX_ALL_ERR " + t);
                                          mixFetching = false;
                                      }
                                  }}).start();
                              }
                          }
                      }
                    // 主页列表接口：native 层拦截重发（比 JS hook 可靠，response 是 JSON）
boolean isProfileList = u != null && profileMode && (
                              u.indexOf("/aweme/v1/web/aweme/post/") >= 0
                          || u.indexOf("/web/api/v2/aweme/post/") >= 0
                          || u.indexOf("/aweme/post/") >= 0
                          || (u.indexOf("graphql") >= 0 && u.indexOf("kuaishou") >= 0)
                          || u.indexOf("/x/space/wbi/arc/search") >= 0
                          || u.indexOf("youtube.com/youtubei/v1/browse") >= 0
                          || (u.indexOf("instagram.com") >= 0 && u.indexOf("graphql") >= 0)
                          || (u.indexOf("instagram.com") >= 0 && u.indexOf("/api/v1/feed/user/") >= 0)
                          || (u.indexOf("instagram.com") >= 0 && u.indexOf("/api/v1/users/") >= 0));
if(isProfileList && !profileFetching){
                           // 确认框还在等用户点：这期间不要真正去抓主页列表，
                           // 否则用户还没点「继续解析」页面就已经把单页 20 条结果渲染出来了
                           if(bigParsePending || !profileAutoConfirmed){
                               Log.i("DYP", "PROFILE_LIST_SKIP 未确认主页解析，先不抓");
                               return null;
                           }
                           // 同一个 sec_uid 只翻一次：页面会反复发 aweme/post（首屏 + 预加载 + 重试），
                          // 每来一条就起一个翻页任务，多个任务并发会互相覆盖/截断，表现为「只出 20 条」
                          String secNow = extractSecUid(u);
                          if(secNow != null && secNow.length() > 10
                                  && secNow.equals(dyProfileDoneSecUid)){
                              Log.i("DYP", "DYPROF_SKIP_DUP sec_uid 已翻完，忽略重复请求");
                              return null;
                          }
                          profileFetching = true;
                          final String lu = u;
                          Log.i("DYP", "PROFILE_LIST_HIT " + u.substring(0, Math.min(200, u.length())));
                          // 诊断：WebView 自己发的抖音主页请求，把 cursor 和是否有 msToken/uifid 打出来
                          if(u.indexOf("aweme/post") >= 0 || u.indexOf("/aweme/v1/web/aweme/post/") >= 0){
                              String ck2 = CookieManager.getInstance().getCookie("https://www.douyin.com/");
                              Log.i("DYP", "DYPROF_WEBREQ hasMsToken=" + (u.indexOf("msToken=") >= 0 ? 1 : 0)
                                  + " hasUifid=" + (u.indexOf("uifid=") >= 0 ? 1 : 0)
                                  + " hasABogus=" + (u.indexOf("a_bogus=") >= 0 ? 1 : 0)
                                  + " ckUifid=" + (ck2 != null && ck2.indexOf("UIFID") >= 0 ? 1 : 0));
                              // 完整参数名列表 + count/max_cursor 实际值，用于和自建请求对比
                              try{
                                  int qq = u.indexOf('?');
                                  if(qq > 0){
                                      StringBuilder names = new StringBuilder();
                                      String cursorVal = "?", countVal = "?";
                                      for(String kv : u.substring(qq + 1).split("&")){
                                          int ee = kv.indexOf('=');
                                          String k = ee > 0 ? kv.substring(0, ee) : kv;
                                          if(names.length() > 0) names.append(',');
                                          names.append(k);
                                          if(k.equals("max_cursor")) cursorVal = kv.substring(ee + 1);
                                          if(k.equals("count")) countVal = kv.substring(ee + 1);
                                      }
                                      Log.i("DYP", "DYPROF_WEBKEYS cursor=" + cursorVal + " count=" + countVal
                                          + " keys=" + names);
                                  }
                              }catch(Throwable t){}
                          }
                          new Thread(new Runnable(){ public void run(){
                              try{
                                  // 抖音主页：交回 WebView 自己翻页（PROFILE_HOOK_JS 会 hook fetch/XHR + 自动滚动），
                                  // 这里不再重发请求——重发只会拿到第一页（a_bogus 签名含 cursor，匿名翻页被服务端掐掉）
                                  boolean isDy = lu.indexOf("/aweme/post/") >= 0 && lu.indexOf("kuaishou") < 0;
                                  if(isDy){
                                      // 抖音主页：签名接口翻页（带 uifid + 登录 Cookie）
                                      Log.i("DYP", "DYPROF_USE_SIGNED 先用签名接口翻页");
                                      fetchAllDyProfilePages(lu);
                                  } else {
                                      String ck = CookieManager.getInstance().getCookie(lu);
                                      java.net.HttpURLConnection c = MainActivity.openConn(MainActivity.this, lu);
                                      c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 12; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36");
                                      c.setRequestProperty("Referer", lu.contains("kuaishou") ? "https://live.kuaishou.com/"
                                              : lu.contains("bilibili") ? "https://space.bilibili.com/" : "https://www.iesdouyin.com/");
                                      c.setRequestProperty("Accept", "application/json,*/*");
                                      if(ck != null) c.setRequestProperty("Cookie", ck);
                                      c.setConnectTimeout(12000); c.setReadTimeout(15000);
                                      java.io.InputStream in = c.getInputStream();
                                      java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
                                      byte[] buf = new byte[8192]; int n;
                                      while((n = in.read(buf)) > 0) bo.write(buf, 0, n);
                                      in.close();
                                      String body = new String(bo.toByteArray(), "UTF-8");
                                      Log.i("DYP", "PROFILE_BODY len=" + body.length());
                                      if(body.length() > 100){
                                          parseProfileBody(body);
                                      }
                                  }
                              }catch(Throwable t){ Log.i("DYP", "PROFILE_REQ_ERR " + t); }
                              profileFetching = false;
                          }}).start();
                      }
                     if(u != null && u.indexOf("slidesinfo") >= 0){
                        Log.i("DYP", "INTERCEPT_HIT " + u.substring(0, Math.min(180, u.length())));
                        // 用同一份 Cookie 自己发一次，拿到 body
                        new Thread(new Runnable(){ public void run(){
                            try{
                                String ck = CookieManager.getInstance().getCookie(u);
                                java.net.HttpURLConnection c = MainActivity.openConn(MainActivity.this, u);
                                c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 12; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36");
                                c.setRequestProperty("Referer", "https://www.iesdouyin.com/");
                                if(ck != null) c.setRequestProperty("Cookie", ck);
                                c.setConnectTimeout(12000); c.setReadTimeout(15000);
                                java.io.InputStream in = c.getInputStream();
                                java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
                                byte[] buf = new byte[8192]; int n;
                                while((n = in.read(buf)) > 0) bo.write(buf, 0, n);
                                in.close();
                                String body = new String(bo.toByteArray(), "UTF-8");
                                Log.i("DYP", "INTERCEPT_BODY len=" + body.length());
                                if(body.length() > 50 && (body.indexOf("aweme_list") >= 0 || body.indexOf("aweme_details") >= 0)){
                                    final String fin = body;
                                    h.post(new Runnable(){ public void run(){ handleSlidesJson(fin); }});
                                }
                            }catch(Throwable t){ Log.i("DYP", "INTERCEPT_ERR " + t); }
                        }}).start();
                    }
                    // 主页条目下载的 detail 请求：只记日志，不 native 重发。
                    // 签名（a_bogus/msToken）绑定 WebView 浏览器环境，native 重发必 404；
                    // 直链数据走 JS fetch 回调（onDetailData）。
                    if(u != null && u.indexOf("/aweme/v1/web/aweme/detail/") >= 0 && pendingDetailId.length() > 0){
                        Log.i("DYP", "DETAIL_NATIVE_HIT " + u.substring(0, Math.min(160, u.length())));
                    }
                }catch(Throwable t){}
                return null;
            }
            public void onPageFinished(WebView v,String u){
                pageHits++;
                Log.i("DYP","onPageFinished#"+pageHits+" "+u);
                // 抖音主页登录模式：每页加载完就尝试把登录 Cookie 落到本地；抓到就自动退出回解析页
                if(dyProfileLoginMode){
                    if(u != null && u.indexOf("douyin.com") >= 0){
                        Log.i("DYP","DY_PROFILE_LOGIN_PAGE " + u.substring(0, Math.min(80, u.length())));
                        final boolean got = saveDyCookieLocal(null);
                        if(got){
                            // 和 YouTube 一样：抓到 Cookie 就直接回解析页，不保留网页
                            DiagLog.i(DiagLog.M_PARSE, "抖音登录成功，自动回到解析页");
                            h.postDelayed(new Runnable(){ public void run(){
                                hideDyLoginBar();
                                stopDyProfileLogin();
                            }}, 800);
                        }
                    }
                    return;
                }
                // B站 登录模式：检测登录态，不跑抖音解析逻辑
                if(biliLoginMode){
                    if(u != null && (u.contains("bilibili.com") || u.contains("bilivideo.com"))){
                        v.evaluateJavascript(
                            "(function(){try{var ck=document.cookie||'';"
                            +"var hasSj=ck.indexOf('DedeUserID')>=0||ck.indexOf('bili_jct')>=0;"
                            +"var hasAv=!!document.querySelector('a[href*=\\'/space/\\'],.header-entry-mini,.bili-avatar');"
                            +"return ck.length+'|'+(hasSj?1:0)+'|'+(hasAv?1:0);}catch(e){return '0|0|0';}})()",
                            new android.webkit.ValueCallback<String>(){ public void onReceiveValue(String r){
                                Log.i("DYP","BILI_LOGIN_PROBE "+r);
                                try{
                                    String[] p = r.replace("\"","").split("\\|");
                                    if(p.length >= 3 && (p[1].equals("1") || p[2].equals("1"))){
                                        final WebView vFinal = v;
                                        h.post(new Runnable(){ public void run(){
                                            final StringBuilder cm = new StringBuilder();
                                            try{ cm.append(CookieManager.getInstance().getCookie("https://www.bilibili.com/")); }catch(Throwable t){}
                                            final String base = cm.toString();
                                            vFinal.evaluateJavascript("(function(){try{return document.cookie||'';}catch(e){return '';}})()",
                                                new android.webkit.ValueCallback<String>(){ public void onReceiveValue(String dc){
                                                    try{ dc = dc.replace("\"","").trim(); }catch(Throwable t){}
                                                    String full = base + (dc != null && dc.length() > 0 ? ("; " + dc) : "");
                                                    if(full.contains("SESSDATA=") && !full.equals(biliCookieLast)){
                                                        biliCookieLast = full;
                                                        int n = CookieStore.save(MainActivity.this, CookieStore.PLATFORM_BILI, full);
                                                        BiliParser.buvid3 = "";
                                                        BiliParser.biliLoginLogged = false;
                                                        Log.i("DYP","BILI_CK_GRAB len=" + full.length() + " saved=" + n);
                                                        DiagLog.i(DiagLog.M_PARSE, "B站登录成功，Cookie 已存本地（" + n + " 项），自动回解析页");
                                                        // 和 YouTube / 抖音一样：抓到就自动退出，不保留网页
                                                        h.postDelayed(new Runnable(){ public void run(){
                                                            hideBiliLoginBar();
                                                            stopBiliLogin();
                                                            buildSettingsPage();
                                                            showHintToast("B站登录成功（" + n + " 项 Cookie），已回解析页");
                                                        }}, 800);
                                                    }
                                                }});
                                        }});
                                    }
                                }catch(Throwable t2){ Log.i("DYP","BILI_LOGIN_PROBE_ERR " + t2); }
                            }});
                    }
                    return;
                }
                if(insLoginMode){
                    if(u != null && u.contains("instagram.com")){
                        v.evaluateJavascript(
                            "(function(){try{var m=document.cookie.match(/csrftoken=([^;]+)/);"
                            +"var d=document.cookie.indexOf('ds_user_id')>=0;"
                            +"var li=!!document.querySelector('a[href*=\\'/direct/inbox/\\'],svg[aria-label=\\'Home\\']');"
                            +"return (m?m[1].length:0)+'|'+(d?1:0)+'|'+(li?1:0);}catch(e){return '0|0|0';}})()",
                            new android.webkit.ValueCallback<String>(){ public void onReceiveValue(String r){
                                Log.i("DYP","INS_LOGIN_PROBE "+r);
                                try{
                                    String[] p = r.replace("\"","").split("\\|");
                                    if(p.length >= 3 && (p[1].equals("1") || p[2].equals("1"))){
                                        final WebView vFinal = v;
                                        h.post(new Runnable(){ public void run(){
                                            final StringBuilder cm = new StringBuilder();
                                            try{ cm.append(CookieManager.getInstance().getCookie("https://www.instagram.com/")); }catch(Throwable t){}
                                            final String base = cm.toString();
                                            vFinal.evaluateJavascript("(function(){try{return document.cookie||'';}catch(e){return '';}})()",
                                                new android.webkit.ValueCallback<String>(){ public void onReceiveValue(String dc){
                                                    try{ dc = dc.replace("\"","").trim(); }catch(Throwable t){}
                                                    String full = base + (dc != null && dc.length() > 0 ? ("; " + dc) : "");
                                                    if(full.contains("sessionid=") && !full.equals(insCookieLast)){
                                                        insCookieLast = full;
                                                        int n = CookieStore.save(MainActivity.this, CookieStore.PLATFORM_INS, full);
                                                        Log.i("DYP","INS_CK_GRAB len=" + full.length() + " saved=" + n);
                                                        s("Cookie 已保存（" + n + " 项），页面保留：完成安全验证后点「完成」");
                                                        updateInsLoginBar();
                                                    }
                                                }});
                                        }});
                                    }
                                }catch(Throwable t2){ Log.i("DYP","INS_LOGIN_PROBE_ERR " + t2); }
                            }});
                    }
                    return;
                }
                // 抖音凭据抓取模式：持续监控 Cookie，记录每次变化（区分主页/视频页下发时机）
                if(dyLoginMode){
                    Log.i("DYP","DY_LOGIN_PROBE page=" + u);
                    checkDyCookie("onPageFinished");
                    return;
                }
                // YouTube 登录模式：检测登录态，不跑抖音解析逻辑
                if(ytLoginMode){
                    if(u != null && (u.contains("youtube.com") || u.contains("google.com"))){
                        v.evaluateJavascript(
                            "(function(){try{var ck=document.cookie||'';var hasLogin=ck.indexOf('SAPISID')>=0||ck.indexOf('LOGIN_INFO')>=0;return ck.length+'|'+(hasLogin?1:0)+'|'+((window.ytInitialPlayerResponse||window.ytcfg)?1:0);}catch(e){return '0|0|0';}})()",
                            new android.webkit.ValueCallback<String>(){ public void onReceiveValue(String r){
                                Log.i("DYP","YT_LOGIN_PROBE "+r);
                                try{
                                    String[] p=r.replace("\"","").split("\\|");
                                    int len=Integer.parseInt(p[0]);
                                    if(p[1].equals("1") || (len>80 && p[2].equals("1"))){
                                        final WebView vFinal = v;
                                        h.post(new Runnable(){ public void run(){
                                            // 从 CookieManager + document.cookie 抓可见部分
                                            final StringBuilder cm = new StringBuilder();
                                            try{ cm.append(CookieManager.getInstance().getCookie("https://www.youtube.com/")); }catch(Throwable t){}
                                            vFinal.evaluateJavascript("(function(){try{return document.cookie||'';}catch(e){return '';}})()",
                                                new android.webkit.ValueCallback<String>(){ public void onReceiveValue(String dc){
                                                    try{ dc=dc.replace("\"","").trim(); }catch(Throwable t){}
                                                    String full = cm.toString() + (dc!=null&&dc.length()>0?("; "+dc):"");
                                                    if(full.length()>=16){
                                                        ytCookieLast=full;
                                                        getSharedPreferences(SP, MODE_PRIVATE).edit().putString(KEY_YT_COOKIE, full).apply();
                                                        saveYtCookieLocal(full);
                                                        Log.i("DYP","YT_CK_GRAB len="+full.length()+" head="+full.substring(0,Math.min(60,full.length())));
                                                        uploadYtCookie(full);
                                                        s("YouTube 登录成功！Cookie 已保存，解析时自动使用");
                                                        showHintToast("YouTube 登录成功");
                                                    } else {
                                                        s("检测到登录态，但 Cookie 读取失败，请再点一次登录");
                                                    }
                                                    stopYtLogin();
                                                }});
                                        }});
                                    }
                                }catch(Throwable t){ Log.i("DYP","YT_LOGIN_PROBE_ERR "+t); }
                            }});
                    }
                    return;
                }
// 页面级主页检测：短链跳转 / 用户自己改链都适用
                 if(isProfilePage(u) && !profileMode){
                     // 签名翻页已出结果：拒绝页面再触发新一轮，避免覆盖
                     if(dyProfileRenderLocked){
                         Log.i("DYP", "PROFILE_AUTO_SKIP 结果已锁定，忽略页面触发");
                         return;
                     }
                     // 已经翻完并渲染出结果的主页，不再被页面的二次跳转覆盖
                     String secSeen = extractSecUid(u);
                     if(secSeen != null && secSeen.length() > 10
                             && secSeen.equals(dyProfileDoneSecUid) && profileCollected.size() > 0){
                         Log.i("DYP", "PROFILE_AUTO_SKIP 该主页已翻完，忽略重复触发");
                         return;
                     }
Log.i("DYP", "PROFILE_AUTO " + u.substring(0, Math.min(120, u.length())));
                     // 短链/页面自动跳到主页：先让用户确认要不要按主页解析
                     // （直接粘主页链接的情况已在 parse() 里确认过，这里只处理「页面自己跳过来」）
                     if(!profileAutoConfirmed){
                         // 确认框正在等用户点：忽略页面后续的重复触发，避免绕过确认
                         if(bigParsePending){
                             Log.i("DYP", "PROFILE_AUTO_SKIP 确认框等待中，忽略重复触发");
                             return;
                         }
                         final String uu = u;
                         Log.i("DYP", "PROFILE_AUTO_CONFIRM 弹出确认");
                         h.post(new Runnable(){ public void run(){
                             confirmBigParse("主页", new Runnable(){ public void run(){
                                 // 只有用户真点了「继续解析」，confirmBigParse 内部才会置 true
                                 enterProfileMode(uu);
                             }}, new Runnable(){ public void run(){
                                 // 用户取消：停掉 WebView，回到可继续解析的状态
                                 Log.i("DYP", "PROFILE_AUTO_CANCEL 用户取消主页解析");
                                 profileMode = false;
                                 webDone = true;
                                 s("已取消主页解析");
                                 showHintToast("已取消主页解析");
                             }});
                         }});
                         return;
                     }
                     enterProfileMode(u);
                 }
                if(profileMode){
                    // 抖音分享页（iesdouyin.com/share/user/）只是个人名片，没有作品列表。
                    // 跳到 PC 主页 www.douyin.com/user/{sec_uid}，让页面自己加载列表 + 自己翻页
                    if(u != null && u.indexOf("/share/user/") >= 0
                            && (u.indexOf("iesdouyin.com") >= 0 || u.indexOf("douyin.com") >= 0)){
                        final String sec = extractSecUid(u);
                        if(sec != null && sec.length() > 10 && !dyProfileJumped){
                            dyProfileJumped = true;
                            Log.i("DYP", "DYPROF_JUMP_SEC " + sec);
                            h.postDelayed(new Runnable(){ public void run(){
                                try{
                                    if(profileMode && web != null){
                                        // 必须切桌面 UA，否则 www.douyin.com/user/ 会被 302 到
                                        // m.douyin.com/share/user/（手机版名片页，没有作品列表）
                                        try{
                                            web.getSettings().setUserAgentString(DyDetail.UA);
                                            dyProfilePcUA = true;
                                        }catch(Throwable t){}
                                        web.loadUrl("https://www.douyin.com/user/" + sec);
                                    }
                                }catch(Throwable t){}
                            }}, 1500);
                        }
                    }
                    // 快手 fw/user 壳页 → 主动跳真实主页
                    if(u != null && u.contains("kuaishou.com/fw/user/")){
                        java.util.regex.Matcher km = java.util.regex.Pattern
                            .compile("/fw/user/([A-Za-z0-9_]+)").matcher(u);
                        if(km.find()){
                            final String real = "https://live.kuaishou.com/profile/" + km.group(1);
                            Log.i("DYP", "KS_PROFILE_JUMP " + real);
                            h.postDelayed(new Runnable(){ public void run(){
                                try{
                                    if(profileMode && web != null
                                            && web.getUrl() != null
                                            && web.getUrl().contains("fw/user")){
                                        web.loadUrl(real);
                                    }
                                }catch(Throwable t){}
                            }}, 1200);
                        }
                    }
                    // 主页收集模式：注入收集脚本 + 诊断 SSR 数据
                    v.evaluateJavascript(PROFILE_HOOK_JS, null);
                    v.evaluateJavascript(
                        "(function(){try{var out={rd:0,aw:0,keys:[]};"
                        +"if(window._ROUTER_DATA){out.rd=JSON.stringify(window._ROUTER_DATA).length;"
                        +"var s=JSON.stringify(window._ROUTER_DATA);out.aw=s.indexOf('aweme_list');"
                        +"var c=0;for(var k in window._ROUTER_DATA){c++;if(c<8)out.keys.push(k);}}"
                        +"var t=document.body?document.body.innerText.length:0;"
                        +"window.DyBridge.onDiag(JSON.stringify({rd:out.rd,aw:out.aw,keys:out.keys.join(','),bodyLen:t}));"
                        +"}catch(e){window.DyBridge.onDiag('err:'+e);}})()", null);
                    return;
                }
                if(webDone) return;
                if(mixMode){ Log.i("DYP", "SKIP_INJECT mixMode"); return; }
                if(u==null || !(u.startsWith("http://")||u.startsWith("https://"))) return;
                // 短剧页：主动构造 item/list 请求，不依赖页面自己触发
                if(u.indexOf("/share/playlet/detail/") >= 0 && !mixFetching){
                    String pid = "";
                    try{
                        java.util.regex.Matcher mm = java.util.regex.Pattern
                            .compile("/playlet/detail/(\\d{6,25})").matcher(u);
                        if(mm.find()) pid = mm.group(1);
                    }catch(Throwable t){}
                    if(pid.length() > 0){
                        mixFetching = true;
                        final String fpid = pid;
                        Log.i("DYP", "PLAYLET_SELF pid=" + pid);
                        new Thread(new Runnable(){ public void run(){
                            try{
                                String firstUrl = "https://www.iesdouyin.com/web/api/playlet/item/list/"
                                    + "?reflow_source=reflow_page&aid=1128&playlet_id=" + fpid
                                    + "&count=10&cursor=0&device_platform=web"
                                    + "&version_code=280500&version_name=28.5.0";
                                fetchAllMixPages(firstUrl);
                            }catch(Throwable t){
                                Log.i("DYP", "PLAYLET_SELF_ERR " + t);
                                mixFetching = false;
                            }
                        }}).start();
                        return;
                    }
                }
                final String uu=u;
                // heuristics: challenge/shell page has no aweme marker
                v.evaluateJavascript("(function(){try{return (document.body?document.body.innerText.length:0)+'|'+((location.href.indexOf('/video/')>=0||location.href.indexOf('/note/')>=0||location.href.indexOf('/slides/')>=0||location.href.indexOf('/mix/')>=0||location.href.indexOf('/playlet/')>=0)?1:0)+'|'+(window._ROUTER_DATA?1:0);}catch(e){return '0|0|0';}})()",
                    new android.webkit.ValueCallback<String>(){ public void onReceiveValue(String r){
                        Log.i("DYP","probe="+r);
                        boolean real=false;
                        try{
                            String[] p=r.replace("\"","").split("\\|");
                            int len=Integer.parseInt(p[0]);
                            // 有 /video/ /note/ /slides/ 路径就认为是真页面（slides 页正文很短）
                            real=(len>200)||p[1].equals("1")||p[2].equals("1");
                        }catch(Exception e){}
                        if(real){ final WebView vv=v; h.post(new Runnable(){ public void run(){ inject(vv); }}); }
                        else { Log.i("DYP","shell page, wait for reload..."); }
                    }});
            }
        });
        web.setWebChromeClient(new WebChromeClient());
        web.addJavascriptInterface(new Bridge(), "DyBridge");
    }

    /** 四页签初始化：解析 / 列表 / 历史 / 设置 */
    View pageParse, pageHistory, pageList, pageSettings;
    LinearLayout historyWrap, listWrap, settingsWrap;
    TextView historyEmpty, listHead, btnDlAll;
    /** 列表页「清空列表」按钮 */
    TextView btnClearList;
    /** 解析页「上一个 / 下一个」导航 */
    LinearLayout parseNavRow;
    TextView btnParsePrev, btnParseNext, parseNavLabel;
    TextView tabParse, tabHistory, tabList, tabSettings;

    void setupTabs(){
        tabParse = (TextView)findViewById(R.id.tabParse);
        tabList = (TextView)findViewById(R.id.tabList);
        tabHistory = (TextView)findViewById(R.id.tabHistory);
        tabSettings = (TextView)findViewById(R.id.tabSettings);
        pageParse = findViewById(R.id.pageParse);
        pageList = findViewById(R.id.pageList);
        pageHistory = findViewById(R.id.pageHistory);
        pageSettings = findViewById(R.id.pageSettings);
        historyWrap = (LinearLayout)findViewById(R.id.historyWrap);
        historyEmpty = (TextView)findViewById(R.id.historyEmpty);
        listWrap = (LinearLayout)findViewById(R.id.listWrap);
        listHead = (TextView)findViewById(R.id.listHead);
        btnDlAll = (TextView)findViewById(R.id.btnDlAll);
        btnClearList = (TextView)findViewById(R.id.btnClearList);
        if(btnClearList != null){
            btnClearList.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){
                showClearListConfirm();
            }});
        }
        // 解析页上一条/下一条：批量解析时用来手动切换当前结果
        parseNavRow = (LinearLayout)findViewById(R.id.parseNavRow);
        btnParsePrev = (TextView)findViewById(R.id.btnParsePrev);
        btnParseNext = (TextView)findViewById(R.id.btnParseNext);
        parseNavLabel = (TextView)findViewById(R.id.parseNavLabel);
        if(btnParsePrev != null){
            btnParsePrev.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){
                gotoBatchItem(batchViewIndex - 1);
            }});
        }
        if(btnParseNext != null){
            btnParseNext.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){
                gotoBatchItem(batchViewIndex + 1);
            }});
        }
        settingsWrap = (LinearLayout)findViewById(R.id.settingsWrap);
        tabParse.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ switchTab(0); }});
        tabList.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ switchTab(1); }});
        tabHistory.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ switchTab(2); }});
        tabSettings.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ switchTab(3); }});
        switchTab(0);
        refreshHistory();
        buildSettingsPage();
    }

    void switchTab(int idx){
        try{
            boolean dark = isDarkMode();
            int textC = dark ? 0xFFE8EAF0 : 0xFF1A1C22;
            int hintC = dark ? 0xFF8A8F9C : 0xFF9AA0AC;
            pageParse.setVisibility(idx == 0 ? View.VISIBLE : View.GONE);
            pageList.setVisibility(idx == 1 ? View.VISIBLE : View.GONE);
            pageHistory.setVisibility(idx == 2 ? View.VISIBLE : View.GONE);
            pageSettings.setVisibility(idx == 3 ? View.VISIBLE : View.GONE);
            TextView[] tabs = {tabParse, tabList, tabHistory, tabSettings};
            for(int i = 0; i < tabs.length; i++){
                if(i == idx){
                    tabs[i].setBackground(makeRoundRect(0x1AFE2C55, 0, 10));
                    tabs[i].setTextColor(textC);
                    tabs[i].setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                } else {
                    tabs[i].setBackground(makeRoundRect(0x0DFFFFFF, 0x14FFFFFF, 10));
                    tabs[i].setTextColor(hintC);
                    tabs[i].setTypeface(android.graphics.Typeface.DEFAULT);
                }
            }
            if(idx == 2) refreshHistory();
        }catch(Throwable t){ Log.i("DYP", "TAB_ERR " + t); }
    }

    /** 设置页：常驻页签版，每项独立入口 */
    /** 重建设置页（Cookie 状态变化后刷新显示） */
    void refreshSettingRows(){
        try{ buildSettingsPage(); }catch(Throwable t){}
    }

    /** 解析后自动播放是否开启（默认关闭） */
    boolean autoPlayOn(){
        return getSharedPreferences(SP, MODE_PRIVATE).getBoolean(KEY_AUTO_PLAY, false);
    }
    /** 解析后自动下载是否开启（默认关闭） */
    boolean autoDlOn(){
        return getSharedPreferences(SP, MODE_PRIVATE).getBoolean(KEY_AUTO_DL, false);
    }
    /** 解析成功后自动清空输入框（默认关闭） */
    boolean clearInputOn(){
        return getSharedPreferences(SP, MODE_PRIVATE).getBoolean(KEY_CLEAR_INPUT, false);
    }
    void setAutoFlag(String key, boolean on){
        getSharedPreferences(SP, MODE_PRIVATE).edit().putBoolean(key, on).apply();
        DiagLog.i(DiagLog.M_APP, "设置变更 | " + key + "=" + on);
    }

    /** 内置能力（无开关，始终开启）：从剪贴板读分享口令，识别出链接就自动填进输入框。
     *  只在输入框为空时填，不覆盖用户正在输入的内容；同一个文本只填一次。
     *  剪贴板里有多条链接时：全部识别出来，一行一个填进输入框，并提示可批量解析。 */
    void autoFillFromClipboard(){
        try{
            if(input == null) return;
            CharSequence cur = input.getText();
            if(cur != null && cur.toString().trim().length() > 0) return;   // 已有内容不打扰
            android.content.ClipboardManager cm = (android.content.ClipboardManager)
                    getSystemService(android.content.Context.CLIPBOARD_SERVICE);
            if(cm == null || !cm.hasPrimaryClip()) return;
            android.content.ClipData clip = cm.getPrimaryClip();
            if(clip == null || clip.getItemCount() == 0) return;
            CharSequence cs = clip.getItemAt(0).coerceToText(this);
            if(cs == null) return;
            String txt = cs.toString();
            if(txt == null || txt.trim().length() == 0) return;
            if(txt.equals(clipLastSeen)) return;      // 同一条不再重复处理
            java.util.List<String> urls = extractUrls(txt);
            if(urls.isEmpty()) return;
            clipLastSeen = txt;
            if(urls.size() == 1){
                String u = urls.get(0);
                input.setText(u);
                input.setSelection(u.length());
                Log.i("DYP", "CLIP_AUTOFILL len=" + u.length() + " url=" + u.substring(0, Math.min(80, u.length())));
                DiagLog.i(DiagLog.M_APP, "剪贴板自动填链接 | " + u);
            } else {
                // 多条：一行一个，方便用户直接点解析走批量
                StringBuilder sb = new StringBuilder();
                for(int i = 0; i < urls.size(); i++){
                    if(i > 0) sb.append("\n");
                    sb.append(urls.get(i));
                }
                input.setText(sb.toString());
                input.setSelection(sb.length());
                Log.i("DYP", "CLIP_AUTOFILL_MULTI n=" + urls.size());
                DiagLog.i(DiagLog.M_APP, "剪贴板识别到 " + urls.size() + " 条链接，已按行填入");
                showHintToast("剪贴板识别到 " + urls.size() + " 条链接，点解析将批量处理");
            }
        }catch(Throwable t){
            Log.i("DYP", "CLIP_AUTOFILL_ERR " + t);
        }
    }

    /** 从一段文本里提取所有链接（去重、保持顺序）。
     *  支持：整段分享口令 / 一行一个链接 / 混合文本。 */
    java.util.List<String> extractUrls(String text){
        java.util.List<String> out = new java.util.ArrayList<String>();
        if(text == null) return out;
        // 先按行切，每行用 extractUrl 试一次（能吃抖音/快手/B站口令）
        String[] lines = text.split("[\\r\\n]+");
        for(String line : lines){
            String l = line == null ? "" : line.trim();
            if(l.length() == 0) continue;
            String u = extractUrl(l);
            if(u == null || u.length() < 10){
                java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("https?://[^\\s，,。、；;]+").matcher(l);
                if(m.find()) u = m.group();
            }
            if(u != null && u.length() >= 10 && !out.contains(u)) out.add(u);
        }
        // 整段没有换行的情况（一串口令连在一起）：兜底全量正则扫描
        if(out.isEmpty()){
            java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("https?://[^\\s，,。、；;]+").matcher(text);
            while(m.find()){
                String u = m.group();
                if(u.length() >= 10 && !out.contains(u)) out.add(u);
            }
        }
        return out;
    }
    /** 上次处理过的剪贴板文本，避免同一内容反复覆盖输入框 */
    String clipLastSeen = "";

    /** 解析成功后按设置清空输入框 */
    void maybeClearInput(){
        try{
            if(!clearInputOn() || input == null) return;
            input.setText("");
            Log.i("DYP", "CLEAR_INPUT 已清空");
        }catch(Throwable t){}
    }

    /** 解析成功后按设置触发自动播放 / 自动下载。
     *
     *  - 只在主渲染路径调用一次（一次解析只触发一次，用 autoFired 防重复）。
     *  - 自动播放只对「单视频」生效：图集/实况没有单一播放地址，不触发。
     *  - 自动下载对视频、图集、实况都生效（doDownload 自己会按类型分流）。
     *  - 两个都开启时同时执行：先起下载任务，再进播放器（下载在后台线程跑，不阻塞）。
     */
    /** 批量解析模式下，把当前这条解析结果攒进 batchCollected。 */
    void collectBatchItem(){
        if(!batchCollectMode) return;
        try{
            ProfileParser.ProfileItem it = new ProfileParser.ProfileItem();
            it.url = curUrl == null ? "" : curUrl;
            it.id = curId == null ? "" : curId;
            String t = curDesc == null ? "" : curDesc.trim();
            if(t.length() > 60) t = t.substring(0, 60) + "…";
            if(t.length() == 0) t = curId == null ? "" : curId;
            it.title = t;
            it.directUrl = curPlay == null ? "" : curPlay;
            boolean hasImgs = imgUrls != null && imgUrls.size() > 0;
            it.isImage = hasImgs;
            if(hasImgs) it.directUrl = imgUrls.get(0);
            synchronized(batchCollected){ batchCollected.add(it); }
            Log.i("DYP", "BATCH_COLLECT now=" + batchCollected.size()
                + " img=" + hasImgs + " title=" + t.substring(0, Math.min(30, t.length())));
        }catch(Throwable t){
            Log.i("DYP", "BATCH_COLLECT_ERR " + t);
        }
    }

    /** 把批量解析结果存到本地（退出重进可恢复）。
     *  存成简单的「标题\t链接\tdirectUrl\tisImage」行格式，避免引入 JSON 库。 */
    void saveBatchList(){
        try{
            if(!batchKeepOn()) return;
            java.util.List<ProfileParser.ProfileItem> snap;
            synchronized(batchCollected){
                snap = new java.util.ArrayList<ProfileParser.ProfileItem>(batchCollected);
            }
            java.io.File f = batchListFile();
            if(f == null) return;
            StringBuilder sb = new StringBuilder();
            for(ProfileParser.ProfileItem it : snap){
                if(it == null) continue;
                sb.append(batchEsc(it.title)).append('\t')
                  .append(batchEsc(it.url)).append('\t')
                  .append(batchEsc(it.directUrl)).append('\t')
                  .append(it.isImage ? "1" : "0").append('\n');
            }
            java.io.FileOutputStream fo = new java.io.FileOutputStream(f);
            fo.write(sb.toString().getBytes("UTF-8"));
            fo.close();
            Log.i("DYP", "BATCH_SAVE n=" + snap.size() + " -> " + f.getAbsolutePath());
        }catch(Throwable t){
            Log.i("DYP", "BATCH_SAVE_ERR " + t);
        }
    }

    /** 启动时恢复列表 */
    void loadBatchList(){
        try{
            if(!batchKeepOn()) return;
            java.io.File f = batchListFile();
            if(f == null || !f.exists()) return;
            java.io.BufferedReader br = new java.io.BufferedReader(
                    new java.io.InputStreamReader(new java.io.FileInputStream(f), "UTF-8"));
            java.util.List<ProfileParser.ProfileItem> tmp = new java.util.ArrayList<ProfileParser.ProfileItem>();
            String line;
            while((line = br.readLine()) != null){
                if(line.length() == 0) continue;
                String[] p = line.split("\t", -1);
                if(p.length < 3) continue;
                ProfileParser.ProfileItem it = new ProfileParser.ProfileItem();
                it.title = batchUnesc(p[0]);
                it.url = batchUnesc(p[1]);
                it.directUrl = p.length > 2 ? batchUnesc(p[2]) : "";
                it.isImage = p.length > 3 && "1".equals(p[3]);
                tmp.add(it);
            }
            br.close();
            synchronized(batchCollected){
                batchCollected.clear();
                batchCollected.addAll(tmp);
            }
            Log.i("DYP", "BATCH_LOAD n=" + tmp.size());
            if(!tmp.isEmpty()){
                renderBatchList();
            }
        }catch(Throwable t){
            Log.i("DYP", "BATCH_LOAD_ERR " + t);
        }
    }

    /** 列表持久化文件 */
    java.io.File batchListFile(){
        try{
            java.io.File dir = getExternalFilesDir(null);
            if(dir == null) dir = new java.io.File(getFilesDir(), "data");
            if(!dir.exists()) dir.mkdirs();
            return new java.io.File(dir, "batch_list.tsv");
        }catch(Throwable t){ return null; }
    }

    static String batchEsc(String s){
        if(s == null) return "";
        return s.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n").replace("\r", "");
    }
    static String batchUnesc(String s){
        if(s == null) return "";
        return s.replace("\\n", "\n").replace("\\t", "\t").replace("\\\\", "\\");
    }

    /** 列表保留开关（默认开启） */
    boolean batchKeepOn(){
        return getSharedPreferences(SP, MODE_PRIVATE).getBoolean(KEY_BATCH_KEEP, true);
    }
    void setBatchKeep(boolean on){
        getSharedPreferences(SP, MODE_PRIVATE).edit().putBoolean(KEY_BATCH_KEEP, on).apply();
        if(!on){
            // 关掉时把已存的清掉，避免下次启动又冒出来
            try{ java.io.File f = batchListFile(); if(f != null && f.exists()) f.delete(); }catch(Throwable t){}
        }
        DiagLog.i(DiagLog.M_APP, "设置变更 | 列表保留=" + on);
    }

    /** 回到前台：列表有数据但界面是空的（被系统回收/未渲染）就补渲染 */
    void restoreListIfEmpty(){
        try{
            if(listWrap == null) return;
            if(listWrap.getChildCount() > 0) return;    // 已经有内容，不重复渲染
            int n;
            synchronized(batchCollected){ n = batchCollected.size(); }
            if(n > 0){
                Log.i("DYP", "LIST_RESTORE n=" + n);
                renderBatchList();
            }
        }catch(Throwable t){}
    }

    /** 把批量解析收集到的结果渲染到列表页 */
    void renderBatchList(){
        int n;
        synchronized(batchCollected){ n = batchCollected.size(); }
        Log.i("DYP", "BATCH_RENDER n=" + n + " wrap=" + (listWrap != null));
        if(n == 0){
            if(listWrap != null) listWrap.removeAllViews();
            if(listHead != null) listHead.setText("批量解析没有拿到结果");
            if(btnDlAll != null) btnDlAll.setVisibility(View.GONE);
            if(btnClearList != null) btnClearList.setVisibility(View.GONE);
            return;
        }
        java.util.List<ProfileParser.ProfileItem> snapshot;
        synchronized(batchCollected){
            snapshot = new java.util.ArrayList<ProfileParser.ProfileItem>(batchCollected);
        }
        profilePage = 0;
        showProfileList(snapshot);
    }

    /** 清空列表（含主页 / 合集 / 批量解析的结果） */
    void clearAllList(){
        try{
            if(listWrap != null) listWrap.removeAllViews();
            synchronized(profileCollected){ profileCollected.clear(); }
            synchronized(profileSeen){ profileSeen.clear(); }
            synchronized(batchCollected){ batchCollected.clear(); }
            synchronized(mixItems){ mixItems.clear(); }
            profilePage = 0;
            batchCollectMode = false;
            // 同步删掉落盘的列表，否则重启后又冒出来
            try{ java.io.File f = batchListFile(); if(f != null && f.exists()) f.delete(); }catch(Throwable t){}
            if(listHead != null) listHead.setText("粘贴主页链接或批量链接后点解析，列表会出现在这里");
            if(btnDlAll != null) btnDlAll.setVisibility(View.GONE);
            if(btnClearList != null) btnClearList.setVisibility(View.GONE);
            Log.i("DYP", "LIST_CLEARED");
        }catch(Throwable t){
            Log.i("DYP", "LIST_CLEAR_ERR " + t);
        }
    }

    /** 清空列表确认 */
    void showClearListConfirm(){
        int n1, n2;
        synchronized(profileCollected){ n1 = profileCollected.size(); }
        synchronized(batchCollected){ n2 = batchCollected.size(); }
        int total = Math.max(n1, n2);
        dypDlg()
            .setTitle("清空列表")
            .setMessage("当前列表有 " + total + " 条记录。\n\n"
                + "清空后需要重新解析才能看到列表，已下载的文件不受影响。")
            .setPositiveButton("清空", new android.content.DialogInterface.OnClickListener(){
                public void onClick(android.content.DialogInterface d, int w){
                    clearAllList();
                    showHintToast("列表已清空");
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }

    void maybeAutoAction(){
        Log.i("DYP", "AUTO_CHECK fired=" + autoFired + " play=" + autoPlayOn() + " dl=" + autoDlOn()
            + " curPlay=" + (curPlay == null ? 0 : curPlay.length())
            + " imgs=" + (imgUrls == null ? 0 : imgUrls.size()));
        // 批量解析模式：把本条结果攒进列表
        collectBatchItem();
        // 本条已渲染完成，推进到批量队列的下一条
        batchItemFinished();
        // 清空输入框不受 autoFired 影响：只要开着，每次解析成功都清
        maybeClearInput();
        if(autoFired) return;
        final boolean ap = autoPlayOn();
        final boolean ad = autoDlOn();
        if(!ap && !ad) return;
        autoFired = true;
        h.postDelayed(new Runnable(){ public void run(){
            try{
                boolean hasImgs = imgUrls != null && imgUrls.size() > 0;
                boolean hasVideo = curPlay != null && curPlay.length() > 0;
                if(ad){
                    // 主页列表模式没有单一目标，不自动下（避免批量误下几百个文件）
                    if(!profileMode && (hasImgs || hasVideo)){
                        Log.i("DYP", "AUTO_DL 触发 图集=" + hasImgs + " 视频=" + hasVideo);
                        doDownload();
                    } else {
                        Log.i("DYP", "AUTO_DL 跳过 profileMode=" + profileMode);
                    }
                }
                if(ap){
                    if(hasVideo && !hasImgs){
                        // 渲染阶段已按开关执行过 doPlay()（页内小窗起播），
                        // 这里再开全屏播放器，实现「解析完直接看」。
                        Log.i("DYP", "AUTO_PLAY 触发 openPlayer");
                        openPlayer();
                    } else {
                        Log.i("DYP", "AUTO_PLAY 跳过 图集=" + hasImgs + " 视频=" + hasVideo);
                    }
                }
            }catch(Throwable t){
                Log.i("DYP", "AUTO_ACTION_ERR " + t);
            }
        }}, 600);   // 等 UI 布局完成再动作，避免播放器拿不到窗口
    }


    /** 「批量解析」入口：弹一个多行输入框，一行一个链接，点确定走串行批量解析。
     *  也提供「读取剪贴板」按钮，直接把手上的多链接粘贴进来。 */
    void showBatchInputDialog(){
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(10), dp(16), dp(4));

        TextView tip = new TextView(this);
        tip.setText("一行一个链接，支持抖音 / 快手 / TikTok / X / YouTube / B站 / Instagram。\n"
                  + "留空的行会被忽略，重复链接自动去重。");
        tip.setTextSize(12);
        tip.setTextColor(curDark ? 0xFF9BA1AE : 0xFF6B6F7A);
        tip.setPadding(0, 0, 0, dp(8));
        box.addView(tip);

        final EditText et = new EditText(this);
        et.setHint("https://v.douyin.com/xxxxx/");
        et.setTextSize(13);
        et.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
        et.setMinLines(6);
        et.setMaxLines(12);
        et.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        box.addView(et, lp);

        android.widget.Button paste = new android.widget.Button(this);
        paste.setText("读取剪贴板");
        paste.setAllCaps(false);
        paste.setTextSize(12);
        paste.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){
            try{
                android.content.ClipboardManager cm = (android.content.ClipboardManager)
                        getSystemService(android.content.Context.CLIPBOARD_SERVICE);
                if(cm == null || !cm.hasPrimaryClip()){ showHintToast("剪贴板为空"); return; }
                CharSequence cs = cm.getPrimaryClip().getItemAt(0).coerceToText(MainActivity.this);
                if(cs == null){ showHintToast("剪贴板为空"); return; }
                java.util.List<String> us = extractUrls(cs.toString());
                if(us.isEmpty()){ showHintToast("剪贴板里没识别到链接"); return; }
                StringBuilder sb = new StringBuilder();
                for(int i = 0; i < us.size(); i++){
                    if(i > 0) sb.append("\n");
                    sb.append(us.get(i));
                }
                et.setText(sb.toString());
                showHintToast("已读取 " + us.size() + " 条链接");
            }catch(Throwable t){ showHintToast("读取失败：" + t); }
        }});
        box.addView(paste);

        dypDlg()
            .setTitle("批量解析链接")
            .setView(box)
            .setPositiveButton("开始批量解析", new android.content.DialogInterface.OnClickListener(){
                public void onClick(android.content.DialogInterface d, int w){
                    java.util.List<String> us = extractUrls(et.getText().toString());
                    if(us.isEmpty()){ showHintToast("没识别到有效链接"); return; }
                    startBatchParse(us);
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }

    void buildSettingsPage(){
        if(settingsWrap == null) return;
        settingsWrap.removeAllViews();

        addSettingRow("批量解析", "一次粘贴多条链接，一行一个", new View.OnClickListener(){
            public void onClick(View v){ showBatchInputDialog(); }});
        addSettingRow("更新日志", "每个版本改了什么", new View.OnClickListener(){
            public void onClick(View v){ showChangelog(); }});
        addSettingRow("关于", "版本信息与免责声明", new View.OnClickListener(){
            public void onClick(View v){ showAbout(); }});
        addSettingRow("用户协议", "免责声明与使用条款", new View.OnClickListener(){
            public void onClick(View v){ showAgreementText(); }});
        addSettingRow("检查更新", "手动检查新版本", new View.OnClickListener(){
            public void onClick(View v){ checkUpdate(false); }});
        addSettingRow("更新源", platformName() + "（点击切换 Gitee / GitHub）",
            new View.OnClickListener(){
                public void onClick(View v){ showUpdSrcDialog(); }});
        if(!isGitee(this)){
            addSettingRow("GitHub 访问方式",
                GH_MODE_MIRROR.equals(ghMode(this))
                    ? "镜像加速 · 国内直连不通时自动选可用镜像"
                    : "直连 · 需要自己挂代理，速度最快",
                new View.OnClickListener(){
                    public void onClick(View v){ showGhModeDialog(); }});
        }
        addSettingRow("反馈群 " + APP_QQ_GROUP, "点击加入 QQ 群", new View.OnClickListener(){
            public void onClick(View v){
                try{
                    startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW,
                            Uri.parse(APP_QQ_GROUP_URL)));
                }catch(Throwable t){
                    s("无法打开浏览器");
                }
            }});

        addSettingSection("代理");
        addSettingRow("内置代理", "海外平台走服务器中转的开关", new View.OnClickListener(){
            public void onClick(View v){ showProxyDialog(); }});

        addSettingSection("解析后动作");
        addSettingRow("解析后自动播放", autoPlayOn() ? "已开启 · 解析完直接进播放器" : "已关闭 · 解析完停在结果页",
            new View.OnClickListener(){
                public void onClick(View v){ setAutoFlag(KEY_AUTO_PLAY, !autoPlayOn()); buildSettingsPage(); }});
        addSettingRow("解析后自动下载", autoDlOn() ? "已开启 · 解析完直接开始下载" : "已关闭 · 需手动点下载",
            new View.OnClickListener(){
                public void onClick(View v){ setAutoFlag(KEY_AUTO_DL, !autoDlOn()); buildSettingsPage(); }});
        addSettingRow("解析成功后清空链接", clearInputOn() ? "已开启 · 解析完自动清空输入框" : "已关闭 · 保留输入框内容",
            new View.OnClickListener(){
                public void onClick(View v){ setAutoFlag(KEY_CLEAR_INPUT, !clearInputOn()); buildSettingsPage(); }});
        addSettingRow("保留解析列表", batchKeepOn()
                ? "已开启 · 退出重进仍保留，批量结果继续往下排"
                : "已关闭 · 退出后清空列表",
            new View.OnClickListener(){
                public void onClick(View v){ setBatchKeep(!batchKeepOn()); buildSettingsPage(); }});

        addSettingSection("下载路径");
LinearLayout stRow = addSettingRow("存储写入检测", storageStateLabel(), new View.OnClickListener(){
            public void onClick(View v){ showStorageState(); }});
        // 调试用：长按「存储写入检测」那一行可切换强制 MediaStore 兜底
        try{
            stRow.setOnLongClickListener(new View.OnLongClickListener(){
                public boolean onLongClick(View v){ toggleForceScoped(); return true; }
            });
        }catch(Throwable t){}
        addSettingRow("视频目录", curDlDirLabel("视频"), new View.OnClickListener(){
            public void onClick(View v){ showDlDirDialog("视频", KEY_DL_VIDEO, "Download/DYParse/视频"); }});
        addSettingRow("图片目录", curDlDirLabel("图片"), new View.OnClickListener(){
            public void onClick(View v){ showDlDirDialog("图片", KEY_DL_IMG, "Download/DYParse/图片"); }});
        addSettingRow("音频目录", curDlDirLabel("音频"), new View.OnClickListener(){
            public void onClick(View v){ showDlDirDialog("音频", KEY_DL_AUDIO, "Download/DYParse/音频"); }});
        addSettingRow("合集目录", curDlDirLabel("合集"), new View.OnClickListener(){
            public void onClick(View v){ showDlDirDialog("合集", KEY_DL_MIX, "Download/DYParse/合集"); }});
        addSettingRow("动图目录", curDlDirLabel("动图"), new View.OnClickListener(){
            public void onClick(View v){ showDlDirDialog("动图", KEY_DL_ANIM, "Download/DYParse/动图"); }});
        addSettingRow("修复媒体库", "相册里看不到已下载文件？点这里补录", new View.OnClickListener(){
            public void onClick(View v){ fixMediaLibrary(); }});

        addSettingSection("服务器");
        addSettingRow("服务器地址", "自建服务器 / 内置服务器状态", new View.OnClickListener(){
            public void onClick(View v){ showSettings(); }});
        addSettingRow("机场流量", "点击查询剩余流量", new View.OnClickListener(){
            public void onClick(View v){
                loadAirInfo(new TextView(MainActivity.this));
            }});
        addSettingRow("YouTube 登录", "YouTube 风控时，登录后可解析更多视频", new View.OnClickListener(){
            public void onClick(View v){ showYtLoginDialog(); }});

        addSettingSection("Cookie（本地保存）");
        addSettingRow("YouTube Cookie", curYtCkLabel(), new View.OnClickListener(){
            public void onClick(View v){ showCookieDialog(CookieStore.PLATFORM_YT, "YouTube"); }});
        addSettingRow("Instagram Cookie", curInsCkLabel(), new View.OnClickListener(){
            public void onClick(View v){ showInsLoginDialog(); }});
addSettingRow("B站 Cookie", curBiliCkLabel(), new View.OnClickListener(){
             public void onClick(View v){ showBiliLoginMenu(); }});
         addSettingRow("抖音访客凭据", curDyUifidLabel(), new View.OnClickListener(){
             public void onClick(View v){ showDyUifidMenu(); }});
        addSettingRow("抖音主页登录", dyProfileCookiesLikelyLoggedIn()
                ? "已登录 · 主页可解析全部作品"
                : "未登录 · 主页最多 21 条，登录后拿全部", new View.OnClickListener(){
             public void onClick(View v){ showDyProfileLoginMenu(); }});
        addSettingRow("Cookie 文件位置", "点开查看已保存的 cookie 名单", new View.OnClickListener(){
            public void onClick(View v){ showCookieFilesInfo(); }});

addSettingSection("平台支持");
         addSettingRow("平台支持说明", "各平台解析方式与服务器依赖", new View.OnClickListener(){
             public void onClick(View v){ showPlatformHelp(); }});

        addSettingSection("调试");
        addSettingRow("调试日志", curDebugLogLabel(), new View.OnClickListener(){
            public void onClick(View v){ toggleDebugLog(); }});
        addSettingRow("查看日志", "复制 / 保存到 Download / 清除", new View.OnClickListener(){
            public void onClick(View v){ showDebugLogView(); }});
        addSettingRow("崩溃日志", curCrashLogLabel(), new View.OnClickListener(){
            public void onClick(View v){ showCrashLogView(); }});
    }

    // ================= 崩溃捕获 =================

    static boolean crashHandlerInstalled = false;

    /** 全局未捕获异常 → 写进崩溃日志，然后再交给系统默认处理（保证还能弹系统崩溃框） */
    void installCrashHandler(){
        if(crashHandlerInstalled) return;
        crashHandlerInstalled = true;
        final Thread.UncaughtExceptionHandler prev =
                Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler(){
            public void uncaughtException(Thread th, Throwable ex){
                try{
                    DiagLog.crash(DiagLog.M_APP,
                        "未捕获异常导致崩溃 | 线程=" + th.getName(), ex);
                }catch(Throwable t){}
                if(prev != null) prev.uncaughtException(th, ex);
            }
        });
Log.i("DYP", "CRASH_HANDLER_INSTALLED");
    }

    // ================= 存储权限 =================
    static final int REQ_STORAGE = 1001;
    /** 本次启动是否已经自动申请过存储权限（避免每次回前台都弹） */
    volatile boolean permAskedThisLaunch = false;

    /** 下载/保存前调用：没权限就申请，返回 false 表示已发起申请需等待 */
    boolean ensureStoragePerm(){
        if(AppInfo.hasStoragePerm(this)) return true;

        // 已被永久拒绝（Android 7 上系统不会再弹窗）→ 直接引导去设置页开
        if(AppInfo.storagePermBlocked(this)){
            DiagLog.w(DiagLog.M_PERM, "存储权限被永久拒绝 | API=" + android.os.Build.VERSION.SDK_INT
                + " | 引导用户去系统设置");
            showPermSettingsDialog();
            return false;
        }

        DiagLog.w(DiagLog.M_PERM, "缺少存储权限 | API=" + android.os.Build.VERSION.SDK_INT
            + " | 正在向用户申请 WRITE_EXTERNAL_STORAGE");
        // 标记「已申请过」，供 storagePermBlocked 判断永久拒绝
        try{
            getSharedPreferences(SP, MODE_PRIVATE).edit()
                .putBoolean("perm_storage_asked", true).apply();
        }catch(Throwable t){}
        try{
            requestPermissions(new String[]{
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
                android.Manifest.permission.READ_EXTERNAL_STORAGE
            }, REQ_STORAGE);
        }catch(Throwable t){
            DiagLog.e(DiagLog.M_PERM, "申请存储权限失败 | " + t);
        }
        return false;
    }

    /** 权限被永久拒绝时的引导弹窗：教用户去系统设置里手动开启 */
    void showPermSettingsDialog(){
        StringBuilder m = new StringBuilder();
        m.append("本应用需要「存储权限」才能把文件保存到手机。\n\n");
        m.append("系统已经不再弹权限框了（之前被拒绝过），请手动开启：\n\n");
        m.append("1. 点下面「去设置」\n");
        m.append("2. 找到「存储」或「存储和相机」\n");
        m.append("3. 选择「允许」\n");
        m.append("4. 返回本应用重新点下载\n\n");
        m.append("如果找不到开关，也可以到：\n");
        m.append("系统设置 → 应用管理 → DY解析器 → 权限");
        dypDlg()
            .setTitle("需要存储权限")
            .setMessage(m.toString())
            .setPositiveButton("去设置", new android.content.DialogInterface.OnClickListener(){
                public void onClick(android.content.DialogInterface d, int w){
                    openAppSettings();
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }

    /** 跳到本应用的系统设置页（权限/存储都在这里） */
    void openAppSettings(){
        try{
            android.content.Intent it = new android.content.Intent(
                    android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            it.setData(android.net.Uri.parse("package:" + getPackageName()));
            startActivity(it);
        }catch(Throwable t){
            Log.i("DYP", "OPEN_SETTINGS_ERR " + t);
            showHintToast("无法打开设置，请手动到系统设置里开启存储权限");
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res){
        super.onRequestPermissionsResult(code, perms, res);
        if(code != REQ_STORAGE) return;
        boolean granted = res != null && res.length > 0
                && res[0] == android.content.pm.PackageManager.PERMISSION_GRANTED;
        if(granted){
            DiagLog.i(DiagLog.M_PERM, "存储权限已授予");
            showHintToast("存储权限已授予，可以正常下载了");
        }else{
            DiagLog.e(DiagLog.M_PERM, "存储权限被拒绝 | 下载将无法保存文件"
                + " | 建议=到系统设置 → 应用 → 本应用 → 权限 → 存储，手动打开");
            showStoragePermHelp();
        }
    }

    /** 权限被拒后的引导弹窗，说清楚为什么需要、去哪开 */
    void showStoragePermHelp(){
        curDark = isDarkMode();
        dypDlg()
            .setTitle("需要存储权限")
            .setMessage("下载文件必须能写入手机的 Download 目录，否则会出现"
                + "「能解析、能播放，但下载失败」的情况。\n\n"
                + "请按这个路径手动打开：\n"
                + "系统设置 → 应用管理 → DY解析器 → 权限 → 存储 → 允许\n\n"
                + "Android 11 及以上系统如果找不到「存储」权限项，"
                + "通常是系统已经默认允许，可以直接再试一次下载。")
            .setPositiveButton("去设置", new android.content.DialogInterface.OnClickListener(){
                public void onClick(android.content.DialogInterface d, int w){
                    try{
                        android.content.Intent it = new android.content.Intent(
                            android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                        it.setData(android.net.Uri.parse("package:" + getPackageName()));
                        startActivity(it);
                    }catch(Throwable t){
                        showHintToast("请手动到系统设置里开启存储权限");
                    }
                }})
            .setNegativeButton("知道了", null)
            .show();
    }

    // ================= 调试日志 UI =================

    String curDebugLogLabel(){
        boolean on = DiagLog.isDebugOn();
        long sz = 0;
        try{ java.io.File f = DiagLog.debugFile(); if(f != null && f.exists()) sz = f.length(); }catch(Throwable t){}
        return (on ? "已开启 · " : "已关闭 · ")
                + (sz > 0 ? (sz / 1024) + " KB" : "暂无记录")
                + " · 记录解析/下载/播放每一步";
    }

    String curCrashLogLabel(){
        long sz = 0;
        try{ java.io.File f = DiagLog.crashFile(); if(f != null && f.exists()) sz = f.length(); }catch(Throwable t){}
        return (sz > 0 ? (sz / 1024) + " KB" : "暂无记录")
                + " · 始终开启，不受调试开关影响";
    }

    void toggleDebugLog(){
        boolean on = DiagLog.isDebugOn();
        if(on){
            DiagLog.setDebugOn(false);
            showHintToast("调试日志已关闭（崩溃日志仍在记录）");
        }else{
            DiagLog.setDebugOn(true);
            showHintToast("调试日志已开启，复现问题后回来「查看日志」");
        }
        buildSettingsPage();
    }

    void showDebugLogView(){
        showLogDialog(false);
    }

    void showCrashLogView(){
        showLogDialog(true);
    }

    /** 日志查看弹窗：内容 + 复制 / 保存到 Download / 清除 */
    void showLogDialog(final boolean crash){
        curDark = isDarkMode();
        String content = crash ? DiagLog.readCrash() : DiagLog.readDebug();
        if(content == null || content.trim().length() == 0){
            content = crash
                ? "崩溃日志还是空的。\n\n说明这段时间没有发生崩溃或失败。\n"
                  + "崩溃日志是一直开着的，不需要你去打开开关；\n"
                  + "一旦出现闪退、黑屏、下载失败这类问题，会自动记在这里。"
                : "调试日志还是空的。\n\n"
                  + "调试日志默认关闭（减少无谓写入）。\n"
                  + "要排查问题的话，请先在上一行把「调试日志」打开，\n"
                  + "然后回到 App 复现一次问题，再回来这里查看。";
        }

        int bgDlg   = curDark ? 0xFF191B21 : 0xFFFFFFFF;
        int textDlg = curDark ? 0xFFF2F3F5 : 0xFF1A1A1F;

        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setBackgroundColor(bgDlg);
        wrap.setPadding(dp(16), dp(12), dp(16), dp(8));

        TextView tip = new TextView(this);
        tip.setText(crash
            ? "崩溃日志：始终记录，不受调试日志开关影响。包含闪退堆栈与所有失败原因。"
            : "调试日志：记录解析/下载/播放的每一步。发给我们可以快速定位问题。");
        tip.setTextSize(12);
        tip.setTextColor(textDlg & 0x99FFFFFF);
        tip.setPadding(0, 0, 0, dp(8));
        wrap.addView(tip);

        android.widget.ScrollView sv = new android.widget.ScrollView(this);
        TextView tv = new TextView(this);
        tv.setText(content);
        tv.setTextSize(11);
        tv.setTextColor(textDlg);
        tv.setTypeface(android.graphics.Typeface.MONOSPACE);
        tv.setTextIsSelectable(true);
        tv.setPadding(dp(8), dp(8), dp(8), dp(8));
        sv.addView(tv);
        LinearLayout.LayoutParams svlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(360));
        sv.setLayoutParams(svlp);
        wrap.addView(sv);

        final String finalContent = content;
        dypDlg()
            .setTitle(crash ? "崩溃日志" : "调试日志")
            .setView(wrap)
            .setPositiveButton("复制", new android.content.DialogInterface.OnClickListener(){
                public void onClick(android.content.DialogInterface d, int w){
                    copyToClipboard(finalContent);
                    showHintToast("已复制到剪贴板");
                }})
            .setNeutralButton("保存到 Download", new android.content.DialogInterface.OnClickListener(){
                public void onClick(android.content.DialogInterface d, int w){
                    if(!AppInfo.hasStoragePerm(MainActivity.this)){
                        DiagLog.e(DiagLog.M_PERM, "保存日志失败 | 缺少存储权限");
                        showHintToast("没有存储权限，无法保存。请到系统设置里给本应用存储权限");
                        return;
                    }
                    String p = DiagLog.exportToDownload(crash);
                    if(p == null){
                        DiagLog.e(DiagLog.M_APP, "保存日志失败 | 写入 Download 返回空");
                        showHintToast("保存失败，试试先开启调试日志再复现一次");
                    }else{
                        DiagLog.i(DiagLog.M_APP, "日志已保存 | " + p);
                        showHintToast("已保存到：\n" + p);
                    }
                }})
            .setNegativeButton("清除", new android.content.DialogInterface.OnClickListener(){
                public void onClick(android.content.DialogInterface d, int w){
                    confirmClearLog(crash);
                }})
            .show();
    }

    void confirmClearLog(final boolean crash){
        curDark = isDarkMode();
        dypDlg()
            .setTitle("清除日志")
            .setMessage("确定清除" + (crash ? "崩溃日志" : "调试日志") + "吗？清除后无法恢复。")
            .setPositiveButton("清除", new android.content.DialogInterface.OnClickListener(){
                public void onClick(android.content.DialogInterface d, int w){
                    if(crash) DiagLog.clearCrash(); else DiagLog.clearDebug();
                    showHintToast("已清除");
                    buildSettingsPage();
                }})
            .setNegativeButton("取消", null)
            .show();
    }

    void copyToClipboard(String text){
        try{
            android.content.ClipboardManager cm = (android.content.ClipboardManager)
                    getSystemService(android.content.Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(android.content.ClipData.newPlainText("DYParse日志", text));
        }catch(Throwable t){}
    }

    /** 抖音访客凭据菜单：自动获取 / 手动填写 / 清空 */
    void showDyUifidMenu(){
        curDark = isDarkMode();
        boolean has = dyHasUifid();
        String[] items = {
            has ? "重新自动获取（约 5-10 秒）" : "自动获取（约 5-10 秒）",
            "手动填写（从浏览器复制）",
            has ? "清空凭据" : "查看原理说明"
        };
        dypDlg()
            .setTitle("抖音访客凭据（UIFID_TEMP）")
            .setItems(items, new android.content.DialogInterface.OnClickListener(){
                public void onClick(android.content.DialogInterface d, int w){
                    if(w == 0) startDyUifidFetchWithUi();
                    else if(w == 1) showUifidDialog();
                    else if(dyHasUifid()){
                        getSharedPreferences(SP, MODE_PRIVATE).edit().remove(KEY_UIFID).apply();
                        showHintToast("抖音凭据已清空");
                        buildSettingsPage();
                    } else {
                        showDyUifidWhy();
                    }
                }
            })
            .setNegativeButton("取消", null)
            .create().show();
    }

    /** 带界面提示的自动获取（用户主动点击时用） */
    void startDyUifidFetchWithUi(){
        curDark = isDarkMode();
        final android.app.AlertDialog prog = dypDlg()
            .setTitle("正在获取访客凭据")
            .setMessage("正在用内置浏览器访问抖音并完成校验，请稍候…\n\n"
                + "· 通常 5-10 秒\n"
                + "· 全程静默，无需你操作\n"
                + "· 成功后抖音实况图可本地解析，不再依赖服务器")
            .setCancelable(false)
            .create();
        prog.show();
        final long t0 = System.currentTimeMillis();
        autoFetchDyUifid(new Runnable(){ public void run(){
            long cost = System.currentTimeMillis() - t0;
            try{ prog.dismiss(); }catch(Throwable t){}
            if(dyHasUifid()){
                showHintToast("凭据已获取（" + (cost / 1000) + " 秒），实况图可本地解析");
                s("抖音访客凭据已就绪");
            } else {
                showDyUifidFail();
            }
            buildSettingsPage();
        }});
    }

    /** 自动获取失败提示 */
    void showDyUifidFail(){
        curDark = isDarkMode();
        dypDlg()
            .setTitle("获取失败")
            .setMessage("没能自动拿到凭据，可能原因：\n\n"
                + "· 抖音页面加载超时（网络慢或代理不通）\n"
                + "· 触发了人机验证\n\n"
                + "你可以：\n"
                + "1. 重试一次\n"
                + "2. 手动填写（从电脑浏览器 F12 复制）\n\n"
                + "注意：不填也能解析普通视频和图文，只是实况图会退化成静态图。")
            .setPositiveButton("重试", new android.content.DialogInterface.OnClickListener(){
                public void onClick(android.content.DialogInterface d, int w){ startDyUifidFetchWithUi(); }})
            .setNeutralButton("手动填写", new android.content.DialogInterface.OnClickListener(){
                public void onClick(android.content.DialogInterface d, int w){ showUifidDialog(); }})
            .setNegativeButton("取消", null)
            .create().show();
    }

    /** 凭据原理说明 */
    void showDyUifidWhy(){
        curDark = isDarkMode();
        dypDlg()
            .setTitle("为什么需要这个")
            .setMessage("抖音实况图（Live Photo）的动图轨道，只有 PC 版详情接口会下发；"
                + "这个接口在风控网关后面，缺少访客凭据时固定返回：\n\n"
                + "403 Blocked by ArgusSecurityPlugin Uifid Not Found\n\n"
                + "该凭据由抖音服务端签发、只发给通过浏览器挑战的客户端，"
                + "没有算法可以伪造。\n\n"
                + "本 App 的做法：在后台开一个隐藏浏览器打开抖音首页，"
                + "让它通过挑战并拿到凭据，然后存到你手机本地。\n"
                + "凭据不会上传到任何服务器。\n\n"
                + "有效期：通常几周到几个月，失效后重新获取即可。")
            .setPositiveButton("知道了", null)
            .create().show();
    }

    /** 统一弹窗构建器：颜色必须跟随 App 主题。
     *  不直接读 curDark —— 它有可能是默认 false 还没被赋值，
     *  那样在深色模式下会弹出亮色框。这里实时查系统深色状态。 */
    android.app.AlertDialog.Builder dypDlg(){
        curDark = isDarkMode();
        return new android.app.AlertDialog.Builder(this, curDark
            ? android.app.AlertDialog.THEME_DEVICE_DEFAULT_DARK
            : android.app.AlertDialog.THEME_DEVICE_DEFAULT_LIGHT);
    }

    /** 设置页：YouTube Cookie 状态标签 */
    String curYtCkLabel(){
        int n = CookieStore.count(this, CookieStore.PLATFORM_YT);
        if(n <= 0) return "未填写 · 点这里粘贴 / 登录自动抓取";
        return "已保存 " + n + " 项" + (CookieStore.ytReady(this) ? "（登录态完整）" : "（缺少关键项）");
    }

    /** 设置页：Instagram Cookie 状态标签 */
    String curInsCkLabel(){
        int n = CookieStore.count(this, CookieStore.PLATFORM_INS);
        if(n <= 0) return "未填写 · IG 视频必须登录才能解析";
        return "已保存 " + n + " 项" + (CookieStore.insReady(this) ? "（含 sessionid）" : "（缺 sessionid）");
    }

    /** 设置页：B站 Cookie 状态标签 */
    String curBiliCkLabel(){
        int n = CookieStore.count(this, CookieStore.PLATFORM_BILI);
        if(n <= 0) return "未填写 · 匿名只有 480P/720P";
        return "已保存 " + n + " 项" + (CookieStore.biliReady(this) ? "（含 SESSDATA，可拿 1080P）" : "（缺 SESSDATA，仍是 720P）");
    }

    /** B站 Cookie 入口：App 内登录 / 手动填写 */
    void showBiliLoginMenu(){
        curDark = isDarkMode();
        int n = CookieStore.count(this, CookieStore.PLATFORM_BILI);
        String[] items = {
            "App 内登录（自动抓 SESSDATA）",
            "手动填写 Cookie",
            n > 0 ? ("清空 Cookie（当前 " + n + " 项）") : "清空 Cookie（无）"
        };
        dypDlg()
            .setTitle("B站 Cookie")
            .setItems(items, new android.content.DialogInterface.OnClickListener(){
                public void onClick(android.content.DialogInterface d, int w){
                    if(w == 0) startBiliLogin();
                    else if(w == 1) showCookieDialog(CookieStore.PLATFORM_BILI, "B站");
                    else {
                        CookieStore.clear(MainActivity.this, CookieStore.PLATFORM_BILI);
                        BiliParser.buvid3 = "";
                        BiliParser.biliLoginLogged = false;
                        showHintToast("B站 Cookie 已清空");
                        buildSettingsPage();
                    }
                }
            })
            .setNegativeButton("取消", null)
            .create().show();
    }

    /** Cookie 文件位置说明 */
    void showCookieFilesInfo(){
        curDark = isDarkMode();
        StringBuilder b = new StringBuilder();
        b.append("Cookie 全部保存在手机本地，不上传任何服务器。\n\n");
        b.append("抖音：\n").append(CookieStore.filePath(this, CookieStore.PLATFORM_DY))
            .append("（").append(CookieStore.count(this, CookieStore.PLATFORM_DY)).append(" 项）\n\n");
        b.append("YouTube：\n").append(CookieStore.filePath(this, CookieStore.PLATFORM_YT))
            .append("（").append(CookieStore.count(this, CookieStore.PLATFORM_YT)).append(" 项）\n\n");
        b.append("Instagram：\n").append(CookieStore.filePath(this, CookieStore.PLATFORM_INS))
            .append("（").append(CookieStore.count(this, CookieStore.PLATFORM_INS)).append(" 项）\n\n");
        b.append("B站：\n").append(CookieStore.filePath(this, CookieStore.PLATFORM_BILI))
            .append("（").append(CookieStore.count(this, CookieStore.PLATFORM_BILI)).append(" 项）\n\n");
        b.append("格式：一行一个 cookie，name=value，name 不能省略。\n");
        b.append("例：\n");
        b.append("sessionid=12345%3Aabc\n");
        b.append("csrftoken=AbCdEf\n");
        b.append("ds_user_id=67890\n\n");
        b.append("B站填 SESSDATA 即可（有它才有 1080P，不填只有 720P）：\n");
        b.append("SESSDATA=xxxxxxxx\n\n");
        b.append("抖音主页解析全量需要 sessionid / sid_tt / uid_tt：\n");
        b.append("sessionid=xxxx\nsid_tt=xxxx\nuid_tt=xxxx\n\n");
        b.append("服务器到期 / 关闭内置代理时，这些 cookie 仍然可用。");
        dypDlg().setTitle("Cookie 存储位置").setMessage(b.toString())
            .setPositiveButton("知道了", null).create().show();
    }

    /** 抖音登录后：把 WebView 里的 Cookie 落到本地文件（sessionid 等是 HttpOnly，必须用 CookieManager 取）
     *  只保存登录票据类字段：msToken / __ac_signature / ttwid 这类签名字段是短时效的，
     *  存下来再拿去用一定失败，所以不落盘。 */
    boolean saveDyCookieLocal(String src){
        try{
            String cm = null;
            try{ cm = CookieManager.getInstance().getCookie("https://www.douyin.com/"); }catch(Throwable t){}
            String ck = cm;
            if((ck == null || ck.length() == 0) && src != null) ck = src;
            if(ck == null || ck.length() == 0) return false;
            if(!ck.contains("sessionid=") && !ck.contains("sid_tt=")) return false;
            if(ck.equals(dyCookieLast)) return false;
            dyCookieLast = ck;

            // 只保留长期有效的登录票据，短时效签名字段不存
            String[] keys = new String[]{
                "sessionid", "sessionid_ss", "sid_tt", "uid_tt", "uid_tt_ss",
                "sid_guard", "sid_ucp_v1", "ssid_ucp_v1", "passport_csrf_token",
                "passport_auth_status", "ttwid", "odin_tt", "d_ticket",
                "login_time", "is_staff_user"
            };
            java.util.Map<String,String> m = new java.util.LinkedHashMap<String,String>();
            for(String kv : ck.split(";")){
                String s = kv.trim();
                int e = s.indexOf('=');
                if(e <= 0) continue;
                String k = s.substring(0, e).trim();
                String v = s.substring(e + 1).trim();
                if(k.length() == 0 || v.length() == 0) continue;
                for(String want : keys){
                    if(k.equals(want)){ m.put(k, v); break; }
                }
            }
            if(m.isEmpty()) return false;
            int n = m.size();
            CookieStore.saveMap(this, CookieStore.PLATFORM_DY, m);
            Log.i("DYP", "DY_CK_GRAB len=" + ck.length() + " saved=" + n + "（仅登录票据）"
                    + " file=" + CookieStore.filePath(this, CookieStore.PLATFORM_DY));
            DiagLog.i(DiagLog.M_PARSE, "抖音登录票据已存本地 | " + n + " 项 | "
                    + CookieStore.filePath(this, CookieStore.PLATFORM_DY));
            return true;
        }catch(Throwable t){
            Log.i("DYP", "DY_CK_GRAB_ERR " + t);
            DiagLog.e(DiagLog.M_PARSE, "保存抖音 Cookie 失败", t);
            return false;
        }
    }

    /** 通用 Cookie 填写弹窗 */
    void showCookieDialog(final String platform, final String label){
        curDark = isDarkMode();
        final String cur = CookieStore.dump(this, platform);
        boolean has = cur.length() > 0;

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        box.setPadding(pad, dp(8), pad, 0);

        TextView tip = new TextView(this);
        tip.setTextSize(12);
        tip.setTextColor(curDark ? 0xFF9AA0AC : 0xFF6B7280);
        StringBuilder tb = new StringBuilder();
        tb.append("一行一个 cookie，name=value，必须带名字。\n");
        if(CookieStore.PLATFORM_INS.equals(platform)){
            tb.append("IG 至少要有：sessionid、csrftoken、ds_user_id\n");
        } else if(CookieStore.PLATFORM_BILI.equals(platform)){
            tb.append("B站填 SESSDATA 即可；填了可拿 1080P，不填只有 720P\n");
        } else if(CookieStore.PLATFORM_DY.equals(platform)){
            tb.append("抖音主页要拿全部作品，至少要有登录票据：\n"
                + "sessionid、sid_tt、uid_tt（有 ttwid 更好）\n"
                + "获取方法：电脑 Chrome 登录 www.douyin.com → F12 → "
                + "Application → Cookies → www.douyin.com，复制对应值\n"
                + "只存手机本地，不上传服务器。\n");
        } else {
            tb.append("YouTube 至少要有：SAPISID、__Secure-1PSID\n");
        }
        tb.append("支持直接粘贴浏览器的一整行 a=1; b=2，会自动拆开。");
        tip.setText(tb.toString());
        box.addView(tip);

        final EditText et = new EditText(this);
        et.setTextSize(12);
        et.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
        et.setMinLines(8);
        et.setMaxLines(14);
        et.setHorizontallyScrolling(false);
        et.setText(cur);
        et.setHint(CookieStore.PLATFORM_INS.equals(platform)
            ? "sessionid=...\ncsrftoken=...\nds_user_id=..."
            : (CookieStore.PLATFORM_BILI.equals(platform)
                ? "SESSDATA=...\nbuvid3=..."
                : (CookieStore.PLATFORM_DY.equals(platform)
                    ? "sessionid=...; sid_tt=...; uid_tt=...\n（直接粘一整行也行）"
                    : "SAPISID=...\n__Secure-1PSID=...\nLOGIN_INFO=...")));
        et.setTextColor(curDark ? 0xFFE6E8EC : 0xFF1F2937);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(10);
        box.addView(et, lp);

        android.app.AlertDialog dlg = dypDlg()
            .setTitle(label + " Cookie")
            .setView(box)
            .setPositiveButton("保存", new android.content.DialogInterface.OnClickListener(){
                public void onClick(android.content.DialogInterface d, int w){
                    String txt = et.getText() == null ? "" : et.getText().toString();
                    int n = CookieStore.save(MainActivity.this, platform, txt);
                    if(n <= 0){
                        showHintToast("没解析到有效 cookie，格式应为 name=value");
                        return;
                    }
                    showHintToast(label + " Cookie 已保存（" + n + " 项）");
                    buildSettingsPage();
                }})
            .setNeutralButton("清空", new android.content.DialogInterface.OnClickListener(){
                public void onClick(android.content.DialogInterface d, int w){
                    CookieStore.clear(MainActivity.this, platform);
                    showHintToast(label + " Cookie 已清空");
                    buildSettingsPage();
                }})
            .setNegativeButton("取消", null)
            .create();
        dlg.show();
    }

    /** Instagram 登录弹窗：每次登录都先弹风险确认 */
    void showInsLoginDialog(){
        curDark = isDarkMode();
        showInsRiskDialog();
    }

    /** Instagram 登录风险提示（每次登录都提示，同意后才进入登录流程） */
    void showInsRiskDialog(){
        curDark = isDarkMode();
        StringBuilder b = new StringBuilder();
        b.append("⚠ 登录前必读\n\n");
        b.append("1. 请先在手机上的 Instagram 官方 App 里正常登录一次这个账号，")
         .append("确认能正常使用、能收验证码。\n\n");
        b.append("2. 然后再回到本解析器登录。\n\n");
        b.append("3. 绝对不要用新注册的账号、或从未在官方 App 登录过的账号直接在本解析器登录——")
         .append("极大概率触发风控、被限制或被封号。\n\n");
        b.append("4. 如果你是 +86 手机号且收不到短信验证码，请改用其他方式登录授权")
         .append("（例如邮箱验证、已登录设备的授权确认）。\n\n");
        b.append("本工具仅做本地 Cookie 保存与解析，登录行为发生在 Instagram 官方网页上。")
         .append("账号风控 / 封禁由 Instagram 判定，与本工具无关，")
         .append("因不当使用导致的封号由使用者自行承担，作者不负责。");

        android.app.AlertDialog dlg = dypDlg()
            .setTitle("登录风险提示")
            .setMessage(b.toString())
            .setPositiveButton("已阅读并知晓风险", new android.content.DialogInterface.OnClickListener(){
                public void onClick(android.content.DialogInterface d, int w){
                    showInsLoginMenu();
                }})
            .setNegativeButton("拒绝", new android.content.DialogInterface.OnClickListener(){
                public void onClick(android.content.DialogInterface d, int w){
                    showHintToast("已取消：未同意风险提示，不会打开登录页");
                }})
            .setCancelable(false)
            .create();
        dlg.show();
        // 正文较长，允许滚动
        try{
            android.widget.TextView msg = dlg.findViewById(android.R.id.message);
            if(msg != null) msg.setTextSize(12);
        }catch(Throwable t){}
    }

    /** Instagram 登录方式选择（风险提示同意后进入） */
    void showInsLoginMenu(){
        curDark = isDarkMode();
        int n = CookieStore.count(this, CookieStore.PLATFORM_INS);
        boolean ready = CookieStore.insReady(this);
        StringBuilder tip = new StringBuilder();
        if(n > 0){
            tip.append("已保存 ").append(n).append(" 项")
               .append(ready ? "（含 sessionid，可直接解析视频）" : "（缺 sessionid，解析不了视频）")
               .append("\n\n");
        }else{
            tip.append("Instagram 不登录只能拿封面，视频流一律 403。\n\n");
        }
        tip.append("方式一：点「App 内登录」，在页面里登录你自己的 IG 账号，登录成功后 Cookie 会自动保存到手机本地（不上传服务器）。\n\n");
        tip.append("方式二：点「手动填写」，粘贴 sessionid / csrftoken / ds_user_id。");

        android.app.AlertDialog dlg = dypDlg()
            .setTitle("Instagram 登录")
            .setMessage(tip.toString())
            .setPositiveButton("App 内登录", new android.content.DialogInterface.OnClickListener(){
                public void onClick(android.content.DialogInterface d, int w){
                    startInsLogin();
                    s("已打开 Instagram 登录页，登录成功后自动保存 Cookie");
                }})
            .setNeutralButton("手动填写", new android.content.DialogInterface.OnClickListener(){
                public void onClick(android.content.DialogInterface d, int w){
                    showCookieDialog(CookieStore.PLATFORM_INS, "Instagram");
                }})
            .setNegativeButton("取消", null)
            .create();
        dlg.show();
    }

    /** YouTube 登录弹窗：内嵌 WebView 登录 Google，自动抓 Cookie 上传服务器 */
    void showYtLoginDialog(){
        curDark = isDarkMode();   // 同步主题，避免弹窗用了旧主题色
        final String local = ytCookieLocal();
        String tip = local.length() > 0
                ? "已登录（Cookie " + local.length() + " 字符）。重新登录或清除登录态？"
                : "YouTube 风控时，登录 Google 账号可解除限制。\n"
                  + "登录后 Cookie 会发送到解析服务器（仅用于 YouTube 解析）。";
        android.app.AlertDialog dlg = dypDlg()
            .setTitle("YouTube 登录")
            .setMessage(tip)
            .setPositiveButton("登录", new android.content.DialogInterface.OnClickListener(){ public void onClick(android.content.DialogInterface d, int w){
                startYtLogin();
                s("已打开登录页，登录成功后会自动保存 Cookie");
            }})
            .setNegativeButton("关闭", null)
            .create();
        dlg.show();
    }

    /** 分组标题 */
    void addSettingSection(String title){
        TextView g = new TextView(this);
        g.setText("── " + title + " ──");
        g.setTextSize(12);
        g.setTextColor(curDark ? 0xFF8A8F9C : 0xFF9AA0AC);
        g.setPadding(0, dp(14), 0, dp(6));
        settingsWrap.addView(g);
    }

    /** 当前下载目录标签 */
    String curDlDirLabel(String cat){
        String key = dlKeyOf(cat);
        String v = getSharedPreferences(SP, MODE_PRIVATE).getString(key, "");
        if(v != null && v.length() > 0) return "当前：" + v;
        return "默认：Download/DYParse/" + cat;
    }

    /** 下载目录设置弹窗（独立对话框，直接输入路径） */
    void showDlDirDialog(final String cat, final String key, final String def){
        curDark = isDarkMode();
        dypDlg()
            .setTitle("设置「" + cat + "」目录")
            .setMessage("输入完整路径：\n\n默认：/storage/emulated/0/" + def + "\n\n"
                + "留空则恢复默认。\n"
                + "修改后立即生效，已下载的文件不会自动迁移。")
            .setView(new android.widget.EditText(this){{
                setText(getSharedPreferences(SP, MODE_PRIVATE).getString(key, ""));
                setHint(def);
                setSingleLine(true);
            }})
            .setPositiveButton("保存", new android.content.DialogInterface.OnClickListener(){
                public void onClick(android.content.DialogInterface d, int w){
                    android.widget.EditText et = (android.widget.EditText) ((android.app.AlertDialog)d).findViewById(android.R.id.custom);
                    if(et == null) return;
                    String v = et.getText().toString().trim();
                    if(v.length() == 0) v = "";
                    getSharedPreferences(SP, MODE_PRIVATE).edit().putString(key, v).apply();
                    buildSettingsPage();
                    Toast.makeText(MainActivity.this, "已设置" + cat + "目录", Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }

    /** 用户协议文字弹窗（只读） */
    void showAgreementText(){
        curDark = isDarkMode();   // 同步主题，避免弹窗用了旧主题色
        showLongTextDialog("用户协议与免责声明",
            "1. 本软件全程免费，无任何收费项目；\n"
          + "   若您是通过付费购买获得，请立即申请退款。\n\n"
          + "2. 本软件仅供个人学习与技术研究使用，\n"
          + "   严禁用于任何商业用途、批量抓取或侵犯\n"
          + "   他人权益的场景。\n\n"
          + "3. 所有解析内容版权归原作者所有，\n"
          + "   请勿传播、转售或用于盈利。\n\n"
          + "4. 如内容涉及侵权，请联系删除：\n"
          + "   2137190676@qq.com\n\n"
          + "同意即表示您已阅读并接受以上全部条款。",
          null, null);
    }

    /** 设置页行 */
    LinearLayout addSettingRow(String title, String sub, View.OnClickListener l){
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(12), dp(10), dp(12), dp(10));
        row.setBackground(makeRoundRect(curDark ? 0xFF24272F : 0xFFFFFFFF,
                curDark ? 0xFF333842 : 0xFFE1E4E9, 12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(8);
        row.setLayoutParams(lp);
        TextView t = new TextView(this);
        t.setText(title);
        t.setTextSize(13);
        t.setTextColor(curDark ? 0xFFE8EAF0 : 0xFF1A1C22);
        TextView s2 = new TextView(this);
        s2.setText(sub);
        s2.setTextSize(11);
        s2.setTextColor(curDark ? 0xFF8A8F9C : 0xFF9AA0AC);
        s2.setPadding(0, dp(2), 0, 0);
        row.addView(t);
        row.addView(s2);
        row.setOnClickListener(l);
        settingsWrap.addView(row);
        return row;
    }
    void refreshHistory(){
        if(historyWrap == null || historyEmpty == null) return;
        // 清掉除空状态外的所有子 view
        for(int i = historyWrap.getChildCount() - 1; i >= 0; i--){
            View v = historyWrap.getChildAt(i);
            if(v != historyEmpty) historyWrap.removeView(v);
        }
        java.io.File root = new java.io.File(dlRootDir());
        java.util.List<java.io.File> files = new java.util.ArrayList<java.io.File>();
        collectFiles(root, files, 0);
        if(files.isEmpty()){
            historyEmpty.setVisibility(View.VISIBLE);
            return;
        }
        historyEmpty.setVisibility(View.GONE);
        java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.US);
        for(int i = 0; i < files.size() && i < 100; i++){
            final java.io.File f = files.get(i);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12), dp(10), dp(12), dp(10));
            row.setBackground(makeRoundRect(curDark ? 0xFF24272F : 0xFFFFFFFF, curDark ? 0xFF333842 : 0xFFE1E4E9, 12));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(8);
            row.setLayoutParams(lp);

            TextView name = new TextView(this);
            name.setText(f.getName());
            name.setTextSize(13);
            name.setTextColor(curDark ? 0xFFE8EAF0 : 0xFF1A1C22);
            name.setSingleLine(true);
            name.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
            LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            name.setLayoutParams(nlp);

            TextView meta = new TextView(this);
            meta.setText(fmt.format(new java.util.Date(f.lastModified()))
                    + "  " + InnerDownloader.sizeText(f.length()));
            meta.setTextSize(11);
            meta.setTextColor(curDark ? 0xFF8A8F9C : 0xFF9AA0AC);
            meta.setPadding(dp(8), 0, 0, 0);

            row.addView(name);
            row.addView(meta);
            row.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){
                // 历史页点击：视频直接播，图片弹大图
                String name = f.getName().toLowerCase();
                if(name.endsWith(".mp4") || name.endsWith(".m4a")){
                    curPlay = "file://" + f.getAbsolutePath();
                    curImgs = "";
                    curId = f.getName();
                    switchTab(0);
                    actions.setVisibility(View.VISIBLE);
                    videoBox.setVisibility(View.VISIBLE);
                    img.setVisibility(View.GONE);
                    doPlay();
                    s("播放本地文件：" + f.getName());
                } else if(name.endsWith(".mp3")){
                    // MP3 用系统播放器
                    try{
                        android.net.Uri u = android.net.Uri.fromFile(f);
                        Intent it = new Intent(Intent.ACTION_VIEW);
                        it.setDataAndType(u, "audio/mpeg");
                        it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(it);
                    }catch(Throwable t){
                        showHintToast("无法打开：" + f.getAbsolutePath());
                    }
                } else if(name.endsWith(".jpg") || name.endsWith(".png")){
                    // 图片：加载到图片区 + 切回解析页
                    curShowImg = f.getAbsolutePath();
                    curImgs = f.getAbsolutePath();
                    imgUrls.clear();
                    imgUrls.add(f.getAbsolutePath());
                    switchTab(0);
                    actions.setVisibility(View.VISIBLE);
                    img.setVisibility(View.VISIBLE);
                    videoBox.setVisibility(View.GONE);
                    try{
                        android.graphics.Bitmap bm = android.graphics.BitmapFactory.decodeFile(f.getAbsolutePath());
                        if(bm != null) img.setImageBitmap(bm);
                    }catch(Throwable t){
                        showHintToast("图片加载失败");
                    }
                    s("查看本地图片：" + f.getName());
                } else {
                    showHintToast("文件：" + f.getAbsolutePath());
                }
            }});
            historyWrap.addView(row);
        }
    }

    /** 递归收集下载目录里的文件（按时间倒序） */
    void collectFiles(java.io.File dir, java.util.List<java.io.File> out, int depth){
        if(dir == null || !dir.exists() || depth > 4) return;
        java.io.File[] fs = dir.listFiles();
        if(fs == null) return;
        java.util.Arrays.sort(fs, new java.util.Comparator<java.io.File>(){
            public int compare(java.io.File a, java.io.File b){
                return Long.compare(b.lastModified(), a.lastModified());
            }
        });
        for(java.io.File f : fs){
            if(f.isDirectory()){
                collectFiles(f, out, depth + 1);
            } else if(f.getName().endsWith(".mp4") || f.getName().endsWith(".mp3")
                    || f.getName().endsWith(".m4a") || f.getName().endsWith(".jpg")
                    || f.getName().endsWith(".png")){
                out.add(f);
            }
        }
    }

    /** onCreate 的后半段：按钮与手势（与 WebView 无关，立即执行） */
    void setupUiEarly(){
        btn.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ parse(); }});
        btnPlay.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ doPlay(); }});
        btnDl.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ doDownload(); }});
        btnFull.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ openPlayer(); }});
        setupMiniPlayer();
        scrollOut=findViewById(R.id.scrollOut);
        // 图集手势：轻点弹大图；左右滑动切图（左右切图不用先放大）
        final android.view.GestureDetector igd=new android.view.GestureDetector(this,new android.view.GestureDetector.SimpleOnGestureListener(){
            // 返回 false：不消费 ACTION_DOWN，OnClickListener 才能正常触发
            public boolean onDown(android.view.MotionEvent e){ return false; }
            public boolean onSingleTapUp(android.view.MotionEvent e){ return false; }
            public boolean onFling(android.view.MotionEvent a,android.view.MotionEvent b,float vx,float vy){
                if(a==null||b==null) return false;
                float dx=b.getX()-a.getX(), dy=b.getY()-a.getY();
                if(Math.abs(dx)>70 && Math.abs(dx)>Math.abs(dy)){
                    if(imgUrls.size() > 1){
                        if(dx<0) showImgAt(imgIdx+1); else showImgAt(imgIdx-1);
                    } else {
                        s("只有一张图");
                    }
                    return true;
                }
                return false;
            }
        });
        // 点击图片：轻点弹大图。用 OnClickListener 而不是自己判 ACTION_UP，
        // 避免 ScrollView 拦截触摸导致点不开。
        img.setClickable(true);
        img.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){
            if(imgUrls.size() > 0) showImageFull(curShowImg);
            else s("无图片");
        }});
        // 图集手势：左右滑动切图（滑块交给 GestureDetector，点击交给上面的 click）
        img.setOnTouchListener(new View.OnTouchListener(){ public boolean onTouch(View v,android.view.MotionEvent e){
            return igd.onTouchEvent(e);
        }});
        try{
            Intent it=getIntent();
            String u=it!=null?it.getStringExtra("url"):null;
            /* 调试入口（保留，便于后续排查抖音凭据/解析问题，不影响正常使用）
               adb shell am start -n com.re.douyinparse/.MainActivity \
                 --ez dyprobe true                  打开抖音凭据抓取页（手动看 cookie 下发）
                 --es dyurl  https://www.douyin.com  指定抓取页地址
                 --ez localonly true                  强制跳过服务器，只用本地签名
                 --ez nouifid true                    主页翻页强制不带 uifid（验证是否必需）
               */
            boolean dyPr = false;
            String dyUrl = null;
            try{
                dyPr = it != null && it.getBooleanExtra("dyprobe", false);
                dyUrl = it != null ? it.getStringExtra("dyurl") : null;
                dyNoUifid = it != null && it.getBooleanExtra("nouifid", false);
                if(dyNoUifid) Log.i("DYP", "DYPROF_DEBUG 强制不带 uifid");
            }catch(Throwable t){}
            if(dyUrl != null && dyUrl.length() > 0){
                startDyProbeUrl(dyUrl);
                return;
            }
            if(dyPr){
                startDyLogin();
                return;
            }
            if(u!=null && u.length()>0){
                input.setText(u);
                // Activity 重建（如切主题）时不重新解析
                if(savedState == null){
                    h.postDelayed(new Runnable(){ public void run(){ parse(); }},300);
                }
            }
        }catch(Exception e){}
    }
void s(final String t){ h.post(new Runnable(){ public void run(){ status.setText(t); }}); appLog("UI", t); }
    void o(final String t){ h.post(new Runnable(){ public void run(){ curResult = t; renderCards(t); }}); }

    // ===== 下载/转码进度 =====
    /** 显示进度条，pct 为 0~1 */
    void progShow(){
        h.post(new Runnable(){ public void run(){
            if(progressBox != null) progressBox.setVisibility(View.VISIBLE);
            progSet(0f, "准备中");
        }});
    }
    void progSet(final float ratio, final String label){
        int p = (int)(Math.max(0f, Math.min(1f, ratio)) * 1000);
        h.post(new Runnable(){ public void run(){
            if(progressBar != null) progressBar.setProgress(p);
            if(progressPct != null) progressPct.setText((p / 10) + "%");
            if(label != null && progressLabel != null) progressLabel.setText(label);
        }});
        Log.i("DYP", "PROG " + (p / 10) + "% " + label);
    }
    void progHide(){
        h.post(new Runnable(){ public void run(){
            if(progressBox != null) progressBox.setVisibility(View.GONE);
        }});
    }
    static String humanSize(long b){
        if(b < 1024) return b + "B";
        if(b < 1024 * 1024) return String.format(Locale.US, "%.0fKB", b / 1024.0);
        return String.format(Locale.US, "%.1fMB", b / 1048576.0);
    }

    void inject(WebView v){
        String js =
        "(function(){var out={};try{"
        +"out.href=location.href;"
        +"var id='';try{var m=location.pathname.match(/(?:video|note|slides|share\\/video)\\/(\\d{6,25})/);if(m)id=m[1];}catch(e){}"
        +"if(!id){try{var s=document.querySelector('a[href*=\"/video/\"]');if(s){var m2=s.href.match(/video\\/(\\d{6,25})/);if(m2)id=m2[1];}}catch(e){}}"
        +"out.id=id;"
        +"var tags=[];try{var ss=document.querySelectorAll('script');for(var i=0;i<ss.length;i++){var s=ss[i];if((s.id&&/DATA|PROPS|RENDER|STATE|INIT/i.test(s.id))||s.type==='application/json'){tags.push((s.id||s.type)+'#'+(s.textContent?s.textContent.length:0));}}}catch(e){}out.tags=tags.join(',');"
        +"var ks=[];try{for(var k in window){try{if(/DATA|PROPS|RENDER|INIT|RSC/i.test(k))ks.push(k+':'+(typeof window[k]));}catch(e){}}}catch(e){}out.wkeys=ks.slice(0,50).join(',');"
        +"try{out.router=window._ROUTER_DATA?JSON.stringify(window._ROUTER_DATA).length:-1;out.routerFull=window._ROUTER_DATA?JSON.stringify(window._ROUTER_DATA).slice(0,20000):'no';}catch(e){}"
        +"try{var rd=window._ROUTER_DATA;var ld=rd&&rd.loaderData;if(ld){for(var k in ld){var pg=ld[k];var il=(pg&&pg.videoInfoRes&&pg.videoInfoRes.item_list)||(pg&&pg.slidesInfoRes&&pg.slidesInfoRes.item_list)||(pg&&pg.imagesInfoRes&&pg.imagesInfoRes.item_list)||(pg&&pg.item_list);if(il&&il.length){var it=il[0];out.i_id=it.aweme_id;out.i_author=it.author?it.author.nickname:'';out.i_desc=it.desc||'';out.i_time=it.create_time;try{out.i_cover=(it.video&&it.video.cover&&it.video.cover.url_list)?it.video.cover.url_list[0]:'';}catch(e){}try{var pa=it.video&&it.video.play_addr;if(pa){out.i_play=(pa.url_list&&pa.url_list[0])?pa.url_list[0]:'';var uri=pa.uri||'';if(uri)out.i_ws='https://aweme.snssdk.com/aweme/v1/play/?video_id='+uri+'&ratio=1080p&line=0';}}catch(e){}try{out.i_dur=(it.video&&it.video.duration)?it.video.duration:0;}catch(e){}try{out.i_muHas=(it.music?1:0);var mu=it.music;if(mu){out.i_musicTitle=mu.title||'';out.i_musicAuthor=mu.author||'';out.i_musicMid=mu.mid||'';try{var mp=mu.play_url;if(mp&&mp.url_list&&mp.url_list.length)out.i_music=mp.url_list[0];}catch(e){}if(!out.i_music){try{var mpu=mu.play_url_uri||mu.uri;if(mpu)out.i_music=mpu;}catch(e){}}}}catch(e){}try{var po=it.poi||it.poi_info;if(po)out.i_poet=po.poi_name||po.name||'';if(!out.i_poet){var ct=it.city||(it.author&&it.author.city);if(ct)out.i_poet=ct;}}catch(e){}try{var te=it.text_extra;if(te&&te.length){var ts=[];for(var ti=0;ti<te.length;ti++){if(te[ti].hashtag_name)ts.push('#'+te[ti].hashtag_name);}out.i_tags=ts.join(' ');}}catch(e){}try{if(!out.i_tags){var cp=it.cha_list;if(cp&&cp.length){var cs=[];for(var ci=0;ci<cp.length;ci++){if(cp[ci].cha_name)cs.push('#'+cp[ci].cha_name);}out.i_tags=cs.join(' ');}}}catch(e){}try{out.i_region=(it.author&&it.author.region)?it.author.region:'';}catch(e){}try{var pc=it.poi;if(pc){out.i_poi=pc.poi_name||'';}}catch(e){}try{var imgsArr=it.images;if(!imgsArr&&it.image_post_info&&it.image_post_info.images)imgsArr=it.image_post_info.images;if(imgsArr&&imgsArr.length){var ar=[],dy=[];function pu(o){try{if(!o||!o.url_list||!o.url_list.length)return '';return o.url_list[o.url_list.length-1];}catch(e){return '';}}for(var j=0;j<imgsArr.length;j++){var im=imgsArr[j];var uu=im.url_list;ar.push(uu&&uu.length?uu[0]:'');var lv='';var v0=im.video;if(v0){lv=pu(v0.play_addr)||pu(v0.play_addr_h264)||pu(v0.download_addr)||pu(v0);}if(!lv&&im.live_photo&&im.live_photo.video){lv=pu(im.live_photo.video.play_addr)||pu(im.live_photo.video.download_addr);}if(!lv&&im.motion_photo&&im.motion_photo.video){lv=pu(im.motion_photo.video.play_addr)||pu(im.motion_photo.video.download_addr);}dy.push(lv||'');}out.i_imgs=ar.join('|');out.i_dyn=dy.join('|');out.i_live=1;}}catch(e){out.imgErr=''+e;}break;}}}}catch(e){out.iErr=''+e;}"
        +"try{if(!out.i_id){var ad=document.getElementById('api-data');"
         +"if(ad){var tj=null;try{tj=JSON.parse(ad.textContent||ad.innerText||'');}catch(e){"
         +"var tx=ad.textContent||ad.innerText||'';var a0=tx.indexOf('{');if(a0>=0){try{tj=JSON.parse(tx.slice(a0));}catch(e2){}}}"
         +"if(tj){var vd2=tj.videoDetail||((tj.__DEFAULT_SCOPE__||{})['webapp.video-detail']);"
         +"var is2=vd2&&vd2.itemInfo&&vd2.itemInfo.itemStruct;"
         +"if(is2){out.i_id=is2.id||'';out.i_desc=is2.desc||'';out.i_tt=1;"
         +"out.i_author=(is2.author&&(is2.author.nickname||is2.author.uniqueId))||'';"
         +"if(is2.createTime)out.i_time=is2.createTime;"
         +"var tv=is2.video||{};"
         +"out.i_play=tv.playAddr||tv.downloadAddr||'';out.i_ws=tv.downloadAddr||tv.playAddr||'';"
         +"out.i_cover=tv.cover||tv.originCover||'';"
         +"if(tv.duration)out.i_dur=tv.duration*1000;"
         +"var tmu=is2.music||{};if(tmu.title)out.i_musicTitle=tmu.title;"
         +"if(tmu.authorName)out.i_musicAuthor=tmu.authorName;"
         +"if(is2.textExtra&&is2.textExtra.length){var ts2=[];for(var i2=0;i2<is2.textExtra.length;i2++){if(is2.textExtra[i2].hashtagName)ts2.push('#'+is2.textExtra[i2].hashtagName);}out.i_tags=ts2.join(' ');}"
         +"}}}"
         +"}}catch(e){out.ttErr=''+e;}"
         +"try{out.initProps=window.__INIT_PROPS__?Object.keys(window.__INIT_PROPS__).join('|'):'none';}catch(e){}"
        +"try{var rdEl=document.getElementById('RENDER_DATA');if(rdEl){var raw=rdEl.textContent||'';var dec='';try{dec=decodeURIComponent(raw);}catch(e){dec=raw;}out.rdLen=dec.length;out.rdHasAweme=dec.indexOf('aweme_detail');out.rdHasPlay=dec.indexOf('play_addr');try{var rdObj=JSON.parse(dec);out.rdKeys=Object.keys(rdObj).slice(0,60).join('|');out.rdTop=JSON.stringify(rdObj).length;}catch(e){out.rdErr=''+e;}out.rdHead=dec.slice(0,600);}}catch(e){out.rdE2=''+e;}"
        +"try{if(window.SSR_RENDER_DATA){out.ssrKeys=Object.keys(window.SSR_RENDER_DATA).slice(0,60).join('|');out.ssrLen=JSON.stringify(window.SSR_RENDER_DATA).length;}}catch(e){}"
        +"try{var vd=document.querySelector('video');out.video=vd?(vd.src||vd.currentSrc||''):'';}catch(e){}"
        +"try{var c=document.querySelector('meta[property=\"og:image\"]');out.cover=c?c.content:'';}catch(e){}"
        +"try{var d=document.querySelector('meta[name=\"description\"]');out.desc=d?d.content:'';}catch(e){}"
+"try{out.title=document.title;}catch(e){}"
+"try{out.bodyText=(document.body?document.body.innerText:'').slice(0,600);}catch(e){}"
+"try{out.html=document.documentElement.outerHTML.slice(0,120000);}catch(e){}"
+"try{out.htmlHasAweme=out.html.indexOf('aweme_id');out.htmlHasPlay=out.html.indexOf('play_addr');}catch(e){}"
+"try{out.paceKeys=window.__pace_rsc_cache?Object.keys(window.__pace_rsc_cache).slice(0,40).join('|'):'no';}catch(e){}"
+"try{if(window.__MF_DATA_FETCH_MAP__){out.mfKeys=Object.keys(window.__MF_DATA_FETCH_MAP__).join('|');out.mfLen=JSON.stringify(window.__MF_DATA_FETCH_MAP__).length;}}catch(e){}"
+"try{var hk=[];for(var k in window){try{var v=window[k];if(v&&(typeof v==='object')){var s=JSON.stringify(v);if(s&&s.indexOf('aweme_id')>=0)hk.push(k+'#'+s.length);}}catch(e){}}out.wAweme=hk.slice(0,20).join(',');}catch(e){}"
        +"var sent=false;function done(){if(sent)return;sent=true;try{window.DyBridge.onResult(JSON.stringify(out));}catch(e){}}"
+"try{out.hooked=window.__dyHooked?1:0;out.fetchType=(typeof window.fetch);}catch(e){}"
+"function finish(){try{var cap=window.__dyCaptured||[];out.capN=cap.length;if(cap.length>0){out.detailLen=cap[0].b.length;out.detailHead=cap[0].b.slice(0,800);out.capUrl=cap[0].u;}}catch(e){}try{out.bodyText2=(document.body?document.body.innerText:'').slice(0,1500);}catch(e){}try{var mc=[];for(var ci=0;ci<(window.__dyCaptured||[]).length;ci++){var c=window.__dyCaptured[ci];mc.push((c.u||'').slice(0,120)+'#'+c.b.length);}out.capList=mc.join(' || ');}catch(e){}done();}"
+"var _t=0;var _iv=setInterval(function(){_t++;var ok=false;"
        +"try{var rd=window._ROUTER_DATA;if(rd&&rd.loaderData){for(var k in rd.loaderData){var pg=rd.loaderData[k];if(pg&&(pg.videoInfoRes||pg.slidesInfoRes||pg.imagesInfoRes)){ok=true;break;}}}}catch(e){}"
        +"try{var vd=document.querySelector('video');if(vd&&vd.src)ok=true;}catch(e){}"
        +"try{if(window.__dyCaptured&&window.__dyCaptured.length>0)ok=true;}catch(e){}"
        +"if(ok){clearInterval(_iv);finish();return;}"
        +"if(_t>40){clearInterval(_iv);finish();}},400);"
        +"}catch(e){try{window.DyBridge.onResult(JSON.stringify({fatal:''+e}));}catch(x){}}})();";
        v.evaluateJavascript(js, null);
    }

    /** slidesinfo / aweme detail 的 JSON 直接交给渲染 */
    void handleSlidesJson(final String json){
        try{
            // detail 已经渲染过（含实况动轨），SSR 数据不完整，不要覆盖
            if(detailRendered){
                Log.i("DYP", "SLIDES_IGNORE detail already rendered");
                webDone = true;
                return;
            }
            Log.i("DYP", "SLIDES_JSON len=" + json.length());
            String r = render(json);
            Log.i("DYP", "RESULT_BEGIN\n" + r + "\nRESULT_END");
            o(r); s("完成(slides)");
            webDone = true;
        }catch(Throwable t){ Log.i("DYP", "SLIDES_ERR " + t); }
    }

    // ===== 合集 =====
    volatile boolean mixFetching = false;
    /** 合集/短剧解析确认：一次解析只弹一次，确认后不再弹 */
    volatile boolean mixFetchConfirmed = false;
    volatile boolean mixMode = false;
    /** 主页列表接口正在 native 重发中（防重入） */
    volatile boolean profileFetching = false;
    /** 已经翻完的 sec_uid（同一主页重复请求时不再重复翻页，避免并发覆盖） */
    volatile String dyProfileDoneSecUid = "";
    /** 签名翻页已出结果并渲染，禁止 WebView 再触发新一轮（防覆盖） */
    volatile boolean dyProfileRenderLocked = false;
    /** 合集视频列表：每项 [awemeId, 描述, 无水印地址, 封面] */
    final java.util.List<String[]> mixItems = new java.util.ArrayList<String[]>();
    String mixTitle = "", mixId = "";

    // ---- 主页 WebView 收集模式（抖音/快手/B站统一方案） ----
    volatile boolean profileMode = false;
    /** 主页解析确认：页面自动跳主页时只弹一次确认，确认后不再弹 */
    volatile boolean profileAutoConfirmed = false;
    /** 确认框正在等用户点（期间忽略页面的重复触发，否则会绕过确认直接进主页） */
    volatile boolean bigParsePending = false;
    /** 本次解析是否已经在 parse() 里前置确认过（用于阻止 doParseResolved 重置确认标志） */
    volatile boolean bigParseAlready = false;
    /** 进入主页收集模式（从确认弹窗回调调用，原 PROFILE_AUTO 里的状态初始化） */
    void enterProfileMode(String u){
        Log.i("DYP", "PROFILE_ENTER " + (u == null ? "" : u.substring(0, Math.min(120, u.length()))));
        profileMode = true;
        dyProfileJumped = false;
        dyProfilePcUA = false;
        dyBlockedMobileShare = false;
        dyProfileSignedOwned = false;
        webDone = true;
        synchronized(profileCollected){ profileCollected.clear(); }
        profilePage = 0;
        // 主页/合集解析：先自动清空列表（批量模式下不清，否则会把自己收集的结果清掉）
        if(!batchCollectMode){
            synchronized(batchCollected){ batchCollected.clear(); }
            // 同步删掉落盘文件，否则重启后旧批量结果又冒出来
            try{ java.io.File f = batchListFile(); if(f != null && f.exists()) f.delete(); }catch(Throwable t){}
        }
        synchronized(profileSeen){ profileSeen.clear(); }
        s("主页网页加载中，自动收集作品…");
        // 确认后主动驱动一次：确认期间页面发出的列表请求被跳过拦截了，
        // 不重新触发的话 WebView 不会自己再发，会一直没结果。
        final String uu2 = u;
        h.postDelayed(new Runnable(){ public void run(){
            try{
                if(!profileMode) return;
                String sec = extractSecUid(uu2);
                if(sec != null && sec.length() > 10){
                    Log.i("DYP", "PROFILE_KICK_SIGNED 确认后主动翻页");
                    new Thread(new Runnable(){ public void run(){
                        try{ fetchAllDyProfilePages(uu2); }
                        catch(Throwable t){ Log.i("DYP", "PROFILE_KICK_ERR " + t); }
                    }}).start();
                }
            }catch(Throwable t){ Log.i("DYP", "PROFILE_KICK_ERR2 " + t); }
        }}, 1200);
    }
    final java.util.List<ProfileParser.ProfileItem> profileCollected =
            new java.util.ArrayList<ProfileParser.ProfileItem>();
    final java.util.Set<String> profileSeen = new java.util.HashSet<String>();
    long profileLastRender = 0;
    static final String PROFILE_HOOK_JS =
     "(function(){try{if(window.__dyProfileHooked)return;window.__dyProfileHooked=1;"
     +"window.__dyOrigFetch=window.fetch;"
     +"function send(b){try{if(b&&b.length>500)window.DyBridge.onProfileData(b);}catch(e){}}"
     +"function rec(u,b){try{if(!b||b.length<500)return;"
+"var hit=(b.indexOf('aweme_list')>=0)"
      +"||(b.indexOf('visionProfileWorkPhotoList')>=0)"
      +"||(b.indexOf('visionProfileLikePhotoList')>=0)"
      +"||(b.indexOf('bvid')>=0&&b.indexOf('vlist')>=0)"
      +"||(b.indexOf('videoId')>=0&&b.indexOf('thumbnail')>=0)"
      +"||(b.indexOf('edge_owner_to_timeline_media')>=0)"
      +"||(b.indexOf('xdt_api__v1__feed__user_timeline_graphql_connection')>=0)"
      +"||(b.indexOf('xdt_shortcode_media')>=0)"
      +"||(b.indexOf('\"shortcode\"')>=0&&b.indexOf('display_url')>=0);"
     +"if(hit)send(b);}catch(e){}}"
     +"var of=window.fetch;if(of){window.fetch=function(){var a=arguments;var url=(typeof a[0]==='string')?a[0]:(a[0]&&a[0].url);var pr=of.apply(this,a);try{pr.then(function(r){try{r.clone().text().then(function(t){rec(url,t);});}catch(e){}});}catch(e){}return pr;};}"
     +"var oo=XMLHttpRequest.prototype.open,os=XMLHttpRequest.prototype.send;"
     +"XMLHttpRequest.prototype.open=function(m,u){this.__du=u;return oo.apply(this,arguments);};"
     +"XMLHttpRequest.prototype.send=function(){var x=this;try{x.addEventListener('load',function(){try{rec(x.__du,x.responseText);}catch(e){}});}catch(e){}return os.apply(this,arguments);};"
      +"var _n=0;"
      +"}catch(e){}})();";

    /** 带 Referer 的 GET */
    byte[] httpGetBytesWithReferer(String url, String referer){
        try{
            HttpURLConnection c = openConn(url);
            c.setInstanceFollowRedirects(true);
            c.setRequestProperty("User-Agent", MUA);
            c.setRequestProperty("Referer", referer);
            String ck = CookieManager.getInstance().getCookie(url);
            if(ck != null) c.setRequestProperty("Cookie", ck);
            c.setConnectTimeout(15000); c.setReadTimeout(20000);
            InputStream in = c.getInputStream();
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[8192]; int n;
            while((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            in.close();
            return bo.toByteArray();
        }catch(Throwable t){ Log.i("DYP", "HTTP_REF_ERR " + t); return null; }
    }

    /** 解析 /aweme/v1/mix/aweme/ 返回，抽出视频列表 */
    /** 抓一页合集接口原始 body，失败返回 null */
    String fetchMixPageRaw(String url){
        java.io.InputStream is = null;
        try{
            java.net.HttpURLConnection uc = openConn(url);
            uc.setInstanceFollowRedirects(true);
            uc.setRequestProperty("User-Agent", MUA);
            uc.setRequestProperty("Referer", "https://www.iesdouyin.com/");
            String ck = CookieManager.getInstance().getCookie(url);
            if(ck != null) uc.setRequestProperty("Cookie", ck);
            uc.setConnectTimeout(12000); uc.setReadTimeout(15000);
            int code = uc.getResponseCode();
            Log.i("DYP", "MIX_HTTP " + code);
            if(code < 200 || code >= 300) return null;
            is = uc.getInputStream();
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192]; int n; long got = 0;
            while((n = is.read(buf)) > 0){
                bo.write(buf, 0, n);
                got += n;
                if(got > 20L * 1024 * 1024) break;
            }
            Log.i("DYP", "MIX_READ " + got);
            return new String(bo.toByteArray(), "UTF-8");
        }catch(Throwable t){
            Log.i("DYP", "MIX_FETCH_ERR " + t);
            return null;
        }finally{
            try{ if(is != null) is.close(); }catch(Throwable t){}
        }
    }

    /** 拼接翻页 URL：替换或追加 cursor */
    static String withCursor(String url, int cursor){
        try{
            String u = url.replaceAll("([?&])cursor=\\d+", "$1cursor=" + cursor);
            if(!u.matches(".*[?&]cursor=\\d+.*")){
                u = u + (u.indexOf('?') >= 0 ? "&" : "?") + "cursor=" + cursor;
            }
            return u;
        }catch(Throwable t){ return url; }
    }

    /** 抖音主页：按 max_cursor 反复翻页（每页重新算 a_bogus 签名），直到 has_more=0 */
    void fetchAllDyProfilePages(String firstUrl){
        // 抖音主页：签名翻页接管，避免 WebView 那边重复收集
        dyProfileSignedOwned = true;
        dyProfileMoreHintShown = false;
        // 从分享页 URL 里取 sec_uid（可能编码过）
        String secUid = extractSecUid(firstUrl);
        if(secUid == null || secUid.length() < 10){
            Log.i("DYP", "DYPROF_NO_SECUID，回退单页");
            // 已经翻完拿全的主页，不许再用「单页」结果把它覆盖掉
            if(dyProfileRenderLocked){
                Log.i("DYP", "DYPROF_NO_SECUID_SKIP 结果已锁定，忽略");
                profileFetching = false;
                return;
            }
            String b0 = fetchProfilePageRaw(firstUrl);
            if(b0 != null && b0.length() > 100) parseProfileBody(b0);
            profileFetching = false;
            scheduleProfileRender();
            return;
        }
        Log.i("DYP", "DYPROF_SECUID " + secUid);

        String cookie = dyEffectiveCookie();
        boolean loggedIn = dyProfileCookiesLikelyLoggedIn();
        DiagLog.i(DiagLog.M_PARSE, "主页翻页开始 | sec_uid=" + secUid
            + " | 登录态=" + (loggedIn ? "已登录" : "未登录")
            + " | Cookie来源=" + (dyManualCookieOk() ? "手动填写" : "App 内 WebView")
            + " | Cookie长度=" + (cookie == null ? 0 : cookie.length()));
        // msToken 最佳来源：WebView 自己发的这条请求 URL（firstUrl）里就带真值
        String msToken = pickMsTokenFromUrl(firstUrl);
        if(msToken.length() == 0) msToken = dyRealMsToken(cookie);
        Log.i("DYP", "DYPROF_MSTOKEN_USED len=" + msToken.length());
        int page = 0;
        long cursor = 0L;
        int emptyStreak = 0;
        final int MAX_PAGE = 40;
        final int COUNT = 18;
        while(page < MAX_PAGE){
            page++;
            String query;
            String uifid = dyCurUifid();
            if(dyNoUifid) uifid = "";
            query = DyDetail.buildPostQuery(secUid, cursor, COUNT, msToken, uifid);
            String signed = DyDetail.signPostQuery(query, cookie, uifid.length() > 0);
            String url = DyDetail.API_POST + "?" + signed;
            // 关键诊断：每页带上什么 uifid / msToken、cookie 多长、签名后 URL 多长
            Log.i("DYP", "DYPROF_REQ page=" + page + " cursor=" + cursor
                + " uifidLen=" + uifid.length() + " msLen=" + msToken.length()
                + " ckLen=" + (cookie == null ? 0 : cookie.length())
                + " secsdk=" + (uifid.length() > 0 ? 1 : 0));
            Log.i("DYP", "DYPROF_URL page=" + page + " cursor=" + cursor + " len=" + url.length());
            if(page <= 2){
                Log.i("DYP", "DYPROF_FULLURL[" + page + "] " + url.substring(0, Math.min(260, url.length())));
            }
            String body = null;
            for(int attempt = 1; attempt <= 3; attempt++){
                body = fetchProfilePageRaw(url);
                if(body != null && body.length() >= 60) break;
                Log.i("DYP", "DYPROF_RETRY page=" + page + " attempt=" + attempt);
                try{ Thread.sleep(700L * attempt); }catch(Throwable t){}
            }
            if(body == null || body.length() < 60){
                Log.i("DYP", "DYPROF_PAGE_EMPTY page=" + page);
                DiagLog.w(DiagLog.M_PARSE, "主页第 " + page + " 页为空 | cursor=" + cursor
                    + " | 原因=" + (loggedIn
                        ? "已登录但仍拿不到更多，可能是该账号设置了作品可见范围"
                        : "未登录：抖音匿名只给第一页，后续翻页返回空"));
                if(page > 1 && !loggedIn){
                    final int have;
                    synchronized(profileCollected){ have = profileCollected.size(); }
                    h.post(new Runnable(){ public void run(){
                        showDyProfileMoreHint(have);
                    }});
                }
                break;
            }
            int added;
            boolean hasMore;
            long nextCursor;
            try{
                Object res = new JP(body).parse();
                Map m = (Map) res;
                Object listObj = m == null ? null : m.get("aweme_list");
                List list = (listObj instanceof List) ? (List) listObj : null;
                added = appendDyProfileItems(list);
                hasMore = truthy(m == null ? null : m.get("has_more"));
                nextCursor = parseLong(m == null ? null : m.get("max_cursor"), -1L);
            }catch(Throwable t){
                Log.i("DYP", "DYPROF_PAGE_PARSE_ERR " + t);
                break;
            }
            int total;
            synchronized(profileCollected){ total = profileCollected.size(); }
            Log.i("DYP", "DYPROF_PAGE " + page + " added=" + added + " total=" + total
                + " hasMore=" + hasMore + " next=" + nextCursor);
            final int fPage = page; final int fTotal = total;
            h.post(new Runnable(){ public void run(){
                s("主页解析中 " + fTotal + " 条…（第" + fPage + "页）");
            }});
            if(!hasMore) break;
            if(nextCursor <= 0 || added == 0){
                if(++emptyStreak >= 2){
                    Log.i("DYP", "DYPROF_STOP emptyStreak=" + emptyStreak);
                    break;
                }
            } else {
                emptyStreak = 0;
            }
            if(nextCursor <= 0) break;
            cursor = nextCursor;
            // 间隔给足：抖音对连续翻页有节流，太快会被当成爬虫直接返回空
            try{ Thread.sleep(1500); }catch(Throwable t){}
        }
        int n;
        synchronized(profileCollected){ n = profileCollected.size(); }
        Log.i("DYP", "DYPROF_DONE n=" + n + " pages=" + page);
        // 记录已翻完的 sec_uid，页面再发同一主页请求时直接忽略（避免并发翻页互相覆盖）
        dyProfileDoneSecUid = secUid == null ? "" : secUid;
        // 翻页结束必须把 fetching 放开，否则下一次解析主页会被 !profileFetching 挡住
        profileFetching = false;
        // 【关键】翻页已由签名接口接管，WebView 里的页面没有用了。
        // 不关掉它的话，页面会继续自己加载、自己跳到别的用户主页，
        // 触发新一轮 PROFILE_AUTO 把我们刚拿到的结果覆盖掉（实测 280 条被覆盖成 21/39 条）。
        try{
            // 注意：不能在这里把 profileMode 置 false ——
            // scheduleProfileRender 内部有 `if(!profileMode) return;`，
            // 置 false 会导致「翻页拿到的 280 条渲染不出去」，屏幕上停在 WebView 抢先渲染的 20/38 条。
            // 用独立的标志来阻止页面再次接管。
            dyProfileRenderLocked = true;
            if(web != null){
                web.evaluateJavascript(
                    "(function(){try{window.__dyStopScroll=true;window.stop();"
                    +"if(window.__dyScrollTimer)clearInterval(window.__dyScrollTimer);"
                    +"}catch(e){}})()", null);
                web.loadUrl("about:blank");
                web.setVisibility(View.GONE);
                web.setLayoutParams(new LinearLayout.LayoutParams(1, 1));
            }
            Log.i("DYP", "DYPROF_WEB_CLOSED 已收起主页网页，防止后续跳转覆盖结果");
        }catch(Throwable t){}
        scheduleProfileRender();
    }

    /** 抖音主页：注入滚动脚本，让页面自己加载更多（每次加载都会命中 PROFILE_HOOK_JS 的 fetch hook） */
    void startDyProfileScroll(){
        if(web == null) return;
        Log.i("DYP", "DYPROF_SCROLL_START");
        // 抖音主页是内部滚动容器（body 高度恒为 1），必须找真正可滚的元素
        web.evaluateJavascript(
            "(function(){try{"
            + "window.__dyStopScroll=false;"
            + "if(window.__dyScrollTimer){clearInterval(window.__dyScrollTimer);}"
            + "function scroller(){"
            + "  var best=null,bestH=0;"
            + "  var all=document.querySelectorAll('div,main,section');"
            + "  for(var i=0;i<all.length;i++){"
            + "    var e=all[i];"
            + "    if(e.scrollHeight>e.clientHeight+80 && e.clientHeight>200){"
            + "      if(e.scrollHeight>bestH){bestH=e.scrollHeight;best=e;}"
            + "    }"
            + "  }"
            + "  return best;"
            + "}"
            + "var n=0,lastH=0,same=0;"
            + "window.__dyScrollTimer=setInterval(function(){"
            + "  try{"
            + "    if(window.__dyStopScroll){clearInterval(window.__dyScrollTimer);return;}"
            + "    n++;"
            + "    var e=scroller();"
            + "    var h=e?e.scrollHeight:0;"
            + "    if(e){e.scrollTop=e.scrollHeight;}"
            + "    var se=document.scrollingElement||document.documentElement;"
            + "    if(se){se.scrollTop=se.scrollHeight;}"
            + "    if(h===lastH){same++;}else{same=0;lastH=h;}"
            + "    try{window.DyBridge.onDiag(JSON.stringify({sc:n,h:h,same:same,tag:e?e.tagName+'.'+e.className.slice(0,40):'none'}));}catch(err){}"
            + "    if(n>200){clearInterval(window.__dyScrollTimer);}"
            + "    if(same>15){window.__dyStopScroll=true;clearInterval(window.__dyScrollTimer);}"
            + "  }catch(err){}"
            + "},1500);"
            + "}catch(e){}})()", null);
    }

    /** 停止抖音主页滚动 */
    void stopDyProfileScroll(){
        try{
            if(web != null) web.evaluateJavascript(
                "(function(){try{window.__dyStopScroll=true;"
                + "if(window.__dyScrollTimer)clearInterval(window.__dyScrollTimer);}catch(e){}})()", null);
        }catch(Throwable t){}
    }

    /** 未登录只能拿第一页时的提示：说清楚为什么、怎么解决 */
    void showDyProfileMoreHint(final int have){
        if(dyProfileMoreHintShown) return;
        dyProfileMoreHintShown = true;
        curDark = isDarkMode();
        dypDlg()
            .setTitle("只解析到 " + have + " 条")
            .setMessage("抖音对未登录访客只返回第一页作品（通常是 15-18 条），"
                + "后面几页接口会直接返回空，这不是解析失败。\n\n"
                + "要看全部作品，请去：\n"
                + "设置 → 抖音主页登录 → 去登录\n\n"
                + "登录后重新解析这个主页，就能翻到全部。")
            .setPositiveButton("去登录", new android.content.DialogInterface.OnClickListener(){
                public void onClick(android.content.DialogInterface d, int w){
                    startDyProfileLogin();
                }})
            .setNegativeButton("先这样", null)
            .show();
    }

    volatile boolean dyProfileMoreHintShown = false;
    /** 抖音主页签名翻页是否已接管（接管后丢弃 WebView 抓的列表，避免重复计数） */
    volatile boolean dyProfileSignedOwned = false;
    /** 调试：强制不带 uifid（验证 uifid 是否必需） */
    boolean dyNoUifid = false;

    /** 从各种抖音 URL 里抽 sec_uid */
    static String extractSecUid(String url){
        try{
            if(url == null) return null;
            // 注意：抖音有两种写法 —— 分享页是 sec_uid=，PC 主页接口是 sec_user_id=，
            // 只匹配 sec_uid 会漏掉 PC 主页那条，导致「只能拿一页」。
            java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("sec_user_id=([A-Za-z0-9_\\-%=]+)").matcher(url);
            if(m.find()) return java.net.URLDecoder.decode(m.group(1), "UTF-8");
            m = java.util.regex.Pattern
                .compile("sec_uid=([A-Za-z0-9_\\-%=]+)").matcher(url);
            if(m.find()) return java.net.URLDecoder.decode(m.group(1), "UTF-8");
            // PC 主页路径：/user/MS4wLjABAAAA...
            m = java.util.regex.Pattern.compile("/user/(MS4wLjABAAAA[A-Za-z0-9_\\-]+)").matcher(url);
            if(m.find()) return m.group(1);
            m = java.util.regex.Pattern.compile("/share/user/([A-Za-z0-9_\\-]+)").matcher(url);
            if(m.find()) return m.group(1);
        }catch(Throwable t){}
        return null;
    }

    /** 拉一页主页 JSON（UA 必须与签名一致，否则 a_bogus 失效） */
    /** 取真实 msToken：Cookie 里真的有 msToken 才用，否则从 WebView 现取，最后才用假值。
     *  注意：DyDetail.msTokenOf 在缺失时会返回 156 长假值，所以必须先自己查 cookie 里有没有。 */
    String dyRealMsToken(String cookie){
        try{
            // 1) Cookie 里确实带 msToken 才用
            if(cookie != null && cookie.indexOf("msToken=") >= 0){
                String t = cookieValueOf(cookie, "msToken");
                if(t != null && t.length() >= 60){
                    Log.i("DYP", "DYPROF_MSTOKEN from_cookie len=" + t.length());
                    return t;
                }
            }
            // 2) CookieManager 里确实带 msToken
            try{
                String cm = CookieManager.getInstance().getCookie("https://www.douyin.com/");
                if(cm != null && cm.indexOf("msToken=") >= 0){
                    String t2 = cookieValueOf(cm, "msToken");
                    if(t2 != null && t2.length() >= 60){
                        Log.i("DYP", "DYPROF_MSTOKEN from_webview len=" + t2.length());
                        return t2;
                    }
                }
            }catch(Throwable t2){}
            Log.i("DYP", "DYPROF_MSTOKEN none_use_fake");
        }catch(Throwable t){}
        return DyDetail.msTokenOf(cookie);
    }

    /** 从完整 URL 里取出 query（去掉 a_bogus，因为要重算） */
    static String stripBogus(String url){
        try{
            int q = url.indexOf('?');
            if(q < 0) return "";
            String query = url.substring(q + 1);
            int h = query.indexOf('#');
            if(h >= 0) query = query.substring(0, h);
            StringBuilder sb = new StringBuilder();
            for(String kv : query.split("&")){
                if(kv.length() == 0) continue;
                int e = kv.indexOf('=');
                String k = e > 0 ? kv.substring(0, e) : kv;
                if(k.equals("a_bogus") || k.equals("X-Bogus") || k.equals("_signature")) continue;
                if(sb.length() > 0) sb.append('&');
                sb.append(kv);
            }
            return sb.toString();
        }catch(Throwable t){ return ""; }
    }

    /** 替换 query 里某个参数的值（没有就追加） */
    static String replaceQueryParam(String query, String key, String value){
        try{
            if(query == null) return "";
            StringBuilder sb = new StringBuilder();
            boolean hit = false;
            for(String kv : query.split("&")){
                if(kv.length() == 0) continue;
                int e = kv.indexOf('=');
                String k = e > 0 ? kv.substring(0, e) : kv;
                if(sb.length() > 0) sb.append('&');
                if(k.equals(key)){
                    sb.append(key).append('=').append(value);
                    hit = true;
                } else {
                    sb.append(kv);
                }
            }
            if(!hit){
                if(sb.length() > 0) sb.append('&');
                sb.append(key).append('=').append(value);
            }
            return sb.toString();
        }catch(Throwable t){ return query; }
    }

    /** 把 query（已含 a_bogus）拼成可请求的完整 URL */
    static String queryPartToUrl(String query, String signed){
        try{
            String base = DyDetail.API_POST;
            if(query != null && query.indexOf("/aweme/post/") >= 0){
                int q = query.indexOf('?');
                if(q > 0) base = query.substring(0, q);
            }
            return base + "?" + signed;
        }catch(Throwable t){ return DyDetail.API_POST + "?" + signed; }
    }

    /** 从请求 URL 的 query 里取 msToken（URL 解码后返回，取不到给空串） */
    static String pickMsTokenFromUrl(String url){
        if(url == null) return "";
        try{
            int q = url.indexOf('?');
            if(q < 0) return "";
            String query = url.substring(q + 1);
            int h = query.indexOf('#');
            if(h >= 0) query = query.substring(0, h);
            for(String kv : query.split("&")){
                int e = kv.indexOf('=');
                if(e <= 0) continue;
                if(!kv.substring(0, e).equals("msToken")) continue;
                String v = kv.substring(e + 1);
                try{ v = java.net.URLDecoder.decode(v, "UTF-8"); }catch(Throwable t){}
                if(v != null && v.length() >= 60){
                    Log.i("DYP", "DYPROF_MSTOKEN from_url len=" + v.length());
                    return v;
                }
            }
        }catch(Throwable t){}
        return "";
    }

    /** 从 cookie 串里取某个键的值（取不到返回空串） */
    static String cookieValueOf(String cookie, String key){
        if(cookie == null || key == null) return "";
        for(String kv : cookie.split(";")){
            String s = kv.trim();
            int e = s.indexOf('=');
            if(e > 0 && s.substring(0, e).trim().equals(key)){
                return s.substring(e + 1).trim();
            }
        }
        return "";
    }

    String fetchProfilePageRaw(String url){
        java.io.InputStream is = null;
        try{
            String ck = dyEffectiveCookie();
            // 诊断：确认关键 cookie 是否真的带上了（只打有没有，不打值）
            StringBuilder ckb = new StringBuilder();
            if(ck != null){
                for(String need : new String[]{"sessionid","sid_tt","uid_tt","msToken","ttwid",
                        "__ac_signature","__ac_nonce","s_v_web_id","UIFID","passport_csrf_token"}){
                    ckb.append(need).append('=').append(ck.indexOf(need + "=") >= 0 ? "1" : "0").append(' ');
                }
            }
            Log.i("DYP", "DYPROF_CK " + ckb);
            java.net.HttpURLConnection uc = openConn(url);
            uc.setInstanceFollowRedirects(true);
            uc.setRequestProperty("User-Agent", DyDetail.UA);
            uc.setRequestProperty("Referer", "https://www.douyin.com/");
            uc.setRequestProperty("Accept", "application/json, text/plain, */*");
            uc.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9");
            if(ck != null) uc.setRequestProperty("Cookie", ck);
            uc.setConnectTimeout(12000); uc.setReadTimeout(15000);
            int code = uc.getResponseCode();
            Log.i("DYP", "DYPROF_HTTP " + code);
            if(code < 200 || code >= 300) return null;
            is = uc.getInputStream();
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192]; int n; long got = 0;
            while((n = is.read(buf)) > 0){
                bo.write(buf, 0, n);
                got += n;
                if(got > 20L * 1024 * 1024) break;
            }
            Log.i("DYP", "DYPROF_READ " + got);
            String rb = new String(bo.toByteArray(), "UTF-8");
            if(rb.length() < 60) Log.i("DYP", "DYPROF_SHORT[" + rb + "]");
            return rb;
        }catch(Throwable t){
            Log.i("DYP", "DYPROF_FETCH_ERR " + t);
            return null;
        }finally{
            try{ if(is != null) is.close(); }catch(Throwable t){}
        }
    }

    /** 把一页 aweme_list 追加进主页列表，返回本页新增条数 */
    int appendDyProfileItems(List list){
        if(list == null) return 0;
        int added = 0;
        for(Object o : list){
            if(!(o instanceof Map)) continue;
            Map aw = (Map) o;
            String awemeId = gs(get(aw, "aweme_id"));
            if(awemeId.length() == 0) continue;
            synchronized(profileSeen){
                if(!profileSeen.add(awemeId)) continue;
            }
            String desc = gs(get(aw, "desc"));
            ProfileParser.ProfileItem it = new ProfileParser.ProfileItem();
            it.id = awemeId;
            it.title = desc;
            Object imgs = get(aw, "images");
            boolean isImage = imgs instanceof List && !((List) imgs).isEmpty();
            it.isImage = isImage;
            if(!isImage){
                Object video = get(aw, "video");
                if(video instanceof Map){
                    Object pa = get((Map) video, "play_addr");
                    if(pa instanceof Map){
                        String uu = firstUrl(get((Map) pa, "url_list"));
                        if(uu.length() > 0 && !uu.equals("?")) it.directUrl = uu;
                    }
                }
            }
            if(isImage && it.directUrl.length() == 0){
                Object firstImg = ((List) imgs).get(0);
                if(firstImg instanceof Map){
                    Object ul = get((Map) firstImg, "url_list");
                    String uu = firstUrl(ul);
                    if(uu.length() > 0 && !uu.equals("?")) it.directUrl = uu;
                }
            }
            it.url = "https://www.iesdouyin.com/share/video/" + awemeId + "/";
            synchronized(profileCollected){ profileCollected.add(it); }
            added++;
        }
        return added;
    }

    /** 反复翻页直到 has_more=0，把所有作品塞进 mixItems */
    void fetchAllMixPages(String firstUrl){
        synchronized(mixItems){ mixItems.clear(); }
        int cursor = 0;
        int page = 0;
        int emptyStreak = 0;
        final int MAX_PAGE = 50;   // 兜底，防止死循环
        while(page < MAX_PAGE){
            page++;
            if(mixMode && page > 1){ /* 仍在合集抓取中，继续 */ }
            String url = withCursor(firstUrl, cursor);
            Log.i("DYP", "MIX_PAGE " + page + " cursor=" + cursor);
            // 网络抖动/DNS 失败重试 3 次，指数退避
            String body = null;
            for(int attempt = 1; attempt <= 3; attempt++){
                body = fetchMixPageRaw(url);
                if(body != null && body.length() >= 20) break;
                Log.i("DYP", "MIX_RETRY page=" + page + " attempt=" + attempt);
                try{ Thread.sleep(600L * attempt); }catch(Throwable t){}
            }
            if(body == null || body.length() < 20){
                Log.i("DYP", "MIX_PAGE_EMPTY page=" + page);
                break;
            }
            int added;
            int hasMore;
            int nextCursor;
            try{
                Object res = new JP(body).parse();
                Map m = (res instanceof Map) ? (Map)res : new HashMap();
                Object listObj = get(m, "aweme_list");
                if(listObj == null) listObj = get(m, "aweme_details");
                List list = (listObj instanceof List) ? (List)listObj : null;
                added = appendMixItems(list);
                hasMore = truthy(get(m, "has_more")) ? 1 : 0;
                long cv = parseLong(get(m, "cursor"), -1);
                nextCursor = (int)(cv > 0 ? cv : (cursor + (added > 0 ? added : 10)));
            }catch(Throwable t){
                Log.i("DYP", "MIX_PAGE_PARSE_ERR " + t);
                break;
            }
            int total;
            synchronized(mixItems){ total = mixItems.size(); }
            Log.i("DYP", "MIX_PAGE " + page + " added=" + added + " total=" + total + " hasMore=" + hasMore + " next=" + nextCursor);
            final int fPage = page;
            h.post(new Runnable(){ public void run(){
                int tt; synchronized(mixItems){ tt = mixItems.size(); }
                s("解析中 " + tt + " 个…（第" + fPage + "页）");
            }});
            if(hasMore == 0) break;
            if(added == 0){
                // 可能是重复页（接口回退），连续两次才放弃
                if(++emptyStreak >= 3){
                    Log.i("DYP", "MIX_STOP emptyStreak=" + emptyStreak);
                    break;
                }
                try{ Thread.sleep(800); }catch(Throwable t){}
            } else {
                emptyStreak = 0;
            }
            cursor = nextCursor;
            try{ Thread.sleep(400); }catch(Throwable t){}   // 间隔，降低被限流/DNS 压力
        }
        int n;
        synchronized(mixItems){ n = mixItems.size(); }
        Log.i("DYP", "MIX_PARSED n=" + n + " pages=" + page);
        mixMode = true;
        webDone = true;
        Log.i("DYP", "MIX_RENDER_START");
        if(n == 0){ s("合集解析失败"); mixFetching = false; return; }
        renderMixList(n);
    }

    /** 用 video_id / uri 拼无水印播放地址 */
    static String playUrl(String vid){
        if(vid == null || vid.length() == 0) return "";
        return "https://aweme.snssdk.com/aweme/v1/play/?video_id=" + vid + "&ratio=1080p&line=0";
    }

    /** 兼容 JP 把数字解析成 Double 的情况：1 / 1.0 / true / "1" 都算真 */
    static boolean truthy(Object o){
        if(o == null) return false;
        if(o instanceof Boolean) return (Boolean)o;
        if(o instanceof Number) return ((Number)o).doubleValue() != 0;
        String s = o.toString().trim();
        if(s.length() == 0) return false;
        if(s.equalsIgnoreCase("true")) return true;
        if(s.equalsIgnoreCase("false")) return false;
        try{ return Double.parseDouble(s) != 0; }catch(Throwable t){ return false; }
    }

    /** 容忍 "10" / "10.0" / Double 三种形式 */
    static long parseLong(String s, long dft){
        try{
            String t = s.trim();
            if(t.length() == 0) return dft;
            int dot = t.indexOf('.');
            if(dot >= 0) t = t.substring(0, dot);
            return Long.parseLong(t);
        }catch(Throwable t){ return dft; }
    }

    static long parseLong(Object o, long dft){
        if(o == null) return dft;
        if(o instanceof Number) return (long)((Number)o).doubleValue();
        return parseLong(o.toString(), dft);
    }

    /** 把一页 aweme_list 追加进 mixItems，返回新增条数（按 aweme_id 去重） */
    int appendMixItems(List list){
        if(list == null || list.size() == 0) return 0;
        int added = 0;
        for(int i = 0; i < list.size(); i++){
            Object o2 = list.get(i);
            if(!(o2 instanceof Map)) continue;
            Map it = (Map)o2;
            String id = gs(get(it, "aweme_id"));
            if(id.length() == 0) continue;
            boolean dup = false;
            synchronized(mixItems){
                for(int k = 0; k < mixItems.size(); k++){
                    if(id.equals(mixItems.get(k)[0])){ dup = true; break; }
                }
            }
            if(dup) continue;
            String desc = gs(get(it, "desc"));
            if(desc.length() == 0) desc = gs(get(it, "caption"));   // 短剧用 caption
            String play = "";
            String cover = "";
            try{
                Map v = (Map)get(it, "video");
                if(v != null){
                    // 1) 常规 play_addr
                    Map pa = (Map)get(v, "play_addr");
                    if(pa != null){
                        List ul = (List)get(pa, "url_list");
                        if(ul != null && ul.size() > 0) play = gs(ul.get(0));
                        String uri = gs(get(pa, "uri"));
                        if(uri.length() > 0) play = playUrl(uri);
                    }
                    // 2) bit_rate 里挑最高码率
                    if(play.length() == 0){
                        List brs = (List)get(v, "bit_rate");
                        if(brs != null && brs.size() > 0){
                            Map best = null; long bestBr = -1;
                            for(int b = 0; b < brs.size(); b++){
                                Object bo = brs.get(b);
                                if(!(bo instanceof Map)) continue;
                                Map bm = (Map)bo;
                                long br = parseLong(get(bm, "bit_rate"), 0);
                                if(br > bestBr){ bestBr = br; best = bm; }
                            }
                            if(best != null){
                                Map bpa = (Map)get(best, "play_addr");
                                if(bpa != null){
                                    List bul = (List)get(bpa, "url_list");
                                    if(bul != null && bul.size() > 0) play = gs(bul.get(0));
                                    String buri = gs(get(bpa, "uri"));
                                    if(play.length() == 0 && buri.length() > 0) play = playUrl(buri);
                                }
                            }
                        }
                    }
                    // 3) 短剧只有 vid，靠 vid 拼播放地址
                    if(play.length() == 0){
                        String vid = gs(get(v, "vid"));
                        if(vid.length() > 0) play = playUrl(vid);
                    }
                    Map cv = (Map)get(v, "cover");
                    if(cv != null){
                        List cl = (List)get(cv, "url_list");
                        if(cl != null && cl.size() > 0) cover = gs(cl.get(0));
                    }
                }
                // 4) 兜底：条目上有 Cover / share_url
                if(cover.length() == 0){
                    Map c2 = (Map)get(it, "Cover");
                    if(c2 != null){
                        List cl2 = (List)get(c2, "url_list");
                        if(cl2 != null && cl2.size() > 0) cover = gs(cl2.get(0));
                    }
                }
            }catch(Throwable t){}
            synchronized(mixItems){ mixItems.add(new String[]{ id, desc, play, cover }); }
            added++;
        }
        return added;
    }

    /** 把合集列表渲染到「列表」页签（与 B 站合集一致），
     *  同时把合集首集填进「解析」页，保证解析页不是空白（有封面/标题/视频可播） */
    void renderMixList(final int n){
        mixMode = true;
        h.post(new Runnable(){ public void run(){
            try{
                webDone = true;
                if(web != null) web.setVisibility(View.GONE);

                java.util.List<String[]> snapshot;
                synchronized(mixItems){ snapshot = new java.util.ArrayList<String[]>(mixItems); }

                // ===== 把合集首集灌进「解析」页（否则解析页一片空白） =====
                if(snapshot.size() > 0){
                    String[] f0 = snapshot.get(0);
                    String fid = f0[0] != null ? f0[0] : "";
                    String fdesc = f0[1] != null ? f0[1] : "";
                    String fplay = f0[2] != null ? f0[2] : "";
                    String fcover = f0[3] != null ? f0[3] : "";
                    if(fid.length() > 0) curId = fid;
                    if(fplay.length() > 0) curPlay = fplay;
                    if(fcover.length() > 0) curCover = fcover;
                    String mt = mixTitle.length() > 0 ? mixTitle : "合集";
                    curDesc = mt + " 第1集" + (fdesc.length() > 0 ? "　" + fdesc : "");
                    // 拼一段结果文本给卡片渲染（与单视频解析格式一致）
                    StringBuilder rb = new StringBuilder();
                    rb.append("【平台】抖音\n");
                    if(fid.length() > 0) rb.append("【作品ID】").append(fid).append("\n");
                    rb.append("【合集】").append(mt).append("（共 ").append(snapshot.size()).append(" 集）\n");
                    rb.append("【描述】").append(fdesc).append("\n");
                    if(fcover.length() > 0) rb.append("【封面】").append(fcover).append("\n");
                    if(fplay.length() > 0) rb.append("【视频直链】").append(fplay).append("\n");
                    final String rt = rb.toString();
                    curResult = rt;
                    renderCards(rt);
                    // 恢复操作栏，让用户能直接播放/下载首集
                    actions.setVisibility(View.VISIBLE);
                    Log.i("DYP", "MIX_FILL_PARSE 首集 id=" + fid + " hasPlay=" + (fplay.length() > 0));
                }

                java.util.List<ProfileParser.ProfileItem> itemList =
                        new java.util.ArrayList<ProfileParser.ProfileItem>();
                for(int i = 0; i < snapshot.size(); i++){
                    String[] it = snapshot.get(i);
                    ProfileParser.ProfileItem pi = new ProfileParser.ProfileItem();
                    pi.id = it[0] != null ? it[0] : "";
                    String d = it[1] != null ? it[1] : "";
                    // 合集条目统一「标题 + 第几集」格式
                    String mt = mixTitle.length() > 0 ? mixTitle : "合集";
                    pi.title = mt + " 第" + (i + 1) + "集" + (d.length() > 0 ? "　" + d : "");
                    pi.url = it[2] != null ? it[2] : "";
                    pi.directUrl = it[2] != null ? it[2] : "";
                    itemList.add(pi);
                }
                final java.util.List<ProfileParser.ProfileItem> items = itemList;

                listHead.setText("抖音合集 · 共 " + n + " 集 · 点「下载」下该集，或点右下「选择下载」批量选");
                btnDlAll.setVisibility(View.VISIBLE);
                btnDlAll.setText("选择下载");
                btnDlAll.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){
                    // 合集独立文件夹
                    final String safeT = (mixTitle.length() > 0 ? mixTitle : curId)
                            .replaceAll("[\\\\/:*?\"<>|\\s]+", "_");
                    final String dirT = safeT + "_" + System.currentTimeMillis();
                    showListPicker(items, mixTitle.length() > 0 ? mixTitle : "合集", "合集", dirT);
                }});
                listWrap.removeAllViews();

                // 合集独立文件夹（同一批进同一个 合集/标题_时间戳）
                final String safeTitle = (mixTitle.length() > 0 ? mixTitle : curId)
                        .replaceAll("[\\\\/:*?\"<>|\\s]+", "_");
                final String mixDir = safeTitle + "_" + System.currentTimeMillis();

                for(int i = 0; i < items.size(); i++){
                    final ProfileParser.ProfileItem it = items.get(i);
                    final int idx = i;
                    LinearLayout row = new LinearLayout(MainActivity.this);
                    row.setOrientation(LinearLayout.VERTICAL);
                    row.setPadding(dp(12), dp(10), dp(12), dp(10));
                    row.setBackground(makeRoundRect(curDark ? 0xFF24272F : 0xFFFFFFFF,
                            curDark ? 0xFF333842 : 0xFFE1E4E9, 12));
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                    lp.bottomMargin = dp(8);
                    row.setLayoutParams(lp);

                    TextView title = new TextView(MainActivity.this);
                    String d = it.title != null ? it.title : "";
                    if(d.length() > 70) d = d.substring(0, 70) + "…";
                    title.setText((i + 1) + ". " + d);
                    title.setTextSize(13);
                    title.setTextColor(curDark ? 0xFFE8EAF0 : 0xFF1A1C22);
                    title.setPadding(0, 0, 0, dp(6));
                    row.addView(title);

                    LinearLayout btns = new LinearLayout(MainActivity.this);
                    btns.setOrientation(LinearLayout.HORIZONTAL);

                    TextView btnDl = new TextView(MainActivity.this);
                    btnDl.setText("下载");
                    btnDl.setTextSize(12);
                    btnDl.setTextColor(0xFFFE2C55);
                    btnDl.setPadding(dp(10), dp(5), dp(10), dp(5));
                    btnDl.setBackground(makeRoundRect(0x1AFE2C55, 0, 8));
                    btnDl.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){
                        if(it.directUrl != null && it.directUrl.length() > 0){
                            enqueueDownload(it.directUrl, "第" + (idx + 1) + "集", "mp4", "合集", mixDir);
                            s("已加入下载队列：第 " + (idx + 1) + " 集");
                            showHintToast("已加入下载队列");
                        } else s("该条无视频地址");
                    }});
                    btns.addView(btnDl);

                    TextView btnPlay = new TextView(MainActivity.this);
                    btnPlay.setText("播放");
                    btnPlay.setTextSize(12);
                    btnPlay.setTextColor(curDark ? 0xFF8AB4F8 : 0xFF1A73E8);
                    btnPlay.setPadding(dp(10), dp(5), dp(10), dp(5));
                    LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                    plp.leftMargin = dp(8);
                    btnPlay.setLayoutParams(plp);
                    btnPlay.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){
                        if(it.directUrl == null || it.directUrl.length() == 0){ s("该条无视频地址"); return; }
                        curPlay = it.directUrl;
                        curId = it.id;
                        curImgs = "";
                        startedUrl = "";
                        switchTab(0);
                        actions.setVisibility(View.VISIBLE);
                        videoBox.setVisibility(View.VISIBLE);
                        img.setVisibility(View.GONE);
                        doPlay();
                        s("播放 第 " + (idx + 1) + " 集");
                    }});
                    btns.addView(btnPlay);

                    row.addView(btns);
                    listWrap.addView(row);
                }

                switchTab(1);   // 自动切到「列表」页
                s("抖音合集 " + n + " 集已列到「列表」页");
                showHintToast("合集 " + n + " 集，已放进「列表」页");
            }catch(Throwable t){
                Log.i("DYP", "MIX_RENDER_ERR " + t);
            }
        }});
    }

    /** 通用列表多选下载：category = 分类目录，dirOverride = 统一子目录（可空）。
     *  B站 DASH 条目需要每集单独解析音频轨，用 isBili 标记走双轨入队。 */
    void showListPicker(final java.util.List<ProfileParser.ProfileItem> items,
                        final String titlePrefix,
                        final String category,
                        final String dirOverride){
        showListPicker(items, titlePrefix, category, dirOverride, false);
    }

    void showListPicker(final java.util.List<ProfileParser.ProfileItem> items,
                        final String titlePrefix,
                        final String category,
                        final String dirOverride,
                        final boolean isBili){
        final int n = items.size();
        if(n == 0){ s("列表为空"); return; }
        final boolean[] sel = new boolean[n];
        String[] labels = new String[n];
        for(int i = 0; i < n; i++){
            String t = items.get(i).title;
            if(t == null) t = "";
            if(t.length() > 30) t = t.substring(0, 30) + "…";
            labels[i] = (i + 1) + ". " + (t.length() > 0 ? t : items.get(i).id);
        }
        dypDlg()
            .setTitle("选择要下载的（共 " + n + " 条）")
            .setMultiChoiceItems(labels, sel,
                new android.content.DialogInterface.OnMultiChoiceClickListener(){
                    public void onClick(android.content.DialogInterface d, int which, boolean isChecked){
                        sel[which] = isChecked;
                    }
                })
            .setPositiveButton("下载所选", new android.content.DialogInterface.OnClickListener(){
                public void onClick(android.content.DialogInterface d, int w){
                    doListDownload(items, sel, titlePrefix, category, dirOverride, isBili);
                }
            })
            .setNeutralButton("全选", new android.content.DialogInterface.OnClickListener(){
                public void onClick(android.content.DialogInterface d, int w){
                    for(int i = 0; i < n; i++) sel[i] = true;
                    doListDownload(items, sel, titlePrefix, category, dirOverride, isBili);
                }
            })
            .setNegativeButton("取消", null)
            .create().show();
    }

    /** 列表多选实际入队。B站条目要单独解析音频轨（DASH 双轨） */
    void doListDownload(final java.util.List<ProfileParser.ProfileItem> items,
                        final boolean[] sel,
                        final String titlePrefix,
                        final String category,
                        final String dirOverride,
                        final boolean isBili){
        if(!isBili){
            int cnt = 0;
            for(int i = 0; i < items.size(); i++){
                if(!sel[i]) continue;
                ProfileParser.ProfileItem it = items.get(i);
                String u = (it.directUrl != null && it.directUrl.length() > 0) ? it.directUrl : it.url;
                if(u == null || u.length() == 0) continue;
                String nb = (titlePrefix.length() > 0 ? titlePrefix + "_" : "") + "第" + (i + 1) + "集";
                if(dirOverride != null && dirOverride.length() > 0){
                    enqueueDownload(u, nb, "mp4", category, dirOverride);
                } else {
                    enqueueDownload(u, nb, "mp4", category);
                }
                cnt++;
            }
            if(cnt == 0){ s("未选择任何条目"); return; }
            s("已加入下载队列：" + cnt + " 条");
            showHintToast("已加入 " + cnt + " 条");
            return;
        }
        // B 站：逐条解析（拿视频轨 + 音频轨）
        s("正在解析所选剧集（B站 DASH）…");
        new Thread(new Runnable(){ public void run(){
            int cnt = 0;
            String lastQ = "";
            for(int i = 0; i < items.size(); i++){
                if(!sel[i]) continue;
                ProfileParser.ProfileItem it = items.get(i);
                String epAid = biliEpAid(it, curBili);
                String v = BiliParser.getPlayUrl(MainActivity.this, epAid, it.id);
                String a = BiliParser.lastAudioUrl;
                lastQ = BiliParser.lastQualityDesc;
                if(v == null || v.length() == 0) continue;
                String nb = (titlePrefix.length() > 0 ? titlePrefix + "_" : "") + "第" + (i + 1) + "集";
                enqueueDownloadWithAudio(v, nb, "mp4", category,
                        dirOverride != null ? dirOverride : "", a != null ? a : "");
                cnt++;
            }
            final int fc = cnt;
            final String fq = lastQ;
            h.post(new Runnable(){ public void run(){
                if(fc == 0){ s("未选择任何条目"); return; }
                s("已加入下载队列：" + fc + " 条" + (fq.length() > 0 ? "（" + fq + "）" : ""));
                showHintToast("已加入 " + fc + " 条");
            }});
        }}).start();
    }

    /** 批量下载合集内全部视频：同一批进同一个 合集/标题_时间戳 文件夹 */
    void downloadAllMix(){
        showMixPicker(true);
    }

    /** 合集按集选择下载；allFirst=true 表示从「全选」入口进来 */
    void showMixPicker(final boolean allFirst){
        final java.util.List<String[]> snapshot;
        synchronized(mixItems){ snapshot = new java.util.ArrayList<String[]>(mixItems); }
        final int n = snapshot.size();
        if(n == 0){ s("合集为空"); return; }

        final boolean[] sel = new boolean[n];
        if(allFirst){ for(int i = 0; i < n; i++) sel[i] = true; }

        String t = mixTitle.length() > 0 ? mixTitle : curId;
        final String safeTitle = t.replaceAll("[\\\\/:*?\"<>|\\s]+", "_");
        final String mixDir = safeTitle + "_" + System.currentTimeMillis();

        String[] labels = new String[n];
        for(int i = 0; i < n; i++){
            String desc = snapshot.get(i)[1];
            if(desc == null) desc = "";
            if(desc.length() > 26) desc = desc.substring(0, 26) + "…";
            labels[i] = "第 " + (i + 1) + " 集" + (desc.length() > 0 ? "　" + desc : "");
        }

        dypDlg()
            .setTitle("选择要下载的集数")
            .setMessage("合集共 " + n + " 集\n保存为：" + safeTitle + "_第N集.mp4")
            .setMultiChoiceItems(labels, sel,
                new android.content.DialogInterface.OnMultiChoiceClickListener(){
                    public void onClick(android.content.DialogInterface d, int which, boolean isChecked){
                        sel[which] = isChecked;
                    }
                })
            .setPositiveButton("下载所选", new android.content.DialogInterface.OnClickListener(){
                public void onClick(android.content.DialogInterface d, int w){
                    int cnt = 0;
                    for(int i = 0; i < n; i++){
                        if(!sel[i]) continue;
                        String u = snapshot.get(i)[2];
                        if(u == null || u.length() == 0) continue;
                        String nb = safeTitle + "_第" + (i + 1) + "集";
                        downloadQueue.add(new DlTask(u, "合集" + (i + 1), "mp4",
                                "合集", mixDir, "", nb));
                        cnt++;
                    }
                    if(cnt == 0){ s("未选择任何集数"); return; }
                    if(!dlRunning){ dlRunning = true; runDownloadQueue(); }
                    s("已加入下载：" + cnt + " 集");
                    Log.i("DYP", "MIX_QUEUE n=" + cnt + " dir=" + mixDir);
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }


    // ===== 检查更新（基于 GitHub Releases，无需自建服务器）=====

    /** 联网检查最新 Release，与当前 versionCode 比对 */
    /** 当前更新源（运行时读 prefs，未设置用默认） */
    static String updSrc(Context c){
        String v = c.getSharedPreferences(SP, MODE_PRIVATE).getString(KEY_UPD_SRC, UPD_SOURCE);
        return SRC_GITHUB.equalsIgnoreCase(v) ? SRC_GITHUB : SRC_GITEE;
    }
    static boolean isGitee(Context c){ return SRC_GITEE.equals(updSrc(c)); }
    static String curOwner(Context c){ return isGitee(c) ? GITEE_OWNER : GH_OWNER; }
    static String curRepo(Context c){  return isGitee(c) ? GITEE_REPO  : GH_REPO; }
    static String curBranch(Context c){ return isGitee(c) ? GITEE_BRANCH : GH_BRANCH; }
    static String curStaticUrl(Context c){ return isGitee(c) ? UPD_STATIC_GITEE : UPD_STATIC_GITHUB; }
    /** 当前源的主页地址 */
    static String curRepoHome(Context c){
        return isGitee(c)
            ? ("https://gitee.com/" + GITEE_OWNER + "/" + GITEE_REPO)
            : ("https://github.com/" + GH_OWNER + "/" + GH_REPO);
    }
    /** 当前源的 Releases 列表地址 */
    static String curReleasesUrl(Context c){ return curRepoHome(c) + "/releases"; }

    // ===== GitHub 镜像支持 =====
    /** 当前 GitHub 访问方式 */
    static String ghMode(Context c){
        String v = c.getSharedPreferences(SP, MODE_PRIVATE).getString(KEY_GH_MODE, GH_MODE_MIRROR);
        return GH_MODE_DIRECT.equalsIgnoreCase(v) ? GH_MODE_DIRECT : GH_MODE_MIRROR;
    }
    static boolean ghUseMirror(Context c){
        return SRC_GITHUB.equals(updSrc(c)) && GH_MODE_MIRROR.equals(ghMode(c));
    }
    /** 把一个 github.com / raw.githubusercontent.com / api.github.com 地址套上镜像前缀 */
    static String mirrorify(String url, int idx){
        try{
            if(url == null || url.length() == 0) return url;
            if(idx < 0 || idx >= GH_MIRRORS.length) return url;
            return GH_MIRRORS[idx] + url;
        }catch(Throwable t){ return url; }
    }

    /**
     * 按 GitHub 访问方式取文本：
     *  - 直连模式：直接请求
     *  - 镜像模式：依次试每个镜像，全失败再回退直连
     * 返回值附带 usedMirror 标记（用于日志/UI 显示）
     */
    String ghGetText(String rawUrl, int ct, int rt){
        if(!ghUseMirror(this)){
            ghLastVia = "direct";
            return httpGetText(rawUrl, ct, rt);
        }
        for(int i = 0; i < GH_MIRRORS.length; i++){
            String u = mirrorify(rawUrl, i);
            String r = httpGetText(u, ct, rt);
            if(r != null && r.length() > 0){
                ghLastVia = "mirror:" + i;
                Log.i("DYP", "GH_MIRROR_OK idx=" + i + " " + GH_MIRRORS[i]);
                return r;
            }
            Log.i("DYP", "GH_MIRROR_FAIL idx=" + i + " " + GH_MIRRORS[i]);
        }
        // 镜像全挂 → 回退直连（用户可能自己挂了代理）
        ghLastVia = "direct(fallback)";
        Log.i("DYP", "GH_MIRROR_ALL_FAIL 回退直连");
        return httpGetText(rawUrl, ct, rt);
    }
    /** 最近一次 ghGetText 走的方式（日志/UI 用） */
    volatile String ghLastVia = "";

    /** 当前平台名 */
    String platformName(){ return isGitee(this) ? "Gitee" : "GitHub"; }

    /** 仓库主页地址 */
    String repoUrl(){ return curRepoHome(this); }

    /** 联网检查最新 Release，与当前 versionCode 比对 */
    // ===== 主题切换 =====
    boolean isDarkMode(){
        try{ return getSharedPreferences(SP, MODE_PRIVATE).getBoolean(KEY_DARK, false); }
        catch(Throwable t){ return false; }
    }

    void applyTheme(boolean dark){
        try{ getSharedPreferences(SP, MODE_PRIVATE).edit().putBoolean(KEY_DARK, dark).apply(); }catch(Throwable t){}
        curDark = dark;
        int bg      = dark ? C_DARK_BG : C_LIGHT_BG;
        int surface = dark ? C_DARK_SURFACE : C_LIGHT_SURFACE;
        int alt     = dark ? C_DARK_ALT : C_LIGHT_ALT;
        int stroke  = dark ? C_DARK_STROKE : C_LIGHT_STROKE;
        int textC   = dark ? C_DARK_TEXT : C_LIGHT_TEXT;
        int subC    = dark ? C_DARK_SUB : C_LIGHT_SUB;
        int hintC   = dark ? C_DARK_HINT : C_LIGHT_HINT;

        View root = findViewById(R.id.root);
        if(root != null) root.setBackgroundColor(bg);
        View t1 = findViewById(R.id.tvTitleDY);
        if(t1 instanceof TextView) ((TextView)t1).setTextColor(textC);
        if(input != null){
            input.setBackground(makeRoundRect(surface, stroke, 14));
            input.setTextColor(textC);
            input.setHintTextColor(hintC);
        }
        if(status != null) status.setTextColor(subC);
        if(btnPlay != null){ btnPlay.setBackground(makeRoundRect(alt, stroke, 12)); btnPlay.setTextColor(textC); }
        if(btnDl   != null){ btnDl.setBackground(makeRoundRect(alt, stroke, 12));   btnDl.setTextColor(textC); }
        if(btnAudio != null){ btnAudio.setBackground(makeRoundRect(alt, stroke, 12)); btnAudio.setTextColor(textC); }
        if(btnMusic != null){ btnMusic.setBackground(makeRoundRect(alt, stroke, 12)); btnMusic.setTextColor(textC); }
        if(btnTheme != null){ btnTheme.setTextColor(subC); btnTheme.setText(dark ? "亮" : "暗"); }
        if(img != null) img.setBackgroundColor(alt);
        if(curResult != null && curResult.length() > 0) renderCards(curResult);
    }

    android.graphics.drawable.GradientDrawable makeRoundRect(int fill, int strokeColor, int radiusDp){
        // 缓存 ConstantState：多个 View 共享同一份绘制参数，但各自持有独立 Drawable，
        // 避免 setBackground 后被复用时状态互相干扰。
        String key = fill + "|" + strokeColor + "|" + radiusDp;
        android.graphics.drawable.Drawable.ConstantState cs = roundRectCache.get(key);
        if(cs != null){
            try{
                android.graphics.drawable.Drawable d = cs.newDrawable();
                if(d instanceof android.graphics.drawable.GradientDrawable)
                    return (android.graphics.drawable.GradientDrawable) d;
            }catch(Throwable t){}
        }
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(radiusDp));
        if(strokeColor != 0) g.setStroke(dp(1), strokeColor);
        try{
            if(roundRectCache.size() < 64 && g.getConstantState() != null)
                roundRectCache.put(key, g.getConstantState());
        }catch(Throwable t){}
        return g;
    }
    final java.util.HashMap<String, android.graphics.drawable.Drawable.ConstantState> roundRectCache =
        new java.util.HashMap<String, android.graphics.drawable.Drawable.ConstantState>();

    // ===== 卡片渲染 =====
    // 短字段在前（用户关心的核心信息），超长字段（封面/直链/图集）排后面
    static final String[] CARD_ORDER = {
        "作品ID", "类型", "作者", "时长", "发布时间", "发布地点", "话题", "描述",
        "视频直链", "无水印直链", "封面", "图集"
    };

    void renderCards(final String text){
        if(out == null) return;
        out.removeAllViews();
        if(text == null || text.length() == 0) return;
        java.util.List<String[]> items = new java.util.ArrayList<String[]>();
        String[] lines = text.split("\n");
        for(String ln : lines){
            if(ln == null) continue;
            ln = ln.trim();
            if(ln.length() == 0) continue;
            int a = ln.indexOf("【");
            int b = ln.indexOf("】");
            if(a == 0 && b > 1){
                items.add(new String[]{ ln.substring(1, b), ln.substring(b + 1).trim() });
            } else if(items.size() > 0){
                String[] last = items.get(items.size() - 1);
                last[1] = last[1] + "\n" + ln;
            }
        }
        if(items.size() == 0){ out.addView(buildCard("解析结果", text, false)); return; }
        java.util.List<String[]> ordered = new java.util.ArrayList<String[]>();
        for(String want : CARD_ORDER){
            for(String[] kv : items) if(kv[0].equals(want)) ordered.add(kv);
        }
        for(String[] kv : items){
            boolean dup = false;
            for(String want : CARD_ORDER) if(kv[0].equals(want)){ dup = true; break; }
            if(!dup) ordered.add(kv);
        }
        for(String[] kv : ordered){
            if(kv[1] == null || kv[1].length() == 0 || kv[1].equals("null")) continue;
            View card = buildCard(kv[0], kv[1], true);
            // 入场动画：从下往上 6dp + 淡入，逐条错开 24ms
            card.setAlpha(0f);
            card.setTranslationY(dp(6));
            final int delay = out.getChildCount() * 24;
            card.animate().alpha(1f).translationY(0f)
                .setStartDelay(delay).setDuration(180)
                .setInterpolator(new android.view.animation.DecelerateInterpolator(1.2f))
                .start();
            out.addView(card);
        }
    }

    View buildCard(String label, String value, final boolean copyable){
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(makeRoundRect(curDark ? C_DARK_SURFACE : C_LIGHT_SURFACE,
                curDark ? C_DARK_STROKE : C_LIGHT_STROKE, 16));
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        clp.bottomMargin = dp(10);
        card.setLayoutParams(clp);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);

        TextView tag = new TextView(this);
        tag.setText(label);
        tag.setTextSize(12);
        tag.setTextColor(0xFFFE2C55);
        tag.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        tag.setBackground(makeRoundRect(curDark ? 0xFF23262F : 0xFFF0F1F4, 0, 8));
        tag.setPadding(dp(8), dp(3), dp(8), dp(3));
        row.addView(tag);

        View spacer = new View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(0, 1, 1f));
        row.addView(spacer);

        if(copyable){
            TextView cp = new TextView(this);
            cp.setText("复制");
            cp.setTextSize(11);
            cp.setTextColor(curDark ? C_DARK_SUB : C_LIGHT_SUB);
            cp.setPadding(dp(8), dp(4), dp(8), dp(4));
            cp.setBackground(makeRoundRect(curDark ? 0xFF23262F : 0xFFF0F1F4, 0, 8));
            cp.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){
                try{
                    android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("dyp", value));
                    Toast.makeText(MainActivity.this, "已复制", Toast.LENGTH_SHORT).show();
                }catch(Throwable t){}
            }});
            row.addView(cp);
        }
        card.addView(row);

        TextView val = new TextView(this);
        // 长值折叠：超过 160 字符（URL 列表等）默认收 3 行，点「展开」看全
        final boolean foldable = value != null && value.length() > 160;
        val.setText(value);
        val.setTextSize(13);
        val.setTextColor(curDark ? C_DARK_TEXT : C_LIGHT_TEXT);
        val.setLineSpacing(dp(4), 1f);
        val.setTextIsSelectable(true);
        val.setPadding(0, dp(8), 0, 0);
        if(foldable) val.setMaxLines(3);
        card.addView(val);

        if(foldable){
            final TextView tg = new TextView(this);
            tg.setText("展开 ▼");
            tg.setTextSize(11);
            tg.setTextColor(0xFFFE2C55);
            tg.setPadding(0, dp(6), 0, 0);
            tg.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            final boolean[] open = {false};
            tg.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){
                open[0] = !open[0];
                val.setMaxLines(open[0] ? Integer.MAX_VALUE : 3);
                tg.setText(open[0] ? "收起 ▲" : "展开 ▼");
            }});
            card.addView(tg);
        }
        return card;
    }


/** 读一个 URL 全量文本，跟随重定向，返回 null 表示失败 */
    String httpGetText(String url, int connectMs, int readMs){
        HttpURLConnection c = null;
        try{
            if(SrvClient.isBuiltin(url)) Log.i("DYP", "SRV_PIN_HIT " + url.substring(0, Math.min(60, url.length())));
            c = openConnRetry(url);
            if(SrvClient.isBuiltin(url)){
                Log.i("DYP", "SRV_HDR_TS=" + c.getRequestProperty("X-TS"));
            }
            c.setInstanceFollowRedirects(true);
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13; DYParse) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36");
            c.setRequestProperty("Accept", "*/*");
            c.setConnectTimeout(connectMs);
            c.setReadTimeout(readMs);
            int code = c.getResponseCode();
            if(code != 200) return null;
            java.io.InputStream in = c.getInputStream();
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192]; int n;
            while((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            in.close();
            return new String(bo.toByteArray(), "UTF-8");
        }catch(Throwable t){
            Log.i("DYP", "HTTP_GET_ERR " + url + " " + t);
            return null;
        }finally{
            try{ if(c != null) c.disconnect(); }catch(Throwable t){}
        }
    }

    void checkUpdate(final boolean silent){
        if(GH_OWNER.length() == 0 || GH_REPO.length() == 0){
            if(!silent) s("未配置仓库地址");
            return;
        }
        if(!silent) s("检查更新中...");
        new Thread(new Runnable(){ public void run(){
            final int curVer = curVersionCode();
            final boolean useGitee = isGitee(MainActivity.this);
            final String owner = curOwner(MainActivity.this);
            final String repo  = curRepo(MainActivity.this);
            final String home  = curRepoHome(MainActivity.this);
            String tag = null, html = null, name = null, bodyTxt = null;
            String apkUrl = "", apkName = "";
            StringBuilder why = new StringBuilder();

            // ---- 源 0：自建服务器 version.json（优先，可配置）----
            String srv = getSharedPreferences(SP, MODE_PRIVATE).getString(KEY_DYP_SRV, DYP_SRV_DEFAULT);
            if(srv != null && srv.length() > 0){
                String srvBody = httpGetText(srv + "/version.json", 7000, 9000);
                if(srvBody != null){
                    // ---- 升级公告：解决老版本版本号算法 bug 导致的「检测不到更新」----
                    // 服务器在 version.json 里下发 upgrade_notice（JSON 对象）：
                    //   {"title":"提示","message":"正文","url":"下载页","until":1030703,"once_per_day":0}
                    // 条件：当前 versionCode < until 才显示；只对老版本生效，新版本不会再弹。
                    // 老版本（1.3.7.1 及以前）的 parseVer 有 bug，无法识别四位版本更新，
                    // 只能靠这个通道把它们引导到 Gitee 手动更新一次。
                    checkUpgradeNotice(srvBody);

                    String st = jget(srvBody, "tag");
                    if(st != null && st.length() > 0){
                        tag = st;
                        name = jget(srvBody, "name");
                        bodyTxt = jget(srvBody, "body");
                        apkUrl = jget(srvBody, "apk");
                        if(apkUrl != null && apkUrl.length() > 0){
                            apkName = apkUrl.substring(apkUrl.lastIndexOf('/') + 1);
                        }
                        html = srv;
                        // 强制更新标记：force=1 或 min_version=<versionCode>
                        // 服务器不可达时这两项读不到，force 就为 false，不影响使用
                        try{
                            SharedPreferences.Editor ed = getSharedPreferences(SP, MODE_PRIVATE).edit();
                            String mv = jnum(srvBody, "min_version");
                            if(mv != null && mv.length() > 0){
                                int mvi = (int) Double.parseDouble(mv);
                                if(mvi > 0) ed.putInt(KEY_FORCE_MIN, mvi);
                            }
                            String fo = jnum(srvBody, "force");
                            int nv = parseVer(tag);
                            if(nv > 0 && fo != null
                                    && ("1".equals(fo) || "true".equalsIgnoreCase(fo))){
                                ed.putBoolean("force_" + nv, true);
                            }
                            ed.apply();
                            Log.i("DYP", "UPDATE_FORCE_INFO force=" + fo + " min=" + mv);
                        }catch(Throwable t){}
                        Log.i("DYP", "UPDATE_SRC selfhost tag=" + tag);
                    }
                } else {
                    why.append("selfhost_fail ");
                }
            }

            // ---- 源 1：API（Gitee 匿名常被限流 403；GitHub 匿名限 60 次/时）----
            String api = useGitee
                ? ("https://gitee.com/api/v5/repos/" + owner + "/" + repo + "/releases/latest")
                : ("https://api.github.com/repos/" + owner + "/" + repo + "/releases/latest");
            // GitHub 源可能走镜像；Gitee 直连
            String body = useGitee ? httpGetText(api, 9000, 11000) : ghGetText(api, 9000, 11000);
            if(body != null){
                // 注意：源 0（自建服务器）已拿到 tag 时不能被低版本覆盖。
                // 之前这里无条件赋值，导致服务器发 v1.3.7.3 会被 Gitee 上的 v1.3.7.2 盖掉，
                // 表现就是「服务器明明发了新版，App 却说已是最新」。
                String apiTag = jget(body, "tag_name");
                if(apiTag == null) apiTag = "";
                boolean takeApi = (tag == null || tag.length() == 0)
                                  || (parseVer(apiTag) > parseVer(tag));
                if(takeApi){
                    tag = apiTag;
                    html = jget(body, "html_url");
                    name = jget(body, "name");
                    bodyTxt = jget(body, "body");
                    int q = body.indexOf("browser_download_url");
                    while(q > 0){
                        String u = jgetFrom(body, q);
                        if(u != null && u.toLowerCase().endsWith(".apk")){
                            apkUrl = u;
                            apkName = u.substring(u.lastIndexOf('/') + 1);
                            break;
                        }
                        q = body.indexOf("browser_download_url", q + 1);
                    }
                } else {
                    Log.i("DYP", "UPDATE_SRC api_skip 已有更高版本 " + tag + " >= " + apiTag);
                }
                Log.i("DYP", "UPDATE_SRC api(" + (useGitee ? "gitee" : "github") + ") tag=" + apiTag);
            } else {
                why.append("api_fail ");
            }

            // ---- 源 2：Releases 网页抓取（Gitee 用，GitHub 页面结构不同故跳过）----
            if((tag == null || tag.length() == 0) && useGitee){
                String relPage = httpGetText(home + "/releases", 11000, 14000);
                if(relPage != null){
                    String t2 = pickTag(relPage);
                    if(t2 != null && t2.length() > 0){
                        tag = t2;
                        name = t2;
                        bodyTxt = "";
                        html = home + "/releases/tag/" + t2;
                        String tp = httpGetText(html, 11000, 14000);
                        if(tp != null){
                            String au = pickApk(tp, t2);
                            if(au != null){
                                apkUrl = au;
                                apkName = au.substring(au.lastIndexOf('/') + 1);
                            }
                            String bd = pickReleaseBody(tp);
                            if(bd != null && bd.length() > 0) bodyTxt = bd;
                        }
                        Log.i("DYP", "UPDATE_SRC web tag=" + tag + " apk=" + apkName);
                    } else {
                        why.append("web_notag ");
                    }
                } else {
                    why.append("web_fail ");
                }
            }

            // ---- 源 3：仓库内静态 version.json ----
            if(tag == null || tag.length() == 0){
                String stUrl = curStaticUrl(MainActivity.this);
                String js = isGitee(MainActivity.this)
                        ? httpGetText(stUrl, 9000, 11000)
                        : ghGetText(stUrl, 9000, 11000);
                if(js != null && js.indexOf("tag") >= 0){
                    tag = jget(js, "tag");
                    String nm = jget(js, "name");
                    name = (nm != null && nm.length() > 0) ? nm : tag;
                    bodyTxt = jget(js, "body");
                    apkUrl = jget(js, "apk");
                    if(apkUrl != null && apkUrl.length() > 0){
                        apkName = apkUrl.substring(apkUrl.lastIndexOf('/') + 1);
                    }
                    html = home + "/releases";
                    Log.i("DYP", "UPDATE_SRC static tag=" + tag);
                } else {
                    why.append("static_fail ");
                }
            }

            final String fTag = tag, fHtml = html, fName = name, fBody = bodyTxt;
            final String fApk = (apkUrl == null) ? "" : apkUrl;
            final String fApkName = (apkName == null) ? "" : apkName;
            Log.i("DYP", "UPDATE_RESULT tag=" + fTag + " cur=" + curVer + " why=" + why.toString().trim());

            h.post(new Runnable(){ public void run(){
                if(fTag == null || fTag.length() == 0){
                    if(!silent) showUpdateFailDialog();
                    return;
                }
                int newVer = parseVer(fTag);
                if(newVer > curVer){
                    // 手动检查（silent=false）时无视「下个版本再提醒」，一定会弹
                    showUpdateDialog(fTag, fName, fBody, fHtml, fApk, fApkName, newVer, curVer, silent);
                    s("发现新版本 " + fTag);
                } else {
                    if(!silent) s("已是最新版 (" + versionName() + ")");
                }
            }});
        }}).start();
    }

    /** 服务器下发的升级公告（JSON 对象）。
     *
     *  用途：v1.3.7.1 及以前的 parseVer 有 bug（只解析三段，把 v1.3.7.3 算成 10307，
     *  小于自身的 103701），导致它们永远检测不到后续更新。新版已修正算法，
     *  但已经装出去的旧版无法回头改，只能靠这条公告引导用户去 Gitee 手动更新一次。
     *
     *  version.json 里这样写：
     *    "upgrade_notice": {
     *      "title": "请更新到 v1.3.7.3",
     *      "message": "……说明……",
     *      "url": "https://gitee.com/adaaixianzun/dyparse/releases",
     *      "until": 1030703,        // 当前 versionCode 小于这个值才弹（0 或缺省=不限）
     *      "once": 1                // 1 = 同一个 versionCode 只弹一次
     *    }
     *
     *  注意：这个方法只有装了「已修正版本」的 App 才会调用；对旧版无效。
     */
    void checkUpgradeNotice(String srvJson){
        try{
            int p = srvJson.indexOf("\"upgrade_notice\"");
            if(p < 0) return;
            // 取 upgrade_notice 这个对象的文本
            int ob = srvJson.indexOf('{', p);
            if(ob < 0) return;
            int depth = 0, oe = -1;
            for(int i = ob; i < srvJson.length(); i++){
                char c = srvJson.charAt(i);
                if(c == '{') depth++;
                else if(c == '}'){ depth--; if(depth == 0){ oe = i; break; } }
            }
            if(oe < 0) return;
            final String obj = srvJson.substring(ob, oe + 1);
            final String nTitle = jget(obj, "title");
            final String nMsg   = jget(obj, "message");
            final String nUrl   = jget(obj, "url");
            if(nMsg == null || nMsg.length() == 0) return;
            String untilS = jnum(obj, "until");
            int until = 0;
            try{ if(untilS != null && untilS.length() > 0) until = (int) Double.parseDouble(untilS); }catch(Throwable t){}
            final int cur = curVersionCode();
            if(until > 0 && cur >= until){
                Log.i("DYP", "UPGRADE_NOTICE skip cur=" + cur + " until=" + until);
                return;
            }
            // 同一个 versionCode 只弹一次
            final String spKey = "upg_notice_" + cur;
            if(getSharedPreferences(SP, MODE_PRIVATE).getBoolean(spKey, false)){
                Log.i("DYP", "UPGRADE_NOTICE already_shown cur=" + cur);
                return;
            }
            Log.i("DYP", "UPGRADE_NOTICE show cur=" + cur + " until=" + until);
            h.post(new Runnable(){ public void run(){
                try{
                    dypDlg()
                        .setTitle(nTitle != null && nTitle.length() > 0 ? nTitle : "重要提示")
                        .setMessage(nMsg)
                        .setCancelable(true)
                        .setPositiveButton("去更新", new android.content.DialogInterface.OnClickListener(){
                            public void onClick(android.content.DialogInterface d, int w){
                                openUrl(nUrl != null && nUrl.length() > 0
                                    ? nUrl
                                    : "https://gitee.com/adaaixianzun/dyparse/releases");
                            }
                        })
                        .setNegativeButton("以后再说", null)
                        .show();
                    getSharedPreferences(SP, MODE_PRIVATE).edit().putBoolean(spKey, true).apply();
                }catch(Throwable t){
                    Log.i("DYP", "UPGRADE_NOTICE_ERR " + t);
                }
            }});
        }catch(Throwable t){
            Log.i("DYP", "UPGRADE_NOTICE_PARSE_ERR " + t);
        }
    }

    /** 三级都失败时的兜底弹窗：给手动入口，不粗暴报 HTTP 码 */
    void showUpdateFailDialog(){
        final String url = curReleasesUrl(this);
        boolean gg = isGitee(this);
        String msg = "无法获取版本信息。\n\n"
            + "API、Release 页、静态文件均未返回数据。\n"
            + (gg ? "Gitee 对部分网络环境会限流，属正常现象。\n"
                  : "GitHub 在国内可能被墙，可在设置里切换回 Gitee 源。\n")
            + "\n当前更新源：" + platformName() + "\n"
            + "当前版本：" + versionName() + "\n\n"
            + "可打开发布页手动查看最新版本。";
        dypDlg()
            .setTitle("检查更新失败")
            .setMessage(msg)
            .setPositiveButton("打开发布页", new android.content.DialogInterface.OnClickListener(){
                public void onClick(android.content.DialogInterface d, int w){
                    try{ openUrl(url); }catch(Throwable t){ s("打开失败：" + t); }
                }
            })
            .setNeutralButton("切换更新源", new android.content.DialogInterface.OnClickListener(){
                public void onClick(android.content.DialogInterface d, int w){
                    showUpdSrcDialog();
                }
            })
            .setNegativeButton("关闭", null)
            .show();
    }

    /** 更新源选择：Gitee / GitHub */
    void showUpdSrcDialog(){
        final String[] labels = {
            "Gitee（国内直连，推荐）",
            "GitHub（国际；国内需选镜像或自己挂代理）"
        };
        int cur = isGitee(this) ? 0 : 1;
        dypDlg()
            .setTitle("选择更新源")
            .setSingleChoiceItems(labels, cur, new android.content.DialogInterface.OnClickListener(){
                public void onClick(android.content.DialogInterface d, int which){
                    String v = which == 1 ? SRC_GITHUB : SRC_GITEE;
                    try{
                        getSharedPreferences(SP, MODE_PRIVATE).edit().putString(KEY_UPD_SRC, v).apply();
                    }catch(Throwable t){}
                    d.dismiss();
                    Toast.makeText(MainActivity.this, "更新源已切换到 " + (which == 1 ? "GitHub" : "Gitee"), Toast.LENGTH_SHORT).show();
                    appLog("更新", "更新源切换为 " + v);
                    buildSettingsPage();   // 刷新设置页（GitHub 访问方式那一行会跟着出现/消失）
                    checkUpdate(false);
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }

    /** GitHub 访问方式：镜像加速 / 直连（两步弹窗：先说明，再选） */
    void showGhModeDialog(){
        final boolean isMirror = GH_MODE_MIRROR.equals(ghMode(this));
        String msg = isMirror
            ? ("当前：镜像加速\n\n会自动从 " + GH_MIRRORS.length
               + " 个公开镜像里挑可用的（只用来取版本号和下载 APK，不传任何私密数据）。\n"
               + "全部镜像失效时会自动回退直连。")
            : "当前：直连\n\n需要自己挂代理才能访问 GitHub。\n适合已经开了代理的情况，速度最快。";
        dypDlg()
            .setTitle("GitHub 访问方式")
            .setMessage(msg)
            .setPositiveButton("切换方式", new android.content.DialogInterface.OnClickListener(){
                public void onClick(android.content.DialogInterface d, int w){
                    showGhPicker();
                }
            })
            .setNegativeButton("关闭", null)
            .show();
    }

    /** 第二步：真正选择 */
    void showGhPicker(){
        final String[] labels = {
            "镜像加速（推荐，国内可用）",
            "直连（自己挂代理时用，最快）"
        };
        int cur = GH_MODE_MIRROR.equals(ghMode(this)) ? 0 : 1;
        dypDlg()
            .setTitle("选择访问方式")
            .setSingleChoiceItems(labels, cur, new android.content.DialogInterface.OnClickListener(){
                public void onClick(android.content.DialogInterface d, int which){
                    String v = which == 0 ? GH_MODE_MIRROR : GH_MODE_DIRECT;
                    try{
                        getSharedPreferences(SP, MODE_PRIVATE).edit().putString(KEY_GH_MODE, v).apply();
                    }catch(Throwable t){}
                    d.dismiss();
                    Toast.makeText(MainActivity.this,
                        which == 0 ? "已切换为镜像加速" : "已切换为直连",
                        Toast.LENGTH_SHORT).show();
                    appLog("更新", "GitHub 访问方式=" + v);
                    buildSettingsPage();
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }

    /** 从 releases 列表页抓最新 tag */
    static String pickTag(String page){
        if(page == null) return null;
        String key = "/releases/tag/";
        int p = page.indexOf(key);
        if(p < 0) return null;
        int st = p + key.length();
        int en = st;
        while(en < page.length()){
            char ch = page.charAt(en);
            if((ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z') || (ch >= '0' && ch <= '9')
                || ch == '.' || ch == '_' || ch == '-') en++;
            else break;
        }
        if(en <= st) return null;
        return page.substring(st, en);
    }

/** 从 tag 详情页抓 apk 下载链接 */
    static String pickApk(String page, String tag){
        if(page == null) return null;
        // 页面里是绝对路径 /owner/repo/releases/download/tag/xxx.apk
        String key = "releases/download/" + tag + "/";
        int p = page.indexOf(key);
        if(p < 0){
            key = "releases/download/";
            p = page.indexOf(key);
            if(p < 0) return null;
        }
        // 往前回溯到路径起点（href="/ 之后）
        int st = p;
        while(st > 0){
            char ch = page.charAt(st - 1);
            if(ch == '"' || ch == '\'' || ch == '(' || ch == '>' || ch == ' ' || ch == '\n') break;
            st--;
        }
        int en = p;
        while(en < page.length()){
            char ch = page.charAt(en);
            if(ch == '"' || ch == '\'' || ch == '<' || ch == '>' || ch == ' ' || ch == 92 || ch == '\n') break;
            en++;
        }
        String path = page.substring(st, en);
        if(!path.toLowerCase().endsWith(".apk")) return null;
        if(path.startsWith("/")) return "https://gitee.com" + path;
        return "https://gitee.com/" + path;
    }

    /** 从 tag 详情页抓更新说明（正文在 <textarea class='content'> 里） */
    static String pickReleaseBody(String page){
        try{
            // 优先：rich-markdown-renderer 的 textarea
            int p = page.indexOf("<textarea class='content'");
            if(p < 0) p = page.indexOf("<textarea class=\"content\"");
            int st, en;
            if(p > 0){
                st = page.indexOf('>', p);
                if(st < 0) return null;
                en = page.indexOf("</textarea>", st);
                if(en < 0 || en <= st) return null;
            } else {
                // 兜底：老的 release-body 结构
                p = page.indexOf("release-body");
                if(p < 0) return null;
                st = page.indexOf('>', p);
                if(st < 0) return null;
                en = page.indexOf("</", st);
                if(en < 0 || en <= st) return null;
            }
            String raw = page.substring(st + 1, en);
            // Gitee 用 &#x000A; 表示换行
            raw = raw.replace("&#x000A;", "\n");
            raw = raw.replace("&#10;", "\n");
            raw = raw.replaceAll("<br[^>]*>", "\n");
            raw = raw.replaceAll("</p>", "\n");
            raw = raw.replaceAll("<[^>]+>", "");
            raw = raw.replace("&amp;", "&");
            raw = raw.replace("&lt;", "<");
            raw = raw.replace("&gt;", ">");
            raw = raw.replace("&nbsp;", " ");
            raw = raw.replace("&quot;", "\"");
            raw = raw.trim();
            // 去掉开头的版本标题行
            int nl = raw.indexOf('\n');
            if(nl > 0 && raw.substring(0, nl).indexOf("v1") >= 0 && nl < 40){
                raw = raw.substring(nl + 1).trim();
            }
            return raw.length() > 800 ? raw.substring(0, 800) + "..." : raw;
        }catch(Throwable t){ return null; }
    }

    int curVersionCode(){
        try{
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
        }catch(Throwable t){ return 1; }
    }
    String versionName(){
        try{
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        }catch(Throwable t){ return "?"; }
    }

    /** 解析版本号 → 与 Manifest 的 versionCode 使用同一套规则（每位占两位，预留空间）。
     *
     *  规则：major*1000000 + minor*10000 + patch*100 + sub
     *    1.3.7   → 1030700
     *    1.3.7.1 → 1030701
     *    1.3.7.3 → 1030703
     *    1.3.8   → 1030800   ← 三位版本也能正常递增
     *    1.4.0   → 1040000
     *    2.0.0   → 2000000
     *
     *  为什么不用老的 major*10000+minor*100+patch：
     *    1.3.7.1 的 versionCode 已是 103701，而 1.3.8 按老规则只有 10308，
     *    数值倒退会导致「装了 1.3.7.1 的用户永远收不到后续更新」。
     */
    static int parseVer(String tag){
        if(tag == null) return 0;
        String t = tag.trim().toLowerCase();
        if(t.startsWith("v")) t = t.substring(1);
        // 去掉可能带的 build 后缀等非版本内容
        int dash = t.indexOf('-');
        if(dash > 0) t = t.substring(0, dash);
        String[] parts = t.split("[.]");
        int major = 0, minor = 0, patch = 0, sub = 0;
        try{ if(parts.length > 0) major = Integer.parseInt(parts[0].replaceAll("[^0-9]", "")); }catch(Throwable e){}
        try{ if(parts.length > 1) minor = Integer.parseInt(parts[1].replaceAll("[^0-9]", "")); }catch(Throwable e){}
        try{ if(parts.length > 2) patch = Integer.parseInt(parts[2].replaceAll("[^0-9]", "")); }catch(Throwable e){}
        try{ if(parts.length > 3) sub   = Integer.parseInt(parts[3].replaceAll("[^0-9]", "")); }catch(Throwable e){}
        return major * 1000000 + minor * 10000 + patch * 100 + sub;
    }

    /** \u4ece JSON \u5b57\u7b26\u4e32\u91cc\u53d6 \"key\":\"value\" */
    static String jget(String json, String key){
        if(json == null) return "";
        int p = json.indexOf("\"" + key + "\"");
        if(p < 0) return "";
        return jgetFrom(json, p);
    }
    static String jgetFrom(String json, int fromIdx){
        try{
            int c = json.indexOf(':', fromIdx);
            if(c < 0) return null;
            int q1 = json.indexOf('"', c + 1);
            if(q1 < 0) return null;
            int i = q1 + 1; StringBuilder b = new StringBuilder();
            while(i < json.length()){
                char ch = json.charAt(i);
                if(ch == '\\'){ char e = json.charAt(i+1);
                    if(e=='n') b.append('\n'); else if(e=='t') b.append('\t'); else if(e=='r') b.append('\r');
                    else if(e=='u'){ b.append((char)Integer.parseInt(json.substring(i+2,i+6),16)); i+=4; }
                    else b.append(e);
                    i += 2; continue;
                }
                if(ch == '"') break;
                b.append(ch); i++;
            }
            return b.toString();
        }catch(Throwable t){ return null; }
    }

    void showUpdateDialog(String tag, String name, String bodyTxt, final String html,
                          final String apkUrl, final String apkName, int newVer, int curVer){
        showUpdateDialog(tag, name, bodyTxt, html, apkUrl, apkName, newVer, curVer, true);
    }

    /**
     * @param respectSnooze true = 尊重用户「下个版本再提醒」的选择（自动检查时用）
     *                      false = 手动点「检查更新」时无视 snooze，一定弹
     */
    void showUpdateDialog(String tag, String name, String bodyTxt, final String html,
                          final String apkUrl, final String apkName, int newVer, int curVer,
                          boolean respectSnooze){
        // 是否强制更新：服务端 version.json 带 force=1 / min_version 高于当前版本
        boolean force = updateIsForce(newVer, curVer);
        // 非强制时，用户之前选过「下个版本再提醒」就静默跳过
        if(!force && respectSnooze){
            int skip = getSharedPreferences(SP, MODE_PRIVATE).getInt(KEY_SKIP_VER, 0);
            if(newVer <= skip){
                Log.i("DYP", "UPDATE_SKIP new=" + newVer + " skip=" + skip);
                return;
            }
        }

        StringBuilder m = new StringBuilder();
        m.append("当前版本：").append(versionName()).append(" (").append(curVer).append(")\n");
        m.append("最新版本：").append(tag).append("\n");
        if(name != null && name.length() > 0) m.append("\n").append(name);
        if(bodyTxt != null && bodyTxt.length() > 0){
            String t = bodyTxt.length() > 600 ? bodyTxt.substring(0, 600) + "…" : bodyTxt;
            m.append("\n\n").append(t);
        }
        if(force){
            m.append("\n\n⚠ 此版本为强制更新（修复关键问题），必须更新后才能继续使用。");
        }

        // 必须用 dypDlg() 走 App 主题，否则 ColorOS 的 Theme.DeviceDefault
        // 默认解析成深色，会出现「App 亮色但更新弹窗全黑」
        android.app.AlertDialog.Builder b = dypDlg()
            .setTitle(force ? "需要更新（强制）" : "发现新版本")
            .setMessage(m.toString())
            .setCancelable(!force)
            .setPositiveButton(apkUrl.length() > 0 ? "立即更新" : "打开页面", new android.content.DialogInterface.OnClickListener(){
                public void onClick(android.content.DialogInterface d, int w){
                    if(apkUrl.length() > 0) downloadApk(apkUrl, apkName);
                    else openUrl(html);
                }
            });

        if(force){
            // 强制更新：只能选「立即更新」或退出，不给跳过
            b.setNegativeButton("退出", new android.content.DialogInterface.OnClickListener(){
                public void onClick(android.content.DialogInterface d, int w){
                    finish();
                }
            });
        } else {
            b.setNeutralButton("浏览器打开", new android.content.DialogInterface.OnClickListener(){
                public void onClick(android.content.DialogInterface d, int w){ openUrl(html); }
            });
            // 下个版本再提醒：记住当前最新版本号，下次更版前不再弹
            b.setNegativeButton("下个版本再提醒", new android.content.DialogInterface.OnClickListener(){
                public void onClick(android.content.DialogInterface d, int w){
                    getSharedPreferences(SP, MODE_PRIVATE).edit()
                        .putInt(KEY_SKIP_VER, newVer).apply();
                    Log.i("DYP", "UPDATE_SNOOZE ver=" + newVer);
                    Toast.makeText(MainActivity.this,
                        "已设置：下个版本发布前不再提醒", Toast.LENGTH_SHORT).show();
                }
            });
        }
        b.show();
    }

    /**
     * 是否强制更新。
     *
     * 判定顺序（任一满足即强制）：
     *  1) 服务端 version.json 的 force 字段为 1/true
     *  2) min_version（或 KEY_FORCE_MIN）高于当前 versionCode
     * 这样服务端只要在下发版本信息时带上 force/min_version 就能强推；
     * 服务器不可达时 force 恒为 false，不影响 App 正常使用。
     */
    boolean updateIsForce(int newVer, int curVer){
        try{
            SharedPreferences p = getSharedPreferences(SP, MODE_PRIVATE);
            // 本地缓存的强制最低版本（上次成功拉取 version.json 时写入）
            int minVer = p.getInt(KEY_FORCE_MIN, 0);
            if(minVer > 0 && curVer < minVer){
                Log.i("DYP", "UPDATE_FORCE by min_ver cur=" + curVer + " min=" + minVer);
                return true;
            }
            if(p.getBoolean("force_" + newVer, false)){
                Log.i("DYP", "UPDATE_FORCE by flag ver=" + newVer);
                return true;
            }
        }catch(Throwable t){}
        return false;
    }

    void openUrl(String u){
        try{ startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(u))); }
        catch(Throwable t){ s("无法打开浏览器"); }
    }

    /** 应用内下载新版 APK 到私有目录，带进度条，完成后自动拉起安装器 */
    void downloadApk(String url, String fname){
        final File target = ApkProvider.apkFile(this);
        if(target.exists()) target.delete();
        progCancel = false;
        s("准备下载更新...");
        progShow();
        progSet(0f, "准备下载更新");

        // GitHub 的 APK 地址如果是直连 github.com，镜像模式下依次试各镜像，失败回退直连
        if(ghUseMirror(this) && url != null && url.indexOf("github.com") >= 0){
            final String rawApk = url;
            new Thread(new Runnable(){ public void run(){
                String good = null;
                for(int i = 0; i < GH_MIRRORS.length; i++){
                    String test = mirrorify(rawApk, i);
                    if(InnerDownloader.headOk(test)){
                        good = test;
                        Log.i("DYP", "APK_MIRROR_OK idx=" + i + " " + GH_MIRRORS[i]);
                        break;
                    }
                    Log.i("DYP", "APK_MIRROR_FAIL idx=" + i);
                }
                final String use = (good != null) ? good : rawApk;
                if(good == null) Log.i("DYP", "APK_MIRROR_ALL_FAIL 回退直连");
                h.post(new Runnable(){ public void run(){ doDownloadApk(use, fname, target); }});
            }}).start();
            return;
        }
        doDownloadApk(url, fname, target);
    }

    /** 真正下载 APK（已确定最终 URL） */
    void doDownloadApk(String url, String fname, final File target){
        new Thread(new Runnable(){ public void run(){
            boolean ok = InnerDownloader.fetch(url, target, new InnerDownloader.Cb(){
                @Override public void onStart(long totalBytes){
                    progSet(0f, "下载更新  " + InnerDownloader.sizeText(totalBytes));
                }
                @Override public void onProgress(long done, long tot){
                    float r = tot > 0 ? (float)((double) done / tot) : 0f;
                    progSet(r, "下载更新  " + InnerDownloader.sizeText(done)
                            + (tot > 0 ? " / " + InnerDownloader.sizeText(tot) : ""));
                }
                @Override public void onDone(boolean ok2, File out, String msg){
                    Log.i("DYP", "APK_DL ok=" + ok2 + " " + msg);
                }
            });

            if(ok){
                progSet(1f, "下载完成");
                h.post(new Runnable(){ public void run(){
                    progHide();
                    // 弹「立即安装」对话框，用户点一下才拉起安装器
                    // （避免自动拉起被 ROM 拦截后用户找不到安装入口）
                    new android.app.AlertDialog.Builder(MainActivity.this)
                        .setTitle("下载完成")
                        .setMessage("新版本已下载完成。\n\n"
                            + "点击「立即安装」开始安装。\n"
                            + "如果系统拦截了安装请求，也可以稍后在设置里重新检查更新再装。")
                        .setPositiveButton("立即安装", new android.content.DialogInterface.OnClickListener(){
                            public void onClick(android.content.DialogInterface d, int w){
                                installApk();
                            }
                        })
                        .setNegativeButton("稍后", null)
                        .show();
                    s("下载完成，点击弹窗安装");
                }});
            } else {
                s("更新下载失败");
                progHide();
            }
        }}).start();
    }

    void showHintToast(String m){
        try{ Toast.makeText(this, m, Toast.LENGTH_LONG).show(); }catch(Throwable t){}
    }

    /** 拉起系统安装界面 */
    void installApk(){
        try{
            File f = ApkProvider.apkFile(this);
            if(!f.exists() || f.length() < 1024){
                s("下载文件无效"); return;
            }
            // 1) Android 8.0+ 必须拿到「安装未知应用」权限，否则系统直接丢弃 Intent
            if(android.os.Build.VERSION.SDK_INT >= 26){
                boolean ok = false;
                try{
                    ok = getPackageManager().canRequestPackageInstalls();
                }catch(Throwable t){}
                if(!ok){
                    showInstallPermissionDialog();
                    return;
                }
            }
            // 2) 校验 APK 头，避免把 Gitee 返回的错误页当安装包
            if(!looksLikeApk(f)){
                s("安装包损坏");
                showHintToast("下载的文件不是有效 APK，请重新检查更新");
                return;
            }
            // 3) 用 content:// 交给安装器，并显式授予读权限
            android.net.Uri uri = ApkProvider.apkUri(this);
            Intent it = new Intent(Intent.ACTION_VIEW);
            it.setDataAndType(uri, "application/vnd.android.package-archive");
            it.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            // 部分 ROM（ColorOS/MIUI）需要 ClipData 才会真正把 grant 传下去
            it.setClipData(android.content.ClipData.newRawUri("apk", uri));
            startActivity(it);
            s("已打开安装器");
        }catch(android.content.ActivityNotFoundException e){
            Log.i("DYP", "INSTALL_NO_ACTIVITY " + e);
            s("没有可用的安装器");
            showHintToast("请到文件管理器手动安装 Download/Android/data/com.re.douyinparse/files/update.apk");
        }catch(Throwable t){
            Log.i("DYP", "INSTALL_ERR " + t);
            s("安装失败：" + t);
            showHintToast("可手动安装：Android/data/com.re.douyinparse/files/update.apk");
        }
    }

    /** 前 4 字节是 PK\x03\x04 才认为是 APK（zip） */
    static boolean looksLikeApk(File f){
        java.io.FileInputStream in = null;
        try{
            in = new java.io.FileInputStream(f);
            byte[] h = new byte[4];
            if(in.read(h) != 4) return false;
            return h[0] == 0x50 && h[1] == 0x4B;
        }catch(Throwable t){
            return false;
        }finally{
            try{ if(in != null) in.close(); }catch(Throwable t){}
        }
    }

    /** 「安装未知应用」未授权时的引导弹窗 */
    void showInstallPermissionDialog(){
        pendingInstallApk = true;
        dypDlg()
            .setTitle("需要安装权限")
            .setMessage("系统尚未允许本应用安装 APK。\n\n"
                + "请在接下来的页面里找到「DY解析器」，打开「允许安装未知应用」，然后返回本应用，会自动继续安装。\n\n"
                + "也可以手动到「设置 → 应用 → 特殊权限 → 安装未知应用」中开启。")
            .setPositiveButton("去开启", new android.content.DialogInterface.OnClickListener(){
                public void onClick(android.content.DialogInterface d, int w){
                    try{
                        Intent it = new Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
                        it.setData(android.net.Uri.parse("package:" + getPackageName()));
                        startActivity(it);
                    }catch(Throwable t){
                        try{
                            startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES));
                        }catch(Throwable t2){
                            showHintToast("请手动到系统设置中开启安装权限");
                        }
                    }
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }

    /** 免责声明弹窗：必须同意才能用；5 秒倒计时后可点同意，不同意退出 */
    void showAgreementDialog(){
        final android.app.AlertDialog dlg = dypDlg()
            .setTitle("用户协议与免责声明")
            .setMessage(
                "1. 本软件全程免费，无任何收费项目；\n"
              + "   若您是通过付费购买获得，请立即申请退款。\n\n"
              + "2. 本软件仅供个人学习与技术研究使用，\n"
              + "   严禁用于任何商业用途、批量抓取或侵犯\n"
              + "   他人权益的场景。\n\n"
              + "3. 所有解析内容版权归原作者所有，\n"
              + "   请勿传播、转售或用于盈利。\n\n"
              + "4. 如内容涉及侵权，请联系删除：\n"
              + "   2137190676@qq.com\n\n"
              + "同意即表示您已阅读并接受以上全部条款。")
            .setCancelable(false)
            .setPositiveButton("同意（5秒）", new android.content.DialogInterface.OnClickListener(){
                public void onClick(android.content.DialogInterface d, int w){
                    // 5 秒倒计时结束前点同意无效：关闭弹窗但不记录，下次启动重新弹
                }
            })
            .setNegativeButton("不同意并退出", new android.content.DialogInterface.OnClickListener(){
                public void onClick(android.content.DialogInterface d, int w){
                    finishAffinity();
                }
            })
            .show();
        // 5 秒倒计时（每秒更新按钮文案：5→4→3→2→1→我同意）
        final int[] count = {5};
        final Runnable tick = new Runnable(){ public void run(){
            try{
                final android.widget.Button pos = dlg.getButton(android.app.AlertDialog.BUTTON_POSITIVE);
                if(pos == null) return;
                count[0]--;
                if(count[0] > 0){
                    pos.setText("同意（" + count[0] + "秒）");
                    h.postDelayed(this, 1000);
                } else {
                    pos.setText("我同意");
                    pos.setEnabled(true);
                    pos.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){
                        getSharedPreferences(SP, MODE_PRIVATE).edit()
                                .putBoolean(KEY_AGREE, true).commit();
                        dlg.dismiss();
                    }});
                }
            }catch(Throwable t){}
        }};
        h.postDelayed(tick, 1000);
    }

    /** 年月日時分 */
    void showAbout(){
        String txt =
            "版本：" + versionName() + " (build " + curVersionCode() + ")\n"
          + "作者：大爱仙尊 / 尘\n\n"
          + "【软件声明】\n"
          + "本软件全程免费且开源，严禁用于任何商业用途或违法行为。"
          + "使用者应自行承担使用风险，包括但不限于违反平台服务条款、侵犯内容版权等。"
          + "因使用本软件产生的一切后果由使用者自负，作者不承担任何责任。\n\n"
          + "【技术说明】\n"
          + "仅解析公开分享页面，不存储任何用户数据，不使用、不上传任何账号信息。\n\n"
+ "【为什么用服务器】\n"
           + "抖音实况照片解析需要访客凭据（UIFID_TEMP），普通用户拿不到。"
           + "服务器内置凭据池代为签名，让你开箱即用、解析更快更稳。\n"
           + "用服务器：实况照片免填凭据、秒解析、抗风控更强。\n"
           + "不用服务器：可以在 App 内一键从本机浏览器抓取凭据（设置 → 抖音访客凭据 → 从浏览器抓取），"
           + "失效后重抓一次即可；服务器不可达时 App 自动回退本地解析。\n"
           + "服务器有效期至：2027-04-20（到期后自动降级，不影响使用）\n\n"
          + "【问题反馈】\n"
          + "QQ：" + APP_QQ + "\n"
          + "反馈群：" + APP_QQ_GROUP + "\n\n"
+ "【开源地址】\n"
           + "Gitee：" + "https://gitee.com/" + GITEE_OWNER + "/" + GITEE_REPO + "\n"
           + "GitHub：" + "https://github.com/" + GH_OWNER + "/" + GH_REPO + "\n\n"
           + "【更新】\n"
           + "新版本同步发布在 Gitee 与 GitHub Releases。\n"
           + "当前更新源：" + platformName() + "（可在设置里切换），点下方「检查更新」即可。";

        int bgDlg   = curDark ? 0xFF191B21 : 0xFFFFFFFF;
        int textDlg = curDark ? 0xFFF2F3F5 : 0xFF1A1A1F;

        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setBackgroundColor(bgDlg);
        wrap.setPadding(dp(20), dp(16), dp(20), dp(4));

        TextView tv = new TextView(this);
        tv.setText(txt);
        tv.setTextSize(13);
        tv.setTextColor(textDlg);
        tv.setLineSpacing(dp(5), 1f);
        tv.setTextIsSelectable(true);
        wrap.addView(tv);

        android.app.AlertDialog dlg = dypDlg()
            .setTitle("关于")
            .setView(wrap)
            .setPositiveButton("关闭", null)
            .create();
        dlg.show();
        try{
            android.view.Window win = dlg.getWindow();
            if(win != null){
                win.setBackgroundDrawable(makeRoundRect(bgDlg, curDark ? 0xFF2B2F39 : 0xFFE3E5EA, 16));
            }
        }catch(Throwable t){}
    }

    int dp(int v){ return (int)(v * getResources().getDisplayMetrics().density); }

    /** 通用长文本弹窗：套 ScrollView + 高度上限，避免盖住按钮 */
    void showLongTextDialog(String title, String txt, String posBtn, final Runnable posAction){
        int bgDlg   = curDark ? 0xFF191B21 : 0xFFFFFFFF;
        int textDlg = curDark ? 0xFFF2F3F5 : 0xFF1A1A1F;

        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setBackgroundColor(bgDlg);
        wrap.setPadding(dp(18), dp(14), dp(18), dp(8));

        TextView tv = new TextView(this);
        tv.setText(txt);
        tv.setTextSize(12);
        tv.setTextColor(textDlg);
        tv.setLineSpacing(dp(6), 1f);
        tv.setTextIsSelectable(true);
        // 关键：必须显式 WRAP_CONTENT，否则在 ScrollView 里会被拉伸成固定高度，文字不渲染
        tv.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT));
        wrap.addView(tv);

        android.widget.ScrollView sv = new android.widget.ScrollView(this);
        sv.addView(wrap, new android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
            android.widget.FrameLayout.LayoutParams.WRAP_CONTENT));
        int maxH = (int)(getResources().getDisplayMetrics().heightPixels * 0.62f);
        sv.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, maxH));

        android.app.AlertDialog.Builder b = dypDlg()
            .setTitle(title)
            .setView(sv);
        if(posBtn != null && posAction != null){
            b.setPositiveButton(posBtn, new android.content.DialogInterface.OnClickListener(){
                public void onClick(android.content.DialogInterface d, int w){ posAction.run(); }
            });
        }
        b.setNegativeButton("关闭", null);
        android.app.AlertDialog dlg = b.create();
        dlg.show();
        try{
            android.view.Window win = dlg.getWindow();
            if(win != null){
                win.setBackgroundDrawable(makeRoundRect(bgDlg, curDark ? 0xFF2B2F39 : 0xFFE3E5EA, 16));
            }
        }catch(Throwable t){}
    }

    /** 更新日志 */
    void showChangelog(){
        showLongTextDialog("更新日志", changelogText(), "检查更新",
            new Runnable(){ public void run(){ checkUpdate(false); }});
    }

/** 内置的更新日志文案（随版本更新，与 CHANGELOG.md 保持同步） */
    static String changelogText(){
        return
            "【v1.3.7.4】批量解析重构：静默解析 + 结果进列表 + 退出重进可恢复\n"
             + "新增\n"
             + "· 更新源可自由切换：设置 → 更新源（Gitee / GitHub）\n"
             + "· GitHub 访问方式（设置 → GitHub 访问方式）：镜像加速 / 直连\n"
             + "  · 镜像加速会自动从 4 个公开镜像里挑可用的，全部失效时自动回退直连\n"
             + "  · 不用自己挂代理也能在 GitHub 源下检查更新和下载 APK\n"
             + "· 自动播放关闭时显示封面预览图：解析完不再黑屏，点「播放」直接切回视频\n"
             + "· 解析后自动播放 / 自动下载开关（设置 → 解析后动作，默认都关闭），两个都开则同时进行\n"
             + "· 解析成功后清空链接开关（默认关闭）：解析完自动清空输入框\n"
             + "· 剪贴板自动识别（内置常开，无开关）：回前台自动读口令填链接，支持一次识别多条\n"
             + "· 多链接批量解析：一行一个链接，串行静默执行，跑完统一进列表页\n"
             + "· 批量结果进「列表」页：逐条可下载 / 解析看详情，结果追加不清掉上一次\n"
             + "· 列表持久化（设置 → 保留解析列表，默认开启）：退出重进仍在，可继续追加\n"
             + "· 清空列表按钮（带二次确认）、上一条 / 下一条导航（显示 2/3 位置）\n"
             + "· 主页/合集批量下载可选第几个：[选择下载] 多选 + [下载全部]，只处理当前页\n"
             + "· 主页 / 合集 / 短剧解析前置确认：说明会抓全量并清空列表；开了自动下载时额外警告\n"
             + "优化\n"
             + "· 批量解析间隔：固定 15 秒改为「解析完 3 秒后推进」+ 60 秒兜底超时\n"
             + "· 批量结果列表标题按来源区分：批量解析 / 主页\n"
             + "· 主页 / 合集解析先自动清空列表（批量模式下不清，保护自己收集的结果）\n"
             + "修复\n"
             + "· 批量解析最后一条永不渲染（列表空但清空说有 4 条记录）：batchItemFinished 里提前 return，\n"
             + "  导致 BATCH_DONE 和列表渲染永不执行\n"
             + "· 自动播放开关无效（默认关闭时解析视频仍会自动播放）：render 视频分支写死 doPlay()\n"
             + "· 批量模式下主页判定误清空自己的结果：4 处主页入口无条件清空列表\n"
             + "· 页面在确认框弹出瞬间就抓完整页数据绕过确认：现改为确认期间拦截请求 + 丢弃页面数据，\n"
             + "  确认后再主动发起签名翻页\n"
             + "· 华为 P40 / Android 10 等系统下载失败（能播能听就是下不了）：\n"
             + "  Manifest 缺 requestLegacyExternalStorage，Android 10 强制分区存储导致写不进公共目录。\n"
             + "  已补该开关，并新增 MediaStore 兜底：目录不可写时先下到私有目录，\n"
             + "  下完自动搬进「内部存储/Download/DYParse/<分类>/」，Android 8~17 全兼容。\n"
             + "  设置页新增「存储写入检测」，可自查权限与目录可写状态\n"
             + "· Android 7/8 下载崩溃：通知用了 API 26+ 才有的双参构造，低版本会 NoSuchMethodError\n"
             + "  导致前台服务起不来。已按版本分开构造，Android 7 起可正常下载\n"
             + "· Android 7 权限被拒后无法下载：系统不再弹窗。现改为启动时主动申请一次，\n"
             + "  被永久拒绝时弹「需要存储权限」引导，可直接跳系统设置开启\n\n"
             + "──────────────\n"
             + "【v1.3.7.3】版本号算法重构 + 弹窗主题修复（重要）\n"
             + "修复\n"
             + "· 检查更新失效（一直提示「已是最新版」但实际有新版）：parseVer 只解析三段版本号，\n"
             + "  v1.3.7.2 被算成 10307，远小于当前版本号 → 永远判「已是最新」\n"
             + "· 更新源被错误覆盖：自建服务器拿到 tag 后会被 Gitee API 无条件覆盖，现改为只在更高版本时覆盖\n"
             + "· 暗色模式下「发现新版本」等 15 处弹窗显示为亮色/深色错乱：全部统一走 App 主题\n"
             + "变更\n"
             + "· 版本号规则改为每段占两位：major*1000000 + minor*10000 + patch*100 + sub\n"
             + "  1.3.7→1030700、1.3.7.1→1030701、1.3.7.3→1030703、1.3.8→1030800、1.4.0→1040000\n"
             + "· 新增服务器公告通道 upgrade_notice：可下发标题/正文/链接/生效版本阈值\n"
             + "提示\n"
             + "· v1.3.7.1 和 v1.3.7.2 的版本号算法有缺陷，检测不到后续更新，\n"
             + "  需到发布页手动下载一次（覆盖安装不丢数据），装完后续更新恢复正常\n\n"
             + "──────────────\n"
             + "【v1.3.7.2】抖音主页全量翻页 + 登录体验统一\n"
             + "修复\n"
             + "· 抖音主页只出 20 条就停：现按 max_cursor 反复翻页（每页重算 a_bogus 签名），\n"
             + "  实测 280 条作品 16 页全部翻完\n"
             + "· 连续解析两个主页时第二个只出一部分：主页模式状态未重置，旧数据不清 + 请求被挡\n"
             + "· 抖音登录操作条崩溃（ClassCastException: LinearLayout cannot be cast to FrameLayout）\n"
             + "新增\n"
             + "· 抖音 / YouTube / B站 / Instagram 登录后自动退出登录页回到解析页，不保留网页\n\n"
             + "──────────────\n"
             + "【v1.3.7.1】抖音实况图凭据自动获取 + 本地解析\n"
             + "新增\n"
             + "· 抖音访客凭据自动获取：首次解析实况图时后台静默获取，实测 2-3 秒，全程无需操作\n"
             + "· 设置 → 抖音访客凭据：可手动重新获取、手动粘贴、清空，并显示当前凭据状态\n"
             + "· 抖音实况图改为「本地优先」解析：有凭据时直接本地签名，不再经过服务器\n"
             + "· 获取失败时弹窗说明原因，提供「重试 / 手动填写」两个入口\n"
             + "修复\n"
             + "· 抖音实况图只有 1 张静态图、没有动图（LIVE 角标不出来）：详情接口强制要求访客凭据，\n"
             + "  缺少时固定返回 403 Blocked by ArgusSecurityPlugin；现改为自动获取凭据后本地签名\n"
             + "· 实况图下载提示文案错误：之前写「可存为 MP4」，实际一直是 .jpg 实况照片，已改正\n"
             + "· 平台支持说明里抖音一栏补充实况图机制说明\n"
             + "变更\n"
             + "· 解析优先级调整：无凭据走服务器，有凭据走本地（本地失败自动回退服务器）\n"
             + "· 服务端 8443 TLS 阻塞热修 + 自愈守卫（详见更新日志 v1.3.7）\n"
             + "· 版本号规则支持四位（1.3.7.1 = build 103701）\n\n"
             + "──────────────\n"
             + "【v1.3.7】B站直连提速 + 合集解析 + YouTube回退修复 + TikTok图文修复\n"
             + "修复\n"
             + "· B站视频播放不再走服务器中转：CDN 现强制校验 buvid3 且拒绝手机 UA，已改为自动取 buvid3 + 桌面 UA 直连，秒开\n"
             + "· B站下载同步改为直连，速度不再受服务器带宽限制\n"
             + "· YouTube 第二次解析不再回退（没画面没进度条、过一会提示被 CDN 拒绝）：回退标志从不复位，已改为每次解析前重置\n"
             + "· TikTok 图文加载不出来：服务器 fetch_media 缩进错误，缓存未命中时返回 None，已重写\n"
             + "· TikTok 图文同一张图被并发重复请求导致连接被写坏，已加图片内存缓存与去重\n"
             + "新增\n"
             + "· B站合集解析：整个合集的每一集都列出来，每集用自己的 aid 下载/播放（合集里各集 aid 不同）\n"
             + "· 抖音/B站合集统一进「列表」页，解析完自动切过去并提示，主解析页保留原详情\n"
             + "· 列表「选择下载」：可勾选任意几集一起下，也有「全选」\n"
             + "· B站画质升级 DASH：匿名 480P/720P，登录 1080P，大会员 1080P60，4K 原片可拿 4K\n"
             + "· B站 Cookie（设置 → Cookie → B站 Cookie），填 SESSDATA 提画质；不填也能用\n"
             + "变更\n"
             + "· B站 CDN 拉流使用桌面 UA（仅 CDN，不影响 API）\n"
             + "· B站播放下载改为视频轨+音频轨双轨，播放器合并、下载后无损合成 mp4\n\n"
             + "──────────────\n"
             + "【v1.3.6】Instagram 登录解析 + 代理简化\n"
             + "新增\n"
             + "· Instagram 登录解析：App 内登录 IG，Cookie 存手机本地（不上传），可解析 Reel 视频并下载\n"
             + "· Instagram 主页解析：用户名主页列出全部作品，每页 100 条带翻页\n"
             + "· Instagram 登录前风险提示（每次登录都会弹出，需确认后才进入）\n"
             + "· B站解析：单视频 / av / BV / 分P合集 + 空间主页，本地直连\n"
             + "· Cookie 本地保存：设置 → Cookie，一行一个 name=value，可手动填可清空\n"
             + "· 设置页新增「Cookie（本地保存）」分区\n"
             + "变更\n"
             + "· 代理简化为单一「内置代理」开关（默认关）：开=海外平台走服务器中转，关=全部直连\n"
             + "· 不再自动探测/套用用户代理，App 不干预你自己的 TUN / 代理\n"
             + "· X 解析：内置代理关时优先直连，失败再兜底服务器\n"
             + "· TikTok 请求与下载一律直连\n"
             + "修复\n"
             + "· X 单推文链接被主页判定误吞\n"
             + "· IG 视频直链错放图集字段导致走图片队列\n\n"
             + "──────────────\n"
             + "【v1.3.5】YouTube 登录解析 + 下载提速\n"
             + "新增\n"
             + "· YouTube 登录：App 内登录 Google 账号，自动抓取 Cookie 上传服务器，解 YouTube 风控\n"
             + "· YouTube 服务器流式中转（/api/yt/stream），带进度条拖动\n"
            + "· YouTube 主页列表下载自动入队（解析完不用再点一次）\n"
            + "优化\n"
            + "· YouTube 下载走服务器中转（直链绑解析 IP 会 403），已缓存视频秒下\n"
            + "· YouTube 播放直连优先，403 自动回退服务器中转\n"
            + "· YouTube 优先 H.264（AV1 部分设备解码不了）\n"
            + "· YouTube 解析合并 yt-dlp 调用（60s → 22s，二次解析 0s）\n"
            + "· 服务器 yt-dlp-ejs + node 解 n-challenge（新格式全恢复）\n"
            + "· 所有弹窗跟随 App 主题（修复暗色弹窗）\n"
            + "修复\n"
            + "· YouTube 下载空文件（403 时残留半成品）\n"
            + "· 下载失败文件名空指针崩溃\n"
            + "· 服务器地址弹窗精简为只有服务器地址\n"
            + "· 用户协议/YouTube 登录弹窗暗色显示异常";
    }
    static String changelogOld(){
        return
              "【v1.3.3】实况照片 + 动图目录\n"
           + "· 多图/实况多选下载：可选择下载第几张\n"
          + "· 视频合集按集下载，文件名自动带「第 N 集」\n"
          + "· 下载完成显示实际保存路径\n"
          + "· 快手图文 / live 图集解析（SINGLE_PICTURE / HORIZONTAL_ATLAS）\n"
          + "· 快手背景音乐适配：完整音轨 m4a 直接下载\n"
          + "· 图片后缀自动修正：WEBP 不再存成 .jpg\n"
          + "· 「修复媒体库」：历史文件一键补录到相册\n\n"
          + "更新策略\n"
          + "· 不再强制更新：可选「立即更新 / 下个版本再提醒」\n"
          + "· 修复重大问题时服务端下发 force，仍会强制更新\n\n"
          + "修复\n"
          + "· 相册看不到已下载文件（未通知媒体库）\n"
          + "· 连续解析时残留上一次结果\n"
          + "· 先解析图文再解析实况，实况退化为静态图\n"
          + "· 图片点不开大图\n"
          + "· 同一张图多 CDN 被识别成多张\n\n"
          + "──────────────\n"
          + "【v1.3.2】双源更新 + BGM 提取\n"
          + "· 更新源可切换：Gitee / GitHub\n"
          + "· BGM 提取：抖音背景音乐原曲直接下载\n"
          + "· 设置页重构、运行日志\n\n"
          + "【v1.3.1】内置服务器加速\n"
          + "· 抖音实况免填凭据（服务器代签）\n"
          + "· 四层安全：混淆 / HTTPS / 证书 pin / HMAC\n\n"
          + "【v1.3.0】实况照片版本\n"
          + "· 快手解析新增，TikTok 解析完全重写\n"
          + "· 抖音实况图解析与 MP4 导出\n\n"
          + "──────────────\n"
          + "【v1.1.3】\n"
          + "· TikTok 解析支持\n"
          + "· 短剧 / 合集解析，自动翻页\n"
          + "· 节点体检、仅 TikTok 走代理开关\n"
          + "· 修复 MP3 导出静音\n\n"
          + "【v1.1.2】\n"
          + "· 检查更新三级回退\n"
          + "· 代理设置界面\n\n"
          + "【v1.1.0】\n"
          + "· 首个公开版本：抖音解析 + 独立播放器";
    }

    /** 平台支持说明：各平台支持清单 */
    void showPlatformHelp(){
        String txt =
            "【抖音】\n"
          + "支持：✓ 视频解析  ✓ 图文解析  ✓ 实况(live图)解析\n"
          + "　　　✓ 合集解析  ✓ 短剧合集  ✓ 主页解析+批量下载\n"
          + "不支持：✗ 付费/私密视频  ✗ 限时日常\n"
          + "方式：WebView 加载页面 + JS 注入抽取数据（无需登录/签名）\n"
          + "　　　实况动轨走 PC 详情接口，需要「访客凭据」：\n"
          + "　　　　首次解析时自动后台获取（2-10 秒，静默无感）\n"
          + "　　　　也可在 设置 → 抖音访客凭据 里手动获取/重取\n"
          + "　　　拿到凭据后走本地签名，不再依赖服务器\n\n"
          + "【快手】\n"
          + "支持：✓ 视频解析  ✓ 图文解析  ✓ 单集解析\n"
          + "　　　✓ 独立音轨  ✓ 实况图（分享页下发动图轨道时）\n"
          + "不支持：✗ 主页解析（需滑块验证码）  ✗ 合集列表\n"
          + "方式：纯 HTTP 解析 INIT_STATE（无需登录/签名）\n\n"
          + "【TikTok】\n"
          + "支持：✓ 视频解析  ✓ 图文解析  ✓ 主页解析+批量下载\n"
          + "不支持：✗ 付费/私密视频\n"
          + "方式：服务器中转（需机场代理出口）\n\n"
          + "【X（推特）】\n"
          + "支持：✓ 视频解析  ✓ 图文解析  ✓ 多视频选择\n"
          + "　　　✓ 主页列表  ✓ 外链卡片深挖\n"
          + "不支持：✗ 私密账号\n"
          + "方式：fxtwitter + syndication 双源，优先直连，失败再兜底服务器\n\n"
          + "【YouTube】\n"
          + "支持：✓ 视频解析（最高 4K）  ✓ 主页解析+批量下载\n"
          + "不支持：✗ 会员专享  ✗ 付费频道\n"
          + "方式：服务器 yt-dlp 中转解析（po_token 校验只能在服务器过）\n\n"
+ "【B 站】\n"
           + "支持：✓ 视频解析（DASH，可到 1080P60/4K）  ✓ 合集(ugc_season)  ✓ 分P\n"
           + "　　　✓ 主页解析+批量下载  ✓ 纯 av号/BV号 直接输入  ✓ b23.tv 短链\n"
           + "不支持：✗ 大会员专享  ✗ 付费视频\n"
           + "方式：view/playurl API 本地直连免签；CDN 拉流用桌面 UA + buvid3\n"
           + "　　　匿名封顶 480P/720P，登录（填 SESSDATA）1080P，大会员 1080P60\n"
           + "　　　可在 App 内登录 B站 自动抓 Cookie（只存本地，不上传）\n\n"
           + "【Instagram】\n"
           + "支持：✓ Reel/视频解析  ✓ 图文多图  ✓ 主页解析+批量下载\n"
           + "不支持：✗ 私密账号  ✗ 快拍(Stories)\n"
           + "方式：需在 App 内登录 IG（Cookie 存本地不上传），带登录态直连解析；\n"
           + "　　　匿名只能拿封面，视频流一律 403。\n"
           + "　　　登录前请先在官方 App 登录过该账号，否则有封号风险。\n\n"
          + "【通用】\n"
          + "· 只解析公开分享页，不解密加密内容，不存储账号信息。\n"
          + "· 文件名前缀：dy_抖音 ks_快手 tt_TikTok x_X yt_YouTube bl_B站 ins_IG\n"
          + "· CDN 直链有时效，过期需重新解析。";

        int bgDlg   = curDark ? 0xFF191B21 : 0xFFFFFFFF;
        int textDlg = curDark ? 0xFFF2F3F5 : 0xFF1A1A1F;

        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setBackgroundColor(bgDlg);
        wrap.setPadding(dp(18), dp(14), dp(18), dp(8));

        TextView tv = new TextView(this);
        tv.setText(txt);
        tv.setTextSize(12);
        tv.setTextColor(textDlg);
        tv.setLineSpacing(dp(6), 1f);
        tv.setTextIsSelectable(true);
        // 关键：必须显式 WRAP_CONTENT，否则在 ScrollView 里会被拉伸成固定高度，
        // 导致文字不渲染（uiautomator 读到的 text 为空）
        tv.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT));
        wrap.addView(tv);
        android.widget.ScrollView sv = new android.widget.ScrollView(this);
        sv.addView(wrap, new android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
            android.widget.FrameLayout.LayoutParams.WRAP_CONTENT));
        int maxH = (int)(getResources().getDisplayMetrics().heightPixels * 0.62f);
        sv.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, maxH));

        android.app.AlertDialog dlg = dypDlg()
            .setTitle("平台支持说明")
            .setView(sv)
            .setPositiveButton("代理设置", new android.content.DialogInterface.OnClickListener(){
                public void onClick(android.content.DialogInterface d, int w){ showProxyDialog(); }
            })
            .setNegativeButton("关闭", null)
            .create();
        dlg.show();
        try{
            android.view.Window win = dlg.getWindow();
            if(win != null){
                win.setBackgroundDrawable(makeRoundRect(bgDlg, curDark ? 0xFF2B2F39 : 0xFFE3E5EA, 16));
            }
        }catch(Throwable t){}
    }

    /** 当前这条 URL 的代理指纹：开关状态变化要重建播放器 */
    String proxyCacheKey(String url){
        try{
            SharedPreferences p = getSharedPreferences(SP, MODE_PRIVATE);
            boolean on = p.getBoolean(KEY_PROXY_ON, false);
            return on ? "builtin" : "off";
        }catch(Throwable t){ return "err"; }
    }

    /** ExoPlayer 走代理用的 DataSource：用标准 HttpURLConnection 带 Proxy 拉流 */
    static class ProxyHttpDataSource implements androidx.media3.datasource.DataSource {
        private final java.net.Proxy proxy;
        private final String ua;
        private final String referer;
        private final String cookie;
        private java.io.InputStream in;
        private java.net.HttpURLConnection conn;
        private android.net.Uri curUri;
        private long bytesRead = 0;
        private boolean opened = false;

        ProxyHttpDataSource(java.net.Proxy px, String ua, String referer){ this(px, ua, referer, null); }
        ProxyHttpDataSource(java.net.Proxy px, String ua, String referer, String cookie){
            this.proxy = px; this.ua = ua; this.referer = referer; this.cookie = cookie;
        }

        @Override public void addTransferListener(androidx.media3.datasource.TransferListener l){}

        @Override public long open(androidx.media3.datasource.DataSpec ds) throws java.io.IOException{
            close();
            try{
                URL u = new URL(ds.uri.toString());
                conn = (java.net.HttpURLConnection) u.openConnection(proxy);
                conn.setInstanceFollowRedirects(true);
                conn.setRequestProperty("User-Agent", ua);
                conn.setRequestProperty("Referer", referer);
                if(cookie != null && cookie.length() > 0){
                    conn.setRequestProperty("Cookie", cookie);
                }
                conn.setRequestProperty("Accept", "*/*");
                // Range 透传，保证 seek 可用
                long pos = ds.position;
                if(pos > 0 || ds.length != -1){
                    StringBuilder r = new StringBuilder("bytes=").append(pos).append('-');
                    if(ds.length != -1) r.append(pos + ds.length - 1);
                    conn.setRequestProperty("Range", r.toString());
                }
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(20000);
                in = conn.getInputStream();
                curUri = ds.uri;
                bytesRead = 0;
                opened = true;
                long len = conn.getContentLength();
                if(pos > 0 && len > 0) return len;      // partial
                if(len > 0) return len;
                return -1;                              // LENGTH_UNSET
            }catch(java.io.IOException e){
                throw e;
            }catch(Throwable t){
                throw new java.io.IOException("proxy open failed: " + t);
            }
        }

        @Override public int read(byte[] buf, int off, int len) throws java.io.IOException{
            if(in == null) return -1;
            int n = in.read(buf, off, len);
            if(n == -1) return -1;
            bytesRead += n;
            return n;
        }

        @Override public android.net.Uri getUri(){
            return curUri != null ? curUri : android.net.Uri.EMPTY;
        }

        @Override public void close() throws java.io.IOException{
            opened = false;
            try{ if(in != null) in.close(); }catch(Throwable t){}
            in = null;
            try{ if(conn != null) conn.disconnect(); }catch(Throwable t){}
            conn = null;
        }
    }

    /** 节点体检用：一个公开的 TikTok 视频 CDN 直链样例。
     * 直链会过期，但用于判断「CDN 是否对本机 IP 放行」足够：
     * 放行 → 403 之外的状态码；被拒 → 403。 */
    static final String SAMPLE_TT_VIDEO =
        "https://v16-webapp-prime.tiktok.com/video/tos/alisg/tos-alisg-ve-37c799-sg/o4jEWENDAeBBm8AFfuqREQApIxFSzhEOYC7GOV/"
        + "?a=1988&bti=ODszNWYuMDE6&&bt=1635&ft=-Csk_mugPD12NSEO6n-Ux912bY6e3wv252cAp"
        + "&mime_type=video_mp4&rc=ZWg1NDMzNDZoNTg8aDZlaUBpanhmZG45cjNyZDMzaTczNEAyMy1gNS1jNS8xMi41MzRgYSMzLV5wMmRrci9hLS1kMTJzcw%3D%3D"
        + "&expire=1792497612&l=20261018200004FA54794C35955107E0A6&ply_type=2&policy=2"
        + "&signature=b9172c24aa5ebc6fd2f0d40f54871551&tk=tt_chain_token&btag=e000b0000";

    /** 节点体检：判断当前出口 IP 是否能拿到 TikTok 视频 */
    void showNetCheck(){
        final android.app.AlertDialog dlg = dypDlg()
            .setTitle("节点体检")
            .setMessage("正在检测…")
            .setPositiveButton("关闭", null)
            .create();
        dlg.show();
        final String testUrl = (curPlay != null && curPlay.length() > 0) ? curPlay : null;
        new Thread(new Runnable(){ public void run(){
            StringBuilder r = new StringBuilder();
            boolean isDC = false;          // 是否机房 IP
            boolean known = false;         // 是否成功判定
            String country = "";

            // ===== 1. 出口 IP 归属 =====
            try{
                String j = httpGet("http://ip-api.com/json/?fields=status,country,countryCode,regionName,city,isp,org,as,proxy,hosting");
                if(j == null || j.indexOf("\"fail\"") >= 0){
                    j = httpGet("https://ipinfo.io/json");
                }
                Log.i("DYP", "NETCHECK_IP " + j);
                if(j != null){
                    Object o = new JP(j).parse();
                    if(o instanceof Map){
                        Map mm = (Map)o;
                        country = gs(get(mm, "regionName"));
                        String cc  = gs(get(mm, "countryCode"));
                        String geo = gs(get(mm, "country"));
                        String city = gs(get(mm, "city"));
                        if(country.length() == 0) country = city;
                        if(geo.length() == 0) geo = cc;
                        String isp = gs(get(mm, "isp"));
                        String org = gs(get(mm, "org"));
                        String as  = gs(get(mm, "as"));
                        String hosting = gs(get(mm, "hosting"));

                        r.append("【出口地区】").append(geo);
                        if(cc.length() > 0 && !geo.equals(cc)) r.append(" / ").append(cc);
                        if(country.length() > 0) r.append(" · ").append(country);
                        r.append("\n");

                        String who = isp.length() > 0 ? isp : org;
                        if(who.length() > 0) r.append("【运营商】").append(who).append("\n");
                        if(as.length() > 0) r.append("【AS】").append(as).append("\n");

                        String low = (who + " " + as).toLowerCase();
                        boolean kw = low.indexOf("hosting") >= 0
                            || low.indexOf("server") >= 0
                            || low.indexOf("cloud") >= 0
                            || low.indexOf("data center") >= 0
                            || low.indexOf("datacenter") >= 0
                            || low.indexOf("vps") >= 0
                            || low.indexOf("llc") >= 0
                            || low.indexOf("limited") >= 0
                            || low.indexOf("technologies") >= 0
                            || low.indexOf("colo") >= 0;
                        isDC = "true".equals(hosting) || kw;
                        known = true;
                    }
                }
            }catch(Throwable t){ r.append("【出口IP】获取失败\n"); }
            r.append("\n");

            // ===== 2. IP 类型结论 =====
            if(known){
                r.append(isDC
                    ? "【IP 类型】机房/云主机（TikTok 视频必然被拒）\n"
                    : "【IP 类型】住宅/运营商宽带（有希望拿到视频）\n");
            } else {
                r.append("【IP 类型】无法判定\n");
            }
            r.append("\n");

            // ===== 3. 主站连通 =====
            r.append(probeUrl("TikTok 主站", "https://www.tiktok.com/", "https://www.tiktok.com/", false));

            // ===== 4. 视频 CDN 实测 =====
            // 优先用当前作品的直链；解析失败时用内置样例直链探测 CDN 可达性
            String cdnLine;
            boolean cdnOk = false;
            boolean cdnTested = false;
            boolean usingSample = false;
            String target = testUrl;
            if(target == null){
                target = SAMPLE_TT_VIDEO;
                usingSample = true;
            }
            if(target != null && target.length() > 0){
                cdnLine = probeUrl(usingSample ? "CDN 样例" : "视频 CDN", target, "https://www.tiktok.com/", true);
                cdnOk = cdnLine.indexOf("OK ") >= 0;
                cdnTested = true;
            } else {
                cdnLine = "【视频 CDN】无法测试\n";
            }
            r.append(cdnLine);
            r.append("\n");

            // ===== 5. 最终结论 =====
            r.append("————————\n");
            if(cdnTested && cdnOk){
                r.append("结论：这个节点能用，视频可以播放和下载。\n");
            } else if(isDC){
                r.append("结论：机房 IP，TikTok CDN 会拒绝。\n");
                r.append("换地区没用，需要住宅 IP 节点。\n");
                r.append("（机场标「原生 IP」不等于住宅，多数仍是机房）\n");
            } else if(cdnTested){
                r.append("结论：CDN 拒绝，但 IP 不像机房。\n");
                r.append("可能是这个 IP 被单独封了，换个节点再测。\n");
            } else {
                r.append("结论：先解析一个 TikTok 作品，再点体检。\n");
            }
            if(usingSample){
                r.append("\n注：用的是内置样例直链，过期也会 403。\n");
                r.append("要看真实结果，先成功解析一个 TikTok 作品。\n");
            }

            final String out = r.toString();
            Log.i("DYP", "NETCHECK_BEGIN\n" + out + "\nNETCHECK_END");
            h.post(new Runnable(){ public void run(){
                try{ dlg.setMessage(out); }catch(Throwable t){}
            }});
        }}).start();
    }

    /** 探测一个 URL 的 HTTP 状态，返回可读一行 */
    String probeUrl(String label, String url, String referer, boolean head){
        long t0 = System.currentTimeMillis();
        HttpURLConnection c = null;
        try{
            c = openConnRetry(url);
            if(head) c.setRequestMethod("HEAD");
            c.setRequestProperty("User-Agent", MUA);
            if(referer != null) c.setRequestProperty("Referer", referer);
            c.setConnectTimeout(12000); c.setReadTimeout(12000);
            c.setInstanceFollowRedirects(true);
            int code = c.getResponseCode();
            long ms = System.currentTimeMillis() - t0;
            String tail = "";
            if(code == 200 || code == 206){
                if(head){
                    long len = c.getContentLength();
                    tail = len > 0 ? ("  " + (len / 1024 / 1024) + "MB") : "  可下载";
                } else {
                    tail = "  可访问";
                }
            } else if(code == 403){
                tail = "  被拒绝";
            } else if(code == 404){
                tail = "  不存在";
            }
            String tag = (code == 200 || code == 206) ? "OK" : (code == 403 ? "封" : "?!");
            return "【" + label + "】" + tag + " " + code + "  (" + ms + "ms)" + tail + "\n";
        }catch(Throwable t){
            long ms = System.currentTimeMillis() - t0;
            return "【" + label + "】失败 " + t.getClass().getSimpleName() + "  (" + ms + "ms)\n";
        }finally{
            try{ if(c != null) c.disconnect(); }catch(Throwable t){}
        }
    }

    /** 抖音访客凭据（UIFID_TEMP）：填一次可长期用，实况照片解析必需 */
    void showUifidDialog(){
        SharedPreferences p = getSharedPreferences(SP, MODE_PRIVATE);
        String cur = p.getString(KEY_UIFID, "");
        int bgDlg   = curDark ? 0xFF17191F : 0xFFFFFFFF;
        int textDlg = curDark ? 0xFFE6E8EE : 0xFF1A1A1F;
        int subDlg  = curDark ? 0xFF9AA0AC : 0xFF6B6F7A;
        int linkClr = curDark ? 0xFF6FA8FF : 0xFF2F6FEB;

        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setBackgroundColor(bgDlg);
        wrap.setPadding(dp(20), dp(16), dp(20), dp(12));

        TextView tip = new TextView(this);
        tip.setText("实况照片（Live Photo）的动轨只有抖音 PC 详情接口会下发，"
            + "该接口在风控网关后面，缺少访客凭据时固定返回：\n"
            + "403 Blocked by ArgusSecurityPlugin Uifid Not Found\n\n"
            + "这个值没有算法可以伪造，必须由真实浏览器通过挑战后签发。\n\n"
            + "推荐直接用「设置 → 抖音访客凭据 → 自动获取」，"
            + "App 内会开一个隐藏浏览器，2-10 秒自动拿到，不用手动折腾。\n\n"
            + "手动填写方式（自动获取失败时用）：\n"
            + "· 电脑 Chrome：打开 www.douyin.com，等页面加载完\n"
+ "　　F12 → Application → Cookies → www.douyin.com\n"
            + "· 手机 Chrome：打开 www.douyin.com，用 Cookie 查看类插件导出\n"
            + "· 找到 UIFID_TEMP，复制值（约 160 字符）粘贴到下面。\n\n"
            + "有效期不固定，取决于抖音的风控策略，一般能长期复用；\n"
            + "失效时解析会提示 403 Uifid Not Found，重新抓一次即可。\n"
            + "不填也能用：实况照片会退化为静态图（图片和视频照常）。");
        tip.setTextSize(12);
        tip.setTextColor(subDlg);
        tip.setLineSpacing(dp(3), 1f);
        wrap.addView(tip);

        final EditText et = new EditText(this);
        et.setText(cur);
        et.setHint("粘贴 UIFID_TEMP 的值");
        et.setTextSize(13);
        et.setTextColor(textDlg);
        et.setHintTextColor(subDlg);
        et.setSingleLine(false);
        et.setMinLines(2);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(12);
        wrap.addView(et, lp);

        final TextView res = new TextView(this);
        res.setTextSize(12);
        res.setTextColor(subDlg);
        res.setPadding(0, dp(8), 0, 0);
        wrap.addView(res);

        TextView btnTest = new TextView(this);
        btnTest.setText("保存并测试（拿一条实况作品试 detail 接口）");
        btnTest.setTextSize(13);
        btnTest.setTextColor(linkClr);
        btnTest.setPadding(0, dp(10), 0, 0);
        wrap.addView(btnTest);

        // 说明入口：为什么 App 内抓不到（实测结论，避免用户反复尝试）
        TextView btnWhy = new TextView(this);
        btnWhy.setText("为什么 App 内抓不到？");
        btnWhy.setTextSize(13);
        btnWhy.setTextColor(linkClr);
        btnWhy.setPadding(0, dp(10), 0, 0);
        wrap.addView(btnWhy);

        final android.app.AlertDialog dlg = dypDlg()
            .setTitle("抖音访客凭据")
            .setView(new android.widget.ScrollView(this){{ addView(wrap); }})
            .setPositiveButton("保存", null)
            .setNegativeButton("关闭", null)
            .create();

        btnTest.setOnClickListener(new View.OnClickListener(){ public void onClick(View vv){
            final String val = et.getText().toString().trim();
            if(val.length() < 32){ res.setText("值太短，UIFID_TEMP 约 160 字符"); return; }
            res.setText("测试中...");
            getSharedPreferences(SP, MODE_PRIVATE).edit().putString(KEY_UIFID, val).apply();
            new Thread(new Runnable(){ public void run(){
                String ck = "";
                try{ ck = android.webkit.CookieManager.getInstance().getCookie("https://www.douyin.com/"); }catch(Throwable t){}
                ck = "UIFID_TEMP=" + val + "; " + (ck == null ? "" : ck);
                final DyDetail.Result r = DyDetail.fetch("7655163834785981115", ck, 2);
                h.post(new Runnable(){ public void run(){
                    if(r.ok){
                        res.setText("✅ 成功！http=" + r.httpCode + " 拿到 " + r.body.length() + " 字节，实况可用了");
                    }else{
                        res.setText("❌ " + r.err);
                    }
                }});
            }}).start();
        }});

        btnWhy.setOnClickListener(new View.OnClickListener(){ public void onClick(View vv){
            showUifidWhyDialog();
        }});

        dlg.setOnShowListener(new android.content.DialogInterface.OnShowListener(){
            public void onShow(android.content.DialogInterface d){
                dlg.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener(){
                    public void onClick(View v){
                        String val = et.getText().toString().trim();
                        getSharedPreferences(SP, MODE_PRIVATE).edit().putString(KEY_UIFID, val).apply();
                        Toast.makeText(MainActivity.this,
                            val.length() == 0 ? "已清空访客凭据" : "已保存，实况解析将尝试使用",
                            Toast.LENGTH_SHORT).show();
                        dlg.dismiss();
                    }
                });
            }
        });
        dlg.show();
    }

    /** 代理设置弹窗：只有一个「内置代理」开关（= 服务器中转），默认关 */
    void showProxyDialog(){
        curDark = isDarkMode();   // 同步主题
        SharedPreferences p = getSharedPreferences(SP, MODE_PRIVATE);
        boolean on = p.getBoolean(KEY_PROXY_ON, false);

        int bgDlg = curDark ? 0xFF17191F : 0xFFFFFFFF;
        int textDlg = curDark ? 0xFFE6E8EE : 0xFF1A1A1F;
        int subDlg = curDark ? 0xFF9AA0AC : 0xFF6B6F7A;

        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setBackgroundColor(bgDlg);
        wrap.setPadding(dp(20), dp(16), dp(20), dp(12));

        // 只有一个开关：内置代理（服务器中转）
        final CheckBox cbOn = new CheckBox(this);
        cbOn.setText("启用内置代理");
        cbOn.setChecked(on);
        cbOn.setTextColor(textDlg);
        cbOn.setTextSize(15);
        wrap.addView(cbOn);

        TextView tip = new TextView(this);
        tip.setText("内置代理 = 海外平台（TikTok/X/YouTube/IG）走我们的服务器中转。\n"
                  + "打开后不烧你自己代理的流量，但受服务器带宽限制。\n"
                  + "关闭后：海外平台全部直连，你自己挂的代理 / TUN 会自动生效，App 不干预。");
        tip.setTextSize(12);
        tip.setTextColor(subDlg);
        tip.setLineSpacing(dp(2), 1f);
        tip.setPadding(0, dp(10), 0, dp(4));
        wrap.addView(tip);

        android.app.AlertDialog dlg = dypDlg()
            .setTitle("代理设置")
            .setView(wrap)
            .setPositiveButton("保存", null)
            .setNegativeButton("取消", null)
            .create();

        dlg.setOnShowListener(new android.content.DialogInterface.OnShowListener(){
            public void onShow(android.content.DialogInterface d){
                dlg.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(
                    new View.OnClickListener(){ public void onClick(View vv){
                        getSharedPreferences(SP, MODE_PRIVATE).edit()
                            .putBoolean(KEY_PROXY_ON, cbOn.isChecked())
                            .apply();
                        Toast.makeText(MainActivity.this,
                            cbOn.isChecked() ? "内置代理已开启（海外走服务器中转）" : "内置代理已关闭（全部直连）",
                            Toast.LENGTH_SHORT).show();
                        dlg.dismiss();
                    }});
            }});
        try{
            android.view.Window win = dlg.getWindow();
            if(win != null) win.setBackgroundDrawable(makeRoundRect(bgDlg, curDark ? 0xFF2B2F39 : 0xFFE3E5EA, 16));
        }catch(Throwable t){}
        dlg.show();
    }

    /**
     * 说明为什么 App 内抓不到 UIFID_TEMP。
     * 结论来自实测（2026-10-21）：
     *   首页 / 作品页（未登录）  → Set-Cookie 只有 __ac_nonce
     *   detail 接口（未登录）    → 403，且 Set-Cookie 为空
     * 也就是说：不是「必须登录」，而是必须有浏览器完成整套 JS 挑战
     * （含每次请求单独计算的 a_bogus 签名），非交互环境过不了。
     */
    void showUifidWhyDialog(){
        String txt =
            "【结论】\n"
          + "App 内抓不到，不是没做好，是抖音的风控设计就不允许。\n"
          + "普通用户只能从真实浏览器里手动取。\n\n"
          + "【实测证据】\n"
          + "· 打开 douyin.com 首页 / 作品页（未登录）\n"
          + "　　Set-Cookie 只下发 __ac_nonce（挑战第一步）\n"
          + "· 直接请求 detail 接口（未登录、无签名）\n"
          + "　　HTTP 403，且 Set-Cookie 为空——服务端什么都不给\n"
          + "· 直接 HTTP 请求抖音时，返回体里写着：\n"
          + "　　「您正在尝试访问的网站存在安全风险」\n\n"
          + "【为什么不是「必须登录」】\n"
          + "登录不是硬性前提。真正卡住的是接口级签名：\n"
          + "detail 接口每次请求都要带一个由页面 JS 现场算出来的 a_bogus 参数，\n"
          + "它依赖设备指纹（canvas / WebGL / 字体 / 时区）、\n"
          + "鼠标轨迹等行为数据。App 里没有这些，算不出来。\n\n"
          + "【那 UIFID_TEMP 什么时候才会给】\n"
          + "它由抖音在「真实浏览器 + 完整挑战通过」的场景下通过 Set-Cookie 下发。\n"
          + "我们试过三种方式：加载首页、加载作品页、在页面里用 XHR 打 detail 接口，\n"
          + "三种都只拿到 ttwid / __ac_signature，没有 UIFID_TEMP。\n\n"
          + "【能用的办法】\n"
          + "1. 电脑 Chrome：打开 www.douyin.com，F12 → Application → Cookies\n"
          + "　 → 找到 UIFID_TEMP，复制值（约 160 字符）\n"
          + "2. 手机 Chrome：打开 www.douyin.com，用 Cookie 查看类插件导出\n"
          + "3. 复制后回到本页，粘贴到输入框保存\n\n"
          + "【有效期】\n"
          + "不固定，取决于抖音策略，通常能用较长时间。\n"
          + "失效时解析会报 403 Uifid Not Found，重新抓一次即可。\n\n"
          + "【不填会怎样】\n"
          + "照常能用：视频、图集、BGM 都正常。\n"
          + "只有「实况照片」的动轨拿不到，会退化成静态图。\n\n"
          + "【用服务器时】\n"
          + "服务器内置凭据池会代你签名，这种情况下可以不填。";
        showLongTextDialog("为什么 App 内抓不到", txt, null, null);
    }

    /** 服务器地址弹窗：只含服务器地址（其他设置项都在设置页独立入口） */
    void showSettings(){
        SharedPreferences p = getSharedPreferences(SP, MODE_PRIVATE);
        curDark = isDarkMode();   // 同步主题

        int bgDlg     = curDark ? 0xFF17191F : 0xFFFFFFFF;
        int textDlg   = curDark ? 0xFFE6E8EE : 0xFF1A1A1F;
        int subDlg    = curDark ? 0xFF9AA0AC : 0xFF6B6F7A;

        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setBackgroundColor(bgDlg);
        wrap.setPadding(dp(20), dp(16), dp(20), dp(12));

        TextView tip = new TextView(this);
        tip.setText("默认使用内置服务器；留空恢复内置地址。\n自建就填你的地址：http://1.2.3.4:8080");
        tip.setTextSize(12);
        tip.setTextColor(subDlg);
        tip.setLineSpacing(dp(3), 1f);
        wrap.addView(tip);

        final EditText etSrv = new EditText(this);
        // 用户没自填过（用内置）时不显示内置地址，只提示"已启用"；内置地址不暴露给用户
        String savedSrv = p.getString(KEY_DYP_SRV, "");
        if(savedSrv != null && savedSrv.length() > 0){
            etSrv.setText(savedSrv);
        }
        etSrv.setHint(savedSrv != null && savedSrv.length() > 0
                ? "http://1.2.3.4:8080"
                : "内置服务器已启用（留空保持默认）");
        etSrv.setTextSize(14);
        etSrv.setTextColor(textDlg);
        etSrv.setHintTextColor(subDlg);
        etSrv.setSingleLine(true);
        LinearLayout.LayoutParams lpSrv = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpSrv.topMargin = dp(10);
        wrap.addView(etSrv, lpSrv);

        android.app.AlertDialog dlg = dypDlg()
            .setTitle("服务器地址")
            .setView(wrap)
            .setPositiveButton("保存", null)
            .setNegativeButton("取消", null)
            .create();
        dlg.show();
        dlg.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(
            new View.OnClickListener(){ public void onClick(View vv){
                String srvAddr = etSrv.getText().toString().trim();
                if(srvAddr.endsWith("/")) srvAddr = srvAddr.substring(0, srvAddr.length() - 1);
                // 空 = 恢复内置默认地址
                if(srvAddr.length() == 0)
                    getSharedPreferences(SP, MODE_PRIVATE).edit().remove(KEY_DYP_SRV).apply();
                else
                    getSharedPreferences(SP, MODE_PRIVATE).edit().putString(KEY_DYP_SRV, srvAddr).apply();
                Toast.makeText(MainActivity.this, "服务器地址已保存", Toast.LENGTH_SHORT).show();
                dlg.dismiss();
            }});
        try{
            android.view.Window win = dlg.getWindow();
            if(win != null) win.setBackgroundDrawable(makeRoundRect(bgDlg, curDark ? 0xFF2B2F39 : 0xFFE3E5EA, 16));
        }catch(Throwable t){}
    }

    /** 保存路径设置：去尾斜杠；空 = 移除 key */
    void saveDlPath(String key, String val){
        if(val != null && val.endsWith("/")) val = val.substring(0, val.length() - 1);
        if(val == null || val.length() == 0)
            getSharedPreferences(SP, MODE_PRIVATE).edit().remove(key).apply();
        else
            getSharedPreferences(SP, MODE_PRIVATE).edit().putString(key, val).apply();
    }

    /** 设置页行：标题 + 副标题 + 点击 */
    TextView settingRow(String title, String sub, View.OnClickListener l){
        TextView t = new TextView(this);
        t.setText(title + "  ›\n" + sub);
        t.setTextSize(13);
        t.setTextColor(curDark ? C_DARK_TEXT : C_LIGHT_TEXT);
        t.setLineSpacing(dp(3), 1f);
        t.setPadding(dp(12), dp(10), dp(12), dp(10));
        t.setBackground(makeRoundRect(curDark ? 0xFF23262F : 0xFFF0F1F4, 0, 10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(8);
        t.setLayoutParams(lp);
        t.setOnClickListener(l);
        return t;
    }

    // ===== 运行日志：内存环形缓冲 + 文件双写 =====
    final java.util.List<String> logBuf = new java.util.ArrayList<String>();
    static final int LOG_MAX = 400;

    void appLog(String tag, String msg){
        String line = new java.text.SimpleDateFormat("MM-dd HH:mm:ss", Locale.US)
                .format(new java.util.Date()) + " [" + tag + "] " + msg;
        synchronized(logBuf){
            logBuf.add(line);
            if(logBuf.size() > LOG_MAX) logBuf.remove(0);
        }
        // 双写文件（异步，尽力而为）
        try{
            final String fl = line;
            new Thread(new Runnable(){ public void run(){
                try{
                    java.io.File f = new java.io.File(getExternalFilesDir(null), "run.log");
                    java.io.FileWriter fw = new java.io.FileWriter(f, true);
                    fw.write(fl + "\n");
                    fw.close();
                    if(f.length() > 256 * 1024){
                        // 超过 256KB 截断一半
                        java.io.RandomAccessFile rf = new java.io.RandomAccessFile(f, "rw");
                        byte[] tail = new byte[(int)(f.length() / 2)];
                        rf.seek(f.length() - tail.length);
                        rf.read(tail);
                        rf.setLength(0);
                        rf.write(tail);
                        rf.close();
                    }
                }catch(Throwable t){}
            }}).start();
        }catch(Throwable t){}
    }

    /** 运行日志查看弹窗 */
    void showLogViewer(){
        final StringBuilder sb = new StringBuilder();
        synchronized(logBuf){
            if(logBuf.isEmpty()) sb.append("（暂无日志）");
            else for(String l : logBuf) sb.append(l).append("\n");
        }
        android.app.AlertDialog dlg = dypDlg()
            .setTitle("运行日志（最近 " + LOG_MAX + " 条）")
            .setMessage(sb.toString())
            .setPositiveButton("复制全部", new android.content.DialogInterface.OnClickListener(){
                public void onClick(android.content.DialogInterface d, int w){
                    try{
                        android.content.ClipboardManager cm = (android.content.ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
                        cm.setPrimaryClip(android.content.ClipData.newPlainText("log", sb.toString()));
                        s("日志已复制");
                    }catch(Throwable t){}
                }
            })
            .setNeutralButton("清空", new android.content.DialogInterface.OnClickListener(){
                public void onClick(android.content.DialogInterface d, int w){
                    synchronized(logBuf){ logBuf.clear(); }
                    s("日志已清空");
                }
            })
            .setNegativeButton("关闭", null)
            .create();
        dlg.show();
    }

    /** 当前平台名 */
    /** 发布时间：秒级时间戳 -> 年-月-日 HH:mm */
    static String fmtTime(String raw){
        if(raw == null || raw.length() == 0) return "";
        try{
            double d = Double.parseDouble(raw.trim());
            long ms = (d < 100000000000L) ? (long)(d * 1000) : (long)d;
            return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(new java.util.Date(ms));
        }catch(Throwable t){ return raw; }
    }

    /** 时长：毫秒 -> 几小时几分几秒 */
    static String fmtDuration(String raw){
        if(raw == null || raw.length() == 0) return "";
        try{
            long ms = (long)Double.parseDouble(raw.trim());
            if(ms <= 0) return "无（图文）";
            long total = ms / 1000;
            long h = total / 3600, m = (total % 3600) / 60, sec = total % 60;
            if(h > 0) return h + "小时" + m + "分" + sec + "秒";
            if(m > 0) return m + "分" + sec + "秒";
            return sec + "秒";
        }catch(Throwable t){ return raw; }
    }


    class Bridge {
        @JavascriptInterface
        public void onYtData(final String data){
            h.post(new Runnable(){ public void run(){
                Log.i("DYP", "YT_BRIDGE " + (data == null ? "null" : data.substring(0, Math.min(120, data.length()))));
                if(data == null || data.length() < 10) return;
                try{
                    String[] parts = data.split("@@YT@@", 7);
                    if(parts.length < 6) return;
                    final String vurl = parts[0];
                    final String aurl = parts[1];
                    final String title = parts[2];
                    final String author = parts[3];
                    final String dur = parts[4];
                    final String cover = parts[5];
                    if(vurl.length() == 0) return;
                    webDone = true;
                    StringBuilder sb = new StringBuilder();
                    sb.append("{");
                    if(title.length() > 0 && !"null".equals(title))
                        sb.append("\"i_desc\":\"").append(YtParser.YtVideo.esc(title)).append("\",");
                    if(author.length() > 0 && !"null".equals(author))
                        sb.append("\"i_author\":\"").append(YtParser.YtVideo.esc(author)).append("\",");
                    if(dur.length() > 0 && !"0".equals(dur) && !"null".equals(dur))
                        sb.append("\"i_dur\":\"").append(dur).append("\",");
                    if(cover.length() > 0 && !"null".equals(cover))
                        sb.append("\"i_cover\":\"").append(YtParser.YtVideo.esc(cover)).append("\",");
                    sb.append("\"i_platform\":\"YouTube\",");
                    sb.append("\"i_play\":\"").append(YtParser.YtVideo.esc(vurl)).append("\"");
                    if(aurl.length() > 0)
                        sb.append(",\"i_audio\":\"").append(YtParser.YtVideo.esc(aurl)).append("\"");
                    sb.append("}");
                    String rr = sb.toString();
                    Log.i("DYP", "RESULT_BEGIN\n" + rr + "\nRESULT_END");
                    curAudio = aurl;
                    o(rr);
                    s("完成(YouTube)");
                    web.setVisibility(View.GONE);
                }catch(Throwable t){ Log.i("DYP", "YT_BRIDGE_ERR " + t); }
            }});
        }
        @JavascriptInterface
        public void onResult(final String json){
            h.post(new Runnable(){ public void run(){
                // 主页收集模式：普通 inject 回包不渲染
                if(profileMode){
                    Log.i("DYP", "IGNORE_RESULT profile mode");
                    return;
                }
                // 限时日常（story）已弹专属提示，忽略 WebView 后到的回包
                if(storyBlocked){
                    Log.i("DYP", "IGNORE_RESULT story blocked");
                    return;
                }
                // detail 接口已成功渲染过：它的数据比 SSR 完整（含实况动轨），
                // 不能被 WebView 的 SSR 结果覆盖，否则实况会退化成静态图。
                if(detailRendered){
                    Log.i("DYP", "IGNORE_RESULT detail already rendered");
                    webDone = true;
                    return;
                }
                // 合集模式已解析出结果，忽略后续普通页面的 inject 回包
                int mixN;
                synchronized(mixItems){ mixN = mixItems.size(); }
                if(mixN > 0 || mixMode){
                    Log.i("DYP", "IGNORE_RESULT mix mode n=" + mixN);
                    webDone = true;
                    return;
                }
                webDone=true;
                try{
                    java.io.File df = new java.io.File(getExternalFilesDir(null), "last_raw.json");
                    java.io.FileOutputStream fo = new java.io.FileOutputStream(df);
                    fo.write(json.getBytes("UTF-8")); fo.close();
                    // raw 保留给调试脚本，正式版不写文件
                }catch(Throwable t){}
                String jsonForRender = DyDetail.normalizeDetail(json);
                try{
                    Object rr = new JP(json).parse();
                    String dc = gs(get(rr, "docCookie"));
                    Log.i("DYP", "DOC_COOKIE len=" + dc.length() + " " + dc);
                    String ck = android.webkit.CookieManager.getInstance().getCookie("https://www.douyin.com/");
                    StringBuilder nm = new StringBuilder();
                    if(ck != null) for(String kv : ck.split(";")){ int e = kv.indexOf('='); if(e>0) nm.append(kv.substring(0,e).trim()).append(","); }
                    Log.i("DYP", "CM_COOKIE_NAMES " + nm);
                    String uf = DyDetail.cookieValue(ck, "UIFID");
                    if(uf.length() == 0) uf = DyDetail.cookieValue(ck, "uifid");
                    Log.i("DYP", "UIFID_FROM_CM len=" + uf.length() + " head=" + (uf.length()>40?uf.substring(0,40):uf));
                }catch(Throwable t){ Log.i("DYP", "COOKIE_PROBE_ERR " + t); }
                String r=render(jsonForRender);
                Log.i("DYP","RESULT_BEGIN\n"+r+"\nRESULT_END");
                Log.i("DYP","RAW_LEN="+json.length());
                // 失败则自动重试（最多 2 次）
                boolean fail = r.indexOf("解析失败") >= 0 || r.indexOf("未能获取") >= 0;
                // 合集页面走 mix 分支，不做普通重试
                String curPage = "";
                try{ curPage = web != null ? web.getUrl() : ""; }catch(Throwable t){}
                if(fail && (mixMode
                        || (curUrl != null && (curUrl.indexOf("/mix/") >= 0 || curUrl.indexOf("/playlet/") >= 0))
                        || (curPage != null && (curPage.indexOf("/mix/") >= 0 || curPage.indexOf("/playlet/") >= 0)))){
                    Log.i("DYP", "SKIP_RETRY mix page " + curPage);
                    if(!mixMode){
                        // 合集接口还没回来，给点时间再判断
                        h.postDelayed(new Runnable(){ public void run(){
                            if(mixMode) web.setVisibility(View.GONE);
                        }}, 5000);
                    }
                    return;
                }
                if(fail && curRetry < 2){
                    curRetry++;
                    webDone = false;
                    Log.i("DYP", "RETRY " + curRetry);
                    h.postDelayed(new Runnable(){ public void run(){ loadForParse(curUrl); } }, 1200);
                    return;
                }
                o(r);
                s(fail ? "解析失败" : "完成");
                appLog("解析", fail ? "✗ 失败 url=" + curUrl : "✓ 完成 id=" + curId);
                // 主页列表「下载」入口：解析成功后自动把高清直链入下载队列
                if(!fail && pendingAutoDlTag.length() > 0 && curPlay != null && curPlay.length() > 0){
                    final String dl = curPlay;
                    final String tag = pendingAutoDlTag;
                    pendingAutoDlTag = "";
                    pendingAutoDlIdx = -1;
                    enqueueDownload(dl, tag, "mp4", "视频");
                    showHintToast("已加入下载队列：" + tag);
                    s("已加入下载队列：" + tag);
                }
                web.setVisibility(View.GONE);
            }});
        }

        @JavascriptInterface
        public void onProfileData(final String body){
            onProfileDataFromWeb(body);
        }

        @JavascriptInterface
        public void onDiag(final String msg){
            onDiagFromWeb(msg);
        }

        /** JS 在页面里发 detail 请求拿到的响应（主页列表条目下载用） */
        @JavascriptInterface
        public void onDetailData(final String body){
            final String b = body;
            new Thread(new Runnable(){ public void run(){
                parseDetailBody(b);
            }}).start();
        }
    }

    /** 是否主页页面 URL（WebView 运行时检测，短链跳转后也适用） */
    static boolean isProfilePage(String u){
        if(u == null) return false;
        String low = u.toLowerCase();
        if(low.contains("/share/user/") || low.contains("douyin.com/user/")
            || low.contains("kuaishou.com/profile/") || low.contains("live.kuaishou.com/u/")
            || low.contains("c.kuaishou.com/fw/user/") || low.contains("kuaishou.com/fw/user/")
            || low.contains("bilibili.com/space/")){
            return true;
        }
        // Instagram 用户主页（不带 /p/ /reel/ /stories/）
        if(low.contains("instagram.com/")){
            if(low.contains("/p/") || low.contains("/reel/") || low.contains("/reels/")
                    || low.contains("/tv/") || low.contains("/stories/") || low.contains("/explore/")
                    || low.contains("/accounts/") || low.contains("/direct/")){
                return false;
            }
            return java.util.regex.Pattern.compile("instagram\\.com/[A-Za-z0-9_.]{2,30}(?:[/?#]|$)")
                    .matcher(low).find();
        }
        return false;
    }

    /** 主页 WebView 收集模式：页面发列表请求时，把响应体送回来（Bridge 已闭，这里拆成独立入口） */
    void onProfileDataFromWeb(final String body){
        if(!profileMode) return;
        // 确认框还没点确认：页面自己抓到的列表数据先丢掉，避免绕过确认直接出单页结果
        if(bigParsePending || !profileAutoConfirmed){
            Log.i("DYP", "PROFILE_WEB_DROP 未确认主页解析，丢弃页面数据 len="
                    + (body == null ? 0 : body.length()));
            return;
        }
        // 抖音主页已由签名翻页接管，WebView 自己抓到的数据会重复混进来（数量虚高、易重复下载）
        if(dyProfileSignedOwned && body != null && body.indexOf("aweme_list") >= 0){
            Log.i("DYP", "DYPROF_WEB_DROP 签名翻页已接管，丢弃 WebView 抓到的列表 len=" + body.length());
            return;
        }
        new Thread(new Runnable(){ public void run(){
            parseProfileBody(body);
        }}).start();
    }

    /** 主页收集诊断回包 */
    void onDiagFromWeb(final String msg){
        Log.i("DYP", "PROFILE_DIAG " + msg);
    }

    // ================= 主页 WebView 收集模式 =================

    /** 把本地保存的 IG Cookie 灌进 WebView 的 CookieManager（登录态复用到 WebView） */
    void injectInsCookiesToWebView(){
        try{
            java.util.Map<String, String> m = CookieStore.load(this, CookieStore.PLATFORM_INS);
            if(m.isEmpty()){
                Log.i("DYP", "INS_CK_INJECT skip (empty)");
                return;
            }
            android.webkit.CookieManager cm = android.webkit.CookieManager.getInstance();
            cm.setAcceptCookie(true);
            int n = 0;
            for(java.util.Map.Entry<String, String> e : m.entrySet()){
                String kv = e.getKey() + "=" + e.getValue();
                cm.setCookie("https://www.instagram.com/", kv);
                cm.setCookie("https://i.instagram.com/", kv);
                cm.setCookie("https://instagram.com/", kv);
                n++;
            }
            try{ cm.flush(); }catch(Throwable t){}
            Log.i("DYP", "INS_CK_INJECT n=" + n);
        }catch(Throwable t){ Log.i("DYP", "INS_CK_INJECT_ERR " + t); }
    }

    /** 启动主页收集：WebView 加载主页 + 注入 PROFILE_HOOK_JS + 自动滚动翻页 */
    void startProfileWebView(final String url){
        profileMode = true;
        webDone = true;
        synchronized(profileCollected){ profileCollected.clear(); } profilePage = 0;
                    // 主页/合集解析：先自动清空列表（批量模式下不清，否则会把自己收集的结果清掉）
                    if(!batchCollectMode){
                        synchronized(batchCollected){ batchCollected.clear(); }
                        // 同步删掉落盘文件，否则重启后旧批量结果又冒出来
                        try{ java.io.File f = batchListFile(); if(f != null && f.exists()) f.delete(); }catch(Throwable t){}
                    }
        synchronized(profileSeen){ profileSeen.clear(); }
        s("主页网页加载中，自动收集作品…");
        setupWebView();
        web.setVisibility(View.VISIBLE);
        web.loadUrl(url);
    }

    /** 停止收集（模式退出时调用；不 stopLoading——后面可能紧跟新的 loadUrl） */
    void stopProfileMode(){
        profileMode = false;
        if(web != null){
            try{
                web.evaluateJavascript("try{window.__dyStopScroll=1;}catch(e){}", null);
            }catch(Throwable t){}
        }
        web.setVisibility(View.GONE);
    }

    /** 主页条目下载：在 WebView 页面里发 detail 请求拿高清直链（利用页面已签名的环境） */
    String pendingDetailId = "";
    String pendingDetailTag = "";
    String pendingDetailSignedUrl = "";   // 页面自动加过 msToken+a_bogus 的完整 URL
    /** 解析完成后自动入下载队列（主页列表抖音条目） */
    String pendingAutoDlTag = "";
    int pendingAutoDlIdx = -1;
    void downloadProfileItemViaDetail(final String awemeId, final String tag){
        if(web == null || !webReady) return;
        pendingDetailId = awemeId;
        pendingDetailTag = tag;
        pendingDetailSignedUrl = "";
        // 页面 fetch 会自动被抖音签名包装器补 msToken+a_bogus；
        // 在 JS 里就地抽取直链字段，只传小 JSON 过桥（避免大字符串跨桥失败）。
        String js = "(function(){try{"
            + "var url='/aweme/v1/web/aweme/detail/?device_platform=webapp&aid=6383"
            + "&channel=channel_pc_web&aweme_id=" + awemeId + "&pc_client_type=1&version_code=190500&version_name=19.5.0&cookie_enabled=true&screen_width=1536&screen_height=864&browser_language=zh-CN&browser_platform=Win32&browser_name=Chrome&browser_version=120.0.0.0&browser_online=true';"
            + "function pick(t){try{var j=JSON.parse(t);var out={};"
            + "var ad=j.aweme_detail||j;"
            + "out.aweme_id=ad.aweme_id||'';"
            + "var v=ad.video;if(v&&v.play_addr){var ul=v.play_addr.url_list||[];out.vurl=ul[0]||'';}"
            + "if(!out.vurl&&v&&v.download_addr){var dl=v.download_addr.url_list||[];out.vurl=dl[0]||'';}"
            + "var imgs=ad.images;if(imgs&&imgs.length){var il=imgs[0].url_list||[];out.iurl=il[0]||'';}"
            + "if(!out.vurl&&!out.iurl&&v&&v.bit_rate){for(var i=0;i<v.bit_rate.length;i++){var b=v.bit_rate[i];if(b.play_addr){var bl=b.play_addr.url_list||[];if(bl[0]){out.vurl=bl[0];break;}}}}"
            + "return out;}catch(e){return {perr:''+e};}}"
            + "fetch(url,{credentials:'include'}).then(function(r){return r.text();}).then(function(t){"
            + "var o=pick(t);window.DyBridge.onDetailData(JSON.stringify(o));"
            + "}).catch(function(e){window.DyBridge.onDetailData(JSON.stringify({ferr:''+e}));});"
            + "}catch(e){window.DyBridge.onDetailData(JSON.stringify({err:''+e}));}})()";
        web.evaluateJavascript(js, null);
    }

    /** 解析 JS 回传的直链小 JSON（{vurl|iurl|err}），入下载队列 */
    void parseDetailBody(final String body){
        try{
            Object root = new JP(body).parse();
            if(!(root instanceof Map)){
                h.post(new Runnable(){ public void run(){
                    s("解析失败");
                    showHintToast("解析失败");
                }});
                return;
            }
            Map m = (Map) root;
            String err = gs(get(m, "err")) + gs(get(m, "ferr")) + gs(get(m, "perr"));
            String vurl = gs(get(m, "vurl"));
            String iurl = gs(get(m, "iurl"));
            if(err.length() > 0 && vurl.length() == 0 && iurl.length() == 0){
                Log.i("DYP", "PROFILE_DETAIL_ERR " + err);
                h.post(new Runnable(){ public void run(){
                    s("解析失败，试试「解析看详情」：" + err.substring(0, Math.min(60, err.length())));
                    showHintToast("解析失败");
                }});
                return;
            }
            final String fin = vurl.length() > 0 ? vurl : iurl;
            final boolean finImg = vurl.length() == 0;
            if(fin.length() == 0 || fin.equals("?")){
                h.post(new Runnable(){ public void run(){
                    s("解析失败：没有直链");
                    showHintToast("解析失败");
                }});
                return;
            }
            h.post(new Runnable(){ public void run(){
                enqueueDownload(fin, pendingDetailTag, finImg ? "jpg" : "mp4",
                        finImg ? "图片" : "视频");
                s("已加入下载队列：" + pendingDetailTag);
                showHintToast("已加入下载队列");
            }});
            pendingDetailId = "";
        }catch(Throwable t){
            Log.i("DYP", "PROFILE_DETAIL_PARSE_ERR " + t);
        }
    }

    /** 解析列表响应 body，按平台提取 ProfileItem */
    void parseProfileBody(final String body){
        try{
            Object root = new JP(body).parse();
            // 平台判定：按内容特征
            if(body.indexOf("aweme_list") >= 0){
                // 临时诊断：确认分页字段
                try{
                    if(root instanceof Map){
                        Map rm = (Map) root;
                        Log.i("DYP", "PROFILE_PAGE_FIELDS has_more=" + rm.get("has_more")
                            + " max_cursor=" + rm.get("max_cursor")
                            + " cursor=" + rm.get("cursor")
                            + " min_cursor=" + rm.get("min_cursor")
                            + " status_code=" + rm.get("status_code"));
                    }
                }catch(Throwable t){}
                parseDyProfile(root);
            } else if(body.indexOf("visionProfileWorkPhotoList") >= 0
                    || body.indexOf("visionProfileLikePhotoList") >= 0){
                parseKsProfile(root);
            } else if(body.indexOf("bvid") >= 0 && body.indexOf("vlist") >= 0){
                parseBiliProfile(root);
            } else if(body.indexOf("videoId") >= 0 && body.indexOf("thumbnail") >= 0){
                parseYtProfile(root);
            } else if(body.indexOf("edge_owner_to_timeline_media") >= 0
                    || body.indexOf("xdt_api__v1__feed__user_timeline_graphql_connection") >= 0
                    || body.indexOf("xdt_shortcode_media") >= 0){
                parseInsProfileJson(root);
            } else {
                Log.i("DYP", "PROFILE_UNKNOWN len=" + body.length());
            }
        }catch(Throwable t){
            Log.i("DYP", "PROFILE_PARSE_ERR " + t);
        }
    }

    /** IG：从 graphql/JSON 响应里抠 shortcode 列表 */
    void parseInsProfileJson(Object root){
        try{
            int added = 0;
            java.util.List<String> codes = new java.util.ArrayList<String>();
            collectInsCodes(root, codes, 0);
            for(String code : codes){
                if(code == null || code.length() < 3) continue;
                synchronized(profileSeen){
                    if(!profileSeen.add("ig_" + code)) continue;
                }
                ProfileParser.ProfileItem it = new ProfileParser.ProfileItem();
                it.id = code;
                it.url = "https://www.instagram.com/p/" + code + "/";
                it.title = "Instagram " + code;
                synchronized(profileCollected){ profileCollected.add(it); }
                added++;
            }
            Log.i("DYP", "INS_PROFILE_JSON added=" + added + " total=" + profileCollected.size());
            if(added > 0) h.post(profileRenderRunnable);
        }catch(Throwable t){
            Log.i("DYP", "INS_PROFILE_JSON_ERR " + t);
        }
    }

    /** 递归找 "shortcode" 字段 */
    void collectInsCodes(Object o, java.util.List<String> out, int depth){
        if(o == null || depth > 12) return;
        if(o instanceof Map){
            Map m = (Map) o;
            Object sc = m.get("shortcode");
            if(sc instanceof String) out.add((String) sc);
            Object codeO = m.get("code");
            if(codeO instanceof String && !m.containsKey("shortcode")) out.add((String) codeO);
            for(Object v : m.values()) collectInsCodes(v, out, depth + 1);
        } else if(o instanceof List){
            for(Object v : (List) o) collectInsCodes(v, out, depth + 1);
        }
    }

    /** 抖音：aweme_list 响应 → items（视频/图集 + 直链） */
    void parseDyProfile(Object root){
        Object awemeList = findKey(root, "aweme_list");
        if(!(awemeList instanceof List)) return;
        int added = 0;
        for(Object o : (List) awemeList){
            if(!(o instanceof Map)) continue;
            Map aw = (Map) o;
            String awemeId = gs(get(aw, "aweme_id"));
            String desc = gs(get(aw, "desc"));
            if(awemeId.length() == 0) continue;
            synchronized(profileSeen){
                if(!profileSeen.add(awemeId)) continue;
            }
            ProfileParser.ProfileItem it = new ProfileParser.ProfileItem();
            it.id = awemeId;
            it.title = desc;
            // 图片集判定
            Object imgs = get(aw, "images");
            boolean isImage = imgs instanceof List && !((List) imgs).isEmpty();
            it.isImage = isImage;
            // 视频直链：video.play_addr.url_list 第一个
            if(!isImage){
                Object video = get(aw, "video");
                if(video instanceof Map){
                    Object pa = get((Map) video, "play_addr");
                    if(pa instanceof Map){
                        String u = firstUrl(get((Map) pa, "url_list"));
                        if(u.length() > 0 && !u.equals("?")) it.directUrl = u;
                    }
                }
            }
            // 图集：第一张图直链
            if(isImage && it.directUrl.length() == 0){
                Object firstImg = ((List) imgs).get(0);
                if(firstImg instanceof Map){
                    Object ul = get((Map) firstImg, "url_list");
                    String u = firstUrl(ul);
                    if(u.length() > 0 && !u.equals("?")) it.directUrl = u;
                }
            }
            it.url = "https://www.iesdouyin.com/share/video/" + awemeId + "/";
            synchronized(profileCollected){ profileCollected.add(it); }
            added++;
        }
        if(added > 0){
            Log.i("DYP", "DY_PROFILE +" + added + " total=" + profileCollected.size());
            scheduleProfileRender();
        }
    }

    /** 快手：visionProfileWorkPhotoList / visionProfileLikePhotoList → items */
    void parseKsProfile(Object root){
        Object listObj = findKey(root, "visionProfileWorkPhotoList");
        if(!(listObj instanceof List)) listObj = findKey(root, "visionProfileLikePhotoList");
        if(!(listObj instanceof List)) return;
        int added = 0;
        for(Object o : (List) listObj){
            if(!(o instanceof Map)) continue;
            Map ph = (Map) o;
            // photoId / photo
            String photoId = gs(get(ph, "photoId"));
            if(photoId.length() == 0) photoId = gs(get(ph, "id"));
            String caption = gs(get(ph, "caption"));
            if(photoId.length() == 0) continue;
            synchronized(profileSeen){
                if(!profileSeen.add(photoId)) continue;
            }
            ProfileParser.ProfileItem it = new ProfileParser.ProfileItem();
            it.id = photoId;
            it.title = caption;
            // 视频直链 mainMvUrl / photoUrl
            Object photo = get(ph, "photo");
            if(photo instanceof Map){
                String mv = gs(get((Map) photo, "mainMvUrl"));
                if(mv.length() == 0) mv = gs(get((Map) photo, "mainMvUrls"));
                if(mv.length() == 0) mv = gs(get((Map) photo, "photoUrl"));
                if(mv.length() > 0) it.directUrl = mv;
            }
            it.url = "https://www.kuaishou.com/short-video/" + photoId;
            synchronized(profileCollected){ profileCollected.add(it); }
            added++;
        }
        if(added > 0){
            Log.i("DYP", "KS_PROFILE +" + added + " total=" + profileCollected.size());
            scheduleProfileRender();
        }
    }

    /** B 站：wbi/arc/search 响应（vlist）→ items */
    void parseBiliProfile(Object root){
        Object vlist = findKey(root, "vlist");
        if(!(vlist instanceof List)) return;
        int added = 0;
        for(Object o : (List) vlist){
            if(!(o instanceof Map)) continue;
            Map v = (Map) o;
            String bvid = gs(get(v, "bvid"));
            if(bvid.length() == 0) continue;
            synchronized(profileSeen){
                if(!profileSeen.add(bvid)) continue;
            }
            ProfileParser.ProfileItem it = new ProfileParser.ProfileItem();
            it.id = bvid;
            it.title = gs(get(v, "title"));
            it.url = "https://www.bilibili.com/video/" + bvid;
            synchronized(profileCollected){ profileCollected.add(it); }
            added++;
        }
        if(added > 0){
            Log.i("DYP", "BILI_PROFILE +" + added + " total=" + profileCollected.size());
            scheduleProfileRender();
        }
    }

    /** YouTube：browse API 响应（videoId + thumbnail/thumbnails）→ items */
    void parseYtProfile(Object root){
        // YouTube browse 响应里有嵌套的 videoRenderer / gridVideoRenderer
        // 递归搜所有 videoId 对应的 title
        java.util.List<Map> items = new ArrayList<Map>();
        collectYtVideos(root, items, new java.util.HashSet<String>());
        if(items.isEmpty()) return;
        int added = 0;
        for(Map v : items){
            String vid = gs(v.get("videoId"));
            String title = gs(v.get("title"));
            if(vid.length() == 0) continue;
            synchronized(profileSeen){
                if(!profileSeen.add(vid)) continue;
            }
            ProfileParser.ProfileItem it = new ProfileParser.ProfileItem();
            it.id = vid;
            it.title = title;
            it.url = "https://www.youtube.com/watch?v=" + vid;
            synchronized(profileCollected){ profileCollected.add(it); }
            added++;
        }
        if(added > 0){
            Log.i("DYP", "YT_PROFILE +" + added + " total=" + profileCollected.size());
            scheduleProfileRender();
        }
    }

    /** 递归搜 YouTube browse 响应里的 videoId + title（videoRenderer / gridVideoRenderer） */
    void collectYtVideos(Object o, java.util.List<Map> out, java.util.Set<String> seen){
        if(o instanceof Map){
            Map m = (Map) o;
            Object vid = m.get("videoId");
            if(vid != null){
                String v = gs(vid);
                if(v.length() > 0 && !seen.contains(v)){
                    seen.add(v);
                    Map<String, String> it = new java.util.HashMap<String, String>();
                    it.put("videoId", v);
                    // 标题：title.runs[0].text / title.simpleText / headline.runs[0].text
                    String title = "";
                    Object titleObj = m.get("title");
                    if(titleObj instanceof Map){
                        Map tm = (Map) titleObj;
                        Object runs = tm.get("runs");
                        if(runs instanceof List && !((List) runs).isEmpty()){
                            Object r0 = ((List) runs).get(0);
                            if(r0 instanceof Map) title = gs(((Map) r0).get("text"));
                        }
                        if(title.length() == 0) title = gs(tm.get("simpleText"));
                    } else if(titleObj instanceof String){
                        title = (String) titleObj;
                    }
                    // 兜底 headline
                    if(title.length() == 0){
                        Object hl = m.get("headline");
                        if(hl instanceof Map){
                            Object hr = ((Map) hl).get("runs");
                            if(hr instanceof List && !((List) hr).isEmpty()){
                                Object r0 = ((List) hr).get(0);
                                if(r0 instanceof Map) title = gs(((Map) r0).get("text"));
                            }
                        }
                    }
                    // 再兜底 accessibilityData
                    if(title.length() == 0){
                        Object ad = m.get("accessibility");
                        if(ad instanceof Map){
                            Object adt = ((Map) ad).get("accessibilityData");
                            if(adt instanceof Map) title = gs(((Map) adt).get("label"));
                        }
                    }
                    it.put("title", title);
                    out.add(it);
                }
            }
            for(Object val : m.values()){
                collectYtVideos(val, out, seen);
            }
        } else if(o instanceof List){
            for(Object val : (List) o){
                collectYtVideos(val, out, seen);
            }
        }
    }

    /** 防抖：收集到数据后 1.2 秒统一刷新列表页（新数据会重置计时） */
    void scheduleProfileRender(){
        profileLastRender = System.currentTimeMillis();
        h.removeCallbacks(profileRenderRunnable);
        h.postDelayed(profileRenderRunnable, 1200);
    }

    final Runnable profileRenderRunnable = new Runnable(){ public void run(){
        if(!profileMode) return;
        // 未确认主页解析前不渲染，避免确认框还在弹就已经出了 20 条单页结果
        if(bigParsePending || !profileAutoConfirmed){
            Log.i("DYP", "PROFILE_RENDER_SKIP 未确认主页解析");
            return;
        }
        synchronized(profileCollected){
            if(profileCollected.isEmpty()) return;
            final java.util.List<ProfileParser.ProfileItem> copy =
                    new java.util.ArrayList<ProfileParser.ProfileItem>(profileCollected);
            h.post(new Runnable(){ public void run(){
                if(!profileMode) return;
                showProfileList(copy);
            }});
        }
    }};

    /** 后台跑 TikTok 解析，结果回到主线程渲染 */
    void triggerTikTokBgParse(final String pageUrl){
        new Thread(new Runnable(){ public void run(){
            try{
                String r = parseTikTokByHttp(pageUrl);
                if(r == null || r.length() == 0){
                    Log.i("DYP", "TT_BG_EMPTY");
                    // 兜底也失败，给个明确提示，别让用户停在"未能获取"
                    final StringBuilder eb = new StringBuilder();
                    eb.append("【解析失败】\n");
                    eb.append("TikTok 页面没有返回视频数据。\n\n");
                    eb.append("常见原因：\n");
                    eb.append("1. 网络访问不了 TikTok（可开「内置代理」走服务器中转）\n");
                    eb.append("2. 出口 IP 被 TikTok 标记\n");
                    eb.append("3. 作品已删除或设为私密\n\n");
                    eb.append("建议：「设置 → 代理 → 内置代理」开启后重试。");
                    final String em = eb.toString();
                    h.post(new Runnable(){ public void run(){
                        o(em);
                        s("TikTok 解析失败");
                    }});
                    return;
                }
                Log.i("DYP", "RESULT_BEGIN\n" + r + "\nRESULT_END");
                o(r);
                s("完成(TikTok)");
                webDone = true;
                h.post(new Runnable(){ public void run(){
                    try{ web.setVisibility(View.GONE); }catch(Throwable t){}
                }});
            }catch(Throwable t){ Log.i("DYP", "TT_BG_ERR " + t); }
        }}).start();
    }

    /** TikTok HTTP 兜底：走 TtParser（新版 reflow + 老版 api-data 双模板 + 自管 cookie 会话） */
    String parseTikTokByHttp(String pageUrl){
        try{
            TtParser.TtVideo v = TtParser.resolve(MainActivity.this, pageUrl, ttSession);
            Log.i("DYP", "TT_RESULT tpl=" + v.template + " err=" + v.err
                    + " ok=" + v.ok() + " id=" + v.id);
            if(!v.ok()){
                if(v.err.length() > 0){
                    final String em = buildTtError(v.err);
                    h.post(new Runnable(){ public void run(){ o(em); s("TikTok 解析失败"); }});
                }
                return null;
            }
            curSession = ttSession;
            return buildTtResult(v);
        }catch(Throwable t){
            Log.i("DYP", "TT_HTTP_ERR " + t);
            return null;
        }
    }
    /** 当前 TikTok 会话（解析和下载共用同一份 cookie） */
    TtParser.Session ttSession = new TtParser.Session();
    TtParser.Session curSession = null;

    /** 服务器端 TikTok 解析结果（走内置加速层，App 无需代理） */
    String ttSrvRaw = "";        // 服务器原始 JSON（拿 dl_via_server 等标记）
    String ttSrvId = "";         // TikTok 作品 id（下载中转要用）
    boolean ttSrvVideoOk = false;  // 视频能否走服务器下载
    boolean ttSrvMusicOk = false;
    boolean ttSrvImageOk = false;
    String ttSrvNote = "";

    /** 取当前生效的服务器地址（用户自填 > 内置默认） */
    String effSrv(){
        return effSrvS(this);
    }

    /** 静态版：给解析器用 */
    static String effSrvS(Context ctx){
        String s;
        try{
            s = ctx.getSharedPreferences(SP, MODE_PRIVATE).getString(KEY_DYP_SRV, DYP_SRV_DEFAULT);
        }catch(Throwable t){
            s = DYP_SRV_DEFAULT;
        }
        if(s == null) s = "";
        s = s.trim();
        while(s.endsWith("/")) s = s.substring(0, s.length() - 1);
        return s;
    }

    /** 静态版：HMAC 鉴权密钥（来自 SrvSecret，异或混淆存储） */
    static String srvHmacKeyS(Context ctx){
        try{
            return SrvSecret.hmacKey();
        }catch(Throwable t){
            return "";
        }
    }

    /** 机场流量缓存（避免每次开设置都请求） */
    static String airCacheText = "";
    static long airCacheTs = 0;

    /**
     * 查机场剩余流量 / 到期天数，显示到 tv。
     * 服务器不可达或流量耗尽 → 提示用户自行开代理（回退本地路径）。
     */
    void loadAirInfo(final TextView tv){
        if(tv == null) return;
        // 5 分钟内复用缓存
        if(airCacheText.length() > 0 && System.currentTimeMillis() - airCacheTs < 300000){
            tv.setText(airCacheText);
            return;
        }
        final String srv = effSrv();
        if(srv.length() == 0){
            tv.setText("机场流量：未配置服务器（TikTok 可开内置代理或走自己的网络）");
            return;
        }
        tv.setText("机场流量：查询中…");
        new Thread(new Runnable(){ public void run(){
            String out;
            try{
                HttpURLConnection c = openConn(srv + "/api/tt/sub");
                c.setRequestProperty("User-Agent", MUA);
                c.setRequestProperty("Accept", "*/*");
                c.setConnectTimeout(8000);
                c.setReadTimeout(12000);
                int code = c.getResponseCode();
                String body = readAll(code >= 400 ? c.getErrorStream() : c.getInputStream());
                c.disconnect();
                Log.i("DYP", "AIR_HTTP " + code + " len=" + (body == null ? 0 : body.length()));
                if(code != 200 || body == null || body.length() == 0){
                    out = "机场流量：服务器无响应（TikTok 可开内置代理或走自己的网络）";
                } else if(body.indexOf("\"ok\":true") < 0 && body.indexOf("\"ok\": true") < 0){
                    out = "机场流量：" + str(jget(body, "err")) + "（TikTok 可开内置代理或走自己的网络）";
                } else {
long remain = parseLong(jnum(body, "remain_gb").replace(".", ""));
                    String remainS = jnum(body, "remain_gb");
                    String totalS = jnum(body, "total_gb");
                    String daysS = jnum(body, "days_left");
                    String dateS = str(jget(body, "expire_date"));
                    boolean usable = "true".equals(jnum(body, "usable"));
                    StringBuilder b = new StringBuilder();
                    b.append("机场流量：剩 ").append(remainS).append(" / ").append(totalS).append(" GB");
                    b.append("　").append(daysS).append(" 天到期");
                    if(dateS.length() > 0) b.append("（").append(dateS).append("）");
                    // 剩余不足 1GB 或已到期 → 提示开内置代理不可用
                    if(!usable || remain < 1){
                        String reason = str(jget(body, "reason"));
                        b.append("\n⚠ 机场不可用");
                        if(reason.length() > 0) b.append("：").append(reason);
                        b.append("\n可关闭「内置代理」走自己的网络使用 TikTok");
                    } else {
                        b.append("\nTikTok 解析/下载走服务器，无需挂代理");
                    }
                    out = b.toString();
                }
            }catch(Throwable t){
                Log.i("DYP", "AIR_EX " + t);
                out = "机场流量：连接失败（TikTok 可开内置代理或走自己的网络）";
            }
            airCacheText = out;
            airCacheTs = System.currentTimeMillis();
            final String f = out;
            h.post(new Runnable(){ public void run(){
                try{ tv.setText(f); }catch(Throwable t){}
            }});
        }}).start();
    }

    /**
     * 走服务器解析 TikTok（经机场出口，App 端不需要代理）。
     * 成功返回 render 认的 JSON；失败返回 null（调用方回退本地 TtParser）。
     */
    String parseTikTokByServer(String rawUrl){
        String srv = effSrv();
        if(srv.length() == 0) return null;
        HttpURLConnection c = null;
        try{
            String full = srv + "/api/tt?url=" + java.net.URLEncoder.encode(rawUrl, "UTF-8");
            c = openConn(full);
            c.setRequestProperty("User-Agent", MUA);
            c.setRequestProperty("Accept", "*/*");
            c.setConnectTimeout(12000);
            c.setReadTimeout(45000);
            int code = c.getResponseCode();
            String body = readAll(code >= 400 ? c.getErrorStream() : c.getInputStream());
            Log.i("DYP", "TT_SRV http=" + code + " len=" + (body == null ? 0 : body.length()));
            if(code != 200 || body == null || body.length() == 0) return null;
            if(body.indexOf("\"ok\":true") < 0 && body.indexOf("\"ok\": true") < 0){
                Log.i("DYP", "TT_SRV_ERR " + str(jget(body, "err")));
                return null;
            }
            ttSrvRaw = body;
            ttSrvId = str(jget(body, "id"));
            String src = str(jget(body, "src"));
            String type = str(jget(body, "type"));
            // dl_via_server 三个标记
            String dl = jobj(body, "dl_via_server");
            ttSrvVideoOk = "true".equals(jnum(dl, "video"));
            ttSrvMusicOk = "true".equals(jnum(dl, "music"));
            ttSrvImageOk = "true".equals(jnum(dl, "image"));
            ttSrvNote = str(jget(body, "dl_note"));

            String desc = str(jget(body, "desc"));
            String vObj = jobj(body, "video");
            String cover = str(jget(vObj, "cover"));
            String author = str(jget(jobj(body, "author"), "nickname"));
            String mObj = jobj(body, "music");
            String musicTitle = str(jget(mObj, "title"));
            String musicAuthor = str(jget(mObj, "author"));
            String musicUrl = str(jget(mObj, "playUrl"));
            String durSec = jnum(vObj, "duration");
            String playAddr = str(jget(vObj, "playAddr"));

            curId = ttSrvId;
            curDesc = desc;
            curCover = cover;
            // 关键：本地 CDN 会被 TikTok 拦（403），统一改成服务器中转地址。
            // 服务器返回 dl_url（自带 query 签名），播放器/图片加载器可直接用。
            final String srvBase = srv;
            String dlObj = jobj(body, "dl_url");
            String uVideo = str(jget(dlObj, "video"));
            String uMusic = str(jget(dlObj, "music"));
            if(uVideo.length() > 0 && !uVideo.startsWith("http")) uVideo = srvBase + uVideo;
            if(uMusic.length() > 0 && !uMusic.startsWith("http")) uMusic = srvBase + uMusic;
            // 视频：原始CDN直链优先（走本机代理），服务器中转兜底
            curPlay = playAddr.length() > 0 ? playAddr : (uVideo.length() > 0 ? uVideo : "");
            curMusic = uMusic.length() > 0 ? uMusic : musicUrl;
            curBgm = curMusic;
            curBgmTitle = musicTitle;
            curImgs = "";

            boolean isImage = "image".equals(type);
            String imgJson = isImage ? jobj(body, "images") : "";
            String imgArr = isImage ? jarrRaw(dlObj, "images") : "";
            if(isImage){
                StringBuilder ib = new StringBuilder();
                if(imgArr.length() > 0){
                    String[] rel = imgArr.split("\n");
                    for(int i = 0; i < rel.length; i++){
                        String u = rel[i].trim();
                        if(u.length() == 0) continue;
                        if(!u.startsWith("http")) u = srvBase + u;
                        if(ib.length() > 0) ib.append('|');
                        ib.append(u);
                    }
                }
                if(ib.length() == 0){
                    // 服务器没给签名地址：退回原始直链
                    String[] urls = jarrUrls(imgJson);
                    for(int i = 0; i < urls.length; i++){
                        if(ib.length() > 0) ib.append('|');
                        ib.append(urls[i]);
                    }
                }
                curImgs = ib.toString();
                curPlay = "";
            }

            final boolean hasVideo = !isImage && curPlay != null && curPlay.length() > 0;
            // 播放/图片渲染统一交给 render() 处理（它会 post 到 UI 线程），这里不再重复动作

            StringBuilder b = new StringBuilder();
            b.append('{');
            TtParser.Kv(b, "i_id", ttSrvId);
            TtParser.Kv(b, "i_platform", "TikTok");
            TtParser.Kv(b, "i_author", author);
            TtParser.Kv(b, "i_desc", desc);
            String ct = jnum(body, "createTime");
            if(ct.length() > 0 && !"0".equals(ct)) TtParser.Kv(b, "i_time", ct);
            if(durSec.length() > 0 && !"0".equals(durSec)) TtParser.Kv(b, "i_dur", String.valueOf(parseLong(durSec) * 1000));
            TtParser.Kv(b, "i_cover", cover);
            if(hasVideo) TtParser.Kv(b, "i_play", curPlay);
            // 图集与 BGM 必须放进 JSON：render() 会用 i_imgs / i_bgm 覆盖 curImgs / curBgm
            if(curImgs.length() > 0) TtParser.Kv(b, "i_imgs", curImgs);
            if(curBgm.length() > 0) TtParser.Kv(b, "i_bgm", curBgm);
            TtParser.Kv(b, "i_musicTitle", musicTitle);
            TtParser.Kv(b, "i_musicAuthor", musicAuthor);
            // 统计
            String stats = jobj(body, "stats");
            StringBuilder st = new StringBuilder();
            long pv = parseLong(jnum(stats, "play"));
            long dg = parseLong(jnum(stats, "digg"));
            long cm = parseLong(jnum(stats, "comment"));
            long sh = parseLong(jnum(stats, "share"));
            if(pv > 0) st.append("播 ").append(pv);
            if(dg > 0){ if(st.length() > 0) st.append("  "); st.append("赞 ").append(dg); }
            if(cm > 0){ if(st.length() > 0) st.append("  "); st.append("评 ").append(cm); }
            if(sh > 0){ if(st.length() > 0) st.append("  "); st.append("转 ").append(sh); }
            TtParser.Kv(b, "i_stats", st.toString());
            b.append('}');
            Log.i("DYP", "TT_SRV_OK src=" + src + " type=" + type
                    + " dlVideo=" + ttSrvVideoOk + " dlMusic=" + ttSrvMusicOk);
            return render(b.toString());
        }catch(Throwable t){
            Log.i("DYP", "TT_SRV_EX " + t);
            return null;
        }finally{
            try{ if(c != null) c.disconnect(); }catch(Throwable t){}
        }
    }

    static long parseLong(String s){
        try{ return Long.parseLong(s.trim()); }catch(Throwable t){ return 0; }
    }

    /** 从 images JSON 数组里抽 url 字段 */
    static String[] jarrUrls(String arrJson){
        java.util.List<String> out = new java.util.ArrayList<String>();
        if(arrJson == null) return new String[0];
        int i = 0;
        while(true){
            int p = arrJson.indexOf("{\"url\"", i);
            if(p < 0) break;
            int q = arrJson.indexOf('"', p + 7);
            if(q < 0) break;
            int e = arrJson.indexOf('"', q + 1);
            if(e < 0) break;
            String u = arrJson.substring(q + 1, e);
            if(u.startsWith("http")) out.add(unesc(u));
            i = e + 1;
        }
        return out.toArray(new String[0]);
    }

    /** 安全取字符串（jget 可能返回 null） */
    static String str(String s){ return s == null ? "" : s; }

    /** 从 JSON 取数字或布尔值（jget 只认字符串，数字会返回 ""） */
    static String jnum(String json, String key){
        if(json == null) return "";
        int p = json.indexOf("\"" + key + "\"");
        if(p < 0) return "";
        int c = json.indexOf(':', p);
        if(c < 0) return "";
        int i = c + 1;
        while(i < json.length() && Character.isWhitespace(json.charAt(i))) i++;
        int b = i;
        while(i < json.length()){
            char ch = json.charAt(i);
            if(ch == ',' || ch == '}' || ch == ']' || Character.isWhitespace(ch)) break;
            i++;
        }
        return json.substring(b, i);
    }

    /** 从 JSON 里取对象子串：{"key":{...}} -> {...} */
    static String jobj(String json, String key){
        if(json == null) return "";
        int p = json.indexOf("\"" + key + "\"");
        if(p < 0) return "";
        int c = json.indexOf(':', p);
        if(c < 0) return "";
        int b = json.indexOf('{', c);
        if(b < 0) return "";
        int depth = 0;
        for(int i = b; i < json.length(); i++){
            char ch = json.charAt(i);
            if(ch == '"'){   // 跳过字符串内的括号
                i++;
                while(i < json.length()){
                    char x = json.charAt(i);
                    if(x == '\\'){ i += 2; continue; }
                    if(x == '"') break;
                    i++;
                }
                continue;
            }
            if(ch == '{') depth++;
            else if(ch == '}'){
                depth--;
                if(depth == 0) return json.substring(b, i + 1);
            }
        }
        return "";
    }

    /** 读 InputStream 全文 */
    static String readAll(InputStream in){
        if(in == null) return "";
        try{
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            return new String(bo.toByteArray(), "UTF-8");
        }catch(Throwable t){
            return "";
        }finally{
            try{ in.close(); }catch(Throwable t){}
        }
    }

    /** JSON 反斜杠转义还原 */
    static String unesc(String s){
        if(s == null) return "";
        if(s.indexOf('\\') < 0) return s;
        StringBuilder b = new StringBuilder(s.length());
        for(int i = 0; i < s.length(); i++){
            char c = s.charAt(i);
            if(c == '\\' && i + 1 < s.length()){
                char e = s.charAt(++i);
                if(e == 'n') b.append('\n');
                else if(e == 't') b.append('\t');
                else if(e == 'r') b.append('\r');
                else if(e == 'u' && i + 4 < s.length()){
                    try{
                        b.append((char)Integer.parseInt(s.substring(i + 1, i + 5), 16));
                        i += 4;
                    }catch(Throwable t){ b.append(e); }
                }
                else b.append(e);
            } else {
                b.append(c);
            }
        }
        return b.toString();
    }

    /** 从 JSON 里的纯字符串数组抽元素（换行拼接），如 dl_url.images */
    static String jarrRaw(String json, String key){
        if(json == null) return "";
        String tag = "\"" + key + "\":";
        int p = json.indexOf(tag);
        if(p < 0) return "";
        int lb = json.indexOf('[', p);
        if(lb < 0) return "";
        int rb = json.indexOf(']', lb);
        if(rb < 0) return "";
        String inner = json.substring(lb + 1, rb);
        StringBuilder b = new StringBuilder();
        java.util.regex.Matcher m = java.util.regex.Pattern
            .compile("\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(inner);
        while(m.find()){
            String s = unesc(m.group(1));
            if(s.length() == 0) continue;
            if(b.length() > 0) b.append('\n');
            b.append(s);
        }
        return b.toString();
    }


    /** 把 TtParser 的结果组装成 render() 认的 JSON */
    String buildTtResult(TtParser.TtVideo v){
        try{
            curId = v.id;
            curDesc = v.desc;
            curPlay = v.playUrl.length() > 0 ? v.playUrl : v.downloadUrl;
            curCover = v.cover;
            curImgs = "";
            curMusic = "";   // TikTok 的 music.playUrl 是配乐片段，不是原声，不当音轨用
            final boolean hasVideo = curPlay != null && curPlay.length() > 0;
            h.post(new Runnable(){ public void run(){
                actions.setVisibility(View.VISIBLE);
                maybeAutoAction();   // 解析完成：按设置自动播放/下载
                img.setVisibility(View.GONE);
                // 自动播放关闭时不起播，只显示播放按钮（用户手动点 play）
                if(hasVideo && autoPlayOn()) doPlay();
            }});

            StringBuilder b = new StringBuilder();
            b.append('{');
            TtParser.Kv(b, "i_id", v.id);
            TtParser.Kv(b, "i_platform", "TikTok");
            TtParser.Kv(b, "i_author", v.author);
            TtParser.Kv(b, "i_desc", v.desc);
            if(v.createTime > 0) TtParser.Kv(b, "i_time", String.valueOf(v.createTime));
            if(v.durationMs > 0) TtParser.Kv(b, "i_dur", String.valueOf(v.durationMs));
            TtParser.Kv(b, "i_cover", v.cover);
            if(hasVideo) TtParser.Kv(b, "i_play", curPlay);
            TtParser.Kv(b, "i_musicTitle", v.musicTitle);
            TtParser.Kv(b, "i_musicAuthor", v.musicAuthor);
            StringBuilder st = new StringBuilder();
            if(v.playCount > 0) st.append("播 ").append(v.playCount);
            if(v.diggCount > 0){ if(st.length() > 0) st.append("  "); st.append("赞 ").append(v.diggCount); }
            if(v.commentCount > 0){ if(st.length() > 0) st.append("  "); st.append("评 ").append(v.commentCount); }
            if(v.shareCount > 0){ if(st.length() > 0) st.append("  "); st.append("转 ").append(v.shareCount); }
            TtParser.Kv(b, "i_stats", st.toString());
            b.append('}');
            String json = b.toString();
            return render(json);
        }catch(Throwable t){
            Log.i("DYP", "TT_BUILD_ERR " + t);
            return null;
        }
    }

    /** TikTok 失败提示（区分原因） */
    static String buildTtError(String reason){
        StringBuilder b = new StringBuilder();
        b.append("【解析失败】\n").append(reason).append("\n\n");
        b.append("常见原因：\n");
        b.append("1. 网络访问不了 TikTok（可开「内置代理」走服务器中转）\n");
        b.append("2. 出口 IP 被 TikTok 标记\n");
        b.append("3. 作品已删除或设为私密\n\n");
        b.append("建议：「设置 → 代理 → 内置代理」开启后重试。");
        return b.toString();
    }

    /**
     * 把 /t/xxx 或 vt.tiktok.com 短链换成 /@user/video/id 完整链接。
     * 关键：TikTok 对短链跳转来的请求只返回精简页（无 api-data），必须走完整链接。
     */
    String normalizeTikTokUrl(String url){
        if(url == null) return "";
        boolean isShort = url.indexOf("tiktok.com/t/") >= 0
                       || url.indexOf("vt.tiktok.com") >= 0
                       || url.indexOf("vm.tiktok.com") >= 0;
        if(!isShort) return url;
        String real = resolveShortUrl(this, url);
        if(real == null) return url;
        // 去掉跟踪参数，只保留干净的 /@user/video/id
        int q = real.indexOf('?');
        if(q > 0 && real.indexOf("/video/") > 0 && q > real.indexOf("/video/")) real = real.substring(0, q);
        Log.i("DYP", "TT_NORMALIZED " + real);
        return real;
    }

    /** 用 TikTok 专用 UA/Referer 抓页面（普通版） */
    String httpGetTikTok(String url){
        return httpGetTikTokInternal(url, false);
    }

    /** 强制带「完整页」特征的请求：不带 _r 跟踪参数、带 Cookie、桌面化 sec-ch 头 */
    String httpGetTikTokFull(String url){
        return httpGetTikTokInternal(url, true);
    }

    String httpGetTikTokInternal(String url, boolean full){
        InputStream in = null;
        try{
            // 去掉 _r / _t 之类的分享跟踪参数，避免被判定为分享来源
            String clean = url;
            if(full){
                int q = clean.indexOf('?');
                if(q > 0) clean = clean.substring(0, q);
            }
            HttpURLConnection c = openConnRetry(clean);
            c.setInstanceFollowRedirects(true);
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 12; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36");
            c.setRequestProperty("Referer", "https://www.tiktok.com/");
            c.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
            c.setRequestProperty("Accept-Language", "en-US,en;q=0.9");
            if(full){
                c.setRequestProperty("Sec-Fetch-Dest", "document");
                c.setRequestProperty("Sec-Fetch-Mode", "navigate");
                c.setRequestProperty("Sec-Fetch-Site", "none");
                c.setRequestProperty("Upgrade-Insecure-Requests", "1");
                // 复用 WebView 的 Cookie，通常能拿到完整页
                try{
                    String ck = CookieManager.getInstance().getCookie("https://www.tiktok.com/");
                    if(ck != null && ck.length() > 0) c.setRequestProperty("Cookie", ck);
                }catch(Throwable t){}
            }
            c.setConnectTimeout(15000); c.setReadTimeout(20000);
            in = c.getInputStream();
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[16384]; int n;
            while((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            in.close();
            String html = new String(bo.toByteArray(), "UTF-8");
            Log.i("DYP", "TT_GET len=" + html.length() + " full=" + full + " hasApi=" + (html.indexOf("id=\"api-data\"") >= 0));
            return html;
        }catch(Throwable t){ Log.i("DYP", "TT_GET_ERR " + t); return null; }
        finally{ try{ if(in != null) in.close(); }catch(Throwable t){} }
    }

    /** 从 HTML 抠出 <script id="api-data"> 的 JSON */
    static String extractTikTokApiData(String html){
        try{
            int i = html.indexOf("id=\"api-data\"");
            if(i < 0) return null;
            int s = html.indexOf('>', i);
            if(s < 0) return null;
            int e = html.indexOf("</script>", s);
            if(e < 0) return null;
            String body = html.substring(s + 1, e).trim();
            if(body.startsWith("{")) return body;
            int a0 = body.indexOf('{');
            if(a0 >= 0) return body.substring(a0);
        }catch(Throwable t){}
        return null;
    }

    /** 用 api-data JSON 组装展示结果 */
    String buildTikTokResult(String json, String pageUrl){
        try{
            Object root = new JP(json).parse();
            Object vd = findKey(root, "videoDetail");
            if(vd == null) vd = root;
            Object item = null;
            if(vd instanceof Map){
                Object ii = ((Map)vd).get("itemInfo");
                if(ii instanceof Map) item = ((Map)ii).get("itemStruct");
            }
            if(!(item instanceof Map)) item = findKey(root, "itemStruct");
            if(!(item instanceof Map)){ Log.i("DYP", "TT_NO_ITEM"); return null; }
            Map it = (Map)item;

            String id = gs(get(it, "id"));
            String desc = gs(get(it, "desc"));
            Map au = (Map)get(it, "author");
            String author = au != null ? gs(get(au, "nickname")) : "";
            if(author.length() == 0 && au != null) author = gs(get(au, "uniqueId"));
            Map v = (Map)get(it, "video");
            String play = "", dl = "", cover = "";
            long dur = 0;
            if(v != null){
                play = gs(get(v, "playAddr"));
                dl   = gs(get(v, "downloadAddr"));
                cover= gs(get(v, "cover"));
                if(cover.length() == 0) cover = gs(get(v, "originCover"));
                Object dOb = get(v, "duration");
                if(dOb != null) dur = parseLong(dOb, 0) * 1000L;
            }
            Map mu = (Map)get(it, "music");
            String mTitle = mu != null ? gs(get(mu, "title")) : "";
            String mAuthor = mu != null ? gs(get(mu, "authorName")) : "";
            long ctime = parseLong(get(it, "createTime"), 0);

            // 写入全局状态供播放/下载使用
            curId = id;
            curDesc = desc;
            curPlay = play.length() > 0 ? play : dl;
            curCover = cover;
            curImgs = "";
            final boolean hasVideo = curPlay != null && curPlay.length() > 0;
            h.post(new Runnable(){ public void run(){
                actions.setVisibility(View.VISIBLE);
                maybeAutoAction();   // 解析完成：按设置自动播放/下载
                img.setVisibility(View.GONE);
                // 自动播放关闭时不起播，只显示播放按钮（用户手动点 play）
                if(hasVideo && autoPlayOn()) doPlay();
            }});

            // 是否真的会走代理下载这条 TikTok 链接（现在一律直连，仅提示用途）
            StringBuilder b = new StringBuilder();
            b.append("【作品ID】").append(id).append("\n");
            b.append("【平台】TikTok\n");
            b.append("【作者】").append(author).append("\n");
            b.append("【描述】").append(desc).append("\n");
            if(ctime > 0) b.append("【发布时间】").append(fmtTime(String.valueOf(ctime))).append("\n");
            if(dur > 0) b.append("【时长】").append(fmtDuration(String.valueOf(dur))).append("\n");
            if(cover.length() > 0) b.append("【封面】").append(cover).append("\n");
            if(play.length() > 0) b.append("【视频直链】").append(play).append("\n");
            if(dl.length() > 0) b.append("【下载直链】").append(dl).append("\n");
            if(mTitle.length() > 0) b.append("【背景音乐】").append(mTitle).append("\n");
            if(mAuthor.length() > 0) b.append("【音乐作者】").append(mAuthor).append("\n");
            if(hasVideo) b.append("【音频下载】点下方「提取音频」，从视频中提取音轨为 MP3\n");
            b.append("\n【注意】TikTok 视频 CDN 有地区限制，直连下载可能 403。\n");
            b.append("　　　　打不开就开「设置 → 代理 → 内置代理」走服务器中转。");
            return b.toString();
        }catch(Throwable t){ Log.i("DYP", "TT_BUILD_ERR " + t); return null; }
    }

    String render(String json){
        Object res=new JP(json).parse();
        Map m=(res instanceof Map)?(Map)res:new HashMap();
        String iid=gs(get(m,"i_id"));
        if(iid.length()>0){
            curId=iid;
            curDesc=gs(get(m,"i_desc"));
            curPlay=gs(get(m,"i_ws"));
            if(curPlay.length()==0) curPlay=gs(get(m,"i_play"));
            // YouTube direct：独立音轨（googlevideo itag 140 m4a），播放器合并、下载后本地 mux
            curAudio=gs(get(m,"i_audio"));
            curCover=gs(get(m,"i_cover"));
            curImgs=gs(get(m,"i_imgs"));
            String dyn=gs(get(m,"i_dyn"));
            if(curImgs.length()>0){ if(dyn.length()>0) curImgs=curImgs+"|DYN|"+dyn; }
            final boolean isImg=curImgs.length()>0;
            // 渲染代次：只有最新一次渲染能改界面，避免旧的解析结果盖掉新的
            final int myGen = ++renderGen;
            h.post(new Runnable(){ public void run(){
                if(myGen != renderGen){
                    Log.i("DYP", "RENDER_SKIP gen=" + myGen + " cur=" + renderGen);
                    return;
                }
                // 静默批量解析：数据已经由 collectBatchItem() 收好，
                // 这里不再改界面，避免每条结果一闪就被下一条覆盖。
                if(batchSilent && batchRunning){
                    Log.i("DYP", "RENDER_SILENT skip 图集=" + isImg);
                    collectBatchItem();
                    // 静默模式也要按设置自动下载（用户开了就下，不影响静默）
                    if(autoDlOn()){
                        boolean hasImgs2 = imgUrls != null && imgUrls.size() > 0;
                        boolean hasVideo2 = curPlay != null && curPlay.length() > 0;
                        if(!profileMode && (hasImgs2 || hasVideo2)){
                            try{ doDownload(); Log.i("DYP", "AUTO_DL 静默触发"); }catch(Throwable t){}
                        }
                    }
                    maybeClearInput();
                    batchItemFinished();
                    return;
                }
                if(isImg){
                    actions.setVisibility(View.VISIBLE);
                    video.setVisibility(View.GONE);
                    img.setVisibility(View.VISIBLE);
                    videoBox.setVisibility(View.GONE);
                    // 切到图集时释放上一个视频的播放器，避免状态残留
                    try{ if(miniPlayer != null){ miniPlayer.release(); miniPlayer = null; } }catch(Throwable t){}
                    miniProxyKey = null;
                    parseImgs(); imgIdx=0; imgZoomed=false;
                    if(imgUrls.size()>0){ curShowImg=imgUrls.get(0); loadImage(curShowImg, img); applyLiveBadge(0); }
                    Toast.makeText(MainActivity.this, imgTipText(), Toast.LENGTH_SHORT).show();
                    maybeAutoAction();   // 图集渲染完成后再判断（此时 imgUrls 已是本条数据）
                } else {
                    actions.setVisibility(View.VISIBLE);
                    // 切到视频时清掉图集状态
                    imgUrls.clear(); dynUrls.clear(); curImgs=""; imgZoomed=false;
                    try{ ((TextView)findViewById(R.id.liveBadge)).setVisibility(View.GONE); }catch(Throwable t){}
                    if(autoPlayOn()){
                        // 自动播放开：正常起播
                        img.setVisibility(View.GONE);
                        doPlay();
                    } else {
                        // 自动播放关：不拉流，改成显示封面图当预览
                        // （video 是 SurfaceView，未起播时是黑的，直接盖住封面看不见，
                        //   所以这里把 video 藏掉、用 img 显示封面，点「播放」再切回来）
                        video.setVisibility(View.GONE);
                        if(curCover != null && curCover.length() > 0){
                            img.setVisibility(View.VISIBLE);
                            loadImage(curCover, img);
                            Log.i("DYP", "VIDEO_PREVIEW 显示封面预览 " + curCover.substring(0, Math.min(90, curCover.length())));
                        } else {
                            img.setVisibility(View.GONE);
                            Log.i("DYP", "VIDEO_PREVIEW 无封面可用");
                        }
                    }
                    maybeAutoAction();   // 视频已起播/预览，再按设置自动下载（避免和 doPlay 抢资源）
                }
            }});
            // 独立音轨：只有快手分享页会直接给完整 m4a；抖音的 music 是背景音乐片段，不能当原声用
            curMusic = "快手".equals(gs(get(m,"i_platform"))) ? gs(get(m,"i_music")) : "";
            // BGM：抖音 detail 给的完整背景音乐 MP3，单独下载，不当视频音轨用
            curBgm = gs(get(m,"i_bgm"));
            curBgmTitle = gs(get(m,"i_bgmTitle"));
            // X 多视频：解析全部视频直链，下载时可逐个选
            curXVideos.clear(); curXVidDur.clear(); curXVidSize.clear();
            try{
                if("X".equals(gs(get(m,"i_platform")))){
                    String mix = gs(get(m,"i_mix"));
                    if(mix.length() > 0){
                        String[] xs = mix.split("\\|");
                        String[] ds = gs(get(m,"i_mixDur")).split("\\|", -1);
                        String[] ss = gs(get(m,"i_mixSize")).split("\\|", -1);
                        for(int i = 0; i < xs.length; i++){
                            if(xs[i].length() == 0) continue;
                            curXVideos.add(xs[i]);
                            curXVidDur.add(i < ds.length ? ds[i] : "");
                            curXVidSize.add(i < ss.length ? ss[i] : "");
                        }
                    }
                }
            }catch(Throwable t){ Log.i("DYP", "XV_MIX_ERR " + t); }
            h.post(new Runnable(){ public void run(){
                if(btnMusic != null){
                    if(curBgm != null && curBgm.length() > 0){
                        btnMusic.setVisibility(View.VISIBLE);
                        btnMusic.setText(curBgmTitle.length() > 0 ? "BGM" : "BGM");
                    } else {
                        btnMusic.setVisibility(View.GONE);
                    }
                }
            }});
            StringBuilder b=new StringBuilder();
            String plat = gs(get(m,"i_platform"));
            if(plat.length() > 0) b.append("【平台】").append(plat).append("\n");
            b.append("【作品ID】").append(iid).append("\n");
            b.append("【作者】").append(gs(get(m,"i_author"))).append("\n");
            b.append("【描述】").append(gs(get(m,"i_desc"))).append("\n");
            b.append("【发布时间】").append(fmtTime(gs(get(m,"i_time")))).append("\n");
            b.append("【时长】").append(fmtDuration(gs(get(m,"i_dur")))).append("\n");
            String stats = gs(get(m,"i_stats"));
            if(stats.length() > 0) b.append("【数据】").append(stats).append("\n");
            String poet = gs(get(m,"i_poet"));
            if(poet.length() > 0) b.append("【发布地点】").append(poet).append("\n");
            String tags = gs(get(m,"i_tags"));
            if(tags.length() > 0) b.append("【话题】").append(tags).append("\n");
            b.append("【封面】").append(curCover).append("\n");
            b.append("【视频直链】").append(gs(get(m,"i_play"))).append("\n");
            String ws=gs(get(m,"i_ws"));
            if(ws.length()>0) b.append("【无水印直链】").append(ws).append("\n");
            // TikTok 老路径（i_tt）才需要提示；新 TtParser 路径已由 i_platform 输出平台名
            if(gs(get(m,"i_tt")).length() > 0 && plat.length() == 0){
                b.append("【平台】TikTok\n");
                b.append("【提示】TikTok 视频 CDN 有地区限制，直连可能 403。\n");
                b.append("　　　　打不开就开「设置 → 代理 → 内置代理」走服务器中转。\n");
            }
            String mt = gs(get(m,"i_musicTitle"));
            if(mt.length()>0) b.append("【背景音乐】").append(mt).append("\n");
            String ma = gs(get(m,"i_musicAuthor"));
            if(ma.length()>0) b.append("【音乐作者】").append(ma).append("\n");
            String murl = gs(get(m,"i_music"));
            if(murl.length()>0) b.append("【音轨直链】").append(murl).append("\n");
            // BGM 直链：detail 主路径给完整 MP3，点 BGM 按钮直接下载
            if(curBgm != null && curBgm.length() > 0){
                b.append("【背景音乐下载】点下方「BGM」按钮直接下载完整原曲 MP3\n");
            }
            if(curPlay != null && curPlay.length() > 0){
                if(curMusic != null && curMusic.length() > 0){
                    b.append("【音频下载】已拿到独立音轨，点「提取音频」直接转 MP3（比抽视频音轨快）\n");
                } else {
                    b.append("【音频下载】点下方「提取音频」，从视频中提取音轨为 MP3\n");
                }
            }
            if(curImgs.length()>0) b.append("【图集】").append(curImgs).append("\n");
            return b.toString();
        }
        // slidesinfo 原始 JSON：直接走 formatNode
        try{
            Object ad = findKey(res, "aweme_details");
            if(ad == null) ad = findKey(res, "aweme_list");
            if(ad instanceof List && !((List)ad).isEmpty()){
                Map item = (Map)((List)ad).get(0);
                String iid2 = gs(get(item, "aweme_id"));
                if(iid2.length() > 0){
                    curId = iid2;
                    curDesc = gs(get(item,"desc"));
                    // 图片集
                    StringBuilder imgs = new StringBuilder();
                    Object im = get(item, "images");
                    StringBuilder dyns = new StringBuilder();
                    if(im instanceof List){
                        for(Object o : (List)im){
                            Map im2 = (Map)o;
                            Object ul = get(im2, "url_list");
                            if(ul instanceof List && !((List)ul).isEmpty()){
                                String uu = gs(((List)ul).get(((List)ul).size()-1));
                                if(imgs.length() > 0) imgs.append("|");
                                imgs.append(uu);
                            }
                            // 动图：每张图可能带 video.play_addr
                            Map iv = (Map) get(im2, "video");
                            if(iv != null){
                                Map pa2 = (Map) get(iv, "play_addr");
                                Object pl = pa2 != null ? get(pa2, "url_list") : null;
                                if(pl instanceof List && !((List)pl).isEmpty()){
                                    if(dyns.length() > 0) dyns.append("|");
                                    dyns.append(gs(((List)pl).get(0)));
                                }
                            }
                        }
                    }
                    final String dynStr = dyns.toString();
                    final boolean isImg2 = imgs.length() > 0;
                    // 图片 + 动图一起存入 curImgs（不能分开赋值，会互相覆盖）
                    if(isImg2 || dynStr.length() > 0){
                        curImgs = (isImg2 ? imgs.toString() : "") + (dynStr.length() > 0 ? "|DYN|" + dynStr : "");
                        Log.i("DYP", "SLIDES_DYN imgs=" + (isImg2 ? imgs.toString().split("[|]").length : 0) + " dyn=" + (dynStr.length() > 0 ? dynStr.split("[|]").length : 0));
                    }
                    curCover = firstUrl(get((Map)get(item,"video"), "cover"));
                    Map au = (Map)get(item,"author");
                    String author = au != null ? gs(get(au,"nickname")) : "";
                    String desc2 = gs(get(item,"desc"));
                    h.post(new Runnable(){ public void run(){
                        actions.setVisibility(View.VISIBLE);
                        maybeAutoAction();   // 解析完成：按设置自动播放/下载
                        if(isImg2){
                            video.setVisibility(View.GONE); img.setVisibility(View.VISIBLE);
                            parseImgs(); imgIdx = 0;
                            if(imgUrls.size() > 0){ curShowImg = imgUrls.get(0); loadImage(curShowImg, img); }
                            Toast.makeText(MainActivity.this, "共" + imgUrls.size() + "张，右下角下载可选全部", Toast.LENGTH_SHORT).show();
                        }
                    }});
                    StringBuilder b = new StringBuilder();
                    b.append("【作品ID】").append(iid2).append("\n");
                    b.append("【作者】").append(author).append("\n");
                    b.append("【描述】").append(desc2).append("\n");
                    b.append("【类型】幻灯片/图文，共").append(imgUrls.size() > 0 ? imgUrls.size() : imgs.toString().split("[|]").length).append("张\n");
                    b.append("【图集】").append(curImgs).append("\n");
                    return b.toString();
                }
            }
        }catch(Throwable t){ Log.i("DYP", "SLIDES_RENDER_ERR " + t); }

        String detail=gs(get(m,"detail"));
        String dh=gs(get(m,"detailHead"));
        if(dh.length()>5 && dh.charAt(0)=='{'){
            try{
                Object d=new JP(dh).parse();
                Object ad=findKey(d,"aweme_detail");
                String r=formatNode(ad!=null?ad:d);
                if(r!=null && r.length()>0) return r;
            }catch(Exception e){}
        }
        if(detail.length()>5 && detail.charAt(0)=='{'){
            try{
                Object d=new JP(detail).parse();
                Object ad=findKey(d,"aweme_detail");
                String r=formatNode(ad!=null?ad:d);
                if(r!=null && r.length()>0) return r;
            }catch(Exception e){}
        }
        String router=gs(get(m,"router"));
        if(router.length()>5 && !router.equals("null")){
            try{
                Object d=new JP(router).parse();
                String r=formatNode(d);
                if(r!=null && r.length()>0) return r;
            }catch(Exception e){}
        }
        String html=gs(get(m,"html"));
        String rd=extractRouterData(html);
        if(rd!=null){ try{ String r=formatNode(new JP(rd).parse()); if(r!=null&&r.length()>0) return r; }catch(Exception e){} }
        // TikTok 兜底需发网络请求，不能在主线程做（render 在 Bridge 主线程回调里）
        // 这里只标记，交给后台线程重新走一遍解析
        String hrefTt = gs(get(m,"href"));
        if(hrefTt.indexOf("tiktok.com") >= 0){
            Log.i("DYP", "TT_NEED_BG_PARSE " + hrefTt);
            triggerTikTokBgParse(hrefTt);
        }
        StringBuilder b=new StringBuilder();
        String href = gs(get(m,"href"));
        String id   = gs(get(m,"id"));
        String body = gs(get(m,"bodyText"));
        // 判断是不是风控/无效页
        boolean blocked = body.indexOf("抱歉出错") >= 0 || body.indexOf("请尝试在抖音内观看") >= 0;
        if(blocked){
            b.append("【解析失败】\n");
            b.append("抖音返回了限制页面，可能原因：\n");
            b.append("1. 链接已过期或作品已删除\n");
            b.append("2. 请求过于频繁，触发风控\n");
            b.append("3. 该作品需在抖音 App 内观看\n\n");
            b.append("建议：稍后重试，或换一条分享链接。");
            return b.toString();
        }
        if(id.length() > 0){
            b.append("【作品ID】").append(id).append("\n");
        }
        b.append("【提示】未能获取到完整信息\n");
        if(href.length() > 0) b.append("【页面】").append(href);
        return b.toString();
    }

    String httpGet(String url){
        try{
            HttpURLConnection c=openConn(url);
            c.setInstanceFollowRedirects(true);
            c.setRequestProperty("User-Agent","Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            c.setConnectTimeout(15000); c.setReadTimeout(20000);
            InputStream in=c.getInputStream();
            ByteArrayOutputStream bo=new ByteArrayOutputStream();
            byte[] buf=new byte[8192]; int n;
            while((n=in.read(buf))>0) bo.write(buf,0,n);
            in.close();
            return new String(bo.toByteArray(),"UTF-8");
        }catch(Exception e){ return null; }
    }
    String extractUrl(String t){
        if(t == null) return null;
        // 优先匹配抖音域名（避免把口令里的其他链接当成目标）
        Matcher m = Pattern.compile("https?://(?:v\\.douyin\\.com|www\\.iesdouyin\\.com|www\\.douyin\\.com|iesdouyin\\.com|www\\.huoshan\\.com|v\\.huoshan\\.com)/[A-Za-z0-9_./-]*").matcher(t);
        if(m.find()) return m.group();
        // 快手：短链大小写混排（J2bQ82Hb），必须 [a-zA-Z0-9]
        m = Pattern.compile("https?://(?:v\\.kuaishou\\.com|www\\.kuaishou\\.com|m\\.kuaishou\\.com|v\\.m\\.chenzhongtech\\.com|www\\.chenzhongtech\\.com|v\\.m\\.gifshow\\.com)/[A-Za-z0-9_.-/]*").matcher(t);
        if(m.find()) return m.group();
        // 兼容无协议的 v.douyin.com/xxxx
        m = Pattern.compile("(?:v\\.douyin\\.com|www\\.iesdouyin\\.com|www\\.huoshan\\.com|v\\.huoshan\\.com)/[A-Za-z0-9_./-]+").matcher(t);
        if(m.find()) return "https://" + m.group();
        // 兼容无协议的 v.kuaishou.com/xxxx
        m = Pattern.compile("(?:v\\.kuaishou\\.com|www\\.kuaishou\\.com|m\\.kuaishou\\.com|v\\.m\\.chenzhongtech\\.com)/[A-Za-z0-9_.-]+").matcher(t);
        if(m.find()) return "https://" + m.group();
        // X / Twitter：x.com/{user}/status/{id}、twitter.com、t.co 短链
        // 注意：X 链接常带 ?s=20 之类查询参数，字符集要含 ? = &
        m = Pattern.compile("https?://(?:www\\.|mobile\\.)?(?:x\\.com|twitter\\.com)/[A-Za-z0-9_./?=&%#@:+-]*").matcher(t);
        if(m.find()) return m.group();
        m = Pattern.compile("https?://t\\.co/[A-Za-z0-9]+").matcher(t);
        if(m.find()) return m.group();
        // 兼容无协议的 x.com/.../status/...
        m = Pattern.compile("(?:www\\.|mobile\\.)?(?:x\\.com|twitter\\.com)/[A-Za-z0-9_./?=&%#@:+-]*").matcher(t);
        if(m.find()) return "https://" + m.group();
        // YouTube：watch?v= / shorts / playlist / youtu.be
        m = Pattern.compile("https?://(?:www\\.|m\\.)?(?:youtube\\.com|youtu\\.be)/[A-Za-z0-9_./?=&%#@:+-]*").matcher(t);
        if(m.find()) return m.group();
        // 兼容无协议的 youtu.be/xxx
        m = Pattern.compile("(?:youtube\\.com|youtu\\.be)/[A-Za-z0-9_./?=&%#@:+-]*").matcher(t);
        if(m.find()) return "https://" + m.group();
        // B站：b23.tv 短链 / bilibili.com / m.bilibili.com / space
        m = Pattern.compile("https?://(?:b23\\.tv|(?:www\\.|m\\.)?bilibili\\.com)/[A-Za-z0-9_./?=&%#@:+-]*").matcher(t);
        if(m.find()) return m.group();
        // 兼容无协议的 b23.tv/xxxx、bilibili.com/video/xxx
        m = Pattern.compile("(?:b23\\.tv|(?:www\\.|m\\.)?bilibili\\.com)/[A-Za-z0-9_./?=&%#@:+-]*").matcher(t);
        if(m.find()) return "https://" + m.group();
        // 兜底：任意 http 链接
        m = Pattern.compile("https?://[\\w./?=&%#@:+-]+").matcher(t);
        if(m.find()) return m.group();
        return null;
    }

    /** 展开后的 B站 链接清理：只留域名+路径，p 参数单独保留（分P用），其余追踪参数丢掉 */
    static String cleanBiliUrl(String url){
        if(url == null) return url;
        try{
            int q = url.indexOf('?');
            if(q < 0) return url;
            String base = url.substring(0, q);
            String query = url.substring(q + 1);
            // 只保留 p=（分P）和 bvid= / aid=（少数分享用参数形式）
            StringBuilder keep = new StringBuilder();
            for(String kv : query.split("&")){
                if(kv.length() == 0) continue;
                String k = kv;
                int e = kv.indexOf('=');
                if(e > 0) k = kv.substring(0, e);
                if(k.equals("p") || k.equals("bvid") || k.equals("aid")){
                    if(keep.length() > 0) keep.append('&');
                    keep.append(kv);
                }
            }
            String out = keep.length() > 0 ? (base + "?" + keep) : base;
            return out;
        }catch(Throwable t){ return url; }
    }

    /** b23.tv 短链展开：跟随 302 拿到真实链接（bilibili.com/video/... 或 /space/...） */
    static String expandB23(String url){
        if(url == null) return url;
        String low = url.toLowerCase();
        if(!low.contains("b23.tv")) return url;
        try{
            java.net.HttpURLConnection c = (java.net.HttpURLConnection)
                    new java.net.URL(url).openConnection(java.net.Proxy.NO_PROXY);
            c.setInstanceFollowRedirects(false);
            c.setConnectTimeout(8000);
            c.setReadTimeout(10000);
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                    + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36");
            int code = c.getResponseCode();
            String loc = c.getHeaderField("Location");
            try{ c.disconnect(); }catch(Throwable t){}
            if(code >= 300 && code < 400 && loc != null && loc.length() > 0){
                if(loc.startsWith("/")) loc = "https://b23.tv" + loc;
                Log.i("DYP", "B23_EXPAND " + url + " -> " + loc.substring(0, Math.min(120, loc.length())));
                // 可能多跳，最多跟 3 次
                if(loc.toLowerCase().contains("b23.tv")) return expandB23(loc);
                return loc;
            }
        }catch(Throwable t){ Log.i("DYP", "B23_EXPAND_ERR " + t); }
        return url;
    }
/** 批量解析结果收集到列表页（与主页/合集共用 listWrap，便于统一查看与下载） */
    final java.util.List<ProfileParser.ProfileItem> batchCollected =
            java.util.Collections.synchronizedList(new java.util.ArrayList<ProfileParser.ProfileItem>());

    /** 批量解析模式：结果逐条攒进 batchCollected，跑完后一次性渲染到列表页 */
    volatile boolean batchCollectMode = false;
    /** 批量解析：等待当前条解析完成（下载/播放完毕）再推进，不按固定 15 秒 */
    volatile boolean batchWaitDone = false;
    /** 批量解析静默模式：不逐条渲染到解析页，跑完一次性出列表（避免结果一闪就被下一条覆盖） */
    volatile boolean batchSilent = false;
    /** 批量解析持久化开关（默认开启）：退出重进仍保留结果 */
    static final String KEY_BATCH_KEEP = "batch_keep";
    /** 列表持久化文件（batch 结果存 JSON，退出重进可恢复） */
    /** 批量解析：用户在解析页查看第几条（0-based，对应 batchUrls/batchCollected） */
    volatile int batchViewIndex = 0;
    /** 批量解析：等当前条解析完成的兜底计时器 */
    Runnable batchAdvanceRunnable = null;
    /** 批量解析队列：一行一个链接，串行解析（WebView 是单实例，不能并发）。 */
    final java.util.List<String> batchUrls = new java.util.ArrayList<String>();
    volatile boolean batchRunning = false;
    volatile int batchIndex = 0;

    /** 该链接是否属于「大解析」（主页 / 合集），需要先弹确认框 */
    boolean needConfirmBigParse(String u){
        if(u == null) return false;
        String low = u.toLowerCase();
        // 明确的主页链接（抖音 / 快手 / B站 / IG）
        if(isProfilePage(u)) return true;
        // 合集 / 短剧 / 快手合集 / B站合集
        if(low.contains("/collection/") || low.contains("/mix/") || low.contains("mix_id=")
                || low.contains("playlet") || low.contains("/series/")
                || low.contains("bilibili.com/list/") || low.contains("space.bilibili.com/")
                || low.contains("kuaishou.com/profile/")){
            return true;
        }
        return false;
    }

    /** 猜一下这是主页还是合集（仅用于弹窗文案） */
    String guessBigParseKind(String u){
        if(u == null) return "列表";
        String low = u.toLowerCase();
        if(isProfilePage(u)) return "主页";
        if(low.contains("playlet")) return "短剧";
        if(low.contains("/collection/") || low.contains("/mix/") || low.contains("mix_id=")
                || low.contains("/series/") || low.contains("bilibili.com/list/")){
            return "合集";
        }
        return "列表";
    }

    /** 主页 / 合集解析前的确认弹窗。
     *  - 会清空列表（非批量模式）→ 必须让用户知道
     *  - 开了自动下载 → 提醒可能会批量下载很多文件，可选择先关掉
     *  @param kind "主页" 或 "合集"
     *  @param onGo 用户确认继续后的动作
     */
    void confirmBigParse(final String kind, final Runnable onGo){
        confirmBigParse(kind, onGo, null);
    }

    /** 带取消回调的版本（取消时做清理，如停掉已加载的 WebView） */
    void confirmBigParse(final String kind, final Runnable onGo, final Runnable onCancel){
        bigParsePending = true;
        final boolean autoDl = autoDlOn();
        StringBuilder msg = new StringBuilder();
        msg.append("即将解析").append(kind).append("。\n\n");
        msg.append("· 会一次性抓取全部作品（可能几十到几百条）\n");
        msg.append("· 列表页现有的内容会被清空并替换成这次的").append(kind).append("结果\n");
        if(autoDl){
            msg.append("\n⚠ 你开启了「解析后自动下载」\n");
            msg.append("解析过程中成功的条目会自动入下载队列，").append(kind)
               .append("可能产生大量下载任务（占用流量和存储）。\n");
        }
        msg.append("\n确定继续吗？");
        dypDlg()
            .setTitle("确认解析" + kind)
            .setMessage(msg.toString())
            .setPositiveButton("继续解析", new android.content.DialogInterface.OnClickListener(){
                public void onClick(android.content.DialogInterface d, int w){
                    Log.i("DYP", "BIG_PARSE_CONFIRM go=" + kind + " autoDl=" + autoDl);
                    bigParsePending = false;
                    profileAutoConfirmed = true;   // 已确认，允许主页列表请求放行
                    mixFetchConfirmed = true;      // 合集同理
                    try{ onGo.run(); }catch(Throwable t){ Log.i("DYP", "BIG_PARSE_GO_ERR " + t); }
                }
            })
            .setNeutralButton(autoDl ? "关掉自动下载并继续" : "取消", autoDl
                ? new android.content.DialogInterface.OnClickListener(){
                    public void onClick(android.content.DialogInterface d, int w){
                        setAutoFlag(KEY_AUTO_DL, false);
                        Log.i("DYP", "BIG_PARSE_CONFIRM go=" + kind + " autoDl=已关闭");
                        showHintToast("已关闭自动下载");
                        bigParsePending = false;
                        profileAutoConfirmed = true;
                        mixFetchConfirmed = true;
                        try{ onGo.run(); }catch(Throwable t){}
                    }
                  }
                : new android.content.DialogInterface.OnClickListener(){
                    public void onClick(android.content.DialogInterface d, int w){
                        Log.i("DYP", "BIG_PARSE_CANCEL " + kind);
                        bigParsePending = false;
                        if(onCancel != null){ try{ onCancel.run(); }catch(Throwable t){} }
                    }
                  })
            .setNegativeButton(autoDl ? "取消" : null,
                autoDl ? new android.content.DialogInterface.OnClickListener(){
                    public void onClick(android.content.DialogInterface d, int w){
                        Log.i("DYP", "BIG_PARSE_CANCEL " + kind);
                        bigParsePending = false;
                        if(onCancel != null){ try{ onCancel.run(); }catch(Throwable t){} }
                    }
                  } : null)
            .setOnCancelListener(new android.content.DialogInterface.OnCancelListener(){
                public void onCancel(android.content.DialogInterface d){
                    Log.i("DYP", "BIG_PARSE_CANCEL_BACK " + kind);
                    bigParsePending = false;
                    if(onCancel != null){ try{ onCancel.run(); }catch(Throwable t){} }
                }
            })
            .show();
    }

    /** 启动批量解析。串行执行：解析完一条（或超时）再下一条，避免多实例互相覆盖。 */
    void startBatchParse(final java.util.List<String> urls){
        if(urls == null || urls.isEmpty()) return;
        if(batchRunning){
            showHintToast("批量解析进行中，请先等待完成");
            return;
        }
        final int n = urls.size();
        final boolean autoDl = autoDlOn();
        StringBuilder m = new StringBuilder();
        m.append("共识别到 ").append(n).append(" 条链接：\n\n")
         .append(previewList(urls, 6))
         .append("\n【静默解析】全部跑完才出结果，中途不会一条条闪过去。\n")
         .append("你可以切到别的页签或直接放着，跑完会自动跳到列表页。\n\n")
         .append("· 每条解析完先攒着，最后一次性列出\n")
         .append("· 结果会追加到现有列表，不会清掉上一次的批量结果\n");
        if(autoDl){
            m.append("\n⚠ 你开启了「解析后自动下载」\n")
             .append("这 ").append(n).append(" 条解析成功后会自动入下载队列，")
             .append("可能连续下载 ").append(n).append(" 个（或更多）文件，占用流量和存储。\n");
        }
        dypDlg()
            .setTitle("批量解析 " + n + " 条链接")
            .setMessage(m.toString())
            .setPositiveButton("开始批量解析", new android.content.DialogInterface.OnClickListener(){
                public void onClick(android.content.DialogInterface d, int w){
                    beginBatchParse(urls);
                }
            })
            .setNeutralButton(autoDl ? "关掉自动下载并继续" : "取消", autoDl
                ? new android.content.DialogInterface.OnClickListener(){
                    public void onClick(android.content.DialogInterface d, int w){
                        setAutoFlag(KEY_AUTO_DL, false);
                        showHintToast("已关闭自动下载");
                        Log.i("DYP", "BATCH_AUTODL_OFF 已关掉自动下载再批量");
                        beginBatchParse(urls);
                    }
                  }
                : null)
            .setNegativeButton(autoDl ? "取消" : null, null)
            .show();
    }

    /** 真正启动批量队列（确认之后调用） */
    void beginBatchParse(final java.util.List<String> urls){
        final int n = urls.size();
        synchronized(batchUrls){ batchUrls.clear(); batchUrls.addAll(urls); }
        batchIndex = 0;
        batchRunning = true;
        // 静默模式：不逐条渲染到解析页，跑完一次性出列表
        batchSilent = true;
        batchCollectMode = true;
        // 注意：不清空 batchCollected —— 新一批结果追加到上一次后面
        if(btnClearList != null) btnClearList.setVisibility(View.VISIBLE);
        Log.i("DYP", "BATCH_START n=" + n + " existing=" + batchCollected.size());
        switchTab(1);   // 切到列表页等结果，不占着解析页防闪烁
        runNextBatch();
    }

    /** 链接预览（最多 max 条，超出显示省略号） */
    String previewList(java.util.List<String> urls, int max){
        StringBuilder sb = new StringBuilder();
        for(int i = 0; i < urls.size() && i < max; i++){
            sb.append(i + 1).append(". ").append(urls.get(i)).append("\n");
        }
        if(urls.size() > max) sb.append("… 还有 ").append(urls.size() - max).append(" 条\n");
        return sb.toString();
    }

    /** 解析页「上一个 / 下一个」：在批量解析结果之间切换。
     *  切过去会重新解析该条（复现同样的渲染流程），方便逐条查看/下载后再决定继续。 */
    void gotoBatchItem(int idx){
        java.util.List<String> snap;
        synchronized(batchUrls){ snap = new java.util.ArrayList<String>(batchUrls); }
        if(snap.isEmpty()){
            showHintToast("没有批量解析记录");
            return;
        }
        if(idx < 0){ showHintToast("已经是第一个"); return; }
        if(idx >= snap.size()){ showHintToast("已经是最后一个"); return; }
        batchViewIndex = idx;
        String u = snap.get(idx);
        Log.i("DYP", "BATCH_NAV -> " + (idx + 1) + "/" + snap.size() + " " + u);
        s("查看第 " + (idx + 1) + "/" + snap.size() + " 条");
        input.setText(u);
        input.setSelection(u.length());
        doParseResolved(u);
        updateParseNav();
    }

    /** 刷新解析页上一条/下一条导航的显示状态 */
    void updateParseNav(){
        try{
            int total;
            synchronized(batchUrls){ total = batchUrls.size(); }
            if(parseNavRow == null) return;
            boolean show = total > 1;
            parseNavRow.setVisibility(show ? View.VISIBLE : View.GONE);
            if(!show) return;
            if(parseNavLabel != null) parseNavLabel.setText((batchViewIndex + 1) + "/" + total);
            if(btnParsePrev != null){
                btnParsePrev.setTextColor(batchViewIndex > 0
                        ? (curDark ? 0xFFF2F3F5 : 0xFF1A1A1F)
                        : (curDark ? 0xFF4A4F5A : 0xFFC0C4CC));
            }
            if(btnParseNext != null){
                btnParseNext.setTextColor(batchViewIndex < total - 1
                        ? (curDark ? 0xFFF2F3F5 : 0xFF1A1A1F)
                        : (curDark ? 0xFF4A4F5A : 0xFFC0C4CC));
            }
        }catch(Throwable t){}
    }

    /** 跑下一条。每条解析结束后由 onBatchItemDone() 或超时推进。 */
    void runNextBatch(){
        if(!batchRunning) return;
        String u;
        synchronized(batchUrls){
            if(batchIndex >= batchUrls.size()){
                batchRunning = false;
                batchSilent = false;      // 静默模式结束，后续单条解析恢复正常渲染
                final int done = batchIndex;
                Log.i("DYP", "BATCH_DONE total=" + done);
                h.post(new Runnable(){ public void run(){
                    // 把收集到的结果渲染到列表页，并自动切过去方便查看
                    renderBatchList();
                    saveBatchList();          // 落盘，退出重进可恢复
                    switchTab(1);
                    int got;
                    synchronized(batchCollected){ got = batchCollected.size(); }
                    showHintToast("批量解析完成：本次 " + done + " 条，列表共 " + got + " 条");
                    s("批量解析完成：列表共 " + got + " 条");
                }});
                return;
            }
            u = batchUrls.get(batchIndex);
        }
        batchIndex++;
        final String cur = u;
        batchViewIndex = batchIndex - 1;
        batchWaitDone = false;
        Log.i("DYP", "BATCH_ITEM " + batchIndex + "/" + batchUrls.size() + " url=" + cur);
        h.post(new Runnable(){ public void run(){
            try{
                s("批量解析 " + batchIndex + "/" + batchUrls.size() + " …");
                input.setText(cur);
                input.setSelection(cur.length());
                doParseResolved(cur);
                updateParseNav();
            }catch(Throwable t){
                Log.i("DYP", "BATCH_ITEM_ERR " + t);
            }
        }});
        // 两条推进方式：
        //  1) 正常：本条解析完成（maybeAutoAction → batchItemFinished）后立刻推进
        //  2) 兜底：等 60 秒还没完成（解析失败/页面卡住）才强行跳下一条
        if(batchAdvanceRunnable != null){
            try{ h.removeCallbacks(batchAdvanceRunnable); }catch(Throwable t){}
        }
        batchAdvanceRunnable = new Runnable(){ public void run(){
            if(!batchRunning) return;
            if(batchIndex < (batchUrls == null ? 0 : batchUrls.size())){
                Log.i("DYP", "BATCH_TIMEOUT_ADVANCE at " + batchIndex);
                showHintToast("第 " + batchIndex + " 条超时，跳到下一条");
                runNextBatch();
            }
        }};
        h.postDelayed(batchAdvanceRunnable, 60000);
    }

    /** 当前这条批量解析已完成（渲染 + 下载/播放已就绪），推进到下一条。
     *  由 maybeAutoAction() 在渲染完成时调用，避免固定等待导致「没看完就跳走」。 */
    void batchItemFinished(){
        if(!batchRunning || !batchCollectMode) return;
        if(batchWaitDone) return;      // 已经推进过
        batchWaitDone = true;
        if(batchAdvanceRunnable != null){
            try{ h.removeCallbacks(batchAdvanceRunnable); }catch(Throwable t){}
            batchAdvanceRunnable = null;
        }
        int total;
        synchronized(batchUrls){ total = batchUrls.size(); }
        final int curNo = batchIndex;
        Log.i("DYP", "BATCH_ITEM_DONE " + curNo + "/" + total);
        // 注意：最后一条也要走 runNextBatch()，那里的 batchIndex>=size 分支
        // 才会执行 BATCH_DONE + renderBatchList() 把结果渲染到列表页。
        // 之前这里 `if(curNo >= total) return;` 会让最后一条永远不渲染。
        h.postDelayed(new Runnable(){ public void run(){
            if(!batchRunning) return;
            runNextBatch();
        }}, 3000);
    }

    void parse(){
         final String raw=input.getText().toString().trim();
         if(raw.length()==0){ s("空链接"); return; }
         // 多链接批量解析：一行一个，或一段文本里含多条链接
         java.util.List<String> multi = extractUrls(raw);
         if(multi.size() > 1){
             startBatchParse(multi);
             return;
         }
         String url=extractUrl(raw);
         // B站纯号输入：av1234567 / BV1xx411c7mD 直接转成标准链接
         if(url==null || url.equals(raw)){
             if(BiliParser.isBareId(raw)){
                 String conv = BiliParser.bareIdToUrl(raw);
                 if(conv != null){
                     url = conv;
                     input.setText(url);
                     input.setSelection(url.length());
                     Log.i("DYP", "BILI_BARE_ID " + raw + " -> " + url);
                 }
             }
         }
         // 把粘贴的整段口令清理成干净链接
         if(url != null && !url.equals(raw)){
             input.setText(url);
             input.setSelection(url.length());
         }
         if(url==null){ s("未找到链接"); return; }
          // 主页 / 合集解析前置确认：这类解析会抓全量作品、并清空列表，先让用户确认
          if(needConfirmBigParse(url)){
              final String u0 = url;
              confirmBigParse(guessBigParseKind(u0), new Runnable(){ public void run(){
                  profileAutoConfirmed = true;   // 已经确认过，页面再跳主页就不再弹
                  bigParseAlready = true;        // 告诉 doParseResolved 不要重置确认标志
                  doParseResolved(u0);
              }}, new Runnable(){ public void run(){
                  profileAutoConfirmed = false;
                  s("已取消解析");
                  showHintToast("已取消解析");
              }});
              return;
          }
          profileAutoConfirmed = false;   // 单条解析：若中途页面跳主页，重新弹确认
         // b23.tv 短链：先跟 302 展开成真实链接（视频 / 合集 / 主页都能识别）
         // 注意：展开是网络请求，必须放后台线程，主线程会抛 NetworkOnMainThreadException
         if(url.toLowerCase().contains("b23.tv")){
             s("展开 B站短链…");
             final String shortUrl = url;
             new Thread(new Runnable(){ public void run(){
                 final String exp = cleanBiliUrl(expandB23(shortUrl));
                 h.post(new Runnable(){ public void run(){
                     if(exp != null && !exp.equals(shortUrl)){
                         input.setText(exp);
                         input.setSelection(exp.length());
                         Log.i("DYP", "B23_RESOLVED " + exp.substring(0, Math.min(140, exp.length())));
                     }
                     // 用展开后的链接继续走原来的解析流程
                     doParseResolved(exp != null ? exp : shortUrl);
                 }});
             }}).start();
             return;
         }
         doParseResolved(url);
    }

    /** 真正的解析流程（短链展开后调用，url 已是真实链接） */
    void doParseResolved(String url){
         if(url == null || url.length() == 0){ s("未找到链接"); return; }
webDone=false;
         storyBlocked=false;
         curRetry = 0;
         // 每次新解析都重置「大解析确认」标志：主页/合集中途触发时重新弹确认
         // （已在 parse() 里前置确认过的不会被重置：那时 bigParseAlready 为 true）
         if(!bigParseAlready){
             mixFetchConfirmed = false;
             profileAutoConfirmed = false;
         }
         bigParseAlready = false;
         // ===== 主页模式状态必须一起重置 =====
         // 否则连续解析两个主页时：profileMode 还是 true → PROFILE_AUTO 里的清空逻辑被跳过，
         // 旧列表不清、profileFetching 一直是 true 把新的列表请求挡掉 → 第二个主页只出一部分。
         profileMode = false;
         profileFetching = false;
         dyProfileDoneSecUid = "";
         dyProfileRenderLocked = false;
         dyProfileJumped = false;
         dyProfilePcUA = false;
         dyBlockedMobileShare = false;
         dyProfileSignedOwned = false;
         dyProfileMoreHintShown = false;
         dyAutoScrolling = false;
         profilePage = 0;
         profileLastRender = 0;
         synchronized(profileCollected){ profileCollected.clear(); }
         synchronized(profileSeen){ profileSeen.clear(); }
         try{ stopDyProfileScroll(); }catch(Throwable t){}
          // 新一轮解析：允许再次触发自动播放/下载
          autoFired = false;
        // 清空上一次的合集状态，避免误判
        synchronized(mixItems){ mixItems.clear(); }
        mixFetching = false;
        mixMode = false;
        // ===== 彻底重置上一条解析结果 =====
        // 递增渲染代次：让上一次还没回来的解析结果作废，不许改界面
        renderGen++;
        detailRendered = false;
        imgUrls.clear();
        dynUrls.clear();
        imgAllLive = false;
        imgIdx = 0;
        imgZoomed = false;
        curShowImg = "";
        curPlay = "";
        curMusic = "";
        curImgs = "";
        curId = "";
        curDesc = "";
        curCover = "";
        curWs = "";
        curBgm = "";
        curBgmTitle = "";
        curXVideos.clear(); curXVidDur.clear(); curXVidSize.clear();
        ttSrvId = "";
        ttSrvRaw = "";
        ttSrvVideoOk = false;
        ttSrvMusicOk = false;
        ttSrvImageOk = false;
        ttSrvNote = "";
        curSession = null;
        // 二次解析：先收起上一次的播放器/图片/操作栏，避免旧内容露出
        try{ if(miniPlayer != null){ miniPlayer.release(); miniPlayer = null; } }catch(Throwable t){}
        startedUrl = "";
        videoBox.setVisibility(View.GONE);
        img.setVisibility(View.GONE);
        actions.setVisibility(View.GONE);
        if(btnMusic != null) btnMusic.setVisibility(View.GONE);
        try{ ((TextView)findViewById(R.id.liveBadge)).setVisibility(View.GONE); }catch(Throwable t){}
        loadForParse(url);
    }

    int curRetry = 0;
    String curUrl = "";

    /** 当前 B 站解析结果（分P下载用） */
    BiliParser.BiliVideo curBili = null;

    /** B 站分P列表：进「列表」页签，点条目直接播/下载该 P */
/** 从 bili://play/{aid}/{cid} 取该集 aid，取不到就退回主 aid */
    static String biliEpAid(ProfileParser.ProfileItem it, BiliParser.BiliVideo v){
        try{
            if(it != null && it.url != null && it.url.startsWith("bili://play/")){
                String rest = it.url.substring("bili://play/".length());
                int k = rest.indexOf('/');
                if(k > 0){
                    String a = rest.substring(0, k);
                    if(a.length() > 0) return a;
                }
            }
        }catch(Throwable t){}
        return v != null ? v.aid : "";
    }

    void showBiliPages(final java.util.List<ProfileParser.ProfileItem> items){
        if(listWrap == null) return;
        listWrap.removeAllViews();
        final int n = items.size();
        final boolean isSeason = curBili != null && curBili.seasonId > 0;
        listHead.setText((isSeason ? "B站合集" : "B站分P") + " · 共 " + n
                + (isSeason ? " 集" : " 个分P") + " · 点「下载」下该集，或点右下「选择下载」批量选");
        btnDlAll.setVisibility(View.VISIBLE);
        btnDlAll.setText("选择下载");
        btnDlAll.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){
            final java.util.List<ProfileParser.ProfileItem> snap =
                    new java.util.ArrayList<ProfileParser.ProfileItem>(items);
            final String pre = (curBili != null && curBili.title.length() > 0)
                    ? curBili.title.replaceAll("[\\\\/:*?\"<>|\\s]+", "_")
                    : "B站";
            showListPicker(snap, pre, "视频", null, true);
        }});
        for(int i = 0; i < n; i++){
            final ProfileParser.ProfileItem it = items.get(i);
            final int idx = i;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(dp(12), dp(10), dp(12), dp(10));
            row.setBackground(makeRoundRect(curDark ? 0xFF24272F : 0xFFFFFFFF,
                    curDark ? 0xFF333842 : 0xFFE1E4E9, 12));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(8);
            row.setLayoutParams(lp);

            TextView title = new TextView(this);
            title.setText((i + 1) + ". " + it.title);
            title.setTextSize(13);
            title.setTextColor(curDark ? 0xFFE8EAF0 : 0xFF1A1C22);
            title.setPadding(0, 0, 0, dp(6));
            row.addView(title);

            LinearLayout btns = new LinearLayout(this);
            btns.setOrientation(LinearLayout.HORIZONTAL);
            TextView btnDl = new TextView(this);
            btnDl.setText("下载");
            btnDl.setTextSize(12);
            btnDl.setTextColor(0xFFFE2C55);
            btnDl.setPadding(dp(10), dp(5), dp(10), dp(5));
            btnDl.setBackground(makeRoundRect(0x1AFE2C55, 0, 8));
            btnDl.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){
                s("解析第 " + (idx + 1) + " 集直链…");
                new Thread(new Runnable(){ public void run(){
                    // 合集里每集 aid 不同，必须用该集自己的 aid
                    final String epAid = biliEpAid(it, curBili);
                    final String dl = BiliParser.getPlayUrl(MainActivity.this, epAid, it.id);
                    final String dA = BiliParser.lastAudioUrl;
                    final String dQ = BiliParser.lastQualityDesc;
                    h.post(new Runnable(){ public void run(){
                        if(dl != null && dl.length() > 0){
                            enqueueDownloadWithAudio(dl, "第" + (idx + 1) + "集", "mp4", "视频", "", dA);
                            s("已加入下载队列：第 " + (idx + 1) + " 集"
                                    + (dQ.length() > 0 ? "（" + dQ + "）" : ""));
                            showHintToast("已加入下载队列");
                        } else {
                            s("第 " + (idx + 1) + " 集解析失败");
                            showHintToast("直链获取失败");
                        }
                    }});
                }}).start();
            }});
            btns.addView(btnDl);

            TextView btnPlay = new TextView(this);
            btnPlay.setText("播放");
            btnPlay.setTextSize(12);
            btnPlay.setTextColor(curDark ? 0xFF8AB4F8 : 0xFF1A73E8);
            btnPlay.setPadding(dp(10), dp(5), dp(10), dp(5));
            LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            plp.leftMargin = dp(8);
            btnPlay.setLayoutParams(plp);
            btnPlay.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){
                s("解析第 " + (idx + 1) + " 集…");
                new Thread(new Runnable(){ public void run(){
                    final String epAid = biliEpAid(it, curBili);
                    final String dl = BiliParser.getPlayUrl(MainActivity.this, epAid, it.id);
                    final String pA = BiliParser.lastAudioUrl;
                    final String pQ = BiliParser.lastQualityDesc;
                    h.post(new Runnable(){ public void run(){
                        if(dl != null && dl.length() > 0){
                            curPlay = dl;
                            curAudio = pA != null ? pA : "";
                            curImgs = "";
                            switchTab(0);
                            actions.setVisibility(View.VISIBLE);
                            maybeAutoAction();   // 解析完成：按设置自动播放/下载
                            videoBox.setVisibility(View.VISIBLE);
                            img.setVisibility(View.GONE);
                            doPlay();
                            s("播放 第 " + (idx + 1) + " 集" + (pQ.length() > 0 ? "（" + pQ + "）" : ""));
                        } else {
                            showHintToast("直链获取失败");
                        }
                    }});
                }}).start();
            }});
            btns.addView(btnPlay);
            row.addView(btns);
            listWrap.addView(row);
        }
        s((isSeason ? "B站合集 " : "B站分P ") + n + (isSeason ? " 集" : " 个") + "已列到「列表」页");
    }
    void loadForParse(final String url){
        curUrl = url;
        // 新解析开始：复位各平台「已回退」标志，否则第二次解析不会触发回退
        ytSrvFallbackDone = false;
        xSrvFallbackDone = false;
        biliSrvFallbackDone = false;
        // 新解析开始时退出主页收集模式（防残留）
        if(profileMode && !isProfileUrl(url)) stopProfileMode();
        o("");
        // B 站：本地直连 API（免服务器）；space 主页走 profile 路径
        if(BiliParser.isBiliUrl(url) && extractBiliMid(url).length() == 0
                && !url.toLowerCase().contains("space.bilibili.com")){
            s("解析 B 站...");
            web.setVisibility(View.GONE);
            new Thread(new Runnable(){ public void run(){
                final BiliParser.BiliVideo v = BiliParser.resolve(MainActivity.this, url, 1);
                Log.i("DYP", "BILI_RESULT err=" + v.err + " ok=" + v.ok()
                        + " pages=" + v.pages.size());
                curBili = v;
                h.post(new Runnable(){ public void run(){
                    if(v.ok()){
                        webDone = true;
                        String r = render(BiliParser.toResultJson(v));
                        Log.i("DYP", "RESULT_BEGIN\n" + r + "\nRESULT_END");
                        o(r);
                        if(v.hasPages()){
                            boolean isSeason = v.seasonId > 0;
                            String unit = isSeason ? "集" : "个分P";
                            s("完成(B站) " + v.pages.size() + " " + unit + " · 点下载可选集");
                            // 分P / 合集列表进「列表」页签
                            java.util.List<ProfileParser.ProfileItem> items =
                                    new java.util.ArrayList<ProfileParser.ProfileItem>();
                            for(int i = 0; i < v.pages.size(); i++){
                                BiliParser.BiliVideo.Page p = v.pages.get(i);
                                ProfileParser.ProfileItem it = new ProfileParser.ProfileItem();
                                it.id = String.valueOf(p.cid);
                                // 合集用「标题 + 第几集」，分P用「P几 分P名」
                                it.title = isSeason
                                        ? (p.part.length() > 0 ? p.part : ("第" + p.page + "集"))
                                        : ("P" + p.page + " " + p.part);
                                it.durSec = p.durSec;
                                // 合集里每集是独立视频，必须用该集自己的 aid
                                String epAid = (p.aid != null && p.aid.length() > 0) ? p.aid : v.aid;
                                it.url = "bili://play/" + epAid + "/" + p.cid;
                                items.add(it);
                            }
                            showBiliPages(items);
                            // 自动切到「列表」页并提示，与抖音合集一致
                            switchTab(1);
                            s("B站" + (isSeason ? "合集" : "分P") + " " + v.pages.size()
                                    + " " + unit + "已列到「列表」页");
                            showHintToast((isSeason ? "B站合集 " : "B站分P ") + v.pages.size()
                                    + (isSeason ? " 集" : " 个") + "，已放进「列表」页");
                        } else {
                            s("完成(B站)");
                        }
                    } else {
                        s("B 站解析失败");
                        showHintToast(v.err.length() > 0 ? v.err : "B 站解析失败");
                    }
                }});
            }}).start();
            return;
        }
        // 主页列表：B 站空间（本地 wbi）/ YouTube @频道 / TikTok @用户 / X 用户名主页
        if(isProfileUrl(url)){
            final String biliMid = extractBiliMid(url);
            // Instagram 主页：优先本地 API（带 Cookie），失败回退 WebView 收集
            if(InsParser.isInsUrl(url)){
                s("解析 Instagram 主页...");
                web.setVisibility(View.GONE);
                new Thread(new Runnable(){ public void run(){
                    final java.util.List<ProfileParser.ProfileItem> list =
                            ProfileParser.resolveInsProfile(MainActivity.this, url);
                    final String err = ProfileParser.lastErr;
                    h.post(new Runnable(){ public void run(){
                        if(list != null && !list.isEmpty()){
                            showProfileList(list);
                            s("完成(IG 主页) " + list.size() + " 条");
                        } else {
                            // 回退 WebView 收集
                            Log.i("DYP", "INS_PROFILE_WEBVIEW_FALLBACK " + err);
                            s("IG 接口受限，改用网页收集…");
                            profileMode = true;
                            webDone = false;
                            synchronized(profileCollected){ profileCollected.clear(); } profilePage = 0;
                    // 主页/合集解析：先自动清空列表（批量模式下不清，否则会把自己收集的结果清掉）
                    if(!batchCollectMode){
                        synchronized(batchCollected){ batchCollected.clear(); }
                        // 同步删掉落盘文件，否则重启后旧批量结果又冒出来
                        try{ java.io.File f = batchListFile(); if(f != null && f.exists()) f.delete(); }catch(Throwable t){}
                    }
                            synchronized(profileSeen){ profileSeen.clear(); }
                            injectInsCookiesToWebView();
                            setupWebView();
                            web.setVisibility(View.VISIBLE);
                            web.loadUrl(url);
                            switchTab(1);
                        }
                    }});
                }}).start();
                return;
            }
            if(biliMid.length() > 0 && BiliParser.isBiliUrl(url)){
                // B 站空间主页：本地 wbi 签名直连
                s("解析 B 站主页...");
                web.setVisibility(View.GONE);
                new Thread(new Runnable(){ public void run(){
                    final java.util.List<java.util.Map<String, Object>> list =
                            BiliParser.resolveSpace(biliMid, 1, 30);
                    h.post(new Runnable(){ public void run(){
                        if(list != null && !list.isEmpty()){
                            webDone = true;
                            java.util.List<ProfileParser.ProfileItem> items =
                                    new java.util.ArrayList<ProfileParser.ProfileItem>();
                            for(java.util.Map<String, Object> it : list){
                                ProfileParser.ProfileItem pi = new ProfileParser.ProfileItem();
                                pi.id = String.valueOf(it.get("bvid"));
                                pi.title = String.valueOf(it.get("title"));
                                pi.url = "https://www.bilibili.com/video/" + pi.id;
                                items.add(pi);
                            }
                            showProfileList(items);
                        } else {
                            // wbi 直连被风控：回退 WebView 主页方案
                            Log.i("DYP", "BILI_SPACE_412 fallback webview");
                            startProfileWebView(url);
                        }
                    }});
                }}).start();
                return;
            }
            // 抖音/快手 主页：WebView 方案（页面自算签名/过风控）；其余走服务器
            String lower = url == null ? "" : url.toLowerCase();
            if(lower.contains("douyin.com") || lower.contains("kuaishou.com")){
                startProfileWebView(url);
                return;
            }
            s("解析主页列表...");
            web.setVisibility(View.GONE);
            new Thread(new Runnable(){ public void run(){
                final java.util.List<ProfileParser.ProfileItem> list = ProfileParser.resolveProfile(MainActivity.this, url);
                final String err = ProfileParser.lastErr;
                h.post(new Runnable(){ public void run(){
                    Log.i("DYP", "PROFILE_LIST size=" + (list != null ? list.size() : -1) + " err=" + err);
                    if(list != null && !list.isEmpty()){
                        webDone = true;
                        try{
                            showProfileList(list);
                            switchTab(1); // 切到列表页签
                        }catch(Throwable t){
                            Log.i("DYP", "SHOW_PROFILE_ERR " + t);
                            s("列表渲染失败");
                        }
                    } else {
                        s("主页解析失败：" + (err.length() > 0 ? err : "未知错误"));
                        showHintToast(err.length() > 0 ? err : "主页解析失败");
                    }
                }});
            }}).start();
            return;
        }
        // YouTube：走服务器解析（yt-dlp dump-json 拿 googlevideo 直链，不走下载缓存）
        if(YtParser.isYtUrl(url)){
            s("解析 YouTube...");
            web.setVisibility(View.GONE);
            new Thread(new Runnable(){ public void run(){
                final YtParser.YtVideo v = YtParser.resolve(MainActivity.this, url);
                Log.i("DYP", "YT_RESULT err=" + v.err + " ok=" + v.ok()
                        + " id=" + v.id + " items=" + v.items.size());
                h.post(new Runnable(){ public void run(){
                    if(v.isPlaylist()){
                        webDone = true;
                        showYtPlaylist(v);
                        return;
                    }
                    if(v.ok()){
                        webDone = true;
                        // 保存 direct 直链（下载优先走本机代理直连，比服务器中转快）
                        if(v.videoUrl != null && (v.videoUrl.contains("googlevideo") || v.videoUrl.contains("videoplayback"))){
                            ytDirectPlay = v.videoUrl;
                            ytDirectAudio = v.audioUrl != null ? v.audioUrl : "";
                        } else {
                            ytDirectPlay = "";
                            ytDirectAudio = "";
                        }
                        String r = render(v.toResultJson());
                        Log.i("DYP", "RESULT_BEGIN\n" + r + "\nRESULT_END");
                        o(r);
                        s("完成(YouTube)");
                        // 主页列表「下载」入口：解析成功后自动入下载队列
                        if(pendingAutoDlTag.length() > 0){
                            final String tag = pendingAutoDlTag;
                            pendingAutoDlTag = "";
                            pendingAutoDlIdx = -1;
                            // 下载走服务器流式中转（直链绑解析 IP，手机直连 403）
                            String srv = effSrvS(MainActivity.this);
                            final String dl = (srv.length() > 0 && v.id.length() > 0)
                                    ? srv + "/api/yt/stream?id=" + v.id + "&uid=" + ytUid(MainActivity.this)
                                    : "";
                            if(dl.length() > 0){
                                enqueueDownload(dl, tag, "mp4", "视频");
                                showHintToast("已加入下载队列：" + tag);
                            }
                        }
                        if(!v.subUsable){
                            showHintToast("服务器代理流量：" + (v.subReason.length() > 0
                                    ? v.subReason : "不可用"));
                        }
                    } else {
                        s("YouTube 解析失败");
                        showHintToast(v.err.length() > 0 ? v.err : "YouTube 解析失败");
                    }
                }});
            }}).start();
            return;
        }
        // Instagram 主页：交给 isProfileUrl 分支处理（本地带 Cookie 直连）
        if(InsParser.isInsUrl(url) && InsParser.extractCode(url).length() == 0){
            if(!ProfileParser.isProfileUrl(url)){
                s("Instagram 链接无法识别");
                showHintToast("支持单条图文/视频，或用户主页");
                return;
            }
        }
        // Instagram：本地带 Cookie 直连优先（走用户网络），失败再服务器兜底
        if(InsParser.isInsUrl(url) && InsParser.extractCode(url).length() > 0){
            final boolean srvOk = ProxyPolicy.builtinOn(this);
            s("解析 Instagram...");
            web.setVisibility(View.GONE);
            new Thread(new Runnable(){ public void run(){
                final InsParser.InsMedia v = InsParser.resolve(MainActivity.this, url, true);
                Log.i("DYP", "INS_RESULT err=" + v.err + " ok=" + v.ok()
                        + " code=" + v.code + " kind=" + v.kind + " local=" + v.local
                        + " video=" + v.isVideo + " srvOn=" + srvOk);
                h.post(new Runnable(){ public void run(){
                    if(v.ok()){
                        webDone = true;
                        String r = render(InsParser.toResultJson(v));
                        Log.i("DYP", "RESULT_BEGIN\n" + r + "\nRESULT_END");
                        o(r);
                        if(v.isVideo){
                            s("完成(IG 视频)" + (v.local ? " · 本地直连" : " · 服务器中转"));
                        } else {
                            s("完成(IG 图文)" + (v.local ? " · 本地直连" : ""));
                        }
                    } else {
                        s("Instagram 解析失败");
                        String msg = v.err.length() > 0 ? v.err : "IG 解析失败";
                        if(!CookieStore.insReady(MainActivity.this)){
                            msg = "IG 需要登录：设置 → Cookie → Instagram，填入 sessionid";
                        }
                        showHintToast(msg);
                    }
                }});
            }}).start();
            return;
        }
        // X（Twitter）：纯 HTTP，fxtwitter + syndication 双源，不需要 WebView
        if(XParser.isXUrl(url)){
            s("解析 X...");
            web.setVisibility(View.GONE);
            new Thread(new Runnable(){ public void run(){
                final XParser.XVideo v = XParser.resolve(MainActivity.this, url);
                Log.i("DYP", "X_RESULT err=" + v.err + " ok=" + v.ok()
                        + " id=" + v.id + " videos=" + v.videos.size() + " imgs=" + v.images.size());
                h.post(new Runnable(){ public void run(){
                    if(v.ok()){
                        webDone = true;
                        xSrvUsable = v.serverUsable;
                        xSrvReason = v.serverReason;
                        String r = render(v.toResultJson());
                        Log.i("DYP", "RESULT_BEGIN\n" + r + "\nRESULT_END");
                        o(r);
                        if(v.hasVideo()){
                            if(v.videos.size() > 1)
                                s("完成(X) " + v.videos.size() + " 个视频 · 可逐个下载");
                            else
                                s("完成(X)");
                        } else if(v.hasImages()){
                            s("完成(X) " + v.images.size() + " 张图片");
                        } else {
                            s("完成(X) 纯文字推");
                        }
                        // 机场流量提醒：服务器中转不可用时告知用户
                        if(!v.serverUsable){
                            showHintToast("服务器代理流量：" + (v.serverReason.length() > 0
                                    ? v.serverReason : "不可用") + "，下载请关闭内置代理走自己的网络");
                        }
                    } else if(!v.ok() && v.cardUrl.length() > 0){
                        // 纯卡片推（无媒体但有外链）：走服务器外链深挖
                        s("推文无媒体，正在解析外链卡片…");
                        new Thread(new Runnable(){ public void run(){
                            final String r = resolveCardLink(v.cardUrl);
                            h.post(new Runnable(){ public void run(){
                                if(r != null && r.length() > 0){
                                    webDone = true;
                                    String rj = render(r);
                                    Log.i("DYP", "RESULT_BEGIN\n" + rj + "\nRESULT_END");
                                    o(rj);
                                    s("完成(外链视频)");
                                } else {
                                    s("X 解析失败：推文无媒体，外链也没挖到视频");
                                }
                            }});
                        }}).start();
                    } else {
                        s("X 解析失败：" + v.err);
                        showHintToast(v.err.length() > 0 ? v.err : "X 解析失败");
                    }
                }});
            }}).start();
            return;
        }
        // 快手：纯 HTTP 就能拿全，不需要 WebView（分享页 SSR 里 INIT_STATE 是明文 JSON）
        if(KsParser.isKsUrl(url) && !isTikTokUrl(url)){
            s("解析快手...");
            web.setVisibility(View.GONE);
            new Thread(new Runnable(){ public void run(){
                final KsParser.KsVideo v = KsParser.resolve(MainActivity.this, url);
                Log.i("DYP", "KS_RESULT err=" + v.err + " ok=" + v.ok()
                        + " id=" + v.photoId + " imgs=" + v.images.size());
                h.post(new Runnable(){ public void run(){
                    if(v.ok()){
                        webDone = true;
                        String r = render(v.toResultJson());
                        Log.i("DYP", "RESULT_BEGIN\n" + r + "\nRESULT_END");
                        o(r);
                        if(v.isHeji){
                            // 合集分享：网页端只有被分享那一集，没有列表
                            s("完成(快手合集) 仅被分享的第 1 集");
                            showHintToast("快手合集只提供被分享那一集，其余集需逐集分享");
                        } else if(v.isKsStaticAtlas()){
                            // 快手网页端不下发动图轨道，作者标「实况」也一样
                            s("完成(快手) " + v.images.size() + " 张静态图 · 无动图轨道");
                            showHintToast("快手不下发动图轨道，只能存静态图");
                        } else {
                            s("完成(快手)");
                        }
                    } else {
                        // 直连失败再退回 WebView 兜底
                        Log.i("DYP", "KS_FALLBACK_WEBVIEW " + v.err);
                        // 主页短链：先试壳页列表解析（fw/user 页里带分享预览作品）
                        if(ProfileParser.isProfileUrl(url)
                                || (url != null && url.contains("kuaishou.com"))){
                            final List<Map<String, String>> plist =
                                    KsParser.resolveProfileList(MainActivity.this, url);
                            if(plist != null && !plist.isEmpty()){
                                final List<ProfileParser.ProfileItem> items =
                                        new ArrayList<ProfileParser.ProfileItem>();
                                for(Map<String, String> it : plist){
                                    ProfileParser.ProfileItem pi = new ProfileParser.ProfileItem();
                                    pi.id = it.get("id");
                                    pi.title = it.get("title");
                                    pi.url = it.get("url");
                                    pi.directUrl = it.get("mv");
                                    items.add(pi);
                                }
                                h.post(new Runnable(){ public void run(){
                                    webDone = true;
                                    showProfileList(items);
                                    s("主页解析完成（快手分享预览 " + items.size() + " 条）");
                                }});
                                return;
                            }
                            // 壳页也没列表：快手主页匿名拿不到完整作品列表（滑块验证码），
                            // 直接告知，不再进 WebView 死循环
                            Log.i("DYP", "KS_PROFILE_EMPTY");
                            h.post(new Runnable(){ public void run(){
                                webDone = true;
                                s("快手主页解析失败：快手对匿名访问主页要求滑块验证，"
                                        + "网页端只能拿到分享预览。\n"
                                        + "建议：在快手 App 里复制单条作品链接解析下载。");
                                showHintToast("快手主页需要登录或逐个分享作品");
                            }});
                            return;
                        }
                        s("快手直连失败，改用网页模式...");
                        setupWebView();
                        web.setVisibility(View.VISIBLE);
                        webDone = false;
                        // 主页短链：进主页收集模式
                        if(ProfileParser.isProfileUrl(url)){
                            profileMode = true;
                            synchronized(profileCollected){ profileCollected.clear(); } profilePage = 0;
                    // 主页/合集解析：先自动清空列表（批量模式下不清，否则会把自己收集的结果清掉）
                    if(!batchCollectMode){
                        synchronized(batchCollected){ batchCollected.clear(); }
                        // 同步删掉落盘文件，否则重启后旧批量结果又冒出来
                        try{ java.io.File f = batchListFile(); if(f != null && f.exists()) f.delete(); }catch(Throwable t){}
                    }
                            synchronized(profileSeen){ profileSeen.clear(); }
                            s("主页网页加载中，自动收集作品…");
                        }
                        web.loadUrl(KsParser.isShort(url) ? url : v.pageUrl);
                    }
                }});
            }}).start();
            return;
        }
        // TikTok：优先走服务器（机场出口，App 不用挂代理），失败回退本地 TtParser
        if(TtParser.isTtUrl(url)){
            s("解析 TikTok...");
            web.setVisibility(View.GONE);
            new Thread(new Runnable(){ public void run(){
                // ---- 第一层：服务器 ----
                String srvR = parseTikTokByServer(url);
                if(srvR != null){
                    Log.i("DYP", "RESULT_BEGIN\n" + srvR + "\nRESULT_END");
                    final String r = srvR;
                    h.post(new Runnable(){ public void run(){
                        o(r);
                        s("完成(TikTok·加速)");
                    }});
                    webDone = true;
                    return;
                }
                Log.i("DYP", "TT_SRV_MISS 回退本地");
                // 明确告知走了本地路径：服务器不可达时也能用，只是 TikTok
                // 的 CDN 需要代理才能直连（这是平台限制，不是服务器限制）。
                if(effSrv().length() == 0){
                    h.post(new Runnable(){ public void run(){ s("无服务器，走本地解析..."); }});
                }
                // ---- 第二层：本地直连（需代理） ----
                TtParser.Session sess = new TtParser.Session();
                final TtParser.TtVideo v = TtParser.resolve(MainActivity.this, url, sess);
                Log.i("DYP", "TT_RESULT tpl=" + v.template + " err=" + v.err
                        + " ok=" + v.ok() + " id=" + v.id + " imgs=" + v.images.size());
                if(v.ok()) curSession = sess;
                h.post(new Runnable(){ public void run(){
                    if(v.ok()){
                        webDone = true;
                        String r = buildTtResult(v);
                        Log.i("DYP", "RESULT_BEGIN\n" + r + "\nRESULT_END");
                        if(r != null){
                            o(r);
                            s("完成(TikTok·本地)");
                        } else {
                            s("组装结果失败");
                        }
                    } else {
                        // 纯 HTTP 失败，退回 WebView（页面里的 inject 也能读到 hydration JSON）
                        Log.i("DYP", "TT_FALLBACK_WEBVIEW " + v.err);
                        s("TikTok 直连失败，改用网页模式...");
                        setupWebView();
                        web.setVisibility(View.VISIBLE);
                        webDone = false;
                        web.loadUrl(v.pageUrl.length() > 0 ? v.pageUrl : url);
                    }
                }});
            }}).start();
            return;
        }
        s(curRetry == 0 ? "加载中..." : ("重试中(" + curRetry + "/2)..."));
        // 抖音路径需要真实浏览器算签名，这里才初始化 WebView
        setupWebView();
        webDone = false;
        web.setVisibility(View.VISIBLE);
        web.loadUrl(url);
        // 短链可能因网络抖动停在中转页，兜底：解析真实地址后直连
        boolean isTtShort = url.indexOf("tiktok.com/t/") >= 0
                         || url.indexOf("vt.tiktok.com") >= 0
                         || url.indexOf("vm.tiktok.com") >= 0;
        boolean isShort = url.indexOf("v.douyin.com") >= 0 || isTtShort;
        if(isTtShort){
            // TikTok 短链必须先换完整链接：短链跳转来的页面是精简版，没有 api-data
            new Thread(new Runnable(){ public void run(){
                try{
                    final String real = normalizeTikTokUrl(url);
                    if(real != null && real.indexOf("tiktok.com/@") >= 0 && real.indexOf("/video/") > 0){
                        Log.i("DYP", "TT_SHORT_TO_FULL " + real);
                        h.post(new Runnable(){ public void run(){
                            try{
                                if(webDone || mixMode) return;
                                // 直接把 WebView 换成完整链接，同时同步 curUrl 供重试使用
                                curUrl = real;
                                Log.i("DYP", "TT_LOAD_FULL " + real);
                                web.loadUrl(real);
                            }catch(Throwable t){}
                        }});
                    }
                }catch(Throwable t){ Log.i("DYP", "TT_SHORT_ERR " + t); }
            }}).start();
        } else if(isShort){
            new Thread(new Runnable(){ public void run(){
                try{
                    final String real = resolveShortUrl(MainActivity.this, url);
                    if(real != null && real.length() > 0 && real.indexOf("iesdouyin.com") >= 0){
                        Log.i("DYP", "SHORT_RESOLVED " + real.substring(0, Math.min(160, real.length())));
                        // 先试签名 detail 接口（实况动轨只在这里下发）
                        final String awemeId = DyDetail.extractAwemeId(real);
                        if(awemeId.length() > 0){
                            tryDyDetail(awemeId);
                        }
                        h.postDelayed(new Runnable(){ public void run(){
                            try{
                                if(webDone || mixMode) return;
                                String cur = web.getUrl();
                                if(cur != null && cur.indexOf("v.douyin.com") >= 0){
                                    Log.i("DYP", "SHORT_REDIRECT load real");
                                    web.loadUrl(real);
                                }
                            }catch(Throwable t){}
                        }}, 2500);
                    }
                }catch(Throwable t){ Log.i("DYP", "SHORT_ERR " + t); }
            }}).start();
        }
    }

    /** 从 iesdouyin 长链里提 aweme_id */
    static String awemeIdOf(String real){
        if(real == null) return "";
        java.util.regex.Matcher mm = java.util.regex.Pattern
            .compile("/(?:video|note|slides)/(\\d{6,25})")
            .matcher(real);
        return mm.find() ? mm.group(1) : "";
    }

    /** 把 detail 接口响应的 aweme_detail 转成 render 消息 JSON */
    String detailToResult(String body){
        try{
            Object root = new JP(body).parse();
            Object ad0 = findKey(root, "aweme_detail");
            Map ad = (ad0 instanceof Map) ? (Map)ad0 : (root instanceof Map ? (Map)root : null);
            if(ad == null) return null;
            StringBuilder b = new StringBuilder();
            b.append("{");
            putQ(b, "i_id", gs(get(ad, "aweme_id")));
            Map au = (Map)get(ad, "author");
            putQ(b, "i_author", au == null ? "" : gs(get(au, "nickname")));
            putQ(b, "i_desc", gs(get(ad, "desc")));
            putQ(b, "i_time", gs(get(ad, "create_time")));
            Map vd = (Map)get(ad, "video");
            if(vd != null){
                Map pa = (Map)get(vd, "play_addr");
                putQ(b, "i_play", pa == null ? "" : firstUrl(get(pa, "url_list")));
                putQ(b, "i_ws", pa == null ? "" : ("https://aweme.snssdk.com/aweme/v1/play/?video_id=" + gs(get(pa, "uri")) + "&ratio=1080p&line=0"));
                Object dur = get(vd, "duration");
                putQ(b, "i_dur", dur == null ? "" : String.valueOf(dur));
                Map cov = (Map)get(vd, "cover");
                putQ(b, "i_cover", cov == null ? "" : firstUrl(get(cov, "url_list")));
            }
            // 图集 + 实况动轨
            Object imgs = get(ad, "images");
            if(imgs == null){
                Object ipi = get(ad, "image_post_info");
                if(ipi instanceof Map) imgs = get((Map)ipi, "images");
            }
            StringBuilder imgS = new StringBuilder();
            StringBuilder dynS = new StringBuilder();
            boolean hasLive = false;
            if(imgs instanceof java.util.List && !((java.util.List)imgs).isEmpty()){
                int seq = 0;   // 图片序号，用来保证 imgS / dynS 索引严格对齐
                for(Object o : (java.util.List)imgs){
                    if(!(o instanceof Map)) continue;
                    Map im = (Map)o;
                    // 关键：imgS 与 dynS 必须按「第几张」一一对齐，
                    // 不能用 dynS.length()>0 判断——第 1 张是静态图时
                    // dynS 还是空的，会导致第 2 张的实况地址被塞到第 1 个位置。
                    if(seq > 0){ imgS.append('|'); dynS.append('|'); }
                    seq++;
                    imgS.append(firstUrl(get(im, "url_list")));
                    String lv = "";
                    Object v0 = get(im, "video");
                    if(v0 instanceof Map){
                        Map vm = (Map)v0;
                        lv = firstUrlOf(vm, "play_addr", "play_addr_h264", "download_addr");
                        if(lv.length() == 0) lv = firstUrlOf(vm, "play_addr_lowbr");
                    }
                    if(lv.length() == 0){
                        Object lp = get(im, "live_photo");
                        if(lp instanceof Map){
                            Object lpv = get((Map)lp, "video");
                            if(lpv instanceof Map) lv = firstUrlOf((Map)lpv, "play_addr", "download_addr");
                        }
                    }
                    if(lv.length() == 0){
                        Object mp = get(im, "motion_photo");
                        if(mp instanceof Map){
                            Object mpv = get((Map)mp, "video");
                            if(mpv instanceof Map) lv = firstUrlOf((Map)mpv, "play_addr", "download_addr");
                        }
                    }
                    if(lv.length() > 0){
                        lv = liveDirect(lv);
                        hasLive = true;
                    }
                    dynS.append(lv);
                }
                Log.i("DYP", "DETAIL_IMGS n=" + seq + " live=" + hasLive);
            }
            putQ(b, "i_imgs", imgS.toString());
            putQ(b, "i_dyn", dynS.toString());
            if(hasLive) putQ(b, "i_live", "1");
            Map st = (Map)get(ad, "statistics");
            if(st != null){
                putQ(b, "i_stats", "赞" + num(get(st, "digg_count"))
                 + " 评" + num(get(st, "comment_count"))
                 + " 藏" + num(get(st, "collect_count")));
            }
            // BGM：detail 的 music.play_url 是完整 MP3（背景音乐原曲），不是视频音轨
            Map mu = (Map)get(ad, "music");
            if(mu != null){
                String mT = gs(get(mu, "title"));
                String mU = "";
                Map pu = (Map)get(mu, "play_url");
                if(pu != null) mU = firstUrl(get(pu, "url_list"));
                if(mU.length() == 0) mU = gs(get(mu, "play_url_uri"));
                if(mU.length() > 0){
                    putQ(b, "i_bgm", mU);
                    putQ(b, "i_bgmTitle", mT);
                    putQ(b, "i_bgmAuthor", gs(get(mu, "author")));
                }
            }
            putQ(b, "i_platform", "douyin");
            b.append("\"i_ok\":1}");
            return b.toString();
        }catch(Throwable t){
            Log.i("DYP", "DETAIL2RESULT_ERR " + t);
            return null;
        }
    }

    /** 把 key/value 以 JSON 字符串形式 append（安全转义） */
    void putQ(StringBuilder b, String k, String v){
        if(b.length() > 1) b.append(",");
        b.append("\"").append(k).append("\":\"");
        if(v != null){
            for(int i = 0; i < v.length(); i++){
                char c = v.charAt(i);
                if(c == '\\') b.append("\\\\");
                else if(c == '"') b.append("\\\\\"");
                else if(c == '\n') b.append("\\\\n");
                else b.append(c);
            }
        }
        b.append("\"");
    }

    /** 从 Map 的一组候选 key 里取第一个非空 url_list[0] */
    String firstUrlOf(Map m, String... keys){
        for(String k : keys){
            Object o = m.get(k);
            if(o instanceof Map){
                String u = firstUrl(get((Map)o, "url_list"));
                if(u.length() > 0) return u;
            }
        }
        return "";
    }

    /** 尝试用签名 detail 接口解析抖音作品（图文/实况主路径）。
     *  自建服务器优先（用户零门槛 + UIFID 池），失败回退本地签名。 */
    void tryDyDetail(final String awemeId){
        // 没有凭据时，后台静默获取一次（不打扰用户），拿到后本次解析就能用
        if(!dyHasUifid() && !dyAutoRunning){
            s("首次解析：正在后台准备实况图凭据（约 5-10 秒）…");
            autoFetchDyUifid(new Runnable(){ public void run(){
                if(dyHasUifid()){
                    Log.i("DYP", "DY_AUTO_READY 本次解析可直接用本地签名");
                    s("凭据就绪，实况图可本地解析");
                } else {
                    Log.i("DYP", "DY_AUTO_MISS 本次仍走服务器/静态图");
                }
            }});
        }
        new Thread(new Runnable(){ public void run(){
            if(webDone || mixMode) return;
            DyDetail.Result r = null;
            // 调试开关：--ez localonly true 时强制走本地签名（验证免服务器路径）
            boolean localOnly = false;
            try{ localOnly = getIntent() != null && getIntent().getBooleanExtra("localonly", false); }catch(Throwable t){}
            // 优先本地签名（已有凭据时不走服务器，更快也更省流量）
            String localUifid = getSharedPreferences(SP, MODE_PRIVATE).getString(KEY_UIFID, "");
            boolean hasLocal = localUifid != null && localUifid.length() >= 32;
            if(hasLocal && !localOnly){
                String ck0 = "UIFID_TEMP=" + localUifid;
                String tw0 = DyDetail.registerTtwid();
                if(tw0.length() > 0) ck0 = ck0 + "; " + tw0;
                r = DyDetail.fetch(awemeId, ck0, 2);
                Log.i("DYP", "DYDETAIL local-first " + r);
                if(!r.ok) r = null;   // 本地失败再走服务器兜底
            }
            // ---- 路径 1：自建服务器中转 ----
            String srv = localOnly ? "" : getSharedPreferences(SP, MODE_PRIVATE).getString(KEY_DYP_SRV, DYP_SRV_DEFAULT);
            if(r == null && srv != null && srv.length() > 0){
                // 服务器 detail 偶发 403（UIFID 池瞬时失效），重试 2 次再放弃
                String js = null;
                for(int attempt = 0; attempt < 3; attempt++){
                    js = httpGetText(srv + "/api/detail?id=" + awemeId, 9000, 15000);
                    if(js != null && js.indexOf("\"aweme_detail\"") >= 0
                       && js.indexOf("\"aweme_detail\":null") < 0) break;
                    if(js != null && js.indexOf("story_25_filter") >= 0) break;
                    if(attempt < 2){
                        Log.i("DYP", "DYDETAIL srv retry " + (attempt + 1));
                        try{ Thread.sleep(700 + attempt * 500); }catch(Throwable t){}
                    }
                }
                // 服务器判定 story_25_filter（限时日常）：200 + filter 标记，弹专属提示且不回退
                if(js != null && js.indexOf("story_25_filter") >= 0 && js.indexOf("\"aweme_detail\"") < 0){
                    h.post(new Runnable(){ public void run(){
                        webDone = true;
                        storyBlocked = true;
                        if(web != null) web.setVisibility(View.GONE);
                        final StringBuilder sb = new StringBuilder();
                        sb.append("【限时日常】\n");
                        sb.append("这条作品是抖音「限时日常」（24 小时可见），");
                        sb.append("内容只在抖音 App 内展示，网页端无法解析。\n\n");
                        sb.append("请在抖音 App 里观看，或换一条普通视频/图文的分享链接。");
                        o(sb.toString());
                        s("限时日常：仅抖音 App 内可见");
                        appLog("解析", "✗ 限时日常(story) id=" + awemeId);
                    }});
                    return;
                }
                // aweme_detail 为 null（被过滤内容）不算成功，回退本地 SSR
                if(js != null && js.indexOf("\"aweme_detail\"") >= 0 && js.indexOf("\"aweme_detail\":null") < 0){
                    r = new DyDetail.Result();
                    r.ok = true;
                    r.body = js;
                    Log.i("DYP", "DYDETAIL srv ok len=" + js.length());
                } else {
                    Log.i("DYP", "DYDETAIL srv fail, fallback local");
                }
            }
            // ---- 路径 2：本地签名（用户自填 UIFID_TEMP） ----
            if(r == null){
                // 只传干净三件套：UIFID_TEMP + ttwid。
                // WebView cookie 里的 __ac_* / bd_ticket_* / fpk* 会触发
                // 403 Blocked by ArgusSecurityPlugin Validate Error
                String ck = "";
                String uifid = getSharedPreferences(SP, MODE_PRIVATE).getString(KEY_UIFID, "");
                if(uifid.length() >= 32) ck = "UIFID_TEMP=" + uifid;
                String tw = DyDetail.registerTtwid();
                if(tw.length() > 0) ck = ck.length() > 0 ? ck + "; " + tw : tw;
                r = DyDetail.fetch(awemeId, ck, 2);
                Log.i("DYP", "DYDETAIL local " + r);
            }
            if(r == null || !r.ok) return;
            // detail 返回的 aweme_detail 转成 render 消息
            final String msg = detailToResult(r.body);
            if(msg == null) return;
            h.post(new Runnable(){ public void run(){
                // 只放弃「合集模式」；webDone 不再拦 detail——
                // detail 的数据包含 SSR 没有的实况动轨，必须让它有机会覆盖 SSR 结果
                if(mixMode || storyBlocked) return;
                String out = render(msg);
                if(out != null && out.length() > 0 && out.indexOf("解析失败") < 0){
                    webDone = true;
                    detailRendered = true;      // 标记：后续 SSR 回包一律忽略
                    Log.i("DYP", "DETAIL_RENDERED ok");
                    Log.i("DYP", "RESULT_BEGIN\n" + out + "\nRESULT_END");
                    o(out);
                    s("完成(detail)");
                }
            }});
        }}).start();
    }

    /** 跟随 302 拿真实地址（静态版，需 Context 读代理） */
    static String resolveShortUrl(Context ctx, String url){
        try{
            java.net.HttpURLConnection c = staticOpenRetry(ctx, url);
            c.setInstanceFollowRedirects(false);
            c.setRequestProperty("User-Agent", MUA);
            c.setConnectTimeout(10000); c.setReadTimeout(10000);
            for(int i = 0; i < 6; i++){
                int code = c.getResponseCode();
                if(code >= 300 && code < 400){
                    String loc = c.getHeaderField("Location");
                    if(loc == null) return null;
                    if(loc.indexOf("iesdouyin.com") >= 0 || loc.indexOf("tiktok.com/@") >= 0
                       || loc.indexOf("/video/") >= 0 || loc.indexOf("/mix/") >= 0
                       || loc.indexOf("/playlet/") >= 0) return loc;
                    c = staticOpenRetry(ctx, loc);
                    c.setInstanceFollowRedirects(false);
                    c.setRequestProperty("User-Agent", MUA);
                    c.setConnectTimeout(10000); c.setReadTimeout(10000);
                } else {
                    return c.getURL().toString();
                }
            }
        }catch(Throwable t){ Log.i("DYP", "SHORT_RESOLVE_ERR " + t); }
        return null;
    }

    /** 静态版连接 + IPv4 回退 */
    static HttpURLConnection staticOpenRetry(Context ctx, String url) throws Exception{
        try{
            return openConn(ctx, url);
        }catch(java.net.UnknownHostException e){
            String ip = ipv4Host(new URL(url).getHost());
            if(ip == null) throw e;
            Log.i("DYP", "IPV4_FALLBACK_STATIC " + url.substring(0, Math.min(70, url.length())));
            URL u3 = new URL(forceIPv4(url));
            Proxy px = staticProxyFor(ctx, url);
            return (HttpURLConnection)(px != null ? u3.openConnection(px) : u3.openConnection());
        }
    }
    static String firstOf(String[] a){ for(String s:a){ if(s!=null && s.length()>0) return s; } return ""; }
    byte[] httpGetBytes(String url){
        return httpGetBytes(url, null);
    }

    /** 带下载进度的 GET。cb 为 null 时等同普通下载 */
    byte[] httpGetBytes(String url, ProgressCb cb){
        HttpURLConnection c = null;
        InputStream in = null;
        try{
            // TikTok 走专用分流：cookie 会话 + 直连/代理自动切换
            if(TtParser.isTtUrl(url) && curSession != null){
                c = TtParser.openDownload(MainActivity.this, url, curSession);
                if(c == null){
                    Log.i("DYP", "TT_DL_ALL_FAIL " + url.substring(0, Math.min(80, url.length())));
                    if(cb != null) cb.onError(new Exception("TikTok CDN 拒绝（代理/直连都不通）"));
                    return null;
                }
            } else {
                c = openConnRetry(url);
                c.setInstanceFollowRedirects(true);
                c.setRequestProperty("User-Agent","Mozilla/5.0 (Linux; Android 12; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36");
                // Referer 按站点自适应：抖音/TikTok/快手 CDN 都校验来源
                String ref = "https://www.douyin.com/";
                if(url.indexOf("tiktok") >= 0 || url.indexOf("tiktokcdn") >= 0) ref = "https://www.tiktok.com/";
                else if(KsParser.isKsUrl(url)) ref = "https://www.kuaishou.com/";
                c.setRequestProperty("Referer", ref);
                c.setConnectTimeout(15000); c.setReadTimeout(20000);
            }
            in=c.getInputStream();

            long total = -1;
            try{ total = c.getContentLength(); }catch(Throwable t){}
            if(total <= 0){
                String cr = c.getHeaderField("Content-Range");
                if(cr != null && cr.contains("/")){
                    try{ total = Long.parseLong(cr.substring(cr.lastIndexOf('/') + 1).trim()); }catch(Throwable t){}
                }
            }
            final long ftotal = total;
            if(cb != null) cb.onStart(ftotal);

            ByteArrayOutputStream bo;
            try{ bo = ftotal > 0 ? new ByteArrayOutputStream((int)Math.min(ftotal, 32L * 1024 * 1024)) : new ByteArrayOutputStream(); }
            catch(Throwable t){ bo = new ByteArrayOutputStream(); }

            byte[] buf=new byte[16384]; int n; long got = 0; long lastCb = 0;
            while((n=in.read(buf))>0){
                bo.write(buf,0,n);
                got += n;
                if(cb != null && (got - lastCb > 64 * 1024 || (ftotal > 0 && got >= ftotal))){
                    lastCb = got;
                    cb.onProgress(got, ftotal);
                }
                if(progCancel) break;
            }
            in.close();
            if(cb != null) cb.onProgress(got, ftotal > 0 ? ftotal : got);
            return bo.toByteArray();
        }catch(Exception e){
            Log.i("DYP","imgErr "+e);
            if(cb != null) cb.onError(e);
            return null;
        }finally{
            try{ if(in != null) in.close(); }catch(Throwable t){}
        }
    }

    /** 下载进度回调 */
    interface ProgressCb {
        void onStart(long totalBytes);
        void onProgress(long doneBytes, long totalBytes);
        void onError(Exception e);
    }
    /** 图集提示语：有实况就说清楚能存成实况照片；快手纯静态图集说明原因 */
    String imgTipText(){
        int n = imgUrls.size();
        int live = 0;
        for(int i = 0; i < dynUrls.size(); i++){ if(dynUrls.get(i).length() > 0) live++; }
        if(live > 0){
            if(live == n) return "共" + n + "张实况，左右滑动切图\n下载为 .jpg 实况照片，相册里长按可播放";
            return "共" + n + "张（" + live + "张实况），左右滑动切图，点图放大\n实况存为 .jpg 实况照片，相册里长按可播放";
        }
        // 快手：网页端不下发动图轨道，作者标「实况」也只给静态图
        if(n > 0 && KsParser.isKsUrl(curUrl) && !isTikTokUrl(curUrl))
            return "共" + n + "张静态图，左右滑动切图，点图放大\n快手不下发动图轨道，无法还原实况";
        return "共" + n + "张，左右滑动切图，点图放大";
    }

    /** 当前图是实况时，在图上角标提示「实况」 */
    void applyLiveBadge(int idx){
        try{
            if(liveBadge == null) return;
            boolean live = dynAt(idx).length() > 0;
            liveBadge.setVisibility(live ? View.VISIBLE : View.GONE);
            if(live) liveBadge.setText("LIVE");
        }catch(Throwable t){}
    }

    /** 深度探测：完整 cookie 值 + localStorage + window.uifid 相关 */

    /** 自测：用 WebView 的 cookie 走 a_bogus 签名的 detail 接口 */

    /** 自测：3 张图，第 1、3 张为实况，第 2 张静态 */

    /** 图片内存缓存：同一 URL 只下一次，避免并发重复请求把服务端连接写坏 */
    final java.util.HashMap<String, byte[]> imgMem = new java.util.HashMap<String, byte[]>();
    final java.util.HashSet<String> imgLoading = new java.util.HashSet<String>();

    void loadImage(final String url, final ImageView iv){
        if(url==null||url.length()==0) return;
        byte[] cached;
        synchronized(imgMem){ cached = imgMem.get(url); }
        if(cached != null){
            Bitmap bm0 = BitmapFactory.decodeByteArray(cached,0,cached.length);
            if(bm0 != null){ iv.setImageBitmap(bm0); return; }
        }
        synchronized(imgLoading){
            if(imgLoading.contains(url)) return;   // 同一张图正在下，别重复发
            imgLoading.add(url);
        }
        new Thread(new Runnable(){ public void run(){
            final byte[] d=httpGetBytes(url);
            synchronized(imgLoading){ imgLoading.remove(url); }
            if(d==null){ h.post(new Runnable(){ public void run(){ s("图片加载失败"); }}); return; }
            final Bitmap bm=BitmapFactory.decodeByteArray(d,0,d.length);
            if(bm != null){
                synchronized(imgMem){
                    if(imgMem.size() > 24) imgMem.clear();   // 简单容量上限
                    imgMem.put(url, d);
                }
            }
            // 成功时不改状态栏：图片本身已经显示了，再写一句会盖掉图集提示
            h.post(new Runnable(){ public void run(){
                if(bm!=null) iv.setImageBitmap(bm);
                else s("解码失败");
            }});
        }}).start();
    }
    static final String MUA="Mozilla/5.0 (Linux; Android 12; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36";
    volatile String startedUrl="";
    void doPlay(){
        if(curPlay==null||curPlay.length()==0){ s("\u65e0\u53ef\u64ad\u653e\u5730\u5740"); return; }
        videoBox.setVisibility(View.VISIBLE);
        // 图集渲染时 TextureView 被 GONE，这里必须恢复 VISIBLE，否则 surface 永远建不起来
        video.setVisibility(View.VISIBLE);
        img.setVisibility(View.GONE);
        if(curPlay.equals(startedUrl)){ s("\u64ad\u653e\u4e2d..."); return; }
        startedUrl = curPlay;
        try{
            // 二次解析后 TextureView 经历 GONE→VISIBLE：旧 Surface 已随播放器 release 失效。
            // 关键：SurfaceTexture 需要视图 attach + layout 后才可用，推迟到 UI 队列执行。
            if(videoSurface != null){
                try{ videoSurface.release(); }catch(Throwable t){}
                videoSurface = null;
            }
            h.post(new Runnable(){ public void run(){
                try{
                    // 等 videoBox 从 GONE→VISIBLE 完成一帧 layout，TextureView 才有尺寸
                    videoBox.post(new Runnable(){ public void run(){
                        try{ doPlayInner(); }catch(Throwable t){ Log.i("DYP", "MINI_INIT_ERR " + t); }
                    }});
                }catch(Throwable t){ Log.i("DYP", "MINI_POST_ERR " + t); }
            }});
        }catch(Throwable t){ Log.i("DYP", "MINI_INIT_ERR " + t); }
    }

    void doPlayInner(){
        if(curPlay==null||curPlay.length()==0) return;
        try{
            // 代理/Referer 可能随设置变化，URL 也换站，重建播放器
boolean ttUrl = isTikTokUrl(curPlay);
            boolean ksUrl = KsParser.isKsUrl(curPlay);
            boolean xUrl = isXDomain(curPlay) || isXDomain(curUrl);
            boolean ytPlayUrl = YtParser.isYtUrl(curPlay) || YtParser.isYtUrl(curUrl)
                    || (curPlay != null && curPlay.contains("googlevideo.com"))
                    || (curAudio != null && curAudio.contains("googlevideo.com"));
            boolean biliUrl = BiliParser.isBiliUrl(curPlay) || BiliParser.isBiliUrl(curUrl)
                    || (curPlay != null && curPlay.contains("bilivideo.com"));
            // 当前播放器用的 DataSource.Factory（双轨合并时复用）
            final androidx.media3.datasource.DataSource.Factory[] dsf = new androidx.media3.datasource.DataSource.Factory[1];
            if(miniPlayer == null || miniProxyKey == null || miniProxyKey != proxyCacheKey(curPlay) || miniWasTT != ttUrl){
                if(miniPlayer != null){ try{ miniPlayer.release(); }catch(Throwable t){} miniPlayer = null; }
                String ref = ttUrl ? "https://www.tiktok.com/"
                    : (ksUrl ? "https://www.kuaishou.com/"
                    : (xUrl ? "https://x.com/"
                    : (biliUrl ? "https://www.bilibili.com/"
                    : (ytPlayUrl ? "https://www.youtube.com/" : "https://www.douyin.com/"))));
                androidx.media3.datasource.DefaultHttpDataSource.Factory http =
                    new androidx.media3.datasource.DefaultHttpDataSource.Factory()
                        .setUserAgent(MUA)
                        .setDefaultRequestProperties(new HashMap<String,String>(){{ put("Referer", ref); }})
                        .setAllowCrossProtocolRedirects(true);
                // 代理：本地文件不套；服务器中转强制直连；其余按设置
                final boolean isLocalFile = curPlay != null && curPlay.startsWith("file://");
                final Proxy pxPlay = isLocalFile ? null
                    : ((curPlay != null && (curPlay.indexOf("/api/x/dl") >= 0
                        || curPlay.indexOf("/api/yt/file") >= 0
                        || curPlay.indexOf("/api/yt/stream") >= 0
                        || curPlay.indexOf("/api/tt/dl") >= 0))
                    ? null : curProxyFor(curPlay));
                Log.i("DYP", "MINI_DS tt=" + ttUrl + " proxy=" + (pxPlay != null) + " local=" + isLocalFile);
                androidx.media3.datasource.DataSource.Factory f;
                if(isLocalFile){
                    // 本地文件：DefaultDataSource 自动选 FileDataSource
                    f = new androidx.media3.datasource.DefaultDataSource.Factory(this, http);
                } else {
                    f = http;
                }
                if(pxPlay != null){
                    final java.net.Proxy pxFinal = pxPlay;
                    final String refFinal = ref;
                    final String ckFinal = ttUrl && curSession != null ? curSession.cookieHeader() : null;
                    Log.i("DYP", "MINI_DS_COOKIE len=" + (ckFinal == null ? -1 : ckFinal.length()));
                    f = new androidx.media3.datasource.DataSource.Factory(){
                        public androidx.media3.datasource.DataSource createDataSource(){
                            return new ProxyHttpDataSource(pxFinal, MUA, refFinal, ckFinal);
                        }
                    };
                } else if(ttUrl && curSession != null){
                    final String ckFinal = curSession.cookieHeader();
                    Log.i("DYP", "MINI_DS_COOKIE(direct) len=" + ckFinal.length());
                    final String refFinal = ref;
                    f = new androidx.media3.datasource.DataSource.Factory(){
                        public androidx.media3.datasource.DataSource createDataSource(){
                            return new ProxyHttpDataSource(java.net.Proxy.NO_PROXY, MUA, refFinal, ckFinal);
                        }
                    };
                }
                // B 站 CDN：必须桌面 UA（手机 UA 一律 403）+ Referer + buvid3
                if(biliUrl && pxPlay == null){
                    final java.net.Proxy pxB = java.net.Proxy.NO_PROXY;
                    final String refB = "https://www.bilibili.com/";
                    final String ckB = BiliParser.biliCookie();
                    Log.i("DYP", "MINI_BILI_CK len=" + ckB.length());
                    f = new androidx.media3.datasource.DataSource.Factory(){
                        public androidx.media3.datasource.DataSource createDataSource(){
                            return new ProxyHttpDataSource(pxB, BiliParser.UA_DESKTOP, refB, ckB);
                        }
                    };
                }
                dsf[0] = f;
                miniPlayer = new androidx.media3.exoplayer.ExoPlayer.Builder(this)
                    .setMediaSourceFactory(new androidx.media3.exoplayer.source.DefaultMediaSourceFactory(f))
                    .build();
                miniWasTT = ttUrl;
                miniProxyKey = proxyCacheKey(curPlay);
                miniPlayer.addListener(new androidx.media3.common.Player.Listener(){
                    @Override public void onPlaybackStateChanged(int state){
                        if(state == androidx.media3.common.Player.STATE_READY){
                            miniPrepared = true;
                            long d = miniPlayer.getDuration();
                            if(d > 0) miniTimeAll.setText(fmt(d));
                            miniPlay.setText("\u23f8");
                            Log.i("DYP", "MINI_READY dur=" + d);
                         } else if(state == androidx.media3.common.Player.STATE_BUFFERING){
                              // 缓冲中：X/YouTube 走服务器中转，回源大视频可能要 1-2 分钟
                              String tip = "缓冲中";
                              if(isXDomain(curUrl)) tip = "缓冲中（服务器回源，稍等）";
                              else if(YtParser.isYtUrl(curUrl)){
                                  tip = "缓冲中（服务器解析回源，稍等）";
                              }
                              if(status != null) status.setText(tip);
                         } else if(state == androidx.media3.common.Player.STATE_ENDED){
                            miniPlay.setText("\u25b6");
                        }
                    }
                    @Override public void onVideoSizeChanged(androidx.media3.common.VideoSize vs){
                        miniVideoW = vs.width; miniVideoH = vs.height;
                        Log.i("DYP", "MINI_VIDEO " + miniVideoW + "x" + miniVideoH);
                        applyMiniScale();
                    }
                    @Override public void onPlayerError(androidx.media3.common.PlaybackException e){
                        Log.i("DYP", "MINI_ERR " + e.getErrorCodeName());
                        // YouTube direct 直连失败（403 绑 IP）：自动回退服务器流式中转
                        if(YtParser.isYtUrl(curUrl) && !ytSrvFallbackDone
                                && (curPlay == null || curPlay.contains("googlevideo.com") || curPlay.contains("videoplayback"))){
                            ytSrvFallbackDone = true;
                            final String fbUrl = curUrl;
                            final String fbId = curId.length() > 0 ? curId
                                    : (curUrl != null ? curUrl : "");
                            h.post(new Runnable(){ public void run(){
                                s("直连被 CDN 拒绝，正在回退服务器中转…");
                            }});
                            new Thread(new Runnable(){ public void run(){
                                // 重新请求服务器（不带 direct=1）拿 STREAM:// 中转地址
                                final YtParser.YtVideo v = YtParser.resolveServer(MainActivity.this, fbUrl, false);
                                h.post(new Runnable(){ public void run(){
                                    if(v.ok() && v.videoUrl.length() > 0){
                                        Log.i("DYP", "YT_SRV_FALLBACK url=" + v.videoUrl.substring(0, Math.min(60, v.videoUrl.length())));
                                        curPlay = v.videoUrl;
                                        curAudio = "";
                                        miniProxyKey = null;
                                        try{ if(miniPlayer != null){ miniPlayer.release(); miniPlayer = null; } }catch(Throwable t){}
                                        doPlay();
                                    } else {
                                        s("YouTube 播放失败：" + (v.err.length() > 0 ? v.err : "服务器中转不可用"));
                                    }
                                }});
                            }}).start();
                            return;
                        }
                        // B 站直连 CDN 失败：自动回退服务器中转（buvid3/UA 变化或网络异常时）
                        if(BiliParser.isBiliUrl(curUrl) && !biliSrvFallbackDone
                                && (curPlay != null && curPlay.contains("bilivideo.com"))){
                            biliSrvFallbackDone = true;
                            final String fbCdn = curPlay;
                            final String fbKey = curId;
                            h.post(new Runnable(){ public void run(){
                                s("B站直连失败，正在回退服务器中转…");
                            }});
                            new Thread(new Runnable(){ public void run(){
                                final String srvUrl = BiliParser.biliProxyUrl(MainActivity.this, fbCdn, fbKey, "");
                                h.post(new Runnable(){ public void run(){
                                    if(srvUrl != null && srvUrl.length() > 0 && !srvUrl.equals(fbCdn)){
                                        Log.i("DYP", "BILI_SRV_FALLBACK " + srvUrl.substring(0, Math.min(70, srvUrl.length())));
                                        curPlay = srvUrl;
                                        miniProxyKey = null;
                                        try{ if(miniPlayer != null){ miniPlayer.release(); miniPlayer = null; } }catch(Throwable t){}
                                        doPlay();
                                    } else {
                                        s("B站播放失败：服务器中转不可用");
                                    }
                                }});
                            }}).start();
                            return;
                        }
                        // 403 时直接给出可操作提示
                        if(e.getErrorCodeName() != null && e.getErrorCodeName().contains("BAD_HTTP_STATUS")){
                            final String msg = "视频被 CDN 拒绝（403）\n"
                                + "当前代理 IP 拿不到视频流。\n"
                                + "这不是解析失败，是 TikTok 的 CDN 只放行住宅 IP。\n"
                                + "换住宅节点，或点「关于 → 网络自检」看具体原因。";
                            h.post(new Runnable(){ public void run(){ s(msg); }});
                        }
                        // X 直连播放失败：直连连不上 video.twimg.com → 自动回退服务器中转
                        if((isXDomain(curUrl) || (curPlay != null && curPlay.contains("twimg.com")))
                                && !xSrvFallbackDone){
                            xSrvFallbackDone = true;
                            final String fbUrl = curUrl;
                            h.post(new Runnable(){ public void run(){
                                s("X 直连播放失败，正在回退服务器中转…");
                            }});
                            new Thread(new Runnable(){ public void run(){
                                final XParser.XVideo v = XParser.resolveServerOnly(MainActivity.this, fbUrl);
                                h.post(new Runnable(){ public void run(){
                                    if(v.ok() && v.hasVideo()){
                                        Log.i("DYP", "X_SRV_FALLBACK render");
                                        curPlay = v.firstVideo();
                                        // 服务器中转地址：不走用户代理（走代理可能同样不通）
                                        miniProxyKey = null;
                                        try{ if(miniPlayer != null){ miniPlayer.release(); miniPlayer = null; } }catch(Throwable t){}
                                        doPlay();
                                    } else {
                                        s("X 播放失败：你的代理节点连不上 X 视频 CDN。\n"
                                          + "解决：设置 → 代理 换个境外节点；\n"
                                          + "或关闭代理走服务器中转。");
                                    }
                                }});
                            }}).start();
                        }
                    }
                });
            }
            if(videoSurface == null || !videoSurface.isValid()){
                // 二次解析后 TextureView 被 GONE→VISIBLE，surface 可能失效，从当前 SurfaceTexture 重建
                try{
                    android.graphics.SurfaceTexture st = video.getSurfaceTexture();
                    Log.i("DYP", "MINI_SURFACE_CHK st=" + (st != null) + " tvW=" + video.getWidth() + " tvH=" + video.getHeight()
                            + " vis=" + (video.getVisibility() == View.VISIBLE));
                    if(st != null){
                        if(videoSurface != null){ try{ videoSurface.release(); }catch(Throwable t){} }
                        videoSurface = new android.view.Surface(st);
                        Log.i("DYP", "MINI_SURFACE_REBUILT valid=" + videoSurface.isValid());
                    } else {
                        // SurfaceTexture 还没创建（layout 未完成）：等 listener 补绑
                        Log.i("DYP", "MINI_SURFACE_WAIT_LISTENER");
                    }
                }catch(Throwable t){ Log.i("DYP", "MINI_SURFACE_ERR " + t); }
            }
            if(videoSurface != null){
                miniPlayer.setVideoSurface(videoSurface);
                Log.i("DYP", "MINI_SET_SURFACE valid=" + videoSurface.isValid());
            } else {
                Log.i("DYP", "MINI_NO_SURFACE");
            }
            if(curAudio != null && curAudio.length() > 0 && curAudio.startsWith("http")){
                // YouTube direct：视频轨 + 音频轨双 URL，用 MergingMediaSource 合并播放
                Log.i("DYP", "MINI_MERGE audio=" + curAudio.substring(0, Math.min(60, curAudio.length())));
                String base = curPlay;
                androidx.media3.datasource.DataSource.Factory baseF = dsf[0];
                if(baseF == null){
                    // 播放器复用时没有重建 DataSource.Factory，用默认 HTTP（不带代理）
                    // 这种情况只发生在同一代理配置下连播两条双轨视频，兜底即可
                    baseF = new androidx.media3.datasource.DefaultHttpDataSource.Factory()
                        .setUserAgent(MUA)
                        .setAllowCrossProtocolRedirects(true);
                }
                androidx.media3.exoplayer.source.MediaSource.Factory mf =
                        new androidx.media3.exoplayer.source.DefaultMediaSourceFactory(baseF);
                // 音频轨复用同一 DataSource.Factory（同样走代理 + youtube Referer）
                java.util.List<androidx.media3.common.MediaItem> items = new java.util.ArrayList<androidx.media3.common.MediaItem>();
                items.add(androidx.media3.common.MediaItem.fromUri(Uri.parse(base)));
                items.add(androidx.media3.common.MediaItem.fromUri(Uri.parse(curAudio)));
                androidx.media3.exoplayer.source.MergingMediaSource mms =
                        new androidx.media3.exoplayer.source.MergingMediaSource(
                                mf.createMediaSource(items.get(0)),
                                mf.createMediaSource(items.get(1)));
                miniPlayer.setMediaSource(mms);
            } else {
                miniPlayer.setMediaItem(androidx.media3.common.MediaItem.fromUri(Uri.parse(curPlay)));
            }
            miniPlayer.prepare();
            miniPlayer.setPlayWhenReady(true);
            s("\u64ad\u653e\u4e2d...");
            startMiniProgress();
        }catch(Throwable t){ Log.i("DYP", "MINI_INIT_ERR " + t); }
    }

    /** 小窗初始化：TextureView + 手势 + 控制条 */
    void setupMiniPlayer(){
        video.setSurfaceTextureListener(new android.view.TextureView.SurfaceTextureListener(){
            public void onSurfaceTextureAvailable(android.graphics.SurfaceTexture st, int w, int h){
                videoSurface = new android.view.Surface(st);
                if(miniPlayer != null) miniPlayer.setVideoSurface(videoSurface);
            }
            public void onSurfaceTextureSizeChanged(android.graphics.SurfaceTexture st, int w, int h){ applyMiniScale(); }
            public boolean onSurfaceTextureDestroyed(android.graphics.SurfaceTexture st){
                // 返回 false：销毁 SurfaceTexture，下次 VISIBLE 重新 onSurfaceTextureAvailable。
                // 二次解析后播放器重建，旧 Surface 已随 ExoPlayer release 失效，
                // 若返回 true 系统会保留已失效的 SurfaceTexture → 黑屏只有控件。
                try{ if(videoSurface != null){ videoSurface.release(); } }catch(Throwable t){}
                videoSurface = null;
                return false;
            }
            public void onSurfaceTextureUpdated(android.graphics.SurfaceTexture st){}
        });

        // 控制条按钮
        miniPlay.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ miniTogglePlay(); }});
        miniFull.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ openPlayer(); }});
        miniSpeed.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ showMiniSpeedDialog(); }});

        miniSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar sb, int pr, boolean fromUser){
                if(fromUser && miniPlayer != null){
                    long d = miniPlayer.getDuration();
                    if(d > 0) miniTimeNow.setText(fmt(d * pr / 1000));
                }
            }
            public void onStartTrackingTouch(SeekBar sb){ miniSeeking = true; }
            public void onStopTrackingTouch(SeekBar sb){
                miniSeeking = false;
                if(miniPlayer != null){
                    long d = miniPlayer.getDuration();
                    if(d > 0) miniPlayer.seekTo(d * sb.getProgress() / 1000);
                }
            }
        });

        // 手势：双击暂停 / 长按左回退 / 长按右快进
        // 双击检测：自己计时判断，不依赖 GestureDetector 的 onDoubleTap
        final android.view.GestureDetector gd = new android.view.GestureDetector(this, new android.view.GestureDetector.SimpleOnGestureListener(){
            public boolean onDown(android.view.MotionEvent e){ return true; }
            public boolean onSingleTapConfirmed(android.view.MotionEvent e){ return true; }
            public boolean onDoubleTap(android.view.MotionEvent e){ miniTogglePlay(); return true; }
        });
        // 左右滑动：YouTube 播放列表切上一集/下一集；X 多视频切上一个/下一个
        final android.view.GestureDetector gdFling = new android.view.GestureDetector(this,
            new android.view.GestureDetector.SimpleOnGestureListener(){
            public boolean onDown(android.view.MotionEvent e){ return false; }
            public boolean onFling(android.view.MotionEvent a, android.view.MotionEvent b, float vx, float vy){
                if(a == null || b == null) return false;
                float dx = b.getX() - a.getX(), dy = b.getY() - a.getY();
                if(Math.abs(dx) < 120 || Math.abs(dx) < Math.abs(dy)) return false;
                if(dx < 0){
                    // 左滑 → 下一集/下一个
                    if(!ytPlaylist.isEmpty()){ ytNext(); return true; }
                    if(curXVideos.size() > 1){
                        // X 多视频：切换到下一个视频
                        playXVideo(1);
                        return true;
                    }
                } else {
                    // 右滑 → 上一集/上一个
                    if(!ytPlaylist.isEmpty()){ ytPrev(); return true; }
                    if(curXVideos.size() > 1){
                        playXVideo(-1);
                        return true;
                    }
                }
                return false;
            }
        });

        video.setOnTouchListener(new View.OnTouchListener(){
            public boolean onTouch(View v, android.view.MotionEvent ev){
                gd.onTouchEvent(ev);
                gdFling.onTouchEvent(ev);
                switch(ev.getActionMasked()){
                    case android.view.MotionEvent.ACTION_DOWN:
                        miniDownX = ev.getX(); miniDownY = ev.getY();
                        miniZoomedLongPress = false;
                        h.postDelayed(miniLpStart, 400);
                        break;
                    case android.view.MotionEvent.ACTION_UP:
                    case android.view.MotionEvent.ACTION_CANCEL:
                        h.removeCallbacks(miniLpStart);
                        if(miniZoomedLongPress) miniStopLongPress();
                        break;
                }
                return true;
            }
        });
    }

    /** X 多视频：切换播放第 N 个（相对偏移） */
    int curXVideoIdx = 0;
    void playXVideo(int delta){
        if(curXVideos.isEmpty()) return;
        int ni = curXVideoIdx + delta;
        if(ni < 0) ni = 0;
        if(ni >= curXVideos.size()) ni = curXVideos.size() - 1;
        if(ni == curXVideoIdx){
            showHintToast(delta > 0 ? "已经是最后一个视频" : "已经是第一个视频");
            return;
        }
        curXVideoIdx = ni;
        curPlay = curXVideos.get(ni);
        try{
            if(miniPlayer != null){ miniPlayer.release(); miniPlayer = null; }
        }catch(Throwable t){}
        startedUrl = "";
        doPlay();
        s("X 视频 " + (ni + 1) + "/" + curXVideos.size());
    }

    // 小窗播放器生命周期：切到后台自动暂停
    @Override protected void onPause(){
        super.onPause();
        try{
            if(miniPlayer != null && miniPlayer.isPlaying()){
                miniPlayer.pause();
                if(miniPlay != null) miniPlay.setText("\u25b6");
                Log.i("DYP", "MINI_AUTO_PAUSE onPause");
            }
        }catch(Throwable t){}
        try{ if(web != null) web.onPause(); }catch(Throwable t){}
    }

    @Override protected void onResume(){
        super.onResume();
        try{ if(web != null) web.onResume(); }catch(Throwable t){}
        // 内置能力：回到前台时读一次剪贴板，识别到分享口令就自动填链接
        // （只在输入框为空时填，不覆盖用户正在输入的内容）
        try{ h.postDelayed(new Runnable(){ public void run(){ autoFillFromClipboard(); }}, 350); }catch(Throwable t){}
        // 列表内如果有内容，回到前台时如果 listWrap 是空的（被系统回收）就补渲染
        try{ h.postDelayed(new Runnable(){ public void run(){ restoreListIfEmpty(); }}, 500); }catch(Throwable t){}
        // 启动时主动申请存储权限：Android 7 及以下系统只弹一次，
        // 等到用户点下载才申请容易错过（拒了就不再弹），所以进来自动要一次
        try{
            h.postDelayed(new Runnable(){ public void run(){
                try{
                    if(!AppInfo.hasStoragePerm(MainActivity.this)
                            && !AppInfo.storagePermBlocked(MainActivity.this)
                            && !permAskedThisLaunch){
                        permAskedThisLaunch = true;
                        Log.i("DYP", "PERM_AUTO_REQUEST API=" + android.os.Build.VERSION.SDK_INT);
                        ensureStoragePerm();
                    }
                }catch(Throwable t){}
            }}, 1200);
        }catch(Throwable t){}
        // 从「安装未知应用」设置页返回且 APK 已就绪 → 自动继续安装
        if(pendingInstallApk){
            pendingInstallApk = false;
            boolean can = false;
            try{ can = getPackageManager().canRequestPackageInstalls(); }catch(Throwable t){}
            if(can){
                h.postDelayed(new Runnable(){ public void run(){
                    s("权限已授予，继续安装...");
                    installApk();
                }}, 400);
            }
        }
    }

    /** 权限引导页返回后要自动重试安装 */
    boolean pendingInstallApk = false;

    @Override protected void onDestroy(){
        super.onDestroy();
        // 退出前把批量解析列表落盘
        try{ saveBatchList(); }catch(Throwable t){}
        try{ if(miniPlayer != null){ miniPlayer.release(); miniPlayer = null; } }catch(Throwable t){}
        try{ if(videoSurface != null){ videoSurface.release(); videoSurface = null; } }catch(Throwable t){}
    }

    Runnable miniLpStart = new Runnable(){ public void run(){
        if(!miniPrepared || miniPlayer == null) return;
        miniZoomedLongPress = true;
        boolean right = miniDownX > video.getWidth() / 2f;
        if(right){
            miniApplySpeed(2.0f);
            s("\u21bb 2X \u5feb\u653e\u4e2d");
        } else {
            miniStartRewind();
        }
    }};

    Handler miniSeekHandler = new Handler(Looper.getMainLooper());
    Runnable miniRewind;

    void miniStartRewind(){
        miniSeekHandler.removeCallbacksAndMessages(null);
        s("\u21ba \u56de\u9000\u4e2d");
        miniRewind = new Runnable(){ public void run(){
            if(!miniZoomedLongPress || miniPlayer == null) return;
            try{
                long cur = miniPlayer.getCurrentPosition();
                long target = cur - 400;
                if(target < 0){ target = 0; miniZoomedLongPress = false; return; }
                miniPlayer.seekTo(target);
                s("\u21ba " + fmt(target) + " / " + fmt(miniPlayer.getDuration()));
            }catch(Throwable t){}
            miniSeekHandler.postDelayed(this, 100);
        }};
        miniSeekHandler.post(miniRewind);
    }

    void miniStopLongPress(){
        miniZoomedLongPress = false;
        miniSeekHandler.removeCallbacksAndMessages(null);
        h.removeCallbacks(miniLpStart);
        if(miniPlayer != null) miniApplySpeed(MINI_SPEEDS[miniSpeedIdx]);
        s("\u6062\u590d " + MINI_SPEED_LABELS[miniSpeedIdx]);
    }

    // Activity 层双击检测：视频区域内双击切换播放/暂停
    // （miniBar 会吃掉下半部分事件，所以提到 Activity 层）
    float evDownX = 0, evDownY = 0;
    long evDownTime = 0;

    @Override public boolean dispatchTouchEvent(android.view.MotionEvent ev){
        try{
            if(videoBox != null && videoBox.getVisibility() == View.VISIBLE && miniPlayer != null){
                int[] loc = new int[2];
                videoBox.getLocationOnScreen(loc);
                int lx = loc[0], ly = loc[1];
                int rx = lx + videoBox.getWidth(), ry = ly + videoBox.getHeight();
                boolean inVideo = ev.getRawX() >= lx && ev.getRawX() <= rx
                               && ev.getRawY() >= ly && ev.getRawY() <= ry;
                if(inVideo){
                    int act = ev.getActionMasked();
                    if(act == android.view.MotionEvent.ACTION_DOWN){
                        long now = System.currentTimeMillis();
                        if(now - evDownTime < 300
                           && Math.abs(ev.getRawX() - evDownX) < 100
                           && Math.abs(ev.getRawY() - evDownY) < 100){
                            evDownTime = 0;
                            miniTogglePlay();
                            return true;   // 吃掉事件，不传给子控件
                        }
                        evDownTime = now;
                        evDownX = ev.getRawX(); evDownY = ev.getRawY();
                    }
                }
            }
        }catch(Throwable t){}
        return super.dispatchTouchEvent(ev);
    }

    void miniTogglePlay(){
        if(miniPlayer == null) return;
        if(miniPlayer.isPlaying()){ miniPlayer.pause(); miniPlay.setText("\u25b6"); s("\u23f8 \u5df2\u6682\u505c"); }
        else { miniPlayer.play(); miniPlay.setText("\u23f8"); s("\u25b6 \u64ad\u653e\u4e2d"); }
    }

    void miniApplySpeed(float sp){
        try{
            if(miniPlayer != null){
                miniPlayer.setPlaybackParameters(new androidx.media3.common.PlaybackParameters(sp, 1.0f));
                Log.i("DYP", "MINI_SPEED " + sp);
            }
        }catch(Throwable t){}
    }

    void showMiniSpeedDialog(){
        final String[] items = {"0.5X", "1.0X", "1.25X", "1.5X", "2.0X"};
        dypDlg()
            .setTitle("\u64ad\u653e\u901f\u5ea6")
            .setSingleChoiceItems(items, miniSpeedIdx, new android.content.DialogInterface.OnClickListener(){
                public void onClick(android.content.DialogInterface d, int which){
                    miniSpeedIdx = which;
                    miniApplySpeed(MINI_SPEEDS[which]);
                    miniSpeed.setText(MINI_SPEED_LABELS[which]);
                    d.dismiss();
                }
            })
            .setNegativeButton("\u53d6\u6d88", null)
            .show();
    }

    void startMiniProgress(){
        h.post(new Runnable(){ public void run(){
            if(miniPlayer != null && miniPrepared){
                try{
                    long d = miniPlayer.getDuration();
                    long c = miniPlayer.getCurrentPosition();
                    miniTimeNow.setText(fmt(c));
                    if(d > 0){
                        miniTimeAll.setText(fmt(d));
                        if(!miniSeeking) miniSeek.setProgress((int)(c * 1000 / d));
                    }
                }catch(Throwable t){}
            }
            h.postDelayed(this, 300);
        }});
    }

    static String fmt(long ms){
        if(ms < 0) ms = 0;
        long s2 = ms / 1000;
        return String.format("%02d:%02d", s2 / 60, s2 % 60);
    }

    /** 小窗按视频比例居中（TextureView 矩阵） */
    void applyMiniScale(){
        try{
            if(video == null || miniVideoW <= 0 || miniVideoH <= 0) return;
            int vw = video.getWidth(), vh = video.getHeight();
            if(vw <= 0 || vh <= 0) return;
            float sc = Math.min((float) vw / miniVideoW, (float) vh / miniVideoH);
            float dispW = miniVideoW * sc, dispH = miniVideoH * sc;
            float dx = (vw - dispW) / 2f, dy = (vh - dispH) / 2f;
            // 注意：不能用中心为支点缩放后再平移，那样会平移两次。
            // 正确做法：以左上角为原点缩放，再平移到居中位置。
            android.graphics.Matrix m = new android.graphics.Matrix();
            m.setScale(dispW / vw, dispH / vh);
            m.postTranslate(dx, dy);
            video.setTransform(m);
            // 让容器高度跟视频比例，避免黑边
            int boxH = (int)(vh);
            android.view.ViewGroup.LayoutParams lp = video.getLayoutParams();
            if(lp.height != boxH){ lp.height = boxH; video.setLayoutParams(lp); }
            Log.i("DYP", "MINI_SCALE " + vw + "x" + vh + " -> " + (int)dispW + "x" + (int)dispH);
        }catch(Throwable t){}
    }

    void parseImgs(){
        imgUrls.clear();
        dynUrls.clear();
        if(curImgs==null||curImgs.length()==0) return;
        String imgsPart = curImgs;
        String dynPart = "";
        int dy = curImgs.indexOf("|DYN|");
        if(dy >= 0){ imgsPart = curImgs.substring(0, dy); dynPart = curImgs.substring(dy + 5); }
        String[] a = imgsPart.split("[|]");
        String[] d = dynPart.length() > 0 ? dynPart.split("[|]") : new String[0];
        for(int i=0;i<a.length;i++){
            String u=a[i].trim();
            if(u.length()==0) continue;
            if(u.equals("DYN")) continue;
            if(u.indexOf("http")!=0) continue;
            imgUrls.add(u);
            // 实况视频按索引对齐；长度不足或不是 http 就补空
            String dv = (i < d.length) ? d[i].trim() : "";
            if(dv.indexOf("http") != 0) dv = "";
            dynUrls.add(dv);
        }
        imgAllLive = false;
        if(imgUrls.size() > 0){
            boolean all = true;
            for(int i=0;i<dynUrls.size();i++){ if(dynUrls.get(i).length() == 0){ all = false; break; } }
            imgAllLive = all;
        }
    }

    /** 当前第 i 张图对应的实况视频，没有则返回空串 */
    String dynAt(int i){
        if(i < 0 || i >= dynUrls.size()) return "";
        return dynUrls.get(i);
    }

    /** 把 aweme.snssdk 这类带签名参数的实况地址转成可直接下载的直链 */
    static String liveDirect(String u){
        if(u == null || u.length() == 0) return "";
        if(u.indexOf("/aweme/v1/playwm/") > 0) return u.replace("/aweme/v1/playwm/", "/aweme/v1/play/");
        return u;
    }
    void showImgAt(int i){
        if(imgUrls.size()==0) return;
        if(i<0) i=0;
        if(i>=imgUrls.size()) i=imgUrls.size()-1;
        if(i==imgIdx && curShowImg.equals(imgUrls.get(i))) return;
        imgIdx=i;
        curShowImg=imgUrls.get(i);
        loadImage(curShowImg, img);
        applyLiveBadge(i);
        s("第"+(imgIdx+1)+"/"+imgUrls.size()+"张" + (imgZoomed ? "  已放大，可左右切图" : "  点击放大"));
    }
    void openPlayer(){
        if(curPlay==null || curPlay.length()==0){ s("\u65e0\u53ef\u64ad\u653e\u5730\u5740"); return; }
        try{
            Intent it=new Intent(MainActivity.this, PlayerActivity.class);
            it.putExtra("url", curPlay);
            it.putExtra("ws", curWs);
            it.putExtra("id", curId);
            String d = curDesc == null ? "" : curDesc;
            it.putExtra("desc", d);
            it.putExtra("ref", KsParser.isKsUrl(curPlay) ? "https://www.kuaishou.com/" : null);
            startActivity(it);
        }catch(Exception e){ Log.i("DYP","openPlayerErr "+e); s("\u64ad\u653e\u5668\u542f\u52a8\u5931\u8d25"); }
    }
    void exitFull(){}
    void doFullscreen(){ openPlayer(); }
    @Override public void onBackPressed(){ super.onBackPressed(); }
    void showImageFull(final String url){
        if(url==null||url.length()==0){ s("无图片"); return; }
        s("加载大图...");
        new Thread(new Runnable(){ public void run(){
            final byte[] d=httpGetBytes(url);
            final Bitmap bm=(d==null)?null:BitmapFactory.decodeByteArray(d,0,d.length);
            h.post(new Runnable(){ public void run(){
                if(bm==null){ s("大图加载失败"); return; }
                final Dialog dlg=new Dialog(MainActivity.this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
                // 大图容器：支持左右滑动切图 + 点一下关闭
                android.widget.FrameLayout box = new android.widget.FrameLayout(MainActivity.this);
                ImageView big=new ImageView(MainActivity.this);
                big.setScaleType(ImageView.ScaleType.FIT_CENTER);
                big.setImageBitmap(bm);
                box.addView(big, new android.widget.FrameLayout.LayoutParams(
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT));
                // 大图里滑左右直接切上一张/下一张，点一下关闭
                final int total = imgUrls.size();
                final int[] cur = new int[]{ imgIdx };
                final android.view.GestureDetector gd = new android.view.GestureDetector(MainActivity.this,
                    new android.view.GestureDetector.SimpleOnGestureListener(){
                    public boolean onDown(android.view.MotionEvent e){ return true; }
                    public boolean onSingleTapUp(android.view.MotionEvent e){ dlg.dismiss(); return true; }
                    public boolean onFling(android.view.MotionEvent a, android.view.MotionEvent b, float vx, float vy){
                        if(a==null||b==null) return false;
                        float dx=b.getX()-a.getX(), dy=b.getY()-a.getY();
                        if(Math.abs(dx)>80 && Math.abs(dx)>Math.abs(dy)){
                            int ni = dx<0 ? cur[0]+1 : cur[0]-1;
                            if(ni<0) ni=0;
                            if(ni>=total) ni=total-1;
                            if(ni!=cur[0]){
                                cur[0]=ni;
                                showImageFull(imgUrls.get(ni));
                                dlg.dismiss();
                            }
                            return true;
                        }
                        return false;
                    }
                });
                big.setOnTouchListener(new View.OnTouchListener(){ public boolean onTouch(View v, android.view.MotionEvent e){
                    return gd.onTouchEvent(e);
                }});
                dlg.setContentView(box);
                dlg.show();
                s(total > 1
                    ? ("大图 " + (cur[0]+1) + "/" + total + "，左右滑动切图，点图关闭")
                    : "大图已显示，点图/返回关闭");
            }});
        }}).start();
    }
    /**
     * X 多视频选择器：一条推带 2~4 个视频时，让用户选下载第几个。
     * 文件名格式：x_{tweetId}_视频N_{时间戳}.mp4
     */
    void showXVideoPicker(){
        final int n = curXVideos.size();
        if(n == 0){ s("无视频"); return; }
        final boolean[] sel = new boolean[n];
        sel[0] = true;

        String[] labels = new String[n];
        for(int i = 0; i < n; i++){
            StringBuilder lb = new StringBuilder();
            lb.append("第 ").append(i + 1).append(" 个");
            String sz = i < curXVidSize.size() ? curXVidSize.get(i) : "";
            String du = i < curXVidDur.size() ? curXVidDur.get(i) : "";
            StringBuilder meta = new StringBuilder();
            if(sz.length() > 0) meta.append(sz);
            if(du.length() > 0){
                if(meta.length() > 0) meta.append("  ");
                try{
                    double d = Double.parseDouble(du);
                    meta.append(String.format(java.util.Locale.US, "%.1fs", d));
                }catch(Throwable t){ meta.append(du).append("s"); }
            }
            if(meta.length() > 0) lb.append("　").append(meta);
            if(i == 0) lb.append("　（默认）");
            labels[i] = lb.toString();
        }

        dypDlg()
            .setTitle("选择要下载的视频（共 " + n + " 个）")
            .setMultiChoiceItems(labels, sel,
                new android.content.DialogInterface.OnMultiChoiceClickListener(){
                    public void onClick(android.content.DialogInterface d, int which, boolean isChecked){
                        sel[which] = isChecked;
                    }
                })
            .setPositiveButton("下载所选", new android.content.DialogInterface.OnClickListener(){
                public void onClick(android.content.DialogInterface d, int w){
                    int cnt = 0;
                    for(int i = 0; i < n; i++){
                        if(!sel[i]) continue;
                        downloadXVideo(i);
                        cnt++;
                    }
                    if(cnt == 0){ s("未选择任何视频"); return; }
                    if(!dlRunning){ dlRunning = true; runDownloadQueue(); }
                    s("已加入下载：" + cnt + " 个视频");
                    Log.i("DYP", "XV_QUEUE n=" + cnt + " total=" + n);
                }
            })
            .setNeutralButton("全选下载", new android.content.DialogInterface.OnClickListener(){
                public void onClick(android.content.DialogInterface d, int w){
                    for(int i = 0; i < n; i++) downloadXVideo(i);
                    if(!dlRunning){ dlRunning = true; runDownloadQueue(); }
                    s("已加入下载：" + n + " 个视频");
                    Log.i("DYP", "XV_QUEUE all n=" + n);
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }

    /** 把第 idx 个 X 视频加入下载队列 */
    void downloadXVideo(int idx){
        if(idx < 0 || idx >= curXVideos.size()) return;
        String u = curXVideos.get(idx);
        String tag = "视频" + (idx + 1);
        enqueueDownload(u, tag, "mp4", "视频");
    }

    /**
     * YouTube 播放列表：卡片列表 + 点击解析单集。
     * 解析后支持左右滑动切换上/下一集（同一播放列表内）。
     */
    java.util.List<YtParser.YtVideo.Item> ytPlaylist = new java.util.ArrayList<YtParser.YtVideo.Item>();
    int ytIdx = 0;

    void showYtPlaylist(final YtParser.YtVideo v){
        ytPlaylist.clear();
        ytPlaylist.addAll(v.items);
        ytIdx = 0;
        s("YouTube 播放列表 · " + ytPlaylist.size() + " 集 · 点标题解析单集");
        StringBuilder sb = new StringBuilder();
        sb.append("▶ 点这里解析第一集（列表太长时先点我）\n");
        sb.append("┌── YouTube 播放列表 ──────────────\n");
        for(int i = 0; i < ytPlaylist.size(); i++){
            YtParser.YtVideo.Item it = ytPlaylist.get(i);
            String t = it.title.length() > 40 ? it.title.substring(0, 40) + "…" : it.title;
            sb.append(String.format(java.util.Locale.US, "【%d】%s  %ds\n",
                    i + 1, t, it.durSec));
        }
        sb.append("└── 左右滑动播放器可切换上一集/下一集");
        // 直接解析第 1 集（列表顶部提示 + 立即执行，避免按钮在屏幕外点不到）
        o(sb.toString());
        h.postDelayed(new Runnable(){ public void run(){
            if(ytPlaylist.isEmpty()){
                Log.i("DYP", "YT_AUTO_PARSE_FIRST skip: playlist empty");
                return;
            }
            Log.i("DYP", "YT_AUTO_PARSE_FIRST n=" + ytPlaylist.size());
            parseYtItem(0);
        }}, 1500);
    }

    /** 解析播放列表里的第 idx 集（0 基） */
    void parseYtItem(final int idx){
        if(idx < 0 || idx >= ytPlaylist.size()){ s("没有这一集"); return; }
        ytIdx = idx;
        final YtParser.YtVideo.Item it = ytPlaylist.get(idx);
        s("解析第 " + (idx + 1) + " 集...");
        new Thread(new Runnable(){ public void run(){
            final YtParser.YtVideo v = YtParser.resolve(MainActivity.this,
                    "https://www.youtube.com/watch?v=" + it.id);
            h.post(new Runnable(){ public void run(){
                if(v.ok()){
                    webDone = true;
                    String r = render(v.toResultJson());
                    Log.i("DYP", "RESULT_BEGIN\n" + r + "\nRESULT_END");
                    o(r);
                    s("完成(YouTube) 第 " + (idx + 1) + "/" + ytPlaylist.size() + " 集 · 左右滑动切换");
                } else {
                    s("第 " + (idx + 1) + " 集解析失败");
                    showHintToast(v.err.length() > 0 ? v.err : "解析失败");
                }
            }});
        }}).start();
    }

    /** 是否主页链接（转发给 ProfileParser 判断） */
    boolean isProfileUrl(String url){
        if(ProfileParser.isProfileUrl(url)) return true;
        // B 站空间主页：space.bilibili.com/{mid} 或 m.bilibili.com/space/{mid}
        if(url != null){
            java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("bilibili\\.com/(?:space/)?(\\d{5,12})").matcher(url);
            if(m.find()) return true;
        }
        return false;
    }

    /** B 站空间主页 mid 提取（排除 av 号/BV 号） */
    static String extractBiliMid(String url){
        if(url == null) return "";
        // 兼容 www / m / space 三种：bilibili.com/85420121、bilibili.com/space/85420121、m.bilibili.com/space/85420121
        java.util.regex.Matcher m = java.util.regex.Pattern
            .compile("bilibili\\.com/(?:space/)?(\\d{5,12})(?![0-9])").matcher(url);
        if(m.find()){
            String mid = m.group(1);
            // av 号排除
            int i = url.lastIndexOf("av" + mid);
            if(i >= 0) return "";
            return mid;
        }
        return "";
    }

    /** 主页作品列表：渲染到「列表」页签，每条可解析/下载（分页，每页 100 条） */
    static final int PROFILE_PAGE_SIZE = 100;
    boolean dyProfileJumped = false;   // 抖音主页跳 PC 版只做一次
    int profilePage = 0;

    /** 主页批量下载：勾选要下载第几个（多选）。
     *  默认列出当前页条目，可「全选本页」；勾选后按序号入队。 */
    void showProfilePickDialog(final java.util.List<ProfileParser.ProfileItem> list,
                               final int from, final int to){
        final int cnt = Math.max(0, to - from);
        if(cnt == 0){ showHintToast("本页没有可下载条目"); return; }
        final boolean[] checked = new boolean[cnt];
        final String[] labels = new String[cnt];
        for(int i = from; i < to; i++){
            ProfileParser.ProfileItem it = list.get(i);
            String title = it.title != null && it.title.length() > 0 ? it.title : (it.id == null ? "" : it.id);
            if(title.length() > 42) title = title.substring(0, 42) + "…";
            labels[i - from] = "第 " + (i + 1) + " 个 · " + title;
        }
        dypDlg()
            .setTitle("选择要下载的作品")
            .setMultiChoiceItems(labels, checked, new android.content.DialogInterface.OnMultiChoiceClickListener(){
                public void onClick(android.content.DialogInterface d, int which, boolean isChecked){
                    checked[which] = isChecked;
                }
            })
            .setPositiveButton("开始下载", new android.content.DialogInterface.OnClickListener(){
                public void onClick(android.content.DialogInterface d, int w){
                    int added = 0;
                    for(int k = 0; k < checked.length; k++){
                        if(!checked[k]) continue;
                        int i = from + k;
                        ProfileParser.ProfileItem it = list.get(i);
                        String u = it.url != null && it.url.length() > 0 ? it.url
                                : (it.id != null && it.id.matches("\\d{15,20}")
                                    ? "https://www.tiktok.com/@/video/" + it.id : "");
                        if(u.length() == 0) continue;
                        enqueueDownload(u, "视频" + (i + 1), "mp4", "视频");
                        added++;
                    }
                    if(added == 0){ showHintToast("没有勾选任何作品"); return; }
                    Log.i("DYP", "PROFILE_PICK_DL added=" + added);
                    s("已加入下载队列：" + added + " 条");
                    showHintToast("已加入 " + added + " 条下载任务");
                    switchTab(0);
                }
            })
            .setNeutralButton("全选本页", new android.content.DialogInterface.OnClickListener(){
                public void onClick(android.content.DialogInterface d, int w){
                    for(int k = 0; k < cnt; k++) checked[k] = true;
                    dypDlg()
                        .setTitle("已全选本页 " + cnt + " 条")
                        .setMultiChoiceItems(labels, checked, new android.content.DialogInterface.OnMultiChoiceClickListener(){
                            public void onClick(android.content.DialogInterface dd, int which, boolean isChecked){
                                checked[which] = isChecked;
                            }
                        })
                        .setPositiveButton("开始下载", new android.content.DialogInterface.OnClickListener(){
                            public void onClick(android.content.DialogInterface dd, int ww){
                                int added = 0;
                                for(int k = 0; k < checked.length; k++){
                                    if(!checked[k]) continue;
                                    int i = from + k;
                                    ProfileParser.ProfileItem it = list.get(i);
                                    String u = it.url != null && it.url.length() > 0 ? it.url
                                            : (it.id != null && it.id.matches("\\d{15,20}")
                                                ? "https://www.tiktok.com/@/video/" + it.id : "");
                                    if(u.length() == 0) continue;
                                    enqueueDownload(u, "视频" + (i + 1), "mp4", "视频");
                                    added++;
                                }
                                if(added == 0){ showHintToast("没有勾选任何作品"); return; }
                                s("已加入下载队列：" + added + " 条");
                                showHintToast("已加入 " + added + " 条下载任务");
                                switchTab(0);
                            }
                        })
                        .setNegativeButton("返回", null)
                        .show();
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }

    void showProfileList(final java.util.List<ProfileParser.ProfileItem> list){
        Log.i("DYP", "SHOW_PROFILE_LIST n=" + (list != null ? list.size() : 0) + " wrap=" + (listWrap != null));
        if(listWrap == null) return;
        listWrap.removeAllViews();
        final int n = list.size();
        final int pages = Math.max(1, (n + PROFILE_PAGE_SIZE - 1) / PROFILE_PAGE_SIZE);
        if(profilePage >= pages) profilePage = pages - 1;
        if(profilePage < 0) profilePage = 0;
        final int from = profilePage * PROFILE_PAGE_SIZE;
        final int to = Math.min(n, from + PROFILE_PAGE_SIZE);

        String _src = batchCollectMode ? "批量解析" : "主页";
        listHead.setText(pages > 1
                ? (_src + "共 " + n + " 条 · 第 " + (profilePage + 1) + "/" + pages + " 页 · 点「下载」直接下")
                : (_src + "共 " + n + " 条 · 点「下载」直接下，点标题先解析"));
        btnDlAll.setVisibility(n > 0 ? View.VISIBLE : View.GONE);
        if(btnClearList != null) btnClearList.setVisibility(n > 0 ? View.VISIBLE : View.GONE);
        btnDlAll.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){
            // 两步：先选「下载全部」还是「挑第几个」（setMessage + setItems 会挤掉列表，必须分开）
            dypDlg()
                .setTitle("批量下载")
                .setMessage("【只处理当前页】本页共 " + (to - from) + " 条（全部共 " + n + " 条，"
                    + pages + " 页）。\n"
                    + "换页请先用下方「上一页/下一页」切到目标页，再点这里。\n\n"
                    + "· 下载全部：把本页所有条目加入下载队列\n"
                    + "· 选择下载：勾选本页要下载第几个（可多选）\n\n"
                    + "境外平台会走服务器中转，大视频较多时速度受限。")
                .setPositiveButton("选择下载", new android.content.DialogInterface.OnClickListener(){
                    public void onClick(android.content.DialogInterface d, int w){
                        showProfilePickDialog(list, from, to);
                    }
                })
                .setNeutralButton("下载全部", new android.content.DialogInterface.OnClickListener(){
                    public void onClick(android.content.DialogInterface d, int w){
                        int added = 0;
                        for(int i = from; i < to; i++){
                            ProfileParser.ProfileItem it = list.get(i);
                            String u = it.url.length() > 0 ? it.url
                                    : (it.id.length() > 0 && it.id.matches("\\d{15,20}") ? "https://www.tiktok.com/@/video/" + it.id : "");
                            if(u.length() == 0) continue;
                            String tag = "视频" + (i + 1);
                            enqueueDownload(u, tag, "mp4", "视频");
                            added++;
                        }
                        s("已加入下载队列：" + added + " 条");
                        showHintToast("已加入 " + added + " 条下载任务");
                        switchTab(0);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
        }});

        // 翻页栏（页数 > 1 才显示）
        if(pages > 1){
            LinearLayout pager = new LinearLayout(this);
            pager.setOrientation(LinearLayout.HORIZONTAL);
            pager.setGravity(android.view.Gravity.CENTER_VERTICAL);
            pager.setPadding(0, dp(4), 0, dp(8));

            TextView prev = new TextView(this);
            prev.setText("上一页");
            prev.setTextSize(12);
            prev.setTextColor(profilePage > 0
                    ? (curDark ? 0xFF8AB4F8 : 0xFF1A73E8)
                    : (curDark ? 0xFF4A4F5A : 0xFFC0C4CC));
            prev.setPadding(dp(6), dp(5), dp(10), dp(5));
            if(profilePage > 0){
                prev.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){
                    profilePage--;
                    showProfileList(list);
                }});
            }
            pager.addView(prev);

            TextView mid = new TextView(this);
            mid.setText("  " + (profilePage + 1) + " / " + pages + "（" + from + "-" + (to - 1) + "）  ");
            mid.setTextSize(12);
            mid.setTextColor(curDark ? 0xFFB8BEC9 : 0xFF4B5563);
            pager.addView(mid);

            TextView next = new TextView(this);
            next.setText("下一页");
            next.setTextSize(12);
            next.setTextColor(profilePage < pages - 1
                    ? (curDark ? 0xFF8AB4F8 : 0xFF1A73E8)
                    : (curDark ? 0xFF4A4F5A : 0xFFC0C4CC));
            next.setPadding(dp(10), dp(5), dp(6), dp(5));
            if(profilePage < pages - 1){
                next.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){
                    profilePage++;
                    showProfileList(list);
                }});
            }
            pager.addView(next);
            listWrap.addView(pager);
        }

        for(int i = from; i < to; i++){
            final ProfileParser.ProfileItem it = list.get(i);
            final int idx = i;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(dp(12), dp(10), dp(12), dp(10));
            row.setBackground(makeRoundRect(curDark ? 0xFF24272F : 0xFFFFFFFF,
                    curDark ? 0xFF333842 : 0xFFE1E4E9, 12));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(8);
            row.setLayoutParams(lp);

            TextView title = new TextView(this);
            String t = it.title;
            if(t.length() == 0) t = it.id;
            title.setText((i + 1) + ". " + t);
            title.setTextSize(13);
            title.setTextColor(curDark ? 0xFFE8EAF0 : 0xFF1A1C22);
            title.setPadding(0, 0, 0, dp(6));
            title.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){
                switchTab(0);
                parseProfileItem(list, idx);
            }});
            row.addView(title);

            LinearLayout btns = new LinearLayout(this);
            btns.setOrientation(LinearLayout.HORIZONTAL);
            TextView btnDl = new TextView(this);
            btnDl.setText("下载");
            btnDl.setTextSize(12);
            btnDl.setTextColor(0xFFFE2C55);
            btnDl.setPadding(dp(10), dp(5), dp(10), dp(5));
            btnDl.setBackground(makeRoundRect(0x1AFE2C55, 0, 8));
            btnDl.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){
                // 快手条目：列表响应里的 mainMvUrl 就是正常视频直链，直接入队
                boolean isKs = (it.url != null && it.url.contains("kuaishou.com"))
                        || (it.directUrl != null && it.directUrl.contains("kwimgs"))
                        || (it.directUrl != null && it.directUrl.contains("kwaicdn"))
                        || (it.directUrl != null && it.directUrl.contains("yximgs"))
                        || (it.directUrl != null && it.directUrl.contains("ksyungslb"));
                if(isKs && it.directUrl != null && it.directUrl.length() > 0){
                    enqueueDownload(it.directUrl, "视频" + (idx + 1), "mp4", "视频");
                    s("已加入下载队列：第 " + (idx + 1) + " 条（快手直链）");
                    showHintToast("已加入下载队列");
                    return;
                }
                final String u = it.url.length() > 0 ? it.url
                        : (it.id.length() > 0 && it.id.matches("\\d{15,20}") ? "https://www.tiktok.com/@/video/" + it.id : "");
                if(u.length() == 0){ s("该条无链接"); return; }
                boolean isDy = u.contains("iesdouyin.com") || u.contains("douyin.com");
                boolean isYt = YtParser.isYtUrl(u);
                // YouTube 主页下载：走服务器中转（服务器有代理，不风控）
                if(isYt && it.id != null && it.id.length() > 0){
                    // YouTube 主页下载：走服务器解析拿直链，同单视频链路
                    pendingAutoDlTag = "视频" + (idx + 1);
                    pendingAutoDlIdx = idx;
                    stopProfileMode();
                    switchTab(0);
                    loadForParse("https://www.youtube.com/watch?v=" + it.id);
                    return;
                }
                // 抖音主页条目：走单视频解析全链路，解析完成后自动入下载队列
                if(isDy && it.id.length() > 0){
                    pendingAutoDlTag = "视频" + (idx + 1);
                    pendingAutoDlIdx = idx;
                    stopProfileMode();
                    switchTab(0);
                    loadForParse(u);
                    return;
                }
                s("解析第 " + (idx + 1) + " 条直链…");
                final String finalU = u;
                new Thread(new Runnable(){ public void run(){
                    final String dlUrl = resolveToDirectUrl(finalU);
                    h.post(new Runnable(){ public void run(){
                        if(dlUrl != null && dlUrl.length() > 0){
                            String tag = "视频" + (idx + 1);
                            enqueueDownload(dlUrl, tag, "mp4", "视频");
                            s("已加入下载队列：第 " + (idx + 1) + " 条");
                            showHintToast("已加入下载队列");
                        } else {
                            s("第 " + (idx + 1) + " 条解析失败");
                            showHintToast("解析失败，试试「解析看详情」");
                        }
                    }});
                }}).start();
            }});
            btns.addView(btnDl);

            TextView btnParse = new TextView(this);
            btnParse.setText("解析看详情");
            btnParse.setTextSize(12);
            btnParse.setTextColor(curDark ? 0xFF8AB4F8 : 0xFF1A73E8);
            btnParse.setPadding(dp(10), dp(5), dp(10), dp(5));
            LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            plp.leftMargin = dp(8);
            btnParse.setLayoutParams(plp);
            btnParse.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){
                switchTab(0);
                parseProfileItem(list, idx);
            }});
            btns.addView(btnParse);

            if(it.durSec > 0){
                TextView dur = new TextView(this);
                dur.setText("  " + it.durSec + "s");
                dur.setTextSize(11);
                dur.setTextColor(curDark ? 0xFF8A8F9C : 0xFF9AA0AC);
                dur.setGravity(android.view.Gravity.CENTER_VERTICAL);
                btns.addView(dur);
            }
            row.addView(btns);
            listWrap.addView(row);
        }
        s("主页解析完成 · " + n + " 条 · 已切到列表页");
        switchTab(1);
    }

    /** 解析主页列表第 idx 条（0 基） */
    void parseProfileItem(final java.util.List<ProfileParser.ProfileItem> list, final int idx){
        if(list == null || idx < 0 || idx >= list.size()) return;
        // 退出主页收集模式（解析单条走正常链路）
        if(profileMode) stopProfileMode();
        final ProfileParser.ProfileItem it = list.get(idx);
        final String target = it.url.length() > 0 ? it.url
                : (it.id.length() > 0 && it.id.matches("\\d{15,20}") ? "https://www.tiktok.com/@/video/" + it.id : "");
        if(target.length() == 0){ s("该条无链接"); return; }
        s("解析第 " + (idx + 1) + " 条...");
        loadForParse(target);
    }

    /** 把一条作品 URL 解析成可直接下载的直链（按平台分发）。
     *  YouTube：走服务器 yt parse，返回 /api/yt/file 中转
     *  TikTok：走 TtParser 服务器解析，返回中转直链
     *  X：走 XParser 服务器解析，返回 /api/x/dl 中转
     *  抖音/快手：本地解析路径（原始直链）
     *  失败返回 null */
    String resolveToDirectUrl(String url){
        try{
            if(YtParser.isYtUrl(url)){
                YtParser.YtVideo v = YtParser.resolve(this, url);
                if(v.ok() && v.videoUrl.length() > 0) return v.videoUrl;
                return null;
            }
            if(BiliParser.isBiliUrl(url)){
                BiliParser.BiliVideo bv = BiliParser.resolve(this, url, 1);
                if(bv.ok() && bv.playUrl.length() > 0) return bv.playUrl;
                return null;
            }
            if(TtParser.isTtUrl(url)){
                // TikTok：优先服务器中转（无需用户代理），失败回退本地解析
                try{
                    String r = parseTikTokByServer(url);
                    if(r != null){
                        // parseTikTokByServer 会把 curPlay 设成中转地址
                        if(curPlay != null && curPlay.length() > 0) return curPlay;
                    }
                }catch(Throwable t){
                    Log.i("DYP", "TT_SRV_FALLBACK " + t);
                }
                TtParser.TtVideo tv = TtParser.resolve(this, url);
                if(tv.ok() && tv.hasVideo()){
                    return tv.playUrl.length() > 0 ? tv.playUrl : tv.downloadUrl;
                }
                return null;
            }
            if(XParser.isXUrl(url)){
                XParser.XVideo v = XParser.resolveServerOnly(this, url);
                if(v.ok() && v.hasVideo()) return v.firstVideo();
                return null;
            }
            // 抖音/快手：原样返回（下载器能处理）
            return url;
        }catch(Throwable t){
            Log.i("DYP", "RESOLVE_DL_ERR " + t);
            return null;
        }
    }

    /** 切到上一集 / 下一集（解析成功后可用） */
    void ytPrev(){
        if(ytPlaylist.isEmpty()){ s("当前不是播放列表"); return; }
        if(ytIdx <= 0){ showHintToast("已经是第一集"); return; }
        parseYtItem(ytIdx - 1);
    }
    void ytNext(){
        if(ytPlaylist.isEmpty()){ s("当前不是播放列表"); return; }
        if(ytIdx >= ytPlaylist.size() - 1){ showHintToast("已经是最后一集"); return; }
        parseYtItem(ytIdx + 1);
    }

    /** 外链卡片深挖：走服务器 /api/link/resolve，返回 render 用的 JSON */
    String resolveCardLink(String cardUrl){
        try{
            String srv = effSrv();
            if(srv.length() == 0) return null;
            String api = srv + "/api/link/resolve?url=" + java.net.URLEncoder.encode(cardUrl, "UTF-8");
            HttpURLConnection c = (HttpURLConnection) new java.net.URL(api).openConnection(java.net.Proxy.NO_PROXY);
            c.setRequestProperty("User-Agent", MUA);
            c.setRequestProperty("Accept", "application/json,*/*");
            // HMAC 鉴权
            String ts = String.valueOf(System.currentTimeMillis() / 1000);
            String nonce = java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 12);
            String path = "/api/link/resolve";
            String msg = path + "\n" + ts + "\n" + nonce;
            String key = srvHmacKeyS(this);
            if(key.length() > 0){
                javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
                mac.init(new javax.crypto.spec.SecretKeySpec(key.getBytes("UTF-8"), "HmacSHA256"));
                byte[] raw = mac.doFinal(msg.getBytes("UTF-8"));
                StringBuilder sb = new StringBuilder();
                for(byte b : raw) sb.append(String.format("%02x", b));
                c.setRequestProperty("X-TS", ts);
                c.setRequestProperty("X-NONCE", nonce);
                c.setRequestProperty("X-SIGN", sb.toString());
            }
            c.setConnectTimeout(20000);
            c.setReadTimeout(30000);
            int code = c.getResponseCode();
            java.io.InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            in.close();
            c.disconnect();
            String body = bo.toString("UTF-8");
            Log.i("DYP", "LINK_RESOLVE http=" + code + " len=" + body.length());
            if(code != 200) return null;
            Object root = XParser.Json.parse(body);
            if(!(root instanceof java.util.Map)) return null;
            java.util.Map r = (java.util.Map) root;
            Object okObj = r.get("ok");
            boolean ok = Boolean.TRUE.equals(okObj) || "true".equals(String.valueOf(okObj));
            if(!ok) return null;
            String vurl = String.valueOf(r.get("video_url"));
            if(vurl == null || vurl.length() == 0) return null;
            // 转成 render 认识的 JSON
            StringBuilder b = new StringBuilder(512);
            b.append('{');
            b.append("\"i_id\":\"").append("card").append("\",");
            b.append("\"i_platform\":\"外链\",");
            b.append("\"i_play\":\"").append(vurl.replace("\"", "\\\"")).append("\"");
            String title = r.get("title") == null ? "" : String.valueOf(r.get("title"));
            if(title.length() > 0) b.append(",\"i_desc\":\"").append(title.replace("\"", "\\\"")).append("\"");
            b.append('}');
            return b.toString();
        }catch(Throwable t){
            Log.i("DYP", "LINK_CARD_ERR " + t);
            return null;
        }
    }

    /** 下载视频前的大文件提醒：
     *  内置代理开着 → X/YouTube 走服务器中转（弹确认）；
     *  关着 → 直连（用户自己代理 / TUN 生效），直接下。 */
    void warnBigOverseas(final Runnable then){
        final boolean isX = isXDomain(curUrl);
        final boolean isYt = YtParser.isYtUrl(curUrl);
        if(!isX && !isYt){
            then.run();
            return;
        }
        // 内置代理关着：走用户自己的网络（含用户自己挂的代理/TUN），直接下
        if(!ProxyPolicy.builtinOn(this)){
            then.run();
            return;
        }
        if(isX && !xSrvUsable){
            showHintToast("服务器代理流量不可用，请关闭内置代理走自己的网络后重试");
            return;
        }
        String msg;
        if(isYt){
            msg = "YouTube 将走服务器中转下载：\n"
                + "解析接口在国内被墙，必须服务器中转。\n\n"
                + "速度受服务器带宽限制（约 250KB/s）。\n\n"
                + "仍要继续下载吗？";
        } else {
            msg = "内置代理已开启，这个大文件将走我们服务器的机场流量下载，"
                + "速度受服务器带宽限制（约 250KB/s），且会消耗机场流量。\n\n"
                + "想更快？关闭内置代理，用你自己的网络 / 代理直连。\n\n"
                + "仍要继续走服务器下载吗？";
        }
        dypDlg()
            .setTitle(isYt ? "YouTube 走服务器下载" : "境外视频走服务器下载")
            .setMessage(msg)
            .setPositiveButton("继续下载", new android.content.DialogInterface.OnClickListener(){
                public void onClick(android.content.DialogInterface d, int w){ then.run(); }
            })
            .setNegativeButton("取消", new android.content.DialogInterface.OnClickListener(){
                public void onClick(android.content.DialogInterface d, int w){ d.dismiss(); }
            })
            .show();
    }


    /** 下载：视频 → mp4；图集 → 多选列表；实况图 → 封装实况照片 */
    void doDownload(){
        // 下载前先确保有存储权限（没有就申请，避免"能播不能下"）
        if(!ensureStoragePerm()){
            DiagLog.w(DiagLog.M_DOWNLOAD, "下载被暂缓 | 已向用户申请存储权限，授权后会提示重试");
            showHintToast("需要存储权限才能保存文件，请在弹窗里点「允许」");
            return;
        }
        DiagLog.i(DiagLog.M_DOWNLOAD, "开始下载 | 链接=" + (curUrl == null ? "?" : curUrl));
        // X 多视频：一条推带 2~4 个视频，先让用户选第几个
        if(curXVideos.size() > 1){
            showXVideoPicker();
            return;
        }
        // X 且服务器机场流量不可用：提示用户自己挂代理（媒体 CDN 被 DNS 污染，直连必挂）
        if(isXDomain(curUrl) && !xSrvUsable){
            showHintToast("服务器代理流量不可用，请自行挂代理后重试下载");
        }
        boolean hasImgs = imgUrls != null && imgUrls.size() > 0;
        if(!hasImgs && (curPlay == null || curPlay.length() == 0)){ s("无可下载地址"); return; }

        // 视频（无图集）：大文件提醒后直接下 mp4（分类：视频）
        if(!hasImgs){
            warnBigOverseas(new Runnable(){ public void run(){
                // YouTube：直链绑服务器解析 IP（手机直连 403），下载走服务器流式中转；
                // 服务器已异步缓存合并好的 mp4，二次下载直接吃缓存
                if(YtParser.isYtUrl(curUrl) || (curPlay != null && curPlay.indexOf("/api/yt/stream") >= 0)){
                    String dlPlay = curPlay;
                    if(dlPlay == null || (dlPlay.indexOf("googlevideo") >= 0 || dlPlay.indexOf("videoplayback") >= 0)){
                        // 直链 403 下不了，换服务器中转
                        String srv = effSrvS(MainActivity.this);
                        if(srv.length() > 0 && curId.length() > 0){
                            dlPlay = srv + "/api/yt/stream?id=" + curId + "&uid=" + ytUid(MainActivity.this);
                        }
                    }
                    enqueueDownloadWithAudio(dlPlay, "视频", "mp4", "视频", "", "");
                    return;
                }
                // 其他平台 direct：带独立音轨，下载后本地合成
                String au = (curAudio != null && curAudio.startsWith("http")) ? curAudio : "";
                enqueueDownloadWithAudio(curPlay, "视频", "mp4", "视频", "", au);
            }});
            return;
        }

        final int n = imgUrls.size();
        final boolean[] sel = new boolean[n];
        sel[imgIdx < n ? imgIdx : 0] = true;   // 默认勾选当前这张

        String[] labels = new String[n];
        for(int i = 0; i < n; i++){
            boolean isLive = liveDirect(dynAt(i)).length() > 0;
            String kind = isLive ? "实况→动图/" : "图片";
            labels[i] = "第 " + (i + 1) + " 张　" + kind + (i == imgIdx ? "　（当前）" : "");
        }

        int liveN = 0;
        for(int i = 0; i < n; i++){ if(liveDirect(dynAt(i)).length() > 0) liveN++; }

        String tip = "共 " + n + " 张";
        if(liveN > 0) tip += "，" + liveN + " 张实况存到「动图」目录";
        tip += "\n实况存成 .jpg 实况照片，相册长按可播放";

        dypDlg()
            .setTitle("选择要下载的图片（" + tip + "）")
            .setMultiChoiceItems(labels, sel,
                new android.content.DialogInterface.OnMultiChoiceClickListener(){
                    public void onClick(android.content.DialogInterface d, int which, boolean isChecked){
                        sel[which] = isChecked;
                    }
                })
            .setPositiveButton("下载所选", new android.content.DialogInterface.OnClickListener(){
                public void onClick(android.content.DialogInterface d, int w){
                    int cnt = 0;
                    for(int i = 0; i < n; i++){
                        if(!sel[i]) continue;
                        String live = liveDirect(dynAt(i));
                        if(live.length() > 0){
                            // 实况 → 动图目录
                            downloadQueue.add(new DlTask(
                                imgUrls.get(i), "实况" + (i + 1), "jpg", "动图", "", live));
                        } else {
                            downloadQueue.add(new DlTask(
                                imgUrls.get(i), "图" + (i + 1), "jpg", "图片", "", ""));
                        }
                        cnt++;
                    }
                    if(cnt == 0){ s("未选择任何图片"); return; }
                    if(!dlRunning){ dlRunning = true; runDownloadQueue(); }
                    s("已加入下载：" + cnt + " 个");
                    Log.i("DYP", "IMG_QUEUE n=" + cnt);
                }
            })
            .setNeutralButton("全选下载", new android.content.DialogInterface.OnClickListener(){
                public void onClick(android.content.DialogInterface d, int w){
                    for(int i = 0; i < n; i++){
                        String live = liveDirect(dynAt(i));
                        if(live.length() > 0){
                            // 实况 → 动图目录
                            downloadQueue.add(new DlTask(
                                imgUrls.get(i), "实况" + (i + 1), "jpg", "动图", "", live));
                        } else {
                            downloadQueue.add(new DlTask(
                                imgUrls.get(i), "图" + (i + 1), "jpg", "图片", "", ""));
                        }
                    }
                    if(!dlRunning){ dlRunning = true; runDownloadQueue(); }
                    s("已加入下载：" + n + " 个");
                    Log.i("DYP", "IMG_QUEUE all n=" + n);
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }

    /**
     * 下载实况图：抓静态图 + 动轨视频，封装成 Android Motion Photo（单个 .jpg）。
     * 相册长按可播放，跟抖音原生实况图一致。
     */
    boolean downloadLivePhoto(DlTask task, File target){
        byte[] img = null, mp4 = null;
        try{
            h.post(new Runnable(){ public void run(){
                progSet(0.15f, "下载实况图…");
            }});
            img = httpGetBytes(task.url);
            if(img == null || img.length < 100){
                Log.i("DYP", "LIVE_IMG_FAIL len=" + (img == null ? -1 : img.length));
                appLog("下载", "✗ 实况静态图下载失败");
                return false;
            }
            h.post(new Runnable(){ public void run(){
                progSet(0.55f, "下载实况动轨…");
            }});
            mp4 = httpGetBytes(task.liveUrl);
            if(mp4 == null || mp4.length < 1000){
                Log.i("DYP", "LIVE_MP4_FAIL len=" + (mp4 == null ? -1 : mp4.length));
                appLog("下载", "✗ 实况动轨下载失败");
                return false;
            }
            h.post(new Runnable(){ public void run(){
                progSet(0.85f, "封装实况照片…");
            }});
            LivePhoto.Result r = LivePhoto.build(img, mp4, target);
            Log.i("DYP", "LIVE_RESULT ok=" + r.ok + " size=" + r.size + " err=" + r.err
                    + " img=" + img.length + " mp4=" + mp4.length);
            appLog("下载", (r.ok ? "✓ 实况 " : "✗ 实况 ")
                    + (r.ok ? target.getName() + "  " + InnerDownloader.sizeText(r.size) : r.err));
            if(r.ok){
                try{ savedDirs.add(target.getParentFile().getAbsolutePath()); }catch(Throwable t){}
            }
            return r.ok;
        }catch(Throwable t){
            Log.i("DYP", "LIVE_DL_ERR " + t);
            appLog("下载", "✗ 实况封装异常 " + t);
            return false;
        }
    }

    /**
     * YouTube direct 双轨下载：视频轨 + 音频轨分别下载（都走用户代理），
     * 再用 MediaExtractor + MediaMuxer 本地合成 mp4（不重编码，秒级完成）。
     */
    boolean downloadDualTrack(DlTask task, File target, final int fIdx, final int fTotal){
        File vTmp = null, aTmp = null;
        try{
            h.post(new Runnable(){ public void run(){
                progSet(0f, String.format(Locale.US, "(%d/%d) 下载视频轨…", fIdx, fTotal));
            }});
            vTmp = new File(getCacheDir(), "yt_v_" + System.currentTimeMillis() + ".mp4");
            boolean vOk = InnerDownloader.fetch(task.url, vTmp, new InnerDownloader.Cb(){
                @Override public void onStart(long totalBytes){}
                @Override public void onProgress(long done, long tot){
                    float r = tot > 0 ? (float)((double) done / tot) : 0f;
                    progSet(r * 0.7f, String.format(Locale.US, "(%d/%d) 视频轨  %s / %s",
                            fIdx, fTotal, InnerDownloader.sizeText(done),
                            tot > 0 ? InnerDownloader.sizeText(tot) : "?"));
                }
                @Override public void onDone(boolean ok2, File out, String msg){}
            }, curSession, this);
            if(!vOk || vTmp.length() < 1024){
                Log.i("DYP", "YT_DUAL_V_FAIL len=" + (vTmp.exists() ? vTmp.length() : -1));
                appLog("下载", "✗ 视频轨下载失败");
                return false;
            }

            h.post(new Runnable(){ public void run(){
                progSet(0.72f, String.format(Locale.US, "(%d/%d) 下载音轨…", fIdx, fTotal));
            }});
            aTmp = new File(getCacheDir(), "yt_a_" + System.currentTimeMillis() + ".m4a");
            boolean aOk = InnerDownloader.fetch(task.audioUrl, aTmp, new InnerDownloader.Cb(){
                @Override public void onStart(long totalBytes){}
                @Override public void onProgress(long done, long tot){
                    float r = tot > 0 ? (float)((double) done / tot) : 0f;
                    progSet(0.72f + r * 0.18f, String.format(Locale.US, "(%d/%d) 音轨  %s",
                            fIdx, fTotal, InnerDownloader.sizeText(done)));
                }
                @Override public void onDone(boolean ok2, File out, String msg){}
            }, curSession, this);
            if(!aOk || aTmp.length() < 1024){
                Log.i("DYP", "YT_DUAL_A_FAIL len=" + (aTmp.exists() ? aTmp.length() : -1));
                appLog("下载", "✗ 音轨下载失败，改用纯视频轨");
                // 音轨失败不整体失败：直接改名视频轨为最终文件
                try{ if(target.exists()) target.delete(); vTmp.renameTo(target); }catch(Throwable t){}
                return true;
            }

            h.post(new Runnable(){ public void run(){
                progSet(0.92f, String.format(Locale.US, "(%d/%d) 合成音视频…", fIdx, fTotal));
            }});
            boolean mOk = muxMp4(vTmp.getAbsolutePath(), aTmp.getAbsolutePath(), target.getAbsolutePath());
            Log.i("DYP", "YT_DUAL_MUX ok=" + mOk + " out=" + (target.exists() ? target.length() : -1));
            appLog("下载", (mOk ? "✓ 视频+音轨已合成 " : "✗ 合成失败 ")
                    + target.getName() + "  " + InnerDownloader.sizeText(target.length()));
            if(mOk){
                try{ savedDirs.add(target.getParentFile().getAbsolutePath()); }catch(Throwable t){}
            }
            return mOk;
        }catch(Throwable t){
            Log.i("DYP", "YT_DUAL_ERR " + t);
            appLog("下载", "✗ 双轨下载异常 " + t);
            return false;
        }finally{
            try{ if(vTmp != null) vTmp.delete(); }catch(Throwable t){}
            try{ if(aTmp != null) aTmp.delete(); }catch(Throwable t){}
        }
    }

    /** MediaExtractor + MediaMuxer 合成 mp4（纯拷贝，不重编码） */
    static boolean muxMp4(String videoPath, String audioPath, String outPath){
        android.media.MediaExtractor vEx = null, aEx = null;
        android.media.MediaMuxer mux = null;
        try{
            vEx = new android.media.MediaExtractor();
            vEx.setDataSource(videoPath);
            int vTrack = -1;
            for(int i = 0; i < vEx.getTrackCount(); i++){
                android.media.MediaFormat f = vEx.getTrackFormat(i);
                String mime = f.getString(android.media.MediaFormat.KEY_MIME);
                if(mime != null && mime.startsWith("video/")){ vTrack = i; break; }
            }
            if(vTrack < 0) return false;

            aEx = new android.media.MediaExtractor();
            aEx.setDataSource(audioPath);
            int aTrack = -1;
            for(int i = 0; i < aEx.getTrackCount(); i++){
                android.media.MediaFormat f = aEx.getTrackFormat(i);
                String mime = f.getString(android.media.MediaFormat.KEY_MIME);
                if(mime != null && mime.startsWith("audio/")){ aTrack = i; break; }
            }
            if(aTrack < 0) return false;

            mux = new android.media.MediaMuxer(outPath, android.media.MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            vEx.selectTrack(vTrack);
            android.media.MediaFormat vf = vEx.getTrackFormat(vTrack);
            int muxV = mux.addTrack(vf);
            int muxA = -1;
            if(aTrack >= 0){
                aEx.selectTrack(aTrack);
                android.media.MediaFormat af = aEx.getTrackFormat(aTrack);
                muxA = mux.addTrack(af);
            }
            mux.start();

            android.media.MediaCodec.BufferInfo info = new android.media.MediaCodec.BufferInfo();
            java.nio.ByteBuffer buf = java.nio.ByteBuffer.allocate(1024 * 1024);
            long baseV = 0;
            while(true){
                int n = vEx.readSampleData(buf, 0);
                if(n < 0) break;
                info.offset = 0;
                info.size = n;
                info.presentationTimeUs = vEx.getSampleTime();
                info.flags = vEx.getSampleFlags();
                if(baseV == 0 && info.presentationTimeUs > 0) baseV = info.presentationTimeUs;
                mux.writeSampleData(muxV, buf, info);
                vEx.advance();
            }
            if(muxA >= 0){
                while(true){
                    int n = aEx.readSampleData(buf, 0);
                    if(n < 0) break;
                    info.offset = 0;
                    info.size = n;
                    info.presentationTimeUs = aEx.getSampleTime();
                    info.flags = aEx.getSampleFlags();
                    mux.writeSampleData(muxA, buf, info);
                    aEx.advance();
                }
            }
            mux.stop();
            return true;
        }catch(Throwable t){
            Log.i("DYP", "MUX_ERR " + t);
            return false;
        }finally{
            try{ if(vEx != null) vEx.release(); }catch(Throwable t){}
            try{ if(aEx != null) aEx.release(); }catch(Throwable t){}
            try{ if(mux != null){ try{ mux.release(); }catch(Throwable t){} } }catch(Throwable t){}
        }
    }

    /** 下载 BGM：抖音 detail 给的完整背景音乐 MP3 直链，无需转码 */
    void downloadBgm(){
        // 文件名带 BGM 标题（清掉非法字符），避免所有 BGM 都叫 dy_xxx_bgm_时间戳
        String safe = "";
        try{
            safe = curBgmTitle.replaceAll("[\\\\/:*?\"<>|\\s]+", "_");
            if(safe.length() > 40) safe = safe.substring(0, 40);
        }catch(Throwable t){ safe = ""; }
        final String tag = "bgm" + (safe.length() > 0 ? "_" + safe : "");
        s("下载背景音乐中...");
        appLog("下载", "BGM 开始 " + (curBgmTitle.length() > 0 ? curBgmTitle : curBgm));
        // 快手给的是 m4a 音轨，抖音给的是 mp3 直链 —— 按真实后缀存，避免 .mp3 里装 m4a
        String ext = "mp3";
        String lower = curBgm.toLowerCase();
        int qm = lower.indexOf('?');
        if(qm > 0) lower = lower.substring(0, qm);
        if(lower.endsWith(".m4a") || lower.endsWith(".aac")) ext = "m4a";
        enqueueDownload(curBgm, tag, ext, "音频", "");
    }

    /** 从当前视频提取音频，转码为 MP3（带进度条） */
    void extractAudio(){
        // 快手有独立音轨（m4a）就直接下它，几百 KB，比整段视频快一个数量级
        final boolean useMusic = curMusic != null && curMusic.length() > 0;
        final String srcUrl = useMusic ? curMusic : curPlay;
        if(srcUrl == null || srcUrl.length() == 0){ s("无视频地址"); return; }
        progCancel = false;
        s(useMusic ? "下载音轨中..." : "提取音频中...");
        progShow();
        new Thread(new Runnable(){ public void run(){
            java.io.File tmp = null;
            try{
                // ---------- 阶段 1：下载（0 ~ 35%） ----------
                final long[] lastLog = new long[]{0};
                byte[] data = httpGetBytes(srcUrl, new ProgressCb(){
                    @Override public void onStart(long total){
                        Log.i("DYP", "DL start total=" + total);
                        progSet(0f, "下载中  " + (total > 0 ? humanSize(total) : "未知大小"));
                    }
                    @Override public void onProgress(long done, long total){
                        float r = total > 0 ? (float)((double) done / total) : 0f;
                        progSet(r * 0.35f, "下载中  " + humanSize(done) + (total > 0 ? " / " + humanSize(total) : ""));
                        long now = System.currentTimeMillis();
                        if(now - lastLog[0] > 1000){ lastLog[0] = now; Log.i("DYP", "DL " + done + "/" + total); }
                    }
                    @Override public void onError(Exception e){ Log.i("DYP", "DL err " + e); }
                });
                if(data == null || data.length < 1024){ s("视频下载失败"); progHide(); return; }

                tmp = new java.io.File(getCacheDir(), useMusic ? "tmp_audio_src.m4a" : "tmp_audio_src.mp4");
                java.io.FileOutputStream fo = new java.io.FileOutputStream(tmp);
                fo.write(data); fo.close();
                Log.i("DYP", "DL done " + data.length + "B music=" + useMusic);
                data = null;

                // ---------- 阶段 2：转码（35% ~ 100%） ----------
                // 文件名按平台加前缀，方便区分来源
final String pfx;
                if(TtParser.isTtUrl(srcUrl)) pfx = "tt_";
                else if(KsParser.isKsUrl(srcUrl)) pfx = "ks_";
                else if(isXDomain(srcUrl) || isXDomain(curUrl)) pfx = "x_";
                else pfx = "dy_";
                // 分类下载目录（音频）：自定义 > 默认
                File audioDir = new File(dlDirFor("音频"));
                if(!audioDir.exists()) audioDir.mkdirs();
                final java.io.File out = new java.io.File(audioDir,
                    pfx + curId + "_audio_" + System.currentTimeMillis() + ".mp3");

                long durationMs = Mp3Converter.probeDuration(tmp.getAbsolutePath());
                final String fmtAll = durationMs > 0 ? String.format(Locale.US, "%.1fs", durationMs / 1000.0) : "?";
                progSet(0.35f, "转码中  0.0s / " + fmtAll);

                final String fout = out.getName();
                boolean ok = Mp3Converter.convert(tmp.getAbsolutePath(), out.getAbsolutePath(), 192, durationMs,
                    new Mp3Converter.Cb(){
                        @Override public void onProgress(float ratio, long timeMs){
                            progSet(0.35f + ratio * 0.65f, String.format(Locale.US,
                                "转码中  %.1fs / %s", timeMs / 1000.0, fmtAll));
                        }
                        @Override public void onDone(boolean ok2, String msg){
                            Log.i("DYP", "MP3 done ok=" + ok2 + " msg=" + msg);
                            appLog("转码", (ok2 ? "✓ " : "✗ ") + fout + "  " + msg);
                        }
                    });

                if(ok){
                    progSet(1f, "完成");
                    s("音频已保存：Download/" + fout);
                    showHintToast("音频已保存到 Download");
                    h.postDelayed(new Runnable(){ public void run(){ progHide(); }}, 1200);
                } else {
                    s("提取音频失败");
                    progHide();
                }
            }catch(Throwable t){
                Log.i("DYP","EXTRACT_ERR " + t);
                s("提取失败");
                progHide();
            }finally{
                try{ if(tmp != null) tmp.delete(); }catch(Throwable t){}
            }
        }}).start();
    }

    /** 设置页「存储写入检测」的副标题 */
    String storageStateLabel(){
        try{
            int sdk = android.os.Build.VERSION.SDK_INT;
            boolean perm = AppInfo.hasStoragePerm(this);
            boolean canWrite = ScopedStore.canWriteDir(new File(dlDirFor("视频")));
            if(canWrite) return "正常 · 可直接写入公共下载目录";
            if(perm) return "受限 · 将自动改存公共 Download（MediaStore）";
            return "缺权限 · 点击授权";
        }catch(Throwable t){ return "点击查看详情"; }
    }

    /** 存储写入详情弹窗（用户自查用，也方便反馈群里排查） */
    void showStorageState(){
        int sdk = android.os.Build.VERSION.SDK_INT;
        String dir = dlDirFor("视频");
        boolean perm = AppInfo.hasStoragePerm(this);
        boolean canWrite = ScopedStore.canWriteDir(new File(dir));
        boolean allFiles = AppInfo.hasAllFilesAccess(this);

        StringBuilder m = new StringBuilder();
        m.append("系统版本：Android ").append(sdk)
         .append("（API ").append(sdk).append("）\n\n");
        m.append("存储权限：").append(perm ? "已授权" : "未授权").append("\n");
        if(sdk >= 30){
            m.append("所有文件访问：").append(allFiles ? "已授权" : "未授权").append("\n");
        }
        m.append("目标目录：\n").append(dir).append("\n\n");
        m.append("目录可写：").append(canWrite ? "是" : "否").append("\n\n");
        if(canWrite){
            m.append("下载会直接写入上面这个目录。");
        }else{
            m.append("检测到目录不可写。下载会自动改存到：\n");
            m.append("内部存储/Download/DYParse/<分类>/\n");
            m.append("（走系统媒体库 MediaStore，无需额外权限）\n\n");
            m.append("如果连媒体库也失败，文件会留在应用私有目录，");
            m.append("此时请到系统设置里给本应用开启存储权限。");
        }

        dypDlg()
            .setTitle("存储写入检测")
            .setMessage(m.toString())
            .setPositiveButton("重新检测", new android.content.DialogInterface.OnClickListener(){
                public void onClick(android.content.DialogInterface d, int w){ showStorageState(); }
            })
            .setNeutralButton("申请权限", new android.content.DialogInterface.OnClickListener(){
                public void onClick(android.content.DialogInterface d, int w){
                    ensureStoragePerm();
                }
            })
            .setNegativeButton("关闭", null)
            .show();
    }

    /** 调试：长按「存储写入检测」切换强制走 MediaStore 兜底（验证 Android 10 等受限环境用） */
    void toggleForceScoped(){
        boolean cur = getSharedPreferences(SP, MODE_PRIVATE).getBoolean("force_scoped", false);
        getSharedPreferences(SP, MODE_PRIVATE).edit().putBoolean("force_scoped", !cur).apply();
        showHintToast(!cur ? "已强制走 MediaStore 兜底（测试模式）" : "已恢复正常写入");
        buildSettingsPage();
    }

    /** 下载根目录（兼容旧版：读旧 key 作为根，分类未填时用它/<分类>） */
    String dlRootDir(){
        String legacy = getSharedPreferences(SP, MODE_PRIVATE).getString(KEY_DL_PATH, "");
        if(legacy != null && legacy.length() > 0) return legacy;
        return new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "DYParse").getAbsolutePath();
    }

    /** 分类下载目录：分类自定义 > 根目录/分类 */
    String dlDirFor(String cat){
        String k = dlKeyOf(cat);
        String v = getSharedPreferences(SP, MODE_PRIVATE).getString(k, "");
        if(v != null && v.length() > 0) return v;
        return new File(dlRootDir(), cat).getAbsolutePath();
    }

    /** 分类 → prefs key */
    static String dlKeyOf(String cat){
        if("图片".equals(cat)) return KEY_DL_IMG;
        if("音频".equals(cat)) return KEY_DL_AUDIO;
        if("合集".equals(cat)) return KEY_DL_MIX;
        if("动图".equals(cat)) return KEY_DL_ANIM;
        return KEY_DL_VIDEO;
    }

    /** 该分类是否有自定义路径 */
    boolean dlCatCustom(String cat){
        String v = getSharedPreferences(SP, MODE_PRIVATE).getString(dlKeyOf(cat), "");
        return v != null && v.length() > 0;
    }

    void enqueueDownload(String url, String tag, String ext){
        enqueueDownload(url, tag, ext, "视频", "");
    }

    void enqueueDownload(String url, String tag, String ext, String cat){
        enqueueDownload(url, tag, ext, cat, "");
    }

    void enqueueDownload(String url, String tag, String ext, String cat, String mixDir){
        downloadQueue.add(new DlTask(url, tag, ext, cat, mixDir));
        Log.i("DYP", "DL_QUEUE + " + tag + " cat=" + cat + " 总计=" + downloadQueue.size());
        if(!dlRunning){
            dlRunning = true;
            runDownloadQueue();
        }
    }

    /** 带独立音轨入队（YouTube direct 双轨下载） */
    void enqueueDownloadWithAudio(String url, String tag, String ext, String cat, String mixDir, String audioUrl){
        downloadQueue.add(new DlTask(url, tag, ext, cat, mixDir, "", "", audioUrl));
        Log.i("DYP", "DL_QUEUE + " + tag + " cat=" + cat + " audio=" + (audioUrl.length() > 0) + " 总计=" + downloadQueue.size());
        if(!dlRunning){
            dlRunning = true;
            runDownloadQueue();
        }
    }

    /** 下载任务：cat = 分类目录（视频/图片/音频/合集），mixDir = 合集专属子目录（可为空） */
    static class DlTask {
        final String url, tag, ext, cat, mixDir;
        /** 非空表示这是实况图：url=静态图，liveUrl=动轨视频，下载后封装成一个 .jpg */
        final String liveUrl;
        /** 非空时用它作为文件名主体（合集用：标题_第N集），否则用 dy_<id>_<tag> */
        final String nameBase;
        /** YouTube direct：独立音轨直链，非空时下载后与视频轨本地 mux */
        final String audioUrl;
        DlTask(String u, String t, String e){ this(u, t, e, "视频", "", "", "", ""); }
        DlTask(String u, String t, String e, String c){ this(u, t, e, c, "", "", "", ""); }
        DlTask(String u, String t, String e, String c, String m){
            this(u, t, e, c, m, "", "", "");
        }
        DlTask(String u, String t, String e, String c, String m, String lv){
            this(u, t, e, c, m, lv, "", "");
        }
        DlTask(String u, String t, String e, String c, String m, String lv, String nb){
            this(u, t, e, c, m, lv, nb, "");
        }
        DlTask(String u, String t, String e, String c, String m, String lv, String nb, String au){
            url = u; tag = t; ext = e; cat = c; mixDir = m;
            liveUrl = lv == null ? "" : lv;
            nameBase = nb == null ? "" : nb;
            audioUrl = au == null ? "" : au;
        }
        boolean isLive(){ return liveUrl.length() > 0; }
    }

    final java.util.List<DlTask> downloadQueue = new java.util.ArrayList<DlTask>();
    volatile boolean dlRunning = false;
    /** 本次下载实际落盘的目录集合（按顺序去重），用于完成时显示完整路径 */
    final java.util.LinkedHashSet<String> savedDirs = new java.util.LinkedHashSet<String>();

    /** 串行消费下载队列，进度条显示 第N个/总数 */
    void runDownloadQueue(){
        // 前台服务：切后台继续下载（WakeLock + 通知栏占位）
        try{
            if(android.os.Build.VERSION.SDK_INT >= 26){
                startForegroundService(new android.content.Intent(this, DownloadService.class));
            } else {
                startService(new android.content.Intent(this, DownloadService.class));
            }
        }catch(Throwable t){
            Log.i("DYP", "DL_SERVICE_ERR " + t);
        }
        new Thread(new Runnable(){ public void run(){
            int total = downloadQueue.size();
            int idx = 0;
            int okCount = 0, failCount = 0;
            final String[] lastCatDir = new String[]{ "" };   // 记录最后一个任务的实际目录
            progShow();
            try{
                while(!downloadQueue.isEmpty()){
                    if(progCancel){ downloadQueue.clear(); break; }
                    DlTask task;
                    synchronized(downloadQueue){ task = downloadQueue.remove(0); }
                    if(task == null || task.url == null || task.url.length() == 0) continue;
                    idx++;
                    final int fIdx = idx, fTotal = total;

                    String name;
                    if(task.nameBase.length() > 0){
                        // 合集：标题_第N集_时间戳.mp4
                        name = task.nameBase + "_" + System.currentTimeMillis() + "." + task.ext;
                    } else {
                        // 前缀按「当前解析的平台」判定：X/YouTube 的媒体走服务器中转，
                        // 光看下载 URL 会误判成 dy_，所以同时看 curUrl。
                        String pfx;
                        if(TtParser.isTtUrl(task.url)) pfx = "tt_";
                        else if(KsParser.isKsUrl(task.url)) pfx = "ks_";
                        else if(isXDomain(task.url) || isXDomain(curUrl)) pfx = "x_";
                        else if(YtParser.isYtUrl(task.url) || YtParser.isYtUrl(curUrl)) pfx = "yt_";
                        else if(BiliParser.isBiliUrl(task.url) || BiliParser.isBiliUrl(curUrl)
                                || task.url.indexOf("bilivideo.com") >= 0) pfx = "bl_";
                        else pfx = "dy_";
                        name = pfx + curId + "_" + task.tag + "_" + System.currentTimeMillis() + "." + task.ext;
                    }
                    // 分类下载目录：分类自定义 > 根目录/分类；合集再加专属子文件夹
                    File catDir = new File(dlDirFor(task.cat));
                    if(task.mixDir != null && task.mixDir.length() > 0){
                        catDir = new File(catDir, task.mixDir);
                    }
                    // 分区存储兼容：目录不可写（Android 10+ 常见）时，
                    // 先落到应用私有目录，下完再经 MediaStore 搬进公共 Download
                    boolean dirWritable = ScopedStore.canWriteDir(catDir);
                    // 测试开关：设置里有 force_scoped 时强制走 MediaStore 兜底（仅调试用）
                    if(getSharedPreferences(SP, MODE_PRIVATE).getBoolean("force_scoped", false)){
                        dirWritable = false;
                        Log.i("DYP", "DL_FORCE_SCOPED 测试开关已开启");
                    }
                    File workDir = catDir;
                    String relSub = task.cat;
                    if(!dirWritable){
                        workDir = new File(getCacheDir(), "dl_tmp");
                        if(!workDir.exists()) workDir.mkdirs();
                        Log.i("DYP", "DL_SCOPED_FALLBACK 目录不可写，改用私有临时目录: "
                                + catDir.getAbsolutePath() + " -> " + workDir.getAbsolutePath());
                        DiagLog.w(DiagLog.M_PERM, "下载目录不可写，走 MediaStore 兜底 | "
                                + catDir.getAbsolutePath() + " | API=" + android.os.Build.VERSION.SDK_INT);
                    }
                    if(task.mixDir != null && task.mixDir.length() > 0){
                        relSub = task.cat + "/" + task.mixDir;
                    }
                    final File target = new File(workDir, name);
                    lastCatDir[0] = catDir.getAbsolutePath();

                    s("下载中 " + idx + "/" + total + "：" + name);
                    Log.i("DYP", "DL_START " + idx + "/" + total + " " + name);

                    boolean ok;
                    if(task.isLive()){
                        ok = downloadLivePhoto(task, target);
                    } else if(task.audioUrl != null && task.audioUrl.length() > 0){
                        // YouTube direct：视频轨 + 音频轨分别下载，MediaMuxer 本地合成 mp4
                        ok = downloadDualTrack(task, target, fIdx, fTotal);
                    } else {
                        // TikTok 下载需要 Session（cookie + 代理分流）
                        final Object ttSes = TtParser.isTtUrl(task.url)
                                ? new TtParser.Session() : curSession;
                        ok = InnerDownloader.fetch(task.url, target, new InnerDownloader.Cb(){
                            @Override public void onStart(long totalBytes){
                                progSet(0f, String.format(Locale.US, "(%d/%d) 下载中  %s",
                                        fIdx, fTotal, InnerDownloader.sizeText(totalBytes)));
                            }
                            @Override public void onProgress(long done, long tot){
                                float r = tot > 0 ? (float)((double) done / tot) : 0f;
                                progSet(r, String.format(Locale.US, "(%d/%d) 下载中  %s / %s",
                                        fIdx, fTotal,
                                        InnerDownloader.sizeText(done),
                                        tot > 0 ? InnerDownloader.sizeText(tot) : "?"));
                            }
                            @Override public void onDone(boolean ok2, File out, String msg){
                                Log.i("DYP", "DL_END " + fIdx + "/" + fTotal + " ok=" + ok2 + " " + msg);
                                String name = out != null ? out.getName() : (target != null ? target.getName() : "?");
                                appLog("下载", (ok2 ? "✓ " : "✗ ") + name + "  " + msg);
                                // 失败时清掉半成品，避免空文件留在下载目录
                                if(!ok2 && target != null && target.exists() && target.length() < 1024){
                                    try{ target.delete(); }catch(Throwable t){}
                                }
                            }
                        }, ttSes, MainActivity.this);
                    }

                    if(ok) okCount++; else failCount++;
                    // 关键：必须通知系统媒体库，否则文件在磁盘上但相册看不到
                    if(ok){
                        File real = target;
                        // 走了私有目录兜底：下完搬进公共 Download（MediaStore，Android 10+ 唯一可靠方式）
                        if(!dirWritable){
                            android.net.Uri mv = ScopedStore.saveToDownloads(
                                    MainActivity.this, target, relSub, target.getName());
                            if(mv != null){
                                Log.i("DYP", "DL_SCOPED_MOVED " + target.getName() + " -> " + mv);
                                try{ target.delete(); }catch(Throwable t){}
                                try{ savedDirs.add(catDir.getAbsolutePath()); }catch(Throwable t){}
                                // MediaStore 插入后系统已建档，不需要再 scanFile
                                h.post(new Runnable(){ public void run(){
                                    progSet(1f, "(" + fIdx + "/" + fTotal + ") 完成"); }});
                                continue;
                            }
                            // MediaStore 也失败：文件留在私有目录，至少用户能在这里找到
                            Log.i("DYP", "DL_SCOPED_FAIL 保留在私有目录 " + target.getAbsolutePath());
                            DiagLog.e(DiagLog.M_PERM, "MediaStore 兜底失败，文件留在 "
                                    + target.getAbsolutePath());
                            try{ savedDirs.add(workDir.getAbsolutePath()); }catch(Throwable t){}
                            h.post(new Runnable(){ public void run(){
                                progSet(1f, "(" + fIdx + "/" + fTotal + ") 完成");
                                showHintToast("公共目录写入受限，文件已存到应用私有目录");
                            }});
                            continue;
                        }
                        // 图片下载后按真实文件头修正后缀：抖音/快手给的是 WEBP，
                        // 存成 .jpg 会让部分相册打不开。
                        real = fixImageExt(target);
                        scanMedia(real);
// 记录实际落盘目录（完整路径）
                        try{ savedDirs.add(real.getParentFile().getAbsolutePath()); }catch(Throwable t){}
                    }
                    h.post(new Runnable(){ public void run(){ progSet(1f, "(" + fIdx + "/" + fTotal + ") 完成"); }});
                }
            }catch(Throwable t){
                Log.i("DYP", "DLQ_ERR " + t);
            }finally{
                final int fo = okCount, ff = failCount;
                dlRunning = false;
                // 队列结束：停前台服务，通知栏图标消失
                try{ stopService(new android.content.Intent(MainActivity.this, DownloadService.class)); }
                catch(Throwable t){}
                // 汇总本次下载实际落盘的目录（去重，保持顺序）
                final java.util.LinkedHashSet<String> dirSet =
                        new java.util.LinkedHashSet<String>(savedDirs);
                h.post(new Runnable(){ public void run(){
                    StringBuilder sb = new StringBuilder();
                    if(ff == 0){
                        sb.append("已保存 ").append(fo).append(" 个文件");
                    } else {
                        sb.append("下载完成：").append(fo).append(" 成功，").append(ff).append(" 失败");
                    }
                    if(!dirSet.isEmpty()){
                        sb.append("\n");
                        int i = 0;
                        for(String d : dirSet){
                            if(i > 0) sb.append("\n");
                            sb.append("→ ").append(d);
                            i++;
                            if(i >= 4){   // 最多显示 4 个目录，避免刷屏
                                if(dirSet.size() > 4) sb.append("\n→ …共 ").append(dirSet.size()).append(" 个目录");
                                break;
                            }
                        }
                    }
                    s(sb.toString());
                    if(ff > 0 && isXDomain(curUrl)){
                        // X 的 CDN（video/pbs.twimg.com）在国内常被 DNS 污染，
                        // 解析能成功但下载连不上，这里明确告诉用户怎么办。
                        showHintToast("X 媒体直连失败，请开启代理后重试下载");
                    }
                    if(ff == 0 && !dirSet.isEmpty()){
                        showHintToast("已保存到 " + dirSet.iterator().next());
                    }
                    h.postDelayed(new Runnable(){ public void run(){ progHide(); }}, 1200);
                }});
                savedDirs.clear();
            }
        }}).start();
    }

    /**
     * 按真实文件头修正图片后缀。
     *
     * 抖音图集给的是 WEBP（RIFF....WEBP），快手 atlas 也是 WEBP。
     * 之前统一存成 .jpg，内容是 WEBP，部分相册（尤其老版本）打不开。
     * 这里读头 12 字节判断：
     *   FF D8 FF        → JPEG，保持 .jpg
     *   RIFF....WEBP    → WEBP，改成 .webp
     *   89 50 4E 47     → PNG，改成 .png
     * 其它（含视频、实况 .jpg）原样返回。
     */
    File fixImageExt(File f){
        if(f == null || !f.exists() || f.length() < 16) return f;
        String n = f.getName().toLowerCase();
        // 只处理以 .jpg/.jpeg 结尾的（实况照片也是 .jpg，但它是真 JPEG，头部 FF D8）
        if(!n.endsWith(".jpg") && !n.endsWith(".jpeg")) return f;
        java.io.FileInputStream in = null;
        try{
            byte[] h = new byte[12];
            in = new java.io.FileInputStream(f);
            int r = in.read(h);
            if(r < 12) return f;
            boolean isJpeg = (h[0] & 0xFF) == 0xFF && (h[1] & 0xFF) == 0xD8 && (h[2] & 0xFF) == 0xFF;
            if(isJpeg) return f;
            boolean isWebp = (h[0] == 'R' && h[1] == 'I' && h[2] == 'F' && h[3] == 'F'
                              && h[8] == 'W' && h[9] == 'E' && h[10] == 'B' && h[11] == 'P');
            if(isWebp){
                String base = f.getName();
                int dot = base.lastIndexOf('.');
                if(dot > 0) base = base.substring(0, dot);
                File nf = new File(f.getParentFile(), base + ".webp");
                if(f.renameTo(nf)){
                    Log.i("DYP", "EXT_FIX webp " + f.getName() + " -> " + nf.getName());
                    return nf;
                }
            }
            boolean isPng = (h[0] & 0xFF) == 0x89 && h[1] == 'P' && h[2] == 'N' && h[3] == 'G';
            if(isPng){
                String base = f.getName();
                int dot = base.lastIndexOf('.');
                if(dot > 0) base = base.substring(0, dot);
                File nf = new File(f.getParentFile(), base + ".png");
                if(f.renameTo(nf)){
                    Log.i("DYP", "EXT_FIX png " + f.getName() + " -> " + nf.getName());
                    return nf;
                }
            }
        }catch(Throwable t){
            Log.i("DYP", "EXT_FIX_ERR " + t);
        }finally{
            try{ if(in != null) in.close(); }catch(Throwable t){}
        }
        return f;
    }

    /**
     * 通知系统媒体库收录新文件，否则相册里看不到。
     *
     * 说明：
     *  - Android 10+ 分区存储下，MediaScannerConnection 仍是最稳的方式，
     *    它会按文件后缀自动归到 images / video / audio 集合。
     *  - 实况照片（.jpg 内含 MP4）走图片集合，华为相册能识别实况标记。
     *  - 扫描是异步的，不阻塞下载队列。
     */
    void scanMedia(final File f){
        if(f == null || !f.exists()) return;
        try{
            android.media.MediaScannerConnection.scanFile(
                getApplicationContext(),
                new String[]{ f.getAbsolutePath() },
                null,
                new android.media.MediaScannerConnection.OnScanCompletedListener(){
                    public void onScanCompleted(String path, android.net.Uri uri){
                        Log.i("DYP", "SCAN done path=" + path + " uri=" + uri);
                    }
                });
            Log.i("DYP", "SCAN request " + f.getName());
        }catch(Throwable t){
            Log.i("DYP", "SCAN_ERR " + t);
        }
    }

    /**
     * 批量补录：把下载目录里历史文件全部通知媒体库。
     * 用于修复旧版本下载后相册看不到的问题。
     *
     * 覆盖范围：
     *  - 默认目录 Download/DYParse/ 及其所有子目录
     *  - 用户在设置里自定义的四个分类目录（若不在默认目录下）
     */
    void fixMediaLibrary(){
        new Thread(new Runnable(){ public void run(){
            final java.util.List<String> paths = new java.util.ArrayList<String>();
            java.util.LinkedHashSet<String> roots = new java.util.LinkedHashSet<String>();
            // 默认根目录
            roots.add(new File(android.os.Environment.getExternalStoragePublicDirectory(
                    android.os.Environment.DIRECTORY_DOWNLOADS), "DYParse").getAbsolutePath());
            // 用户自定义的分类目录
            try{
                String[] cats = new String[]{"视频", "图片", "动图", "音频", "合集"};
                for(String c : cats){
                    String d = dlDirFor(c);
                    if(d != null && d.length() > 0) roots.add(d);
                }
            }catch(Throwable t){}

            for(String root : roots){
                collectMedia(new File(root), paths, 0);
            }
            Log.i("DYP", "FIXMEDIA found=" + paths.size());
            if(paths.isEmpty()){
                h.post(new Runnable(){ public void run(){
                    Toast.makeText(MainActivity.this, "没有找到可补录的文件", Toast.LENGTH_SHORT).show();
                }});
                return;
            }
            h.post(new Runnable(){ public void run(){
                Toast.makeText(MainActivity.this,
                    "正在补录 " + paths.size() + " 个文件…", Toast.LENGTH_SHORT).show();
            }});

            final int[] done = new int[]{0};
            final int totalN = paths.size();
            android.media.MediaScannerConnection.scanFile(
                getApplicationContext(),
                paths.toArray(new String[0]),
                null,
                new android.media.MediaScannerConnection.OnScanCompletedListener(){
                    public void onScanCompleted(String path, android.net.Uri uri){
                        int n;
                        synchronized(done){ n = ++done[0]; }
                        Log.i("DYP", "FIXMEDIA " + n + "/" + totalN + " -> " + uri);
                        if(n >= totalN){
                            h.post(new Runnable(){ public void run(){
                                Toast.makeText(MainActivity.this,
                                    "已补录 " + totalN + " 个文件，打开相册看看",
                                    Toast.LENGTH_LONG).show();
                            }});
                            appLog("媒体库", "补录完成 " + totalN + " 个文件");
                        }
                    }
                });
        }}).start();
    }

    /** 递归收集媒体文件（图片/视频/音频），限深度防死循环 */
    static void collectMedia(File dir, java.util.List<String> out, int depth){
        if(dir == null || depth > 4 || !dir.isDirectory()) return;
        File[] fs = dir.listFiles();
        if(fs == null) return;
        for(File f : fs){
            if(f.isDirectory()){
                collectMedia(f, out, depth + 1);
            } else {
                String n = f.getName().toLowerCase();
                if(n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png")
                   || n.endsWith(".webp") || n.endsWith(".mp4") || n.endsWith(".mp3")
                   || n.endsWith(".m4a") || n.endsWith(".gif")){
                    if(f.length() > 1024) out.add(f.getAbsolutePath());
                }
            }
        }
    }

    String extractRouterData(String html){
        if(html==null) return null;
        int p=html.indexOf("_ROUTER_DATA");
        if(p<0) return null;
        int b=html.indexOf('{',p);
        if(b<0) return null;
        int depth=0; boolean instr=false; boolean esc=false;
        for(int i=b;i<html.length();i++){
            char c=html.charAt(i);
            if(instr){ if(esc) esc=false; else if(c=='\\') esc=true; else if(c=='"') instr=false; }
            else { if(c=='"') instr=true; else if(c=='{') depth++; else if(c=='}'){ depth--; if(depth==0) return html.substring(b,i+1); } }
        }
        return null;
    }

    static class JP {
        String s; int i;
        JP(String s){ this.s=s; this.i=0; }
        Object parse(){ skip(); return val(); }
        void skip(){ while(i<s.length() && Character.isWhitespace(s.charAt(i))) i++; }
        Object val(){
            skip(); char c=s.charAt(i);
            if(c=='{') return obj();
            if(c=='[') return arr();
            if(c=='"') return str();
            if(c=='t'){ i+=4; return Boolean.TRUE; }
            if(c=='f'){ i+=5; return Boolean.FALSE; }
            if(c=='n'){ i+=4; return null; }
            return num();
        }
        Map obj(){ Map m=new HashMap(); i++; skip(); if(s.charAt(i)=='}'){ i++; return m; } while(true){ skip(); String k=str(); skip(); i++; Object v=val(); m.put(k,v); skip(); char c=s.charAt(i); i++; if(c=='}') break; } return m; }
        List arr(){ List l=new ArrayList(); i++; skip(); if(s.charAt(i)==']'){ i++; return l; } while(true){ Object v=val(); l.add(v); skip(); char c=s.charAt(i); i++; if(c==']') break; } return l; }
        String str(){ StringBuilder b=new StringBuilder(); i++; while(true){ char c=s.charAt(i++); if(c=='"') break; if(c=='\\'){ char e=s.charAt(i++); switch(e){ case 'n': b.append('\n'); break; case 't': b.append('\t'); break; case 'r': b.append('\r'); break; case 'u': String hx=s.substring(i,i+4); b.append((char)Integer.parseInt(hx,16)); i+=4; break; default: b.append(e); } } else b.append(c); } return b.toString(); }
        Object num(){ int st=i; while(i<s.length() && "-+.eE0123456789".indexOf(s.charAt(i))>=0) i++; String t=s.substring(st,i); try{ return Double.valueOf(t); }catch(Exception e){ return t; } }
    }
    Object get(Object o,String k){ if(o instanceof Map){ return ((Map)o).get(k); } return null; }
    String gs(Object o){ return o==null?"":o.toString(); }
    Object findKey(Object o,String k){
        if(o instanceof Map){ Map m=(Map)o; if(m.containsKey(k)) return m.get(k); for(Object v:m.values()){ Object r=findKey(v,k); if(r!=null) return r; } }
        else if(o instanceof List){ for(Object v:(List)o){ Object r=findKey(v,k); if(r!=null) return r; } }
        return null;
    }
    String firstUrl(Object l){
        if(l instanceof List){ for(Object o:(List)l){ if(o instanceof String) return (String)o; if(o instanceof Map){ Object u=((Map)o).get("url"); if(u!=null) return u.toString(); } } }
        return "?";
    }
    String num(Object o){ if(o instanceof Double) return String.valueOf(((Double)o).longValue()); return gs(o); }

    String formatNode(Object root){
        if(root==null) return "";
        Object a=root;
        if(!(a instanceof Map)){
            Object ad=findKey(root,"aweme_detail");
            if(ad instanceof Map) a=ad;
        }
        if(!(a instanceof Map)){
            Object il=findKey(root,"item_list");
            if(il instanceof List && !((List)il).isEmpty()) a=(Map)((List)il).get(0);
        }
        // slidesinfo 返回的是 aweme_details / aweme_list
        if(!(a instanceof Map)){
            Object al=findKey(root,"aweme_details");
            if(al instanceof List && !((List)al).isEmpty()) a=(Map)((List)al).get(0);
        }
        if(!(a instanceof Map)){
            Object al=findKey(root,"aweme_list");
            if(al instanceof List && !((List)al).isEmpty()) a=(Map)((List)al).get(0);
        }
        if(!(a instanceof Map)) return "";
        Map am=(Map)a;
        if(get(am,"aweme_id")==null && get(am,"desc")==null) return "";
        StringBuilder b=new StringBuilder();
        b.append("【标题】").append(gs(get(am,"desc"))).append("\n");
        Map au=(Map)get(am,"author");
        if(au!=null) b.append("【作者】").append(gs(get(au,"nickname"))).append("  @").append(gs(get(au,"unique_id"))).append("\n");
        b.append("【作品ID】").append(gs(get(am,"aweme_id"))).append("\n");
        Map vd=(Map)get(am,"video");
        if(vd!=null){
            Map pa=(Map)get(vd,"play_addr");
            if(pa!=null){
                String uri=gs(get(pa,"uri"));
                b.append("【带水印地址】").append(firstUrl(get(pa,"url_list"))).append("\n");
                if(uri.length()>0) b.append("【无水印直链】https://aweme.snssdk.com/aweme/v1/play/?video_id="+uri+"&ratio=1080p&line=0\n");
            }
            Map cov=(Map)get(vd,"cover");
            if(cov!=null) b.append("【封面】").append(firstUrl(get(cov,"url_list"))).append("\n");
            Map dcov=(Map)get(vd,"dynamic_cover");
            if(dcov!=null) b.append("【动态封面/动图】").append(firstUrl(get(dcov,"url_list"))).append("\n");
            Map orig=(Map)get(vd,"origin_cover");
            if(orig!=null) b.append("【原封面】").append(firstUrl(get(orig,"url_list"))).append("\n");
            Object dur=get(vd,"duration");
            if(dur instanceof Double) b.append("【时长】").append(((Double)dur).longValue()/1000).append("s\n");
        }
        Object imgs=get(am,"images");
        if(imgs instanceof List && !((List)imgs).isEmpty()){
            b.append("【类型】图文/动图, 共").append(((List)imgs).size()).append("张\n");
            int idx=1;
            for(Object o:(List)imgs){
                b.append("  图").append(idx).append(": ").append(firstUrl(get((Map)o,"url_list")));
                Object dvo=get((Map)o,"video");
                if(dvo instanceof Map){ Object dpa=get((Map)dvo,"play_addr"); if(dpa!=null) b.append("  [动图]").append(firstUrl(get((Map)dpa,"url_list"))); }
                b.append("\n"); idx++;
            }
        } else if(vd!=null){ b.append("【类型】视频\n"); }
        Map st=(Map)get(am,"statistics");
        if(st!=null) b.append("【数据】赞").append(num(get(st,"digg_count"))).append(" 评").append(num(get(st,"comment_count"))).append(" 转").append(num(get(st,"share_count"))).append(" 藏").append(num(get(st,"collect_count"))).append("\n");
        Object ct=get(am,"create_time");
        if(ct instanceof Double) b.append("【发布时间】").append(new java.util.Date(((Double)ct).longValue()*1000)).append("\n");
        return b.toString();
    }
}