# -*- coding: utf-8 -*-
# 磁力拦截必须放在 extractUrl 之前：
#   extractUrl 只认 http(s)，磁力链接会返回 null -> 「未找到链接」
import io

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/MainActivity.java'
d = io.open(P, encoding='utf-8').read()
n = 0

# 1) 删掉之前插在 url==null 之后的那段
old_del = '''        // ===== 磁力 / BT 种子 =====
        // 放在所有平台校验之前：磁力链接不走任何平台解析，直接交给 BT 引擎
        if(BtDownloader.isMagnet(url)){
            Log.i("DYP", "BT_PARSE " + url);
            startMagnetDownload(url);
            return;
        }
'''
if old_del in d:
    d = d.replace(old_del, '', 1); n += 1; print('删除旧位置 OK')
else:
    print('MISS 删除旧位置')

# 2) 插到 parse() 开头，raw 判空之后
old_ins = '''    void parse(){
         final String raw=input.getText().toString().trim();
         if(raw.length()==0){ s("空链接"); return; }'''

new_ins = '''    void parse(){
         final String raw=input.getText().toString().trim();
         if(raw.length()==0){ s("空链接"); return; }
         // ===== 磁力 / BT 种子 =====
         // 必须放在 extractUrl 之前：extractUrl 只认 http(s)，磁力链接会返回 null
         if(BtDownloader.isMagnet(raw)){
             Log.i("DYP", "BT_PARSE " + raw);
             startMagnetDownload(raw);
             return;
         }'''

if old_ins in d:
    d = d.replace(old_ins, new_ins, 1); n += 1; print('插入 parse 开头 OK')
else:
    print('MISS 插入')

io.open(P, 'w', encoding='utf-8').write(d)
print('changed:', n)

# 校验
d2 = io.open(P, encoding='utf-8').read()
print('BT_PARSE 次数:', d2.count('BT_PARSE'))
i = d2.find('void parse(){')
print('--- parse 开头 ---')
print(d2[i:i + 620])