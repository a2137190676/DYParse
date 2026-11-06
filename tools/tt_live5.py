# -*- coding: utf-8 -*-
# liveImages 是纯字符串数组 ["http://a","http://b"]，
# jarrUrls 只认 {"url":"..."} 格式，所以要加个纯串版解析。
import io

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/MainActivity.java'
d = io.open(P, encoding='utf-8').read()
n = 0

# 1) 加 strArr 方法
anchor = '''    /** 从 images JSON 数组里抽 url 字段 */
    static String[] jarrUrls(String arrJson){'''
method = '''    /** 从纯字符串数组 ["http://a","http://b"] 里抽元素（liveImages 用） */
    static String[] strArr(String arrJson){
        java.util.List<String> out = new java.util.ArrayList<String>();
        if(arrJson == null || arrJson.length() < 3) return new String[0];
        int i = 1;   // 跳过 '['
        int n = arrJson.length();
        while(i < n){
            int q1 = arrJson.indexOf('"', i);
            if(q1 < 0) break;
            // 跳过转义引号
            StringBuilder sb = new StringBuilder();
            int k = q1 + 1;
            while(k < n){
                char c = arrJson.charAt(k);
                if(c == '\\\\' && k + 1 < n){
                    sb.append(arrJson.charAt(k + 1));
                    k += 2;
                    continue;
                }
                if(c == '"') break;
                sb.append(c);
                k++;
            }
            String u = sb.toString();
            if(u.startsWith("http")) out.add(u);
            i = (k < n) ? k + 1 : n;
        }
        return out.toArray(new String[0]);
    }

''' + anchor
if anchor in d and 'static String[] strArr' not in d:
    d = d.replace(anchor, method, 1); n += 1; print('strArr OK')
else:
    print('MISS strArr')

# 2) TT_LIVE 那段改用 strArr 读 live，imgArrX 仍用 jarrUrls
old = '''                            String[] liveUrls = jarrUrls(liveArr);
                            String[] imgUrls2 = jarrUrls(imgArrX);'''
new = '''                            // liveImages 是纯字符串数组；images 是 [{"url":...}] 对象数组
                            String[] liveUrls = strArr(liveArr);
                            if(liveUrls.length == 0) liveUrls = jarrUrls(liveArr);
                            String[] imgUrls2 = jarrUrls(imgArrX);'''
if old in d:
    d = d.replace(old, new, 1); n += 1; print('live 解析改用 strArr OK')
else:
    print('MISS live 解析')

io.open(P, 'w', encoding='utf-8').write(d)
print('changed:', n)