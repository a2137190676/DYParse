# -*- coding: utf-8 -*-
# 删除 366 行多余的 }
import io

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/MainActivity.java'
lines = io.open(P, encoding='utf-8').read().split('\n')

# 找 uploadYtCookie(full); 之后的 h.post 块，再往后第一个孤立的 }
i = None
for k in range(340, 380):
    if 'uploadYtCookie(full);' in lines[k]:
        i = k
        break

print('uploadYtCookie 行:', i + 1 if i else 'MISS')

if i:
    # 从 i 往后找 "}});" 然后下一个 "}" 是多余的
    for k in range(i, i + 12):
        if lines[k].strip() == '}});':
            print('  }}); 行:', k + 1)
            # 找它后面第一个 "}" 行
            for m in range(k + 1, k + 4):
                if lines[m].strip() == '}':
                    print('  第一个 } 行:', m + 1, repr(lines[m]))
            # 再找第二个
            cnt = 0
            for m in range(k + 1, k + 6):
                if lines[m].strip() == '}':
                    cnt += 1
                    print('  } #' + str(cnt) + ' 行:', m + 1)
            # 如果有两个连续 }，删第二个
            first = None
            second = None
            for m in range(k + 1, k + 6):
                if lines[m].strip() == '}':
                    if first is None:
                        first = m
                    elif second is None:
                        second = m
                        break
            if second is not None:
                del lines[second]
                print('已删除第', second + 1, '行多余的 }')
            break

io.open(P, 'w', encoding='utf-8').write('\n'.join(lines))
print('done')

# 校验
d = '\n'.join(lines)
print('curDark 附近:')
j = d.find('curDark = isDarkMode();')
print(d[max(0,j-200):j+60])