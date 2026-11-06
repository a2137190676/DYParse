# -*- coding: utf-8 -*-
# 加 parseTencentVideo()，照微博的写法
import io

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/MainActivity.java'
d = io.open(P, encoding='utf-8').read()

anchor = '''        // 微博：本地直连（visitor 访客匿名），借鉴 media-parser 的 weibo 方案'''

method = '''        // 腾讯视频：本地直连 getinfo 接口
        if(TxParser.isTxUrl(url)){
            s("解析腾讯视频...");
            web.setVisibility(View.GONE);
            new Thread(new Runnable(){ public void run(){
                final TxParser.TxVideo v = TxParser.parse(url);
                Log.i("DYP", "TX_RESULT ok=" + v.ok + " err=" + v.err
                    + " gears=" + v.gears.size() + " td=" + v.durSec);
                h.post(new Runnable(){ public void run(){
                    if(v.ok && v.playUrl.length() > 0){
                        webDone = true;
                        StringBuilder sb = new StringBuilder();
                        sb.append("【腾讯视频】").append(v.title).append("\\n");
                        sb.append("【视频ID】").append(v.vid).append("\\n");
                        sb.append("【时长】").append(fmtSec((long) v.durSec)).append("\\n");
                        if(v.fileSize > 0){
                            sb.append("【大小】").append(InnerDownloader.sizeText(v.fileSize)).append("\\n");
                        }
                        if(v.gears.size() > 0){
                            sb.append("【清晰度】");
                            for(int i = 0; i < v.gears.size(); i++){
                                String[] g = v.gears.get(i);
                                if(i > 0) sb.append(" / ");
                                sb.append(g[0]);
                                try{
                                    long fs = Long.parseLong(g[2]);
                                    if(fs > 0) sb.append("(").append(InnerDownloader.sizeText(fs)).append(")");
                                }catch(Throwable t){}
                            }
                            sb.append("\\n");
                        }
                        sb.append("【直链】").append(v.playUrl);
                        o2(sb.toString(), v.playUrl, "mp4", "");
                        s("完成(腾讯视频) " + fmtSec((long) v.durSec));
                    } else {
                        s("腾讯视频解析失败：" + (v.err.length() > 0 ? v.err : "无直链"));
                        showHintToast("该视频可能需要会员，或链接已失效");
                    }
                }});
            }}).start();
            return;
        }
''' + anchor

if anchor in d and 'TxParser.isTxUrl(url)' not in d:
    d = d.replace(anchor, method, 1)
    io.open(P, 'w', encoding='utf-8').write(d)
    print('parseTencentVideo OK')
else:
    print('MISS/EXISTS')

# 检查 fmtSec 是否存在
print('fmtSec 存在:', 'String fmtSec' in d or 'fmtSec(' in d)
i = d.find('String fmtSec')
if i > 0:
    print(d[i:i+80])