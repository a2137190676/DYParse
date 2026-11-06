# -*- coding: utf-8 -*-
"""
修：cleanupByPolicy 里的 i(M_APP, ...) 会递归。

链路：
    write() → cleanupByPolicy() → i(M_APP,...) → write() → cleanupByPolicy() ...

虽然 seconds 节流能挡住大部分，但第一次调用时
lastCleanup 刚被赋值，30 秒内不会重入 —— 这条其实是安全的。
不过为了彻底干净，且避免「清理日志本身又写日志」导致刚删掉就长回来，
改成直接追加一行到 debug.log，绕过所有策略检查。
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


# 1) 删除超期文件的日志
rep('1-超期日志',
    '''                        if(f.delete()){
                            i(M_APP, "日志清理：删除超期文件 " + f.getName()
                                    + "（" + sz + " 字节，超过 " + days + " 天）");
                        }''',
    '''                        if(f.delete()){
                            noteCleanup("删除超期文件 " + f.getName()
                                    + "（" + sz + " 字节，超过 " + days + " 天）");
                        }''')

# 2) 目录超限的日志
rep('2-超限日志',
    '''                if(f.delete()){
                    total -= sz;
                    i(M_APP, "日志清理：目录超 100MB，删除 " + f.getName()
                            + "（" + sz + " 字节）");
                }''',
    '''                if(f.delete()){
                    total -= sz;
                    noteCleanup("目录超 100MB，删除 " + f.getName()
                            + "（" + sz + " 字节）");
                }''')

# 3) 新增 noteCleanup：直接追加，不走策略检查，避免递归
anchor = '''    /** 读文件末尾 n 字节 */'''
helper = '''    /** 写一条清理记录。
     *
     *  不能调 i() —— i() 会进 write()，write() 又会调 cleanupByPolicy()，
     *  形成 write → cleanup → write 的递归，
     *  而且「清理动作本身触发新日志」会让刚压下去的体积又涨回来。
     *  这里直接追加，不做任何检查和清理。
     */
    static void noteCleanup(String msg){
        try{
            File f = debugFile();
            if(f == null) return;
            synchronized(LOCK){
                FileWriter w = new FileWriter(f, true);
                try{
                    w.append(LINE_TS.format(new Date()))
                     .append(" [信息] [").append(M_APP).append("] [日志清理] ")
                     .append(msg == null ? "" : msg)
                     .append('\\n');
                }finally{
                    try{ w.close(); }catch(Throwable t){}
                }
            }
        }catch(Throwable t){}
    }

'''
if 'void noteCleanup(' not in s:
    s = s.replace(anchor, helper + anchor, 1)
    ok.append('3-noteCleanup')
else:
    ok.append('3-已存在')

io.open(P, 'w', encoding='utf-8').write(s)
print('成功:', ok)
print('失败:', miss)