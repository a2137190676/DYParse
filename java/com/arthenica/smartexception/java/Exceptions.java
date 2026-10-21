package com.arthenica.smartexception.java;

/** ffmpeg-kit \u6240\u9700\u7684\u8f7b\u91cf\u66ff\u4ee3\u5b9e\u73b0\uff08\u4ec5\u7528\u4e8e\u65e5\u5fd7\uff09 */
public class Exceptions {

    public static void registerRootPackage(String packageName){ }

    public static String getStackTraceString(Throwable throwable){
        if(throwable == null) return "";
        java.io.StringWriter sw = new java.io.StringWriter();
        java.io.PrintWriter pw = new java.io.PrintWriter(sw);
        throwable.printStackTrace(pw);
        pw.flush();
        return sw.toString();
    }
}
