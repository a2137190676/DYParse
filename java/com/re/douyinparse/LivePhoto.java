package com.re.douyinparse;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;

/**
 * 实况照片（Motion Photo）封装器。
 *
 * ===== 原理 =====
 * Android / Google 相册认的实况照片，是「JPEG + MP4」拼在同一个 .jpg 文件里：
 *
 *   [ JPEG 主体 ][ MP4 数据 ][ XMP 元数据 ]
 *
 * 关键在两个 XMP 字段（写在 JPEG 的 APP1 段里）：
 *   GCamera:MicroVideoOffset = MP4 相对文件尾的偏移（距文件末尾的字节数）
 *   GCamera:MicroVideo     = 1
 *   GCamera:MicroVideoPresentationTimestampUs = 0
 *
 * 相册读到这几个标记，就知道「这个 jpg 后面还挂了一段视频」，
 * 长按照片即可播放。Apple 的 Live Photo 是另一套（jpg+mov 双文件 + UUID 关联），
 * 这里不做，因为目标机型是 Android。
 *
 * ===== 兼容性 =====
 * - Android 相册 / Google 相册：认
 * - 微信、QQ 发送：会退化成静态图（正常）
 * - 部分第三方相册：可能只显示静态图
 */
public final class LivePhoto {

    static final String TAG = "DYP";

    /** 封装结果 */
    public static class Result {
        public boolean ok = false;
        public String path = "";
        public long size = 0;
        public String err = "";
        public Result(boolean o, String p, long s, String e){
            ok = o; path = p; size = s; err = e;
        }
    }

    /**
     * 把「WEBP/JPEG 静态图 + MP4 动轨」封装成一个实况照片。
     *
     * @param imgBytes 静态图原始字节（WEBP 或 JPEG 都行，内部会转 JPEG）
     * @param mp4Bytes 动轨 MP4 原始字节
     * @param out      输出文件（后缀建议 .jpg）
     */
    public static Result build(byte[] imgBytes, byte[] mp4Bytes, File out){
        try{
            if(imgBytes == null || imgBytes.length < 100){
                return new Result(false, "", 0, "静态图为空");
            }
            if(mp4Bytes == null || mp4Bytes.length < 1000){
                return new Result(false, "", 0, "动轨视频为空");
            }
            if(mp4Bytes.length > Integer.MAX_VALUE - imgBytes.length - 65536){
                return new Result(false, "", 0, "文件过大");
            }

            // 1) 静态图统一转成 JPEG（抖音给的是 WEBP，直接拼相册不认）
            byte[] jpeg = toJpeg(imgBytes);
            if(jpeg == null || jpeg.length < 100){
                return new Result(false, "", 0, "图片转 JPEG 失败");
            }
            byte[] mp4 = mp4Bytes;

            // 2) XMP 段。V2 格式不带 MicroVideoOffset，
            //    靠 Container:Directory 里 Item:Length 描述视频长度，
            //    相册从「文件尾 - 视频长度」反推 MP4 起点。
            byte[] xmpSeg = app1Segment(xmp(mp4.length), mp4.length);

            // 3) 拼装：[SOI][XMP APP1][JPEG剩余][MP4]
            ByteArrayOutputStream bo = new ByteArrayOutputStream(
                    jpeg.length + xmpSeg.length + mp4.length + 64);
            bo.write(jpeg, 0, 2);                        // SOI
            bo.write(xmpSeg, 0, xmpSeg.length);          // 实况标记
            bo.write(jpeg, 2, jpeg.length - 2);          // 原 JPEG 剩余
            bo.write(mp4, 0, mp4.length);                // 动轨
            byte[] all = bo.toByteArray();

            // 4) 落盘
            File parent = out.getParentFile();
            if(parent != null && !parent.exists()) parent.mkdirs();
            FileOutputStream fo = new FileOutputStream(out);
            try{
                fo.write(all);
                fo.flush();
            } finally {
                try{ fo.close(); }catch(Throwable t){}
            }

            Log.i(TAG, "LIVE_BUILD ok jpeg=" + jpeg.length + " mp4=" + mp4.length
                    + " xmp=" + xmpSeg.length + " total=" + all.length);
            return new Result(true, out.getAbsolutePath(), all.length, "");
        }catch(Throwable t){
            Log.i(TAG, "LIVE_BUILD_ERR " + t);
            return new Result(false, "", 0, "" + t);
        }
    }

