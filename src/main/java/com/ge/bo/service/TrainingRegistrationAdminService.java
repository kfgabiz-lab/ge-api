package com.ge.bo.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ge.bo.dto.TrainingRegistrationDetailResponse;
import com.ge.bo.dto.TrainingRegistrationListResponse;
import com.ge.bo.dto.TrainingRegistrationPageResponse;
import com.ge.bo.entity.PageData;
import com.ge.bo.entity.TrainingRegistration;
import com.ge.bo.exception.ErrorCode;
import com.ge.bo.repository.PageDataRepository;
import com.ge.bo.repository.TrainingRegistrationRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
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
 * Training 신청 내역(관리자 조회) 비즈니스 로직
 * - training_registration(자체 컬럼) + page_data 2단계 조인(curriculum_id → currMgmt-data, session_id → currDtlMgmt-data)
 * - JSONB(data_json) 내부 경로를 조건으로 걸어야 해서 JPA Criteria(Specification) 대신 EntityManager 네이티브 쿼리 채택
 *   (FoTrainingService/PageDataService와 동일 패턴 재사용 — 설계서 7절 참고,
 *   docs/pages/training-registration/be_training-registration.md)
 * - 조회 전용(수정/삭제 없음, training_registration은 이력성 append-only 테이블)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrainingRegistrationAdminService {

    private final TrainingRegistrationRepository trainingRegistrationRepository;
    private final PageDataRepository pageDataRepository;
    private final ObjectMapper objectMapper;

    @PersistenceContext
    private EntityManager entityManager;

    /** 구분(training_schedule_type) 응답 고정값 — training_registration은 오직 "정기 Training" 플로우만 나타냄(설계서 4절) */
    private static final String TRAINING_SCHEDULE_TYPE_FIXED = "01";

    /** curriculum(코스) 조인 대상 slug */
    private static final String CURR_SLUG = "currMgmt-data";
    /** session(오퍼링) 조인 대상 slug */
    private static final String SESS_SLUG = "currDtlMgmt-data";
    /** 신청일시(timestamptz) dateRange CAST 바인딩용 포맷 — LocalDateTime.toString()의 초 단위 생략(예: "00:00") 문제 회피 */
    private static final DateTimeFormatter TIMESTAMPTZ_CAST_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    /* ══════════ 목록 조회 ══════════ */

    /**
     * 목록 조회 — 동적 필터 + 서버 페이징(네이티브 COUNT + DATA 쿼리)
     *
     * @param createdFrom          신청일시 dateRange 시작 (tr.created_at 기준)
     * @param createdTo            신청일시 dateRange 종료
     * @param trainingDateFrom     시작일 dateRange 시작 (session.curriculum_detail2.training_date_from 기준)
     * @param trainingDateTo       시작일 dateRange 종료
     * @param trainingDateToFrom   종료일 dateRange 시작 (session.curriculum_detail2.training_date_to 기준)
     * @param trainingDateToTo     종료일 dateRange 종료
     * @param trainingScheduleType 구분 — "01" 또는 미지정이면 조건 없음, 그 외 값이면 결과 0건(설계서 4절)
     * @param trainingType         교육방식 코드(CSV 부분일치)
     * @param trainingCourse       Training 코드(정확일치)
     * @param curriculum           커리큘럼 제목 키워드(부분일치, 대소문자 무시)
     * @param title                제목 키워드(부분일치, 대소문자 무시)
     * @param page                 페이지 번호(0-based)
     * @param size                 페이지 크기
     */
    @Transactional(readOnly = true)
    public TrainingRegistrationPageResponse getList(
            LocalDate createdFrom, LocalDate createdTo,
            LocalDate trainingDateFrom, LocalDate trainingDateTo,
            LocalDate trainingDateToFrom, LocalDate trainingDateToTo,
            String trainingScheduleType, String trainingType, String trainingCourse,
            String curriculum, String title, int page, int size) {

        // 구분(trainingScheduleType) — "01" 또는 미지정 이외 값이 오면 즉시 빈 결과(설계서 4절, 비정기 데이터가 이 테이블에 없으므로 정상)
        if (StringUtils.isNotBlank(trainingScheduleType)
                && !TRAINING_SCHEDULE_TYPE_FIXED.equals(trainingScheduleType.trim())) {
            return emptyPageResponse(page, size);
        }

        StringBuilder where = new StringBuilder(" WHERE 1=1");
        appendDateRange(where, "tr.created_at", createdFrom != null, createdTo != null, true);
        appendDateRange(where, "sess.data_json->'curriculum_detail2'->>'training_date_from'",
                trainingDateFrom != null, trainingDateTo != null, false);
        appendDateRange(where, "sess.data_json->'curriculum_detail2'->>'training_date_to'",
                trainingDateToFrom != null, trainingDateToTo != null, false);
        if (StringUtils.isNotBlank(trainingType)) {
            where.append(" AND sess.data_json->'curriculum_detail1'->>'training_type' LIKE :trainingType");
        }
        if (StringUtils.isNotBlank(trainingCourse)) {
            where.append(" AND curr.data_json->'curriculum'->>'training_course' = :trainingCourse");
        }
        if (StringUtils.isNotBlank(curriculum)) {
            where.append(" AND curr.data_json->'curriculum'->>'title' ILIKE :curriculum");
        }
        if (StringUtils.isNotBlank(title)) {
            where.append(" AND sess.data_json->'curriculum_detail2'->>'title' ILIKE :title");
        }

        String fromJoin = " FROM training_registration tr"
                + " LEFT JOIN page_data curr ON curr.id = tr.curriculum_id AND curr.data_slug = '" + CURR_SLUG + "'"
                + " LEFT JOIN page_data sess ON sess.id = tr.session_id AND sess.data_slug = '" + SESS_SLUG + "'";

        // COUNT
        String countSql = "SELECT COUNT(*)" + fromJoin + where;
        Query countQuery = entityManager.createNativeQuery(countSql);
        bindListFilters(countQuery, createdFrom, createdTo, trainingDateFrom, trainingDateTo,
                trainingDateToFrom, trainingDateToTo, trainingType, trainingCourse, curriculum, title);
        long totalElements = ((Number) countQuery.getSingleResult()).longValue();

        if (totalElements == 0) {
            return emptyPageResponse(page, size);
        }

        // DATA
        String dataSql = "SELECT"
                + " tr.id, tr.curriculum_id, tr.session_id, tr.student_name, tr.email,"
                + " tr.event_date, tr.created_at,"
                + " curr.data_json->'curriculum'->>'title'            AS curriculum_title,"
                + " curr.data_json->'curriculum'->>'training_course'   AS training_course,"
                + " sess.data_json->'curriculum_detail1'->>'training_type' AS training_type,"
                + " sess.data_json->'curriculum_detail2'->>'title'          AS session_title,"
                + " NULLIF(sess.data_json->'curriculum_detail2'->>'training_date_from', '')::date AS training_date_from,"
                + " NULLIF(sess.data_json->'curriculum_detail2'->>'training_date_to', '')::date   AS training_date_to"
                + fromJoin + where
                + " ORDER BY tr.created_at DESC LIMIT :size OFFSET :offset";
        Query dataQuery = entityManager.createNativeQuery(dataSql);
        bindListFilters(dataQuery, createdFrom, createdTo, trainingDateFrom, trainingDateTo,
                trainingDateToFrom, trainingDateToTo, trainingType, trainingCourse, curriculum, title);
        dataQuery.setParameter("size", size);
        dataQuery.setParameter("offset", (long) page * size);

        @SuppressWarnings("unchecked")
        List<Object[]> rows = dataQuery.getResultList();
        List<TrainingRegistrationListResponse> content = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            content.add(mapRowToListResponse(row));
        }

        int totalPages = size > 0 ? (int) Math.ceil((double) totalElements / size) : 0;
        return TrainingRegistrationPageResponse.builder()
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
     * dateRange WHERE 조건 추가
     * - 감사 컬럼(timestamptz): CAST(:param AS timestamptz) — PageDataService의 감사 컬럼 캐스팅 관례 재사용
     * - JSON 텍스트 경로: 캐스팅 없이 문자열 비교(ISO 'yyyy-MM-dd' 문자열은 사전순 비교가 시간순 비교와 일치)
     */
    private void appendDateRange(StringBuilder where, String column, boolean hasFrom, boolean hasTo, boolean auditColumn) {
        String paramFrom = paramNameFor(column) + "_from";
        String paramTo = paramNameFor(column) + "_to";
        if (hasFrom) {
            where.append(" AND ").append(column).append(auditColumn ? " >= CAST(:" + paramFrom + " AS timestamptz)" : " >= :" + paramFrom);
        }
        if (hasTo) {
            where.append(" AND ").append(column).append(auditColumn ? " <= CAST(:" + paramTo + " AS timestamptz)" : " <= :" + paramTo);
        }
    }

    /** SQL 컬럼/경로 표현식 → 파라미터명(영숫자만 남김) 변환 */
    private String paramNameFor(String column) {
        return "p_" + column.replaceAll("[^a-zA-Z0-9]", "_");
    }

    /** COUNT/DATA 쿼리 공용 필터 파라미터 바인딩 — WHERE 절 조립 조건과 반드시 동일하게 유지 */
    private void bindListFilters(Query query,
                                  LocalDate createdFrom, LocalDate createdTo,
                                  LocalDate trainingDateFrom, LocalDate trainingDateTo,
                                  LocalDate trainingDateToFrom, LocalDate trainingDateToTo,
                                  String trainingType, String trainingCourse,
                                  String curriculum, String title) {
        if (createdFrom != null) {
            query.setParameter(paramNameFor("tr.created_at") + "_from",
                    createdFrom.atStartOfDay().format(TIMESTAMPTZ_CAST_FORMAT));
        }
        if (createdTo != null) {
            query.setParameter(paramNameFor("tr.created_at") + "_to",
                    createdTo.atTime(23, 59, 59).format(TIMESTAMPTZ_CAST_FORMAT));
        }
        if (trainingDateFrom != null) {
            query.setParameter(paramNameFor("sess.data_json->'curriculum_detail2'->>'training_date_from'") + "_from",
                    trainingDateFrom.toString());
        }
        if (trainingDateTo != null) {
            query.setParameter(paramNameFor("sess.data_json->'curriculum_detail2'->>'training_date_from'") + "_to",
                    trainingDateTo.toString());
        }
        if (trainingDateToFrom != null) {
            query.setParameter(paramNameFor("sess.data_json->'curriculum_detail2'->>'training_date_to'") + "_from",
                    trainingDateToFrom.toString());
        }
        if (trainingDateToTo != null) {
            query.setParameter(paramNameFor("sess.data_json->'curriculum_detail2'->>'training_date_to'") + "_to",
                    trainingDateToTo.toString());
        }
        if (StringUtils.isNotBlank(trainingType)) {
            query.setParameter("trainingType", "%" + trainingType.trim() + "%");
        }
        if (StringUtils.isNotBlank(trainingCourse)) {
            query.setParameter("trainingCourse", trainingCourse.trim());
        }
        if (StringUtils.isNotBlank(curriculum)) {
            query.setParameter("curriculum", "%" + curriculum.trim() + "%");
        }
        if (StringUtils.isNotBlank(title)) {
            query.setParameter("title", "%" + title.trim() + "%");
        }
    }

    /** DATA 행 → TrainingRegistrationListResponse 매핑 (컬럼 순서는 getList() dataSql의 SELECT 목록과 1:1 대응) */
    private TrainingRegistrationListResponse mapRowToListResponse(Object[] row) {
        LocalDate eventDateFallback = toLocalDate(row[5]);
        LocalDate trainingDateFrom = toLocalDate(row[11]);
        if (trainingDateFrom == null) {
            // 세션 시작일 없으면 자체 컬럼(event_date) fallback (설계서 5절)
            trainingDateFrom = eventDateFallback;
        }
        return new TrainingRegistrationListResponse(
                row[0] != null ? ((Number) row[0]).longValue() : null,
                row[1] != null ? ((Number) row[1]).longValue() : null,
                row[2] != null ? ((Number) row[2]).longValue() : null,
                TRAINING_SCHEDULE_TYPE_FIXED,
                (String) row[9],
                (String) row[8],
                (String) row[7],
                (String) row[10],
                trainingDateFrom,
                toLocalDate(row[12]),
                toOffsetDateTime(row[6]),
                (String) row[4],
                (String) row[3]
        );
    }

    /** 빈 페이지 응답 (page/size는 요청값 유지) */
    private TrainingRegistrationPageResponse emptyPageResponse(int page, int size) {
        return TrainingRegistrationPageResponse.builder()
                .content(Collections.emptyList())
                .totalElements(0)
                .totalPages(0)
                .page(page)
                .size(size)
                .first(page == 0)
                .last(true)
                .build();
    }

    /* ══════════ 단건 조회 ══════════ */

    /**
     * 상세 조회 — training_registration 자체 컬럼 전체 + curriculum/session 조인 표시값 보강
     * 단건이라 목록의 네이티브 JOIN 없이 기존 리포지토리 메서드 재사용(설계서 9절)
     */
    @Transactional(readOnly = true)
    public TrainingRegistrationDetailResponse getOne(Long id) {
        TrainingRegistration tr = trainingRegistrationRepository.findById(id)
                .orElseThrow(ErrorCode.TRAINING_REGISTRATION_NOT_FOUND::toException);

        Map<String, Object> curriculumSection = findSection(tr.getCurriculumId(), "curriculum");
        Map<String, Object> detail1 = findSection(tr.getSessionId(), "curriculum_detail1");
        Map<String, Object> detail2 = findSection(tr.getSessionId(), "curriculum_detail2");

        String curriculumTitle = asString(curriculumSection.get("title"));
        String trainingCourse = asString(curriculumSection.get("training_course"));
        String trainingType = asString(detail1.get("training_type"));
        String sessionTitle = asString(detail2.get("title"));

        LocalDate trainingDateFrom = parseLocalDate(asString(detail2.get("training_date_from")));
        if (trainingDateFrom == null) {
            // 세션 시작일 없으면 자체 컬럼(event_date) fallback (설계서 5절)
            trainingDateFrom = tr.getEventDate();
        }
        LocalDate trainingDateTo = parseLocalDate(asString(detail2.get("training_date_to")));

        return new TrainingRegistrationDetailResponse(
                tr.getId(),
                tr.getCurriculumId(),
                tr.getSessionId(),
                TRAINING_SCHEDULE_TYPE_FIXED,
                trainingType,
                trainingCourse,
                curriculumTitle,
                sessionTitle,
                trainingDateFrom,
                trainingDateTo,
                tr.getCreatedAt(),
                tr.getEmail(),
                tr.getStudentName(),
                tr.getJobTitle(),
                tr.getPhone(),
                tr.getCompanyName(),
                tr.getEventDate(),
                tr.getStreetAddress(),
                tr.getAddress2(),
                tr.getApartment(),
                tr.getCity(),
                tr.getStateProvince(),
                tr.getZipCode(),
                tr.getTypeOfBusiness(),
                tr.getPrivacyConsentFlag(),
                tr.getCreatedIp()
        );
    }

    /** page_data(pageDataId)의 data_json을 파싱하여 지정 섹션 키(Map) 반환 — 없으면 빈 Map(조인 실패/파싱 실패 시에도 NPE 없이 안전 처리) */
    private Map<String, Object> findSection(Long pageDataId, String sectionKey) {
        if (pageDataId == null) {
            return Collections.emptyMap();
        }
        return pageDataRepository.findById(pageDataId)
                .map(PageData::getDataJson)
                .map(this::parseDataJson)
                .map(json -> asMap(json.get(sectionKey)))
                .orElse(Collections.emptyMap());
    }

    /** data_json(JSON 문자열) → Map 파싱 (실패 시 빈 Map, FoTrainingService.mapRowToResponse와 동일한 방어적 처리) */
    private Map<String, Object> parseDataJson(String dataJson) {
        try {
            if (dataJson == null) {
                return Collections.emptyMap();
            }
            return objectMapper.readValue(dataJson, new TypeReference<>() {
            });
        } catch (Exception e) {
            log.warn("training_registration 상세 조회 - page_data.data_json 파싱 실패: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    /** Object → Map 안전 캐스팅 (섹션이 없거나 예상과 다른 타입이면 빈 Map) */
    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object obj) {
        return obj instanceof Map ? (Map<String, Object>) obj : Collections.emptyMap();
    }

    /** Object → String 안전 변환 */
    private String asString(Object obj) {
        return obj != null ? obj.toString() : null;
    }

    /** "yyyy-MM-dd" 문자열 → LocalDate (형식 불량/빈 값이면 null, 예외 없이 통과) */
    private LocalDate parseLocalDate(String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (Exception e) {
            return null;
        }
    }

    /** 네이티브 쿼리 결과(date 계열 다양한 JDBC 타입) → LocalDate (PageDataService.toLocalDateTime과 동일한 방어적 변환 방식) */
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
