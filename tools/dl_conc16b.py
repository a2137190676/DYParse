# -*- coding: utf-8 -*-
# 档位列表扩到 16
import io

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/MainActivity.java'
d = io.open(P, encoding='utf-8').read()

old = '''        final String[] items = new String[]{
            "1 个 · 串行（最稳，旧机型推荐）",
            "2 个 · 省流量",
            "3 个 · 均衡（默认）",
            "4 个 · 较快",
            "5 个 · 快",
            "6 个 · 很快（可能被平台限速）",
            "7 个 · 极快（耗流量/发热）",
            "8 个 · 上限（最快，慎用）"
        };'''

new = '''        final String[] items = new String[]{
            "1 个 · 串行（最稳，旧机型推荐）",
            "2 个 · 省流量",
            "3 个 · 均衡（默认）",
            "4 个 · 较快",
            "5 个 · 快",
            "6 个 · 很快（可能被平台限速）",
            "7 个 · 极快（耗流量/发热）",
            "8 个 · 上限（快，慎用）",
            "10 个 · 激进（部分平台会拒绝）",
            "12 个 · 更激进（耗电明显）",
            "16 个 · 拉满（最快，老机型可能卡）"
        };'''

if old in d:
    d = d.replace(old, new, 1)
    # 档位到数值的映射：索引 -> 真实并发数
    old2 = '''                public void onClick(android.content.DialogInterface d, int which){
                    int n = which + 1;
                    getSharedPreferences(SP, MODE_PRIVATE).edit()
                        .putInt("dl_concurrency", n).apply();'''
    new2 = '''                public void onClick(android.content.DialogInterface d, int which){
                    // 索引 -> 实际并发数（1~8 直接对应，后面跳到 10/12/16）
                    int n;
                    if(which < 8) n = which + 1;
                    else if(which == 8) n = 10;
                    else if(which == 9) n = 12;
                    else n = 16;
                    getSharedPreferences(SP, MODE_PRIVATE).edit()
                        .putInt("dl_concurrency", n).apply();'''
    if old2 in d:
        d = d.replace(old2, new2, 1)
        print('档位映射 OK')
    else:
        print('MISS 档位映射')

    # 选中态也要按数值反查索引
    old3 = '''.setSingleChoiceItems(items, cur - 1, new android.content.DialogInterface.OnClickListener(){'''
    new3 = '''.setSingleChoiceItems(items, concToIndex(cur), new android.content.DialogInterface.OnClickListener(){'''
    if old3 in d:
        d = d.replace(old3, new3, 1)
        print('选中态 OK')

    # 加反查方法
    anchor = '''    /** 同时下载数：1~8，默认 3 */'''
    helper = '''    /** 并发数 -> 弹窗里的索引（1~8 连续，10/12/16 在后面） */
    static int concToIndex(int n){
        if(n <= 8) return Math.max(0, n - 1);
        if(n <= 10) return 8;
        if(n <= 12) return 9;
        return 10;
    }

''' + anchor
    if anchor in d:
        d = d.replace(anchor, helper, 1)
        print('concToIndex OK')

    io.open(P, 'w', encoding='utf-8').write(d)
    print('done')
else:
    print('MISS items')