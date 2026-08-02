package com.ge.bo.common.search;

import java.util.regex.Pattern;

/**
 * FO 통합검색(미디어/페이지) 네이티브 쿼리 공용 규칙 모음.
 *
 * <p>MediaSearchService 에서 먼저 확립된 규칙을 그대로 옮겨온 것이며, PageSearchService 와 공유한다.
 * 모든 메서드는 입력 문자열만으로 결과가 결정되는 순수 함수라, 이전(각 서비스 private 메서드)과
 * 생성되는 SQL 문자열/파라미터 값이 완전히 동일하다.</p>
 *
 * <p>규칙이 바뀌면 두 탭(Media/Pages)의 검색 결과가 함께 바뀐다는 점에 유의할 것.</p>
 */
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

    /**
     * 사용자 입력 키워드 → LIKE 부분일치 패턴({@code %...%}).
     * 와일드카드 이스케이프 순서는 반드시 {@code \} → {@code %} → {@code _} 다.
     * ({@code \} 를 나중에 하면 앞서 만든 이스케이프 문자가 이중 처리된다)
     * 이 값을 쓰는 SQL 은 반드시 {@code ILIKE :param ESCAPE '\'} 형태여야 한다.
     */
    public static String toLikePattern(String value) {
        return "%" + escapeLike(value) + "%";
    }

    /**
     * 사용자 입력 키워드 → HTML 엔티티 인코딩 후 LIKE 패턴(HTML 원문 컬럼 매칭 전용).
     *
     * <p>순서 주의: HTML 엔티티 인코딩을 "먼저" 하고 그 결과에 LIKE 이스케이프를 적용한다.
     * 엔티티가 만들어내는 문자(&amp; a m p ; 등)에는 LIKE 와일드카드({@code % _ \})가 없으므로 서로 오염되지 않는다.
     * 예) 입력 {@code &%} → 인코딩 {@code &amp;%} → LIKE 이스케이프 {@code &amp;\%}</p>
     */
    public static String toHtmlLikePattern(String value) {
        return toLikePattern(htmlEscape(value));
    }

    /**
     * 사용자 입력 키워드 → HTML 엔티티 인코딩(HTML 원문 컬럼 매칭 전용).
     * 실데이터 근거:
     *  - integration_contents.content 에 실제로 존재하는 엔티티는 {@code &amp;}(48건) / {@code &nbsp;}(31건) 뿐이고,
     *    태그를 제거한 "보이는 본문"에는 {@code " < >} 가 단 한 건도 없다 → 이 4개는 인코딩해야 매칭이 맞다.
     *  - 반면 작은따옴표는 {@code LS ELECTRIC's} 처럼 raw 로 저장되어 있고 {@code &#39;} 는 0건이므로
     *    의도적으로 인코딩하지 않는다(인코딩하면 "ELECTRIC's" 검색이 깨진다).
     * {@code &} 를 반드시 가장 먼저 치환해야 뒤에 생성되는 {@code &lt;} 등이 이중 인코딩되지 않는다.
     */
    public static String htmlEscape(String value) {
        return value
                .replace("&", "&amp;")   // 반드시 첫 번째
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    /**
     * 본문 HTML → 평문 텍스트 SQL 표현식(스니펫 "표시용" 전용).
     * FE 공용 유틸 stripHtmlText(fo/src/lib/stripHtmlText.ts) 와 동일한 순서/규칙을 SQL 로 옮긴 것이다.
     *  ① 태그 제거 : {@code <[^>]*>} → 공백 1칸. 빈 문자열이 아니라 공백으로 치환해야
     *                 {@code ...downtime.</p><p>When...} 같은 블록 경계에서 단어가 붙지 않는다.
     *  ② 엔티티 복원 : 실제 데이터에 존재하는 {@code &nbsp; / &#160; / &amp;} 만 처리한다.
     *                 {@code &lt; / &gt;} 는 복원하면 평문에 다시 꺾쇠 문자가 섞이므로 의도적으로 미복원(현 데이터에도 없음).
     *                 {@code &amp;} 는 반드시 마지막 — {@code &amp;nbsp;} 가 공백으로 이중 복원되는 것을 막는다.
     *  ③ 개행/연속 공백 1칸으로 정리 후 앞뒤 trim.
     * 이 표현식은 SELECT 절(스니펫)에만 쓴다. 검색(q) 매칭은 원본 컬럼 그대로 유지해야 하므로 WHERE 절에는 적용 금지.
     */
    public static String buildPlainTextExpr(String column) {
        String stripped = "regexp_replace(" + column + ", '<[^>]*>', ' ', 'g')";                 // ① 태그 → 공백
        String nbsp = "regexp_replace(" + stripped + ", '&nbsp;|&#160;', ' ', 'gi')";            // ② nbsp
        String amp = "regexp_replace(" + nbsp + ", '&amp;', '&', 'gi')";                         // ② amp(마지막)
        return "btrim(regexp_replace(" + amp + ", '\\s+', ' ', 'g'))";                           // ③ 공백 정리 + trim
    }
}
