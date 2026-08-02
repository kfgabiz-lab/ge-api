package com.ge.bo.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ge.bo.common.excel.EntityExcelExportService;
import com.ge.bo.dto.TrainingRequestDetailResponse;
import com.ge.bo.dto.TrainingRequestResponse;
import com.ge.bo.entity.TrainingRequest;
import com.ge.bo.exception.ErrorCode;
import com.ge.bo.repository.TrainingRequestRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Training Request(비정기 교육 신청, 관리자 조회) 서비스
 * - JSONB(data_json) 내부 경로를 검색 조건/정렬 대상으로 써야 해서 JPA Criteria(Specification) 대신
 *   EntityManager 네이티브 쿼리 채택 (TrainingRegistrationAdminService와 동일 패턴 재사용)
 * - 응답 계약은 기존과 동일하게 Spring Page(PageImpl)로 유지 — FE(page.tsx)의 content/totalElements/totalPages 사용부 무변경
 * - 조회 전용(수정/삭제 없음, training_request는 이력성 append-only 테이블)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrainingRequestAdminService {

    private final TrainingRequestRepository trainingRequestRepository;
    private final ObjectMapper objectMapper;
    private final EntityExcelExportService entityExcelExportService;

    @PersistenceContext
    private EntityManager entityManager;

    /** 검색 기간 구분(SEARCHPERIODTYPE) 코드 — 접수일시(기본) */
    private static final String SEARCH_PERIOD_CREATED_AT = "01";
    /** 검색 기간 구분 — 희망 시작일(schedule_start) */
    private static final String SEARCH_PERIOD_SCHEDULE_START = "02";
    /** 검색 기간 구분 — 희망 종료일(schedule_end) */
    private static final String SEARCH_PERIOD_SCHEDULE_END = "03";

    /** 교육 형태(TRAININGTYPE) 코드 → training_request.training_format 실제 저장값 매핑 */
    private static final Map<String, String> TRAINING_FORMAT_CODE_MAP = Map.of(
            "001", "In-Person",
            "002", "Virtual");

    /** 분류(TRAININGSCHEDULETYPE) 코드 — 비정기 Training. training_request는 이 값만 존재(설계서 4절 대응) */
    private static final String TRAINING_SCHEDULE_TYPE_IRREGULAR = "02";

    private static final String FROM_JOIN = " FROM training_request tr";

    /**
     * 정렬 화이트리스트 — FE가 넘긴 sort 프로퍼티만 SQL 표현식으로 치환한다.
     * 목록에 없는 값은 무시(기본 정렬 적용)하여 네이티브 ORDER BY에 임의 문자열이 섞이는 것을 차단한다.
     */
    private static final Map<String, String> SORT_COLUMN_MAP = Map.ofEntries(
            Map.entry("id", "tr.id"),
            Map.entry("createdAt", "tr.created_at"),
            Map.entry("firstName", "tr.first_name"),
            Map.entry("lastName", "tr.last_name"),
            Map.entry("company", "tr.company"),
            Map.entry("email", "tr.email"),
            Map.entry("phone", "tr.phone"),
            Map.entry("trainingTrack", "tr.training_track"),
            Map.entry("trainingFormat", "tr.training_format"),
            Map.entry("scheduleStart", "tr.schedule_start"),
            Map.entry("scheduleEnd", "tr.schedule_end"),
            Map.entry("studentCount", "tr.student_count"));

    private static final String DEFAULT_ORDER_BY = " ORDER BY tr.created_at DESC";

    /** 접수일시(timestamptz) CAST 바인딩용 포맷 — 오프셋까지 그대로 보존해 FE가 보낸 +09:00 경계값을 왜곡하지 않는다 */
    private static final DateTimeFormatter TIMESTAMPTZ_CAST_FORMAT = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    /* ══════════ 목록 조회 ══════════ */

    /**
     * 동적 필터 + 서버 페이징 목록 조회(네이티브 COUNT + DATA 쿼리)
     *
     * @param trainingScheduleType 분류 코드(TRAININGSCHEDULETYPE: 01=정기/02=비정기). training_request는 비정기만
     *                              존재하므로 01이 오면 즉시 빈 결과(TrainingRegistrationAdminService의 역대응),
     *                              02 또는 미지정이면 조건 없음
     * @param trainingTrack    Training 트랙(training_track 정확일치: engineering/sales, null이면 전체)
     * @param trainingFormat   교육 형태 코드(TRAININGTYPE: 001/002, null이면 전체)
     * @param searchPeriodType 검색 기간 구분(SEARCHPERIODTYPE: 01=접수일시/02=희망시작일/03=희망종료일, 기본 01)
     * @param startDate        기간 시작 (null이면 전체)
     * @param endDate          기간 종료 (null이면 전체)
     * @param pageable         페이지 정보(정렬은 SORT_COLUMN_MAP 화이트리스트만 반영)
     */
    @Transactional(readOnly = true)
    public Page<TrainingRequestResponse> getList(String trainingScheduleType, String trainingTrack, String trainingFormat,
                                                  String searchPeriodType,
                                                  OffsetDateTime startDate, OffsetDateTime endDate,
                                                  Pageable pageable) {

        // 분류(trainingScheduleType) — "02" 또는 미지정 이외 값이면 즉시 빈 결과(정기 데이터가 이 테이블에 없으므로 정상)
        if (StringUtils.isNotBlank(trainingScheduleType)
                && !TRAINING_SCHEDULE_TYPE_IRREGULAR.equals(trainingScheduleType.trim())) {
            return new PageImpl<>(Collections.emptyList(), pageable, 0);
        }

        String periodType = StringUtils.defaultIfBlank(searchPeriodType, SEARCH_PERIOD_CREATED_AT).trim();
        boolean periodIsDate = SEARCH_PERIOD_SCHEDULE_START.equals(periodType)
                || SEARCH_PERIOD_SCHEDULE_END.equals(periodType);

        StringBuilder where = new StringBuilder(" WHERE 1=1");
        if (StringUtils.isNotBlank(trainingTrack)) {
            where.append(" AND tr.training_track = :trainingTrack");
        }
        if (StringUtils.isNotBlank(trainingFormat)) {
            where.append(" AND tr.training_format = :trainingFormat");
        }
        String periodColumn = periodColumnOf(periodType);
        if (startDate != null) {
            where.append(" AND ").append(periodColumn)
                    .append(periodIsDate ? " >= CAST(:periodFrom AS date)" : " >= CAST(:periodFrom AS timestamptz)");
        }
        if (endDate != null) {
            where.append(" AND ").append(periodColumn)
                    .append(periodIsDate ? " <= CAST(:periodTo AS date)" : " <= CAST(:periodTo AS timestamptz)");
        }

        String countSql = "SELECT COUNT(*)" + FROM_JOIN + where;
        Query countQuery = entityManager.createNativeQuery(countSql);
        bindListFilters(countQuery, trainingTrack, trainingFormat, periodIsDate, startDate, endDate);
        long totalElements = ((Number) countQuery.getSingleResult()).longValue();

        if (totalElements == 0) {
            return new PageImpl<>(Collections.emptyList(), pageable, 0);
        }

        String dataSql = "SELECT"
                + " tr.id, tr.training_track, tr.first_name, tr.last_name, tr.company, tr.email, tr.phone,"
                + " tr.training_format, tr.schedule_start, tr.schedule_end, tr.student_count, tr.created_at"
                + FROM_JOIN + where
                + buildOrderBy(pageable.getSort())
                + " LIMIT :size OFFSET :offset";
        Query dataQuery = entityManager.createNativeQuery(dataSql);
        bindListFilters(dataQuery, trainingTrack, trainingFormat, periodIsDate, startDate, endDate);
        dataQuery.setParameter("size", pageable.getPageSize());
        dataQuery.setParameter("offset", pageable.getOffset());

        @SuppressWarnings("unchecked")
        List<Object[]> rows = dataQuery.getResultList();
        List<TrainingRequestResponse> content = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            content.add(mapRowToListResponse(row));
        }
        return new PageImpl<>(content, pageable, totalElements);
    }

    /** 검색 기간 구분 코드 → 비교 대상 컬럼 */
    private String periodColumnOf(String periodType) {
        if (SEARCH_PERIOD_SCHEDULE_START.equals(periodType)) {
            return "tr.schedule_start";
        }
        if (SEARCH_PERIOD_SCHEDULE_END.equals(periodType)) {
            return "tr.schedule_end";
        }
        return "tr.created_at";
    }

    /** Pageable.Sort → 화이트리스트 기반 ORDER BY (매칭되는 항목이 없으면 기본 정렬) */
    private String buildOrderBy(Sort sort) {
        if (sort == null || sort.isUnsorted()) {
            return DEFAULT_ORDER_BY;
        }
        StringBuilder orderBy = new StringBuilder();
        for (Sort.Order order : sort) {
            String column = SORT_COLUMN_MAP.get(order.getProperty());
            if (column == null) {
                continue;
            }
            orderBy.append(orderBy.length() == 0 ? " ORDER BY " : ", ")
                    .append(column)
                    .append(order.isAscending() ? " ASC NULLS LAST" : " DESC NULLS LAST");
        }
        return orderBy.length() == 0 ? DEFAULT_ORDER_BY : orderBy.toString();
    }

    /** COUNT/DATA 쿼리 공용 필터 파라미터 바인딩 — WHERE 절 조립 조건과 반드시 동일하게 유지 */
    private void bindListFilters(Query query, String trainingTrack, String trainingFormat, boolean periodIsDate,
                                  OffsetDateTime startDate, OffsetDateTime endDate) {
        if (StringUtils.isNotBlank(trainingTrack)) {
            query.setParameter("trainingTrack", trainingTrack.trim());
        }
        if (StringUtils.isNotBlank(trainingFormat)) {
            String format = trainingFormat.trim();
            query.setParameter("trainingFormat", TRAINING_FORMAT_CODE_MAP.getOrDefault(format, format));
        }
        if (startDate != null) {
            query.setParameter("periodFrom", periodIsDate
                    ? startDate.toLocalDate().toString()
                    : startDate.format(TIMESTAMPTZ_CAST_FORMAT));
        }
        if (endDate != null) {
            query.setParameter("periodTo", periodIsDate
                    ? endDate.toLocalDate().toString()
                    : endDate.format(TIMESTAMPTZ_CAST_FORMAT));
        }
    }

    /** DATA 행 → TrainingRequestResponse 매핑 (컬럼 순서는 getList() dataSql의 SELECT 목록과 1:1 대응) */
    private TrainingRequestResponse mapRowToListResponse(Object[] row) {
        return new TrainingRequestResponse(
                row[0] != null ? ((Number) row[0]).longValue() : null,
                (String) row[1],
                (String) row[2],
                (String) row[3],
                (String) row[4],
                (String) row[5],
                (String) row[6],
                (String) row[7],
                toLocalDate(row[8]),
                toLocalDate(row[9]),
                (String) row[10],
                toOffsetDateTime(row[11]));
    }

    /* ══════════ 단건 조회 ══════════ */

    /**
     * 단건 상세 조회 — selectedProducts(jsonb) 파싱해 제품명 목록 보강
     * (단건이라 목록의 네이티브 JOIN 없이 리포지토리 조회 재사용 — TrainingRegistrationAdminService.getOne과 동일)
     */
    @Transactional(readOnly = true)
    public TrainingRequestDetailResponse getOne(Long id) {
        TrainingRequest entity = trainingRequestRepository.findById(id)
                .orElseThrow(ErrorCode.TRAINING_REQUEST_NOT_FOUND::toException);

        return TrainingRequestDetailResponse.from(entity,
                parseSelectedProductNames(entity.getSelectedProducts()));
    }

    /** selectedProducts(jsonb 문자열, [{id,name,type,groupId,groupTitle}]) → name 목록 (파싱 실패 시 빈 목록) */
    private List<String> parseSelectedProductNames(String selectedProductsJson) {
        if (StringUtils.isBlank(selectedProductsJson)) {
            return Collections.emptyList();
        }
        try {
            List<Map<String, Object>> items = objectMapper.readValue(
                    selectedProductsJson, new TypeReference<>() {
                    });
            List<String> names = new ArrayList<>(items.size());
            for (Map<String, Object> item : items) {
                Object name = item.get("name");
                if (name != null) {
                    names.add(name.toString());
                }
            }
            return names;
        } catch (Exception e) {
            log.warn("Training Request 상세 조회 - selectedProducts 파싱 실패: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /* ══════════ CSV 다운로드 ══════════ */

    /** 전체 데이터 CSV 다운로드 — 동적 필터/변환/이력 로깅은 공통 EntityExcelExportService에 위임(TestDataService와 동일 패턴) */
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> exportCsv(Map<String, String> allParams, String headers,
                                             String keys, String dateFormats, String codeMaps,
                                             String reason, HttpServletRequest request) {
        return entityExcelExportService.export(trainingRequestRepository, TrainingRequest.class, "training-request",
                allParams, headers, keys, dateFormats, codeMaps, reason, request);
    }

    /* ══════════ 네이티브 쿼리 결과 타입 변환 ══════════ */

    /** 네이티브 쿼리 결과(date 계열 다양한 JDBC 타입) → LocalDate */
    private LocalDate toLocalDate(Object obj) {
        if (obj == null) return null;
        if (obj instanceof LocalDate ld) return ld;
        if (obj instanceof Date d) return d.toLocalDate();
        if (obj instanceof Timestamp ts) return ts.toLocalDateTime().toLocalDate();
        if (obj instanceof LocalDateTime ldt) return ldt.toLocalDate();
        try {
            return LocalDate.parse(obj.toString());
        } catch (Exception e) {
            return null;
        }
    }

    /** 네이티브 쿼리 결과(timestamptz 계열 다양한 JDBC 타입) → OffsetDateTime */
    private OffsetDateTime toOffsetDateTime(Object obj) {
        if (obj == null) return null;
        if (obj instanceof OffsetDateTime odt) return odt;
        if (obj instanceof ZonedDateTime zdt) return zdt.toOffsetDateTime();
        if (obj instanceof Instant instant) return instant.atOffset(ZoneOffset.UTC);
        if (obj instanceof Timestamp ts) return ts.toInstant().atOffset(ZoneOffset.UTC);
        if (obj instanceof LocalDateTime ldt) return ldt.atOffset(ZoneOffset.UTC);
        try {
            return OffsetDateTime.parse(obj.toString());
        } catch (Exception e) {
            return null;
        }
    }
}
