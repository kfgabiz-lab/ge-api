package com.ge.bo.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ge.bo.common.excel.ExcelService;
import com.ge.bo.common.util.ClientIpUtils;
import com.ge.bo.dto.TrainingApplicationResponse;
import com.ge.bo.dto.TrainingApplicationSummaryResponse;
import com.ge.bo.repository.AdminRepository;
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
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class TrainingApplicationService {

    @PersistenceContext
    private EntityManager entityManager;

    private final ExcelService excelService;
    private final DownloadLogService downloadLogService;
    private final AdminRepository adminRepository;
    private final ObjectMapper objectMapper;

    private static final String TRAINING_SCHEDULE_TYPE_REGULAR = "01";
    private static final String TRAINING_SCHEDULE_TYPE_IRREGULAR = "02";

    private static final String SEARCH_PERIOD_CREATED_AT = "01";
    private static final String SEARCH_PERIOD_START = "02";
    private static final String SEARCH_PERIOD_END = "03";

    private static final Map<String, String> TRAINING_COURSE_CODE_TO_TRACK = Map.of(
            "01", "engineering",
            "02", "service",
            "03", "sales");

    private static final Map<String, String> TRAINING_TYPE_CODE_TO_FORMAT = Map.of(
            "001", "In-Person",
            "002", "Virtual");

    private static final String CURR_SLUG = "currMgmt-data";
    private static final String SESS_SLUG = "currDtlMgmt-data";

    private static final String CURR_TITLE_EXPR = "curr.data_json->'curriculum'->>'title'";
    private static final String SESS_TITLE_EXPR = "sess.data_json->'curriculum_detail2'->>'title'";

    private static final String REGULAR_TRAINING_COURSE_EXPR = "curr.data_json->'curriculum'->>'training_course'";
    private static final String REGULAR_TRAINING_TYPE_EXPR = "sess.data_json->'curriculum_detail1'->>'training_type'";
    private static final String REGULAR_DATE_FROM_EXPR =
            "COALESCE(NULLIF(sess.data_json->'curriculum_detail2'->>'training_date_from', '')::date, tr.event_date)";
    private static final String REGULAR_DATE_TO_EXPR =
            "NULLIF(sess.data_json->'curriculum_detail2'->>'training_date_to', '')::date";

    private static final String IRREGULAR_TRAINING_COURSE_EXPR =
            "CASE tr.training_track WHEN 'engineering' THEN '01' WHEN 'service' THEN '02' WHEN 'sales' THEN '03' ELSE tr.training_track END";
    private static final String IRREGULAR_TRAINING_TYPE_EXPR =
            "CASE tr.training_format WHEN 'In-Person' THEN '001' WHEN 'Virtual' THEN '002' ELSE tr.training_format END";

    private static final String FROM_JOIN_REGULAR = " FROM training_registration tr"
            + " LEFT JOIN page_data curr ON curr.id = tr.curriculum_id AND curr.data_slug = '" + CURR_SLUG + "' AND curr.is_deleted = false"
            + " LEFT JOIN page_data sess ON sess.id = tr.session_id AND sess.data_slug = '" + SESS_SLUG + "' AND sess.is_deleted = false";

    /** 비정기(training_request)는 관련 커리큘럼/세션 개념이 없어 JOIN하지 않는다 — IRREGULAR_SELECT_COLUMNS에서 NULL로 채움 */
    private static final String FROM_JOIN_IRREGULAR = " FROM training_request tr";

    private static final String REGULAR_SELECT_COLUMNS = "'" + TRAINING_SCHEDULE_TYPE_REGULAR + "' AS schedule_type,"
            + " tr.id AS id,"
            + " " + REGULAR_TRAINING_COURSE_EXPR + " AS training_course,"
            + " " + REGULAR_TRAINING_TYPE_EXPR + " AS training_type,"
            + " " + CURR_TITLE_EXPR + " AS curriculum_title,"
            + " " + SESS_TITLE_EXPR + " AS session_title,"
            + " " + REGULAR_DATE_FROM_EXPR + " AS date_from,"
            + " COALESCE(" + REGULAR_DATE_TO_EXPR + ", " + REGULAR_DATE_FROM_EXPR + ") AS date_to,"
            + " tr.created_at AS created_at,"
            + " tr.email AS email,"
            + " tr.student_name AS applicant";

    private static final String IRREGULAR_SELECT_COLUMNS = "'" + TRAINING_SCHEDULE_TYPE_IRREGULAR + "' AS schedule_type,"
            + " tr.id AS id,"
            + " " + IRREGULAR_TRAINING_COURSE_EXPR + " AS training_course,"
            + " " + IRREGULAR_TRAINING_TYPE_EXPR + " AS training_type,"
            + " NULL AS curriculum_title,"
            + " NULL AS session_title,"
            + " tr.schedule_start AS date_from,"
            + " tr.schedule_end AS date_to,"
            + " tr.created_at AS created_at,"
            + " tr.email AS email,"
            + " tr.first_name AS applicant";

    private static final Map<String, String> SORT_COLUMN_MAP = Map.ofEntries(
            Map.entry("createdAt", "u.created_at"),
            Map.entry("scheduleType", "u.schedule_type"),
            Map.entry("trainingType", "u.training_type"),
            Map.entry("trainingCourse", "u.training_course"),
            Map.entry("curriculumTitle", "u.curriculum_title"),
            Map.entry("sessionTitle", "u.session_title"),
            Map.entry("dateFrom", "u.date_from"),
            Map.entry("dateTo", "u.date_to"),
            Map.entry("email", "u.email"),
            Map.entry("applicant", "u.applicant"));

    private static final String DEFAULT_ORDER_BY = " ORDER BY u.created_at DESC";

    private static final DateTimeFormatter TIMESTAMPTZ_CAST_FORMAT = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    @Transactional(readOnly = true)
    public Page<TrainingApplicationResponse> getList(String trainingScheduleType, String trainingCourse, String trainingType,
                                                       String curriculumTitle, String sessionTitle, String searchPeriodType,
                                                       OffsetDateTime startDate, OffsetDateTime endDate,
                                                       Pageable pageable) {

        UnionQuery uq = buildUnionQuery(trainingScheduleType, trainingCourse, trainingType,
                curriculumTitle, sessionTitle, searchPeriodType, startDate, endDate);
        if (uq.isEmpty()) {
            return new PageImpl<>(Collections.emptyList(), pageable, 0);
        }

        String countSql = "SELECT COUNT(*) FROM (" + uq.unionSql() + ") u";
        Query countQuery = entityManager.createNativeQuery(countSql);
        bindFilters(countQuery, uq);
        long totalElements = ((Number) countQuery.getSingleResult()).longValue();

        if (totalElements == 0) {
            return new PageImpl<>(Collections.emptyList(), pageable, 0);
        }

        String dataSql = "SELECT * FROM (" + uq.unionSql() + ") u"
                + buildOrderBy(pageable.getSort())
                + " LIMIT :size OFFSET :offset";
        Query dataQuery = entityManager.createNativeQuery(dataSql);
        bindFilters(dataQuery, uq);
        dataQuery.setParameter("size", pageable.getPageSize());
        dataQuery.setParameter("offset", pageable.getOffset());

        @SuppressWarnings("unchecked")
        List<Object[]> rows = dataQuery.getResultList();
        List<TrainingApplicationResponse> content = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            content.add(mapRowToResponse(row));
        }
        return new PageImpl<>(content, pageable, totalElements);
    }

    @Transactional(readOnly = true)
    public TrainingApplicationSummaryResponse getSummary(String regularTrainingType, String irregularTrainingType,
                                                           OffsetDateTime startDate, OffsetDateTime endDate) {
        TrainingApplicationSummaryResponse.CourseCounts regular =
                queryCourseCounts(true, regularTrainingType, startDate, endDate);
        TrainingApplicationSummaryResponse.CourseCounts irregular =
                queryCourseCounts(false, irregularTrainingType, startDate, endDate);
        return new TrainingApplicationSummaryResponse(regular, irregular);
    }

    private TrainingApplicationSummaryResponse.CourseCounts queryCourseCounts(boolean regular, String trainingType,
                                                                                OffsetDateTime startDate, OffsetDateTime endDate) {
        String courseExpr = regular ? REGULAR_TRAINING_COURSE_EXPR : IRREGULAR_TRAINING_COURSE_EXPR;
        String fromJoin = regular ? FROM_JOIN_REGULAR : FROM_JOIN_IRREGULAR;

        StringBuilder where = regular
                ? buildRegularWhere(null, trainingType, null, null, SEARCH_PERIOD_CREATED_AT, startDate, endDate)
                : buildIrregularWhere(null, trainingType, null, null, SEARCH_PERIOD_CREATED_AT, startDate, endDate);

        String sql = "SELECT COUNT(*) AS total,"
                + " COUNT(*) FILTER (WHERE " + courseExpr + " = '01') AS engineering,"
                + " COUNT(*) FILTER (WHERE " + courseExpr + " = '02') AS service,"
                + " COUNT(*) FILTER (WHERE " + courseExpr + " = '03') AS sales"
                + fromJoin + where;

        Query query = entityManager.createNativeQuery(sql);
        bindFilters(query, regular, !regular, null, trainingType, null, null,
                SEARCH_PERIOD_CREATED_AT, startDate, endDate);

        Object[] row = (Object[]) query.getSingleResult();
        return new TrainingApplicationSummaryResponse.CourseCounts(
                ((Number) row[0]).longValue(),
                ((Number) row[1]).longValue(),
                ((Number) row[2]).longValue(),
                ((Number) row[3]).longValue());
    }

    private UnionQuery buildUnionQuery(String trainingScheduleType, String trainingCourse, String trainingType,
                                        String curriculumTitle, String sessionTitle, String searchPeriodType,
                                        OffsetDateTime startDate, OffsetDateTime endDate) {
        String scheduleType = StringUtils.trimToNull(trainingScheduleType);
        boolean includeRegular = scheduleType == null || TRAINING_SCHEDULE_TYPE_REGULAR.equals(scheduleType);
        boolean includeIrregular = scheduleType == null || TRAINING_SCHEDULE_TYPE_IRREGULAR.equals(scheduleType);
        if (!includeRegular && !includeIrregular) {
            return new UnionQuery(false, false, null, null,
                    trainingCourse, trainingType, curriculumTitle, sessionTitle, startDate, endDate);
        }

        String periodType = StringUtils.defaultIfBlank(searchPeriodType, SEARCH_PERIOD_CREATED_AT).trim();

        StringBuilder regularWhere = includeRegular
                ? buildRegularWhere(trainingCourse, trainingType, curriculumTitle, sessionTitle, periodType, startDate, endDate)
                : null;
        StringBuilder irregularWhere = includeIrregular
                ? buildIrregularWhere(trainingCourse, trainingType, curriculumTitle, sessionTitle, periodType, startDate, endDate)
                : null;

        String unionSql = buildUnionSql(includeRegular, includeIrregular, regularWhere, irregularWhere);

        return new UnionQuery(includeRegular, includeIrregular, unionSql, periodType,
                trainingCourse, trainingType, curriculumTitle, sessionTitle, startDate, endDate);
    }

    private void bindFilters(Query query, UnionQuery uq) {
        bindFilters(query, uq.includeRegular(), uq.includeIrregular(), uq.trainingCourse(), uq.trainingType(),
                uq.curriculumTitle(), uq.sessionTitle(), uq.periodType(), uq.startDate(), uq.endDate());
    }

    private record UnionQuery(boolean includeRegular, boolean includeIrregular, String unionSql, String periodType,
                               String trainingCourse, String trainingType, String curriculumTitle, String sessionTitle,
                               OffsetDateTime startDate, OffsetDateTime endDate) {

        boolean isEmpty() {
            return !includeRegular && !includeIrregular;
        }
    }

    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> exportCsv(String trainingScheduleType, String trainingCourse, String trainingType,
                                             String curriculumTitle, String sessionTitle, String searchPeriodType,
                                             OffsetDateTime startDate, OffsetDateTime endDate, Sort sort,
                                             String headers, String keys, String dateFormats, String codeMaps,
                                             String reason, HttpServletRequest request) {

        List<TrainingApplicationResponse> content = getListForExport(trainingScheduleType, trainingCourse, trainingType,
                curriculumTitle, sessionTitle, searchPeriodType, startDate, endDate, sort);

        List<String> headerList = splitCsv(headers);
        List<String> keyList = splitCsv(keys);
        List<String> dateFormatList = splitCsv(dateFormats);

        Map<String, Map<String, String>> codeMapData = Collections.emptyMap();
        if (StringUtils.isNotBlank(codeMaps)) {
            try {
                codeMapData = objectMapper.readValue(codeMaps, new TypeReference<Map<String, Map<String, String>>>() {
                });
            } catch (Exception e) {
                log.warn("training-applications export codeMaps 파싱 실패, 매핑 없이 진행: {}", e.getMessage());
            }
        }

        List<Map<String, Object>> rows = new ArrayList<>(content.size());
        for (TrainingApplicationResponse item : content) {
            rows.add(toExportRow(item));
        }

        byte[] fileBytes = excelService.buildCsv(headerList, keyList, dateFormatList, codeMapData, rows);

        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String fileName = "training-applications_" + today + ".csv";
        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        responseHeaders.setContentDisposition(
                ContentDisposition.attachment().filename(fileName, StandardCharsets.UTF_8).build());

        if (StringUtils.isNotBlank(reason)) {
            String email = SecurityContextHolder.getContext().getAuthentication().getName();
            String createdBy = adminRepository.findByEmail(email)
                    .map(u -> String.valueOf(u.getId()))
                    .orElse(null);
            downloadLogService.saveAsync("training-applications", reason, "csv", createdBy, ClientIpUtils.resolve(request));
        }

        return ResponseEntity.ok().headers(responseHeaders).body(fileBytes);
    }

    private List<TrainingApplicationResponse> getListForExport(String trainingScheduleType, String trainingCourse, String trainingType,
                                                                 String curriculumTitle, String sessionTitle, String searchPeriodType,
                                                                 OffsetDateTime startDate, OffsetDateTime endDate, Sort sort) {

        UnionQuery uq = buildUnionQuery(trainingScheduleType, trainingCourse, trainingType,
                curriculumTitle, sessionTitle, searchPeriodType, startDate, endDate);
        if (uq.isEmpty()) {
            return Collections.emptyList();
        }

        String dataSql = "SELECT * FROM (" + uq.unionSql() + ") u" + buildOrderBy(sort);
        Query dataQuery = entityManager.createNativeQuery(dataSql);
        bindFilters(dataQuery, uq);

        @SuppressWarnings("unchecked")
        List<Object[]> rows = dataQuery.getResultList();
        List<TrainingApplicationResponse> content = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            content.add(mapRowToResponse(row));
        }
        return content;
    }

    private Map<String, Object> toExportRow(TrainingApplicationResponse item) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("scheduleType", item.scheduleType());
        row.put("trainingType", item.trainingType());
        row.put("trainingCourse", item.trainingCourse());
        row.put("curriculumTitle", item.curriculumTitle());
        row.put("sessionTitle", item.sessionTitle());
        row.put("dateFrom", item.dateFrom());
        row.put("dateTo", item.dateTo());
        row.put("createdAt", item.createdAt());
        row.put("email", item.email());
        row.put("applicant", item.applicant());
        return row;
    }

    private List<String> splitCsv(String s) {
        return (s != null && !s.isBlank()) ? Arrays.asList(s.split(",", -1)) : Collections.emptyList();
    }

    private String buildUnionSql(boolean includeRegular, boolean includeIrregular,
                                  StringBuilder regularWhere, StringBuilder irregularWhere) {
        StringBuilder sql = new StringBuilder();
        if (includeRegular) {
            sql.append("SELECT ").append(REGULAR_SELECT_COLUMNS).append(FROM_JOIN_REGULAR).append(regularWhere);
        }
        if (includeIrregular) {
            if (sql.length() > 0) {
                sql.append(" UNION ALL ");
            }
            sql.append("SELECT ").append(IRREGULAR_SELECT_COLUMNS).append(FROM_JOIN_IRREGULAR).append(irregularWhere);
        }
        return sql.toString();
    }

    private StringBuilder buildRegularWhere(String trainingCourse, String trainingType,
                                             String curriculumTitle, String sessionTitle,
                                             String periodType, OffsetDateTime startDate, OffsetDateTime endDate) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        if (StringUtils.isNotBlank(trainingCourse)) {
            where.append(" AND ").append(REGULAR_TRAINING_COURSE_EXPR).append(" = :regTrainingCourse");
        }
        if (StringUtils.isNotBlank(trainingType)) {
            where.append(" AND ").append(REGULAR_TRAINING_TYPE_EXPR).append(" LIKE :regTrainingType");
        }
        if (StringUtils.isNotBlank(curriculumTitle)) {
            where.append(" AND ").append(CURR_TITLE_EXPR).append(" ILIKE :curriculumTitle");
        }
        if (StringUtils.isNotBlank(sessionTitle)) {
            where.append(" AND ").append(SESS_TITLE_EXPR).append(" ILIKE :sessionTitle");
        }
        appendRegularPeriod(where, periodType, startDate, endDate);
        return where;
    }

    private StringBuilder buildIrregularWhere(String trainingCourse, String trainingType,
                                               String curriculumTitle, String sessionTitle,
                                               String periodType, OffsetDateTime startDate, OffsetDateTime endDate) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        if (StringUtils.isNotBlank(trainingCourse)) {
            where.append(" AND tr.training_track = :irrTrainingTrack");
        }
        if (StringUtils.isNotBlank(trainingType)) {
            where.append(" AND tr.training_format = :irrTrainingFormat");
        }
        // 비정기(training_request)는 관련 커리큘럼/세션 개념이 없어 제목으로 필터링하면 항상 매치 없음
        if (StringUtils.isNotBlank(curriculumTitle) || StringUtils.isNotBlank(sessionTitle)) {
            where.append(" AND FALSE");
        }
        appendIrregularPeriod(where, periodType, startDate, endDate);
        return where;
    }

    private void appendRegularPeriod(StringBuilder where, String periodType, OffsetDateTime startDate, OffsetDateTime endDate) {
        if (SEARCH_PERIOD_START.equals(periodType)) {
            if (startDate != null) {
                where.append(" AND ").append(REGULAR_DATE_FROM_EXPR).append(" >= CAST(:regPeriodFrom AS date)");
            }
            if (endDate != null) {
                where.append(" AND ").append(REGULAR_DATE_FROM_EXPR).append(" <= CAST(:regPeriodTo AS date)");
            }
        } else if (SEARCH_PERIOD_END.equals(periodType)) {
            String regularDateToExpr = "COALESCE(" + REGULAR_DATE_TO_EXPR + ", " + REGULAR_DATE_FROM_EXPR + ")";
            if (startDate != null) {
                where.append(" AND ").append(regularDateToExpr).append(" >= CAST(:regPeriodFrom AS date)");
            }
            if (endDate != null) {
                where.append(" AND ").append(regularDateToExpr).append(" <= CAST(:regPeriodTo AS date)");
            }
        } else {
            if (startDate != null) {
                where.append(" AND tr.created_at >= CAST(:periodFrom AS timestamptz)");
            }
            if (endDate != null) {
                where.append(" AND tr.created_at <= CAST(:periodTo AS timestamptz)");
            }
        }
    }

    private void appendIrregularPeriod(StringBuilder where, String periodType, OffsetDateTime startDate, OffsetDateTime endDate) {
        if (SEARCH_PERIOD_START.equals(periodType)) {
            if (startDate != null) {
                where.append(" AND tr.schedule_start >= CAST(:irrPeriodFrom AS date)");
            }
            if (endDate != null) {
                where.append(" AND tr.schedule_start <= CAST(:irrPeriodTo AS date)");
            }
        } else if (SEARCH_PERIOD_END.equals(periodType)) {
            if (startDate != null) {
                where.append(" AND tr.schedule_end >= CAST(:irrPeriodFrom AS date)");
            }
            if (endDate != null) {
                where.append(" AND tr.schedule_end <= CAST(:irrPeriodTo AS date)");
            }
        } else {
            if (startDate != null) {
                where.append(" AND tr.created_at >= CAST(:periodFrom AS timestamptz)");
            }
            if (endDate != null) {
                where.append(" AND tr.created_at <= CAST(:periodTo AS timestamptz)");
            }
        }
    }

    private void bindFilters(Query query, boolean includeRegular, boolean includeIrregular,
                              String trainingCourse, String trainingType,
                              String curriculumTitle, String sessionTitle,
                              String periodType, OffsetDateTime startDate, OffsetDateTime endDate) {
        if (includeRegular && StringUtils.isNotBlank(trainingCourse)) {
            query.setParameter("regTrainingCourse", trainingCourse.trim());
        }
        if (includeRegular && StringUtils.isNotBlank(trainingType)) {
            query.setParameter("regTrainingType", "%" + trainingType.trim() + "%");
        }
        if (includeIrregular && StringUtils.isNotBlank(trainingCourse)) {
            String code = trainingCourse.trim();
            query.setParameter("irrTrainingTrack", TRAINING_COURSE_CODE_TO_TRACK.getOrDefault(code, code));
        }
        if (includeIrregular && StringUtils.isNotBlank(trainingType)) {
            String code = trainingType.trim();
            query.setParameter("irrTrainingFormat", TRAINING_TYPE_CODE_TO_FORMAT.getOrDefault(code, code));
        }
        if (StringUtils.isNotBlank(curriculumTitle)) {
            query.setParameter("curriculumTitle", "%" + curriculumTitle.trim() + "%");
        }
        if (StringUtils.isNotBlank(sessionTitle)) {
            query.setParameter("sessionTitle", "%" + sessionTitle.trim() + "%");
        }
        bindPeriod(query, includeRegular, includeIrregular, periodType, startDate, endDate);
    }

    private void bindPeriod(Query query, boolean includeRegular, boolean includeIrregular,
                             String periodType, OffsetDateTime startDate, OffsetDateTime endDate) {
        if (SEARCH_PERIOD_START.equals(periodType) || SEARCH_PERIOD_END.equals(periodType)) {
            if (includeRegular) {
                if (startDate != null) {
                    query.setParameter("regPeriodFrom", startDate.toLocalDate().toString());
                }
                if (endDate != null) {
                    query.setParameter("regPeriodTo", endDate.toLocalDate().toString());
                }
            }
            if (includeIrregular) {
                if (startDate != null) {
                    query.setParameter("irrPeriodFrom", startDate.toLocalDate().toString());
                }
                if (endDate != null) {
                    query.setParameter("irrPeriodTo", endDate.toLocalDate().toString());
                }
            }
        } else {
            if (startDate != null) {
                query.setParameter("periodFrom", startDate.format(TIMESTAMPTZ_CAST_FORMAT));
            }
            if (endDate != null) {
                query.setParameter("periodTo", endDate.format(TIMESTAMPTZ_CAST_FORMAT));
            }
        }
    }

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

    private TrainingApplicationResponse mapRowToResponse(Object[] row) {
        String scheduleType = (String) row[0];
        Long id = row[1] != null ? ((Number) row[1]).longValue() : null;
        return new TrainingApplicationResponse(
                scheduleType + ":" + id,
                id,
                scheduleType,
                (String) row[2],
                (String) row[3],
                (String) row[4],
                (String) row[5],
                toLocalDate(row[6]),
                toLocalDate(row[7]),
                toOffsetDateTime(row[8]),
                (String) row[9],
                (String) row[10]);
    }

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
