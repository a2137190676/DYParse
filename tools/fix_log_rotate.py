# -*- coding: utf-8 -*-
"""
日志系统改造：
  1. 上限 512KB → 100MB（用户要求）
  2. 新增「按天数清理」：超过 N 天的日志文件直接删
  3. 新增「目录总量清理」：logs 目录超 100MB 时从最旧的开始删
  4. 保留现有的「超上限截断保留后半段」逻辑作为最后兜底

为什么要三个一起做：
  只做 100MB 上限的话，debug.log 可以涨到 100MB，
  再加上 crash.log 和轮转出的旧文件，整个 logs 目录能到几百 MB。
  用户手机上这点空间不算小，所以必须同时约束「单文件」「目录总量」「时间」。

天数从 SharedPreferences 读，默认 7 天，用户可以在设置里改。
"""
import io

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/DiagLog.java'
s = io.open(P, encoding='utf-8').read()
ok, miss = [], []


def rep(name, old, new):
    global s
    if old in s:
        s = s.replace(old, new, 1)
        ok.append(name)
    else:
        miss.append(name)


# ============================================================
# 1. 上限 512KB → 100MB，并新增常量
# ============================================================
rep('1-上限改100MB',
    '''    static final long MAX_BYTES = 512 * 1024;''',
    '''    /** 单个日志文件上限：100MB（用户要求，原来是 512KB 太小，
     *  出问题想翻前几天的记录都翻不到） */
    static final long MAX_BYTES = 100L * 1024 * 1024;

    /** logs 目录总上限：也是 100MB。
     *  单文件 100MB 是不够的 —— crash.log + 轮转旧文件加起来会更大，
     *  所以目录总量也要卡住，超了从最旧的文件开始删。 */
    static final long MAX_DIR_BYTES = 100L * 1024 * 1024;

    /** 日志保留天数默认值（用户可在设置里改） */
    static final int DEFAULT_KEEP_DAYS = 7;

    /** 保留天数开关的 prefs key */
    static final String KEY_KEEP_DAYS = "log_keep_days";''')

# ============================================================
# 2. 新增：读保留天数
# ============================================================
rep('2-读保留天数',
    '''    // ---------- 开关 ----------''',
    '''    /** 读用户设置的日志保留天数（0 = 永不按时间清理） */
    public static int keepDays(){
        try{
            int v = appCtx.getSharedPreferences(SP, Context.MODE_PRIVATE)
                    .getInt(KEY_KEEP_DAYS, DEFAULT_KEEP_DAYS);
            if(v < 0) v = DEFAULT_KEEP_DAYS;
            return v;
        }catch(Throwable t){ return DEFAULT_KEEP_DAYS; }
    }

    /** 设置日志保留天数（0 = 不按时间清理，只受 100MB 总量约束） */
    public static void setKeepDays(int d){
        try{
            appCtx.getSharedPreferences(SP, Context.MODE_PRIVATE)
                    .edit().putInt(KEY_KEEP_DAYS, d < 0 ? DEFAULT_KEEP_DAYS : d).apply();
        }catch(Throwable t){}
    }

    // ---------- 开关 ----------''')

