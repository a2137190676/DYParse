# -*- coding: utf-8 -*-
# 1) ProfileItem 加 cover 字段
# 2) MainActivity 加 loadNetThumb（网络缩略图 + 磁盘缓存）
import io

# --- 1) ProfileItem 加字段 ---
P1 = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/ProfileParser.java'
d1 = io.open(P1, encoding='utf-8').read()
old1 = '''        /** 图集（下载走图片队列） */
        public boolean isImage = false;'''
new1 = '''        /** 图集（下载走图片队列） */
        public boolean isImage = false;
        /** 封面图 URL（列表缩略图用，可为空） */
        public String cover = "";'''
if old1 in d1:
    d1 = d1.replace(old1, new1, 1)
    io.open(P1, 'w', encoding='utf-8').write(d1)
    print('ProfileItem.cover OK')
else:
    print('MISS ProfileItem')

# --- 2) MainActivity 加 loadNetThumb ---
P2 = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/MainActivity.java'
d2 = io.open(P2, encoding='utf-8').read()

anchor = '''    /** 采样解码，长边不超过 maxPx */'''
helper = '''    /** 网络图缩略图磁盘缓存目录 */
    java.io.File thumbCacheDir(){
        java.io.File f = new java.io.File(getCacheDir(), "thumbs");
        if (!f.exists()) f.mkdirs();
        return f;
    }

    /**
     * 网络图片缩略图：先查内存，再查磁盘，最后下载。
     * 下载后落盘，二次打开列表不用重新拉。
     */
    void loadNetThumb(final android.widget.ImageView iv, final String url, final int sizePx){
        if (iv == null || url == null || url.length() == 0) return;
        // 内存缓存
        android.graphics.Bitmap cached = thumbCache.get(url);
        if (cached != null) {
            iv.setImageBitmap(cached);
            iv.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
            return;
        }
        iv.setImageDrawable(thumbPlaceholder(2));

        final java.lang.ref.WeakReference<android.widget.ImageView> ref =
                new java.lang.ref.WeakReference<android.widget.ImageView>(iv);
        new Thread(new Runnable(){ public void run(){
            android.graphics.Bitmap bmp = null;
            try{
                // 磁盘缓存
                String hash = Integer.toHexString(url.hashCode());
                java.io.File cf = new java.io.File(thumbCacheDir(), hash + ".jpg");
                if (cf.exists() && cf.length() > 0) {
                    bmp = decodeScaled(cf.getAbsolutePath(), sizePx);
                }
                if (bmp == null) {
                    byte[] raw = httpGetBytes(url, null);
                    if (raw != null && raw.length > 0) {
                        try{
                            java.io.FileOutputStream fos = new java.io.FileOutputStream(cf);
                            try { fos.write(raw); } finally { try{ fos.close(); }catch(Throwable t){} }
                        }catch(Throwable t){}
                        android.graphics.BitmapFactory.Options o =
                                new android.graphics.BitmapFactory.Options();
                        o.inSampleSize = 1;
                        android.graphics.Bitmap src = android.graphics.BitmapFactory
                                .decodeByteArray(raw, 0, raw.length, o);
                        if (src != null) {
                            int w = src.getWidth(), hh = src.getHeight();
                            int longSide = Math.max(w, hh);
                            if (longSide > sizePx * 2) {
                                float sc = (float)(sizePx * 2) / longSide;
                                bmp = android.graphics.Bitmap.createScaledBitmap(src,
                                        Math.max(1, (int)(w * sc)), Math.max(1, (int)(hh * sc)), true);
                                src.recycle();
                            } else {
                                bmp = src;
                            }
                        }
                    }
                }
            }catch(Throwable t){
                Log.i("DYP", "NET_THUMB_ERR " + t);
            }
            if (bmp == null) return;
            thumbCache.put(url, bmp);
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

''' + anchor

if anchor in d2 and 'void loadNetThumb' not in d2:
    d2 = d2.replace(anchor, helper, 1)
    io.open(P2, 'w', encoding='utf-8').write(d2)
    print('loadNetThumb OK')
else:
    print('MISS/EXISTS loadNetThumb')

# 检查 httpGetBytes 签名
import re
i = d2.find('byte[] httpGetBytes(')
print('httpGetBytes 签名:', d2[i:i+120].split('{')[0] if i > 0 else 'MISS')