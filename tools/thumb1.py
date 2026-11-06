# -*- coding: utf-8 -*-
# 遗留项 2：历史页缩略图
#   - 图片：直接解码采样
#   - 视频：MediaMetadataRetriever 抽首帧
#   - 音频/其他：类型图标底
#   异步加载 + LRU 缓存，避免卡列表
import io

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/MainActivity.java'
d = io.open(P, encoding='utf-8').read()

# 1) 插入缩略图工具方法（放在 refreshHistory 前）
anchor = '''    void refreshHistory(){'''
helper = '''    // ===== 缩略图工具（列表/历史共用） =====

    /** 缩略图内存缓存：key=路径+mtime，避免重复解码 */
    final android.util.LruCache<String, android.graphics.Bitmap> thumbCache =
            new android.util.LruCache<String, android.graphics.Bitmap>(96){
                @Override protected int sizeOf(String k, android.graphics.Bitmap b){
                    return b == null ? 1 : Math.max(1, b.getByteCount() / 1024);
                }
            };

    /**
     * 给 ImageView 异步加载缩略图。
     * 视频抽首帧，图片解码缩放，其它显示类型图标。
     * sizePx 是期望的边长（dp 已换算）。
     */
    void loadThumb(final android.widget.ImageView iv, final java.io.File f,
                   final int sizePx, final int kind){
        if (iv == null || f == null) return;
        final String key = f.getAbsolutePath() + "|" + f.lastModified();
        android.graphics.Bitmap cached = thumbCache.get(key);
        if (cached != null) {
            iv.setImageBitmap(cached);
            iv.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
            return;
        }
        // 先放类型图标当占位
        iv.setImageDrawable(thumbPlaceholder(kind));
        iv.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);

        final java.lang.ref.WeakReference<android.widget.ImageView> ref =
                new java.lang.ref.WeakReference<android.widget.ImageView>(iv);
        new Thread(new Runnable(){ public void run(){
            android.graphics.Bitmap bmp = null;
            try{
                if (kind == 2) {
                    bmp = decodeScaled(f.getAbsolutePath(), sizePx);
                } else if (kind == 0) {
                    bmp = videoFirstFrame(f.getAbsolutePath(), sizePx);
                }
            }catch(Throwable t){
                Log.i("DYP", "THUMB_ERR " + t);
            }
            if (bmp == null) return;
            thumbCache.put(key, bmp);
            final android.graphics.Bitmap fb = bmp;
            h.post(new Runnable(){ public void run(){
                android.widget.ImageView v = ref.get();
                if (v != null) {
                    try{
                        v.setImageBitmap(fb);
                        v.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
                    }catch(Throwable t){}
                }
            }});
        }}).start();
    }

    /** 采样解码，长边不超过 maxPx */
    static android.graphics.Bitmap decodeScaled(String path, int maxPx){
        try{
            android.graphics.BitmapFactory.Options o = new android.graphics.BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            android.graphics.BitmapFactory.decodeFile(path, o);
            int w = o.outWidth, hh = o.outHeight;
            if (w <= 0 || hh <= 0) return null;
            int sample = 1;
            while (w / sample > maxPx * 2 || hh / sample > maxPx * 2) sample *= 2;
            android.graphics.BitmapFactory.Options o2 = new android.graphics.BitmapFactory.Options();
            o2.inSampleSize = sample;
            o2.inPreferredConfig = android.graphics.Bitmap.Config.RGB_565;
            return android.graphics.BitmapFactory.decodeFile(path, o2);
        }catch(Throwable t){
            return null;
        }
    }

    /** 抽视频首帧 */
    static android.graphics.Bitmap videoFirstFrame(String path, int maxPx){
        android.media.MediaMetadataRetriever mmr = null;
        try{
            mmr = new android.media.MediaMetadataRetriever();
            mmr.setDataSource(path);
            android.graphics.Bitmap b = mmr.getFrameAtTime(1000 * 1000,
                    android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
            if (b == null) b = mmr.getFrameAtTime();
            if (b == null) return null;
            int w = b.getWidth(), hh = b.getHeight();
            int longSide = Math.max(w, hh);
            if (longSide <= maxPx) return b;
            float sc = (float) maxPx / longSide;
            android.graphics.Bitmap r = android.graphics.Bitmap.createScaledBitmap(
                    b, Math.max(1, (int)(w * sc)), Math.max(1, (int)(hh * sc)), true);
            if (r != b) b.recycle();
            return r;
        }catch(Throwable t){
            return null;
        }finally{
            try{ if (mmr != null) mmr.release(); }catch(Throwable t){}
        }
    }

    /** 占位图：类型图标 + 圆角底 */
    android.graphics.drawable.Drawable thumbPlaceholder(int kind){
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setCornerRadius(dp(8));
        g.setColor(curDark ? 0xFF2C3038 : 0xFFEDF0F5);
        return g;
    }

    /** 缩略图容器（圆角裁剪） */
    android.widget.ImageView makeThumbView(int sizePx){
        android.widget.ImageView iv = new android.widget.ImageView(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(sizePx, sizePx);
        lp.rightMargin = dp(10);
        iv.setLayoutParams(lp);
        iv.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
        iv.setBackground(makeRoundRect(curDark ? 0xFF2C3038 : 0xFFEDF0F5, 0, dp(8)));
        iv.setClipToOutline(true);
        try{
            iv.setOutlineProvider(new android.view.ViewOutlineProvider(){
                public void getOutline(View v, android.graphics.Outline o){
                    o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), dp(8));
                }
            });
        }catch(Throwable t){}
        return iv;
    }

''' + anchor

if anchor in d and 'void loadThumb' not in d:
    d = d.replace(anchor, helper, 1)
    io.open(P, 'w', encoding='utf-8').write(d)
    print('缩略图工具 OK')
else:
    print('MISS/EXISTS')