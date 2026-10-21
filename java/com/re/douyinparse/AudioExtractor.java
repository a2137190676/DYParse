package com.re.douyinparse;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.util.Log;

import java.io.File;
import java.nio.ByteBuffer;

/**
 * 从视频文件里提取音轨，输出 m4a。
 *
 * 抖音分享页的 music 对象只有 mid/title/author，没有音频直链，
 * 而抖音的视频文件本身自带音轨，所以直接从视频里抽音频最稳。
 * 纯 MediaExtractor + MediaMuxer，不需要 ffmpeg。
 */
public class AudioExtractor {

    static final String TAG = "DYP";

    /** 同步抽取：输入视频，输出 m4a。返回是否成功 */
    public static boolean extract(String srcPath, String dstPath){
        MediaExtractor ex = null;
        MediaMuxer mx = null;
        try{
            ex = new MediaExtractor();
            ex.setDataSource(srcPath);

            int trackIndex = -1;
            MediaFormat fmt = null;
            for(int i = 0; i < ex.getTrackCount(); i++){
                MediaFormat f = ex.getTrackFormat(i);
                String mime = f.getString(MediaFormat.KEY_MIME);
                if(mime != null && mime.startsWith("audio/")){
                    trackIndex = i; fmt = f; break;
                }
            }
            if(trackIndex < 0){ Log.i(TAG, "AUDIO no audio track"); return false; }

            ex.selectTrack(trackIndex);

            mx = new MediaMuxer(dstPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            int outTrack = mx.addTrack(fmt);
            mx.start();

            ByteBuffer buf = ByteBuffer.allocate(256 * 1024);
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            long lastPts = 0;

            while(true){
                int size = ex.readSampleData(buf, 0);
                if(size < 0) break;
                info.offset = 0;
                info.size = size;
                info.presentationTimeUs = ex.getSampleTime();
                info.flags = ex.getSampleFlags();
                mx.writeSampleData(outTrack, buf, info);
                lastPts = info.presentationTimeUs;
                ex.advance();
            }

            mx.stop();
            Log.i(TAG, "AUDIO ok dur_us=" + lastPts);
            return new File(dstPath).length() > 1024;
        }catch(Throwable t){
            Log.i(TAG, "AUDIO_ERR " + t);
            return false;
        }finally{
            try{ if(mx != null) mx.release(); }catch(Throwable t){}
            try{ if(ex != null) ex.release(); }catch(Throwable t){}
        }
    }
}
