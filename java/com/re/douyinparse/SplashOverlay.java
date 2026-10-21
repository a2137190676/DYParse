package com.re.douyinparse;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 启动动画：在 Activity 内容之上盖一层，播完后淡出移除。
 *
 * 为什么不用 SplashActivity：
 *   多一个 Activity 就多一次冷启动开销和一次窗口切换动画，
 *   这里直接在同一个窗口里盖层，启动更快、无白屏切换。
 *
 * 动画节奏（总计约 620ms）：
 *   0ms     圆形图标从 0.82 缩放到 1.0 + 淡入
 *   120ms   图标上浮 8dp
 *   180ms   标题逐字淡入
 *   420ms   整体淡出
 *   620ms   移除视图
 */
public class SplashOverlay {

    /** 播完回调 */
    public interface OnDone { void onFinished(); }

    static Handler h = new Handler(Looper.getMainLooper());

    /**
     * 显示启动动画。
     * @param act    宿主 Activity
     * @param dark   是否夜间（决定底色）
     * @param onDone 播完回调（可为 null）
     */
    public static void show(final Activity act, boolean dark, final OnDone onDone){
        if(act == null || act.isFinishing()) {
            if(onDone != null) onDone.onFinished();
            return;
        }
        final ViewGroup decor;
        try{
            decor = (ViewGroup) act.getWindow().getDecorView();
        }catch(Throwable t){
            if(onDone != null) onDone.onFinished();
            return;
        }

        final FrameLayout layer = new FrameLayout(act);
        layer.setLayoutParams(new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        // 底色：夜间纯深色，日间浅灰，避免纯白刺眼
        layer.setBackgroundColor(dark ? 0xFF0E0F13 : 0xFFF5F6F8);
        layer.setClickable(true);   // 挡住下层点击

        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams boxLp = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        boxLp.gravity = Gravity.CENTER;
        box.setLayoutParams(boxLp);

        // 图标
        ImageView icon = new ImageView(act);
        int sz = (int)(act.getResources().getDisplayMetrics().density * 92);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(sz, sz);
        ilp.gravity = Gravity.CENTER_HORIZONTAL;
        icon.setLayoutParams(ilp);
        try{
            icon.setImageResource(R.drawable.ic_launcher);
        }catch(Throwable t){}
        box.addView(icon);

        // 标题
        final TextView title = new TextView(act);
        title.setText("解析器");
        title.setTextSize(19);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        title.setTextColor(dark ? 0xFFF2F3F5 : 0xFF1A1A1F);
        title.setAlpha(0f);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tlp.gravity = Gravity.CENTER_HORIZONTAL;
        tlp.topMargin = (int)(act.getResources().getDisplayMetrics().density * 16);
        title.setLayoutParams(tlp);
        box.addView(title);

        // 副标题（多平台标识）
        final TextView sub = new TextView(act);
        sub.setText("抖音 · 快手 · TikTok");
        sub.setTextSize(11);
        sub.setTextColor(dark ? 0xFF63697A : 0xFFA8ACB6);
        sub.setAlpha(0f);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        slp.gravity = Gravity.CENTER_HORIZONTAL;
        slp.topMargin = (int)(act.getResources().getDisplayMetrics().density * 6);
        sub.setLayoutParams(slp);
        box.addView(sub);

        layer.addView(box);
        decor.addView(layer);

        // ---- 动画 ----
        final float d = act.getResources().getDisplayMetrics().density;
        icon.setScaleX(0.82f);
        icon.setScaleY(0.82f);
        icon.setAlpha(0f);

        icon.animate().alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(260).setInterpolator(new DecelerateInterpolator(1.4f))
            .start();

        h.postDelayed(new Runnable(){ public void run(){
            if(act.isFinishing()) return;
            icon.animate().translationY(-8 * d).setDuration(240).start();
        }}, 120);

        h.postDelayed(new Runnable(){ public void run(){
            if(act.isFinishing()) return;
            title.animate().alpha(1f).setDuration(200).start();
            sub.animate().alpha(1f).setDuration(240).start();
        }}, 180);

        // 淡出并移除
        h.postDelayed(new Runnable(){ public void run(){
            if(act.isFinishing()){ remove(decor, layer); return; }
            layer.animate().alpha(0f).setDuration(200).start();
            h.postDelayed(new Runnable(){ public void run(){
                remove(decor, layer);
                if(onDone != null) onDone.onFinished();
            }}, 205);
        }}, 420);
    }

    static void remove(ViewGroup parent, View v){
        try{ if(v != null && v.getParent() == parent) parent.removeView(v); }catch(Throwable t){}
    }
}