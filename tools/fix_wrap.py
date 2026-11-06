# -*- coding: utf-8 -*-
# 按行号把合集处的 rightWrap 改回 row（5898/5899 附近）
import io

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/MainActivity.java'
lines = io.open(P, encoding='utf-8').read().split('\n')

# 定位：btns.addView(btnPlay); 之后紧跟 rightWrap.addView(btns); 和 row.addView(rightWrap);
for k in range(5880, 5910):
    if 'rightWrap.addView(btns);' in lines[k]:
        print('找到 rightWrap.addView(btns) 行:', k + 1)
        # 上一行应是 btns.addView(btnPlay);
        print('  上一行:', repr(lines[k - 1]))
        print('  下一行:', repr(lines[k + 1]))
        # 检查这里是否在 showProfileList 之外（合集场景没有 rightWrap 声明）
        # 直接改成 row.addView(btns)
        lines[k] = lines[k].replace('rightWrap.addView(btns);', 'row.addView(btns);')
        # 下一行如果是 row.addView(rightWrap); 删掉
        if 'row.addView(rightWrap);' in lines[k + 1]:
            del lines[k + 1]
            print('  已删除多余的 row.addView(rightWrap)')
        print('  改后:', repr(lines[k]))
        break

io.open(P, 'w', encoding='utf-8').write('\n'.join(lines))
print('done')

# 校验：rightWrap 声明与使用是否配对
d = '\n'.join(lines)
print('rightWrap 声明次数:', d.count('LinearLayout rightWrap = new LinearLayout(this);'))
print('rightWrap.addView 次数:', d.count('rightWrap.addView'))
print('row.addView(rightWrap) 次数:', d.count('row.addView(rightWrap)'))