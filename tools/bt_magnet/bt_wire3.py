# -*- coding: utf-8 -*-
# 修正：磁力拦截放在「平台前置校验」最前面（parse 主入口），
#       而不是 loadForParse 里（那里太靠后，且参数名是 url）
import io

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/MainActivity.java'
d = io.open(P, encoding='utf-8').read()
n = 0

# 1) 删掉 loadForParse 里那段（位置不对）
old_del = '''        // 磁力 / BT 种子：直接交给 BtDownloader，不走平台解析
        if(BtDownloader.isMagnet(url)){
            startMagnetDownload(url);
            return;
        }
'''
if old_del in d:
    d = d.replace(old_del, '', 1); n += 1; print('删除旧拦截 OK')
else:
    print('MISS 删除旧拦截')

# 2) 在平台前置校验最前面插入
old_ins = '''        if(url==null){ s("未找到链接"); return; }
        // ===== 平台前置校验 ====='''
new_ins = '''        if(url==null){ s("未找到链接"); return; }
        // ===== 磁力 / BT 种子 =====
        // 放在所有平台校验之前：磁力链接不走任何平台解析，直接交给 BT 引擎
        if(BtDownloader.isMagnet(url)){
            Log.i("DYP", "BT_PARSE " + url);
            startMagnetDownload(url);
            return;
        }
        // ===== 平台前置校验 ====='''
if old_ins in d:
    d = d.replace(old_ins, new_ins, 1); n += 1; print('插入拦截 OK')
else:
    print('MISS 插入拦截')
    i = d.find('未找到链接')
    print(repr(d[i - 100:i + 200]))

io.open(P, 'w', encoding='utf-8').write(d)
print('changed:', n)