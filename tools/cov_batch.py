# -*- coding: utf-8 -*-
# 批量解析列表补封面：collectBatchItem 时用 curCover；落盘也带一份
import io

P = '/storage/emulated/0/MT2/mcp/DYParse/java/com/re/douyinparse/MainActivity.java'
d = io.open(P, encoding='utf-8').read()
n = 0

# 1) collectBatchItem 补 curCover
old1 = '''            it.directUrl = curPlay == null ? "" : curPlay;
            boolean hasImgs = imgUrls != null && imgUrls.size() > 0;
            it.isImage = hasImgs;
            if(hasImgs) it.directUrl = imgUrls.get(0);'''
new1 = '''            it.directUrl = curPlay == null ? "" : curPlay;
            // 封面：单视频用 curCover；图集用第一张图本身当封面
            String cv = curCover == null ? "" : curCover;
            boolean hasImgs = imgUrls != null && imgUrls.size() > 0;
            if(cv.length() == 0 && hasImgs) cv = imgUrls.get(0);
            it.cover = cv;
            it.isImage = hasImgs;
            if(hasImgs) it.directUrl = imgUrls.get(0);'''
if old1 in d:
    d = d.replace(old1, new1, 1); n += 1; print('collectBatchItem 封面 OK')

# 2) saveBatchList 落盘加 cover（格式：title url directUrl isImage cover）
old2 = '''                sb.append(batchEsc(it.title)).append('\\t')
                  .append(batchEsc(it.url)).append('\\t')
                  .append(batchEsc(it.directUrl)).append('\\t')
                  .append(it.isImage ? "1" : "0").append('\\n');'''
new2 = '''                sb.append(batchEsc(it.title)).append('\\t')
                  .append(batchEsc(it.url)).append('\\t')
                  .append(batchEsc(it.directUrl)).append('\\t')
                  .append(it.isImage ? "1" : "0").append('\\t')
                  .append(batchEsc(it.cover == null ? "" : it.cover)).append('\\n');'''
if old2 in d:
    d = d.replace(old2, new2, 1); n += 1; print('saveBatchList 封面 OK')

# 3) loadBatchList 恢复 cover
old3 = '''                it.isImage = p.length > 3 && "1".equals(p[3]);
                tmp.add(it);'''
new3 = '''                it.isImage = p.length > 3 && "1".equals(p[3]);
                it.cover = p.length > 4 ? batchUnesc(p[4]) : "";
                tmp.add(it);'''
if old3 in d:
    d = d.replace(old3, new3, 1); n += 1; print('loadBatchList 封面 OK')

io.open(P, 'w', encoding='utf-8').write(d)
print('changed:', n)