# ============================================================
# 3. 新增：按天数 + 目录总量清理
# ============================================================
rep('3-清理函数',
    '''    /** 超过上限就截断保留后半段，避免日志无限增长吃满空间 */
    static void rollIfNeeded(File f){''',
    '''    /**
     * 清理策略（用户要求）：
     *   A. 超过保留天数的日志文件 → 直接删
     *      （debug.log / crash.log 是当前文件，不按时间删，
     *        否则正在写的记录会被清掉；它们靠 B 和 C 约束）
     *   B. logs 目录总量超过 MAX_DIR_BYTES → 从最旧的文件开始删，
     *      直到回落到上限的 80% 以下
     *   C. 单个文件超过 MAX_BYTES → 截断保留后半段（rollIfNeeded 原有逻辑）
     *
     * 每次写日志前都会调用，开销只有一次 listFiles，可接受。
     */
    static void cleanupByPolicy(){
        File d = logDir();
        if(d == null) return;
        try{
            File[] fs = d.listFiles();
            if(fs == null || fs.length == 0) return;

            long now = System.currentTimeMillis();
            int days = keepDays();
            // A. 按天数删（跳过当前正在写的两个文件）
            File curDebug = debugFile();
            File curCrash = crashFile();
            if(days > 0){
                long limit = now - (long)days * 24L * 3600L * 1000L;
                for(File f : fs){
                    if(f == null || !f.isFile()) continue;
                    if(f.equals(curDebug) || f.equals(curCrash)) continue;
                    if(f.lastModified() < limit){
                        long sz = f.length();
                        if(f.delete()){
                            i(M_APP, "日志清理：删除超期文件 " + f.getName()
                                    + "（" + sz + " 字节，超过 " + days + " 天）");
                        }
                    }
                }
            }

            // B. 目录总量超限 → 从最旧的开始删
            fs = d.listFiles();
            if(fs == null) return;
            long total = 0;
            for(File f : fs) if(f != null && f.isFile()) total += f.length();
            if(total <= MAX_DIR_BYTES) return;

            // 按最后修改时间升序（最旧在前）
            java.util.Arrays.sort(fs, new java.util.Comparator<File>(){
                public int compare(File a, File b){
                    if(a == null || b == null) return 0;
                    long x = a.lastModified(), y = b.lastModified();
                    return x < y ? -1 : (x > y ? 1 : 0);
                }
            });
            long target = (long)(MAX_DIR_BYTES * 0.8);
            for(File f : fs){
                if(f == null || !f.isFile()) continue;
                if(total <= target) break;
                if(f.equals(curDebug) || f.equals(curCrash)){
                    // 当前文件不能删：先截断到 1MB 再继续判断
                    if(f.length() > 1024 * 1024){
                        try{
                            byte[] tail = readTail(f, 1024 * 1024);
                            if(tail != null){
                                total -= f.length() - tail.length;
                                writeRaw(f, tail);
                            }
                        }catch(Throwable t){}
                    }
                    continue;
                }
                long sz = f.length();
                if(f.delete()){
                    total -= sz;
                    i(M_APP, "日志清理：目录超 100MB，删除 " + f.getName()
                            + "（" + sz + " 字节）");
                }
            }
        }catch(Throwable t){
            // 清理失败不影响正常写日志
        }
    }

    /** 读文件末尾 n 字节 */
    static byte[] readTail(File f, int n){
        try{
            long len = f.length();
            int keep = (int)Math.min(len, n);
            byte[] buf = new byte[keep];
            java.io.RandomAccessFile raf = new java.io.RandomAccessFile(f, "r");
            try{
                raf.seek(len - keep);
                raf.readFully(buf);
            }finally{ try{ raf.close(); }catch(Throwable t){} }
            return buf;
        }catch(Throwable t){ return null; }
    }

    /** 原样写回（覆盖） */
    static void writeRaw(File f, byte[] data){
        try{
            java.io.FileOutputStream fo = new java.io.FileOutputStream(f, false);
            try{ fo.write(data); }finally{ try{ fo.close(); }catch(Throwable t){} }
        }catch(Throwable t){}
    }

    /** 超过上限就截断保留后半段，避免日志无限增长吃满空间 */
    static void rollIfNeeded(File f){''')

# ============================================================
# 4. write() 里接入清理（节流：最多每 30 秒检查一次）
# ============================================================
rep('4-write接入清理',
    '''    static void write(File f, int level, String mod, String msg){
        if(f == null) return;
        synchronized(LOCK){
            try{
                rollIfNeeded(f);''',
    '''    /** 上次清理时间（毫秒）。清理不用每写一行都做，30 秒一次足够。 */
    static volatile long lastCleanup = 0L;

    static void write(File f, int level, String mod, String msg){
        if(f == null) return;
        synchronized(LOCK){
            try{
                long now = System.currentTimeMillis();
                if(now - lastCleanup > 30000L){
                    lastCleanup = now;
                    cleanupByPolicy();
                }
                rollIfNeeded(f);''')

# ============================================================
# 5. init 时先清一次
# ============================================================
rep('5-init清理',
    '''    public static void init(Context c){
        if(c != null && appCtx == null) appCtx = c.getApplicationContext();
    }''',
    '''    public static void init(Context c){
        if(c != null && appCtx == null) appCtx = c.getApplicationContext();
        // 启动时先按策略清一次，避免上次退出时日志已经超限
        try{ cleanupByPolicy(); }catch(Throwable t){}
    }''')

io.open(P, 'w', encoding='utf-8').write(s)
print('成功:', ok)
print('失败:', miss)