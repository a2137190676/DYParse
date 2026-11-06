# -*- coding: utf-8 -*-
"""补 subTabMixParse 的字段声明 + findViewById"""
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


rep('1-字段声明',
    '''    TextView subTabBatchParse, subTabProfileParse, subTabBatchDl;''',
    '''    TextView subTabBatchParse, subTabProfileParse, subTabBatchDl, subTabMixParse;''')

rep('2-findViewById',
    '''        subTabBatchParse = (TextView)findViewById(R.id.subTabBatchParse);''',
    '''        subTabBatchParse = (TextView)findViewById(R.id.subTabBatchParse);
        subTabMixParse = (TextView)findViewById(R.id.subTabMixParse);''')

rep('3-调试日志',
    '''        Log.i("DYP", "SUBTAB_FIND batch=" + (subTabBatchParse != null)''',
    '''        Log.i("DYP", "SUBTAB_FIND batch=" + (subTabBatchParse != null)
                + " mix=" + (subTabMixParse != null)''')

io.open(P, 'w', encoding='utf-8').write(s)
print('成功:', ok)
print('失败:', miss)