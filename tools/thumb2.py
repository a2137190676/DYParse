# -*- coding: utf-8 -*-
# 历史页行：加缩略图
import io

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/MainActivity.java'
d = io.open(P, encoding='utf-8').read()

old = '''            TextView name = new TextView(this);
            name.setText(f.getName());
            name.setTextSize(13);
            name.setTextColor(curDark ? 0xFFE8EAF0 : 0xFF1A1C22);
            name.setSingleLine(true);
            name.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
            LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            name.setLayoutParams(nlp);

            TextView meta = new TextView(this);
            meta.setText(fmt.format(new java.util.Date(f.lastModified()))
                    + "  " + InnerDownloader.sizeText(f.length()));
            meta.setTextSize(11);
            meta.setTextColor(curDark ? 0xFF8A8F9C : 0xFF9AA0AC);
            meta.setPadding(dp(8), 0, 0, 0);

            row.addView(name);
            row.addView(meta);'''

new = '''            // ---- 缩略图 ----
            final int fk = BtDownloader.fileKind(f.getName());
            android.widget.ImageView thumb = makeThumbView(dp(52));
            row.addView(thumb);
            if (fk == 0 || fk == 2) {
                loadThumb(thumb, f, dp(52), fk);
            } else {
                // 音频/文档/压缩包：中间放类型图标
                TextView ic = new TextView(this);
                ic.setText(BtDownloader.kindIcon(fk));
                ic.setTextSize(20);
                ic.setGravity(android.view.Gravity.CENTER);
                LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(52), dp(52));
                ilp.rightMargin = dp(10);
                ic.setLayoutParams(ilp);
                ic.setBackground(makeRoundRect(curDark ? 0xFF2C3038 : 0xFFEDF0F5, 0, dp(8)));
                row.removeView(thumb);
                row.addView(ic);
            }

            // ---- 右侧文字（文件名 + 类型/时间/大小）----
            LinearLayout txtWrap = new LinearLayout(this);
            txtWrap.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            txtWrap.setLayoutParams(tlp);

            TextView name = new TextView(this);
            name.setText(f.getName());
            name.setTextSize(13);
            name.setTextColor(curDark ? 0xFFE8EAF0 : 0xFF1A1C22);
            name.setSingleLine(true);
            name.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
            txtWrap.addView(name);

            TextView meta = new TextView(this);
            meta.setText(BtDownloader.kindName(fk) + " · "
                    + InnerDownloader.sizeText(f.length()) + " · "
                    + fmt.format(new java.util.Date(f.lastModified())));
            meta.setTextSize(11);
            meta.setTextColor(curDark ? 0xFF8A8F9C : 0xFF9AA0AC);
            meta.setPadding(0, dp(2), 0, 0);
            txtWrap.addView(meta);

            row.addView(txtWrap);'''

if old in d:
    d = d.replace(old, new, 1)
    io.open(P, 'w', encoding='utf-8').write(d)
    print('历史缩略图 OK')
else:
    print('MISS')