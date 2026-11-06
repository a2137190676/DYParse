# -*- coding: utf-8 -*-
import io

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/MainActivity.java'
d = io.open(P, encoding='utf-8').read()

bad = '''                    }});
                }
            }
}
        }catch(Throwable t){ Log.i("DYP", "YT_CK_GRAB_ERR " + t); }'''

good = '''                    }});
                }
            }
        }catch(Throwable t){ Log.i("DYP", "YT_CK_GRAB_ERR " + t); }'''

if bad in d:
    d = d.replace(bad, good, 1)
    io.open(P, 'w', encoding='utf-8').write(d)
    print('缩进修正 OK')
else:
    print('MISS')
    i = d.find('YT_CK_GRAB_ERR')
    print(repr(d[max(0, i-160):i+80]))