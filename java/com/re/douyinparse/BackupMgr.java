package com.re.douyinparse;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 全量备份 / 恢复 —— 把用户能设置的东西全部存下来。
 *
 * 【为什么必须导出到公共目录】
 *   Cookie 在  /sdcard/Android/data/{包名}/files/cookies/
 *   配置在      /data/data/{包名}/shared_prefs/dyp_pref.xml
 *   这两处都是 App 私有数据，**清数据 / 卸载重装 100% 会被系统删掉**。
 *   App 没法阻止，只能把内容导出到：
 *       /sdcard/Download/DYParse/备份/DYParse-backup-<时间>.zip
 *   公共目录卸载不动，重装后点「恢复配置」读回来。
 *
 * 【备份内容 —— 全量】
 *   1. cookies/*.txt   所有平台登录 Cookie
 *   2. prefs.txt       **全部** SharedPreferences（dyp_pref）键值，
 *                      不设白名单 —— 下载路径、代理开关、主题、并发数、
 *                      磁力线路、更新源、GitHub 模式、访客凭据…
 *                      以后新增的设置项也自动包含，不用改这里。
 *   3. files/          外部私有目录里除 cookies 外的其它小文件（<256KB）
 *   4. info.txt        说明
 *
 * 【刻意不备份】
 *   · 下载好的媒体文件 —— 本来就在公共目录，不需要
 *   · cache/ —— 临时文件
 */
public class BackupMgr {

    static final String TAG = "DYP";
    static final String BAK_DIR = "/storage/emulated/0/Download/DYParse/备份";
    /** 和 MainActivity 里的 SP 常量保持一致 */
    static final String SP_NAME = "dyp_pref";
    /** 单个附加文件上限 */
    static final long MAX_EXTRA = 256 * 1024;

    public static File bakDir(){
        File d = new File(BAK_DIR);
        if(!d.exists()) d.mkdirs();
        return d;
    }

    /** 执行全量备份 */
    public static File backup(Context ctx){
        File out = null;
        try{
            File d = bakDir();
            if(!d.isDirectory()){
                Log.i(TAG, "BAK_DIR_FAIL " + BAK_DIR);
                return null;
            }
            String ts = new java.text.SimpleDateFormat("yyyyMMdd-HHmmss",
                    java.util.Locale.US).format(new java.util.Date());
            out = new File(d, "DYParse-backup-" + ts + ".zip");

            java.util.zip.ZipOutputStream zos =
                    new java.util.zip.ZipOutputStream(new FileOutputStream(out));
            int ckCount = 0, prefCount = 0, extraCount = 0;
            try{
                // ---------- 1. 所有平台 Cookie ----------
                File ckDir = CookieStore.dirOf(ctx);
                File[] fs = ckDir == null ? null : ckDir.listFiles();
                if(fs != null){
                    for(File f : fs){
                        if(f == null || !f.isFile()) continue;
                        if(!f.getName().endsWith(".txt")) continue;
                        if(f.length() == 0) continue;
                        putFile(zos, "cookies/" + f.getName(), f);
                        ckCount++;
                    }
                }

                // ---------- 2. 全部 SharedPreferences ----------
                SharedPreferences sp = ctx.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
                Map<String, ?> all = sp.getAll();
                StringBuilder sb = new StringBuilder();
                for(Map.Entry<String, ?> e : all.entrySet()){
                    String k = e.getKey();
                    if(k == null) continue;
                    Object v = e.getValue();
                    if(v == null) continue;
                    if(v instanceof String){
                        sb.append(k).append('\t').append("s").append('\t')
                          .append(esc((String) v)).append('\n');
                    }else if(v instanceof Boolean){
                        sb.append(k).append('\t').append("b").append('\t')
                          .append(v).append('\n');
                    }else if(v instanceof Integer){
                        sb.append(k).append('\t').append("i").append('\t')
                          .append(v).append('\n');
                    }else if(v instanceof Long){
                        sb.append(k).append('\t').append("l").append('\t')
                          .append(v).append('\n');
                    }else if(v instanceof Float){
                        sb.append(k).append('\t').append("f").append('\t')
                          .append(v).append('\n');
                    }else if(v instanceof java.util.Set){
                        StringBuilder set = new StringBuilder();
                        for(Object o : (java.util.Set) v){
                            if(set.length() > 0) set.append('\u0001');
                            set.append(esc(String.valueOf(o)));
                        }
                        sb.append(k).append('\t').append("S").append('\t')
                          .append(set).append('\n');
                    }else{
                        continue;
                    }
                    prefCount++;
                }
                putText(zos, "prefs.txt", sb.toString());

                // ---------- 3. 外部私有目录里的其它小文件 ----------
                try{
                    File ext = ctx.getExternalFilesDir(null);
                    if(ext != null && ext.isDirectory()){
                        File[] ef = ext.listFiles();
                        if(ef != null){
                            for(File f : ef){
                                if(f == null || !f.isFile()) continue;
                                if("cookies".equals(f.getName())) continue;
                                if(f.length() > MAX_EXTRA) continue;
                                putFile(zos, "files/" + f.getName(), f);
                                extraCount++;
                            }
                        }
                    }
                }catch(Throwable t){}

                // ---------- 4. 说明 ----------
                StringBuilder info = new StringBuilder();
                info.append("DYParse 全量配置备份\n");
                info.append("备份时间: ").append(new java.util.Date().toString()).append('\n');
                info.append("App 版本: ").append(AppInfo.versionLine()).append('\n');
                info.append("Cookie 文件: ").append(ckCount).append(" 个\n");
                info.append("配置项: ").append(prefCount).append(" 条\n");
                info.append("附加文件: ").append(extraCount).append(" 个\n\n");
                info.append("恢复方法:\n");
                info.append("  设置 → 备份 / 恢复 → 恢复配置 → 选这个 zip\n\n");
                info.append("注意: 本文件含账号登录凭据，请勿外传。\n");
                putText(zos, "info.txt", info.toString());
            }finally{
                try{ zos.close(); }catch(Throwable t){}
            }
            Log.i(TAG, "BAK_OK ck=" + ckCount + " pref=" + prefCount
                    + " extra=" + extraCount + " -> " + out.getAbsolutePath());
        }catch(Throwable t){
            Log.i(TAG, "BAK_ERR " + t);
            return null;
        }
        return out;
    }

