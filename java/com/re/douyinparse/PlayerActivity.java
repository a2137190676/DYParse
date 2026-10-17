package com.re.douyinparse;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;
import android.widget.*;

import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.common.VideoSize;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;

/**
 * \u64ad\u653e\u5668\uff1aExoPlayer \u5185\u6838 + TextureView \u6e32\u67d3
 *
 * \u4e3a\u4ec0\u4e48\u4e0d\u7528 PlayerView\uff1a
 *   media3-ui \u4f1a\u62d6\u8fdb androidx.core / recyclerview / lifecycle /
 *   versionedparcelable / interpolator \u4e00\u5927\u5806\u4f9d\u8d56\uff0c
 *   \u88f8 aapt+d8 \u6784\u5efa\u4e0b\u9010\u4e2a\u8865\u6ca1\u6709\u5c3d\u5934\u3002
 *
 * \u4e3a\u4ec0\u4e48\u8981 ExoPlayer\uff1a
 *   1) \u500d\u901f\uff1aMediaPlayer.setPlaybackParams \u5728 Android 12+ \u629b IllegalArgumentException\uff1b
 *      ExoPlayer \u7528 Sonic \u505a\u97f3\u9891\u65f6\u95f4\u62c9\u4f38\uff0c0.5x~4x \u771f\u53d8\u901f\u4e0d\u7834\u97f3\u3002
 *   2) \u65cb\u8f6c\uff1aVideoSize.unappliedRotationDegrees \u76f4\u63a5\u544a\u8bc9\u4f60\u89c6\u9891\u771f\u5b9e\u671d\u5411\u3002
 *
 * \u4e3a\u4ec0\u4e48\u7528 TextureView \u800c\u4e0d\u662f SurfaceView\uff1a
 *   TextureView \u53ef\u4ee5\u7528 setTransform(Matrix) \u81ea\u5df1\u63a7\u5236\u7f29\u653e\u4e0e\u5c45\u4e2d\uff0c
 *   SurfaceView \u628a Surface \u9501\u6b7b\u4e3a\u89c6\u9891\u539f\u59cb\u5206\u8fa8\u7387\uff0c\u5c0f\u4e8e View \u65f6\u8d34\u5de6\u4e0a\u89d2\u3002
 */
public class PlayerActivity extends Activity {
    static final String TAG = "DYP";
    static final String MUA = "Mozilla/5.0 (Linux; Android 12; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36";
    static final float LONG_PRESS_SPEED = 2.0f;

    TextureView texture;
    Surface surface;
    ExoPlayer player;

    TextView title, timeNow, timeAll, playPause, speedBtn, download, fullBtn, back, hint;
    SeekBar seekBar;
    View topBar, bottomBar;

    String url = "", wsUrl = "", awemeId = "", desc = "";

    Handler h = new Handler(Looper.getMainLooper());
    boolean controlsVisible = true;
    boolean seeking = false;
    boolean prepared = false;
    boolean longPressing = false;
    boolean lpRight = false;
    float downX = 0, downY = 0;
    int scaleRetry = 0;

    static final float[] SPEEDS = {0.5f, 1.0f, 1.25f, 1.5f, 2.0f};
    static final String[] SPEED_LABELS = {"0.5X", "1.0X", "1.25X", "1.5X", "2.0X"};
    int speedIdx = 1;

    int videoW = 0, videoH = 0, videoRot = 0;
    float par = 1f;