    /** 任意图片字节（WEBP/JPEG/PNG）→ JPEG 字节 */
    static byte[] toJpeg(byte[] raw){
        try{
            // 已经是 JPEG 就直接用（避免二次压缩掉画质）
            if(raw.length > 3 && (raw[0] & 0xFF) == 0xFF && (raw[1] & 0xFF) == 0xD8){
                return raw;
            }
            BitmapFactory.Options opt = new BitmapFactory.Options();
            opt.inPreferredConfig = Bitmap.Config.ARGB_8888;
            Bitmap bm = BitmapFactory.decodeByteArray(raw, 0, raw.length, opt);
            if(bm == null) return null;
            ByteArrayOutputStream bo = new ByteArrayOutputStream(raw.length / 2 + 65536);
            bm.compress(Bitmap.CompressFormat.JPEG, 95, bo);
            bm.recycle();
            return bo.toByteArray();
        }catch(Throwable t){
            Log.i(TAG, "LIVE_TOJPEG_ERR " + t);
            return null;
        }
    }

    /**
     * XMP 包体（不含 APP1 头）。
     *
     * 采用 Google Motion Photo V2 格式（GCamera:MotionPhoto）：
     *   - 这是 Android 8+ / Google 相册 / OPPO·一加·华为相册认的写法
     *   - 老的 GCamera:MicroVideo 只在 Android 7 前的 Google 相机上有效，
     *     现在的国产 ROM 相册基本只认 MotionPhoto
     * 另外补一份 OpCamera 私有命名空间，兼容 OPPO/一加系（实测本机就是 oplus）。
     *
     * @param videoLen MP4 字节长度（写进 Item:Length 与 OpCamera:VideoLength）
     */
    static byte[] xmp(long videoLen){
        String s =
            "http://ns.adobe.com/xap/1.0/\u0000" +
            "<x:xmpmeta xmlns:x=\"adobe:ns:meta/\" x:xmptk=\"Adobe XMP Core 5.1.0\">" +
            "<rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\">" +
            "<rdf:Description rdf:about=\"\"" +
            " xmlns:GCamera=\"http://ns.google.com/photos/1.0/camera/\"" +
            " xmlns:OpCamera=\"http://ns.oplus.com/photos/1.0/camera/\"" +
            " xmlns:Container=\"http://ns.google.com/photos/1.0/container/\"" +
            " xmlns:Item=\"http://ns.google.com/photos/1.0/container/item/\"" +
            " GCamera:MotionPhoto=\"1\"" +
            " GCamera:MotionPhotoVersion=\"1\"" +
            " GCamera:MotionPhotoPresentationTimestampUs=\"0\"" +
            " OpCamera:MotionPhotoPrimaryPresentationTimestampUs=\"0\"" +
            " OpCamera:MotionPhotoOwner=\"oplus\"" +
            " OpCamera:OLivePhotoVersion=\"2\"" +
            " OpCamera:VideoLength=\"" + videoLen + "\">" +
            "<Container:Directory>" +
            "<rdf:Seq>" +
            "<rdf:li rdf:parseType=\"Resource\">" +
            "<Container:Item Item:Mime=\"image/jpeg\" Item:Semantic=\"Primary\"" +
            " Item:Length=\"0\" Item:Padding=\"0\"/>" +
            "</rdf:li>" +
            "<rdf:li rdf:parseType=\"Resource\">" +
            "<Container:Item Item:Mime=\"video/mp4\" Item:Semantic=\"MotionPhoto\"" +
            " Item:Length=\"" + videoLen + "\" Item:Padding=\"0\"/>" +
            "</rdf:li>" +
            "</rdf:Seq>" +
            "</Container:Directory>" +
            "</rdf:Description>" +
            "</rdf:RDF>" +
            "</x:xmpmeta>";
        return s.getBytes(Charset.forName("UTF-8"));
    }

    /** APP1 段总长度（含 2 字节标记 + 2 字节长度字段） */
    static int app1SegmentLength(byte[] payload){
        return 2 + 2 + payload.length;
    }

    /**
     * 生成完整 APP1 段：FFE1 + 长度(2) + 载荷
     * 注意：载荷里的 offset 字段必须已经按最终值填好。
     */
    static byte[] app1Segment(byte[] payload, int mp4Len){
        int len = payload.length + 2;   // 长度字段自身 2 字节
        if(len > 0xFFFF){
            // 超长（极少见）：截断会破坏 XMP，这里直接放弃
            Log.i(TAG, "LIVE_XMP_TOO_LONG " + len);
            len = 0xFFFF;
        }
        byte[] out = new byte[2 + 2 + payload.length];
        out[0] = (byte)0xFF;
        out[1] = (byte)0xE1;
        out[2] = (byte)((len >> 8) & 0xFF);
        out[3] = (byte)(len & 0xFF);
        System.arraycopy(payload, 0, out, 4, payload.length);
        return out;
    }

    private LivePhoto(){}
}