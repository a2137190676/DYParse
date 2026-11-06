# -*- coding: utf-8 -*-
"""
修：并发下载时，4 条里有 1 条被误判「目录不可写」，文件落到
    /Download/DYParse/视频/ 根目录，而不是 /Download/DYParse/抖音/视频/

实测日志：
    19:51:58.307  DL_SCOPED_FALLBACK 目录不可写，改用私有临时目录:
                 /storage/emulated/0/Download/DYParse/抖音/视频
                 -> /data/user/0/com.re.douyinparse/cache/dl_tmp
    19:52:06.482  DL_SCOPED_MOVED dy_uacf98a13_视频4.mp4
                 -> content://media/external/downloads/1000220233

    目录明明是可写的（同一批另外 3 条都写进去了），
    只有排在第 4 位、恰好和前面同毫秒启动的那条判成不可写。

根因（ScopedStore.canWriteDir）：

    File probe = new File(dir, ".dyp_probe_" + System.currentTimeMillis());
    boolean ok = probe.createNewFile();

    currentTimeMillis() 只有毫秒精度。
    4 条任务在 dlConcurrency=8 下一启动就并发进入这里，
    同一毫秒内生成**完全相同**的探测文件名：
        第 1 条 createNewFile() → true，随后 delete()
        第 2/3/4 条撞上已存在的同名文件 → createNewFile() 返回 false
    → 被判成「目录不可写」→ 走私有目录 + MediaStore 兜底。

    createNewFile() 的 false 有两种含义：
      a) 文件已存在（这里的情况，目录其实是好的）
      b) 真的写不进去
    代码把 a 当成了 b。

改法：
  1. 探测文件名加随机后缀，彻底避免并发撞名。
  2. 回退判断：如果 createNewFile() 失败但文件存在，
     说明目录可写（只是名字被占了），判为可写。
  3. 无论如何 finally 清理探测文件，别留垃圾。
"""
import io

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/ScopedStore.java'
s = io.open(P, encoding='utf-8').read()

old = '''    public static boolean canWriteDir(File dir){
        try{
            if(dir == null) return false;
            if(!dir.exists() && !dir.mkdirs()) return false;
            File probe = new File(dir, ".dyp_probe_" + System.currentTimeMillis());
            boolean ok = probe.createNewFile();
            if(ok) probe.delete();
            return ok;
        }catch(Throwable t){
            return false;
        }
    }'''

new = '''    public static boolean canWriteDir(File dir){
        try{
            if(dir == null) return false;
            if(!dir.exists() && !dir.mkdirs()) return false;
            // 【并发安全】探测文件名必须唯一。
            //
            // 原来是 ".dyp_probe_" + currentTimeMillis()，毫秒精度。
            // 批量下载在 dlConcurrency=8 下一启动就并发调这里，
            // 同一毫秒内会生成完全相同的探测名：
            //   第 1 条 createNewFile() → true，随后 delete()
            //   第 2~8 条撞上已存在的同名文件 → createNewFile() 返回 false
            // → 误判「目录不可写」，文件被搬去私有目录再经 MediaStore
            //   落到 Download/DYParse/视频 根目录，用户就找不到文件。
            //
            // 实测：4 条并发里恰好 1 条落错目录，其余 3 条正常。
            String rnd = Long.toHexString(Double.doubleToLongBits(Math.random()));
            File probe = new File(dir, ".dyp_probe_" + System.currentTimeMillis() + "_" + rnd);
            boolean ok;
            try{
                ok = probe.createNewFile();
            }catch(Throwable t){
                return false;
            }
            if(!ok){
                // createNewFile 返回 false 有两种可能：
                //   a) 文件已存在（并发撞名）—— 目录其实是可写的
                //   b) 目录真的不可写
                // 用 exists() 区分：存在就说明我们（或别的线程）能在这里建文件。
                ok = probe.exists();
            }
            try{ if(probe.exists()) probe.delete(); }catch(Throwable t){}
            return ok;
        }catch(Throwable t){
            return false;
        }
    }'''

if old in s:
    s = s.replace(old, new, 1)
    io.open(P, 'w', encoding='utf-8').write(s)
    print('OK canWriteDir 已修')
else:
    print('FAIL 找不到 canWriteDir')