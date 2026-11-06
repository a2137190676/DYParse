# -*- coding: utf-8 -*-
"""A) 全部下载按钮：按行号精确替换"""
import io

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/MainActivity.java'
lines = io.open(P, encoding='utf-8').read().split('\n')

# 定位 16074 附近的 btnDlAll 监听（内容是「两步：先选...」那段）
start = None
for i, ln in enumerate(lines):
    if '// 两步：先选「下载全部」还是「挑第几个」' in ln:
        # 往上找 btnDlAll.setOnClickListener
        for j in range(i, max(0, i - 5), -1):
            if 'btnDlAll.setOnClickListener' in lines[j]:
                start = j
                break
        break

if start is None:
    print('未找到 btnDlAll 监听')
    raise SystemExit(1)

# 找结束：以 "        }});" 结尾（缩进 8 空格 + }});）
end = None
for i in range(start, min(start + 60, len(lines))):
    if lines[i].rstrip() == '        }});':
        end = i
        break

if end is None:
    print('未找到结束位置')
    raise SystemExit(1)

print('替换行范围: %d - %d (%d 行)' % (start + 1, end + 1, end - start + 1))

new_block = '''btnDlAll.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){
            // 「全部下载」= 只负责「本页全部」，不再弹选项。
            // 想挑第几个请用旁边的「批量下载」按钮，两个按钮各管一件事。
            dypDlg()
                .setTitle("全部下载")
                .setMessage("【本页 " + (to - from) + " 条】全部加入下载队列？\\n\\n"
                    + "全部共 " + n + " 条（" + pages + " 页），换页请先用「上一页/下一页」切到目标页。\\n"
                    + "加进队列后会逐条解析出真实直链，再按并发数（"
                    + dlConcurrency() + " 个）同时下载。")
                .setPositiveButton("确定下载本页", new android.content.DialogInterface.OnClickListener(){
                    public void onClick(android.content.DialogInterface d, int w){
                        int added = 0;
                        for(int i = from; i < to; i++){
                            ProfileParser.ProfileItem it = list.get(i);
                            String u = it.url.length() > 0 ? it.url
                                    : (it.id.length() > 0 && it.id.matches("\\\\d{15,20}") ? "https://www.tiktok.com/@/video/" + it.id : "");
                            if(u.length() == 0) continue;
                            String tag = "视频" + (i + 1);
                            enqueueDownload(u, tag, "mp4", "视频");
                            added++;
                        }
                        s("已加入下载队列：" + added + " 条");
                        showHintToast("已加入 " + added + " 条下载任务");
                        switchTab(0);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
        }});'''

lines[start:end + 1] = new_block.split('\n')
io.open(P, 'w', encoding='utf-8').write('\n'.join(lines))
print('A 项已替换')