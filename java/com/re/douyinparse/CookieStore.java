package com.re.douyinparse;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Cookie 本地存储（纯本地，不上传、不依赖服务器）。
 *
 * 文件位置：/sdcard/Android/data/{包名}/files/cookies/{platform}.txt
 *   —— 走外部私有目录，用户用文件管理器就能直接看到、复制、备份，
 *      卸载 App 时系统会自动清理，不会留垃圾。
 * 旧版本在 {filesDir}/cookies/（内部私有目录，普通文件管理器看不到），
 * 首次访问时会自动迁移过来。
 *
 * 文件格式：一行一个 cookie，必须是 name=value 形式，name 不允许省略。
 *
 * 例：instagram.txt
 *   sessionid=12345%3Aabcde
 *   csrftoken=AbCdEf
 *   ds_user_id=67890
 *
 * 例：youtube.txt
 *   SAPISID=xxxx
 *   __Secure-1PSID=yyyy
 *   LOGIN_INFO=zzzz
 *
 * 说明：
 *  - 服务器到期 / 关掉内置代理，这些 cookie 依然可用（本地直连解析）
 *  - 支持从浏览器粘贴的 `a=1; b=2` 单行格式，也支持多行 name=value 格式
 *  - 值里允许出现 `=`，只按第一个 `=` 切分
 */
public class CookieStore {

    static final String TAG = "DYP";
    static final String DIR = "cookies";

    public static final String PLATFORM_YT = "youtube";
    public static final String PLATFORM_INS = "instagram";
    public static final String PLATFORM_BILI = "bilibili";
    /** 抖音主页登录 Cookie（本地保存，绝不上传服务器） */
    public static final String PLATFORM_DY = "douyin";

    /** Cookie 根目录：优先外部私有目录 Android/data/{包名}/files/cookies */
    static File dirOf(Context ctx){
        File d = null;
        try{
            File ext = ctx.getExternalFilesDir(null);   // /sdcard/Android/data/{pkg}/files
            if(ext != null){
                d = new File(ext, DIR);
                if(!d.exists()) d.mkdirs();
                if(d.isDirectory()) return d;
            }
        }catch(Throwable t){}
        // 兜底：外部不可用（极少数机型 / 存储未挂载）时用内部私有目录
        d = new File(ctx.getFilesDir(), DIR);
        if(!d.exists()) d.mkdirs();
        return d;
    }

    /** 旧版目录（内部私有），只用于迁移 */
    static File legacyDir(Context ctx){
        return new File(ctx.getFilesDir(), DIR);
    }

    /** 首次访问时把旧目录里的 cookie 文件搬到新目录 */
    static void migrateIfNeeded(Context ctx){
        try{
            File nw = dirOf(ctx);
            File old = legacyDir(ctx);
            if(nw.getAbsolutePath().equals(old.getAbsolutePath())) return;
            if(!old.isDirectory()) return;
            File[] fs = old.listFiles();
            if(fs == null) return;
            int moved = 0;
            for(File f : fs){
                if(!f.isFile()) continue;
                File dst = new File(nw, f.getName());
                if(dst.exists() && dst.length() > 0) continue;   // 新目录已有更新的，不覆盖
                java.io.FileInputStream in = null;
                java.io.FileOutputStream out = null;
                try{
                    in = new java.io.FileInputStream(f);
                    out = new java.io.FileOutputStream(dst);
                    byte[] buf = new byte[8192];
                    int n;
                    while((n = in.read(buf)) > 0) out.write(buf, 0, n);
                    out.flush();
                    moved++;
                }catch(Throwable t2){
                }finally{
                    try{ if(in != null) in.close(); }catch(Throwable t2){}
                    try{ if(out != null) out.close(); }catch(Throwable t2){}
                }
            }
            if(moved > 0) Log.i(TAG, "CK_MIGRATE moved=" + moved + " -> " + nw.getAbsolutePath());
        }catch(Throwable t){
            Log.i(TAG, "CK_MIGRATE_ERR " + t);
        }
    }

    /** 文件全路径（用于界面展示） */
    public static String filePath(Context ctx, String platform){
        migrateIfNeeded(ctx);
        return new File(dirOf(ctx), platform + ".txt").getAbsolutePath();
    }

    static File fileOf(Context ctx, String platform){
        migrateIfNeeded(ctx);
        return new File(dirOf(ctx), platform + ".txt");
    }

    /**
     * 解析用户粘贴的 cookie 文本，支持三种写法：
     *   1) a=1; b=2; c=3          （浏览器 Header 串）
     *   2) a=1\nb=2\nc=3          （多行 name=value）
     *   3) Set-Cookie 多行（每行带 Path=/; Domain=...）自动只取第一段
     */
    public static Map<String, String> parse(String text){
        Map<String, String> out = new LinkedHashMap<String, String>();
        if(text == null) return out;
        String t = text.trim();
        if(t.length() == 0) return out;

        // 先按换行拆（多行 / Set-Cookie 列表）
        String[] lines = t.split("\\r?\\n");
        for(String line : lines){
            String ln = line.trim();
            if(ln.length() == 0) continue;
            if(ln.startsWith("#")) continue;                 // Netscape 注释
            // Netscape http cookie file：7 列 tab 分隔，第 6/7 列是 name/value
            if(ln.indexOf('\t') >= 0){
                String[] p = ln.split("\t");
                if(p.length >= 7){
                    String n = p[5].trim();
                    String v = p[6].trim();
                    if(n.length() > 0) out.put(n, v);
                    continue;
                }
            }
            // Set-Cookie 形式：name=value; Path=/; Domain=...
            if(ln.indexOf(';') >= 0 && (ln.contains("Path=") || ln.contains("Domain=")
                    || ln.contains("Expires=") || ln.contains("Max-Age="))){
                String head = ln.split(";")[0].trim();
                putPair(out, head);
                continue;
            }
            // Header 串：a=1; b=2; c=3
            if(ln.indexOf(';') >= 0){
                for(String one : ln.split(";")) putPair(out, one.trim());
                continue;
            }
            // 单行 name=value
            putPair(out, ln);
        }
        return out;
    }

