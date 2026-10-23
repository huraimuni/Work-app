package kr.todanjeon.workapp;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** 폰 안(앱 전용 폴더)에 백업·되돌리기용 파일을 보관한다. 자동으로 오래된 것부터 정리한다. */
final class Backups {
    static File dir(Context c) {
        File d = new File(c.getFilesDir(), "backups");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    /** 임시 파일에 먼저 쓰고 이름을 바꿔서, 쓰다가 끊겨도 기존 파일이 망가지지 않게 한다. */
    static File save(Context c, String name, String json) throws Exception {
        File out = new File(dir(c), name);
        File tmp = new File(dir(c), name + ".tmp");
        FileOutputStream fo = new FileOutputStream(tmp);
        try {
            fo.write(json.getBytes("UTF-8"));
            fo.getFD().sync();
        } finally {
            fo.close();
        }
        if (out.exists() && !out.delete()) throw new java.io.IOException("old file busy");
        if (!tmp.renameTo(out)) throw new java.io.IOException("rename failed");
        return out;
    }

    /** prefix 로 시작하는 .json 파일을 최신순으로. */
    static List<File> list(Context c, final String prefix) {
        File[] fs = dir(c).listFiles((d, n) -> n.startsWith(prefix) && n.endsWith(".json"));
        List<File> out = new ArrayList<>();
        if (fs != null) out.addAll(Arrays.asList(fs));
        Collections.sort(out, new Comparator<File>() {
            @Override
            public int compare(File a, File b) {
                return b.getName().compareTo(a.getName());
            }
        });
        return out;
    }

    static void prune(Context c, String prefix, int keep) {
        List<File> l = list(c, prefix);
        for (int i = keep; i < l.size(); i++) l.get(i).delete();
    }

    static String read(File f) throws Exception {
        InputStream in = new FileInputStream(f);
        try {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            return bo.toString("UTF-8");
        } finally {
            in.close();
        }
    }

    private Backups() {
    }
}
