package com.re.douyinparse;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * 给安装器提供 content:// URI。
 * Android 7+ 禁止用 file:// 分享文件，必须走 ContentProvider。
 *
 * 注意：provider 声明 exported="false" + grantUriPermissions="true"，
 *      由拉起方（本应用）通过 FLAG_GRANT_READ_URI_PERMISSION 临时授权给安装器。
 *      部分 ROM 还需要 ClipData 才会把 grant 真正传下去，调用方已处理。
 */
public class ApkProvider extends ContentProvider {

    public static final String AUTH = "com.re.douyinparse.apk";
    public static final String NAME = "update.apk";

    /** 下载目标：应用外部私有目录，无需存储权限，安装器可读 */
    public static File apkFile(Context ctx){
        File dir = ctx.getExternalFilesDir(null);
        if(dir == null) dir = ctx.getFilesDir();
        if(dir != null && !dir.exists()) dir.mkdirs();
        return new File(dir, NAME);
    }

    public static Uri apkUri(Context ctx){
        return Uri.parse("content://" + AUTH + "/" + NAME);
    }

    @Override public boolean onCreate(){ return true; }

    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        // 只允许读，且只允许 update.apk 这一条路径
        String last = uri != null ? uri.getLastPathSegment() : null;
        if(last == null || !NAME.equals(last)){
            throw new FileNotFoundException("仅支持 " + NAME + "，收到：" + uri);
        }
        File f = apkFile(getContext());
        if(f == null || !f.exists() || f.length() == 0){
            throw new FileNotFoundException("apk not found: " + (f == null ? "null" : f.getAbsolutePath()));
        }
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override public Cursor query(Uri uri, String[] projection, String sel, String[] selArgs, String sort){
        File f = apkFile(getContext());
        String[] cols = (projection != null && projection.length > 0)
                ? projection
                : new String[]{ OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE };
        MatrixCursor c = new MatrixCursor(cols, 1);
        Object[] row = new Object[cols.length];
        for(int i = 0; i < cols.length; i++){
            if(OpenableColumns.DISPLAY_NAME.equals(cols[i])) row[i] = NAME;
            else if(OpenableColumns.SIZE.equals(cols[i])) row[i] = (f != null && f.exists()) ? f.length() : 0L;
            else row[i] = null;
        }
        c.addRow(row);
        return c;
    }

    @Override public String getType(Uri uri){ return "application/vnd.android.package-archive"; }
    @Override public Uri insert(Uri uri, ContentValues v){ return null; }
    @Override public int delete(Uri uri, String s, String[] a){ return 0; }
    @Override public int update(Uri uri, ContentValues v, String s, String[] a){ return 0; }
}
