package com.ge.bo.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ge.bo.dto.PageDataListResponse;
import com.ge.bo.dto.PageDataRequest;
import com.ge.bo.dto.PageDataResponse;
import com.ge.bo.entity.PageData;
import com.ge.bo.exception.ErrorCode;
import com.ge.bo.repository.PageDataRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.Objects;

/**
 * 페이지 데이터 비즈니스 로직
 * - 목록 조회: EntityManager 네이티브 쿼리로 동적 JSONB 검색
 * - 단건 CRUD: JPA Repository 사용
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PageDataService {

    private final PageDataRepository pageDataRepository;
    private final ObjectMapper objectMapper;
    private final PageFileService pageFileService;

    @PersistenceContext
    private EntityManager entityManager;

    /** 예약 파라미터 — 검색 조건에서 제외 */
    private static final Set<String> RESERVED_PARAMS = Set.of("page", "size", "sort");

    /**
     * 목록 조회 — 동적 JSONB 검색 + 페이지네이션
     *
     * @param slug         페이지 식별자
     * @param allParams    요청 Query Params 전체 (page/size 포함)
     * @param page         페이지 번호 (0-based)
     * @param size         페이지 크기
     */
    @Transactional(readOnly = true)
    public PageDataListResponse search(String slug, Map<String, String> allParams, int page, int size) {
        // 검색 조건 파라미터 추출 (예약어 제거 + 빈 값 제거)
        Map<String, String> searchParams = new LinkedHashMap<>();
        allParams.forEach((key, value) -> {
            if (!RESERVED_PARAMS.contains(key) && value != null && !value.isBlank()) {
                searchParams.put(key, value);
            }
        });

        // WHERE 절 동적 생성
        StringBuilder whereClause = new StringBuilder("WHERE template_slug = :slug");
        searchParams.forEach((key, value) -> {
            // SQL Injection 방지: 키는 영문자/숫자/언더스코어만 허용
            if (key.matches("[a-zA-Z0-9_]+")) {
                whereClause.append(" AND data_json->>'").append(key).append("' ILIKE :p_").append(key);
            }
        });

        // 전체 건수 조회
        String countSql = "SELECT COUNT(*) FROM page_data " + whereClause;
        Query countQuery = entityManager.createNativeQuery(countSql);
        countQuery.setParameter("slug", slug);
        searchParams.forEach((key, value) -> {
            if (key.matches("[a-zA-Z0-9_]+")) {
                countQuery.setParameter("p_" + key, "%" + value + "%");
            }
        });
        long totalElements = ((Number) countQuery.getSingleResult()).longValue();

        if (totalElements == 0) {
            return buildEmptyResponse(page, size);
        }

        // 정렬 조건 파싱 — "컬럼키,asc|desc" 형식, SQL Injection 방지
        String orderBy = " ORDER BY created_at DESC"; // 기본값
        String sortParam = allParams.get("sort");
        if (sortParam != null && !sortParam.isBlank()) {
            String[] parts = sortParam.split(",", 2);
            String sortCol = parts[0].trim();
            String sortDir = parts.length > 1 && "desc".equalsIgnoreCase(parts[1].trim()) ? "DESC" : "ASC";
            // 컬럼키 유효성 검증 (영문자/숫자/언더스코어만 허용)
            if (sortCol.matches("[a-zA-Z0-9_]+")) {
                orderBy = " ORDER BY data_json->>'" + sortCol + "' " + sortDir;
            }
        }

        // 데이터 조회
        String dataSql = "SELECT id, template_slug, data_json::text, created_by, created_at, updated_by, updated_at "
                + "FROM page_data " + whereClause
                + orderBy
                + " LIMIT :size OFFSET :offset";
        Query dataQuery = entityManager.createNativeQuery(dataSql);
        dataQuery.setParameter("slug", slug);
        dataQuery.setParameter("size", size);
        dataQuery.setParameter("offset", (long) page * size);
        searchParams.forEach((key, value) -> {
            if (key.matches("[a-zA-Z0-9_]+")) {
                dataQuery.setParameter("p_" + key, "%" + value + "%");
            }
        });

        @SuppressWarnings("unchecked")
        List<Object[]> rows = dataQuery.getResultList();
        List<PageDataResponse> content = rows.stream()
                .map(this::mapRowToResponse)
                .toList();

        int totalPages = (int) Math.ceil((double) totalElements / size);
        return PageDataListResponse.builder()
                .content(content)
                .totalElements(totalElements)
                .totalPages(totalPages)
                .page(page)
                .size(size)
                .build();
    }

    /**
     * 단건 조회
     *
     * @param slug 페이지 식별자
     * @param id   데이터 PK
     */
    @Transactional(readOnly = true)
    public PageDataResponse getById(String slug, Long id) {
        PageData pageData = pageDataRepository.findByIdAndTemplateSlug(id, slug)
                .orElseThrow(ErrorCode.PAGE_DATA_NOT_FOUND::toException);
        return PageDataResponse.from(pageData);
    }

    /**
     * 등록 — 네이티브 쿼리로 직접 INSERT하여 ::jsonb 캐스팅 적용
     *
     * @param slug    페이지 식별자
     * @param request 등록 요청 (dataJson Map)
     */
    @Transactional
    public PageDataResponse create(String slug, PageDataRequest request) {
        String dataJsonStr = serializeDataJson(request.getDataJson());
        // JPA save() 대신 네이티브 쿼리 사용: String → JSONB 타입 명시적 캐스팅
        Query insertQuery = entityManager.createNativeQuery(
                "INSERT INTO page_data (template_slug, data_json, created_at, updated_at) " +
                "VALUES (:slug, CAST(:dataJson AS jsonb), NOW(), NOW()) RETURNING id"
        );
        insertQuery.setParameter("slug", slug);
        insertQuery.setParameter("dataJson", dataJsonStr);
        Long newId = ((Number) insertQuery.getSingleResult()).longValue();
        return getById(slug, newId);
    }

    /**
     * 수정 — 네이티브 쿼리로 직접 UPDATE하여 ::jsonb 캐스팅 적용
     *
     * @param slug    페이지 식별자
     * @param id      데이터 PK
     * @param request 수정 요청 (dataJson Map)
     */
    @Transactional
    public PageDataResponse update(String slug, Long id, PageDataRequest request) {
        // 존재 여부 확인 (없으면 404)
        pageDataRepository.findByIdAndTemplateSlug(id, slug)
                .orElseThrow(ErrorCode.PAGE_DATA_NOT_FOUND::toException);
        String dataJsonStr = serializeDataJson(request.getDataJson());
        // JPA save() 대신 네이티브 쿼리 사용: String → JSONB 타입 명시적 캐스팅
        Query updateQuery = entityManager.createNativeQuery(
                "UPDATE page_data SET data_json = CAST(:dataJson AS jsonb), updated_at = NOW() " +
                "WHERE id = :id AND template_slug = :slug"
        );
        updateQuery.setParameter("dataJson", dataJsonStr);
        updateQuery.setParameter("id", id);
        updateQuery.setParameter("slug", slug);
        updateQuery.executeUpdate();
        return getById(slug, id);
    }

    /**
     * 삭제
     * page_data 삭제 전 연관 page_file(파일 + DB)을 먼저 정리
     *
     * @param slug 페이지 식별자
     * @param id   데이터 PK
     */
    @Transactional
    public void delete(String slug, Long id) {
        pageDataRepository.findByIdAndTemplateSlug(id, slug)
                .orElseThrow(ErrorCode.PAGE_DATA_NOT_FOUND::toException);

        // 연관 파일 일괄 삭제 (파일시스템 + DB)
        pageFileService.deleteByDataId(id);

        pageDataRepository.deleteByIdAndTemplateSlug(id, slug);
    }

    /**
     * 전체 데이터 조회 — LIMIT/OFFSET 없이 전체 조회 (엑셀 다운로드 전용)
     * 검색 조건은 search()와 동일하게 적용
     *
     * @param slug      페이지 식별자
     * @param allParams 검색 조건 (export/format/headers/keys 등 예약어는 제외됨)
     * @return 전체 데이터 목록 (Map<키, 값> 형태)
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> exportAll(String slug, Map<String, String> allParams) {
        // 예약 파라미터 확장 (export 전용 파라미터 추가)
        Set<String> reservedForExport = new HashSet<>(RESERVED_PARAMS);
        reservedForExport.addAll(Set.of("format", "headers", "keys"));

        // 검색 조건 파라미터 추출
        Map<String, String> searchParams = new LinkedHashMap<>();
        allParams.forEach((key, value) -> {
            if (!reservedForExport.contains(key) && value != null && !value.isBlank()) {
                searchParams.put(key, value);
            }
        });

        // WHERE 절 동적 생성 (search()와 동일 로직)
        StringBuilder whereClause = new StringBuilder("WHERE template_slug = :slug");
        searchParams.forEach((key, value) -> {
            if (key.matches("[a-zA-Z0-9_]+")) {
                whereClause.append(" AND data_json->>'").append(key).append("' ILIKE :p_").append(key);
            }
        });

        // LIMIT/OFFSET 없이 전체 조회
        String dataSql = "SELECT id, template_slug, data_json::text, created_by, created_at, updated_by, updated_at "
                + "FROM page_data " + whereClause
                + " ORDER BY created_at DESC";
        Query dataQuery = entityManager.createNativeQuery(dataSql);
        dataQuery.setParameter("slug", slug);
        searchParams.forEach((key, value) -> {
            if (key.matches("[a-zA-Z0-9_]+")) {
                dataQuery.setParameter("p_" + key, "%" + value + "%");
            }
        });

        @SuppressWarnings("unchecked")
        List<Object[]> rows = dataQuery.getResultList();

        // Map<키, 값> 형태로 변환 (ExcelService에서 keys 기준으로 값 추출)
        return rows.stream()
                .map(row -> {
                    Map<String, Object> dataMap = new LinkedHashMap<>();
                    try {
                        if (row[2] != null) {
                            dataMap = objectMapper.readValue(row[2].toString(),
                                    new com.fasterxml.jackson.core.type.TypeReference<>() {});
                        }
                    } catch (Exception e) {
                        log.warn("exportAll dataJson 파싱 실패: {}", e.getMessage());
                    }
                    return dataMap;
                })
                .toList();
    }

    // ── private 헬퍼 ──────────────────────────────────────────

    /** Map → JSON 문자열 직렬화 */
    private String serializeDataJson(Map<String, Object> dataMap) {
        try {
            return objectMapper.writeValueAsString(dataMap);
        } catch (Exception e) {
            log.error("dataJson 직렬화 실패: {}", e.getMessage());
            return "{}";
        }
    }

    /**
     * 네이티브 쿼리 결과 행(Object[]) → PageDataResponse 변환
     * 컬럼 순서: id, template_slug, data_json::text, created_by, created_at, updated_by, updated_at
     */
    private PageDataResponse mapRowToResponse(Object[] row) {
        Map<String, Object> dataMap = Collections.emptyMap();
        try {
            if (row[2] != null) {
                dataMap = objectMapper.readValue(row[2].toString(),
                        new com.fasterxml.jackson.core.type.TypeReference<>() {});
            }
        } catch (Exception e) {
            log.warn("dataJson 파싱 실패: {}", e.getMessage());
        }

        return PageDataResponse.builder()
                .id(((Number) row[0]).longValue())
                .templateSlug((String) row[1])
                .dataJson(dataMap)
                .createdBy((String) row[3])
                .createdAt(row[4] != null ? toLocalDateTime(row[4]) : null)
                .updatedBy((String) row[5])
                .updatedAt(row[6] != null ? toLocalDateTime(row[6]) : null)
                .build();
    }

    /**
     * 네이티브 쿼리 결과의 타임스탬프 변환
     * Hibernate 6에서 TIMESTAMP는 LocalDateTime 또는 java.sql.Timestamp로 반환될 수 있음
     */
    private java.time.LocalDateTime toLocalDateTime(Object obj) {
        if (obj instanceof java.time.LocalDateTime ldt) return ldt;
        if (obj instanceof java.sql.Timestamp ts) return ts.toLocalDateTime();
        return null;
    }

    /** 검색 결과 없을 때 빈 응답 생성 */
    private PageDataListResponse buildEmptyResponse(int page, int size) {
        return PageDataListResponse.builder()
                .content(Collections.emptyList())
                .totalElements(0)
                .totalPages(0)
                .page(page)
                .size(size)
                .build();
    }
}