    static void putPair(Map<String, String> out, String kv){
        if(kv == null) return;
        String s = kv.trim();
        if(s.length() == 0) return;
        int i = s.indexOf('=');
        if(i <= 0) return;
        String n = s.substring(0, i).trim();
        String v = s.substring(i + 1).trim();
        if(n.length() == 0) return;
        out.put(n, v);
    }

    /** 保存（覆盖）。返回实际写入条数，0 表示没解析出任何 cookie */
    public static int save(Context ctx, String platform, String text){
        Map<String, String> m = parse(text);
        if(m.isEmpty()) return 0;
        saveMap(ctx, platform, m);
        return m.size();
    }

    public static void saveMap(Context ctx, String platform, Map<String, String> m){
        File f = fileOf(ctx, platform);
        FileOutputStream fos = null;
        OutputStreamWriter w = null;
        try{
            fos = new FileOutputStream(f, false);
            w = new OutputStreamWriter(fos, "UTF-8");
            for(Map.Entry<String, String> e : m.entrySet()){
                w.write(e.getKey());
                w.write('=');
                w.write(e.getValue() == null ? "" : e.getValue());
                w.write('\n');
            }
            w.flush();
            Log.i(TAG, "CK_SAVE " + platform + " n=" + m.size() + " -> " + f.getAbsolutePath());
        }catch(Throwable t){
            Log.i(TAG, "CK_SAVE_ERR " + platform + " " + t);
        }finally{
            try{ if(w != null) w.close(); }catch(Throwable t){}
            try{ if(fos != null) fos.close(); }catch(Throwable t){}
        }
    }

    /** 读取为有序 Map（保持文件里的行序） */
    public static Map<String, String> load(Context ctx, String platform){
        Map<String, String> out = new LinkedHashMap<String, String>();
        File f = fileOf(ctx, platform);
        if(!f.exists() || f.length() == 0) return out;
        FileInputStream fis = null;
        try{
            fis = new FileInputStream(f);
            byte[] buf = new byte[(int) f.length()];
            int n = fis.read(buf);
            String text = new String(buf, 0, n < 0 ? 0 : n, "UTF-8");
            for(String line : text.split("\\r?\\n")){
                String ln = line.trim();
                if(ln.length() == 0 || ln.startsWith("#")) continue;
                int i = ln.indexOf('=');
                if(i <= 0) continue;
                out.put(ln.substring(0, i).trim(), ln.substring(i + 1).trim());
            }
        }catch(Throwable t){
            Log.i(TAG, "CK_LOAD_ERR " + platform + " " + t);
        }finally{
            try{ if(fis != null) fis.close(); }catch(Throwable t){}
        }
        return out;
    }

    /** 拼成 HTTP 请求头用的 `a=1; b=2` */
    public static String header(Context ctx, String platform){
        Map<String, String> m = load(ctx, platform);
        if(m.isEmpty()) return "";
        StringBuilder b = new StringBuilder();
        for(Map.Entry<String, String> e : m.entrySet()){
            if(b.length() > 0) b.append("; ");
            b.append(e.getKey()).append('=').append(e.getValue());
        }
        return b.toString();
    }

    public static String get(Context ctx, String platform, String name){
        return load(ctx, platform).get(name);
    }

    public static boolean has(Context ctx, String platform){
        return !load(ctx, platform).isEmpty();
    }

    public static int count(Context ctx, String platform){
        return load(ctx, platform).size();
    }

    /** 清空某个平台 */
    public static void clear(Context ctx, String platform){
        try{
            File f = fileOf(ctx, platform);
            if(f.exists()) f.delete();
            Log.i(TAG, "CK_CLEAR " + platform);
        }catch(Throwable t){}
    }

    /** 供界面展示：把当前内容还原成多行 name=value */
    public static String dump(Context ctx, String platform){
        Map<String, String> m = load(ctx, platform);
        StringBuilder b = new StringBuilder();
        for(Map.Entry<String, String> e : m.entrySet()){
            b.append(e.getKey()).append('=').append(e.getValue()).append('\n');
        }
        return b.toString();
    }

    /** 当前文件里所有 cookie 名（界面提示用） */
    public static List<String> names(Context ctx, String platform){
        return new ArrayList<String>(load(ctx, platform).keySet());
    }

    /** Instagram 登录态是否够用：至少要有 sessionid */
    public static boolean insReady(Context ctx){
        Map<String, String> m = load(ctx, PLATFORM_INS);
        String sid = m.get("sessionid");
        return sid != null && sid.length() >= 8;
    }

    /** YouTube 登录态是否够用：SAPISID + __Secure-1PSID 之一 */
    public static boolean ytReady(Context ctx){
        Map<String, String> m = load(ctx, PLATFORM_YT);
        boolean sapisid = m.containsKey("SAPISID") || m.containsKey("__Secure-3PAPISID");
        boolean psid = m.containsKey("__Secure-1PSID") || m.containsKey("SID")
                || m.containsKey("LOGIN_INFO");
        return sapisid && psid;
    }

    /** B 站登录态是否够用：SESSDATA 存在即可（有它才有 1080P / 大会员清晰度） */
    public static boolean biliReady(Context ctx){
        String s = load(ctx, PLATFORM_BILI).get("SESSDATA");
        return s != null && s.length() >= 8;
    }
}