package com.ge.bo.sso;

import java.nio.charset.StandardCharsets;

public class SsoCryptoUtil {

    private SsoCryptoUtil() {}

    /** encPWD 생성 — 아이디 길이 기반 prefix/suffix 가공 */
    public static String makeEncPwd(String id, String pwd) {
        int len = id.length();
        char c1 = (char) ((len * 9 % 26) + 97);
        char c2 = (char) ((len * 111 % 26) + 97);
        char c3 = (char) ((len * 1111 % 26) + 97);
        int n1 = (len * 11) % 9;
        int n2 = (len * 111) % 9;
        return "" + c1 + c2 + c3 + pwd + n1 + n2;
    }

    /**
     * FN_UrlEncode 동일 로직 (SQL 함수 이식)
     * ASCII [A-Za-z0-9()'*+,-._ !] 는 그대로, 나머지는 UTF-8 %XX 인코딩
     */
    public static String urlEncode(String str) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                    || c == '(' || c == ')' || c == '\''
                    || (c >= '*' && c <= '.') // * + , - .
                    || c == '_' || c == '!' || c == ' ') {
                sb.append(c);
            } else {
                byte[] bytes = String.valueOf(c).getBytes(StandardCharsets.UTF_8);
                for (byte b : bytes) {
                    sb.append(String.format("%%%02X", b & 0xFF));
                }
            }
        }
        return sb.toString();
    }
}
