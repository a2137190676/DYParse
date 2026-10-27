package com.re.douyinparse;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;

import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 调试日志与崩溃日志。
 *
 * 设计要点（都是为了用户能把日志发给我们排查）：
 * - 两份独立的文件：
 *     files/logs/debug.log   受「调试日志」开关控制，默认关
 *     files/logs/crash.log   崩溃日志，永远记录，不受开关影响
 * - 每条日志都写清楚：时间 + 等级 + 模块 + 具体原因 + 关键参数
 *     比如不是写 "下载失败"，而是写
 *     "下载失败 | 无法写入文件 | 路径=/storage/.../a.mp4 | 原因=Permission denied | 建议=去设置里给存储权限"
 * - 文件超过 512KB 自动滚动（老的截断保留后半段），避免无限增大
 * - 保存到 Download 时用一份人可读的文件名，带时间戳，用户一眼能找到
 */
public class DiagLog {

    /** 模块名，写日志时带上，方便区分是谁打的 */
    public static final String M_DOWNLOAD = "下载";
    public static final String M_PARSE = "解析";
    public static final String M_PLAY = "播放";
    public static final String M_PERM = "权限";
    public static final String M_NET = "网络";
    public static final String M_APP = "应用";
    public static final String M_UI = "界面";

    static final int LEVEL_D = 0;   // 调试细节
    static final int LEVEL_I = 1;   // 正常流程
    static final int LEVEL_W = 2;   // 异常但可继续
    static final int LEVEL_E = 3;   // 失败

    static final long MAX_BYTES = 512 * 1024;

    static final String SP = "dyp_diag";
    static final String KEY_DEBUG_ON = "debug_log_on";

