# -*- coding: utf-8 -*-
# 按行号改造解析列表行：加封面缩略图
import io

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/MainActivity.java'
lines = io.open(P, encoding='utf-8').read().split('\n')

# 定位：for(int i = from...) 之后 —— row 声明那几行
start = None
for k in range(14490, 14520):
    if 'LinearLayout row = new LinearLayout(this);' in lines[k]:
        start = k
        break
print('row 起始行:', start + 1 if start is not None else 'MISS')

if start is None:
    raise SystemExit

# 找 row.addView(title); 结束位置
end = None
for k in range(start, start + 40):
    if lines[k].strip() == 'row.addView(title);':
        end = k
        break
print('row.addView(title) 行:', end + 1 if end is not None else 'MISS')
if end is None:
    raise SystemExit

new_block = '''            // 外层：横向（左缩略图 + 右内容）
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

            // 左：封面缩略图（http 开头的 cover 走网络加载）
            String cov = it.cover;
            boolean hasCov = (cov != null && cov.length() > 8
                    && (cov.startsWith("http") || cov.startsWith("//")));
            if (hasCov) {
                android.widget.ImageView thumb = makeThumbView(dp(58));
                row.addView(thumb);
                loadNetThumb(thumb, cov, dp(58));
            } else {
                TextView ic = new TextView(this);
                ic.setText(it.isImage ? "\\uD83D\\uDDBC" : (it.durSec > 0 ? "\\uD83C\\uDFAC" : "\\uD83D\\uDCCE"));
                ic.setTextSize(22);
                ic.setGravity(android.view.Gravity.CENTER);
                LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(58), dp(58));
                ilp.rightMargin = dp(10);
                ic.setLayoutParams(ilp);
                ic.setBackground(makeRoundRect(curDark ? 0xFF2C3038 : 0xFFEDF0F5, 0, dp(8)));
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

            // 时长/类型
            StringBuilder sbMeta = new StringBuilder();
            if (it.durSec > 0) {
                long dsec = it.durSec;
                sbMeta.append(String.format(java.util.Locale.US, "%d:%02d", dsec / 60, dsec % 60));
            }
            if (it.isImage) {
                if (sbMeta.length() > 0) sbMeta.append(" \\u00B7 ");
                sbMeta.append("\\u56FE\\u96C6");
            }
            if (sbMeta.length() > 0) {
                TextView tmeta = new TextView(this);
                tmeta.setText(sbMeta.toString());
                tmeta.setTextSize(10);
                tmeta.setTextColor(curDark ? 0xFF8A8F9C : 0xFF9AA0AC);
                tmeta.setPadding(0, dp(2), 0, 0);
                rightWrap.addView(tmeta);
            }'''

# 替换 start..end（含）
before = lines[:start]
after = lines[end + 1:]
lines2 = before + new_block.split('\n') + after
io.open(P, 'w', encoding='utf-8').write('\n'.join(lines2))
print('行替换完成，新行数', len(lines2))

# 校验后续要把 btns 加到 rightWrap，而不是 row
d2 = io.open(P, encoding='utf-8').read()
i = d2.find('LinearLayout btns = new LinearLayout(this);', d2.find('rightWrap.addView(tmeta)'))
print('btns 位置:', d2[:i].count('\n') + 1 if i > 0 else 'MISS')