package com.ge.bo.service;

import com.ge.bo.dto.TrainingApplicationResponse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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

@Service
@RequiredArgsConstructor
public class TrainingApplicationService {

    @PersistenceContext
    private EntityManager entityManager;

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
            + " LEFT JOIN page_data curr ON curr.id = tr.curriculum_id AND curr.data_slug = '" + CURR_SLUG + "'"
            + " LEFT JOIN page_data sess ON sess.id = tr.session_id AND sess.data_slug = '" + SESS_SLUG + "'";

    /** 비정기(training_request)는 관련 커리큘럼/세션 개념이 없어 JOIN하지 않는다 — IRREGULAR_SELECT_COLUMNS에서 NULL로 채움 */
    private static final String FROM_JOIN_IRREGULAR = " FROM training_request tr";

    private static final String REGULAR_SELECT_COLUMNS = "'" + TRAINING_SCHEDULE_TYPE_REGULAR + "' AS schedule_type,"
            + " tr.id AS id,"
            + " " + REGULAR_TRAINING_COURSE_EXPR + " AS training_course,"
            + " " + REGULAR_TRAINING_TYPE_EXPR + " AS training_type,"
            + " " + CURR_TITLE_EXPR + " AS curriculum_title,"
            + " " + SESS_TITLE_EXPR + " AS session_title,"
            + " " + REGULAR_DATE_FROM_EXPR + " AS date_from,"
            + " " + REGULAR_DATE_TO_EXPR + " AS date_to,"
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
            + " TRIM(CONCAT_WS(' ', tr.first_name, tr.last_name)) AS applicant";

    private static final Map<String, String> SORT_COLUMN_MAP = Map.ofEntries(
            Map.entry("createdAt", "u.created_at"),
            Map.entry("trainingType", "u.training_type"),
            Map.entry("trainingCourse", "u.training_course"),
            Map.entry("curriculumTitle", "u.curriculum_title"),
            Map.entry("sessionTitle", "u.session_title"),
            Map.entry("dateFrom", "u.date_from"),
            Map.entry("dateTo", "u.date_to"),
            Map.entry("email", "u.email"));

    private static final String DEFAULT_ORDER_BY = " ORDER BY u.created_at DESC";

    private static final DateTimeFormatter TIMESTAMPTZ_CAST_FORMAT = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    @Transactional(readOnly = true)
    public Page<TrainingApplicationResponse> getList(String trainingScheduleType, String trainingCourse, String trainingType,
                                                       String curriculumTitle, String sessionTitle, String searchPeriodType,
                                                       OffsetDateTime startDate, OffsetDateTime endDate,
                                                       Pageable pageable) {

        String scheduleType = StringUtils.trimToNull(trainingScheduleType);
        boolean includeRegular = scheduleType == null || TRAINING_SCHEDULE_TYPE_REGULAR.equals(scheduleType);
        boolean includeIrregular = scheduleType == null || TRAINING_SCHEDULE_TYPE_IRREGULAR.equals(scheduleType);
        if (!includeRegular && !includeIrregular) {
            return new PageImpl<>(Collections.emptyList(), pageable, 0);
        }

        String periodType = StringUtils.defaultIfBlank(searchPeriodType, SEARCH_PERIOD_CREATED_AT).trim();

        StringBuilder regularWhere = includeRegular
                ? buildRegularWhere(trainingCourse, trainingType, curriculumTitle, sessionTitle, periodType, startDate, endDate)
                : null;
        StringBuilder irregularWhere = includeIrregular
                ? buildIrregularWhere(trainingCourse, trainingType, curriculumTitle, sessionTitle, periodType, startDate, endDate)
                : null;

        String unionSql = buildUnionSql(includeRegular, includeIrregular, regularWhere, irregularWhere);

        String countSql = "SELECT COUNT(*) FROM (" + unionSql + ") u";
        Query countQuery = entityManager.createNativeQuery(countSql);
        bindFilters(countQuery, includeRegular, includeIrregular, trainingCourse, trainingType,
                curriculumTitle, sessionTitle, periodType, startDate, endDate);
        long totalElements = ((Number) countQuery.getSingleResult()).longValue();

        if (totalElements == 0) {
            return new PageImpl<>(Collections.emptyList(), pageable, 0);
        }

        String dataSql = "SELECT * FROM (" + unionSql + ") u"
                + buildOrderBy(pageable.getSort())
                + " LIMIT :size OFFSET :offset";
        Query dataQuery = entityManager.createNativeQuery(dataSql);
        bindFilters(dataQuery, includeRegular, includeIrregular, trainingCourse, trainingType,
                curriculumTitle, sessionTitle, periodType, startDate, endDate);
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
            if (startDate != null) {
                where.append(" AND ").append(REGULAR_DATE_TO_EXPR).append(" >= CAST(:regPeriodFrom AS date)");
            }
            if (endDate != null) {
                where.append(" AND ").append(REGULAR_DATE_TO_EXPR).append(" <= CAST(:regPeriodTo AS date)");
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