    static Context appCtx;
    static final Object LOCK = new Object();
    static final SimpleDateFormat TS = new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US);
    static final SimpleDateFormat FILE_TS = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US);
    static final SimpleDateFormat LINE_TS = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);

    /** 初始化，Application 或 Activity onCreate 里调一次 */
    public static void init(Context c){
        if(c != null && appCtx == null) appCtx = c.getApplicationContext();
    }

    static File logDir(){
        File base = null;
        try{ base = appCtx != null ? appCtx.getFilesDir() : null; }catch(Throwable t){}
        if(base == null) return null;
        File d = new File(base, "logs");
        if(!d.exists()){ try{ d.mkdirs(); }catch(Throwable t){} }
        return d;
    }

    public static File debugFile(){
        File d = logDir();
        return d == null ? null : new File(d, "debug.log");
    }

    public static File crashFile(){
        File d = logDir();
        return d == null ? null : new File(d, "crash.log");
    }

    // ---------- 开关 ----------

    public static boolean isDebugOn(){
        try{
            return appCtx.getSharedPreferences(SP, Context.MODE_PRIVATE)
                    .getBoolean(KEY_DEBUG_ON, false);
        }catch(Throwable t){ return false; }
    }

    public static void setDebugOn(boolean on){
        try{
            appCtx.getSharedPreferences(SP, Context.MODE_PRIVATE)
                    .edit().putBoolean(KEY_DEBUG_ON, on).apply();
        }catch(Throwable t){}
        if(on) i(M_APP, "调试日志已开启");
        else {
            // 关闭前留一条，免得用户以为没生效
            i(M_APP, "调试日志已关闭（崩溃日志不受影响，仍在记录）");
        }
    }

    // ---------- 对外写日志 ----------

    /** 调试细节，只在开关打开时记录 */
    public static void d(String mod, String msg){
        if(!isDebugOn()) return;
        write(debugFile(), LEVEL_D, mod, msg);
    }

    /** 正常流程信息，只在开关打开时记录 */
    public static void i(String mod, String msg){
        if(!isDebugOn()) return;
        write(debugFile(), LEVEL_I, mod, msg);
    }

    /** 警告，只在开关打开时记录 */
    public static void w(String mod, String msg){
        if(!isDebugOn()) return;
        write(debugFile(), LEVEL_W, mod, msg);
    }

    /** 失败。开关打开时进调试日志；同时会进崩溃日志，保证事后一定拿得到。
     *  自动附带调用位置（类.方法:行号），方便我们直接跳到出问题的代码。 */
    public static void e(String mod, String msg){
        String where = callerInfo();
        String full = msg + "\n  代码位置: " + where;
        boolean on = isDebugOn();
        if(on) write(debugFile(), LEVEL_E, mod, full);
        write(crashFile(), LEVEL_E, mod, full);
    }

    /** 带异常的失败：附带异常堆栈，和崩溃日志一样能定位到行 */
    public static void e(String mod, String msg, Throwable t){
        StringBuilder sb = new StringBuilder(msg);
        sb.append("\n  代码位置: ").append(callerInfo());
        if(t != null){
            sb.append("\n  异常类型: ").append(t.getClass().getName());
            sb.append("\n  异常信息: ").append(safe(t.getMessage()));
            Throwable cause = t.getCause();
            if(cause != null && cause != t){
                sb.append("\n  起因: ").append(cause.getClass().getName())
                  .append(" : ").append(safe(cause.getMessage()));
            }
            sb.append("\n  堆栈:");
            StackTraceElement[] sts = t.getStackTrace();
            int lim = sts == null ? 0 : sts.length;
            for(int i = 0; i < lim; i++) sb.append("\n    at ").append(sts[i].toString());
        }
        boolean on = isDebugOn();
        if(on) write(debugFile(), LEVEL_E, mod, sb.toString());
        write(crashFile(), LEVEL_E, mod, sb.toString());
    }

    /** 取调用 DiagLog 的那一行代码位置：SimpleClassName.method(File.java:123) */
    static String callerInfo(){
        try{
            StackTraceElement[] st = Thread.currentThread().getStackTrace();
            for(int i = 0; st != null && i < st.length; i++){
                String cn = st[i].getClassName();
                if(cn == null) continue;
                if(cn.endsWith("DiagLog")) continue;   // 跳过本类自己的帧
                String simple = cn;
                int dot = cn.lastIndexOf('.');
                if(dot >= 0) simple = cn.substring(dot + 1);
                return simple + "." + st[i].getMethodName()
                        + "(" + st[i].getFileName() + ":" + st[i].getLineNumber() + ")";
            }
        }catch(Throwable t){}
        return "未知";
    }

    /**
     * 记录崩溃（未捕获异常）。永远写，不受开关影响。
     * 会带上线程、异常类型、完整堆栈，方便直接定位。
     */
    public static void crash(String mod, String what, Throwable t){
        StringBuilder sb = new StringBuilder();
        sb.append("崩溃/未捕获异常 | ").append(what);
        if(t != null){
            sb.append("\n  异常类型: ").append(t.getClass().getName());
            sb.append("\n  异常信息: ").append(safe(t.getMessage()));
            Throwable cause = t.getCause();
            if(cause != null && cause != t){
                sb.append("\n  起因: ").append(cause.getClass().getName())
                  .append(" : ").append(safe(cause.getMessage()));
            }
            sb.append("\n  堆栈:");
            for(StackTraceElement el : t.getStackTrace()){
                sb.append("\n    at ").append(el.toString());
            }
        }
        sb.append("\n  线程: ").append(Thread.currentThread().getName());
        write(crashFile(), LEVEL_E, mod, sb.toString());
    }

    static String safe(String s){ return s == null ? "(null)" : s; }

    // ---------- 写文件 ----------

    static void write(File f, int level, String mod, String msg){
        if(f == null) return;
        synchronized(LOCK){
            try{
                rollIfNeeded(f);
                FileWriter w = new FileWriter(f, true);
                try{
                    w.append(LINE_TS.format(new Date()))
                     .append(" [").append(levelName(level)).append("]")
                     .append(" [").append(mod == null ? "?" : mod).append("] ")
                     .append(msg == null ? "" : msg)
                     .append('\n');
                }finally{
                    try{ w.close(); }catch(Throwable t){}
                }
            }catch(Throwable t){
                // 连日志都写不了就别递归了
            }
        }
    }

    static String levelName(int lv){
        switch(lv){
            case LEVEL_D: return "调试";
            case LEVEL_I: return "信息";
            case LEVEL_W: return "警告";
            case LEVEL_E: return "失败";
            default: return "?";
        }
    }

    /** 超过上限就截断保留后半段，避免日志无限增长吃满空间 */
    static void rollIfNeeded(File f){
        try{
            if(!f.exists() || f.length() < MAX_BYTES) return;
            byte[] all = new byte[(int)f.length()];
            java.io.FileInputStream in = new java.io.FileInputStream(f);
            try{ in.read(all); }finally{ try{ in.close(); }catch(Throwable t){} }
            int keep = all.length / 2;
            int start = all.length - keep;
            // 尽量从换行处开始，别把一行截一半
            while(start < all.length && all[start] != '\n') start++;
            FileWriter w = new FileWriter(f, false);
            try{
                w.write("===== 日志超过 512KB，前面的内容已自动清理 =====\n");
                w.write(new String(all, start, all.length - start, "UTF-8"));
            }finally{ try{ w.close(); }catch(Throwable t){} }
        }catch(Throwable t){}
    }

    // ---------- 读 / 清 / 导出 ----------

    public static String readDebug(){
        return readFile(debugFile());
    }

    public static String readCrash(){
        return readFile(crashFile());
    }

    static String readFile(File f){
        try{
            if(f == null || !f.exists()) return "";
            byte[] all = new byte[(int)f.length()];
            java.io.FileInputStream in = new java.io.FileInputStream(f);
            try{ in.read(all); }finally{ try{ in.close(); }catch(Throwable t){} }
            return new String(all, "UTF-8");
        }catch(Throwable t){
            return "读取日志失败: " + t;
        }
    }

    public static void clearDebug(){ deleteFile(debugFile()); }
    public static void clearCrash(){ deleteFile(crashFile()); }

    static void deleteFile(File f){
        try{ if(f != null && f.exists()) f.delete(); }catch(Throwable t){}
    }

    /**
     * 导出到 Download/DYParse/ 下，返回保存后的完整路径；失败返回 null。
     * 文件名带类型和时间，用户找起来容易。
     */
    public static String exportToDownload(boolean crash){
        try{
            String content = crash ? readCrash() : readDebug();
            if(content == null || content.trim().length() == 0) return null;

            File dir = new File(Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS), "DYParse");
            if(!dir.exists()){ try{ dir.mkdirs(); }catch(Throwable t){} }

            String name = "DYParse日志_" + (crash ? "崩溃" : "调试") + "_"
                    + FILE_TS.format(new Date()) + ".txt";
            File out = new File(dir, name);

            // 头部写清楚是谁的日志、什么版本、什么设备，方便我们直接看
            StringBuilder head = new StringBuilder();
            head.append("========== DYParse 日志 ==========\n");
            head.append("类型: ").append(crash ? "崩溃日志（不受调试开关影响）" : "调试日志").append('\n');
            head.append("导出时间: ").append(LINE_TS.format(new Date())).append('\n');
            head.append("App版本: ").append(AppInfo.versionLine()).append('\n');
            head.append("设备: ").append(AppInfo.deviceLine()).append('\n');
            head.append("==================================\n\n");

            FileWriter w = new FileWriter(out, false);
            try{
                w.write(head.toString());
                w.write(content);
            }finally{ try{ w.close(); }catch(Throwable t){} }
            return out.getAbsolutePath();
        }catch(Throwable t){
            return null;
        }
    }

    /** 日志文件列表（给「查看日志」里显示概况用） */
    public static List<String> summary(){
        List<String> out = new ArrayList<String>();
        File d = logDir();
        if(d == null) return out;
        File[] fs = d.listFiles();
        if(fs == null) return out;
        for(File f : fs){
            if(f.isFile()) out.add(f.getName() + "  " + (f.length() / 1024) + " KB");
        }
        return out;
    }
}
