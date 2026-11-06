# -*- coding: utf-8 -*-
import io

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/MainActivity.java'
d = io.open(P, encoding='utf-8').read()

old = '''                int[] r = btSelected;
                if (r == null) return null;   // 没选成 -> 全下
                return r;
            }
        });
    }

    void loadForParse(final String url){'''

new = '''                int[] r = btSelected;
                if (r == null) return null;   // 没选成 -> 全下
                return r;
            }
        };
        btCbRef = _cb;
        BtDownloader.start(magnet, dir, _cb);
    }

    void loadForParse(final String url){'''

if old in d:
    d = d.replace(old, new, 1)
    io.open(P, 'w', encoding='utf-8').write(d)
    print('补 start 调用 OK')
else:
    print('MISS')

# 校验
d2 = io.open(P, encoding='utf-8').read()
i = d2.find('btCbRef = _cb;')
print(d2[i-160:i+140] if i > 0 else 'not found')