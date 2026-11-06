# -*- coding: utf-8 -*-
# 修 API 用法：
#   st.isChecking() 不存在 -> 用 hasMetadata() 判断元数据
import io

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/BtDownloader.java'
d = io.open(P, encoding='utf-8').read()
n = 0

# 1) hasMeta 改用 st.hasMetadata()
old1 = '''                    boolean hasMeta = false;
                    try {
                        hasMeta = h.torrentFile() != null;
                    } catch (Throwable te) {
                        hasMeta = false;
                    }'''
new1 = '''                    boolean hasMeta = st.hasMetadata();
                    if(!hasMeta){
                        try{ hasMeta = h.torrentFile() != null; }catch(Throwable te){}
                    }'''
if old1 in d:
    d = d.replace(old1, new1, 1); n += 1; print('hasMeta OK')
else:
    print('MISS hasMeta')

# 2) isChecking 去掉
old2 = '''                    if (st.isSeeding() || st.isFinished()) state = "已完成";
                    else if (st.isChecking()) state = "校验中…";
                    else if (st.numPeers() == 0) state = "等待 peers…（没人做种可能下不动）";
                    else state = "下载中";'''
new2 = '''                    if (st.isSeeding() || st.isFinished()) state = "已完成";
                    else if (st.numPeers() == 0) state = "等待 peers…（没人做种可能下不动）";
                    else state = "下载中";'''
if old2 in d:
    d = d.replace(old2, new2, 1); n += 1; print('isChecking OK')
else:
    print('MISS isChecking')

# 3) 去掉没用到的 import
for imp in ['import org.libtorrent4j.AlertListener;\n',
            'import org.libtorrent4j.Sha1Hash;\n',
            'import org.libtorrent4j.swig.alert;\n']:
    if imp in d:
        d = d.replace(imp, '', 1); print('去 import:', imp.strip())

io.open(P, 'w', encoding='utf-8').write(d)
print('changed:', n)