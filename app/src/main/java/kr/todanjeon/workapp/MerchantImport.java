package kr.todanjeon.workapp;

import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** 이카운트 등에서 내려받은 엑셀(.xlsx)·CSV 를 표(행/열 글자)로 읽고, 머리글로 가맹점 항목을 찾아낸다. */
final class MerchantImport {
    private MerchantImport() {
    }

    static List<List<String>> read(byte[] data, String fname) throws Exception {
        boolean zip = data.length > 3 && data[0] == 'P' && data[1] == 'K';
        if (zip) return readXlsx(data);
        if (data.length > 4 && (data[0] & 0xFF) == 0xD0 && (data[1] & 0xFF) == 0xCF) {
            throw new Exception("옛 엑셀(.xls) 형식이에요. 엑셀에서 '다른 이름으로 저장 → Excel 통합 문서(.xlsx)' 또는 CSV 로 저장해서 올려 주세요.");
        }
        return readCsv(decode(data));
    }

    private static String decode(byte[] b) {
        int off = 0;
        if (b.length >= 3 && (b[0] & 0xFF) == 0xEF && (b[1] & 0xFF) == 0xBB && (b[2] & 0xFF) == 0xBF) off = 3;
        try {
            return Charset.forName("UTF-8").newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(b, off, b.length - off)).toString();
        } catch (CharacterCodingException e) {
            return new String(b, Charset.forName("EUC-KR"));
        }
    }

    static List<List<String>> readCsv(String s) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean q = false;
        char sep = ',';
        String first = s.length() > 2000 ? s.substring(0, 2000) : s;
        int tabs = first.length() - first.replace("\t", "").length();
        int commas = first.length() - first.replace(",", "").length();
        if (tabs > commas) sep = '\t';
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (q) {
                if (c == '"') {
                    if (i + 1 < s.length() && s.charAt(i + 1) == '"') {
                        cur.append('"');
                        i++;
                    } else q = false;
                } else cur.append(c);
            } else if (c == '"') q = true;
            else if (c == sep) {
                row.add(cur.toString());
                cur.setLength(0);
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < s.length() && s.charAt(i + 1) == '\n') i++;
                row.add(cur.toString());
                cur.setLength(0);
                rows.add(row);
                row = new ArrayList<>();
            } else cur.append(c);
        }
        if (cur.length() > 0 || !row.isEmpty()) {
            row.add(cur.toString());
            rows.add(row);
        }
        return rows;
    }

    private static byte[] all(ZipInputStream z) throws Exception {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = z.read(buf)) > 0) o.write(buf, 0, n);
        return o.toByteArray();
    }

    private static List<List<String>> readXlsx(byte[] data) throws Exception {
        byte[] shared = null;
        Map<String, byte[]> sheets = new HashMap<>();
        ZipInputStream z = new ZipInputStream(new ByteArrayInputStream(data));
        ZipEntry e;
        while ((e = z.getNextEntry()) != null) {
            String n = e.getName();
            if (n.equals("xl/sharedStrings.xml")) shared = all(z);
            else if (n.startsWith("xl/worksheets/sheet") && n.endsWith(".xml")) sheets.put(n, all(z));
        }
        if (sheets.isEmpty()) throw new Exception("엑셀 안에서 시트를 찾지 못했어요.");
        List<String> names = new ArrayList<>(sheets.keySet());
        java.util.Collections.sort(names);
        List<String> ss = new ArrayList<>();
        if (shared != null) {
            XmlPullParser p = Xml.newPullParser();
            p.setInput(new ByteArrayInputStream(shared), "UTF-8");
            StringBuilder cur = null;
            boolean inT = false;
            for (int t = p.getEventType(); t != XmlPullParser.END_DOCUMENT; t = p.next()) {
                if (t == XmlPullParser.START_TAG) {
                    if (p.getName().equals("si")) cur = new StringBuilder();
                    else if (p.getName().equals("t")) inT = true;
                } else if (t == XmlPullParser.TEXT) {
                    if (inT && cur != null) cur.append(p.getText());
                } else if (t == XmlPullParser.END_TAG) {
                    if (p.getName().equals("t")) inT = false;
                    else if (p.getName().equals("si")) {
                        ss.add(cur == null ? "" : cur.toString());
                        cur = null;
                    }
                }
            }
        }
        List<List<String>> best = null;
        for (String sn : names) {
            List<List<String>> r = readSheet(sheets.get(sn), ss);
            if (best == null || r.size() > best.size()) best = r;
        }
        return best;
    }

    private static int colOf(String ref) {
        int c = 0;
        for (int i = 0; i < ref.length(); i++) {
            char ch = ref.charAt(i);
            if (ch >= 'A' && ch <= 'Z') c = c * 26 + (ch - 'A' + 1);
            else break;
        }
        return Math.max(0, c - 1);
    }

    private static List<List<String>> readSheet(byte[] xml, List<String> ss) throws Exception {
        List<List<String>> rows = new ArrayList<>();
        XmlPullParser p = Xml.newPullParser();
        p.setInput(new ByteArrayInputStream(xml), "UTF-8");
        List<String> row = null;
        int col = 0;
        String type = "";
        StringBuilder val = null;
        boolean inV = false, inT = false;
        for (int t = p.getEventType(); t != XmlPullParser.END_DOCUMENT; t = p.next()) {
            if (t == XmlPullParser.START_TAG) {
                String n = p.getName();
                if (n.equals("row")) row = new ArrayList<>();
                else if (n.equals("c")) {
                    String r = p.getAttributeValue(null, "r");
                    col = r == null ? (row == null ? 0 : row.size()) : colOf(r);
                    type = p.getAttributeValue(null, "t");
                    if (type == null) type = "";
                    val = new StringBuilder();
                } else if (n.equals("v")) inV = true;
                else if (n.equals("t")) inT = true;
            } else if (t == XmlPullParser.TEXT) {
                if ((inV || inT) && val != null) val.append(p.getText());
            } else if (t == XmlPullParser.END_TAG) {
                String n = p.getName();
                if (n.equals("v")) inV = false;
                else if (n.equals("t")) inT = false;
                else if (n.equals("c") && row != null && val != null) {
                    String v = val.toString();
                    if (type.equals("s")) {
                        try {
                            v = ss.get(Integer.parseInt(v.trim()));
                        } catch (Exception ex) {
                            v = "";
                        }
                    } else if (type.isEmpty() || type.equals("n")) {
                        if (v.contains("E") || v.contains("e")) {
                            try {
                                v = new BigDecimal(v).toPlainString();
                            } catch (Exception ignored) {
                            }
                        } else if (v.endsWith(".0")) v = v.substring(0, v.length() - 2);
                    }
                    while (row.size() < col) row.add("");
                    row.add(v);
                    val = null;
                } else if (n.equals("row") && row != null) {
                    rows.add(row);
                    row = null;
                }
            }
        }
        return rows;
    }

    // ---- 머리글 → 항목
    static String norm(String s) {
        return s == null ? "" : s.replaceAll("[\\s\\u00A0*()\\[\\]·._/-]", "").toLowerCase(java.util.Locale.KOREA);
    }

    private static String fieldOf(String h) {
        h = norm(h);
        if (h.isEmpty()) return null;
        if (h.contains("사업자") || h.equals("등록번호") || h.contains("bizno")) return "bizno";
        if (h.contains("거래처코드") || h.equals("코드") || h.contains("고객코드")) return "ecode";
        if (h.contains("거래처명") || h.contains("상호") || h.contains("가맹점명") || h.contains("가맹점") || h.contains("업체명") || h.contains("회사명") || h.equals("거래처") || h.equals("이름") || h.equals("고객명")) return "name";
        if (h.contains("대표")) return "owner";
        if (h.contains("핸드폰") || h.contains("휴대") || h.contains("모바일") || h.contains("mobile") || h.equals("hp")) return "mobile";
        if (h.contains("전화") || h.contains("연락처") || h.equals("tel")) return "phone";
        if (h.contains("주소")) return "addr";
        if (h.contains("등급")) return "grade";
        if (h.contains("업태")) return "biztype";
        if (h.contains("종목")) return "bizitem";
        if (h.contains("메모") || h.contains("비고")) return "memo";
        return null;
    }

    /** 표에서 머리글 줄을 찾는다. 돌려주는 값: [머리글 줄 번호, 항목별 열 번호 맵]. 못 찾으면 null. */
    static Object[] findHeader(List<List<String>> rows) {
        for (int i = 0; i < Math.min(rows.size(), 15); i++) {
            Map<String, Integer> m = new HashMap<>();
            List<String> r = rows.get(i);
            for (int c = 0; c < r.size(); c++) {
                String f = fieldOf(r.get(c));
                if (f != null && !m.containsKey(f)) m.put(f, c);
            }
            if (m.containsKey("name") && m.size() >= 2) return new Object[]{i, m};
        }
        return null;
    }

    static String cell(List<String> r, Map<String, Integer> m, String f) {
        Integer c = m.get(f);
        if (c == null || c >= r.size()) return "";
        String v = r.get(c);
        return v == null ? "" : v.trim();
    }

    static String bizFmt(String raw) {
        String d = raw.replaceAll("[^0-9]", "");
        if (d.length() == 10) return d.substring(0, 3) + "-" + d.substring(3, 5) + "-" + d.substring(5);
        return raw.trim();
    }

    static int gradeOf(String g) {
        g = g.trim().toUpperCase(java.util.Locale.KOREA);
        if (g.isEmpty()) return -1;
        if (g.startsWith("S")) return 0;
        if (g.startsWith("A")) return 1;
        if (g.startsWith("B")) return 2;
        if (g.startsWith("C")) return 3;
        if (g.contains("해지")) return 4;
        if (g.contains("신규")) return 5;
        if (g.contains("협력")) return 7;
        String d = g.replaceAll("[^0-9]", "");
        if (!d.isEmpty()) {
            int n = Integer.parseInt(d.substring(0, 1));
            if (n >= 0 && n <= 7 && n != 6) return n;
        }
        return -1;
    }
}
