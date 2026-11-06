# -*- coding: utf-8 -*-
"""
【第 2 项】设置里的输入框高亮显示。

【问题】
  现有的输入框只设了 setTextColor / setHintTextColor，
  但没设背景。ColorOS 的对话框默认主题下，输入框背景和对话框底色接近，
  用户「根本看不清输入的内容」——尤其是深色模式下。

【改法】
  统一走 styleInput(EditText)：
    · 背景 = makeRoundRect(输入框底色, 描边色, 圆角 10dp)
      深色: 底色 0xFF1A1D24、描边 0xFF3A3F4B
      浅色: 底色 0xFFF7F8FA、描边 0xFFD8DBE2
    · 文字色：深色 0xFFE6E8EC / 浅色 0xFF1A1C22（对比度都够）
    · hint 色：深色 0xFF8A8F9C / 浅色 0xFF9AA0AC（弱于正文但不糊）
    · 内边距 12dp，光标颜色跟随主题

  只改样式，不动逻辑，风险低。
"""
import io
import re

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/MainActivity.java'
s = io.open(P, encoding='utf-8').read()
ok, miss = [], []


def rep(name, old, new, cnt=1):
    global s
    if old in s:
        s = s.replace(old, new, cnt)
        ok.append(name)
    else:
        miss.append(name)


# ============================================================
# 1. 新增 styleInput 工具
# ============================================================
anchor = '''    /** 设置页行 */'''

helper = '''    /** 统一设置输入框样式：高亮可读。
     *
     *  原来的输入框只设了文字色，没设背景 ——
     *  在 ColorOS 的对话框主题下，输入框和对话框底色几乎一样，
     *  用户根本看不清自己输入了什么（尤其深色模式）。
     *
     *  这里给：实心底色 + 明显描边 + 圆角 + 内边距，
     *  保证「没聚焦时也能一眼看出这是输入区」。
     */
    void styleInput(EditText et){
        if(et == null) return;
        boolean dark = isDarkMode();
        int bg  = dark ? 0xFF1A1D24 : 0xFFF7F8FA;
        int str = dark ? 0xFF3A3F4B : 0xFFD8DBE2;
        int txt = dark ? 0xFFE6E8EC : 0xFF1A1C22;
        int sub = dark ? 0xFF8A8F9C : 0xFF9AA0AC;
        int accent = 0xFFFE2C55;
        try{
            et.setBackground(makeRoundRect(bg, str, 10));
        }catch(Throwable t){}
        et.setTextColor(txt);
        et.setHintTextColor(sub);
        int p = dp(12);
        et.setPadding(p, dp(10), p, dp(10));
        // 光标用主题强调色，聚焦时看得见
        try{
            if(android.os.Build.VERSION.SDK_INT >= 29){
                et.setTextCursorDrawable(null);
            }
        }catch(Throwable t){}
    }

    /** 设置页行 */'''

if 'void styleInput(EditText et)' not in s:
    s = s.replace(anchor, helper, 1)
    ok.append('1-styleInput')
else:
    ok.append('1-已存在')

# ============================================================
# 2. 逐个套用
# ============================================================

# 2a. 批量解析输入框（3759 附近）
rep('2a-批量解析框',
    '''        final EditText et = new EditText(this);
        et.setHint("https://v.douyin.com/xxxxx/");
        et.setTextSize(13);
        et.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
        et.setMinLines(6);
        et.setMaxLines(12);''',
    '''        final EditText et = new EditText(this);
        et.setHint("https://v.douyin.com/xxxxx/");
        et.setTextSize(13);
        et.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
        et.setMinLines(6);
        et.setMaxLines(12);
        styleInput(et);''')

# 2b. Cookie 输入框（4825 附近）
rep('2b-Cookie框',
    '''        final EditText et = new EditText(this);
        et.setTextSize(12);
        et.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
        et.setMinLines(8);
        et.setMaxLines(14);''',
    '''        final EditText et = new EditText(this);
        et.setTextSize(12);
        et.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
        et.setMinLines(8);
        et.setMaxLines(14);
        styleInput(et);''')

# 2c. UIFID 输入框（9317 附近）
rep('2c-UIFID框',
    '''        final EditText et = new EditText(this);
        et.setText(cur);
        et.setHint("粘贴 UIFID_TEMP 的值");
        et.setTextSize(13);
        et.setTextColor(textDlg);
        et.setHintTextColor(subDlg);
        et.setSingleLine(false);
        et.setMinLines(2);''',
    '''        final EditText et = new EditText(this);
        et.setText(cur);
        et.setHint("粘贴 UIFID_TEMP 的值");
        et.setTextSize(13);
        et.setSingleLine(false);
        et.setMinLines(2);
        styleInput(et);''')

# 2d. 服务器地址输入框（9505 附近）
rep('2d-服务器框',
    '''        final EditText etSrv = new EditText(this);''',
    '''        final EditText etSrv = new EditText(this);
        styleInput(etSrv);''')

# 2e. 主页解析输入框（17278 附近）
rep('2e-主页解析框',
    '''        final EditText et = new EditText(this);
        et.setHint("粘贴主页链接，例如\\nhttps://www.douyin.com/user/xxx\\nhttps://v.douyin.com/xxxxx/");
        et.setSingleLine(false);
        et.setMinLines(3);
        et.setTextSize(13);
        et.setTextColor(curDark ? 0xFFE6E8EC : 0xFF23262E);
        et.setHintTextColor(curDark ? 0xFF7A8194 : 0xFF9AA0AC);''',
    '''        final EditText et = new EditText(this);
        et.setHint("粘贴主页链接，例如\\nhttps://www.douyin.com/user/xxx\\nhttps://v.douyin.com/xxxxx/");
        et.setSingleLine(false);
        et.setMinLines(3);
        et.setTextSize(13);
        styleInput(et);''')

# 2f. 下载目录输入框（5070 附近）—— 这个后面会被目录选择器替换，
#     但先统一风格，避免替换没生效时还是看不清
rep('2f-下载目录框',
    '''        final EditText et = new EditText(this);
        et.setText(getSharedPreferences(SP, MODE_PRIVATE).getString(key, ""));
        et.setHint(def);
        et.setSingleLine(true);
        et.setTextSize(13);
        et.setTextColor(curDark ? 0xFFE6E8EC : 0xFF23262E);
        et.setHintTextColor(curDark ? 0xFF7A8194 : 0xFF9AA0AC);''',
    '''        final EditText et = new EditText(this);
        et.setText(getSharedPreferences(SP, MODE_PRIVATE).getString(key, ""));
        et.setHint(def);
        et.setSingleLine(true);
        et.setTextSize(13);
        styleInput(et);''')

io.open(P, 'w', encoding='utf-8').write(s)
print('成功:', ok)
print('失败:', miss)