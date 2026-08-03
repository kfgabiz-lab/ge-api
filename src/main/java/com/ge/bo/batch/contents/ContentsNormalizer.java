package com.ge.bo.batch.contents;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 콘텐츠 통합배치 공통 정제 유틸 — 매핑정의서 R-01(trim)/R-02(시간 통일) 등 공통 가공 규칙을 원천 공용으로 구현한다.
 * 원천 판단이 필요한 예외(격리 사유)는 여기서 던지지 않고 IllegalArgumentException만 던진다 — 배치ID/문서키 같은
 * 문맥 정보는 호출한 Converter가 알고 있으므로, 격리 처리(ContentsIngestException 변환)는 Converter 책임으로 둔다.
 */
public final class ContentsNormalizer {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final Set<String> TRUE_VALUES = Set.of("Y", "YES", "TRUE", "T", "1");
    private static final Set<String> FALSE_VALUES = Set.of("N", "NO", "FALSE", "F", "0");
    private static final DateTimeFormatter SSQ_DATETIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-M-d H:m[:s]");

    private ContentsNormalizer() {
    }

    /** 앞뒤 공백 제거, 빈 문자열은 null */
    public static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * Y/YES/TRUE/1 → true, N/NO/FALSE/0 → false (대소문자 무시). 알 수 없는 값은 무조건 false로 바꾸지 않고 예외.
     */
    public static boolean parseStrictBoolean(String raw) {
        String v = trimToNull(raw);
        if (v == null) {
            throw new IllegalArgumentException("boolean 값이 비어있음");
        }
        String upper = v.toUpperCase();
        if (TRUE_VALUES.contains(upper)) {
            return true;
        }
        if (FALSE_VALUES.contains(upper)) {
            return false;
        }
        throw new IllegalArgumentException("알 수 없는 boolean 원천값: '" + raw + "'");
    }

    /** 언어코드 정규화 — 공백 제거 + 대문자화 + 하위태그 제거('en-US' → 'EN') */
    public static String normalizeLang(String raw) {
        String v = trimToNull(raw);
        if (v == null) {
            return null;
        }
        int dash = v.indexOf('-');
        String primary = dash > 0 ? v.substring(0, dash) : v;
        return primary.toUpperCase();
    }

    /** 한국시각(시간대 없는 timestamp) → UTC 기준 OffsetDateTime 변환 */
    public static OffsetDateTime koreaTimeToUtc(LocalDateTime kstDateTime) {
        if (kstDateTime == null) {
            return null;
        }
        return kstDateTime.atZone(KST).toOffsetDateTime().withOffsetSameInstant(java.time.ZoneOffset.UTC);
    }

    /**
     * SSQ의 UTC 문자열 날짜(create_datetime/update_datetime 등)를 관대하게 파싱 — 이미 UTC라 시간대 변환은
     * 하지 않고 오프셋만 붙인다. 빈 문자열은 null, 파싱 실패는 예외(호출자가 격리 처리)
     */
    public static OffsetDateTime parseSsqUtcDateTime(String raw) {
        String v = trimToNull(raw);
        if (v == null) {
            return null;
        }
        try {
            return LocalDateTime.parse(v, SSQ_DATETIME_FORMAT).atOffset(ZoneOffset.UTC);
        } catch (java.time.format.DateTimeParseException e) {
            throw new IllegalArgumentException("SSQ 날짜 파싱 실패: '" + raw + "'", e);
        }
    }

    /** 파일명 폴백 체인 — 우선순위 순으로 첫 값이 있는 후보를 채택(모두 공백이면 null) */
    public static String firstNonBlank(String... candidates) {
        for (String candidate : candidates) {
            String v = trimToNull(candidate);
            if (v != null) {
                return v;
            }
        }
        return null;
    }

    /** 파일 경로의 마지막 세그먼트를 파일명으로 추출(쿼리스트링 제거 후) */
    public static String extractFileNameFromPath(String path) {
        String v = trimToNull(path);
        if (v == null) {
            return null;
        }
        String withoutQuery = v.split("\\?", 2)[0];
        int lastSlash = Math.max(withoutQuery.lastIndexOf('/'), withoutQuery.lastIndexOf('\\'));
        String name = lastSlash >= 0 ? withoutQuery.substring(lastSlash + 1) : withoutQuery;
        return trimToNull(name);
    }

    /** 파일명에서 소문자 확장자 추출 — 확장자가 없으면 null(리포트 대상, DB 잘림 없음) */
    public static String extractExtension(String fileName) {
        String v = trimToNull(fileName);
        if (v == null) {
            return null;
        }
        int dot = v.lastIndexOf('.');
        if (dot < 0 || dot == v.length() - 1) {
            return null;
        }
        return v.substring(dot + 1).toLowerCase();
    }

    // doc_type -> blob 저장 폴더명 — 챗봇/문서 다운로드 API가 공유하는 blob storage 경로 규칙(CTP/{폴더}/파일명).
    // O(OS/Firmware)는 더 이상 안 씀. 매핑에 없는 doc_type(Z 등 미확인 유형)은 하위 폴더 없이 CTP/ 바로 아래에 둔다.
    private static final Map<String, String> DOC_TYPE_BLOB_FOLDERS = Map.of(
        "C", "Catalog", "M", "Manual", "D", "CAD", "S", "Software", "V", "Video", "T", "Techdata", "R", "Certification");

    /**
     * blob storage 파일 서빙 경로 생성 — "CTP/{doc_type 폴더}/{파일명}" 형식.
     * doc_type이 폴더 매핑에 없으면(Z 등 미확인 유형) 하위 폴더 없이 "CTP/{파일명}"으로 만든다.
     */
    public static String buildBlobFilePath(String docType, String fileName) {
        return buildBlobFilePath(docType, null, fileName);
    }

    /**
     * blob storage 파일 서빙 경로 생성(문서별 하위 폴더 포함) — "CTP/{doc_type 폴더}/{folderKey}/{파일명}" 형식.
     * SSQ는 doc_id+version_id를 이어붙인 값을 folderKey로 넘겨 문서별 폴더를 구성한다(파일명 충돌 방지).
     * folderKey가 null이면 이 세그먼트는 생략한다(CATALOG/CERTI는 문서별 폴더 없음).
     */
    public static String buildBlobFilePath(String docType, String folderKey, String fileName) {
        String name = trimToNull(fileName);
        if (name == null) {
            return null;
        }
        String folder = DOC_TYPE_BLOB_FOLDERS.get(docType);
        String key = trimToNull(folderKey);
        StringBuilder path = new StringBuilder("CTP/");
        if (folder != null) {
            path.append(folder).append('/');
        }
        if (key != null) {
            path.append(key).append('/');
        }
        return path.append(name).toString();
    }

    /** 카테고리 레벨 값들을 trim→빈 레벨 제외→구분자로 연결해 source_path 생성 */
    public static String buildCategoryPath(String delimiter, String... levels) {
        List<String> parts = new ArrayList<>();
        for (String level : levels) {
            String v = trimToNull(level);
            if (v != null) {
                parts.add(v);
            }
        }
        return parts.isEmpty() ? null : String.join(delimiter, parts);
    }
}
