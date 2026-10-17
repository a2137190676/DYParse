package com.re.douyinparse;
import android.app.Activity;
import android.app.Dialog;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
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
import java.net.*;
import java.util.*;
import java.util.regex.*;

public class MainActivity extends Activity {
    EditText input; TextView btn; TextView status; LinearLayout out; WebView web;
    TextView btnPlay, btnDl, btnFull; LinearLayout actions; ImageView img;
    // 小窗播放器（ExoPlayer + TextureView）
    android.view.TextureView video;
    android.view.Surface videoSurface;
    androidx.media3.exoplayer.ExoPlayer miniPlayer;
    View videoBox, miniBar;
    TextView miniTimeNow, miniTimeAll, miniPlay, miniSpeed, miniFull;
    SeekBar miniSeek;
    int miniVideoW = 0, miniVideoH = 0;
    boolean miniPrepared = false, miniSeeking = false, miniZoomedLongPress = false;
    float miniDownX = 0, miniDownY = 0;
    static final float[] MINI_SPEEDS = {0.5f, 1.0f, 1.25f, 1.5f, 2.0f};
    static final String[] MINI_SPEED_LABELS = {"0.5X", "1.0X", "1.25X", "1.5X", "2.0X"};
    int miniSpeedIdx = 1;
    long miniLastTapTime = 0;
    float miniLastTapX = 0;
    String curPlay="", curCover="", curWs="", curImgs="", curId="", curDesc="";
    String curShowImg="";
    java.util.List<String> imgUrls=new java.util.ArrayList<String>();
    int imgIdx=0; View scrollOut;
    boolean imgZoomed=false; float imgDownX=0, imgDownY=0;
    android.widget.MediaController mc;
    volatile boolean shown=false;
    Handler h = new Handler(Looper.getMainLooper());
    volatile boolean webDone = false;
    static final String HOOK_JS =
     "(function(){try{if(window.__dyHooked)return;window.__dyHooked=1;window.__dyCaptured=[];"
     +"function rec(u,b){try{if(u&&b&&(b.indexOf('aweme_detail')>=0||b.indexOf('slidesinfo')>=0||b.indexOf('aweme_list')>=0)&&b.length>200){window.__dyCaptured.push({u:u,b:b});}}catch(e){}}"
     +"var of=window.fetch;if(of){window.fetch=function(){var a=arguments;var url=(typeof a[0]==='string')?a[0]:(a[0]&&a[0].url);var pr=of.apply(this,a);try{pr.then(function(r){try{r.clone().text().then(function(t){rec(url,t);});}catch(e){}});}catch(e){}return pr;};}"
     +"var oo=XMLHttpRequest.prototype.open,os=XMLHttpRequest.prototype.send;"
     +"XMLHttpRequest.prototype.open=function(m,u){this.__du=u;return oo.apply(this,arguments);};"
     +"XMLHttpRequest.prototype.send=function(){var x=this;try{x.addEventListener('load',function(){try{rec(x.__du,x.responseText);}catch(e){}});}catch(e){}return os.apply(this,arguments);};"
     +"}catch(e){}})();";

