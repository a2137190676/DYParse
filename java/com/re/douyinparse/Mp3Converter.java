package com.re.douyinparse;

import android.media.MediaMetadataRetriever;
import android.util.Log;

import com.arthenica.ffmpegkit.FFmpegKit;
import com.arthenica.ffmpegkit.FFmpegSession;
import com.arthenica.ffmpegkit.FFmpegSessionCompleteCallback;
import com.arthenica.ffmpegkit.ReturnCode;
import com.arthenica.ffmpegkit.Statistics;
import com.arthenica.ffmpegkit.StatisticsCallback;

import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** 用 ffmpeg-kit 将视频音轨转成 MP3 */
public class Mp3Converter {

    static final String TAG = "DYP";

    /** 进度回调：ratio 0~1，timeMs 为已处理时长 */
    public interface Cb {
        void onProgress(float ratio, long timeMs);
        void onDone(boolean ok, String msg);
    }

    public static boolean convert(String srcPath, String dstPath, int bitrateKbps){
        return convert(srcPath, dstPath, bitrateKbps, 0, null);
    }

    public static boolean convert(String srcPath, String dstPath, int bitrateKbps,
                                  long totalDurationMs, final Cb cb){
        try{
            File src = new File(srcPath);
            if(!src.exists() || src.length() < 1024){
                Log.i(TAG, "MP3 src missing");
                if(cb != null) cb.onDone(false, "源文件不存在");
                return false;
            }
            final File dst = new File(dstPath);
            if(dst.exists()) dst.delete();

            final long dur = totalDurationMs > 0 ? totalDurationMs : probeDuration(srcPath);
            Log.i(TAG, "MP3 src=" + src.length() + "B dur=" + dur + "ms");

            String cmd = "-hide_banner -y -i \"" + srcPath + "\" -vn -c:a libmp3lame -b:a "
                    + bitrateKbps + "k -ar 44100 \"" + dstPath + "\"";
            Log.i(TAG, "MP3 cmd " + cmd);

            final CountDownLatch latch = new CountDownLatch(1);
            final boolean[] result = new boolean[]{false};
            final String[] errMsg = new String[]{""};

            FFmpegSession session = FFmpegKit.executeAsync(
                cmd,
                new FFmpegSessionCompleteCallback(){
                    @Override public void apply(FFmpegSession s){
                        try{
                            ReturnCode rc = s.getReturnCode();
                            long len = dst.exists() ? dst.length() : 0;
                            boolean ok = ReturnCode.isSuccess(rc) && len > 1024;
                            result[0] = ok;
                            Log.i(TAG, "MP3 rc=" + rc + " size=" + len);
                            if(!ok){
                                String logs = s.getOutput();
                                if(logs != null && logs.length() > 0){
                                    int st = Math.max(0, logs.length() - 400);
                                    Log.i(TAG, "MP3 log " + logs.substring(st));
                                    errMsg[0] = "转码失败";
                                }
                            }
                        }catch(Throwable t){
                            Log.i(TAG, "MP3 complete err " + t);
                        }finally{
                            latch.countDown();
                        }
                    }
                },
                null,
                new StatisticsCallback(){
                    @Override public void apply(Statistics st){
                        if(cb == null || dur <= 0) return;
                        try{
                            long ms = (long) st.getTime();
                            float r = (float)((double) ms / dur);
                            cb.onProgress(Math.max(0f, Math.min(1f, r)), ms);
                        }catch(Throwable t){}
                    }
                }
            );

            boolean finished = latch.await(10, TimeUnit.MINUTES);
            if(!finished){
                Log.i(TAG, "MP3 timeout, cancel");
                FFmpegKit.cancel(session.getSessionId());
                if(cb != null) cb.onDone(false, "转码超时");
                return false;
            }

            if(cb != null) cb.onDone(result[0], result[0] ? "成功" : errMsg[0]);
            return result[0];
        }catch(Throwable t){
            Log.i(TAG, "MP3_ERR " + t);
            if(cb != null) cb.onDone(false, "异常:" + t);
            return false;
        }
    }

    /** 读取媒体时长（毫秒），失败返回 0 */
    public static long probeDuration(String path){
        MediaMetadataRetriever r = null;
        try{
            r = new MediaMetadataRetriever();
            r.setDataSource(path);
            String d = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            if(d != null) return Long.parseLong(d.trim());
        }catch(Throwable t){
            Log.i(TAG, "probe err " + t);
        }finally{
            try{ if(r != null) r.release(); }catch(Throwable t){}
        }
        return 0;
    }
}