    @Override protected void onCreate(Bundle b){
        super.onCreate(b);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON, WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        applyCutout(true);
        setContentView(R.layout.activity_player);

        texture = (TextureView) findViewById(R.id.pVideo);
        title = (TextView) findViewById(R.id.pTitle);
        timeNow = (TextView) findViewById(R.id.pTimeNow);
        timeAll = (TextView) findViewById(R.id.pTimeAll);
        playPause = (TextView) findViewById(R.id.pPlayPause);
        speedBtn = (TextView) findViewById(R.id.pSpeed);
        download = (TextView) findViewById(R.id.pDownload);
        fullBtn = (TextView) findViewById(R.id.pFull);
        back = (TextView) findViewById(R.id.pBack);
        hint = (TextView) findViewById(R.id.pHint);
        seekBar = (SeekBar) findViewById(R.id.pSeek);
        topBar = findViewById(R.id.pTopBar);
        bottomBar = findViewById(R.id.pBottomBar);

        Intent it = getIntent();
        if(it != null){
            url = nz(it.getStringExtra("url"));
            wsUrl = nz(it.getStringExtra("ws"));
            awemeId = nz(it.getStringExtra("id"));
            desc = nz(it.getStringExtra("desc"));
        }
        title.setText(desc.length() > 0 ? desc : ("DY " + awemeId));

        if(url.length() == 0){ Toast.makeText(this, "\u65e0\u64ad\u653e\u5730\u5740", Toast.LENGTH_SHORT).show(); finish(); return; }

        bindListeners();

        texture.addOnLayoutChangeListener(new View.OnLayoutChangeListener(){
            public void onLayoutChange(View v, int l, int t, int r, int b, int ol, int ot, int or, int ob){
                if(r - l != or - ol || b - t != ob - ot) applyScale();
            }
        });

        texture.setSurfaceTextureListener(new TextureView.SurfaceTextureListener(){
            public void onSurfaceTextureAvailable(SurfaceTexture st, int w, int h){
                surface = new Surface(st);
                initPlayer();
            }
            public void onSurfaceTextureSizeChanged(SurfaceTexture st, int w, int h){ applyScale(); }
            public boolean onSurfaceTextureDestroyed(SurfaceTexture st){ return surface != null && surface.isValid(); }
            public void onSurfaceTextureUpdated(SurfaceTexture st){}
        });

        autoHide();
    }

    static String nz(String s){ return s == null ? "" : s; }

    void applyCutout(boolean on){
        try{
            if(android.os.Build.VERSION.SDK_INT >= 28){
                WindowManager.LayoutParams wa = getWindow().getAttributes();
                wa.layoutInDisplayCutoutMode = on
                    ? WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                    : WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT;
                getWindow().setAttributes(wa);
                if(on) getWindow().addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
                else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
            }
        }catch(Throwable t){}
    }

    void initPlayer(){
        try{
            DefaultHttpDataSource.Factory http = new DefaultHttpDataSource.Factory()
                .setUserAgent(MUA)
                .setDefaultRequestProperties(new java.util.HashMap<String,String>(){{ put("Referer", "https://www.douyin.com/"); }})
                .setAllowCrossProtocolRedirects(true);

            player = new ExoPlayer.Builder(this)
                .setMediaSourceFactory(new DefaultMediaSourceFactory(http))
                .setTrackSelector(new DefaultTrackSelector(this))
                .build();

            player.setVideoSurface(surface);

            player.addListener(new Player.Listener(){
                @Override public void onPlaybackStateChanged(int state){
                    if(state == Player.STATE_READY){
                        prepared = true;
                        long dur = player.getDuration();
                        if(dur > 0) timeAll.setText(fmt((int)dur));
                        playPause.setText("\u23f8");
                        Log.i(TAG, "EXO_READY dur=" + dur);
                    } else if(state == Player.STATE_ENDED){
                        playPause.setText("\u25b6");
                        showControls();
                    }
                }
                @Override public void onVideoSizeChanged(VideoSize vs){
                    videoW = vs.width;
                    videoH = vs.height;
                    par = vs.pixelWidthHeightRatio;
                    videoRot = 0;   // ExoPlayer \u81ea\u5df1\u5df2\u7ecf\u5904\u7406\u4e86\u65cb\u8f6c\uff0c\u8fd9\u91cc\u5bbd\u9ad8\u5df2\u662f\u5c55\u793a\u5c3a\u5bf8
                    Log.i(TAG, "EXO_VIDEO " + videoW + "x" + videoH + " par=" + par);
                    applyScale();
                    adaptOrientation();
                }
                @Override public void onPlayerError(androidx.media3.common.PlaybackException e){
                    Log.i(TAG, "EXO_ERR " + e.getErrorCodeName() + " " + e.getMessage());
                    showHint("\u64ad\u653e\u5931\u8d25", 1500);
                }
            });

            player.setMediaItem(MediaItem.fromUri(Uri.parse(url)));
            player.prepare();
            player.setPlayWhenReady(true);
            startProgress();
        }catch(Throwable t){
            Log.i(TAG, "EXO_INIT_ERR " + t);
            Throwable c = t.getCause();
            while(c != null){ Log.i(TAG, "EXO_INIT_CAUSE " + c); c = c.getCause(); }
        }
    }