    Bundle savedState;
    protected void onCreate(Bundle b){
        super.onCreate(b);
        savedState = b;
        setContentView(R.layout.activity_main);
        input=(EditText)findViewById(R.id.input);
        btn=(TextView)findViewById(R.id.btn);
        status=(TextView)findViewById(R.id.status);
        out=(LinearLayout)findViewById(R.id.out);
        web=(WebView)findViewById(R.id.web);
        actions=(LinearLayout)findViewById(R.id.actions);
        btnPlay=(TextView)findViewById(R.id.btnPlay);
        btnDl=(TextView)findViewById(R.id.btnDl);
        btnFull=(TextView)findViewById(R.id.btnFull);
        video=(android.view.TextureView)findViewById(R.id.video);
        findViewById(R.id.btnAbout).setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ showAbout(); }});
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
        videoBox=findViewById(R.id.videoBox);
        miniBar=findViewById(R.id.miniBar);
        miniTimeNow=(TextView)findViewById(R.id.miniTimeNow);
        miniTimeAll=(TextView)findViewById(R.id.miniTimeAll);
        miniPlay=(TextView)findViewById(R.id.miniPlay);
        miniSpeed=(TextView)findViewById(R.id.miniSpeed);
        miniFull=(TextView)findViewById(R.id.miniFull);
        miniSeek=(SeekBar)findViewById(R.id.miniSeek);
        img=(ImageView)findViewById(R.id.img);
        WebSettings ws=web.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setDatabaseEnabled(true);
        ws.setUserAgentString("Mozilla/5.0 (Linux; Android 12; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36");
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);
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
                    if(u != null && u.indexOf("slidesinfo") >= 0){
                        Log.i("DYP", "INTERCEPT_HIT " + u.substring(0, Math.min(180, u.length())));
                        // 用同一份 Cookie 自己发一次，拿到 body
                        new Thread(new Runnable(){ public void run(){
                            try{
                                String ck = CookieManager.getInstance().getCookie(u);
                                java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(u).openConnection();
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
                if(u==null || !(u.startsWith("http://")||u.startsWith("https://"))) return;
                final String uu=u;
                // heuristics: challenge/shell page has no aweme marker
                v.evaluateJavascript("(function(){try{return (document.body?document.body.innerText.length:0)+'|'+((location.href.indexOf('/video/')>=0||location.href.indexOf('/note/')>=0||location.href.indexOf('/slides/')>=0)?1:0)+'|'+(window._ROUTER_DATA?1:0);}catch(e){return '0|0|0';}})()",
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
        btn.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ parse(); }});
        btnPlay.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ doPlay(); }});
        btnDl.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ doDownload(); }});
        btnFull.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ openPlayer(); }});
        setupMiniPlayer();
        scrollOut=findViewById(R.id.scrollOut);
        // 图集手势：未放大时不允许滑动（仅单击放大），放大后才能左右切图
        final android.view.GestureDetector igd=new android.view.GestureDetector(this,new android.view.GestureDetector.SimpleOnGestureListener(){
            public boolean onDown(android.view.MotionEvent e){ return true; }
            public boolean onSingleTapUp(android.view.MotionEvent e){ showImageFull(curShowImg); return true; }
            public boolean onFling(android.view.MotionEvent a,android.view.MotionEvent b,float vx,float vy){
                if(a==null||b==null) return false;
                // \u672a\u653e\u5927\u65f6\u4e0d\u54cd\u5e94\u6ed1\u52a8
                if(!imgZoomed){ s("\u5148\u70b9\u51fb\u56fe\u7247\u653e\u5927\uff0c\u624d\u80fd\u5de6\u53f3\u5207\u56fe"); return true; }
                float dx=b.getX()-a.getX(), dy=b.getY()-a.getY();
                if(Math.abs(dx)>70 && Math.abs(dx)>Math.abs(dy)){ if(dx<0) showImgAt(imgIdx+1); else showImgAt(imgIdx-1); return true; }
                return false;
            }
        });
        // \u70b9\u51fb\u56fe\u7247\uff1a\u5207\u6362\u7f29\u653e\u72b6\u6001\uff08\u653e\u5927\u4e0e\u539f\u56fe\u4e4b\u95f4\u5207\u6362\uff09
        img.setOnTouchListener(new View.OnTouchListener(){ public boolean onTouch(View v,android.view.MotionEvent e){
            boolean r = igd.onTouchEvent(e);
            if(e.getActionMasked()==android.view.MotionEvent.ACTION_UP && !imgZoomed && Math.abs(e.getX()-imgDownX)<30 && Math.abs(e.getY()-imgDownY)<30){
                // \u5355\u51fb\u4e14\u672a\u653e\u5927\uff1a\u76f4\u63a5\u653e\u5927
                imgZoomed = true;
                showImageFull(curShowImg);
            }
            if(e.getActionMasked()==android.view.MotionEvent.ACTION_DOWN){ imgDownX=e.getX(); imgDownY=e.getY(); }
            return r;
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
    void s(final String t){ h.post(new Runnable(){ public void run(){ status.setText(t); }}); }
    void o(final String t){ h.post(new Runnable(){ public void run(){ curResult = t; renderCards(t); }}); }

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
        +"try{var rd=window._ROUTER_DATA;var ld=rd&&rd.loaderData;if(ld){for(var k in ld){var pg=ld[k];var il=(pg&&pg.videoInfoRes&&pg.videoInfoRes.item_list)||(pg&&pg.slidesInfoRes&&pg.slidesInfoRes.item_list)||(pg&&pg.imagesInfoRes&&pg.imagesInfoRes.item_list)||(pg&&pg.item_list);if(il&&il.length){var it=il[0];out.i_id=it.aweme_id;out.i_author=it.author?it.author.nickname:'';out.i_desc=it.desc||'';out.i_time=it.create_time;try{out.i_cover=(it.video&&it.video.cover&&it.video.cover.url_list)?it.video.cover.url_list[0]:'';}catch(e){}try{var pa=it.video&&it.video.play_addr;if(pa){out.i_play=(pa.url_list&&pa.url_list[0])?pa.url_list[0]:'';var uri=pa.uri||'';if(uri)out.i_ws='https://aweme.snssdk.com/aweme/v1/play/?video_id='+uri+'&ratio=1080p&line=0';}}catch(e){}try{out.i_dur=(it.video&&it.video.duration)?it.video.duration:0;}catch(e){}try{var po=it.poi||it.poi_info;if(po)out.i_poet=po.poi_name||po.name||'';if(!out.i_poet){var ct=it.city||(it.author&&it.author.city);if(ct)out.i_poet=ct;}}catch(e){}try{var te=it.text_extra;if(te&&te.length){var ts=[];for(var ti=0;ti<te.length;ti++){if(te[ti].hashtag_name)ts.push('#'+te[ti].hashtag_name);}out.i_tags=ts.join(' ');}}catch(e){}try{if(!out.i_tags){var cp=it.cha_list;if(cp&&cp.length){var cs=[];for(var ci=0;ci<cp.length;ci++){if(cp[ci].cha_name)cs.push('#'+cp[ci].cha_name);}out.i_tags=cs.join(' ');}}}catch(e){}try{out.i_region=(it.author&&it.author.region)?it.author.region:'';}catch(e){}try{var pc=it.poi;if(pc){out.i_poi=pc.poi_name||'';}}catch(e){}try{if(it.images){var ar=[],dy=[];for(var j=0;j<it.images.length;j++){var im=it.images[j];var u=im.url_list;if(u)ar.push(u[u.length-1]);var iv=im.video;if(iv&&iv.play_addr&&iv.play_addr.url_list&&iv.play_addr.url_list.length){dy.push(iv.play_addr.url_list[0]);}}out.i_imgs=ar.join('|');if(dy.length>0)out.i_dyn=dy.join('|');}}catch(e){}break;}}}}catch(e){out.iErr=''+e;}"
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
+"function finish(){try{var cap=window.__dyCaptured||[];out.capN=cap.length;if(cap.length>0){out.detailLen=cap[0].b.length;out.detailHead=cap[0].b.slice(0,800);out.capUrl=cap[0].u;}}catch(e){}try{out.bodyText2=(document.body?document.body.innerText:'').slice(0,1500);}catch(e){}done();}"
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
            Log.i("DYP", "SLIDES_JSON len=" + json.length());
            String r = render(json);
            Log.i("DYP", "RESULT_BEGIN\n" + r + "\nRESULT_END");
            o(r); s("完成(slides)");
            webDone = true;
        }catch(Throwable t){ Log.i("DYP", "SLIDES_ERR " + t); }
    }


    // ===== 检查更新（基于 GitHub Releases，无需自建服务器）=====
    static final String GH_OWNER = "a2137190676";
    static final String GH_REPO  = "DYParse";

    /** 联网检查最新 Release，与当前 versionCode 比对 */
    void checkUpdate(final boolean silent){
        if(GH_OWNER.length() == 0 || GH_REPO.length() == 0){
            if(!silent) s("未配置仓库地址");
            return;
        }
        if(!silent) s("检查更新中...");
        new Thread(new Runnable(){ public void run(){
            try{
                String api = "https://api.github.com/repos/" + GH_OWNER + "/" + GH_REPO + "/releases/latest";
                java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(api).openConnection();
                c.setRequestProperty("User-Agent", "DYParse-Updater");
                c.setRequestProperty("Accept", "application/vnd.github+json");
                c.setConnectTimeout(12000);
                c.setReadTimeout(15000);
                int code = c.getResponseCode();
                if(code != 200){
                    Log.i("DYP", "UPDATE_HTTP " + code);
                    if(!silent) s("检查失败(HTTP " + code + ")");
                    return;
                }
                java.io.InputStream in = c.getInputStream();
                java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
                byte[] buf = new byte[8192]; int n;
                while((n = in.read(buf)) > 0) bo.write(buf, 0, n);
                in.close();
                final String body = new String(bo.toByteArray(), "UTF-8");
                final String tag = jget(body, "tag_name");
                final String html = jget(body, "html_url");
                final String name = jget(body, "name");
                String bodyTxt = jget(body, "body");
                String apkUrl = ""; String apkName = "";
                // 找 .apk 附件
                int p = body.indexOf("browser_download_url");
                while(p > 0){
                    String u = jgetFrom(body, p);
                    if(u != null && u.toLowerCase().endsWith(".apk")){ apkUrl = u; apkName = u.substring(u.lastIndexOf('/') + 1); break; }
                    p = body.indexOf("browser_download_url", p + 1);
                }
                final String fApk = apkUrl, fApkName = apkName;
                final String fTag = tag;
                Log.i("DYP", "UPDATE_TAG " + tag + " cur=" + curVersionCode());
                final int newVer = parseVer(tag);
                final int curVer = curVersionCode();
                h.post(new Runnable(){ public void run(){
                    if(newVer > curVer){
                        showUpdateDialog(fTag, name, bodyTxt, html, fApk, fApkName, newVer, curVer);
                        s("发现新版本 " + fTag);
                    } else {
                        if(!silent) s("已是最新版 (" + versionName() + ")");
                    }
                }});
            }catch(Throwable t){
                Log.i("DYP", "UPDATE_ERR " + t);
                if(!silent) s("检查失败：" + t);
            }
        }}).start();
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
        StringBuilder m = new StringBuilder();
        m.append("当前版本：").append(versionName()).append(" (").append(curVer).append(")\n");
        m.append("最新版本：").append(tag).append("\n");
        if(name != null && name.length() > 0) m.append("\n").append(name);
        if(bodyTxt != null && bodyTxt.length() > 0){
            String t = bodyTxt.length() > 600 ? bodyTxt.substring(0, 600) + "…" : bodyTxt;
            m.append("\n\n").append(t);
        }
        new android.app.AlertDialog.Builder(this)
            .setTitle("发现新版本")
            .setMessage(m.toString())
            .setPositiveButton(apkUrl.length() > 0 ? "直接下载" : "打开页面", new android.content.DialogInterface.OnClickListener(){
                public void onClick(android.content.DialogInterface d, int w){
                    if(apkUrl.length() > 0) downloadApk(apkUrl, apkName);
                    else openUrl(html);
                }
            })
            .setNeutralButton("浏览器打开", new android.content.DialogInterface.OnClickListener(){
                public void onClick(android.content.DialogInterface d, int w){ openUrl(html); }
            })
            .setNegativeButton("稍后", null)
            .show();
    }

    void openUrl(String u){
        try{ startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(u))); }
        catch(Throwable t){ s("无法打开浏览器"); }
    }

    /** \u76f4\u63a5\u7528 DownloadManager \u4e0b\u65b0\u7248 APK \u5230 Download */
    void downloadApk(String url, String fname){
        try{
            String nm = (fname == null || fname.length() == 0) ? ("DYParse_" + System.currentTimeMillis() + ".apk") : fname;
            DownloadManager dm = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            DownloadManager.Request r = new DownloadManager.Request(Uri.parse(url));
            r.setTitle(nm);
            r.setMimeType("application/vnd.android.package-archive");
            r.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            r.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, nm);
            r.addRequestHeader("User-Agent", MUA);
            dm.enqueue(r);
            s("已开始下载：Download/" + nm);
            Toast.makeText(this, "下载完成后到 Download 目录安装", Toast.LENGTH_LONG).show();
        }catch(Throwable t){ s("下载失败：" + t); }
    }


    // ===== 关于 / 免责声明 =====
    static final String APP_QQ = "2137190676";

    void showAbout(){
        String txt =
            "版本：" + versionName() + " (build " + curVersionCode() + ")\n\n"
          + "【软件声明】\n"
          + "本软件全程免费且开源，严禁用于任何商业用途或违法行为。"
          + "使用者应自行承担使用风险，包括但不限于违反平台服务条款、侵犯内容版权等。"
          + "因使用本软件产生的一切后果由使用者自负，作者不承担任何责任。\n\n"
          + "【技术说明】\n"
          + "仅解析公开分享页面，不存储任何用户数据，不使用、不上传任何账号信息。\n\n"
          + "【问题反馈】\n"
          + "QQ：" + APP_QQ + "\n\n"
          + "【更新】\n"
          + "新版本发布在 GitHub Releases，点下方「检查更新」即可。";

        int bgDlg   = curDark ? 0xFF191B21 : 0xFFFFFFFF;
        int textDlg = curDark ? 0xFFF2F3F5 : 0xFF1A1A1F;

        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setBackgroundColor(bgDlg);
        wrap.setPadding(dp(20), dp(16), dp(20), dp(12));

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
            .setPositiveButton("检查更新", new android.content.DialogInterface.OnClickListener(){
                public void onClick(android.content.DialogInterface d, int w){ checkUpdate(false); }
            })
            .setNeutralButton("复制QQ", new android.content.DialogInterface.OnClickListener(){
                public void onClick(android.content.DialogInterface d, int w){
                    try{
                        android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                        cm.setPrimaryClip(android.content.ClipData.newPlainText("qq", APP_QQ));
                        Toast.makeText(MainActivity.this, "已复制 QQ：" + APP_QQ, Toast.LENGTH_SHORT).show();
                    }catch(Throwable t){}
                }
            })
            .setNegativeButton("关闭", null)
            .create();
        dlg.show();
        // 弹窗本体也跟主题
        try{
            android.view.Window win = dlg.getWindow();
            if(win != null){
                win.setBackgroundDrawable(makeRoundRect(bgDlg, curDark ? 0xFF2B2F39 : 0xFFE3E5EA, 16));
            }
        }catch(Throwable t){}
    }

    int dp(int v){ return (int)(v * getResources().getDisplayMetrics().density); }


    // ===== 卡片式渲染 =====
    // 字段优先级（视频/图集各自一套）
    static final String[] CARD_ORDER = {
        "作品ID", "作者", "描述", "发布时间", "时长", "封面", "视频直链", "无水印直链", "图集", "类型"
    };

    /** 把 render() 输出的 【key】value 文本拆成卡片 */
    void renderCards(final String text){
        if(out == null) return;
        out.removeAllViews();
        if(text == null || text.length() == 0) return;

        // 解析成有序 kv
        java.util.List<String[]> items = new java.util.ArrayList<String[]>();
        String[] lines = text.split("\\n");
        for(String ln : lines){
            if(ln == null) continue;
            ln = ln.trim();
            if(ln.length() == 0) continue;
            int a = ln.indexOf("\u3010");
            int b = ln.indexOf("\u3011");
            if(a == 0 && b > 1){
                String k = ln.substring(1, b);
                String v = ln.substring(b + 1).trim();
                items.add(new String[]{k, v});
            } else if(items.size() > 0){
                // 续行，拼到上一项
                String[] last = items.get(items.size() - 1);
                last[1] = last[1] + "\\n" + ln;
            }
        }
        if(items.size() == 0){
            // 没结构化，直接一张大卡片
            out.addView(buildCard("解析结果", text, false));
            return;
        }

        // 按优先级排序
        java.util.List<String[]> ordered = new java.util.ArrayList<String[]>();
        for(String want : CARD_ORDER){
            for(String[] kv : items){
                if(kv[0].equals(want)) ordered.add(kv);
            }
        }
        // 剩下的追加
        for(String[] kv : items){
            boolean dup = false;
            for(String want : CARD_ORDER) if(kv[0].equals(want)){ dup = true; break; }
            if(!dup) ordered.add(kv);
        }

        for(String[] kv : ordered){
            String k = kv[0], v = kv[1];
            if(v == null || v.length() == 0 || v.equals("null")) continue;
            boolean copyable = true;
            out.addView(buildCard(k, v, copyable));
        }
    }

    /** 单个信息卡：标签在上，值在下 */
    View buildCard(String label, String value, final boolean copyable){
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(makeRoundRect(curDark ? C_DARK_SURFACE : C_LIGHT_SURFACE, curDark ? C_DARK_STROKE : C_LIGHT_STROKE, 16));
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        clp.bottomMargin = dp(10);
        card.setLayoutParams(clp);

        // 标签行
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

        // 值
        TextView val = new TextView(this);
        val.setText(value);
        val.setTextSize(13);
        val.setTextColor(curDark ? C_DARK_TEXT : C_LIGHT_TEXT);
        val.setLineSpacing(dp(4), 1f);
        val.setTextIsSelectable(true);
        val.setPadding(0, dp(8), 0, 0);
        card.addView(val);

        return card;
    }

    int colorRes(int id){ return id; }



    // ===== 日间/夜间切换（手动改控件颜色，不依赖系统 UiMode） =====
    static final String SP = "dyp_pref";
    static final String KEY_DARK = "dark_mode";
    TextView btnTheme;

    boolean isDarkMode(){
        try{ return getSharedPreferences(SP, MODE_PRIVATE).getBoolean(KEY_DARK, false); }
        catch(Throwable t){ return false; }
    }

    // 两套色值
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

        // 根布局
        View root = findViewById(R.id.root);
        if(root != null) root.setBackgroundColor(bg);

        // 标题
        View t1 = findViewById(R.id.tvTitle);
        if(t1 instanceof TextView) ((TextView)t1).setTextColor(textC);

        // 输入框
        if(input != null){
            input.setBackground(makeRoundRect(surface, stroke, 14));
            input.setTextColor(textC);
            input.setHintTextColor(hintC);
        }
        if(status != null) status.setTextColor(subC);

        // 播放 / 下载（淡色按钮）
        if(btnPlay != null){ btnPlay.setBackground(makeRoundRect(alt, stroke, 12)); btnPlay.setTextColor(textC); }
        if(btnDl   != null){ btnDl.setBackground(makeRoundRect(alt, stroke, 12));   btnDl.setTextColor(textC); }

        // 顶部图标按钮
        if(btnTheme != null){
            btnTheme.setTextColor(subC);
            btnTheme.setText(dark ? "亮" : "暗");
        }
        if(btnAbout != null) btnAbout.setTextColor(subC);

        // 图片区背景
        if(img != null) img.setBackgroundColor(alt);

        // 结果卡片（重建，带新颜色）
        if(curResult != null && curResult.length() > 0) renderCards(curResult);
    }

    /** 动态画圆角矩形背景 */
    android.graphics.drawable.GradientDrawable makeRoundRect(int fill, int strokeColor, int radiusDp){
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(radiusDp));
        if(strokeColor != 0) g.setStroke(dp(1), strokeColor);
        return g;
    }

    TextView btnAbout;
    String curResult = "";
    boolean curDark = false;

    /** 发布时间：秒级时间戳 -> 年-月-日 HH:mm */
    static String fmtTime(String raw){
        if(raw == null || raw.length() == 0) return "";
        try{
            double d = Double.parseDouble(raw.trim());
            long ms = (long)(d * 1000);
            // 大部分是秒，但也有些是毫秒
            if(d < 100000000000L) ms = (long)d * 1000;
            else ms = (long)d;
            java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm");
            return f.format(new java.util.Date(ms));
        }catch(Throwable t){ return raw; }
    }

    /** 时长：毫秒 -> 几时几分几秒 */
    static String fmtDuration(String raw){
        if(raw == null || raw.length() == 0) return "";
        try{
            double d = Double.parseDouble(raw.trim());
            long ms = (long)d;
            if(ms <= 0) return "无（图文）";
            long total = ms / 1000;
            long h = total / 3600;
            long m = (total % 3600) / 60;
            long sec = total % 60;
            if(h > 0) return h + "小时" + m + "分" + sec + "秒";
            if(m > 0) return m + "分" + sec + "秒";
            return sec + "秒";
        }catch(Throwable t){ return raw; }
    }

    class Bridge {
        @JavascriptInterface
        public void onResult(final String json){
            h.post(new Runnable(){ public void run(){
                webDone=true;
                String r=render(json);
                Log.i("DYP","RESULT_BEGIN\n"+r+"\nRESULT_END");
                Log.i("DYP","RAW_LEN="+json.length());
                o(r); s("完成(WebView)");
                web.setVisibility(View.GONE);
            }});
        }
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
            h.post(new Runnable(){ public void run(){
                if(isImg){ actions.setVisibility(View.VISIBLE); video.setVisibility(View.GONE); img.setVisibility(View.VISIBLE);
                    parseImgs(); imgIdx=0;
                    if(imgUrls.size()>0){ curShowImg=imgUrls.get(0); loadImage(curShowImg, img); }
                    Toast.makeText(MainActivity.this,"共"+imgUrls.size()+"张，左右滑动切图，点图放大",Toast.LENGTH_SHORT).show();
                } else { actions.setVisibility(View.VISIBLE); img.setVisibility(View.GONE);
                    doPlay();
                }
            }});
            StringBuilder b=new StringBuilder();
            b.append("【作品ID】").append(iid).append("\n");
            b.append("【作者】").append(gs(get(m,"i_author"))).append("\n");
            b.append("【描述】").append(gs(get(m,"i_desc"))).append("\n");
            b.append("【发布时间】").append(fmtTime(gs(get(m,"i_time")))).append("\n");
            b.append("【时长】").append(fmtDuration(gs(get(m,"i_dur")))).append("\n");
            String poet = gs(get(m,"i_poet"));
            if(poet.length() > 0) b.append("【发布地点】").append(poet).append("\n");
            String tags = gs(get(m,"i_tags"));
            if(tags.length() > 0) b.append("【话题】").append(tags).append("\n");
            b.append("【封面】").append(curCover).append("\n");
            b.append("【视频直链】").append(gs(get(m,"i_play"))).append("\n");
            String ws=gs(get(m,"i_ws"));
            if(ws.length()>0) b.append("【无水印直链】").append(ws).append("\n");
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
        StringBuilder b=new StringBuilder();
        b.append("【页面URL】").append(gs(get(m,"href"))).append("\n");
        b.append("【作品ID】").append(gs(get(m,"id"))).append("\n");
        String dsc=gs(get(m,"desc"));
        if(dsc.length()>0) b.append("【描述】").append(dsc).append("\n");
        String vid=gs(get(m,"video"));
        if(vid.length()>0) b.append("【视频直链】").append(vid).append("\n");
        String cov=gs(get(m,"cover"));
        if(cov.length()>0) b.append("【封面】").append(cov).append("\n");
        b.append("【提示】未能取到结构化详情\n");
        b.append("【tags】").append(gs(get(m,"tags"))).append("\n");
        b.append("【capN】").append(gs(get(m,"capN"))).append(" 【capUrl】").append(gs(get(m,"capUrl"))).append("\n");
        b.append("【title】").append(gs(get(m,"title"))).append("\n");
        b.append("【htmlAweme】").append(gs(get(m,"htmlHasAweme"))).append(" 【htmlPlay】").append(gs(get(m,"htmlHasPlay"))).append("\n");
        b.append("【paceKeys】").append(gs(get(m,"paceKeys"))).append("\n");
        b.append("【mfKeys】").append(gs(get(m,"mfKeys"))).append(" len=").append(gs(get(m,"mfLen"))).append("\n");
        b.append("【wAweme】").append(gs(get(m,"wAweme"))).append("\n");
        b.append("【ROUTERFULL】").append(gs(get(m,"routerFull"))).append("\n");
        b.append("【bodyText】").append(gs(get(m,"bodyText"))).append("\n");
        b.append("【hooked】").append(gs(get(m,"hooked"))).append(" 【fetchType】").append(gs(get(m,"fetchType"))).append("\n");
        b.append("【bodyText2】").append(gs(get(m,"bodyText2"))).append("\n");
        b.append("【wkeys】").append(gs(get(m,"wkeys"))).append("\n");
        b.append("【initProps】").append(gs(get(m,"initProps"))).append("\n");
        b.append("【detailLen】").append(gs(get(m,"detailLen"))).append("\n");
        b.append("【detailHead】").append(gs(get(m,"detailHead"))).append("\n");
        b.append("【rdLen】").append(gs(get(m,"rdLen"))).append("\n");
        b.append("【rdHasAweme】").append(gs(get(m,"rdHasAweme"))).append("\n");
        b.append("【rdHasPlay】").append(gs(get(m,"rdHasPlay"))).append("\n");
        b.append("【rdKeys】").append(gs(get(m,"rdKeys"))).append("\n");
        b.append("【rdErr】").append(gs(get(m,"rdErr"))).append(" / ").append(gs(get(m,"rdE2"))).append("\n");
        b.append("【ssrLen】").append(gs(get(m,"ssrLen"))).append("\n");
        b.append("【ssrKeys】").append(gs(get(m,"ssrKeys"))).append("\n");
        b.append("【rdHead】").append(gs(get(m,"rdHead"))).append("\n");
        b.append("【fe】").append(gs(get(m,"ferr"))).append("\n");
        return b.toString();
    }

    String httpGet(String url){
        try{
            HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();
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
        Matcher m=Pattern.compile("https?://[\\w./?=&%#@:+-]+").matcher(t);
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
        o(""); s("加载中(WebView)...");
        web.setVisibility(View.VISIBLE);
        web.loadUrl(url);
    }
    static String firstOf(String[] a){ for(String s:a){ if(s!=null && s.length()>0) return s; } return ""; }
    byte[] httpGetBytes(String url){
        try{
            HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();
            c.setInstanceFollowRedirects(true);
            c.setRequestProperty("User-Agent","Mozilla/5.0 (Linux; Android 12; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36");
            c.setRequestProperty("Referer","https://www.douyin.com/");
            c.setConnectTimeout(15000); c.setReadTimeout(20000);
            InputStream in=c.getInputStream();
            ByteArrayOutputStream bo=new ByteArrayOutputStream();
            byte[] buf=new byte[8192]; int n;
            while((n=in.read(buf))>0) bo.write(buf,0,n);
            in.close();
            return bo.toByteArray();
        }catch(Exception e){ Log.i("DYP","imgErr "+e); return null; }
    }
    void loadImage(final String url, final ImageView iv){
        if(url==null||url.length()==0) return;
        new Thread(new Runnable(){ public void run(){
            final byte[] d=httpGetBytes(url);
            if(d==null){ s("图片加载失败"); return; }
            final Bitmap bm=BitmapFactory.decodeByteArray(d,0,d.length);
            h.post(new Runnable(){ public void run(){ if(bm!=null){ iv.setImageBitmap(bm); s("图片已显示"); } else s("解码失败"); }});
        }}).start();
    }
    static final String MUA="Mozilla/5.0 (Linux; Android 12; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36";
    volatile String startedUrl="";
    void doPlay(){
        if(curPlay==null||curPlay.length()==0){ s("\u65e0\u53ef\u64ad\u653e\u5730\u5740"); return; }
        videoBox.setVisibility(View.VISIBLE);
        img.setVisibility(View.GONE);
        if(curPlay.equals(startedUrl)){ s("\u64ad\u653e\u4e2d..."); return; }
        startedUrl = curPlay;
        try{
            if(miniPlayer == null){
                androidx.media3.datasource.DefaultHttpDataSource.Factory http =
                    new androidx.media3.datasource.DefaultHttpDataSource.Factory()
                        .setUserAgent(MUA)
                        .setDefaultRequestProperties(new HashMap<String,String>(){{ put("Referer", "https://www.douyin.com/"); }})
                        .setAllowCrossProtocolRedirects(true);
                miniPlayer = new androidx.media3.exoplayer.ExoPlayer.Builder(this)
                    .setMediaSourceFactory(new androidx.media3.exoplayer.source.DefaultMediaSourceFactory(http))
                    .build();
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
                    }
                });
            }
            if(videoSurface != null) miniPlayer.setVideoSurface(videoSurface);
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
            public boolean onSurfaceTextureDestroyed(android.graphics.SurfaceTexture st){ return videoSurface != null && videoSurface.isValid(); }
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
        if(curImgs==null||curImgs.length()==0) return;
        String[] a=curImgs.split("[|]");
        for(int i=0;i<a.length;i++){
            String u=a[i].trim();
            if(u.length()==0) continue;
            if(u.equals("DYN")) continue;
            if(u.indexOf("http")!=0) continue;
            imgUrls.add(u);
        }
    }
    void showImgAt(int i){
        if(imgUrls.size()==0) return;
        if(i<0) i=0;
        if(i>=imgUrls.size()) i=imgUrls.size()-1;
        if(i==imgIdx && curShowImg.equals(imgUrls.get(i))) return;
        imgIdx=i;
        curShowImg=imgUrls.get(i);
        loadImage(curShowImg, img);
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
                ImageView big=new ImageView(MainActivity.this);
                big.setScaleType(ImageView.ScaleType.FIT_CENTER);
                big.setImageBitmap(bm);
                big.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ dlg.dismiss(); }});
                dlg.setContentView(big);
                dlg.show();
                s("大图已显示，点图/返回关闭");
            }});
        }}).start();
    }
    /** 下载：视频 → mp4；图集 → 弹窗选择（当前这张 / 全部）；动图 → 图片 + 动图视频都下 */
    void doDownload(){
        boolean hasImgs = imgUrls != null && imgUrls.size() > 0;
        // 动图视频地址（curImgs 里 DYN 之后）
        java.util.List<String> dyns = new java.util.ArrayList<String>();
        if(curImgs != null && curImgs.indexOf("|DYN|") >= 0){
            String tail = curImgs.substring(curImgs.indexOf("|DYN|") + 5);
            for(String u : tail.split("[|]")){
                u = u.trim();
                if(u.startsWith("http")) dyns.add(u);
            }
        }

        if(!hasImgs && (curPlay == null || curPlay.length() == 0)){ s("\u65e0\u53ef\u4e0b\u8f7d\u5730\u5740"); return; }

        // 视频（无图集）：直接下 mp4
        if(!hasImgs){
            enqueueDownload(curPlay, "\u89c6频", "mp4");
            return;
        }

        // 图集 / 动图：弹窗选择
        final String cur = curShowImg != null && curShowImg.length() > 0 ? curShowImg : imgUrls.get(0);
        final java.util.List<String> dynList = dyns;
        String tip = "\u56fe\u96c6共" + imgUrls.size() + "\u5f20" + (dyns.size() > 0 ? "\uff08\u542b" + dyns.size() + "\u4e2a\u52a8\u56fe\uff09" : "");

        new android.app.AlertDialog.Builder(this)
            .setTitle("\u4e0b\u8f7d")
            .setMessage(tip + "\n\n\u5f53\u524d\uff1a\u7b2c" + (imgIdx + 1) + "\u5f20")
            .setPositiveButton("\u4e0b\u8f7d\u5168\u90e8", new android.content.DialogInterface.OnClickListener(){
                public void onClick(android.content.DialogInterface d, int w){
                    // 图片逐张
                    for(int i = 0; i < imgUrls.size(); i++){
                        enqueueDownload(imgUrls.get(i), "\u56fe" + (i + 1), "jpg");
                    }
                    // 动图视频
                    for(int i = 0; i < dynList.size(); i++){
                        enqueueDownload(dynList.get(i), "\u52a8\u56fe" + (i + 1), "mp4");
                    }
                    s("\u5df2\u52a0\u5165\u4e0b\u8f7d\uff1a" + imgUrls.size() + "\u5f20\u56fe" + (dynList.size() > 0 ? " + " + dynList.size() + "\u4e2a\u52a8\u56fe" : ""));
                }
            })
            .setNeutralButton("\u53ea\u4e0b\u5f53\u524d", new android.content.DialogInterface.OnClickListener(){
                public void onClick(android.content.DialogInterface d, int w){
                    enqueueDownload(cur, "\u7b2c" + (imgIdx + 1) + "\u5f20", "jpg");
                    // 当前这张若有动图版本，一并\u4e0b
                    if(imgIdx < dynList.size()){
                        enqueueDownload(dynList.get(imgIdx), "\u7b2c" + (imgIdx + 1) + "\u5f20\u52a8\u56fe", "mp4");
                    }
                }
            })
            .setNegativeButton("\u53d6\u6d88", null)
            .show();
    }

    void enqueueDownload(String url, String tag, String ext){
        if(url == null || url.length() == 0) return;
        try{
            String name = "dy_" + curId + "_" + tag + "_" + System.currentTimeMillis() + "." + ext;
            DownloadManager dm = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            DownloadManager.Request r = new DownloadManager.Request(Uri.parse(url));
            r.setTitle(name);
            r.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            r.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name);
            r.addRequestHeader("User-Agent", MUA);
            r.addRequestHeader("Referer", "https://www.douyin.com/");
            dm.enqueue(r);
            Log.i("DYP", "DL_ENQUEUE " + name);
        }catch(Exception e){ Log.i("DYP", "dlErr " + e); }
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