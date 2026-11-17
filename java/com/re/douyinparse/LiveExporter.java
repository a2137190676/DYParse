package com.re.douyinparse;

import android.util.Log;

import com.arthenica.ffmpegkit.FFmpegKit;
import com.arthenica.ffmpegkit.FFmpegSession;
import com.arthenica.ffmpegkit.FFmpegSessionCompleteCallback;
import com.arthenica.ffmpegkit.ReturnCode;
import com.arthenica.ffmpegkit.Statistics;
import com.arthenica.ffmpegkit.StatisticsCallback;

import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 实况图动轨 → GIF 动图。
 *
 * 用工程里现成的 ffmpeg-kit（audio 版，已验证内置 GIF encoder +
 * palettegen/paletteuse 滤镜，不需要新增依赖）。
 *
 * 转码参数：
 *   - fps=12：动轨一般 1~3 秒，12 帧足够顺滑且体积可控
 *   - scale=480:-2：宽度压到 480 保持比例，适合聊天发送
 *   - 双通道调色板（palettegen + paletteuse）：色彩比单通道好很多
 */
public final class LiveExporter {

    static final String TAG = "DYP";

    /** 转码进度回调：0~1 */
    public interface ProgressCb {
        void onProgress(float r);
    }

    /**
     * 把动轨 MP4 字节转成 GIF。
     *
     * @param mp4Bytes 动轨视频字节
     * @param out      输出文件（后缀 .gif）
     * @param cacheDir 临时目录（放输入的 mp4，转完即删）
     * @param cb       进度回调（可为 null）
     */
    public static boolean convertGif(byte[] mp4Bytes, File out, File cacheDir, final ProgressCb cb){
        File tmpIn = null;
        try{
            if(mp4Bytes == null || mp4Bytes.length < 1000) return false;
            File parent = out.getParentFile();
            if(parent != null && !parent.exists()) parent.mkdirs();
            // 1) 动轨先落临时 mp4（ffmpeg 按文件读）
            tmpIn = new File(cacheDir, "live_" + System.currentTimeMillis() + ".mp4");
            FileOutputStream fo = new FileOutputStream(tmpIn);
            try{
                fo.write(mp4Bytes);
                fo.flush();
            } finally {
                try{ fo.close(); }catch(Throwable t){}
            }
            if(out.exists()) out.delete();

            // 2) 双通道调色板转码
            String cmd = "-hide_banner -y -i \"" + tmpIn.getAbsolutePath()
                    + "\" -vf \"fps=12,scale=480:-2:flags=lanczos,split[s0][s1];"
                    + "[s0]palettegen=max_colors=128[p];[s1][p]paletteuse=dither=bayer\" \""
                    + out.getAbsolutePath() + "\"";
            Log.i(TAG, "LIVE_GIF cmd " + cmd);

            final CountDownLatch latch = new CountDownLatch(1);
            final boolean[] ok = new boolean[]{ false };
            FFmpegSession session = FFmpegKit.executeAsync(cmd,
                new FFmpegSessionCompleteCallback(){
                    @Override public void apply(FFmpegSession s){
                        try{
                            ReturnCode rc = s.getReturnCode();
                            long len = out.exists() ? out.length() : 0;
                            ok[0] = ReturnCode.isSuccess(rc) && len > 1024;
                            Log.i(TAG, "LIVE_GIF rc=" + rc + " size=" + len);
                            if(!ok[0]){
                                String logs = s.getOutput();
                                if(logs != null && logs.length() > 0){
                                    int st = Math.max(0, logs.length() - 300);
                                    Log.i(TAG, "LIVE_GIF log " + logs.substring(st));
                                }
                            }
                        }catch(Throwable t){
                            Log.i(TAG, "LIVE_GIF_CB_ERR " + t);
                        }finally{
                            latch.countDown();
                        }
                    }
                },
                null,
                new StatisticsCallback(){
                    @Override public void apply(Statistics st){
                        if(cb == null) return;
                        try{
                            long ms = (long) st.getTime();
                            // 动轨 1~3 秒，20 秒内必完
                            cb.onProgress(Math.max(0f, Math.min(1f, ms / 20000f)));
                        }catch(Throwable t){}
                    }
                });

            boolean finished = latch.await(5, TimeUnit.MINUTES);
            if(!finished){
                Log.i(TAG, "LIVE_GIF timeout, cancel");
                FFmpegKit.cancel(session.getSessionId());
                return false;
            }
            return ok[0];
        }catch(Throwable t){
            Log.i(TAG, "LIVE_GIF_ERR " + t);
            return false;
        }finally{
            try{ if(tmpIn != null && tmpIn.exists()) tmpIn.delete(); }catch(Throwable t){}
        }
    }

    private LiveExporter(){}
}