    void applyScale(){
        if(texture == null || videoW <= 0 || videoH <= 0) return;
        int viewW = texture.getWidth();
        int viewH = texture.getHeight();
        if(viewW <= 0 || viewH <= 0){
            if(scaleRetry++ < 30) h.postDelayed(new Runnable(){ public void run(){ applyScale(); }}, 100);
            return;
        }
        scaleRetry = 0;

        float vw = videoW * par, vh = videoH;
        float scale = Math.min(viewW / vw, viewH / vh);
        float dispW = vw * scale, dispH = vh * scale;
        float dx = (viewW - dispW) / 2f;
        float dy = (viewH - dispH) / 2f;

        // TextureView \u7684\u5185\u5bb9\u88ab\u9ed8\u8ba4\u62c9\u4f38\u5230\u6574\u4e2a View\uff0c
        // \u6240\u4ee5\u5148\u628a\u7f29\u653e\u57fa\u51c6\u5f52\u4e00\u5230 dispW/viewW \u4e0e dispH/viewH\uff0c\u518d\u5c45\u4e2d\u5e73\u79fb\u3002
        float sx = dispW / viewW;
        float sy = dispH / viewH;

        // 以左上角为原点缩放，再平移到居中位置
        Matrix m = new Matrix();
        m.setScale(sx, sy);
        m.postTranslate(dx, dy);
        texture.setTransform(m);
        Log.i(TAG, "SCALE view=" + viewW + "x" + viewH + " video=" + (int)vw + "x" + (int)vh
                + " disp=" + (int)dispW + "x" + (int)dispH + " d=" + (int)dx + "/" + (int)dy);

        // 控制栏跟随视频画面：下栏贴视频底边，上栏贴视频顶边
        if(bottomBar != null){
            bottomBar.setTranslationY(-dy);
        }
        if(topBar != null){
            topBar.setTranslationY(dy);
        }
    }

    void adaptOrientation(){
        if(videoW <= 0 || videoH <= 0) return;
        if(videoH > videoW){
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            Log.i(TAG, "ORIENT portrait " + videoW + "x" + videoH);
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            Log.i(TAG, "ORIENT landscape " + videoW + "x" + videoH);
        }
    }

    void applySpeed(){
        if(player == null) return;
        float sp = longPressing ? LONG_PRESS_SPEED : SPEEDS[speedIdx];
        try{
            player.setPlaybackParameters(new PlaybackParameters(sp, 1.0f));
            Log.i(TAG, "EXO_SPEED " + sp + " ok");
        }catch(Throwable t){ Log.i(TAG, "EXO_SPEED_ERR " + t); }
        if(!longPressing) speedBtn.setText(SPEED_LABELS[speedIdx]);
    }

    void startProgress(){
        h.post(new Runnable(){ public void run(){
            if(player != null && prepared){
                try{
                    long dur = player.getDuration();
                    long cur = player.getCurrentPosition();
                    timeNow.setText(fmt((int)cur));
                    if(dur > 0){
                        timeAll.setText(fmt((int)dur));
                        if(!seeking) seekBar.setProgress((int)(cur * 1000 / dur));
                    }
                }catch(Throwable t){}
            }
            h.postDelayed(this, 300);
        }});
    }

    static String fmt(int ms){
        if(ms < 0) ms = 0;
        int s2 = ms / 1000;
        return String.format("%02d:%02d", s2 / 60, s2 % 60);
    }

    void autoHide(){ h.removeCallbacks(hideR); h.postDelayed(hideR, 4000); }
    Runnable hideR = new Runnable(){ public void run(){ hideControls(); } };

