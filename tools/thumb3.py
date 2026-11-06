# -*- coding: utf-8 -*-
# 解析列表：加封面缩略图（用 ProfileItem.cover）
import io

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/MainActivity.java'
d = io.open(P, encoding='utf-8').read()

# 先确认 ProfileItem 有 cover 字段
print('cover 字段:', 'public String cover' in io.open(
    '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/ProfileParser.java',
    encoding='utf-8').read())

old = '''            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(dp(12), dp(10), dp(12), dp(10));
            row.setBackground(makeRoundRect(curDark ? 0xFF24272F : 0xFFFFFFFF,
                    curDark ? 0xFF333842 : 0xFFE1E4E9, 12));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(8);
            row.setLayoutParams(lp);
            TextView title = new TextView(this);
            String t = it.title;
            if(t.length() == 0) t = it.id;
            title.setText((i + 1) + ". " + t);
            title.setTextSize(13);
            title.setTextColor(curDark ? 0xFFE8EAF0 : 0xFF1A1C22);
            title.setPadding(0, 0, 0, dp(6));
            title.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){
                switchTab(0);
                parseProfileItem(list, idx);
            }});
            row.addView(title);'''

new = '''            // 外层：横向（左缩略图 + 右内容）
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setPadding(dp(10), dp(9), dp(10), dp(9));
            row.setBackground(makeRoundRect(curDark ? 0xFF24272F : 0xFFFFFFFF,
                    curDark ? 0xFF333842 : 0xFFE1E4E9, 12));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(8);
            row.setLayoutParams(lp);

            // 左：缩略图（有 cover 就网络加载，没有就类型图标）
            final android.widget.ImageView thumb = makeThumbView(dp(58));
            row.addView(thumb);
            String cov = it.cover;
            if (cov != null && cov.length() > 0) {
                loadNetThumb(thumb, cov, dp(58));
            } else {
                // 没封面：按条目类型给图标
                TextView ic = new TextView(this);
                ic.setText(it.isImage ? "🖼" : (it.durSec > 0 ? "🎬" : "📎"));
                ic.setTextSize(22);
                ic.setGravity(android.view.Gravity.CENTER);
                LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(58), dp(58));
                ilp.rightMargin = dp(10);
                ic.setLayoutParams(ilp);
                ic.setBackground(makeRoundRect(curDark ? 0xFF2C3038 : 0xFFEDF0F5, 0, dp(8)));
                row.removeView(thumb);
                row.addView(ic);
            }

            // 右：竖排（标题 + 时长/类型 + 按钮）
            LinearLayout rightWrap = new LinearLayout(this);
            rightWrap.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            rightWrap.setLayoutParams(rlp);

            TextView title = new TextView(this);
            String t = it.title;
            if(t.length() == 0) t = it.id;
            title.setText((i + 1) + ". " + t);
            title.setTextSize(13);
            title.setTextColor(curDark ? 0xFFE8EAF0 : 0xFF1A1C22);
            title.setMaxLines(2);
            title.setEllipsize(android.text.TextUtils.TruncateAt.END);
            title.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){
                switchTab(0);
                parseProfileItem(list, idx);
            }});
            rightWrap.addView(title);

            // 时长/类型小字
            StringBuilder sbMeta = new StringBuilder();
            if (it.durSec > 0) {
                long dsec = it.durSec;
                sbMeta.append(String.format(java.util.Locale.US, "%d:%02d", dsec / 60, dsec % 60));
            }
            if (it.isImage) {
                if (sbMeta.length() > 0) sbMeta.append(" · ");
                sbMeta.append("图集");
            }
            if (sbMeta.length() > 0) {
                TextView tmeta = new TextView(this);
                tmeta.setText(sbMeta.toString());
                tmeta.setTextSize(10);
                tmeta.setTextColor(curDark ? 0xFF8A8F9C : 0xFF9AA0AC);
                tmeta.setPadding(0, dp(2), 0, dp(4));
                rightWrap.addView(tmeta);
            }

            LinearLayout btns = new LinearLayout(this);
            btns.setOrientation(LinearLayout.HORIZONTAL);'''

if old in d:
    d = d.replace(old, new, 1)
    # 原 btns 声明重复了，删掉原来的那句
    old_dup = '''            LinearLayout btns = new LinearLayout(this);
            btns.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout btns = new LinearLayout(this);
            btns.setOrientation(LinearLayout.HORIZONTAL);'''
    new_dup = '''            LinearLayout btns = new LinearLayout(this);
            btns.setOrientation(LinearLayout.HORIZONTAL);'''
    if old_dup in d:
        d = d.replace(old_dup, new_dup, 1)
        print('去重 OK')
    io.open(P, 'w', encoding='utf-8').write(d)
    print('列表行改造 OK')
else:
    print('MISS')