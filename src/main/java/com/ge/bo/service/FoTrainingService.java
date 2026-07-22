package com.ge.bo.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ge.bo.dto.PageDataListResponse;
import com.ge.bo.dto.PageDataResponse;
import com.ge.bo.exception.BusinessException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * FO 공개 커리큘럼(교육) 조회 비즈니스 로직
 * - 카테고리(category-data id) 기준으로 연결된 공개 커리큘럼(currMgmt-data) 목록을 2-HOP 네이티브 조회로 반환
 *   HOP-1) currDtlMgmt-data 에서 해당 카테고리(power_list/automation_list)에 매칭되는 curriculum_id 수집
 *   HOP-2) 수집한 id 로 currMgmt-data 를 is_visible=001 필터 + 페이징 조회
 * - 응답 구조는 기존 currMgmt-data 목록 조회(PageDataService)와 완전히 동일한 PageDataListResponse
 * - createdBy/updatedBy 는 PageDataService.findPublicDetail 과 동일하게 user-name 치환 없이 원본 id 문자열 그대로
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FoTrainingService {

    private final ObjectMapper objectMapper;

    @PersistenceContext
    private EntityManager entityManager;

    /** 커리큘럼 상세(카테고리 매핑) 조회 slug */
    private static final String CURR_DTL_SLUG = "currDtlMgmt-data";
    /** 커리큘럼 본문 조회 slug */
    private static final String CURR_SLUG = "currMgmt-data";
    /** 노출 상태 코드 (고정 리터럴) */
    private static final String VISIBLE_CODE = "001";
    /** trainingCourse 허용 화이트리스트 */
    private static final Set<String> TRAINING_COURSE_WHITELIST = Set.of("01", "02", "03");

    /**
     * 카테고리별 커리큘럼 목록 조회
     * @param categoryIdsRaw 콤마구분 category-data id 목록 (필수, 예: "1731,1732,1745")
     *                       - 상위 묶음(depth1/depth2)에 속한 리프(depth3, 제품연결) id 여러 개를 한꺼번에 매칭하기 위함
     *                       - 비숫자 값이 섞이면 400, 유효 id 가 하나도 없으면 400
     * @param trainingCourse 교육과정 코드(01/02/03, 선택) — blank 면 조건 생략, 화이트리스트 미포함이면 400
     * @param page          0-based 페이지 번호
     * @param size          페이지 크기
     * @param siteId        사이트 스코프 (null 이면 사이트 필터 미적용)
     * @return PageDataListResponse (currMgmt-data 목록 조회와 동일한 응답 구조)
     */
    @Transactional(readOnly = true)
    public PageDataListResponse findCurriculumByCategory(
            String categoryIdsRaw, String trainingCourse, int page, int size, Long siteId) {

        // categoryIds 파싱/검증 — 콤마구분 문자열 → List<Long> (비숫자 섞이면 400, 유효 id 0개면 400)
        List<Long> categoryIds = parseCategoryIds(categoryIdsRaw);

        // trainingCourse 검증 — blank 는 조건 생략, 값이 있으면 화이트리스트만 허용
        String course = (trainingCourse != null && !trainingCourse.isBlank())
                ? trainingCourse.trim() : null;
        if (course != null && !TRAINING_COURSE_WHITELIST.contains(course)) {
            throw BusinessException.badRequest("유효하지 않은 trainingCourse 값입니다.");
        }

        // ── HOP-1: currDtlMgmt-data 에서 카테고리 매칭 curriculum_id 수집 ──────────
        List<Long> curriculumIds = collectCurriculumIds(categoryIds, siteId);
        if (curriculumIds.isEmpty()) {
            // 매칭되는 커리큘럼이 없으면 즉시 빈 결과 반환 (page/size 는 요청값 유지)
            return emptyResult(page, size);
        }

        // ── HOP-2: currMgmt-data 최종 조회 + 페이징 ────────────────────────────
        // COUNT
        StringBuilder countSql = new StringBuilder(
                "SELECT COUNT(*) FROM page_data"
                        + " WHERE data_slug = :slug AND id IN (:curriculumIds)"
                        + " AND data_json->'curriculum'->>'is_visible' = '" + VISIBLE_CODE + "'");
        if (course != null) {
            countSql.append(" AND data_json->'curriculum'->>'training_course' = :trainingCourse");
        }
        if (siteId != null) {
            countSql.append(" AND (site_id = :siteId OR site_id IS NULL)");
        }
        Query countQuery = entityManager.createNativeQuery(countSql.toString());
        countQuery.setParameter("slug", CURR_SLUG);
        countQuery.setParameter("curriculumIds", curriculumIds);
        if (course != null) {
            countQuery.setParameter("trainingCourse", course);
        }
        if (siteId != null) {
            countQuery.setParameter("siteId", siteId);
        }
        long totalElements = ((Number) countQuery.getSingleResult()).longValue();

        List<PageDataResponse> content;
        if (totalElements == 0) {
            content = Collections.emptyList();
        } else {
            // DATA
            StringBuilder dataSql = new StringBuilder(
                    "SELECT id, template_slug, data_json::text, group_id,"
                            + " created_by, created_at, updated_by, updated_at"
                            + " FROM page_data"
                            + " WHERE data_slug = :slug AND id IN (:curriculumIds)"
                            + " AND data_json->'curriculum'->>'is_visible' = '" + VISIBLE_CODE + "'");
            if (course != null) {
                dataSql.append(" AND data_json->'curriculum'->>'training_course' = :trainingCourse");
            }
            if (siteId != null) {
                dataSql.append(" AND (site_id = :siteId OR site_id IS NULL)");
            }
            dataSql.append(" ORDER BY created_at DESC LIMIT :size OFFSET :offset");

            Query dataQuery = entityManager.createNativeQuery(dataSql.toString());
            dataQuery.setParameter("slug", CURR_SLUG);
            dataQuery.setParameter("curriculumIds", curriculumIds);
            if (course != null) {
                dataQuery.setParameter("trainingCourse", course);
            }
            if (siteId != null) {
                dataQuery.setParameter("siteId", siteId);
            }
            dataQuery.setParameter("size", size);
            dataQuery.setParameter("offset", (long) page * size);

            @SuppressWarnings("unchecked")
            List<Object[]> rows = dataQuery.getResultList();
            content = new ArrayList<>(rows.size());
            for (Object[] row : rows) {
                content.add(mapRowToResponse(row));
            }
        }

        int totalPages = size > 0 ? (int) Math.ceil((double) totalElements / size) : 0;
        return PageDataListResponse.builder()
                .content(content)
                .totalElements(totalElements)
                .totalPages(totalPages)
                .page(page)
                .size(size)
                .first(page == 0)
                .last(page >= totalPages - 1)
                .build();
    }

    /**
     * 콤마구분 categoryIds 문자열 → List<Long> 파싱/검증
     * - null/blank → 400 (최소 1개 필수)
     * - 각 토큰 trim, 빈 토큰(연속 콤마 등)은 무시
     * - 비숫자 토큰이 섞이면 400
     * - 유효 id 가 하나도 없으면 400
     */
    private List<Long> parseCategoryIds(String raw) {
        if (raw == null || raw.isBlank()) {
            throw BusinessException.badRequest("categoryIds 는 최소 1개 이상 필요합니다.");
        }
        List<Long> ids = new ArrayList<>();
        for (String token : raw.split(",")) {
            String t = token.trim();
            if (t.isEmpty()) continue; // 연속 콤마/후행 콤마로 인한 빈 토큰은 무시
            try {
                ids.add(Long.parseLong(t));
            } catch (NumberFormatException e) {
                throw BusinessException.badRequest("categoryIds 에 숫자가 아닌 값이 포함되어 있습니다: " + t);
            }
        }
        if (ids.isEmpty()) {
            throw BusinessException.badRequest("categoryIds 는 최소 1개 이상 필요합니다.");
        }
        return ids;
    }

    /**
     * HOP-1) currDtlMgmt-data 에서 카테고리(power_list/automation_list)에 매칭되는 curriculum_id 수집
     * - 주어진 categoryIds 목록 중 하나라도 power_list/automation_list 에 포함되면 매칭
     *   (상위 묶음에 속한 리프 id 여러 개를 한꺼번에 OR 매칭)
     * - jsonb_array_elements_text 로 배열 원소를 펼쳐 bigint 비교 (Hibernate '?' 파서 충돌 없는 방식)
     * - id 목록 바인딩은 HOP-2(id IN (:curriculumIds))와 동일한 List<Long> + IN 관례 사용
     * - curriculum_id 는 정규식(^[0-9]+$) 통과분만 Long 파싱
     */
    private List<Long> collectCurriculumIds(List<Long> categoryIds, Long siteId) {
        StringBuilder sql = new StringBuilder(
                "SELECT DISTINCT (data_json->'curriculum_detail1'->>'curriculum_id')"
                        + " FROM page_data"
                        + " WHERE data_slug = :slug"
                        + " AND ("
                        + "   EXISTS (SELECT 1 FROM jsonb_array_elements_text(data_json->'power_list') v"
                        + "           WHERE v ~ '^[0-9]+$' AND v::bigint IN (:catIds))"
                        + "   OR EXISTS (SELECT 1 FROM jsonb_array_elements_text(data_json->'automation_list') v"
                        + "           WHERE v ~ '^[0-9]+$' AND v::bigint IN (:catIds))"
                        + " )"
                        + " AND (data_json->'curriculum_detail1'->>'curriculum_id') ~ '^[0-9]+$'");
        if (siteId != null) {
            sql.append(" AND (site_id = :siteId OR site_id IS NULL)");
        }
        Query query = entityManager.createNativeQuery(sql.toString());
        query.setParameter("slug", CURR_DTL_SLUG);
        query.setParameter("catIds", categoryIds);
        if (siteId != null) {
            query.setParameter("siteId", siteId);
        }

        @SuppressWarnings("unchecked")
        List<Object> rows = query.getResultList();
        List<Long> ids = new ArrayList<>(rows.size());
        for (Object row : rows) {
            if (row == null) continue;
            try {
                ids.add(Long.parseLong(row.toString().trim()));
            } catch (NumberFormatException ignored) {
                // 정규식 통과분만 넘어오므로 정상적으로는 발생하지 않음
            }
        }
        return ids;
    }

    /**
     * DATA row → PageDataResponse 매핑
     * PageDataService.mapRowToResponse 가 private 이라 재사용 불가하여 동일 로직을 소형 메서드로 복제
     * (createdBy/updatedBy 는 user-name 치환 없이 원본 id 문자열 그대로 — findPublicDetail 과 동일 방식)
     */
    private PageDataResponse mapRowToResponse(Object[] row) {
        java.util.Map<String, Object> dataMap = Collections.emptyMap();
        try {
            if (row[2] != null) {
                dataMap = objectMapper.readValue(row[2].toString(),
                        new com.fasterxml.jackson.core.type.TypeReference<>() {
                        });
            }
        } catch (Exception e) {
            log.warn("currMgmt dataJson 파싱 실패: {}", e.getMessage());
        }
        return PageDataResponse.builder()
                .id(((Number) row[0]).longValue())
                .templateSlug((String) row[1])
                .dataJson(dataMap)
                .groupId((String) row[3])
                .createdBy((String) row[4])
                .createdAt(row[5] != null ? toOffsetDateTime(row[5]) : null)
                .updatedBy((String) row[6])
                .updatedAt(row[7] != null ? toOffsetDateTime(row[7]) : null)
                .build();
    }

    /** 다양한 시간 타입 → OffsetDateTime (PageDataService.toOffsetDateTime 동일 로직) */
    private java.time.OffsetDateTime toOffsetDateTime(Object obj) {
        if (obj == null) return null;
        if (obj instanceof java.time.OffsetDateTime odt) return odt;
        if (obj instanceof java.time.Instant instant) return instant.atOffset(java.time.ZoneOffset.UTC);
        if (obj instanceof java.time.ZonedDateTime zdt) return zdt.toOffsetDateTime();
        if (obj instanceof java.sql.Timestamp ts) return ts.toInstant().atOffset(java.time.ZoneOffset.UTC);
        if (obj instanceof java.time.LocalDateTime ldt) return ldt.atOffset(java.time.ZoneOffset.UTC);
        try {
            return java.time.OffsetDateTime.parse(obj.toString());
        } catch (Exception ignored) {
            return null;
        }
    }

    /** 빈 결과 (page/size 는 요청값 유지) */
    private PageDataListResponse emptyResult(int page, int size) {
        return PageDataListResponse.builder()
                .content(Collections.emptyList())
                .totalElements(0)
                .totalPages(0)
                .page(page)
                .size(size)
                .first(page == 0)
                .last(true)
                .build();
    }
}