    /** 从 zip 全量恢复 */
    public static String restore(Context ctx, File zip){
        try{
            if(zip == null || !zip.exists()){
                Log.i(TAG, "RST_NO_FILE");
                return null;
            }
            int ck = 0, pref = 0, extra = 0;
            java.util.zip.ZipInputStream zis =
                    new java.util.zip.ZipInputStream(new FileInputStream(zip));
            try{
                java.util.zip.ZipEntry e;
                byte[] buf = new byte[8192];
                while((e = zis.getNextEntry()) != null){
                    String name = e.getName();
                    if(e.isDirectory()) continue;
                    java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
                    int n;
                    while((n = zis.read(buf)) > 0) bo.write(buf, 0, n);
                    byte[] data = bo.toByteArray();

                    if(name.startsWith("cookies/") && name.endsWith(".txt")){
                        String fn = name.substring("cookies/".length());
                        if(fn.length() == 0 || fn.indexOf('/') >= 0) continue;
                        writeAll(new File(CookieStore.dirOf(ctx), fn), data);
                        ck++;
                    }else if(name.equals("prefs.txt")){
                        String txt = new String(data, "UTF-8");
                        SharedPreferences.Editor ed =
                                ctx.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE).edit();
                        for(String line : txt.split("\\r?\\n")){
                            if(line.length() == 0) continue;
                            String[] p = line.split("\t");
                            if(p.length < 3) continue;
                            String k = p[0], ty = p[1], v = p[2];
                            try{
                                if("s".equals(ty)){
                                    ed.putString(k, unesc(v));
                                }else if("b".equals(ty)){
                                    ed.putBoolean(k, Boolean.parseBoolean(v));
                                }else if("i".equals(ty)){
                                    ed.putInt(k, Integer.parseInt(v));
                                }else if("l".equals(ty)){
                                    ed.putLong(k, Long.parseLong(v));
                                }else if("f".equals(ty)){
                                    ed.putFloat(k, Float.parseFloat(v));
                                }else if("S".equals(ty)){
                                    java.util.Set<String> set = new java.util.HashSet<String>();
                                    for(String one : v.split("\u0001")){
                                        if(one.length() > 0) set.add(unesc(one));
                                    }
                                    ed.putStringSet(k, set);
                                }else{
                                    continue;
                                }
                                pref++;
                            }catch(Throwable t){}
                        }
                        ed.apply();
                    }else if(name.startsWith("files/")){
                        String fn = name.substring("files/".length());
                        if(fn.length() == 0 || fn.indexOf('/') >= 0) continue;
                        File ext = ctx.getExternalFilesDir(null);
                        if(ext == null) continue;
                        writeAll(new File(ext, fn), data);
                        extra++;
                    }
                }
            }finally{
                try{ zis.close(); }catch(Throwable t){}
            }
            String r = "恢复完成：Cookie " + ck + " 个，配置 " + pref + " 条，文件 " + extra + " 个";
            Log.i(TAG, "RST_OK ck=" + ck + " pref=" + pref + " extra=" + extra);
            return r;
        }catch(Throwable t){
            Log.i(TAG, "RST_ERR " + t);
            return null;
        }
    }

    /** 列出已有备份（新的在前） */
    public static List<File> list(){
        List<File> out = new ArrayList<File>();
        try{
            File d = bakDir();
            File[] fs = d.listFiles();
            if(fs == null) return out;
            for(File f : fs){
                if(f != null && f.isFile() && f.getName().endsWith(".zip")) out.add(f);
            }
            java.util.Collections.sort(out, new java.util.Comparator<File>(){
                public int compare(File a, File b){
                    return Long.compare(b.lastModified(), a.lastModified());
                }
            });
        }catch(Throwable t){}
        return out;
    }

    public static String sizeOf(File f){
        if(f == null) return "?";
        long n = f.length();
        if(n < 1024) return n + " B";
        if(n < 1024 * 1024) return (n / 1024) + " KB";
        return String.format(java.util.Locale.US, "%.2f MB", n / 1048576.0);
    }

    static String esc(String s){
        if(s == null) return "";
        return s.replace("\\", "\\\\").replace("\n", "\\n").replace("\t", "\\t").replace("\r", "\\r");
    }

    static String unesc(String s){
        if(s == null) return "";
        StringBuilder b = new StringBuilder();
        for(int i = 0; i < s.length(); i++){
            char c = s.charAt(i);
            if(c == '\\' && i + 1 < s.length()){
                char n = s.charAt(i + 1);
                if(n == 'n'){ b.append('\n'); i++; continue; }
                if(n == 't'){ b.append('\t'); i++; continue; }
                if(n == 'r'){ b.append('\r'); i++; continue; }
                if(n == '\\'){ b.append('\\'); i++; continue; }
            }
            b.append(c);
        }
        return b.toString();
    }

    static void writeAll(File dst, byte[] data){
        FileOutputStream fo = null;
        try{
            fo = new FileOutputStream(dst, false);
            fo.write(data);
            fo.flush();
        }catch(Throwable t){
            Log.i(TAG, "BAK_WRITE_ERR " + dst + " " + t);
        }finally{
            try{ if(fo != null) fo.close(); }catch(Throwable t){}
        }
    }

    static void putFile(java.util.zip.ZipOutputStream zos, String entry, File f){
        FileInputStream in = null;
        try{
            java.util.zip.ZipEntry ze = new java.util.zip.ZipEntry(entry);
            zos.putNextEntry(ze);
            in = new FileInputStream(f);
            byte[] buf = new byte[8192];
            int n;
            while((n = in.read(buf)) > 0) zos.write(buf, 0, n);
            zos.closeEntry();
        }catch(Throwable t){
            Log.i(TAG, "BAK_PUT_ERR " + entry + " " + t);
        }finally{
            try{ if(in != null) in.close(); }catch(Throwable t){}
        }
    }

    static void putText(java.util.zip.ZipOutputStream zos, String entry, String text){
        try{
            java.util.zip.ZipEntry ze = new java.util.zip.ZipEntry(entry);
            zos.putNextEntry(ze);
            zos.write(text == null ? new byte[0] : text.getBytes("UTF-8"));
            zos.closeEntry();
        }catch(Throwable t){
            Log.i(TAG, "BAK_PUTTXT_ERR " + entry + " " + t);
        }
    }
}