package com.ge.bo.common.search;

import java.util.regex.Pattern;

public final class SearchSqlSupport {

    private static final Pattern REGEX_META = Pattern.compile("([\\\\.^$|?*+()\\[\\]{}])");

    private SearchSqlSupport() {
    }

    private static String escapeLike(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }

    public static String toLikeExactPattern(String value) {
        return escapeLike(value);
    }

    public static String toLikePrefixPattern(String value) {
        return escapeLike(value) + "%";
    }

    public static String toWordStartRegex(String value) {
        return "\\m" + REGEX_META.matcher(value).replaceAll("\\\\$1");
    }

    public static String toLikePattern(String value) {
        return "%" + escapeLike(value) + "%";
    }

    public static String toHtmlLikePattern(String value) {
        return toLikePattern(htmlEscape(value));
    }

    public static String htmlEscape(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    public static String buildPlainTextExpr(String column) {
        String stripped = "regexp_replace(" + column + ", '<[^>]*>', ' ', 'g')";
        String nbsp = "regexp_replace(" + stripped + ", '&nbsp;|&#160;', ' ', 'gi')";
        String amp = "regexp_replace(" + nbsp + ", '&amp;', '&', 'gi')";
        return "btrim(regexp_replace(" + amp + ", '\\s+', ' ', 'g'))";
    }

    public static String toRawLowerKeyword(String value) {
        return value.trim().toLowerCase();
    }

    /**
     * 등장 횟수(occurrence count) 계산식. :qRaw(소문자 원본 키워드, LIKE 이스케이프 안 됨)/:qLen 바인딩 전제.
     * expr에는 NULL이 올 수 있어 COALESCE로 감싼다.
     */
    public static String buildOccurrenceCountExpr(String expr) {
        String safe = "COALESCE(" + expr + ", '')";
        return "((length(lower(" + safe + ")) - length(replace(lower(" + safe + "), :qRaw, ''))) / :qLen)";
    }
}
