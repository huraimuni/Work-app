package kr.todanjeon.workapp;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.util.Base64;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/** 사진·파일 압축, base64 변환, 내려받은 파일 보관. */
final class Media {
    /** 문서 하나(1MiB)에 base64로 들어가야 하므로 원본 기준 이 크기까지만 올린다. */
    static final int MAX_BYTES = 700 * 1024;
    /** 파일 하나의 최대 크기(조각 여러 개로 나눠 올림)와 조각 크기. */
    static final int MAX_FILE = 3 * 1024 * 1024;
    static final int CHUNK = 600 * 1024;

    static Bitmap decode(ContentResolver cr, Uri uri, int maxSide) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            try (InputStream in = cr.openInputStream(uri)) {
                BitmapFactory.decodeStream(in, null, o);
            }
            int sample = 1;
            while (Math.max(o.outWidth, o.outHeight) / (sample * 2) >= maxSide) sample *= 2;
            BitmapFactory.Options o2 = new BitmapFactory.Options();
            o2.inSampleSize = sample;
            Bitmap b;
            try (InputStream in = cr.openInputStream(uri)) {
                b = BitmapFactory.decodeStream(in, null, o2);
            }
            if (b == null) return null;
            int rot = 0;
            try (InputStream in = cr.openInputStream(uri)) {
                android.media.ExifInterface ex = new android.media.ExifInterface(in);
                int ori = ex.getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1);
                if (ori == 6) rot = 90;
                else if (ori == 3) rot = 180;
                else if (ori == 8) rot = 270;
            } catch (Exception ignored) {
            }
            float sc = Math.min(1f, (float) maxSide / Math.max(b.getWidth(), b.getHeight()));
            if (rot != 0 || sc < 1f) {
                Matrix m = new Matrix();
                if (rot != 0) m.postRotate(rot);
                if (sc < 1f) m.postScale(sc, sc);
                b = Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), m, true);
            }
            return b;
        } catch (Exception e) {
            return null;
        }
    }

    /** 용량 한도 안에 들 때까지 화질·크기를 줄여 JPEG 로 만든다. */
    static byte[] jpeg(Bitmap b, int maxBytes) {
        Bitmap cur = b;
        for (int round = 0; round < 6; round++) {
            for (int q = 80; q >= 40; q -= 10) {
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                cur.compress(Bitmap.CompressFormat.JPEG, q, bo);
                if (bo.size() <= maxBytes) return bo.toByteArray();
            }
            cur = Bitmap.createScaledBitmap(cur, Math.max(64, cur.getWidth() * 3 / 4), Math.max(64, cur.getHeight() * 3 / 4), true);
        }
        return null;
    }

    /** 가운데를 정사각형으로 잘라 size 로 줄인다(프로필 사진용). */
    static Bitmap square(Bitmap b, int size) {
        int s = Math.min(b.getWidth(), b.getHeight());
        Bitmap c = Bitmap.createBitmap(b, (b.getWidth() - s) / 2, (b.getHeight() - s) / 2, s, s);
        return Bitmap.createScaledBitmap(c, size, size, true);
    }

    static String b64(byte[] d) {
        return Base64.encodeToString(d, Base64.NO_WRAP);
    }

    static byte[] unb64(String s) {
        return Base64.decode(s, Base64.NO_WRAP);
    }

    static String name(ContentResolver cr, Uri u) {
        try (Cursor c = cr.query(u, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (i >= 0) return c.getString(i);
            }
        } catch (Exception ignored) {
        }
        String p = u.getLastPathSegment();
        return p == null ? "파일" : p;
    }

    /** 파일 전체를 읽는다. 한도를 넘으면 null. */
    static byte[] readAll(ContentResolver cr, Uri u, int max) {
        try (InputStream in = cr.openInputStream(u)) {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) {
                bo.write(buf, 0, n);
                if (bo.size() > max) return null;
            }
            return bo.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }

    static File dir(Context c) {
        File d = new File(c.getCacheDir(), "shared");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    static File blobFile(Context c, String id) {
        return new File(dir(c), "b" + id);
    }

    static void write(File f, byte[] d) {
        try (FileOutputStream o = new FileOutputStream(f)) {
            o.write(d);
        } catch (Exception ignored) {
        }
    }

    private Media() {
    }
}
