package kr.todanjeon.workapp;

/** 한글 초성 검색 도우미. "ㅅㅇㅍ" 처럼 초성만 입력해도 "삼영푸드"를 찾는다. */
final class Kor {
    private static final char[] CHO = {'ㄱ', 'ㄲ', 'ㄴ', 'ㄷ', 'ㄸ', 'ㄹ', 'ㅁ', 'ㅂ', 'ㅃ', 'ㅅ', 'ㅆ', 'ㅇ', 'ㅈ', 'ㅉ', 'ㅊ', 'ㅋ', 'ㅌ', 'ㅍ', 'ㅎ'};

    static String cho(String s) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 0xAC00 && c <= 0xD7A3) b.append(CHO[(c - 0xAC00) / 588]);
            else b.append(c);
        }
        return b.toString();
    }

    private static boolean allJamo(String s) {
        if (s.isEmpty()) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!(c >= 0x3131 && c <= 0x314E)) return false;
        }
        return true;
    }

    /** 글자 일부 또는 초성이 들어 있으면 true. 빈 검색어는 모두 true. */
    static boolean match(String text, String q) {
        if (q == null) return true;
        q = q.trim().toLowerCase();
        if (q.isEmpty()) return true;
        String t = text == null ? "" : text.toLowerCase();
        if (t.contains(q)) return true;
        return allJamo(q) && cho(t).contains(q);
    }

    private Kor() {
    }
}
