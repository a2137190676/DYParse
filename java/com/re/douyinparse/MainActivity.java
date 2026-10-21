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
    /** 快手：分享页给的独立音轨 m4a，非空时优先用它转 MP3 */
    String curMusic="";
    String curShowImg="";
    // ===== 主题与关于 =====
    TextView btnTheme;
    boolean curDark = false;
    String curResult = "";
    static final String SP = "dyp_pref";
    static final String KEY_DARK = "dark_mode";
    static final String KEY_PROXY = "proxy_url";      // 形如 http://host:port 或 socks5://host:port
    static final String KEY_PROXY_ON = "proxy_on";
    static final String KEY_PROXY_TT_ONLY = "proxy_tt_only";   // 仅 TikTok 走代理
    /** 抖音访客凭据 UIFID_TEMP：由真实浏览器通过挑战后签发，缺失时 detail 接口一律 403 */
    static final String KEY_UIFID = "dy_uifid_temp";
    /** 自建服务器地址（可配置）：用户自填值 > 内置默认 > 本地/Gitee 回退 */
    static final String KEY_DYP_SRV = "dyp_srv";
    /** 用户点了「下个版本再提醒」：记下跳过的版本号，该版本及以下不再弹窗 */
    static final String KEY_SKIP_VER = "skip_ver";
    /** 强制更新：version.json 里带 "force":1 或 "min_version"，低于 min_version 必须更新 */
    static final String KEY_FORCE_MIN = "force_min_ver";
    /** 分类下载路径（空 = 用默认 Download/DYParse/<分类>） */
    static final String KEY_DL_VIDEO = "dl_video";
    static final String KEY_DL_IMG   = "dl_img";
    static final String KEY_DL_AUDIO = "dl_audio";
    static final String KEY_DL_MIX   = "dl_mix";
    /** 动图（实况 GIF / WebP 动图）单独目录 */
    static final String KEY_DL_ANIM  = "dl_anim";
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

    /** 依据「仅 TikTok」开关决定这条 URL 要不要走代理 */
    static boolean proxyFor(String url, SharedPreferences p){
        if(p == null) return true;
        if(!p.getBoolean(KEY_PROXY_TT_ONLY, false)) return true;   // 全局模式：都走
        return isTikTokUrl(url);
    }

    /** 读代理配置：返回 null 表示不用代理 */
    Proxy curProxy(){
        return curProxyFor(null);
    }

    /** 按 URL 取代理：url 为 null 时只做全局判断 */
    Proxy curProxyFor(String url){
        try{
            SharedPreferences p = getSharedPreferences(SP, MODE_PRIVATE);
            if(!p.getBoolean(KEY_PROXY_ON, false)) return null;
            if(url != null && !proxyFor(url, p)) return null;
            return parseProxy(p.getString(KEY_PROXY, ""));
        }catch(Throwable t){ return null; }
    }

    /** 解析 "http://1.2.3.4:8080" / "socks5://1.2.3.4:1080" / "1.2.3.4:8080" */
    static Proxy parseProxy(String s){
        if(s == null) return null;
        s = s.trim();
        if(s.length() == 0) return null;
        boolean socks = false;
        if(s.startsWith("socks5://")){ socks = true; s = s.substring(9); }
        else if(s.startsWith("socks://")){ socks = true; s = s.substring(8); }
        else if(s.startsWith("http://")){ s = s.substring(7); }
        else if(s.startsWith("https://")){ s = s.substring(8); }
        int c = s.lastIndexOf(':');
        if(c <= 0 || c == s.length() - 1) return null;
        String host = s.substring(0, c);
        String portS = s.substring(c + 1);
        int port;
        try{ port = Integer.parseInt(portS.trim()); }catch(Throwable t){ return null; }
        if(port <= 0 || port > 65535) return null;
        return new Proxy(socks ? Proxy.Type.SOCKS : Proxy.Type.HTTP,
                         InetSocketAddress.createUnresolved(host, port));
    }

    /** 静态版代理读取（给静态工具方法用）；url 非空时按「仅 TikTok」开关过滤 */
    static Proxy staticProxy(Context ctx){
        return staticProxyFor(ctx, null);
    }

    static Proxy staticProxyFor(Context ctx, String url){
        try{
            SharedPreferences p = ctx.getSharedPreferences(SP, MODE_PRIVATE);
            if(!p.getBoolean(KEY_PROXY_ON, false)) return null;
            if(url != null && !proxyFor(url, p)) return null;
            return parseProxy(p.getString(KEY_PROXY, ""));
        }catch(Throwable t){ return null; }
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
    Bundle savedState;

    java.util.List<String> imgUrls=new java.util.ArrayList<String>();
    /** 实况照片：与 imgUrls 一一对应的短视频地址，空串表示该图不是实况 */
    java.util.List<String> dynUrls=new java.util.ArrayList<String>();
    /** 所有图片都是实况（下载时可整体转 mp4） */
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
        Log.i("DYP", "SRV_DEBUG addr=[" + SrvSecret.serverAddr() + "] hmacLen=" + SrvSecret.hmacKey().length());
        appLog("启动", "v" + versionName() + " (build " + curVersionCode() + ") 内置服务器=" + (SrvSecret.serverAddr().length() > 0 ? "ON" : "OFF"));
        setContentView(R.layout.activity_main);
        // 启动动画：盖在内容之上，620ms 后自动移除；不影响解析流程
        if(b == null){
            try{ SplashOverlay.show(this, isDarkMode(), null); }catch(Throwable t){}
        }
        input=(EditText)findViewById(R.id.input);
        btn=(TextView)findViewById(R.id.btn);
        status=(TextView)findViewById(R.id.status);
        out=(LinearLayout)findViewById(R.id.out);
        // WebView 改为懒加载：抖音解析时才初始化，避免拖慢冷启动
        web=(WebView)findViewById(R.id.web);
        actions=(LinearLayout)findViewById(R.id.actions);
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
        // 「设置」：功能入口 + 代理 + 服务器 + 分类下载路径
        View bs = findViewById(R.id.btnSettings);
        if(bs != null) bs.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ showSettings(); }});
        btnTheme=(TextView)findViewById(R.id.btnTheme);
        if(btnTheme != null){
            btnTheme.setText(isDarkMode() ? "亮" : "暗");
            btnTheme.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){
                boolean d = !isDarkMode();
                applyTheme(d);
                btnTheme.setText(d ? "亮" : "暗");
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
            }
            public boolean shouldOverrideUrlLoading(WebView v,String u){
                if(u==null) return false;
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
                }catch(Throwable t){}
                return null;
            }
            public void onPageFinished(WebView v,String u){
                pageHits++;
                Log.i("DYP","onPageFinished#"+pageHits+" "+u);
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
    volatile boolean mixMode = false;
    /** 合集视频列表：每项 [awemeId, 描述, 无水印地址, 封面] */
    final java.util.List<String[]> mixItems = new java.util.ArrayList<String[]>();
    String mixTitle = "", mixId = "";

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

    /** 把合集列表渲染成卡片 + 批量下载按钮 */
    void renderMixList(final int n){
        mixMode = true;
        h.post(new Runnable(){ public void run(){
            try{
                webDone = true;
                if(web != null) web.setVisibility(View.GONE);
                out.removeAllViews();
                actions.setVisibility(View.GONE);
                img.setVisibility(View.GONE);
                videoBox.setVisibility(View.GONE);

                LinearLayout head = new LinearLayout(MainActivity.this);
                head.setOrientation(LinearLayout.VERTICAL);
                head.setBackground(makeRoundRect(curDark ? C_DARK_SURFACE : C_LIGHT_SURFACE,
                        curDark ? C_DARK_STROKE : C_LIGHT_STROKE, 16));
                head.setPadding(dp(16), dp(14), dp(16), dp(14));

                TextView t = new TextView(MainActivity.this);
                t.setText("合集 · " + n + " 个作品");
                t.setTextSize(15);
                t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                t.setTextColor(0xFFFE2C55);
                head.addView(t);

                TextView sub = new TextView(MainActivity.this);
                sub.setText("点下方按钮可一次性下载全部视频");
                sub.setTextSize(12);
                sub.setTextColor(curDark ? C_DARK_SUB : C_LIGHT_SUB);
                sub.setPadding(0, dp(6), 0, dp(10));
                head.addView(sub);

                TextView dlAll = new TextView(MainActivity.this);
                dlAll.setText("批量下载全部（" + n + "）");
                dlAll.setTextSize(14);
                dlAll.setGravity(android.view.Gravity.CENTER);
                dlAll.setTextColor(0xFFFFFFFF);
                dlAll.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                dlAll.setBackground(makeRoundRect(0xFFFE2C55, 0, 12));
                dlAll.setPadding(0, dp(12), 0, dp(12));
                dlAll.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ downloadAllMix(); }});
                head.addView(dlAll);

                LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                hlp.bottomMargin = dp(10);
                head.setLayoutParams(hlp);
                out.addView(head);

                java.util.List<String[]> snapshot;
                synchronized(mixItems){ snapshot = new java.util.ArrayList<String[]>(mixItems); }
                for(int i = 0; i < snapshot.size(); i++){
                    final String[] it = snapshot.get(i);
                    final int idx = i;
                    LinearLayout card = new LinearLayout(MainActivity.this);
                    card.setOrientation(LinearLayout.VERTICAL);
                    card.setBackground(makeRoundRect(curDark ? C_DARK_SURFACE : C_LIGHT_SURFACE,
                            curDark ? C_DARK_STROKE : C_LIGHT_STROKE, 14));
                    card.setPadding(dp(14), dp(12), dp(14), dp(12));
                    LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                    clp.bottomMargin = dp(8);
                    card.setLayoutParams(clp);

                    TextView no = new TextView(MainActivity.this);
                    String d = it[1] != null ? it[1] : "";
                    if(d.length() > 60) d = d.substring(0, 60) + "…";
                    no.setText((i + 1) + ". " + (d.length() > 0 ? d : (it[0])));
                    no.setTextSize(13);
                    no.setTextColor(curDark ? C_DARK_TEXT : C_LIGHT_TEXT);
                    no.setLineSpacing(dp(3), 1f);
                    card.addView(no);

                    LinearLayout btnRow = new LinearLayout(MainActivity.this);
                    btnRow.setOrientation(LinearLayout.HORIZONTAL);
                    btnRow.setPadding(0, dp(8), 0, 0);

                    TextView one = new TextView(MainActivity.this);
                    one.setText("下载这条");
                    one.setTextSize(12);
                    one.setTextColor(curDark ? C_DARK_SUB : C_LIGHT_SUB);
                    one.setBackground(makeRoundRect(curDark ? C_DARK_ALT : C_LIGHT_ALT, 0, 8));
                    one.setPadding(dp(10), dp(5), dp(10), dp(5));
                    one.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){
                        if(it[2] != null && it[2].length() > 0){
                            // 合集单条：进 合集/标题_时间戳 专属文件夹
                            String t = mixTitle.length() > 0 ? mixTitle : curId;
                            String mixDir = t + "_" + System.currentTimeMillis();
                            enqueueDownload(it[2], "合集" + (idx + 1), "mp4", "合集", mixDir);
                        } else s("该条无视频地址");
                    }});
                    btnRow.addView(one);

                    TextView playBtn = new TextView(MainActivity.this);
                    playBtn.setText("播放");
                    playBtn.setTextSize(12);
                    playBtn.setTextColor(curDark ? C_DARK_SUB : C_LIGHT_SUB);
                    LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                    plp.leftMargin = dp(8);
                    playBtn.setLayoutParams(plp);
                    playBtn.setBackground(makeRoundRect(curDark ? C_DARK_ALT : C_LIGHT_ALT, 0, 8));
                    playBtn.setPadding(dp(10), dp(5), dp(10), dp(5));
                    playBtn.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){
                        if(it[2] == null || it[2].length() == 0){ s("该条无视频地址"); return; }
                        curPlay = it[2];
                        curId = it[0];
                        curImgs = "";
                        startedUrl = "";
                        actions.setVisibility(View.VISIBLE);
                        videoBox.setVisibility(View.VISIBLE);
                        img.setVisibility(View.GONE);
                        doPlay();
                    }});
                    btnRow.addView(playBtn);

                    card.addView(btnRow);
                    out.addView(card);
                }
                s("合集解析完成：" + n + " 个作品");
            }catch(Throwable t){
                Log.i("DYP", "MIX_RENDER_ERR " + t);
            }
        }});
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

        new android.app.AlertDialog.Builder(this)
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
            String body = httpGetText(api, 9000, 11000);
            if(body != null){
                tag = jget(body, "tag_name");
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
                Log.i("DYP", "UPDATE_SRC api(" + (useGitee ? "gitee" : "github") + ") tag=" + tag);
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
                String js = httpGetText(curStaticUrl(MainActivity.this), 9000, 11000);
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
        new android.app.AlertDialog.Builder(this)
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
        final String[] labels = {"Gitee（国内快，推荐）", "GitHub（国际，可能需代理）"};
        int cur = isGitee(this) ? 0 : 1;
        new android.app.AlertDialog.Builder(this)
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
                    checkUpdate(false);
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

    /** tag \u8f6c\u6570\u5b57\uff1av1.2.3 -> 10203\uff08\u4e24\u4f4d\u4e00\u6bb5\uff09 */
    static int parseVer(String tag){
        if(tag == null) return 0;
        String t = tag.trim().toLowerCase();
        if(t.startsWith("v")) t = t.substring(1);
        String[] parts = t.split("[.]");
        int major = 0, minor = 0, patch = 0;
        try{ if(parts.length > 0) major = Integer.parseInt(parts[0].replaceAll("[^0-9]", "")); }catch(Throwable e){}
        try{ if(parts.length > 1) minor = Integer.parseInt(parts[1].replaceAll("[^0-9]", "")); }catch(Throwable e){}
        try{ if(parts.length > 2) patch = Integer.parseInt(parts[2].replaceAll("[^0-9]", "")); }catch(Throwable e){}
        return major * 10000 + minor * 100 + patch;
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

        android.app.AlertDialog.Builder b = new android.app.AlertDialog.Builder(this)
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
                progSet(1f, "下载完成，正在安装");
                s("下载完成，正在安装...");
                h.post(new Runnable(){ public void run(){
                    showHintToast("下载完成，正在拉起安装器");
                }});
                h.postDelayed(new Runnable(){ public void run(){
                    progHide();
                    installApk();
                }}, 700);
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
        new android.app.AlertDialog.Builder(this)
            .setTitle("需要安装权限")
            .setMessage("系统尚未允许本应用安装 APK。\n\n"
                + "请在接下来的页面里找到「DY解析器」，打开「允许安装未知应用」，然后返回重试。\n\n"
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

        android.app.AlertDialog dlg = new android.app.AlertDialog.Builder(this)
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

        android.app.AlertDialog.Builder b = new android.app.AlertDialog.Builder(this)
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
            "【v1.3.3】实况照片 + 动图目录\n"
          + "新增\n"
          + "· 实况照片封装：抖音实况存成真·Motion Photo，相册长按可播\n"
          + "· 「动图」下载目录：实况单独存放，路径可自定义\n"
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

    /** 平台支持说明：三个平台各自怎么解析、需要什么条件 */
    void showPlatformHelp(){
        String txt =
            "【抖音】\n"
          + "方式：WebView 加载分享页，注入 JS 拦截 fetch/XHR 里的 aweme_detail。\n"
          + "备选：解析页面 SSR 的 window._ROUTER_DATA。\n"
          + "条件：无需登录。抖音有 a_bogus / X-Bogus 签名风控，所以走真实浏览器让它自己算签名。\n"
          + "支持：视频、图集、动图、合集、短剧合集。\n\n"
          + "【快手】\n"
          + "方式：纯 HTTP，不依赖 WebView。短链 302 到 v.m.chenzhongtech.com 后，\n"
          + "　　　解析页面里的 window.INIT_STATE。\n"
          + "要点：INIT_STATE 的顶层 key 被逐字符 -1 混淆（tusjoh → string，qipup → photo），\n"
          + "　　　但 value 全是明文，所以按结构递归找「有 photoId + manifest」的节点，不认 key 名。\n"
          + "条件：无需登录，无需签名。视频直链本身无水印。\n"
          + "音频：分享页额外给一条独立音轨 m4a（soundTrack），转 MP3 比抽视频音轨快得多。\n\n"
          + "【TikTok】\n"
          + "方式：纯 HTTP + 自管 Cookie 会话，两条页面模板互为兜底。\n"
          + "　模板 A（@用户名/video/ID）：__UNIVERSAL_DATA_FOR_REHYDRATION__\n"
          + "　　　→ __DEFAULT_SCOPE__ / webapp.video-detail 或 webapp.reflow.video.detail\n"
          + "　模板 B（@i/video/ID，@i 是占位用户名）：<script id=\"api-data\">\n"
          + "　　　→ {\"videoDetail\":...}\n"
          + "要点：模板 A 有时会返回 HTTP 400，自动切模板 B 兜底。\n"
          + "关键：playAddr 里带 tk=tt_chain_token，CDN 会拿它比对请求头的 Cookie。\n"
          + "　　　页面和视频必须来自同一次会话，否则一律 403。\n"
          + "下载：两套 CDN 行为相反 ——\n"
          + "　　　*.tiktokcdn.com 直连（走代理反而 SSL 失败）\n"
          + "　　　*v-webapp-prime.tiktok.com 走代理\n"
          + "　　　程序按 host 自动选，失败自动换另一种。\n"
          + "条件：需要能访问 TikTok 的网络环境。若直连不通，请在「代理设置」填境外代理。\n\n"
          + "【通用】\n"
          + "· 只解析公开分享页，不解密任何加密内容，不存储账号信息。\n"
          + "· 音频统一转 192kbps MP3，存到 Download/DYParse/音频。\n"
          + "· 文件名前缀区分来源：dy_（抖音）/ ks_（快手）/ tt_（TikTok）。\n"
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

        android.app.AlertDialog dlg = new android.app.AlertDialog.Builder(this)
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

    /** 当前这条 URL 的代理指纹：开关/地址/仅TT 任一变化都要重建播放器 */
    String proxyCacheKey(String url){
        try{
            SharedPreferences p = getSharedPreferences(SP, MODE_PRIVATE);
            boolean on = p.getBoolean(KEY_PROXY_ON, false);
            String addr = p.getString(KEY_PROXY, "");
            boolean ttOnly = p.getBoolean(KEY_PROXY_TT_ONLY, false);
            boolean applies = on && (!ttOnly || isTikTokUrl(url));
            return on + "|" + applies + "|" + addr;
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
        final android.app.AlertDialog dlg = new android.app.AlertDialog.Builder(this)
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
            + "获取方式（必须真实浏览器，App 内抓不到，原因见下方说明）：\n"
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

        final android.app.AlertDialog dlg = new android.app.AlertDialog.Builder(this)
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

    /** 代理设置弹窗 */
    void showProxyDialog(){
        // 代理设置已并入「设置」弹窗
        showSettings();
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

    /** 统一设置弹窗：代理 + 服务器 + 分类下载路径 */
    void showSettings(){
        SharedPreferences p = getSharedPreferences(SP, MODE_PRIVATE);
        String cur = p.getString(KEY_PROXY, "");
        boolean on = p.getBoolean(KEY_PROXY_ON, false);
        boolean ttOnly = p.getBoolean(KEY_PROXY_TT_ONLY, true);

        int bgDlg     = curDark ? 0xFF17191F : 0xFFFFFFFF;
        int textDlg   = curDark ? 0xFFE6E8EE : 0xFF1A1A1F;
        int subDlg    = curDark ? 0xFF9AA0AC : 0xFF6B6F7A;

        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setBackgroundColor(bgDlg);
        wrap.setPadding(dp(20), dp(16), dp(20), dp(12));

        // ===== 独立入口区 =====
        TextView miscHead = new TextView(this);
        miscHead.setText("── 功能入口 ──");
        miscHead.setTextSize(13);
        miscHead.setTextColor(0xFFFE2C55);
        miscHead.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        wrap.addView(miscHead);

        wrap.addView(settingRow("平台支持说明", "抖音 / 快手 / TikTok 解析原理",
            new View.OnClickListener(){ public void onClick(View v){ showPlatformHelp(); }}));
        wrap.addView(settingRow("更新日志", "每个版本改了什么",
            new View.OnClickListener(){ public void onClick(View v){ showChangelog(); }}));
        wrap.addView(settingRow("运行日志", "下载 / 解析 / 报错记录",
            new View.OnClickListener(){ public void onClick(View v){ showLogViewer(); }}));
        wrap.addView(settingRow("关于", "版本信息与免责声明",
            new View.OnClickListener(){ public void onClick(View v){ showAbout(); }}));
wrap.addView(settingRow("检查更新", "手动检查新版本",
             new View.OnClickListener(){ public void onClick(View v){ checkUpdate(false); }}));
        wrap.addView(settingRow("更新源：" + platformName(), "点此切换 Gitee / GitHub",
             new View.OnClickListener(){ public void onClick(View v){ showUpdSrcDialog(); }}));
        wrap.addView(settingRow("反馈群 " + APP_QQ_GROUP, "点击加入 QQ 群",
            new View.OnClickListener(){ public void onClick(View v){
                try{
                    startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW,
                        Uri.parse(APP_QQ_GROUP_URL)));
                }catch(Throwable t){
                    s("无法打开浏览器");
                }
            }}));
        // 访客凭据：始终显示。有服务器时说明「可不填」，但保留手动入口，
        // 用户想自己抓/自己填随时能进。
        String effSrv = getSharedPreferences(SP, MODE_PRIVATE).getString(KEY_DYP_SRV, DYP_SRV_DEFAULT);
        boolean hasSrv = effSrv != null && effSrv.length() > 0;
        String uifidNow = getSharedPreferences(SP, MODE_PRIVATE).getString(KEY_UIFID, "");
        boolean hasUifid = uifidNow != null && uifidNow.length() >= 32;
        wrap.addView(settingRow("抖音访客凭据",
            hasUifid ? "已填写（实况照片可用）"
                     : (hasSrv ? "服务器池代签；也可自己填" : "实况照片必需"),
            new View.OnClickListener(){ public void onClick(View v){ showUifidDialog(); }}));

        // ===== 代理区 =====
        TextView pxHead = new TextView(this);
        pxHead.setText("── 代理 ──");
        pxHead.setTextSize(13);
        pxHead.setTextColor(curDark ? 0xFFFE2C55 : 0xFFFE2C55);
        pxHead.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        wrap.addView(pxHead);

        TextView tip = new TextView(this);
        tip.setText("支持格式：http://1.2.3.4:8080 / socks5://1.2.3.4:1080");
        tip.setTextSize(12);
        tip.setTextColor(subDlg);
        tip.setLineSpacing(dp(3), 1f);
        wrap.addView(tip);

        final EditText et = new EditText(this);
        et.setText(cur);
        et.setHint("http://host:port");
        et.setTextSize(14);
        et.setTextColor(textDlg);
        et.setHintTextColor(subDlg);
        et.setSingleLine(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(10);
        wrap.addView(et, lp);

        final CheckBox cb = new CheckBox(this);
        cb.setText("启用代理");
        cb.setChecked(on);
        cb.setTextColor(textDlg);
        cb.setTextSize(13);
        wrap.addView(cb);

        final CheckBox cbTT = new CheckBox(this);
        cbTT.setText("仅 TikTok 走代理（抖音直连更快）");
        cbTT.setChecked(ttOnly);
        cbTT.setTextColor(textDlg);
        cbTT.setTextSize(13);
        wrap.addView(cbTT);

        // ===== 服务器区 =====
        TextView srvHead = new TextView(this);
        srvHead.setText("── 服务器 ──");
        srvHead.setTextSize(13);
        srvHead.setTextColor(0xFFFE2C55);
        srvHead.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        srvHead.setPadding(0, dp(14), 0, 0);
        wrap.addView(srvHead);

        TextView srvTip = new TextView(this);
        srvTip.setText("用于自更新 / 抖音实况中转 / 短链解析 / TikTok 加速。\n默认使用内置服务器；留空恢复内置地址。\n自建就填你的地址：http://1.2.3.4:8080");
        srvTip.setTextSize(12);
        srvTip.setTextColor(subDlg);
        srvTip.setLineSpacing(dp(3), 1f);
        wrap.addView(srvTip);

        // 机场流量状态（点击刷新；服务器不可用时提示走本地代理）
        final TextView tvAir = new TextView(this);
        tvAir.setText("机场流量：点击查询");
        tvAir.setTextSize(13);
        tvAir.setTextColor(curDark ? 0xFF8AB4F8 : 0xFF1A73E8);
        tvAir.setPadding(0, dp(8), 0, dp(4));
        tvAir.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){
            loadAirInfo(tvAir);
        }});
        wrap.addView(tvAir);
        loadAirInfo(tvAir);

        final EditText etSrv = new EditText(this);
        // 用户没自填过（用内置）时不显示内置地址，只提示"已启用"；
        // 自填过的显示他填的地址。内置地址不暴露给用户/抓屏者。
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
        lpSrv.topMargin = dp(8);
        wrap.addView(etSrv, lpSrv);

        // 媒体库修复：历史下载的文件没进相册的，一键补录
        wrap.addView(settingRow("修复媒体库", "相册里看不到已下载的文件？点这里补录",
            new View.OnClickListener(){ public void onClick(View v){ fixMediaLibrary(); }}));

        // ===== 下载路径区 =====
        TextView dlHead = new TextView(this);
        dlHead.setText("── 下载路径 ──");
        dlHead.setTextSize(13);
        dlHead.setTextColor(0xFFFE2C55);
        dlHead.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        dlHead.setPadding(0, dp(14), 0, 0);
        wrap.addView(dlHead);

        TextView dlTip = new TextView(this);
        dlTip.setText("每类可单独自定义，留空 = 默认 Download/DYParse/<分类>。\n合集会在所选目录下再建「标题_时间」子文件夹。");
        dlTip.setTextSize(12);
        dlTip.setTextColor(subDlg);
        dlTip.setLineSpacing(dp(3), 1f);
        wrap.addView(dlTip);

        final EditText etDlRoot = new EditText(this);
        etDlRoot.setHint("根目录（默认 Download/DYParse）");
        etDlRoot.setText(p.getString(KEY_DL_PATH, ""));
        etDlRoot.setTextSize(14);
        etDlRoot.setTextColor(textDlg);
        etDlRoot.setHintTextColor(subDlg);
        etDlRoot.setSingleLine(true);
        LinearLayout.LayoutParams lpDl0 = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpDl0.topMargin = dp(8);
        wrap.addView(etDlRoot, lpDl0);

        final EditText etDlVideo = new EditText(this);
        etDlVideo.setHint("视频目录（默认 <根目录>/视频）");
        etDlVideo.setText(p.getString(KEY_DL_VIDEO, ""));
        etDlVideo.setTextSize(14);
        etDlVideo.setTextColor(textDlg);
        etDlVideo.setHintTextColor(subDlg);
        etDlVideo.setSingleLine(true);
        LinearLayout.LayoutParams lpDlV = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpDlV.topMargin = dp(6);
        wrap.addView(etDlVideo, lpDlV);

        final EditText etDlImg = new EditText(this);
        etDlImg.setHint("图片目录（默认 <根目录>/图片）");
        etDlImg.setText(p.getString(KEY_DL_IMG, ""));
        etDlImg.setTextSize(14);
        etDlImg.setTextColor(textDlg);
        etDlImg.setHintTextColor(subDlg);
        etDlImg.setSingleLine(true);
        wrap.addView(etDlImg, lpDlV);

        final EditText etDlAnim = new EditText(this);
        etDlAnim.setHint("动图目录（默认 <根目录>/动图）");
        etDlAnim.setText(p.getString(KEY_DL_ANIM, ""));
        etDlAnim.setTextSize(14);
        etDlAnim.setTextColor(textDlg);
        etDlAnim.setHintTextColor(subDlg);
        etDlAnim.setSingleLine(true);
        wrap.addView(etDlAnim, lpDlV);

        final EditText etDlAudio = new EditText(this);
        etDlAudio.setHint("音频目录（默认 <根目录>/音频）");
        etDlAudio.setText(p.getString(KEY_DL_AUDIO, ""));
        etDlAudio.setTextSize(14);
        etDlAudio.setTextColor(textDlg);
        etDlAudio.setHintTextColor(subDlg);
        etDlAudio.setSingleLine(true);
        wrap.addView(etDlAudio, lpDlV);

        final EditText etDlMix = new EditText(this);
        etDlMix.setHint("合集目录（默认 <根目录>/合集）");
        etDlMix.setText(p.getString(KEY_DL_MIX, ""));
        etDlMix.setTextSize(14);
        etDlMix.setTextColor(textDlg);
        etDlMix.setHintTextColor(subDlg);
        etDlMix.setSingleLine(true);
        wrap.addView(etDlMix, lpDlV);

        final TextView testRes = new TextView(this);
        testRes.setTextSize(12);
        testRes.setTextColor(subDlg);
        testRes.setPadding(0, dp(8), 0, 0);
        wrap.addView(testRes);

        TextView btnTest = new TextView(this);
        btnTest.setText("测试代理连通性");
        btnTest.setTextSize(13);
        btnTest.setTextColor(curDark ? 0xFF6FA8FF : 0xFF2F6FEB);
        btnTest.setPadding(0, dp(10), 0, 0);
        wrap.addView(btnTest);

        TextView btnNet = new TextView(this);
        btnNet.setText("节点体检（判断能否拿到视频）");
        btnNet.setTextSize(13);
        btnNet.setTextColor(curDark ? 0xFF6FA8FF : 0xFF2F6FEB);
        btnNet.setPadding(0, dp(10), 0, 0);
        wrap.addView(btnNet);
        btnNet.setOnClickListener(new View.OnClickListener(){ public void onClick(View vv){
            showNetCheck();
        }});

        android.app.AlertDialog dlg = new android.app.AlertDialog.Builder(this)
            .setTitle("设置")
            .setView(new android.widget.ScrollView(this){{ addView(wrap); }})
            .setPositiveButton("保存", null)
            .setNegativeButton("取消", null)
            .create();

        btnTest.setOnClickListener(new View.OnClickListener(){ public void onClick(View vv){
            final String s = et.getText().toString().trim();
            final Proxy px = parseProxy(s);
            if(px == null){ testRes.setText("格式不对，例：http://1.2.3.4:8080"); return; }
            testRes.setText("测试中…");
            new Thread(new Runnable(){ public void run(){
                String msg;
                long t0 = System.currentTimeMillis();
                try{
                    java.net.HttpURLConnection c = (java.net.HttpURLConnection)
                        new URL("https://www.tiktok.com/").openConnection(px);
                    c.setRequestProperty("User-Agent", MUA);
                    c.setConnectTimeout(12000); c.setReadTimeout(12000);
                    int code = c.getResponseCode();
                    long ms = System.currentTimeMillis() - t0;
                    if(code == 200) msg = "✓ 连通，tiktok.com " + code + "（" + ms + "ms）";
                    else msg = "⚠ 代理可达，返回 " + code + "（" + ms + "ms）";
                }catch(Throwable t){
                    msg = "✗ 失败：" + t.getClass().getSimpleName() + " " + t.getMessage();
                }
                final String fm = msg;
                h.post(new Runnable(){ public void run(){ testRes.setText(fm); }});
            }}).start();
        }});

        dlg.show();
        dlg.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(
            new View.OnClickListener(){ public void onClick(View vv){
                String s = et.getText().toString().trim();
                if(s.length() > 0 && parseProxy(s) == null){
                    Toast.makeText(MainActivity.this, "代理格式不对，例：http://1.2.3.4:8080", Toast.LENGTH_SHORT).show();
                    return;
                }
                String srvAddr = etSrv.getText().toString().trim();
                if(srvAddr.endsWith("/")) srvAddr = srvAddr.substring(0, srvAddr.length() - 1);
                getSharedPreferences(SP, MODE_PRIVATE).edit()
                    .putString(KEY_PROXY, s)
                    .putBoolean(KEY_PROXY_ON, cb.isChecked() && s.length() > 0)
                    .putBoolean(KEY_PROXY_TT_ONLY, cbTT.isChecked())
                    .apply();
                // 空 = 恢复内置默认地址
                if(srvAddr.length() == 0)
                    getSharedPreferences(SP, MODE_PRIVATE).edit().remove(KEY_DYP_SRV).apply();
                else
                    getSharedPreferences(SP, MODE_PRIVATE).edit().putString(KEY_DYP_SRV, srvAddr).apply();
                // 下载路径：六项（根 + 五分类），空 = 用默认
                saveDlPath(KEY_DL_PATH,  etDlRoot.getText().toString().trim());
                saveDlPath(KEY_DL_VIDEO, etDlVideo.getText().toString().trim());
                saveDlPath(KEY_DL_IMG,   etDlImg.getText().toString().trim());
                saveDlPath(KEY_DL_ANIM,  etDlAnim.getText().toString().trim());
                saveDlPath(KEY_DL_AUDIO, etDlAudio.getText().toString().trim());
                saveDlPath(KEY_DL_MIX,   etDlMix.getText().toString().trim());
                boolean enabled = cb.isChecked() && s.length() > 0;
                String msg;
                if(!enabled) msg = "设置已保存";
                else if(cbTT.isChecked()) msg = "设置已保存（代理仅 TikTok）";
                else msg = "设置已保存（代理全部流量）";
                Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show();
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
        android.app.AlertDialog dlg = new android.app.AlertDialog.Builder(this)
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
        public void onResult(final String json){
            h.post(new Runnable(){ public void run(){
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
                web.setVisibility(View.GONE);
            }});
        }
    }

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
                    eb.append("1. 未开代理，TikTok 判定为受限地区\n");
                    eb.append("2. 代理节点被 TikTok 标记\n");
                    eb.append("3. 作品已删除或设为私密\n\n");
                    eb.append("建议：「关于 → 代理设置」填入可访问的境外代理后重试。");
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
        String s = getSharedPreferences(SP, MODE_PRIVATE).getString(KEY_DYP_SRV, DYP_SRV_DEFAULT);
        if(s == null) s = "";
        s = s.trim();
        while(s.endsWith("/")) s = s.substring(0, s.length() - 1);
        return s;
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
            tv.setText("机场流量：未配置服务器（TikTok 需自行开代理）");
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
                    out = "机场流量：服务器无响应（TikTok 需自行开代理）";
                } else if(body.indexOf("\"ok\":true") < 0 && body.indexOf("\"ok\": true") < 0){
                    out = "机场流量：" + str(jget(body, "err")) + "（TikTok 需自行开代理）";
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
                    // 剩余不足 1GB 或已到期 → 提醒走本地代理
                    if(!usable || remain < 1){
                        String reason = str(jget(body, "reason"));
                        b.append("\n⚠ 机场不可用");
                        if(reason.length() > 0) b.append("：").append(reason);
                        b.append("\n请在「代理设置」自行填入境外代理后使用 TikTok");
                    } else {
                        b.append("\nTikTok 解析/下载走服务器，无需挂代理");
                    }
                    out = b.toString();
                }
            }catch(Throwable t){
                Log.i("DYP", "AIR_EX " + t);
                out = "机场流量：连接失败（TikTok 需自行开代理）";
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
            curPlay = uVideo.length() > 0 ? uVideo : playAddr;
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
                img.setVisibility(View.GONE);
                if(hasVideo) doPlay();
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
        b.append("1. 未开代理，TikTok 直连不通\n");
        b.append("2. 代理节点被 TikTok 标记\n");
        b.append("3. 作品已删除或设为私密\n\n");
        b.append("建议：「关于 → 代理设置」填入境外代理后重试。");
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
                img.setVisibility(View.GONE);
                if(hasVideo) doPlay();
            }});

            // 是否真的会走代理下载这条 TikTok 链接
            boolean pxOn = curProxyFor(play.length() > 0 ? play : "https://www.tiktok.com/") != null;
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
            if(!pxOn){
                b.append("\n【注意】TikTok 视频 CDN 有地区限制，直连下载会 403。\n");
                b.append("　　　　「关于 → 代理设置」填入境外代理后即可下载。");
            }
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
                } else {
                    actions.setVisibility(View.VISIBLE);
                    img.setVisibility(View.GONE);
                    // 切到视频时清掉图集状态
                    imgUrls.clear(); dynUrls.clear(); curImgs=""; imgZoomed=false;
                    try{ ((TextView)findViewById(R.id.liveBadge)).setVisibility(View.GONE); }catch(Throwable t){}
                    doPlay();
                }
            }});
            // 独立音轨：只有快手分享页会直接给完整 m4a；抖音的 music 是背景音乐片段，不能当原声用
            curMusic = "快手".equals(gs(get(m,"i_platform"))) ? gs(get(m,"i_music")) : "";
            // BGM：抖音 detail 给的完整背景音乐 MP3，单独下载，不当视频音轨用
            curBgm = gs(get(m,"i_bgm"));
            curBgmTitle = gs(get(m,"i_bgmTitle"));
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
                boolean pxOn = curProxyFor(curPlay != null ? curPlay : "https://www.tiktok.com/") != null;
                if(!pxOn){
                    b.append("【提示】TikTok 视频 CDN 有地区限制，直连可能 403。\n");
                    b.append("　　　　「关于 → 代理设置」填入境外代理后再下载。\n");
                }
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
        // 兜底：任意 http 链接
        m = Pattern.compile("https?://[\\w./?=&%#@:+-]+").matcher(t);
        if(m.find()) return m.group();
        return null;
    }