    void showControls(){
        controlsVisible = true;
        topBar.setVisibility(View.VISIBLE);
        bottomBar.setVisibility(View.VISIBLE);
        topBar.animate().alpha(1f).setDuration(160).start();
        bottomBar.animate().alpha(1f).setDuration(160).start();
        autoHide();
    }
    void hideControls(){
        if(!controlsVisible) return;
        controlsVisible = false;
        topBar.animate().alpha(0f).setDuration(220).withEndAction(new Runnable(){ public void run(){ topBar.setVisibility(View.GONE); }}).start();
        bottomBar.animate().alpha(0f).setDuration(220).withEndAction(new Runnable(){ public void run(){ bottomBar.setVisibility(View.GONE); }}).start();
    }
    void toggleControls(){ if(controlsVisible) hideControls(); else showControls(); }

    void showHint(String t, long ms){
        hint.setText(t);
        hint.setVisibility(View.VISIBLE);
        h.removeCallbacks(hintR);
        h.postDelayed(hintR, ms);
    }
    Runnable hintR = new Runnable(){ public void run(){ hint.setVisibility(View.GONE); } };

    void bindListeners(){
        back.setOnClickListener(new View.OnClickListener(){ public void onClick(View x){ finish(); }});
        playPause.setOnClickListener(new View.OnClickListener(){ public void onClick(View x){
            if(player == null) return;
            if(player.isPlaying()){ player.pause(); playPause.setText("\u25b6"); showHint("\u23f8 \u5df2\u6682\u505c", 900); }
            else { player.play(); playPause.setText("\u23f8"); }
            showControls();
        }});
        speedBtn.setOnClickListener(new View.OnClickListener(){ public void onClick(View x){ showSpeedDialog(); }});
        download.setOnClickListener(new View.OnClickListener(){ public void onClick(View x){ doDownload(); }});
        fullBtn.setOnClickListener(new View.OnClickListener(){ public void onClick(View x){ toggleOrientation(); }});

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar sb, int p, boolean fromUser){
                if(fromUser && player != null){
                    long dur = player.getDuration();
                    if(dur > 0) timeNow.setText(fmt((int)(dur * p / 1000)));
                }
            }
            public void onStartTrackingTouch(SeekBar sb){ seeking = true; showControls(); }
            public void onStopTrackingTouch(SeekBar sb){
                seeking = false;
                if(player != null){
                    long dur = player.getDuration();
                    if(dur > 0){
                        long target = dur * sb.getProgress() / 1000;
                        player.seekTo(target);
                        timeNow.setText(fmt((int)target));
                    }
                }
                autoHide();
            }
        });

        final GestureDetector gd = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener(){
            public boolean onDown(MotionEvent e){ return true; }
            public boolean onSingleTapConfirmed(MotionEvent e){ toggleControls(); return true; }
            public boolean onDoubleTap(MotionEvent e){
                if(player == null) return true;
                if(player.isPlaying()){
                    player.pause();
                    playPause.setText("\u25b6");
                    showHint("\u23f8 \u5df2\u6682\u505c", 900);
                } else {
                    player.play();
                    playPause.setText("\u23f8");
                    showHint("\u25b6 \u64ad\u653e\u4e2d", 900);
                }
                showControls();
                return true;
            }
        });

        texture.setOnTouchListener(new View.OnTouchListener(){
            public boolean onTouch(View v, MotionEvent ev){
                gd.onTouchEvent(ev);
                switch(ev.getActionMasked()){
                    case MotionEvent.ACTION_DOWN:
                        downX = ev.getX(); downY = ev.getY();
                        longPressing = false;
                        h.postDelayed(lpStart, 400);
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        h.removeCallbacks(lpStart);
                        if(longPressing) stopLongPress();
                        break;
                }
                return true;
            }
        });
    }

    // 长按右侧 = 2x 快放 / 长按左侧 = 连续回退
    Handler seekHandler = new Handler(Looper.getMainLooper());
    Runnable seekTicker;

    Runnable lpStart = new Runnable(){ public void run(){
        if(!prepared || player == null) return;
        longPressing = true;
        lpRight = downX > texture.getWidth() / 2f;
        if(lpRight){
            applySpeed();                          // 真 2x，音轨一起变
            showHint("\u21bb 2X \u5feb\u653e\u4e2d", 60000);
        } else {
            startRewind();                         // 左侧：连续回退 seek
        }
    }};

    /** 左侧长按：每 100ms 回退 400ms，视觉上约 4x 倒退 */
    void startRewind(){
        seekHandler.removeCallbacksAndMessages(null);
        showHint("\u21ba  \u56de\u9000\u4e2d", 60000);
        seekTicker = new Runnable(){ public void run(){
            if(!longPressing || player == null){ return; }
            try{
                long cur = player.getCurrentPosition();
                long dur = player.getDuration();
                long target = cur - 400;
                if(target < 0){ target = 0; longPressing = false; showHint("\u5df2\u5230\u5f00\u5934", 800); return; }
                player.seekTo(target);
                showHint("\u21ba  " + fmt((int)target) + " / " + fmt((int)dur), 3000);
            }catch(Throwable t){}
            seekHandler.postDelayed(this, 100);
        }};
        seekHandler.post(seekTicker);
    }

    void stopRewind(){
        seekHandler.removeCallbacksAndMessages(null);
        seekTicker = null;
    }

    void stopLongPress(){
        longPressing = false;
        stopRewind();
        h.removeCallbacks(lpStart);
        applySpeed();
        showHint("\u6062\u590d " + SPEED_LABELS[speedIdx], 800);
        showControls();
    }

    void toggleOrientation(){
        int o = getResources().getConfiguration().orientation;
        if(o == Configuration.ORIENTATION_LANDSCAPE) setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        else setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
    }

    void showSpeedDialog(){
        final String[] items = {"0.5X", "1.0X", "1.25X", "1.5X", "2.0X"};
        new AlertDialog.Builder(this)
            .setTitle("\u64ad\u653e\u901f\u5ea6")
            .setSingleChoiceItems(items, speedIdx, new DialogInterface.OnClickListener(){
                public void onClick(DialogInterface d, int which){
                    speedIdx = which;
                    applySpeed();
                    showHint("\u500d\u901f " + items[which], 900);
                    d.dismiss(); autoHide();
                }
            })
            .setNegativeButton("\u53d6\u6d88", null)
            .show();
    }

    void doDownload(){
        String d = url.length() > 0 ? url : wsUrl;
        if(d.length() == 0){ showHint("\u65e0\u4e0b\u8f7d\u5730\u5740", 1200); return; }
        try{
            String name = "dy_" + (awemeId.length() > 0 ? awemeId : String.valueOf(System.currentTimeMillis())) + "_" + System.currentTimeMillis() + ".mp4";
            DownloadManager dm = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            DownloadManager.Request r = new DownloadManager.Request(Uri.parse(d));
            r.setTitle(name);
            r.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            r.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name);
            r.addRequestHeader("User-Agent", MUA);
            r.addRequestHeader("Referer", "https://www.douyin.com/");
            dm.enqueue(r);
            showHint("\u5df2\u52a0\u5165\u4e0b\u8f7d\uff1aDownload/" + name, 2200);
        }catch(Exception e){ Log.i(TAG, "DL_ERR " + e); showHint("\u4e0b\u8f7d\u5931\u8d25", 1500); }
    }

    @Override public void onConfigurationChanged(Configuration cfg){
        super.onConfigurationChanged(cfg);
        h.postDelayed(new Runnable(){ public void run(){ applyScale(); }}, 150);
    }

    @Override protected void onPause(){ super.onPause(); try{ if(player != null) player.pause(); }catch(Throwable t){} }

    @Override protected void onDestroy(){
        super.onDestroy();
        h.removeCallbacksAndMessages(null);
        try{ if(player != null){ player.release(); player = null; } }catch(Throwable t){}
        try{ if(surface != null){ surface.release(); surface = null; } }catch(Throwable t){}
    }
}
