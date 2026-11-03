package com.re.douyinparse;

import android.util.Log;

/**
 * DYP 功能日志桥。
 *
 * <p>为什么要有这个类：
 * <pre>
 *   项目里原来有 700 多处  Log.i("DYP", "...")，
 *   这些日志写得很全，覆盖解析 / 下载 / 播放 / 批量的每一步，
 *   但它们只进 logcat：
 *     · 手机重启或 App 被杀之后 logcat 就没了
 *     · 用户没法在手机上直接看
 *     · 用户反馈问题时也没法导出发给我们
 *   真正会落文件的 DiagLog 只有几十处，覆盖不全。
 *
 *   所以这里做一个桥：一份调用同时写 logcat 和文件。
 *   把 Log.i("DYP", x) 换成 DypLog.i(x) 即可，语义不变。
 * </pre>
 *
 * <p>写文件受两个条件控制（任一成立就写）：
 * <ol>
 *   <li>设置里的「调试日志」开关打开（正常情况）</li>
 *   <li>「强制记录」打开（用户帮我们抓现场时用，
 *       即使开关关着也照写，避免用户忘了开）</li>
 * </ol>
 *
 * <p>logcat 始终照常输出，方便开发时直接 adb logcat 看。
 */
public class DypLog {

    /** logcat 用的 tag，保持和原来一致，方便老的排查命令继续可用 */
    public static final String TAG = "DYP";

    /** 强制记录开关的 prefs key */
    private static final String SP = "dyp_diag";
    private static final String KEY_FORCE = "log_force_on";

    /** 强制记录：不受「调试日志」开关限制，永远写文件 */
    public static boolean isForceOn(){
        try{
            return DiagLog.appCtx != null && DiagLog.appCtx
                    .getSharedPreferences(SP, android.content.Context.MODE_PRIVATE)
                    .getBoolean(KEY_FORCE, false);
        }catch(Throwable t){ return false; }
    }

    public static void setForceOn(boolean on){
        try{
            if(DiagLog.appCtx == null) return;
            DiagLog.appCtx.getSharedPreferences(SP, android.content.Context.MODE_PRIVATE)
                    .edit().putBoolean(KEY_FORCE, on).apply();
        }catch(Throwable t){}
        Log.i(TAG, "强制记录 " + (on ? "已开启" : "已关闭"));
    }

    /** 是否要写文件 */
    private static boolean shouldWrite(){
        return DiagLog.isDebugOn() || isForceOn();
    }

    // ---------- 三个级别 ----------

    public static void i(String msg){
        Log.i(TAG, msg);
        if(shouldWrite()) DiagLog.raw(DiagLog.LEVEL_I, DiagLog.M_APP, msg);
    }

    public static void w(String msg){
        Log.w(TAG, msg);
        if(shouldWrite()) DiagLog.raw(DiagLog.LEVEL_W, DiagLog.M_APP, msg);
    }

    public static void e(String msg){
        Log.e(TAG, msg);
        // 失败一定进两个文件（调试 + 崩溃），和 DiagLog.e 行为一致
        DiagLog.e(DiagLog.M_APP, msg);
    }

    /** 带异常的失败 */
    public static void e(String msg, Throwable t){
        Log.e(TAG, msg, t);
        DiagLog.e(DiagLog.M_APP, msg, t);
    }

    /** 调试细节 */
    public static void d(String msg){
        Log.d(TAG, msg);
        if(shouldWrite()) DiagLog.raw(DiagLog.LEVEL_D, DiagLog.M_APP, msg);
    }

    /** 指定模块（解析 / 下载 / 播放 ...），方便日志里分模块看 */
    public static void i(String mod, String msg){
        Log.i(TAG, "[" + mod + "] " + msg);
        if(shouldWrite()) DiagLog.raw(DiagLog.LEVEL_I, mod, msg);
    }
}