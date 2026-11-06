# -*- coding: utf-8 -*-
"""
修正 upgrade_parse_dy_profile.py 里的两个字段类型错误：
  · imgs / dyns 是 List<String>，不是 String（我上一版按 tsv 的 "|" 拼接理解错了）
  · ProfileItem 没有 live 字段，删掉赋值
"""
import io

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/MainActivity.java'
s = io.open(P, encoding='utf-8').read()
ok, miss = [], []


def rep(name, old, new):
    global s
    if old in s:
        s = s.replace(old, new, 1)
        ok.append(name)
    else:
        miss.append(name)


rep('修正 imgs/dyns 赋值',
    '''                if(!imgUrls.isEmpty()){
                    it.directUrl = imgUrls.get(0);
                    it.cover = imgUrls.get(0);
                    try{
                        StringBuilder sb = new StringBuilder();
                        for(String u : imgUrls){
                            if(sb.length() > 0) sb.append("|");
                            sb.append(u);
                        }
                        it.imgs = sb.toString();
                    }catch(Throwable t){}
                    int liveN = 0;
                    try{
                        StringBuilder sb2 = new StringBuilder();
                        for(String u : dynUrls){
                            if(sb2.length() > 0) sb2.append("|");
                            if(u != null) sb2.append(u);
                            if(u != null && u.length() > 0) liveN++;
                        }
                        it.dyns = sb2.toString();
                    }catch(Throwable t){}
                    it.live = liveN > 0;
                    DypLog.i("PROF_WEB_IMGS id=" + awemeId + " n=" + imgUrls.size()
                            + " live=" + liveN);
                }''',
    '''                if(!imgUrls.isEmpty()){
                    it.directUrl = imgUrls.get(0);
                    it.cover = imgUrls.get(0);
                    // imgs / dyns 本身就是 List<String>，直接放进去
                    try{
                        it.imgs.clear();
                        it.imgs.addAll(imgUrls);
                    }catch(Throwable t){}
                    int liveN = 0;
                    try{
                        it.dyns.clear();
                        for(String u : dynUrls){
                            it.dyns.add(u == null ? "" : u);
                            if(u != null && u.length() > 0) liveN++;
                        }
                    }catch(Throwable t){}
                    DypLog.i("PROF_WEB_IMGS id=" + awemeId + " n=" + imgUrls.size()
                            + " live=" + liveN);
                }''')

io.open(P, 'w', encoding='utf-8').write(s)
print('成功:', ok)
print('失败:', miss)