void parse(){
        final String raw=input.getText().toString().trim();
        if(raw.length()==0){ s("空链接"); return; }
        String url=extractUrl(raw);
        // 把粘贴的整段口令清理成干净链接
        if(url != null && !url.equals(raw)){
            input.setText(url);
            input.setSelection(url.length());
        }
        if(url==null){ s("未找到链接"); return; }
        webDone=false;
        storyBlocked=false;
        curRetry = 0;
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

    void loadForParse(final String url){
        curUrl = url;
        o("");
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
                        s("快手直连失败，改用网页模式...");
                        setupWebView();
                        web.setVisibility(View.VISIBLE);
                        webDone = false;
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
        new Thread(new Runnable(){ public void run(){
            if(webDone || mixMode) return;
            DyDetail.Result r = null;
            // ---- 路径 1：自建服务器中转 ----
            String srv = getSharedPreferences(SP, MODE_PRIVATE).getString(KEY_DYP_SRV, DYP_SRV_DEFAULT);
            if(srv != null && srv.length() > 0){
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
    /** 图集提示语：有实况就说清楚能转 MP4；快手纯静态图集说明原因 */
    String imgTipText(){
        int n = imgUrls.size();
        int live = 0;
        for(int i = 0; i < dynUrls.size(); i++){ if(dynUrls.get(i).length() > 0) live++; }
        if(live > 0){
            if(live == n) return "共" + n + "张实况，左右滑动切图，可存为 MP4";
            return "共" + n + "张（" + live + "张实况），左右滑动切图，点图放大";
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

    void loadImage(final String url, final ImageView iv){
        if(url==null||url.length()==0) return;
        new Thread(new Runnable(){ public void run(){
            final byte[] d=httpGetBytes(url);
            if(d==null){ h.post(new Runnable(){ public void run(){ s("图片加载失败"); }}); return; }
            final Bitmap bm=BitmapFactory.decodeByteArray(d,0,d.length);
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
            if(miniPlayer == null || miniProxyKey == null || miniProxyKey != proxyCacheKey(curPlay) || miniWasTT != ttUrl){
                if(miniPlayer != null){ try{ miniPlayer.release(); }catch(Throwable t){} miniPlayer = null; }
                String ref = ttUrl ? "https://www.tiktok.com/" : (ksUrl ? "https://www.kuaishou.com/" : "https://www.douyin.com/");
                androidx.media3.datasource.DefaultHttpDataSource.Factory http =
                    new androidx.media3.datasource.DefaultHttpDataSource.Factory()
                        .setUserAgent(MUA)
                        .setDefaultRequestProperties(new HashMap<String,String>(){{ put("Referer", ref); }})
                        .setAllowCrossProtocolRedirects(true);
                // 代理：按「仅 TikTok」开关决定
                final Proxy pxPlay = curProxyFor(curPlay);
                Log.i("DYP", "MINI_DS tt=" + ttUrl + " proxy=" + (pxPlay != null));
                androidx.media3.datasource.DataSource.Factory f = http;
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
                        // 403 时直接给出可操作提示
                        if(e.getErrorCodeName() != null && e.getErrorCodeName().contains("BAD_HTTP_STATUS")){
                            final String msg = "视频被 CDN 拒绝（403）\n"
                                + "当前代理 IP 拿不到视频流。\n"
                                + "这不是解析失败，是 TikTok 的 CDN 只放行住宅 IP。\n"
                                + "换住宅节点，或点「关于 → 网络自检」看具体原因。";
                            h.post(new Runnable(){ public void run(){ s(msg); }});
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
            miniPlayer.setMediaItem(androidx.media3.common.MediaItem.fromUri(Uri.parse(curPlay)));
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

        video.setOnTouchListener(new View.OnTouchListener(){
            public boolean onTouch(View v, android.view.MotionEvent ev){
                gd.onTouchEvent(ev);
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
    }

    @Override protected void onDestroy(){
        super.onDestroy();
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
        new android.app.AlertDialog.Builder(this)
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
    /** 下载：视频 → mp4；图集 → 多选列表；实况图 → 封装实况照片 */
    void doDownload(){
        boolean hasImgs = imgUrls != null && imgUrls.size() > 0;
        if(!hasImgs && (curPlay == null || curPlay.length() == 0)){ s("无可下载地址"); return; }

        // 视频（无图集）：直接下 mp4（分类：视频）
        if(!hasImgs){
            enqueueDownload(curPlay, "视频", "mp4", "视频");
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

        new android.app.AlertDialog.Builder(this)
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

    /** 下载 BGM：抖音 detail 给的完整背景音乐 MP3 直链，无需转码 */
    void downloadBgm(){
        if(curBgm == null || curBgm.length() == 0){ s("无背景音乐地址"); return; }
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
                final String pfx = TtParser.isTtUrl(srcUrl) ? "tt_"
                                 : (KsParser.isKsUrl(srcUrl) ? "ks_" : "dy_");
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

    /** 下载任务：cat = 分类目录（视频/图片/音频/合集），mixDir = 合集专属子目录（可为空） */
    static class DlTask {
        final String url, tag, ext, cat, mixDir;
        /** 非空表示这是实况图：url=静态图，liveUrl=动轨视频，下载后封装成一个 .jpg */
        final String liveUrl;
        /** 非空时用它作为文件名主体（合集用：标题_第N集），否则用 dy_<id>_<tag> */
        final String nameBase;
        DlTask(String u, String t, String e){ this(u, t, e, "视频", "", "", ""); }
        DlTask(String u, String t, String e, String c){ this(u, t, e, c, "", "", ""); }
        DlTask(String u, String t, String e, String c, String m){
            this(u, t, e, c, m, "", "");
        }
        DlTask(String u, String t, String e, String c, String m, String lv){
            this(u, t, e, c, m, lv, "");
        }
        DlTask(String u, String t, String e, String c, String m, String lv, String nb){
            url = u; tag = t; ext = e; cat = c; mixDir = m;
            liveUrl = lv == null ? "" : lv;
            nameBase = nb == null ? "" : nb;
        }
        boolean isLive(){ return liveUrl.length() > 0; }
    }

    final java.util.List<DlTask> downloadQueue = new java.util.ArrayList<DlTask>();
    volatile boolean dlRunning = false;
    /** 本次下载实际落盘的目录集合（按顺序去重），用于完成时显示完整路径 */
    final java.util.LinkedHashSet<String> savedDirs = new java.util.LinkedHashSet<String>();

    /** 串行消费下载队列，进度条显示 第N个/总数 */
    void runDownloadQueue(){
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
                        name = (TtParser.isTtUrl(task.url) ? "tt_" : (KsParser.isKsUrl(task.url) ? "ks_" : "dy_"))
                                + curId + "_" + task.tag + "_" + System.currentTimeMillis() + "." + task.ext;
                    }
                    // 分类下载目录：分类自定义 > 根目录/分类；合集再加专属子文件夹
                    File catDir = new File(dlDirFor(task.cat));
                    if(task.mixDir != null && task.mixDir.length() > 0){
                        catDir = new File(catDir, task.mixDir);
                    }
                    if(!catDir.exists()) catDir.mkdirs();
                    final File target = new File(catDir, name);
                    lastCatDir[0] = catDir.getAbsolutePath();

                    s("下载中 " + idx + "/" + total + "：" + name);
                    Log.i("DYP", "DL_START " + idx + "/" + total + " " + name);

                    boolean ok;
                    if(task.isLive()){
                        ok = downloadLivePhoto(task, target);
                    } else {
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
                                appLog("下载", (ok2 ? "✓ " : "✗ ") + out.getName() + "  " + msg);
                            }
                        }, curSession, MainActivity.this);
                    }

                    if(ok) okCount++; else failCount++;
                    // 关键：必须通知系统媒体库，否则文件在磁盘上但相册看不到
                    if(ok){
                        // 图片下载后按真实文件头修正后缀：抖音/快手给的是 WEBP，
                        // 存成 .jpg 会让部分相册打不开。
                        File real = fixImageExt(target);
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