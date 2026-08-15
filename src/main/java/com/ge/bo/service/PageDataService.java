package com.ge.bo.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ge.bo.common.context.SiteTimeZoneResolver;
import com.ge.bo.common.html.RichTextSanitizer;
import com.ge.bo.common.search.SearchSqlSupport;
import com.ge.bo.dto.AdjacentResponse;
import com.ge.bo.dto.CategoryLv2RowResponse;
import com.ge.bo.dto.CategoryProductRowResponse;
import com.ge.bo.dto.DevicesTreeRowResponse;
import com.ge.bo.dto.FoProductCategoryCountResponse;
import com.ge.bo.dto.FoProductSearchResponse;
import com.ge.bo.dto.ProductInsightRowResponse;
import com.ge.bo.dto.PageDataListResponse;
import com.ge.bo.dto.PageDataRequest;
import com.ge.bo.dto.PageDataResponse;
import com.ge.bo.dto.PopupResponse;
import com.ge.bo.dto.TrainingProductNodeResponse;
import com.ge.bo.dto.TrainingProductOptionResponse;
import com.ge.bo.dto.TrainingProductTreeItemResponse;
import com.ge.bo.dto.TrainingProductTreeResponse;
import com.ge.bo.exception.BusinessException;
import com.ge.bo.entity.AdminUser;
import com.ge.bo.entity.PageData;
import com.ge.bo.entity.SlugRelation;
import com.ge.bo.entity.ValidationRule;
import com.ge.bo.exception.ErrorCode;
import com.ge.bo.repository.AdminRepository;
import com.ge.bo.repository.PageDataRepository;
import com.ge.bo.repository.SlugRelationRepository;
import com.ge.bo.repository.ValidationRuleRepository;
import com.ge.bo.security.JwtTokenProvider;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Caching;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class PageDataService {

  private final PageDataRepository pageDataRepository;
  private final AdminRepository adminRepository;
  private final ObjectMapper objectMapper;
  private final PageFileService pageFileService;
  private final SlugRelationRepository slugRelationRepository;
  private final ValidationRuleRepository validationRuleRepository;
  private final SiteTimeZoneResolver siteTimeZoneResolver;
  private final IntegrationContentsSyncService integrationContentsSyncService;
  private final JwtTokenProvider jwtTokenProvider;
  private final RichTextSanitizer richTextSanitizer;

  @PersistenceContext
    private EntityManager entityManager;

  private static final Set<String> RESERVED_PARAMS = Set.of("page", "size", "sort", "sortExpr", "unpaged", "exclude", "fetchRelationIds", "previewToken", "drsKeys");

  /**
   * FO 공개 API(FoPageDataController)에서 게시상태를 클라이언트 파라미터가 아니라 서버가 직접 강제하는 slug.
   * findProductInsights()/queryCategoryInsights()가 이미 쓰던 것과 동일한 방식(JSON 섹션 안의 is_visible/publish_dttm을
   * 서버측 SQL에 하드코딩)을 press/blog/articles/events에도 동일하게 적용한다.
   */
  private static final Set<String> FO_PUBLISH_GATED_SLUGS =
      Set.of("press-data", "blog-data", "articles-data", "events-data");

  private static final String FO_PUBLISH_GATE_SQL =
        " AND data_json->(replace(data_slug,'-data','')) ->> 'is_visible' = '001'"
      + " AND substring(regexp_replace(data_json->(replace(data_slug,'-data',''))->>'publish_dttm', '[^0-9]', '', 'g'), 1, 8) <= :today";

  /**
   * FO 공개 API에서 게시일(publish_dttm) 없이 노출여부(is_visible)만 서버가 강제하는 slug → JSON 섹션명 매핑.
   * FO_PUBLISH_GATED_SLUGS와 동일한 목적이나, 섹션명이 slug명에서 유도되지 않아 별도 매핑이 필요하다.
   */
  private static final Map<String, String> FO_VISIBILITY_GATED_SLUGS =
      Map.of(
          "wheretobuy-agency-data", "agency",
          "currMgmt-data", "curriculum",
          "currDtlMgmt-data", "curriculum_detail3",
          "banner-data", "banner");

  private String visibilityGateSql(String slug) {
    String section = FO_VISIBILITY_GATED_SLUGS.get(slug);
    return section == null ? "" : " AND data_json->'" + section + "'->>'is_visible' = '001'";
  }

  /**
   * FO 공개 API에서 노출기간(post_period_from~to)을 클라이언트 파라미터(drs_post_period)가 아니라
   * 서버가 직접 강제하는 slug → JSON 섹션명 매핑. hero-data/banner-data처럼 is_visible 없이
   * 기간으로만 노출여부를 결정하는 데이터가 대상.
   */
  private static final Map<String, String> FO_PERIOD_GATED_SLUGS =
      Map.of(
          "hero-data", "hero",
          "banner-data", "banner");

  private String periodGateSql(String slug) {
    String section = FO_PERIOD_GATED_SLUGS.get(slug);
    if (section == null) return "";
    String fromExpr = toRangeBoundExpr("data_json->'" + section + "'->>'post_period_from'", false);
    String toExpr = toRangeBoundExpr("data_json->'" + section + "'->>'post_period_to'", true);
    return " AND " + fromExpr + " <= :nowValue AND " + toExpr + " >= :nowValue";
  }

  private static final String PRODUCT_DATA_SLUG_COND = "#slug == 'product-data'";

  private static final String CONTENTS_DATA_SLUG_COND =
      "#slug == 'blog-data' or #slug == 'press-data' or #slug == 'articles-data'";

  private static final String CATEGORY_LV2_CTE =
        "WITH visible_lv2 AS ("
      + "  SELECT c.id AS id, c.data_json AS data_json"
      + "  FROM page_data c"
      + "  WHERE c.data_slug = 'category-data'"
      + "   AND c.is_deleted = false"
      + "   AND c.data_json->'category'->>'parentId' = :categoryId"
      + "   AND c.data_json->'category'->>'depth'    = '2'"
      + "   AND c.data_json->'category'->>'is_visible' = '001'"
      + "   AND (c.site_id = :siteId OR c.site_id IS NULL)"
      + "), visible_product AS ("
      + "  SELECT DISTINCT v.id AS lv2_id, p.id AS product_id"
      + "  FROM visible_lv2 v"
      + "  JOIN page_data j"
      + "    ON j.data_slug = 'category-data'"
      + "   AND j.is_deleted = false"
      + "   AND j.data_json->'product'->>'depth'    = '3'"
      + "   AND j.data_json->'product'->>'parentId' = v.id::text"
      + "   AND (j.site_id = :siteId OR j.site_id IS NULL)"
      + "  JOIN page_data p"
      + "    ON p.data_slug = 'product-data'"
      + "   AND p.is_deleted = false"
      + "   AND p.id::text = j.data_json->'product'->>'id'"
      + "   AND (p.site_id = :siteId OR p.site_id IS NULL)"
      + "   AND p.data_json->'product'->>'is_visible'   = '001'"
      + "   AND p.data_json->'product'->>'order_status' = '01'"
      + ")";

  private static final String CATEGORY_SELF_PRODUCT_CTE =
        "WITH visible_product AS ("
      + "  SELECT DISTINCT ON (p.id) p.id AS product_id,"
      + "         j.data_json->>'sortOrder' AS sort_order,"
      + "         p.data_json AS data_json"
      + "  FROM page_data j"
      + "  JOIN page_data p"
      + "    ON p.data_slug = 'product-data'"
      + "   AND p.is_deleted = false"
      + "   AND p.id::text = j.data_json->'product'->>'id'"
      + "   AND (p.site_id = :siteId OR p.site_id IS NULL)"
      + "   AND p.data_json->'product'->>'is_visible'   = '001'"
      + "   AND p.data_json->'product'->>'order_status' = '01'"
      + "  WHERE j.data_slug = 'category-data'"
      + "   AND j.is_deleted = false"
      + "   AND j.data_json->'product'->>'depth'    = '3'"
      + "   AND j.data_json->'product'->>'parentId' = :categoryId"
      + "   AND (j.site_id = :siteId OR j.site_id IS NULL)"
      + "  ORDER BY p.id,"
      + "   CASE WHEN j.data_json->>'sortOrder' ~ '^[0-9]+$' THEN (j.data_json->>'sortOrder')::int END ASC NULLS LAST"
      + ")";

  /** 관리자용(BO) 조회 — 게시상태 서버 강제 없음(초안/미게시 포함 전체 조회가 정상 동작이므로) */
  @Transactional(readOnly = true)
    public PageDataListResponse search(String slug, Map<String, String> allParams, int page, int size, Long siteId) {
    return searchInternal(slug, allParams, page, size, siteId, false, false);
  }

  /** FO 공개용 조회 — FoPageDataController 전용 경로. 게시상태를 서버가 강제한다(enforcePublishGate=true) */
  @Transactional(readOnly = true)
    public PageDataListResponse search(String slug, Map<String, String> allParams, int page, int size, Long siteId, boolean unpaged) {
    return searchInternal(slug, allParams, page, size, siteId, unpaged, true);
  }

  private PageDataListResponse searchInternal(String slug, Map<String, String> allParams, int page, int size, Long siteId, boolean unpaged, boolean enforcePublishGate) {
    Map<String, String> relFilterParams = new LinkedHashMap<>();
    Map<String, String> joinFilterParams = new LinkedHashMap<>();
    Map<String, String> innerRelParams = new LinkedHashMap<>();
    Map<String, String> existsRelParams = new LinkedHashMap<>();
    Map<String, String> searchParams = new LinkedHashMap<>();
    allParams.forEach((key, value) -> {
      if (RESERVED_PARAMS.contains(key) || value == null || value.isBlank()) return;
      if (key.startsWith("rel_")) relFilterParams.put(key, value);
      else if (key.startsWith("joinr_") || key.startsWith("joink_") || key.startsWith("joinv_")) joinFilterParams.put(key, value);
      else if (key.startsWith("innerRel_")) innerRelParams.put(key, value);
      else if (key.startsWith("exs_") || key.startsWith("exk_") || key.startsWith("exm_") || key.startsWith("exf_")) existsRelParams.put(key, value);
      else searchParams.put(key, value);
    });

    StringBuilder whereClause = new StringBuilder("WHERE data_slug = :slug AND is_deleted = false");
    if (siteId != null) {
      whereClause.append(" AND (site_id = :siteId OR site_id IS NULL)");
    }
    appendWhereConditions(whereClause, searchParams);
    if (enforcePublishGate && FO_PUBLISH_GATED_SLUGS.contains(slug)) {
      whereClause.append(FO_PUBLISH_GATE_SQL);
    }
    if (enforcePublishGate) {
      whereClause.append(visibilityGateSql(slug));
      whereClause.append(periodGateSql(slug));
    }

    if ("currDtlMgmt-data".equals(slug) && relFilterParams.containsKey("rel_4")) {
      String categoryIdStr = relFilterParams.get("rel_4");
      Set<Long> filterIds = categoryIdStr.matches("\\d+")
          ? resolveCurrDtlMgmtIdsByCategoryFilter(Long.parseLong(categoryIdStr))
          : Set.of();
      if (filterIds.isEmpty()) return buildEmptyResponse(page, size);
      String idList = filterIds.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
      whereClause.append(" AND id IN (").append(idList).append(")");
    } else if (!relFilterParams.isEmpty()) {
      Set<Long> filterIds = resolveFilterRelationIds(relFilterParams);
      if (filterIds != null) {
        if (filterIds.isEmpty()) return buildEmptyResponse(page, size);
        String idList = filterIds.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
        whereClause.append(" AND id IN (").append(idList).append(")");
      }
    }

    if (!joinFilterParams.isEmpty()) {
      Set<Long> joinIds = resolveJoinFilterIds(joinFilterParams);
      if (joinIds != null) {
        if (joinIds.isEmpty()) return buildEmptyResponse(page, size);
        String idList = joinIds.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
        whereClause.append(" AND id IN (").append(idList).append(")");
      }
    }

    if (!innerRelParams.isEmpty()) {
      Set<Long> innerRelIds = resolveInnerRelationIds(innerRelParams);
      if (innerRelIds != null) {
        if (innerRelIds.isEmpty()) return buildEmptyResponse(page, size);
        String idList = innerRelIds.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
        whereClause.append(" AND id IN (").append(idList).append(")");
      }
    }

    Map<String, String> existsBindParams = new LinkedHashMap<>();
    appendExistsRelationConditions(whereClause, existsRelParams, existsBindParams, siteId);

    long totalElements = -1;
    if (!unpaged) {
      String countSql = "SELECT COUNT(*) FROM page_data " + whereClause;
      Query countQuery = entityManager.createNativeQuery(countSql);
      countQuery.setParameter("slug", slug);
      if (siteId != null) {
        countQuery.setParameter("siteId", siteId);
      }
      bindSearchParams(countQuery, searchParams, siteId);
      bindTodayIfPresent(countQuery, countSql, siteId);
      bindNowIfPresent(countQuery, countSql, siteId);
      existsBindParams.forEach(countQuery::setParameter);
      totalElements = ((Number) countQuery.getSingleResult()).longValue();

      if (totalElements == 0) {
        return buildEmptyResponse(page, size);
      }
    }

    OrderByClause orderByClause = buildOrderByClauseWithExpr(allParams.get("sort"), allParams.get("sortExpr"), !enforcePublishGate, slug, siteId);
    String orderBy = orderByClause.sql();

    String dataSql = "SELECT id, template_slug, data_json::text, group_id,"
                + " created_by, created_at, updated_by, updated_at, \"count\" "
                + "FROM page_data " + whereClause
                + orderBy
                + (unpaged ? "" : " LIMIT :size OFFSET :offset");
    Query dataQuery = entityManager.createNativeQuery(dataSql);
    dataQuery.setParameter("slug", slug);
    if (!unpaged) {
      dataQuery.setParameter("size", size);
      dataQuery.setParameter("offset", (long) page * size);
    }
    if (siteId != null) {
      dataQuery.setParameter("siteId", siteId);
    }
    bindSearchParams(dataQuery, searchParams, siteId);
    bindTodayIfPresent(dataQuery, dataSql, siteId);
    bindNowIfPresent(dataQuery, dataSql, siteId);
    existsBindParams.forEach(dataQuery::setParameter);
    orderByClause.params().forEach(dataQuery::setParameter);

    @SuppressWarnings("unchecked")
        List<Object[]> rows = dataQuery.getResultList();

    Map<Long, String> userNameMap = buildUserNameMap(rows, 4, 6);

    List<PageDataResponse> content = rows.stream()
                .map(row -> mapRowToResponse(row, userNameMap))
                .toList();

    applyExclude(content, allParams.get("exclude"));

    content = applyFetch(slug, content, siteId, parseFetchRelationIds(allParams));
    content = applyDateRangeStatus(content, allParams.get("drsKeys"), siteId);
    content = applyRegistrationState(slug, content, siteId, enforcePublishGate);

    if (unpaged) {
      int actualCount = content.size();
      return PageDataListResponse.builder()
                    .content(content)
                    .totalElements(actualCount)
                    .totalPages(1)
                    .page(0)
                    .size(actualCount)
                    .last(true)
                    .first(true)
                    .build();
    }

    int totalPages = (int) Math.ceil((double) totalElements / size);
    return PageDataListResponse.builder()
                .content(content)
                .totalElements(totalElements)
                .totalPages(totalPages)
                .page(page)
                .size(size)
                .last((page + 1) >= totalPages)
                .first(page == 0)
                .build();
  }

  @Transactional(readOnly = true)
    public PageDataListResponse searchDatetimeRange(String slug, Map<String, String> allParams, int page, int size, Long siteId, boolean unpaged) {
    Map<String, String> relFilterParams = new LinkedHashMap<>();
    Map<String, String> joinFilterParams = new LinkedHashMap<>();
    Map<String, String> innerRelParams = new LinkedHashMap<>();
    Map<String, String> existsRelParams = new LinkedHashMap<>();
    Map<String, String> searchParams = new LinkedHashMap<>();
    allParams.forEach((key, value) -> {
      if (RESERVED_PARAMS.contains(key) || value == null || value.isBlank()) return;
      if (key.startsWith("rel_")) relFilterParams.put(key, value);
      else if (key.startsWith("joinr_") || key.startsWith("joink_") || key.startsWith("joinv_")) joinFilterParams.put(key, value);
      else if (key.startsWith("innerRel_")) innerRelParams.put(key, value);
      else if (key.startsWith("exs_") || key.startsWith("exk_") || key.startsWith("exm_") || key.startsWith("exf_")) existsRelParams.put(key, value);
      else searchParams.put(key, value);
    });

    StringBuilder whereClause = new StringBuilder("WHERE data_slug = :slug AND is_deleted = false");
    if (siteId != null) {
      whereClause.append(" AND (site_id = :siteId OR site_id IS NULL)");
    }
    appendWhereConditionsDatetime(whereClause, searchParams);
    if (FO_PUBLISH_GATED_SLUGS.contains(slug)) {
      whereClause.append(FO_PUBLISH_GATE_SQL);
    }
    whereClause.append(visibilityGateSql(slug));
    whereClause.append(periodGateSql(slug));

    if (!relFilterParams.isEmpty()) {
      Set<Long> filterIds = resolveFilterRelationIds(relFilterParams);
      if (filterIds != null) {
        if (filterIds.isEmpty()) return buildEmptyResponse(page, size);
        String idList = filterIds.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
        whereClause.append(" AND id IN (").append(idList).append(")");
      }
    }

    if (!joinFilterParams.isEmpty()) {
      Set<Long> joinIds = resolveJoinFilterIds(joinFilterParams);
      if (joinIds != null) {
        if (joinIds.isEmpty()) return buildEmptyResponse(page, size);
        String idList = joinIds.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
        whereClause.append(" AND id IN (").append(idList).append(")");
      }
    }

    if (!innerRelParams.isEmpty()) {
      Set<Long> innerRelIds = resolveInnerRelationIds(innerRelParams);
      if (innerRelIds != null) {
        if (innerRelIds.isEmpty()) return buildEmptyResponse(page, size);
        String idList = innerRelIds.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
        whereClause.append(" AND id IN (").append(idList).append(")");
      }
    }

    Map<String, String> existsBindParams = new LinkedHashMap<>();
    appendExistsRelationConditions(whereClause, existsRelParams, existsBindParams, siteId);

    long totalElements = -1;
    if (!unpaged) {
      String countSql = "SELECT COUNT(*) FROM page_data " + whereClause;
      Query countQuery = entityManager.createNativeQuery(countSql);
      countQuery.setParameter("slug", slug);
      if (siteId != null) {
        countQuery.setParameter("siteId", siteId);
      }
      bindSearchParams(countQuery, searchParams, siteId);
      bindTodayIfPresent(countQuery, countSql, siteId);
      bindNowIfPresent(countQuery, countSql, siteId);
      existsBindParams.forEach(countQuery::setParameter);
      totalElements = ((Number) countQuery.getSingleResult()).longValue();

      if (totalElements == 0) {
        return buildEmptyResponse(page, size);
      }
    }

    String orderBy = buildOrderByClause(allParams.get("sort"), slug, siteId);

    String dataSql = "SELECT id, template_slug, data_json::text, group_id,"
                + " created_by, created_at, updated_by, updated_at, \"count\" "
                + "FROM page_data " + whereClause
                + orderBy
                + (unpaged ? "" : " LIMIT :size OFFSET :offset");
    Query dataQuery = entityManager.createNativeQuery(dataSql);
    dataQuery.setParameter("slug", slug);
    if (!unpaged) {
      dataQuery.setParameter("size", size);
      dataQuery.setParameter("offset", (long) page * size);
    }
    if (siteId != null) {
      dataQuery.setParameter("siteId", siteId);
    }
    bindSearchParams(dataQuery, searchParams, siteId);
    bindTodayIfPresent(dataQuery, dataSql, siteId);
    bindNowIfPresent(dataQuery, dataSql, siteId);
    existsBindParams.forEach(dataQuery::setParameter);

    @SuppressWarnings("unchecked")
        List<Object[]> rows = dataQuery.getResultList();

    Map<Long, String> userNameMap = buildUserNameMap(rows, 4, 6);

    List<PageDataResponse> content = rows.stream()
                .map(row -> mapRowToResponse(row, userNameMap))
                .toList();

    applyExclude(content, allParams.get("exclude"));
    content = applyFetch(slug, content, siteId, parseFetchRelationIds(allParams));
    content = applyDateRangeStatus(content, allParams.get("drsKeys"), siteId);
    content = applyRegistrationState(slug, content, siteId, true);

    if (unpaged) {
      int actualCount = content.size();
      return PageDataListResponse.builder()
                    .content(content)
                    .totalElements(actualCount)
                    .totalPages(1)
                    .page(0)
                    .size(actualCount)
                    .last(true)
                    .first(true)
                    .build();
    }

    int totalPages = (int) Math.ceil((double) totalElements / size);
    return PageDataListResponse.builder()
                .content(content)
                .totalElements(totalElements)
                .totalPages(totalPages)
                .page(page)
                .size(size)
                .last((page + 1) >= totalPages)
                .first(page == 0)
                .build();
  }

  @Transactional(readOnly = true)
    public PageDataResponse findPublicDetail(String slug, Long id, Map<String, String> allParams, Long siteId) {
    Map<String, String> statusParams = extractStatusParams(allParams);

    StringBuilder whereClause = new StringBuilder("WHERE data_slug = :slug AND id = :id AND is_deleted = false");
    if (siteId != null) {
      whereClause.append(" AND (site_id = :siteId OR site_id IS NULL)");
    }
    appendWhereConditions(whereClause, statusParams);
    if (FO_PUBLISH_GATED_SLUGS.contains(slug) && !isValidPreviewToken(allParams.get("previewToken"), slug, id)) {
      whereClause.append(FO_PUBLISH_GATE_SQL);
    }
    whereClause.append(visibilityGateSql(slug));

    String dataSql = "SELECT id, template_slug, data_json::text, group_id,"
                + " created_by, created_at, updated_by, updated_at, \"count\" "
                + "FROM page_data " + whereClause
                + " LIMIT 1";
    Query dataQuery = entityManager.createNativeQuery(dataSql);
    dataQuery.setParameter("slug", slug);
    dataQuery.setParameter("id", id);
    if (siteId != null) {
      dataQuery.setParameter("siteId", siteId);
    }
    bindSearchParams(dataQuery, statusParams, siteId);
    bindTodayIfPresent(dataQuery, dataSql, siteId);
    bindNowIfPresent(dataQuery, dataSql, siteId);

    @SuppressWarnings("unchecked")
        List<Object[]> rows = dataQuery.getResultList();
    if (rows.isEmpty()) {
      return null;
    }
    return mapRowToResponse(rows.get(0), Collections.emptyMap());
  }

  @Transactional
    public void incrementViewCount(String slug, Long id, Long siteId) {
    StringBuilder where = new StringBuilder("WHERE data_slug = :slug AND id = :id");
    if (siteId != null) {
      where.append(" AND (site_id = :siteId OR site_id IS NULL)");
    }
    Query q = entityManager.createNativeQuery(
                "UPDATE page_data SET \"count\" = \"count\" + 1 " + where);
    q.setParameter("slug", slug);
    q.setParameter("id", id);
    if (siteId != null) {
      q.setParameter("siteId", siteId);
    }
    q.executeUpdate();
  }

  @Transactional(readOnly = true)
    public AdjacentResponse findAdjacent(String slug, Long id, String sortField, String titleField,
                                         Map<String, String> allParams, Long siteId) {
    Map<String, String> statusParams = extractStatusParams(allParams, "sortField", "titleField");

    String sortExpr = resolveFieldExpr(sortField, true);
    String titleExpr = resolveFieldExpr(titleField, false);

    StringBuilder baseWhere = new StringBuilder("WHERE data_slug = :slug AND is_deleted = false");
    if (siteId != null) {
      baseWhere.append(" AND (site_id = :siteId OR site_id IS NULL)");
    }
    appendWhereConditions(baseWhere, statusParams);
    if (FO_PUBLISH_GATED_SLUGS.contains(slug)) {
      baseWhere.append(FO_PUBLISH_GATE_SQL);
    }
    baseWhere.append(visibilityGateSql(slug));

    String curVal = "(SELECT " + sortExpr + " FROM page_data WHERE data_slug = :slug AND id = :id)";

    String prevSql = "SELECT id, " + titleExpr + " AS title FROM page_data " + baseWhere
                + " AND (" + sortExpr + " > " + curVal
                + " OR (" + sortExpr + " = " + curVal + " AND id > :id))"
                + " ORDER BY " + sortExpr + " ASC, id ASC LIMIT 1";

    String nextSql = "SELECT id, " + titleExpr + " AS title FROM page_data " + baseWhere
                + " AND (" + sortExpr + " < " + curVal
                + " OR (" + sortExpr + " = " + curVal + " AND id < :id))"
                + " ORDER BY " + sortExpr + " DESC, id DESC LIMIT 1";

    AdjacentResponse.AdjacentItem prev = queryAdjacentItem(prevSql, slug, id, siteId, statusParams);
    AdjacentResponse.AdjacentItem next = queryAdjacentItem(nextSql, slug, id, siteId, statusParams);
    return new AdjacentResponse(prev, next);
  }

  @Transactional(readOnly = true)
    public List<DevicesTreeRowResponse> findDevicesTree(Long siteId) {
    String sql = "SELECT"
        + "  c.id AS row_id,"
        + "  CASE WHEN jsonb_exists(c.data_json, 'category')"
        + "       THEN c.data_json->'category'->>'depth'"
        + "       ELSE c.data_json->'product'->>'depth'"
        + "  END AS depth,"
        + "  CASE WHEN jsonb_exists(c.data_json, 'category')"
        + "       THEN c.data_json->'category'->>'parentId'"
        + "       ELSE c.data_json->'product'->>'parentId'"
        + "  END AS parent_id,"
        + "  c.data_json->'category'->>'title'                 AS category_title,"
        + "  c.data_json->'device_systems'->>'description'     AS category_description,"
        + "  c.data_json->'seo'->>'slug'                        AS category_slug,"
        + "  c.data_json->>'sortOrder'                          AS sort_order,"
        + "  (c.data_json->'product'->>'id')::bigint            AS product_id,"
        + "  p.data_json->'seo'->>'slug'                        AS product_slug,"
        + "  p.data_json->'product'->>'product_name'            AS product_title,"
        + "  p.data_json->'product'->>'product_description'    AS product_description,"
        + "  p.data_json->'product_info'->>'gnb_image'          AS product_image,"
        + "  p.data_json->'product'->>'order_status'            AS product_order_status,"
        + "  p.data_json->'product'->>'order_method'            AS product_order_method"
        + " FROM page_data c"
        + " LEFT JOIN page_data p"
        + "  ON p.data_slug = 'product-data'"
        + " AND p.is_deleted = false"
        + " AND p.id = (c.data_json->'product'->>'id')::bigint"
        + " AND p.data_json->'product'->>'is_visible' = '001'"
        + " AND (p.site_id = :siteId OR p.site_id IS NULL)"
        + " WHERE c.data_slug = 'category-data'"
        + "  AND c.is_deleted = false"
        + "  AND CASE WHEN jsonb_exists(c.data_json, 'category')"
        + "           THEN c.data_json->'category'->>'is_visible' = '001'"
        + "           ELSE true"
        + "      END"
        + "  AND (NOT jsonb_exists(c.data_json, 'product') OR p.id IS NOT NULL)"
        + "  AND (c.site_id = :siteId OR c.site_id IS NULL)"
        + " ORDER BY"
        + "  CASE WHEN jsonb_exists(c.data_json, 'category') THEN c.data_json->'category'->>'depth' ELSE c.data_json->'product'->>'depth' END ASC,"
        + "  CASE WHEN c.data_json->>'sortOrder' ~ '^[0-9]+$' THEN (c.data_json->>'sortOrder')::int END ASC NULLS LAST,"
        + "  c.id ASC";

    Query query = entityManager.createNativeQuery(sql);
    query.setParameter("siteId", siteId);

    @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();

    List<DevicesTreeRowResponse> result = new ArrayList<>();
    for (Object[] row : rows) {
      result.add(new DevicesTreeRowResponse(
          row[0] != null ? ((Number) row[0]).longValue() : null,
          row[1] != null ? row[1].toString() : null,
          row[2] != null ? row[2].toString() : null,
          row[3] != null ? row[3].toString() : null,
          row[4] != null ? row[4].toString() : null,
          row[5] != null ? row[5].toString() : null,
          row[6] != null ? row[6].toString() : null,
          row[7] != null ? ((Number) row[7]).longValue() : null,
          row[8] != null ? row[8].toString() : null,
          row[9] != null ? row[9].toString() : null,
          row[10] != null ? row[10].toString() : null,
          row[11] != null ? row[11].toString() : null,
          row[12] != null ? row[12].toString() : null,
          row[13] != null ? row[13].toString() : null
      ));
    }
    return result;
  }

    @Transactional(readOnly = true)
    public Optional<String> findProductManagerEmail(Long productId, Long siteId) {
        String sql = "SELECT data_json->'product_manager'->>'email'"
            + " FROM page_data"
            + " WHERE data_slug = 'productManager-data'"
            + "  AND is_deleted = false"
            + "  AND data_json->'ms' @> to_jsonb(:productId)"
            + "  AND data_json->'product_manager'->>'is_visible' = '001'"
            + "  AND (site_id = :siteId OR site_id IS NULL)"
            + " LIMIT 1";

        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("productId", productId);
        query.setParameter("siteId", siteId);

        @SuppressWarnings("unchecked")
        List<Object> rows = query.getResultList();
        return rows.isEmpty() ? Optional.empty() : Optional.ofNullable((String) rows.get(0));
    }

    @Transactional(readOnly = true)
    public List<ProductInsightRowResponse> findProductInsights(Long productId, Long siteId) {
        String section = "data_json->(replace(data_slug,'-data',''))";
        String sql = "SELECT id, data_slug,"
            + "  " + section + "->>'title'        AS title,"
            + "  " + section + "->>'publish_dttm' AS publish_dttm,"
            + "  " + section + "->>'image'        AS image"
            + " FROM page_data"
            + " WHERE data_slug IN ('blog-data','press-data','articles-data')"
            + "  AND is_deleted = false"
            + "  AND data_json->'product_list' @> to_jsonb(:productId)"
            + "  AND " + section + "->>'is_visible' = '001'"
            + "  AND substring(regexp_replace(" + section + "->>'publish_dttm', '[^0-9]', '', 'g'), 1, 8) <= :today"
            + "  AND (site_id = :siteId OR site_id IS NULL)"
            + " ORDER BY " + section + "->>'publish_dttm' DESC, id DESC"
            + " LIMIT 3";

        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("productId", productId);
        query.setParameter("today", resolveTodayParam(siteId));
        query.setParameter("siteId", siteId);

        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();

        List<ProductInsightRowResponse> result = new ArrayList<>();
        for (Object[] row : rows) {
            result.add(new ProductInsightRowResponse(
                row[0] != null ? ((Number) row[0]).longValue() : null,
                row[1] != null ? row[1].toString() : null,
                row[2] != null ? row[2].toString() : null,
                row[3] != null ? row[3].toString() : null,
                row[4] != null ? row[4].toString() : null
            ));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public FoProductSearchResponse searchProducts(String q, List<Long> categoryIds, int offset, int limit, Long siteId) {
        boolean hasCategories = categoryIds != null && !categoryIds.isEmpty();
        boolean hasKeyword = q != null && !q.isBlank();

        if (!hasKeyword && !hasCategories) {
            return new FoProductSearchResponse(0L, java.util.Collections.emptyList());
        }

        String kw = null;
        String kwExact = null;
        String kwPrefix = null;
        String kwRegex = null;
        if (hasKeyword) {
            String trimmed = q.trim();
            kw = SearchSqlSupport.toLikePattern(trimmed);
            kwExact = SearchSqlSupport.toLikeExactPattern(trimmed);
            kwPrefix = SearchSqlSupport.toLikePrefixPattern(trimmed);
            kwRegex = SearchSqlSupport.toWordStartRegex(trimmed);
        }

        String siteCond = siteId != null ? " AND (pd.site_id = :siteId OR pd.site_id IS NULL)" : "";
        String fromClause = " FROM page_data pd";

        String categoryJoin = buildProductCategoryJoin("pd", siteId);

        String whereClause = " WHERE pd.data_slug = 'product-data'"
            + "  AND pd.is_deleted = false"
            + "  AND pd.data_json->'product'->>'is_visible' = '001'"
            + siteCond;
        if (hasKeyword) {
            whereClause += buildProductKeywordCondition("pd");
        }
        if (hasCategories) {
            String junctionSiteCond = siteId != null ? " AND (j.site_id = :siteId OR j.site_id IS NULL)" : "";
            whereClause += " AND ("
                + " SELECT (j.data_json->'product'->>'parentId')::bigint"
                + " FROM page_data j"
                + " WHERE j.data_slug = 'category-data'"
                + "  AND j.is_deleted = false"
                + "  AND j.data_json->'product'->>'depth' = '3'"
                + "  AND (j.data_json->'product'->>'id')::bigint = pd.id"
                + junctionSiteCond
                + " ORDER BY"
                + "  CASE WHEN j.data_json->>'sortOrder' ~ '^[0-9]+$' THEN (j.data_json->>'sortOrder')::int END ASC NULLS LAST,"
                + "  j.id ASC"
                + " LIMIT 1"
                + " ) IN (:categoryIds)";
        }

        String countSql = "SELECT COUNT(*)" + fromClause + categoryJoin + whereClause;
        Query countQuery = entityManager.createNativeQuery(countSql);
        if (hasKeyword) {
            countQuery.setParameter("kw", kw);
        }
        if (hasCategories) {
            countQuery.setParameter("categoryIds", categoryIds);
        }
        if (siteId != null) {
            countQuery.setParameter("siteId", siteId);
        }
        long total = ((Number) countQuery.getSingleResult()).longValue();
        if (total == 0) {
            return new FoProductSearchResponse(0L, java.util.Collections.emptyList());
        }

        String relevanceOrder = "";
        if (hasKeyword) {
            String titleExpr = "pd.data_json->'product'->>'product_name'";
            relevanceOrder = " (CASE"
                + " WHEN " + titleExpr + " ILIKE :kwExact  ESCAPE '\\' THEN 100"
                + " WHEN " + titleExpr + " ILIKE :kwPrefix ESCAPE '\\' THEN 80"
                + " WHEN " + titleExpr + " ~* :kwRegex THEN 60"
                + " WHEN " + titleExpr + " ILIKE :kw ESCAPE '\\' THEN 40"
                + " ELSE 10 END) DESC,";
        }
        String listSql = "SELECT pd.id,"
            + "  pd.data_json->'product'->>'product_name'        AS product_name,"
            + "  pd.data_json->'product'->>'product_description' AS product_description,"
            + "  pd.data_json->'product_info'->'image'->>0       AS image_media_id,"
            + "  pd.data_json->'seo'->>'slug'                     AS slug,"
            + "  lv1.data_json->'category'->>'title'              AS category,"
            + "  lv2.data_json->'category'->>'title'              AS highlight"
            + fromClause
            + categoryJoin
            + whereClause
            + " ORDER BY" + relevanceOrder + " pd.updated_at DESC NULLS LAST, pd.id ASC"
            + " LIMIT :limit OFFSET :offset";
        Query listQuery = entityManager.createNativeQuery(listSql);
        if (hasKeyword) {
            listQuery.setParameter("kw", kw);
            listQuery.setParameter("kwExact", kwExact);
            listQuery.setParameter("kwPrefix", kwPrefix);
            listQuery.setParameter("kwRegex", kwRegex);
        }
        if (hasCategories) {
            listQuery.setParameter("categoryIds", categoryIds);
        }
        if (siteId != null) {
            listQuery.setParameter("siteId", siteId);
        }
        listQuery.setParameter("limit", limit);
        listQuery.setParameter("offset", offset);

        @SuppressWarnings("unchecked")
        List<Object[]> rows = listQuery.getResultList();

        List<FoProductSearchResponse.Item> items = new ArrayList<>();
        for (Object[] row : rows) {
            items.add(new FoProductSearchResponse.Item(
                row[0] != null ? ((Number) row[0]).longValue() : null,
                row[1] != null ? row[1].toString() : null,
                row[2] != null ? row[2].toString() : null,
                resolveMediaProxyUrl(row[3]),
                row[4] != null ? row[4].toString() : null,
                row[5] != null ? row[5].toString() : null,
                row[6] != null ? row[6].toString() : null
            ));
        }
        return new FoProductSearchResponse(total, items);
    }

    @Transactional(readOnly = true)
    public List<FoProductCategoryCountResponse> getProductCategoryCounts(String q, Long siteId) {
        boolean hasKeyword = q != null && !q.isBlank();

        String productSiteCond = siteId != null ? " AND (p.site_id = :siteId OR p.site_id IS NULL)" : "";
        String categoryJoin = buildProductCategoryJoin("p", siteId);
        String sql = "SELECT pc.lv2_id AS category_l2_id,"
            + "       count(*)::int AS cnt"
            + " FROM page_data p"
            + categoryJoin
            + " WHERE p.data_slug = 'product-data'"
            + "   AND p.is_deleted = false"
            + "   AND p.data_json->'product'->>'is_visible' = '001'"
            + productSiteCond;
        if (hasKeyword) {
            sql += buildProductKeywordCondition("p");
        }
        sql += " GROUP BY pc.lv2_id";

        Query query = entityManager.createNativeQuery(sql);
        if (hasKeyword) {
            query.setParameter("kw", SearchSqlSupport.toLikePattern(q.trim()));
        }
        if (siteId != null) {
            query.setParameter("siteId", siteId);
        }
        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();
        List<FoProductCategoryCountResponse> result = new ArrayList<>();
        for (Object[] r : rows) {
            result.add(new FoProductCategoryCountResponse(
                r[0] != null ? r[0].toString() : null,
                r[1] != null ? ((Number) r[1]).intValue() : 0));
        }
        return result;
    }

    private String buildProductCategoryJoin(String productAlias, Long siteId) {
        String j3SiteCond = siteId != null ? " AND (j3.site_id = :siteId OR j3.site_id IS NULL)" : "";
        String lv2SiteCond = siteId != null ? " AND (lv2.site_id = :siteId OR lv2.site_id IS NULL)" : "";
        String lv1SiteCond = siteId != null ? " AND (lv1.site_id = :siteId OR lv1.site_id IS NULL)" : "";
        return " LEFT JOIN LATERAL ("
            + "  SELECT (j3.data_json->'product'->>'parentId')::bigint AS lv2_id"
            + "  FROM page_data j3"
            + "  WHERE j3.data_slug = 'category-data'"
            + "   AND j3.is_deleted = false"
            + "   AND j3.data_json->'product'->>'depth' = '3'"
            + "   AND (j3.data_json->'product'->>'id')::bigint = " + productAlias + ".id"
            + j3SiteCond
            + "  ORDER BY"
            + "   CASE WHEN j3.data_json->>'sortOrder' ~ '^[0-9]+$' THEN (j3.data_json->>'sortOrder')::int END ASC NULLS LAST,"
            + "   j3.id ASC"
            + "  LIMIT 1"
            + " ) pc ON true"
            + " LEFT JOIN page_data lv2 ON lv2.id = pc.lv2_id AND lv2.data_slug = 'category-data' AND lv2.is_deleted = false" + lv2SiteCond
            + " LEFT JOIN page_data lv1 ON lv1.id = (lv2.data_json->'category'->>'parentId')::bigint AND lv1.data_slug = 'category-data' AND lv1.is_deleted = false" + lv1SiteCond;
    }

    private String buildProductKeywordCondition(String productAlias) {
        // 검색 대상: Lv3 제품명/제품명 보조설명/제품설명 + Lv1·Lv2 카테고리명/카테고리명 보조설명
        return "  AND ( " + productAlias + ".data_json->'product'->>'product_name'             ILIKE :kw ESCAPE '\\'"
            + "     OR " + productAlias + ".data_json->'product'->>'product_description'      ILIKE :kw ESCAPE '\\'"
            + "     OR " + productAlias + ".data_json->'product_info'->>'info_description'    ILIKE :kw ESCAPE '\\'"
            + "     OR lv1.data_json->'category'->>'title'                  ILIKE :kw ESCAPE '\\'"
            + "     OR lv1.data_json->'category'->>'sub_title'              ILIKE :kw ESCAPE '\\'"
            + "     OR lv2.data_json->'category'->>'title'                  ILIKE :kw ESCAPE '\\'"
            + "     OR lv2.data_json->'category'->>'sub_title'              ILIKE :kw ESCAPE '\\' )";
    }

    private String resolveMediaProxyUrl(Object mediaIdValue) {
        if (mediaIdValue == null) return null;
        try {
            long mediaId = Long.parseLong(mediaIdValue.toString().trim());
            return "/api/v1/fo/page-files/" + mediaId;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Transactional(readOnly = true)
    public List<PopupResponse> findActivePopup(Long siteId) {
        String siteCond = siteId != null ? "  AND (site_id = :siteId OR site_id IS NULL)" : "";
        String fromDigits = "regexp_replace(data_json->'popup'->>'post_period_from', '[^0-9]', '', 'g')";
        String toDigits   = "regexp_replace(data_json->'popup'->>'post_period_to',   '[^0-9]', '', 'g')";
        String fromCmp = "(CASE WHEN char_length(" + fromDigits + ") = 8 THEN " + fromDigits + " || '000000' ELSE " + fromDigits + " END)";
        String toCmp   = "(CASE WHEN char_length(" + toDigits   + ") = 8 THEN " + toDigits   + " || '235959'"
            + " WHEN char_length(" + toDigits + ") = 12 THEN " + toDigits + " || '59'"
            + " ELSE " + toDigits   + " END)";
        String sql = "SELECT id,"
            + "  data_json->'popup'->>'url'          AS url,"
            + "  data_json->'popup'->'image'->>0     AS image_file_id"
            + " FROM page_data"
            + " WHERE data_slug = 'popup-data'"
            + "  AND is_deleted = false"
            + siteCond
            + "  AND " + fromCmp + " <= :nowValue"
            + "  AND " + toCmp + " >= :nowValue"
            + " ORDER BY created_at DESC, id DESC";

        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("nowValue", resolveNowParam(siteId));
        if (siteId != null) {
            query.setParameter("siteId", siteId);
        }

        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();
        List<PopupResponse> result = new ArrayList<>();
        for (Object[] row : rows) {
            Long id = row[0] != null ? ((Number) row[0]).longValue() : null;
            String url = row[1] != null ? row[1].toString() : null;
            Long imageFileId = null;
            if (row[2] != null) {
                try {
                    imageFileId = Long.valueOf(row[2].toString().trim());
                } catch (NumberFormatException ignore) {
                    imageFileId = null;
                }
            }
            result.add(new PopupResponse(id, url, imageFileId));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<CategoryLv2RowResponse> findCategoryLv2(Long categoryId, Long siteId) {
        String sql = CATEGORY_LV2_CTE
            + " SELECT v.id AS id,"
            + "  v.data_json->'category'->>'title'        AS title,"
            + "  v.data_json->'seo'->>'slug'              AS slug,"
            + "  v.data_json->'device_systems'->>'image'  AS image"
            + " FROM visible_lv2 v"
            + " WHERE EXISTS (SELECT 1 FROM visible_product vp WHERE vp.lv2_id = v.id)"
            + " ORDER BY"
            + "  CASE WHEN v.data_json->>'sortOrder' ~ '^[0-9]+$' THEN (v.data_json->>'sortOrder')::int END ASC NULLS LAST,"
            + "  v.id ASC";

        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("categoryId", String.valueOf(categoryId));
        query.setParameter("siteId", siteId);

        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();

        List<CategoryLv2RowResponse> result = new ArrayList<>();
        for (Object[] row : rows) {
            result.add(new CategoryLv2RowResponse(
                row[0] != null ? ((Number) row[0]).longValue() : null,
                row[1] != null ? row[1].toString() : null,
                row[2] != null ? row[2].toString() : null,
                row[3] != null ? row[3].toString() : null
            ));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<CategoryProductRowResponse> findCategoryProducts(Long categoryId, Long siteId) {
        String sql = CATEGORY_SELF_PRODUCT_CTE
            + " SELECT vp.product_id AS id,"
            + "  vp.data_json->'product'->>'product_name'          AS product_name,"
            + "  vp.data_json->'product_info'->>'image'            AS image,"
            + "  vp.data_json->'product_info'->>'info_description' AS info_description,"
            + "  vp.data_json->'seo'->>'slug'                      AS slug,"
            + "  vp.data_json->'product'->>'awards'                AS awards"
            + " FROM visible_product vp"
            + " ORDER BY"
            + "  CASE WHEN vp.sort_order ~ '^[0-9]+$' THEN vp.sort_order::int END ASC NULLS LAST,"
            + "  vp.product_id ASC";

        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("categoryId", String.valueOf(categoryId));
        query.setParameter("siteId", siteId);

        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();

        List<CategoryProductRowResponse> result = new ArrayList<>();
        for (Object[] row : rows) {
            result.add(new CategoryProductRowResponse(
                row[0] != null ? ((Number) row[0]).longValue() : null,
                row[1] != null ? row[1].toString() : null,
                row[2] != null ? row[2].toString() : null,
                row[3] != null ? row[3].toString() : null,
                row[4] != null ? row[4].toString() : null,
                row[5] != null ? row[5].toString() : null
            ));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public TrainingProductTreeResponse findTrainingProductTree(Long siteId, boolean activeOnly) {
        String sql =
            "SELECT lv1.id AS lv1_id,"
          + "  lv1.data_json->'category'->>'title' AS lv1_title,"
          + "  lv2.id AS lv2_id,"
          + "  lv2.data_json->'category'->>'title' AS lv2_title,"
          + "  p.id AS product_id,"
          + "  p.data_json->'product'->>'product_name' AS product_name,"
          + "  p.data_json->'product'->>'product_type' AS product_type,"
          + "  j.id AS join_id"
          + " FROM page_data lv2"
          + " JOIN page_data lv1"
          + "   ON lv1.data_slug = 'category-data'"
          + "  AND lv1.is_deleted = false"
          + "  AND lv1.id::text = lv2.data_json->'category'->>'parentId'"
          + " JOIN page_data j"
          + "   ON j.data_slug = 'category-data'"
          + "  AND j.is_deleted = false"
          + "  AND j.data_json->'product'->>'depth' = '3'"
          + "  AND j.data_json->'product'->>'parentId' = lv2.id::text"
          + "  AND (j.site_id = :siteId OR j.site_id IS NULL)"
          + " JOIN page_data p"
          + "   ON p.data_slug = 'product-data'"
          + "  AND p.is_deleted = false"
          + "  AND p.id::text = j.data_json->'product'->>'id'"
          + (activeOnly ? "  AND p.data_json->'product'->>'has_training' = '001'"
                        + "  AND p.data_json->'product'->>'is_visible' = '001'" : "")
          + "  AND (p.site_id = :siteId OR p.site_id IS NULL)"
          + " WHERE lv2.data_slug = 'category-data'"
          + "  AND lv2.is_deleted = false"
          + "  AND lv2.data_json->'category'->>'depth' = '2'"
          + "  AND (lv2.site_id = :siteId OR lv2.site_id IS NULL)"
          + "  AND (lv1.site_id = :siteId OR lv1.site_id IS NULL)"
          + " ORDER BY lv1.id, lv2.id, p.id";

        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("siteId", siteId);

        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();

        List<TrainingProductNodeResponse> power = buildTrainingTree(rows, "P");
        List<TrainingProductNodeResponse> automation = buildTrainingTree(rows, "A");
        return new TrainingProductTreeResponse(power, automation, buildTrainingTreeItems(rows));
    }

    private List<TrainingProductTreeItemResponse> buildTrainingTreeItems(List<Object[]> rows) {
        List<TrainingProductTreeItemResponse> items = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            items.add(new TrainingProductTreeItemResponse(
                row[7] != null ? ((Number) row[7]).longValue() : null,
                row[0] != null ? ((Number) row[0]).longValue() : null,
                row[1] != null ? row[1].toString() : null,
                row[2] != null ? ((Number) row[2]).longValue() : null,
                row[3] != null ? row[3].toString() : null,
                row[4] != null ? ((Number) row[4]).longValue() : null,
                row[5] != null ? row[5].toString() : null,
                row[6] != null ? row[6].toString() : null
            ));
        }
        return items;
    }

    private List<TrainingProductNodeResponse> buildTrainingTree(List<Object[]> rows, String productType) {
        boolean powerMode = "P".equals(productType);

        Map<Long, String> groupTitles = new LinkedHashMap<>();
        Map<Long, Map<Long, String>> optionsByGroup = new LinkedHashMap<>();

        for (Object[] row : rows) {
            String rowProductType = row[6] != null ? row[6].toString() : null;
            if (!productType.equals(rowProductType)) continue;

            Long lv1Id = ((Number) row[0]).longValue();
            String lv1Title = row[1] != null ? row[1].toString() : null;
            Long lv2Id = ((Number) row[2]).longValue();
            String lv2Title = row[3] != null ? row[3].toString() : null;
            Long productId = ((Number) row[4]).longValue();
            String productName = row[5] != null ? row[5].toString() : null;

            Long groupId = powerMode ? lv1Id : lv2Id;
            String groupTitle = powerMode ? lv1Title : lv2Title;
            Long optionId = powerMode ? lv2Id : productId;
            String optionName = powerMode ? lv2Title : productName;

            groupTitles.putIfAbsent(groupId, groupTitle);
            optionsByGroup
                .computeIfAbsent(groupId, k -> new LinkedHashMap<>())
                .putIfAbsent(optionId, optionName);
        }

        List<TrainingProductNodeResponse> nodes = new ArrayList<>();
        for (Map.Entry<Long, String> groupEntry : groupTitles.entrySet()) {
            Long groupId = groupEntry.getKey();
            List<TrainingProductOptionResponse> options = new ArrayList<>();
            for (Map.Entry<Long, String> optionEntry : optionsByGroup.get(groupId).entrySet()) {
                options.add(new TrainingProductOptionResponse(optionEntry.getKey(), optionEntry.getValue()));
            }
            nodes.add(new TrainingProductNodeResponse(groupId, groupEntry.getValue(), options));
        }
        return nodes;
    }

    @Transactional(readOnly = true)
    public Map<Long, String> findCategoryNamesByIds(List<Long> ids, Long siteId) {
        if (ids == null || ids.isEmpty()) {
            return new LinkedHashMap<>();
        }
        String sql =
            "SELECT c.id,"
          + "  COALESCE("
          + "    c.data_json->'category'->>'title',"
          + "    c.data_json->'product'->>'product_name',"
          + "    p.data_json->'product'->>'product_name'"
          + "  ) AS name"
          + " FROM page_data c"
          + " LEFT JOIN page_data p"
          + "   ON p.data_slug = 'product-data'"
          + "  AND p.is_deleted = false"
          + "  AND p.id::text = c.data_json->'product'->>'id'"
          + "  AND (p.site_id = :siteId OR p.site_id IS NULL)"
          + " WHERE c.data_slug = 'category-data'"
          + "  AND c.is_deleted = false"
          + "  AND c.id IN (:ids)"
          + "  AND (c.site_id = :siteId OR c.site_id IS NULL)";

        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("ids", ids);
        query.setParameter("siteId", siteId);

        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();

        Map<Long, String> names = new LinkedHashMap<>();
        for (Object[] row : rows) {
            String name = row[1] != null ? row[1].toString() : null;
            if (name != null && !name.isBlank()) {
                names.put(((Number) row[0]).longValue(), name);
            }
        }
        return names;
    }

    @Transactional(readOnly = true)
    public List<ProductInsightRowResponse> findCategoryInsights(Long categoryId, Long siteId) {
        return queryCategoryInsights(CATEGORY_LV2_CTE, categoryId, siteId);
    }

    @Transactional(readOnly = true)
    public List<ProductInsightRowResponse> findCategoryInsightsBySelf(Long categoryId, Long siteId) {
        return queryCategoryInsights(CATEGORY_SELF_PRODUCT_CTE, categoryId, siteId);
    }

    private List<ProductInsightRowResponse> queryCategoryInsights(String cte, Long categoryId, Long siteId) {
        String section = "n.data_json->(replace(n.data_slug,'-data',''))";
        String sql = cte
            + ", matched AS ("
            + "   SELECT DISTINCT n.id"
            + "   FROM visible_product vp"
            + "   JOIN page_data n"
            + "     ON n.data_slug IN ('blog-data','press-data','articles-data')"
            + "    AND n.is_deleted = false"
            + "    AND n.data_json->'product_list' @> to_jsonb(vp.product_id)"
            + " )"
            + " SELECT n.id, n.data_slug,"
            + "  " + section + "->>'title'        AS title,"
            + "  " + section + "->>'publish_dttm' AS publish_dttm,"
            + "  " + section + "->>'image'        AS image"
            + " FROM page_data n"
            + " JOIN matched m ON m.id = n.id"
            + " WHERE n.is_deleted = false"
            + "  AND " + section + "->>'is_visible' = '001'"
            + "  AND substring(regexp_replace(" + section + "->>'publish_dttm', '[^0-9]', '', 'g'), 1, 8) <= :today"
            + "  AND (n.site_id = :siteId OR n.site_id IS NULL)"
            + " ORDER BY " + section + "->>'publish_dttm' DESC, n.id DESC"
            + " LIMIT 3";

        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("categoryId", String.valueOf(categoryId));
        query.setParameter("today", resolveTodayParam(siteId));
        query.setParameter("siteId", siteId);

        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();

        List<ProductInsightRowResponse> result = new ArrayList<>();
        for (Object[] row : rows) {
            result.add(new ProductInsightRowResponse(
                row[0] != null ? ((Number) row[0]).longValue() : null,
                row[1] != null ? row[1].toString() : null,
                row[2] != null ? row[2].toString() : null,
                row[3] != null ? row[3].toString() : null,
                row[4] != null ? row[4].toString() : null
            ));
        }
        return result;
    }

  @Transactional(readOnly = true)
    public PageDataResponse getById(String slug, Long id) {
    PageData pageData = pageDataRepository.findByIdAndDataSlug(id, slug)
                .orElseThrow(ErrorCode.PAGE_DATA_NOT_FOUND::toException);
    PageDataResponse response = PageDataResponse.from(pageData);
    List<PageDataResponse> enriched = applyFetch(slug, List.of(response), null, null);
    PageDataResponse enrichedResponse = enriched.get(0);
    return enrichedResponse.withUserNames(
        resolveUserName(pageData.getCreatedBy()),
        resolveUserName(pageData.getUpdatedBy())
    );
  }

  private Map<String, Object> stripFetchedFields(Map<String, Object> dataJson) {
    if (dataJson == null) return dataJson;
    Map<String, Object> cleaned = new LinkedHashMap<>();
    dataJson.forEach((key, value) -> {
      if (!key.startsWith("_fetchedRel") && !key.startsWith("_drs_") && !key.startsWith("_registration")) cleaned.put(key, value);
    });
    return richTextSanitizer.sanitizeDataJson(cleaned);
  }

  @Transactional
    @Caching(put = {
        @CachePut(cacheNames = "productData", key = "#result.getId()", condition = PRODUCT_DATA_SLUG_COND),
        @CachePut(cacheNames = "contentsData", key = "#result.getId()", condition = CONTENTS_DATA_SLUG_COND)
    })
    public PageDataResponse create(String slug, PageDataRequest request, Long siteId) {
    if (request.getPkKeys() != null && !request.getPkKeys().isEmpty()) {
      checkPkDuplicate(slug, request.getPkKeys(), request.getDataJson(), null, siteId);
    }
    if (request.getValidationRuleIds() != null && !request.getValidationRuleIds().isEmpty()) {
      checkValidationRules(slug, request.getValidationRuleIds(), request.getDataJson(), null, siteId);
    }

    Map<String, Object> cleanDataJson = stripFetchedFields(request.getDataJson());
    cleanDataJson = applySnapshotPathFields(slug, null, cleanDataJson, cleanDataJson);
    String dataJsonStr = serializeDataJson(cleanDataJson);
    String currentUser = getCurrentUserId();
    LocalDateTime now = LocalDateTime.now(siteTimeZoneResolver.resolve(siteId));
    final Query insertQuery;
    if (request.getGroupId() != null && !request.getGroupId().isBlank()) {
      insertQuery = entityManager.createNativeQuery(
          "INSERT INTO page_data"
          + " (template_slug, data_slug, data_json, site_id, group_id, created_by, created_at, updated_by, updated_at)"
          + " VALUES (:templateSlug, :slug, CAST(:dataJson AS jsonb), :siteId, :groupId, :createdBy, :createdAt, :updatedBy, :updatedAt)"
          + " RETURNING id");
      insertQuery.setParameter("groupId", request.getGroupId());
    } else {
      insertQuery = entityManager.createNativeQuery(
          "INSERT INTO page_data"
          + " (template_slug, data_slug, data_json, site_id, created_by, created_at, updated_by, updated_at)"
          + " VALUES (:templateSlug, :slug, CAST(:dataJson AS jsonb), :siteId, :createdBy, :createdAt, :updatedBy, :updatedAt)"
          + " RETURNING id");
    }
    insertQuery.setParameter("templateSlug", request.getTemplateSlug() != null ? request.getTemplateSlug() : slug);
    insertQuery.setParameter("slug", slug);
    insertQuery.setParameter("dataJson", dataJsonStr);
    insertQuery.setParameter("siteId", siteId);
    insertQuery.setParameter("createdBy", currentUser);
    insertQuery.setParameter("createdAt", now);
    insertQuery.setParameter("updatedBy", currentUser);
    insertQuery.setParameter("updatedAt", now);
    Long newId = ((Number) insertQuery.getSingleResult()).longValue();

    Map<String, Object> dataJsonWithId = new LinkedHashMap<>(cleanDataJson);
    dataJsonWithId.put("id", newId);
    Query updateIdQuery = entityManager.createNativeQuery(
                "UPDATE page_data SET data_json = CAST(:dataJson AS jsonb) WHERE id = :id");
    updateIdQuery.setParameter("dataJson", serializeDataJson(dataJsonWithId));
    updateIdQuery.setParameter("id", newId);
    updateIdQuery.executeUpdate();

    integrationContentsSyncService.syncUpsert(slug, newId, cleanDataJson, siteId);

    return getById(slug, newId);
  }

  @Transactional
    @Caching(put = {
        @CachePut(cacheNames = "productData", key = "#id", condition = PRODUCT_DATA_SLUG_COND),
        @CachePut(cacheNames = "contentsData", key = "#id", condition = CONTENTS_DATA_SLUG_COND)
    })
    public PageDataResponse update(String slug, Long id, PageDataRequest request, Long siteId) {
    PageData existing = pageDataRepository.findByIdAndDataSlug(id, slug)
                .orElseThrow(ErrorCode.PAGE_DATA_NOT_FOUND::toException);

    Map<String, Object> baseDataJson;
    try {
      baseDataJson = new LinkedHashMap<>(objectMapper.readValue(
          existing.getDataJson(), new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {}));
    } catch (Exception e) {
      baseDataJson = new LinkedHashMap<>();
    }
    baseDataJson = stripFetchedFields(baseDataJson);
    Map<String, Object> requestDataJson = stripFetchedFields(request.getDataJson());
    Map<String, Object> mergedDataJson = deepMerge(baseDataJson, requestDataJson);
    mergedDataJson = applySnapshotPathFields(slug, baseDataJson, requestDataJson, mergedDataJson);

    if (request.getValidationRuleIds() != null && !request.getValidationRuleIds().isEmpty()) {
      checkValidationRules(slug, request.getValidationRuleIds(), mergedDataJson, id, siteId);
    }

    Map<String, Object> dataJsonWithId = new LinkedHashMap<>(mergedDataJson);
    dataJsonWithId.put("id", id);
    String dataJsonStr = serializeDataJson(dataJsonWithId);
    String currentUser = getCurrentUserId();
    LocalDateTime updatedAt = LocalDateTime.now(siteTimeZoneResolver.resolve(existing.getSiteId()));
    Query updateQuery = entityManager.createNativeQuery(
        "UPDATE page_data"
        + " SET data_json = CAST(:dataJson AS jsonb), updated_by = :updatedBy, updated_at = :updatedAt"
        + ", template_slug = :templateSlug"
        + " WHERE id = :id AND data_slug = :slug");
    updateQuery.setParameter("dataJson", dataJsonStr);
    updateQuery.setParameter("updatedBy", currentUser);
    updateQuery.setParameter("updatedAt", updatedAt);
    updateQuery.setParameter("templateSlug", request.getTemplateSlug() != null ? request.getTemplateSlug() : slug);
    updateQuery.setParameter("id", id);
    updateQuery.setParameter("slug", slug);
    updateQuery.executeUpdate();

    integrationContentsSyncService.syncUpsert(slug, id, mergedDataJson, existing.getSiteId());

    return getById(slug, id);
  }

  private Map<String, Object> deepMerge(Map<String, Object> base, Map<String, Object> patch) {
    Map<String, Object> result = new LinkedHashMap<>(base);
    patch.forEach((key, patchValue) -> {
      Object baseValue = result.get(key);
      if (baseValue instanceof Map && patchValue instanceof Map) {
        @SuppressWarnings("unchecked")
        Map<String, Object> baseMap = (Map<String, Object>) baseValue;
        @SuppressWarnings("unchecked")
        Map<String, Object> patchMap = (Map<String, Object>) patchValue;
        result.put(key, deepMerge(baseMap, patchMap));
      } else {
        result.put(key, patchValue);
      }
    });
    return result;
  }

  private Map<String, Object> applySnapshotPathFields(
      String slug, Map<String, Object> baseDataJson, Map<String, Object> incomingDataJson, Map<String, Object> targetDataJson) {
    Map<String, SlugRelation> snapshotFields;
    try {
      snapshotFields = resolveSnapshotPathFields(slug);
    } catch (Exception e) {
      log.warn("카테고리 경로 스냅샷 대상 필드 조회 실패, 기존 저장 방식으로 폴백: slug={}, err={}", slug, e.getMessage());
      return targetDataJson;
    }
    if (snapshotFields.isEmpty()) return targetDataJson;

    Map<String, Object> result = new LinkedHashMap<>(targetDataJson);
    for (Map.Entry<String, SlugRelation> entry : snapshotFields.entrySet()) {
      String fieldKey = entry.getKey();
      if (!incomingDataJson.containsKey(fieldKey)) continue;
      try {
        SlugRelation pathRel = entry.getValue();
        /* 선택 단위 = 카테고리 매핑(depth3) 고유 id — 같은 제품이 여러 카테고리에 매핑돼도 매핑별로 독립 선택/보존된다.
           FE가 category-data(depth3 leaf) 행의 고유 id를 그대로 제출하므로 여기서 추출되는 값도 productId가 아니라
           매핑 자신의 id다(카테고리 매핑이 없는 예외적인 경우만 productId 그대로 폴백 — buildSnapshotEntry와 동일 관례,
           저장되는 entry 형태 자체(id/productId/depth1/depth2/depth3)는 이전과 동일하게 유지된다) */
        LinkedHashSet<Long> requestedIds = extractProductIdsFromFieldValue(incomingDataJson.get(fieldKey));
        Map<Long, Map<String, Object>> previousById = baseDataJson != null
            ? groupSnapshotEntriesByProductId(baseDataJson.get(fieldKey))
            : Map.of();

        List<Long> idsToResolve = new ArrayList<>();
        for (Long id : requestedIds) {
          if (!previousById.containsKey(id)) idsToResolve.add(id);
        }
        Map<Long, Map<String, Object>> resolved = resolveCategoryPathSnapshotByMappingIds(pathRel, idsToResolve);

        List<Map<String, Object>> merged = new ArrayList<>();
        for (Long id : requestedIds) {
          Map<String, Object> item = previousById.get(id);
          if (item == null) item = resolved.get(id);
          if (item == null) item = buildSnapshotEntry(id, null, null, null);
          merged.add(item);
        }
        result.put(fieldKey, merged);
      } catch (Exception e) {
        log.warn("카테고리 경로 스냅샷 처리 실패, 기존 저장 방식으로 폴백: slug={}, field={}, err={}", slug, fieldKey, e.getMessage());
      }
    }
    return result;
  }

  private Map<String, SlugRelation> resolveSnapshotPathFields(String slug) {
    Map<String, SlugRelation> result = new LinkedHashMap<>();
    List<SlugRelation> fetchRelations = slugRelationRepository.findByMasterSlugAndRelationDir(slug, "FETCH");
    for (SlugRelation rel : fetchRelations) {
      if (rel.getSnapshotPathRelationId() == null) continue;
      slugRelationRepository.findById(rel.getSnapshotPathRelationId())
          .ifPresent(pathRel -> result.put(rel.getMasterKey(), pathRel));
    }
    return result;
  }

  @SuppressWarnings("unchecked")
  private LinkedHashSet<Long> extractProductIdsFromFieldValue(Object rawValue) {
    LinkedHashSet<Long> ids = new LinkedHashSet<>();
    if (!(rawValue instanceof List<?> list)) return ids;
    for (Object item : list) {
      Long id;
      if (item instanceof Map) {
        Map<String, Object> itemMap = (Map<String, Object>) item;
        Object pid = itemMap.containsKey("productId") ? itemMap.get("productId") : itemMap.get("id");
        id = toLongOrNull(pid);
      } else {
        id = toLongOrNull(item);
      }
      if (id != null) ids.add(id);
    }
    return ids;
  }

  /**
   * 이전 저장값을 매핑(depth3) 고유 id 기준으로 그룹핑 — 재저장 시 변경 없는 매핑을 그대로 보존하기 위한 조회용.
   * depth3(카테고리 매핑 id)가 없는 예외 케이스(카테고리 미매핑 제품)만 productId/id로 폴백한다.
   * depth3는 category-data 행의 고유 id라 매핑당 정확히 1건만 존재한다(리스트가 아닌 단건 Map으로 그룹핑).
   */
  @SuppressWarnings("unchecked")
  private Map<Long, Map<String, Object>> groupSnapshotEntriesByProductId(Object rawValue) {
    Map<Long, Map<String, Object>> grouped = new LinkedHashMap<>();
    if (!(rawValue instanceof List<?> list)) return grouped;
    for (Object item : list) {
      if (!(item instanceof Map)) continue;
      Map<String, Object> itemMap = (Map<String, Object>) item;
      Long key = toLongOrNull(itemMap.get("depth3"));
      if (key == null) {
        Object pid = itemMap.containsKey("productId") ? itemMap.get("productId") : itemMap.get("id");
        key = toLongOrNull(pid);
      }
      if (key == null) continue;
      grouped.put(key, itemMap);
    }
    return grouped;
  }

  private Long toLongOrNull(Object value) {
    if (value == null) return null;
    if (value instanceof Number number) return number.longValue();
    try {
      return Long.parseLong(value.toString().trim());
    } catch (Exception e) {
      return null;
    }
  }

  private Map<String, Object> buildSnapshotEntry(Long productId, Long depth1, Long depth2, Long depth3) {
    Map<String, Object> entry = new LinkedHashMap<>();
    entry.put("id", productId);
    entry.put("productId", productId);
    entry.put("depth1", depth1);
    entry.put("depth2", depth2);
    entry.put("depth3", depth3);
    return entry;
  }

  /**
   * 카테고리 매핑(depth3) id를 직접 조회해 productId/depth1/depth2를 역산한다.
   * FE가 이미 category-data(depth3 leaf) 행의 고유 id를 선택값으로 제출하므로, product.id 기준 fan-out 조회
   * 없이 그 id 자체를 바로 조회하면 된다(예전 product 기준 fan-out 방식보다 단순함 — 매핑당 정확히 1행 조회).
   */
  private Map<Long, Map<String, Object>> resolveCategoryPathSnapshotByMappingIds(
      SlugRelation pathRel, Collection<Long> mappingIds) {
    Map<Long, Map<String, Object>> result = new LinkedHashMap<>();
    if (mappingIds == null || mappingIds.isEmpty()) return result;

    Map<Long, Map<String, Object>> depth3DataById = fetchDataJsonByIds(pathRel.getSlaveSlug(), mappingIds);

    Set<Long> depth2Ids = new LinkedHashSet<>();
    for (Map<String, Object> depth3Data : depth3DataById.values()) {
      String depth2IdStr = extractField(depth3Data, "product.parentId");
      Long depth2Id = depth2IdStr != null ? toLongOrNull(depth2IdStr.trim()) : null;
      if (depth2Id != null) depth2Ids.add(depth2Id);
    }
    Map<Long, Map<String, Object>> depth2DataById = fetchDataJsonByIds(pathRel.getSlaveSlug(), depth2Ids);

    for (Long mappingId : mappingIds) {
      Map<String, Object> depth3Data = depth3DataById.get(mappingId);
      if (depth3Data == null) continue; /* 매핑 행이 삭제된 경우 — 호출부의 productId 폴백으로 처리됨 */
      String productIdStr = extractField(depth3Data, "product.id");
      Long productId = productIdStr != null ? toLongOrNull(productIdStr.trim()) : null;
      if (productId == null) continue;

      String depth2IdStr = extractField(depth3Data, "product.parentId");
      Long depth2Id = depth2IdStr != null ? toLongOrNull(depth2IdStr.trim()) : null;

      Long depth1Id = null;
      if (depth2Id != null) {
        Map<String, Object> depth2Data = depth2DataById.get(depth2Id);
        if (depth2Data == null) {
          depth2Id = null;
        } else {
          String depth1IdStr = extractField(depth2Data, "category.parentId");
          depth1Id = depth1IdStr != null ? toLongOrNull(depth1IdStr.trim()) : null;
        }
      }
      result.put(mappingId, buildSnapshotEntry(productId, depth1Id, depth2Id, mappingId));
    }

    return result;
  }

  private Map<Long, Map<String, Object>> fetchDataJsonByIds(String dataSlug, Collection<Long> ids) {
    Map<Long, Map<String, Object>> result = new LinkedHashMap<>();
    if (ids == null || ids.isEmpty()) return result;
    Query query = entityManager.createNativeQuery(
        "SELECT id, data_json::text FROM page_data WHERE data_slug = :slug AND is_deleted = false AND id IN (:ids)");
    query.setParameter("slug", dataSlug);
    query.setParameter("ids", ids);
    @SuppressWarnings("unchecked")
    List<Object[]> rows = query.getResultList();
    for (Object[] row : rows) {
      Long id = ((Number) row[0]).longValue();
      try {
        Map<String, Object> dataJson = objectMapper.readValue(
            row[1].toString(), new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
        result.put(id, dataJson);
      } catch (Exception e) {
        log.warn("카테고리 경로 스냅샷 조회 중 JSON 파싱 실패: id={}, err={}", id, e.getMessage());
      }
    }
    return result;
  }

  @Transactional
    @Caching(put = {
        @CachePut(cacheNames = "productData", key = "#id", condition = PRODUCT_DATA_SLUG_COND),
        @CachePut(cacheNames = "contentsData", key = "#id", condition = CONTENTS_DATA_SLUG_COND)
    })
    public PageDataResponse patchField(String slug, Long id, String fieldKey, Object value) {
    value = richTextSanitizer.sanitizeValue(value);
    PageData existing = pageDataRepository.findByIdAndDataSlug(id, slug)
                .orElseThrow(ErrorCode.PAGE_DATA_NOT_FOUND::toException);
    Map<String, Object> dataJson;
    try {
      dataJson = new LinkedHashMap<>(objectMapper.readValue(
          existing.getDataJson(), new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {}));
    } catch (Exception e) {
      dataJson = new LinkedHashMap<>();
    }
    dataJson = new LinkedHashMap<>(stripFetchedFields(dataJson));
    String[] segments = fieldKey.split("\\.");
    if (segments.length == 1) {
      dataJson.put(fieldKey, value);
    } else {
      Map<String, Object> cursor = dataJson;
      for (int i = 0; i < segments.length - 1; i++) {
        Object next = cursor.get(segments[i]);
        if (!(next instanceof Map)) {
          next = new LinkedHashMap<String, Object>();
          cursor.put(segments[i], next);
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> nextMap = (Map<String, Object>) next;
        cursor = nextMap;
      }
      cursor.put(segments[segments.length - 1], value);
    }
    dataJson.put("id", id);
    String dataJsonStr = serializeDataJson(dataJson);
    String currentUser = getCurrentUserId();
    LocalDateTime updatedAt = LocalDateTime.now(siteTimeZoneResolver.resolve(existing.getSiteId()));
    Query updateQuery = entityManager.createNativeQuery(
        "UPDATE page_data"
        + " SET data_json = CAST(:dataJson AS jsonb), updated_by = :updatedBy, updated_at = :updatedAt"
        + " WHERE id = :id AND data_slug = :slug");
    updateQuery.setParameter("dataJson", dataJsonStr);
    updateQuery.setParameter("updatedBy", currentUser);
    updateQuery.setParameter("updatedAt", updatedAt);
    updateQuery.setParameter("id", id);
    updateQuery.setParameter("slug", slug);
    updateQuery.executeUpdate();
    return getById(slug, id);
  }

  @CacheEvict(cacheNames = "contentsData", key = "#id", condition = CONTENTS_DATA_SLUG_COND)
  @Transactional
    public void delete(String slug, Long id) {
    pageDataRepository.findByIdAndDataSlug(id, slug)
                .orElseThrow(ErrorCode.PAGE_DATA_NOT_FOUND::toException);

    pageFileService.deleteByDataId(id);

    pageDataRepository.deleteByIdAndDataSlug(id, slug);

    integrationContentsSyncService.syncSoftDelete(slug, id);
  }

  @CacheEvict(cacheNames = "contentsData", allEntries = true, condition = CONTENTS_DATA_SLUG_COND)
  @Transactional
    public void deleteByPk(String slug, List<String> pkKeys, Map<String, Object> dataJson) {
    if (pkKeys == null || pkKeys.isEmpty()) {
      throw ErrorCode.PAGE_DATA_PK_REQUIRED.toException();
    }

    List<String> validKeys = pkKeys.stream()
                .filter(k -> k != null && k.matches("[a-zA-Z0-9_]+"))
                .toList();
    if (validKeys.isEmpty()) {
      throw ErrorCode.PAGE_DATA_PK_REQUIRED.toException();
    }

    StringBuilder sql = new StringBuilder(
                "SELECT id FROM page_data WHERE data_slug = :slug AND is_deleted = false");
    for (String key : validKeys) {
      sql.append(" AND data_json->>'").append(key).append("' = :pk_").append(key);
    }
    sql.append(" LIMIT 1");

    Query query = entityManager.createNativeQuery(sql.toString());
    query.setParameter("slug", slug);
    for (String key : validKeys) {
      Object val = dataJson.get(key);
      query.setParameter("pk_" + key, val != null ? val.toString() : "");
    }

    @SuppressWarnings("unchecked")
        List<Object> results = query.getResultList();
    if (results.isEmpty()) {
      throw ErrorCode.PAGE_DATA_NOT_FOUND.toException();
    }

    Long id = ((Number) results.get(0)).longValue();
    delete(slug, id);
  }

  @Transactional(readOnly = true)
    public Set<Long> resolveExportFilterIds(String slug, Map<String, String> allParams, Long siteId) {
    Map<String, String> relFilterParams = new LinkedHashMap<>();
    Map<String, String> joinFilterParams = new LinkedHashMap<>();
    Map<String, String> innerRelParams = new LinkedHashMap<>();
    Map<String, String> existsRelParams = new LinkedHashMap<>();
    Map<String, String> searchParams = new LinkedHashMap<>();
    allParams.forEach((key, value) -> {
      if (RESERVED_PARAMS.contains(key) || value == null || value.isBlank()) return;
      if (key.startsWith("rel_")) relFilterParams.put(key, value);
      else if (key.startsWith("joinr_") || key.startsWith("joink_") || key.startsWith("joinv_")) joinFilterParams.put(key, value);
      else if (key.startsWith("innerRel_")) innerRelParams.put(key, value);
      else if (key.startsWith("exs_") || key.startsWith("exk_") || key.startsWith("exm_") || key.startsWith("exf_")) existsRelParams.put(key, value);
      else searchParams.put(key, value);
    });

    StringBuilder whereClause = new StringBuilder("WHERE data_slug = :slug AND is_deleted = false");
    if (siteId != null) {
      whereClause.append(" AND (site_id = :siteId OR site_id IS NULL)");
    }
    appendWhereConditions(whereClause, searchParams);

    if ("currDtlMgmt-data".equals(slug) && relFilterParams.containsKey("rel_4")) {
      String categoryIdStr = relFilterParams.get("rel_4");
      Set<Long> filterIds = categoryIdStr.matches("\\d+")
          ? resolveCurrDtlMgmtIdsByCategoryFilter(Long.parseLong(categoryIdStr))
          : Set.of();
      if (filterIds.isEmpty()) return Set.of();
      String idList = filterIds.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
      whereClause.append(" AND id IN (").append(idList).append(")");
    } else if (!relFilterParams.isEmpty()) {
      Set<Long> filterIds = resolveFilterRelationIds(relFilterParams);
      if (filterIds != null) {
        if (filterIds.isEmpty()) return Set.of();
        String idList = filterIds.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
        whereClause.append(" AND id IN (").append(idList).append(")");
      }
    }

    if (!joinFilterParams.isEmpty()) {
      Set<Long> joinIds = resolveJoinFilterIds(joinFilterParams);
      if (joinIds != null) {
        if (joinIds.isEmpty()) return Set.of();
        String idList = joinIds.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
        whereClause.append(" AND id IN (").append(idList).append(")");
      }
    }

    if (!innerRelParams.isEmpty()) {
      Set<Long> innerRelIds = resolveInnerRelationIds(innerRelParams);
      if (innerRelIds != null) {
        if (innerRelIds.isEmpty()) return Set.of();
        String idList = innerRelIds.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
        whereClause.append(" AND id IN (").append(idList).append(")");
      }
    }

    Map<String, String> existsBindParams = new LinkedHashMap<>();
    appendExistsRelationConditions(whereClause, existsRelParams, existsBindParams, siteId);

    String sql = "SELECT id FROM page_data " + whereClause;
    Query query = entityManager.createNativeQuery(sql);
    query.setParameter("slug", slug);
    if (siteId != null) {
      query.setParameter("siteId", siteId);
    }
    bindSearchParams(query, searchParams, siteId);
    bindTodayIfPresent(query, sql, siteId);
    bindNowIfPresent(query, sql, siteId);
    existsBindParams.forEach(query::setParameter);

    @SuppressWarnings("unchecked")
    List<Object> rows = query.getResultList();
    Set<Long> ids = new LinkedHashSet<>();
    for (Object row : rows) {
      ids.add(((Number) row).longValue());
    }
    return ids;
  }

  @Transactional(readOnly = true)
    public List<Map<String, Object>> exportAll(String slug, Map<String, String> allParams, Long siteId, List<Long> relationIds) {
    Set<String> reservedForExport = new HashSet<>(RESERVED_PARAMS);
    reservedForExport.addAll(Set.of("format", "headers", "keys", "dateFormats", "codeMaps", "reason", "relationIds"));

    Map<String, String> searchParams = new LinkedHashMap<>();
    allParams.forEach((key, value) -> {
      if (!reservedForExport.contains(key) && value != null && !value.isBlank()) {
        searchParams.put(key, value);
      }
    });

    StringBuilder whereClause = new StringBuilder("WHERE data_slug = :slug AND is_deleted = false");
    if (siteId != null) {
      whereClause.append(" AND (site_id = :siteId OR site_id IS NULL)");
    }
    appendWhereConditions(whereClause, searchParams);

    String dataSql = "SELECT id, template_slug, data_json::text, created_by, created_at, updated_by, updated_at "
                + "FROM page_data " + whereClause
                + " ORDER BY created_at DESC";
    Query dataQuery = entityManager.createNativeQuery(dataSql);
    dataQuery.setParameter("slug", slug);
    if (siteId != null) {
      dataQuery.setParameter("siteId", siteId);
    }
    bindSearchParams(dataQuery, searchParams, siteId);
    bindTodayIfPresent(dataQuery, dataSql, siteId);
    bindNowIfPresent(dataQuery, dataSql, siteId);

    @SuppressWarnings("unchecked")
        List<Object[]> rows = dataQuery.getResultList();

    List<Map<String, Object>> dataJsonList = new ArrayList<>();
    for (Object[] row : rows) {
      Map<String, Object> dataMap = new LinkedHashMap<>();
      try {
        if (row[2] != null) {
          dataMap = objectMapper.readValue(row[2].toString(),
                        new com.fasterxml.jackson.core.type.TypeReference<>() {
                        });
        }
      } catch (Exception e) {
        log.warn("exportAll dataJson 파싱 실패: {}", e.getMessage());
      }
      dataJsonList.add(dataMap);
    }

    applyFetchBatch(slug, dataJsonList, relationIds);

    List<Map<String, Object>> result = new ArrayList<>();
    for (int i = 0; i < rows.size(); i++) {
      Object[] row = rows.get(i);
      Map<String, Object> flattened = flattenDataJson(dataJsonList.get(i));
      flattened.put("createdBy",  row[3]);
      flattened.put("createdAt",  row[4] != null ? row[4].toString() : null);
      flattened.put("updatedBy",  row[5]);
      flattened.put("updatedAt",  row[6] != null ? row[6].toString() : null);
      result.add(flattened);
    }
    return result;
  }

    @SuppressWarnings("unchecked")
  private Map<String, Object> flattenDataJson(Map<String, Object> raw) {
    if (raw == null || raw.isEmpty()) return raw;

    List<Map.Entry<String, Object>> sectionEntries = raw.entrySet().stream()
                .filter(e -> e.getValue() instanceof Map)
                .collect(java.util.stream.Collectors.toList());

    if (sectionEntries.isEmpty()) return raw;

    Map<String, Integer> keyCount = new LinkedHashMap<>();
    for (Map.Entry<String, Object> entry : sectionEntries) {
      Map<String, Object> section = (Map<String, Object>) entry.getValue();
      section.keySet().forEach(k -> keyCount.merge(k, 1, Integer::sum));
    }

    Map<String, Object> result = new LinkedHashMap<>(raw);
    for (Map.Entry<String, Object> entry : sectionEntries) {
      Map<String, Object> section = (Map<String, Object>) entry.getValue();
      section.forEach((k, v) -> {
        if (keyCount.getOrDefault(k, 0) == 1) result.put(k, v);
      });
    }
    return result;
  }

  @Transactional(readOnly = true)
    public PageDataResponse findByGroupIdAndSlug(String groupId, String slug) {
    PageData pageData = pageDataRepository.findByGroupIdAndDataSlug(groupId, slug)
                .orElseThrow(ErrorCode.PAGE_DATA_NOT_FOUND::toException);
    return PageDataResponse.from(pageData);
  }

  @CacheEvict(cacheNames = "contentsData", allEntries = true)
  @Transactional
    public void deleteByGroupId(String groupId) {
    List<PageData> list = pageDataRepository.findByGroupId(groupId);
    if (list.isEmpty()) {
      throw ErrorCode.PAGE_DATA_NOT_FOUND.toException();
    }
    for (PageData pd : list) {
      pageFileService.deleteByDataId(pd.getId());
      pageDataRepository.delete(pd);
    }
  }


  private void applyExclude(List<PageDataResponse> content, String excludeParam) {
    if (excludeParam == null || excludeParam.isBlank()) return;
    Set<String> excludeKeys = new HashSet<>();
    for (String k : excludeParam.split(",")) {
      String trimmed = k.trim();
      if (!trimmed.isEmpty()) excludeKeys.add(trimmed);
    }
    if (excludeKeys.isEmpty()) return;
    for (PageDataResponse r : content) {
      removeExcludedKeys(r.getDataJson(), excludeKeys);
    }
  }

    @SuppressWarnings("unchecked")
  private void removeExcludedKeys(Object node, Set<String> excludeKeys) {
    if (node instanceof Map) {
      Map<String, Object> map = (Map<String, Object>) node;
      if (map.isEmpty()) return;
      for (String k : excludeKeys) map.remove(k);
      for (Object v : map.values()) {
        if (v instanceof Map || v instanceof List) removeExcludedKeys(v, excludeKeys);
      }
    } else if (node instanceof List) {
      for (Object item : (List<Object>) node) {
        if (item instanceof Map || item instanceof List) removeExcludedKeys(item, excludeKeys);
      }
    }
  }

  private void checkPkDuplicate(String slug, List<String> pkKeys,
                                   Map<String, Object> dataJson, Long excludeId, Long siteId) {
    List<String> validKeys = pkKeys.stream()
                .filter(k -> k != null && k.matches("[a-zA-Z0-9_]+"))
                .toList();
    if (validKeys.isEmpty()) {
      return;
    }

    StringBuilder sql = new StringBuilder(
                "SELECT COUNT(*) FROM page_data WHERE data_slug = :slug AND is_deleted = false");
    for (String key : validKeys) {
      sql.append(" AND data_json->>'").append(key).append("' = :pk_").append(key);
    }
    appendSiteSql(sql, siteId);
    if (excludeId != null) {
      sql.append(" AND id != :excludeId");
    }

    Query query = entityManager.createNativeQuery(sql.toString());
    query.setParameter("slug", slug);
    for (String key : validKeys) {
      Object val = dataJson.get(key);
      query.setParameter("pk_" + key, val != null ? val.toString() : "");
    }
    bindSiteParam(query, siteId);
    if (excludeId != null) {
      query.setParameter("excludeId", excludeId);
    }

    long count = ((Number) query.getSingleResult()).longValue();
    if (count > 0) {
      throw ErrorCode.PAGE_DATA_PK_DUPLICATE.toException();
    }
  }

  private void checkValidationRules(String slug, List<Long> ruleIds,
                                       Map<String, Object> dataJson, Long excludeId, Long siteId) {
    List<ValidationRule> rules = validationRuleRepository.findAllById(ruleIds);
    for (ValidationRule rule : rules) {
      if ("unique".equals(rule.getType())) {
        checkUniqueRule(slug, rule, dataJson, excludeId, siteId);
      } else if ("maxCount".equals(rule.getType())) {
        checkMaxCountRule(slug, rule, dataJson, excludeId, siteId);
      }
    }
  }

  private void checkUniqueRule(String slug, ValidationRule rule,
                                  Map<String, Object> dataJson, Long excludeId, Long siteId) {
    List<String> fields = parseCsvList(rule.getFields());
    if (fields.isEmpty()) {
      return;
    }

    StringBuilder sql = new StringBuilder(
                "SELECT COUNT(*) FROM page_data WHERE data_slug = :slug AND is_deleted = false");
    for (int i = 0; i < fields.size(); i++) {
      sql.append(" AND ").append(toJsonPathExpr(fields.get(i))).append(" = :f_").append(i);
    }
    appendConditionSql(sql, rule.getCondition());
    appendSiteSql(sql, siteId);
    if (excludeId != null) {
      sql.append(" AND id != :excludeId");
    }

    Query query = entityManager.createNativeQuery(sql.toString());
    query.setParameter("slug", slug);
    for (int i = 0; i < fields.size(); i++) {
      Object val = extractFieldValue(dataJson, fields.get(i));
      query.setParameter("f_" + i, val != null ? val.toString() : "");
    }
    setConditionParams(query, rule.getCondition());
    bindSiteParam(query, siteId);
    bindTodayIfPresent(query, sql.toString(), siteId);
    if (excludeId != null) {
      query.setParameter("excludeId", excludeId);
    }

    long count = ((Number) query.getSingleResult()).longValue();
    if (count > 0) {
      throw ErrorCode.VALIDATION_RULE_UNIQUE_VIOLATION.toException();
    }
  }

  private void checkMaxCountRule(String slug, ValidationRule rule,
                                    Map<String, Object> dataJson, Long excludeId, Long siteId) {
    if (rule.getMaxCount() == null) {
      return;
    }
    if (!matchesCondition(dataJson, rule.getCondition(), siteId)) {
      return;
    }

    StringBuilder sql = new StringBuilder(
                "SELECT COUNT(*) FROM page_data WHERE data_slug = :slug AND is_deleted = false");
    appendConditionSql(sql, rule.getCondition());
    appendSiteSql(sql, siteId);
    if (excludeId != null) {
      sql.append(" AND id != :excludeId");
    }

    Query query = entityManager.createNativeQuery(sql.toString());
    query.setParameter("slug", slug);
    setConditionParams(query, rule.getCondition());
    bindSiteParam(query, siteId);
    bindTodayIfPresent(query, sql.toString(), siteId);
    if (excludeId != null) {
      query.setParameter("excludeId", excludeId);
    }

    long count = ((Number) query.getSingleResult()).longValue();
    if (count + 1 > rule.getMaxCount()) {
      throw ErrorCode.VALIDATION_RULE_MAX_COUNT_EXCEEDED.toException();
    }
  }

  private List<String> parseCsvList(String csv) {
    if (csv == null || csv.isBlank()) {
      return List.of();
    }
    return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty() && isValidFieldPath(s))
                .toList();
  }

  private List<String> parseInValues(String csv) {
    if (csv == null || csv.isBlank()) {
      return List.of();
    }
    return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
  }

  private void appendConditionSql(StringBuilder sql, String condition) {
    int i = 0;
    for (CondToken t : parseConditionExpr(condition)) {
      String sqlOp = "!=".equals(t.op()) ? "<>" : t.op();
      if (t.isToday()) {
        sql.append(" AND substring(regexp_replace(").append(toJsonPathExpr(t.key()))
                .append(", '[^0-9]', '', 'g'), 1, 8) ").append(sqlOp).append(" :today");
      } else if (!t.key().contains(".")) {
        String fieldKey = t.key();
        String paramName = "cond_" + i;
        sql.append(" AND (data_json->>'").append(fieldKey).append("' ").append(sqlOp).append(" :").append(paramName)
                .append(" OR EXISTS (SELECT 1 FROM jsonb_each(data_json) kv1")
                .append(" WHERE jsonb_typeof(kv1.value) = 'object'")
                .append(" AND (kv1.value->>'").append(fieldKey).append("' ").append(sqlOp).append(" :").append(paramName)
                .append(" OR EXISTS (SELECT 1 FROM jsonb_each(kv1.value) kv2")
                .append(" WHERE jsonb_typeof(kv2.value) = 'object'")
                .append(" AND kv2.value->>'").append(fieldKey).append("' ").append(sqlOp).append(" :").append(paramName).append("))))");
      } else {
        sql.append(" AND ").append(toJsonPathExpr(t.key())).append(" ").append(sqlOp).append(" :cond_").append(i);
      }
      i++;
    }
  }

  private void setConditionParams(Query query, String condition) {
    int i = 0;
    for (CondToken t : parseConditionExpr(condition)) {
      if (!t.isToday()) {
        query.setParameter("cond_" + i, t.value());
      }
      i++;
    }
  }

  private boolean isValidFieldPath(String s) {
    String[] parts = s.split("\\.", -1);
    if (parts.length > 2) {
      return false;
    }
    return Arrays.stream(parts).allMatch(p -> p.matches("[a-zA-Z0-9_]+"));
  }

  private String toJsonPathExpr(String fieldPath) {
    String[] parts = fieldPath.split("\\.", 2);
    if (parts.length == 1) {
      return "data_json->>'" + parts[0] + "'";
    }
    return "data_json->'" + parts[0] + "'->>'" + parts[1] + "'";
  }

  private void appendSiteSql(StringBuilder sql, Long siteId) {
    if (siteId != null) {
      sql.append(" AND (site_id = :siteId OR site_id IS NULL)");
    }
  }

  private void bindSiteParam(Query query, Long siteId) {
    if (siteId != null) {
      query.setParameter("siteId", siteId);
    }
  }

  private ZoneId resolveZone(Long siteId) {
    return siteTimeZoneResolver.resolve(siteId);
  }

  private String resolveTodayParam(Long siteId) {
    return LocalDate.now(resolveZone(siteId)).format(DateTimeFormatter.ofPattern("yyyyMMdd"));
  }

  private String resolveTodayIsoDate(Long siteId) {
    return LocalDate.now(resolveZone(siteId)).format(DateTimeFormatter.ISO_LOCAL_DATE);
  }

  private void bindTodayIfPresent(Query query, String sql, Long siteId) {
    if (sql.contains(":today")) {
      query.setParameter("today", resolveTodayParam(siteId));
    }
  }

  /** BO 관리자가 미게시 콘텐츠를 미리보기 링크로 열람할 때만 게시 게이트를 우회시켜준다(slug+recordId 바인딩된 5분 토큰). */
  private boolean isValidPreviewToken(String previewToken, String slug, Long id) {
    return previewToken != null && jwtTokenProvider.validatePreviewToken(previewToken, slug, String.valueOf(id));
  }

  private String resolveNowParam(Long siteId) {
    return LocalDateTime.now(resolveZone(siteId)).format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
  }

  private void bindNowIfPresent(Query query, String sql, Long siteId) {
    if (sql.contains(":nowValue")) {
      query.setParameter("nowValue", resolveNowParam(siteId));
    }
  }

  private String toNowPaddedExpr(String rawJsonPathExpr) {
    String digits = "regexp_replace(" + rawJsonPathExpr + ", '[^0-9]', '', 'g')";
    return "(CASE WHEN char_length(" + digits + ") = 8 THEN " + digits + " || '000000' ELSE " + digits + " END)";
  }

  /**
   * drs_ 검색필터 / dateRangeStatus 뱃지 공통 SQL 경계값 정규화.
   * 저장값(숫자만 추출)의 자릿수(6/8/12/그외)에 따라 :nowValue(14자리 yyyyMMddHHmmss)와 비교 가능한
   * 14자리 문자열로 패딩한다. isUpper=false면 하한(from), isUpper=true면 상한(to).
   * 값이 비어 있으면 NULL(무매칭 — WHERE 조건에서 자동으로 걸러짐).
   */
  private String toRangeBoundExpr(String rawJsonPathExpr, boolean isUpper) {
    String digits = "regexp_replace(" + rawJsonPathExpr + ", '[^0-9]', '', 'g')";
    String len6Pad = isUpper ? "'31235959'" : "'01000000'";
    String len8Pad = isUpper ? "'235959'" : "'000000'";
    String len12Pad = isUpper ? "'59'" : "'00'";
    String padChar = isUpper ? "'9'" : "'0'";
    return "(CASE"
        + " WHEN " + digits + " = '' THEN NULL"
        + " WHEN char_length(" + digits + ") = 6 THEN " + digits + " || " + len6Pad
        + " WHEN char_length(" + digits + ") = 8 THEN " + digits + " || " + len8Pad
        + " WHEN char_length(" + digits + ") = 12 THEN " + digits + " || " + len12Pad
        + " ELSE left(rpad(" + digits + ", 14, " + padChar + "), 14)"
        + " END)";
  }

  /** toRangeBoundExpr의 Java 측 동등 구현(응답 후처리용 뱃지 계산에서 사용) */
  private static String padRangeBound(String raw, boolean isUpper) {
    if (raw == null) return null;
    String digits = raw.replaceAll("[^0-9]", "");
    if (digits.isEmpty()) return null;
    return switch (digits.length()) {
      case 6 -> digits + (isUpper ? "31235959" : "01000000");
      case 8 -> digits + (isUpper ? "235959" : "000000");
      case 12 -> digits + (isUpper ? "59" : "00");
      default -> digits.length() >= 14
          ? digits.substring(0, 14)
          : digits + (isUpper ? "9" : "0").repeat(14 - digits.length());
    };
  }

  /** from/to(14자리 패딩값)와 now(14자리)를 비교해 dateRangeStatus 뱃지 값을 판정한다. */
  private static String resolveRangeStatus(String from, String to, String now) {
    if (from != null && now.compareTo(from) < 0) return "before";
    if (from != null && to != null && now.compareTo(from) >= 0 && now.compareTo(to) <= 0) return "in_range";
    if (to != null && now.compareTo(to) > 0) return "after";
    return null;
  }

  /**
   * dataJson에서 "{rangeKey}_from" / "{rangeKey}_to" 원본값을 찾는다.
   * root에 있으면 root 우선, 없으면 1-depth 중첩 섹션에서 탐색한다(2-depth까지는 가지 않음 — drs_ 검색 SQL과 동일한 범위).
   */
  @SuppressWarnings("unchecked")
  private String[] findRangeBounds(Map<String, Object> dataJson, String rangeKey) {
    if (dataJson == null) return new String[]{null, null};
    String fromKey = rangeKey + "_from";
    String toKey = rangeKey + "_to";

    Object rootFrom = dataJson.get(fromKey);
    Object rootTo = dataJson.get(toKey);
    if (rootFrom != null || rootTo != null) {
      return new String[]{
          rootFrom != null ? rootFrom.toString() : null,
          rootTo != null ? rootTo.toString() : null
      };
    }

    for (Object value : dataJson.values()) {
      if (!(value instanceof Map)) continue;
      Map<String, Object> section = (Map<String, Object>) value;
      Object sectionFrom = section.get(fromKey);
      Object sectionTo = section.get(toKey);
      if (sectionFrom != null || sectionTo != null) {
        return new String[]{
            sectionFrom != null ? sectionFrom.toString() : null,
            sectionTo != null ? sectionTo.toString() : null
        };
      }
    }
    return new String[]{null, null};
  }

  /**
   * 목록 응답에 dateRangeStatus 뱃지를 서버가 계산해 얹어준다(opt-in, FE 요청 파라미터 drsKeys가 있을 때만 동작).
   * drsKeys는 콤마로 구분된 rangeKey 목록이며, 각 rangeKey마다 dataJson에 "_drs_{rangeKey}" 키로
   * "before"/"in_range"/"after"/null 값을 추가한다. applyFetch와는 무관한 별도 후처리 단계다.
   */
  private List<PageDataResponse> applyDateRangeStatus(List<PageDataResponse> content, String drsKeysParam, Long siteId) {
    if (drsKeysParam == null || drsKeysParam.isBlank()) return content;

    List<String> rangeKeys = Arrays.stream(drsKeysParam.split(","))
        .map(String::trim)
        .filter(k -> k.matches("[a-zA-Z0-9_]+"))
        .toList();
    if (rangeKeys.isEmpty()) return content;

    String now = resolveNowParam(siteId);
    List<PageDataResponse> result = new ArrayList<>(content.size());
    for (PageDataResponse item : content) {
      Map<String, Object> enriched = new LinkedHashMap<>(item.getDataJson());
      for (String rangeKey : rangeKeys) {
        String[] bounds = findRangeBounds(item.getDataJson(), rangeKey);
        String from = padRangeBound(bounds[0], false);
        String to = padRangeBound(bounds[1], true);
        enriched.put("_drs_" + rangeKey, resolveRangeStatus(from, to, now));
      }
      result.add(item.withDataJson(enriched));
    }
    return result;
  }

  @SuppressWarnings("unchecked")
  private List<PageDataResponse> applyRegistrationState(String slug, List<PageDataResponse> content, Long siteId, boolean enforcePublishGate) {
    if (!enforcePublishGate || !"currDtlMgmt-data".equals(slug)) return content;

    LocalDate today = LocalDate.now(resolveZone(siteId));
    List<PageDataResponse> result = new ArrayList<>(content.size());
    for (PageDataResponse item : content) {
      Map<String, Object> enriched = new LinkedHashMap<>(item.getDataJson());
      Object detail2Obj = enriched.get("curriculum_detail2");
      Map<String, Object> detail2 = detail2Obj instanceof Map ? (Map<String, Object>) detail2Obj : Collections.emptyMap();
      Object fromVal = detail2.get("register_period_from");
      Object toVal = detail2.get("register_period_to");
      String registerPeriodFrom = fromVal != null ? fromVal.toString() : null;
      String registerPeriodTo = toVal != null ? toVal.toString() : null;

      Integer daysLeft = registrationDaysLeft(registerPeriodTo, today);
      Boolean notYetOpen = registrationNotYetOpen(registerPeriodFrom, today);

      enriched.put("_registrationDaysLeft", daysLeft);
      enriched.put("_registrationClosed", daysLeft != null && daysLeft < 0);
      enriched.put("_registrationClosesToday", daysLeft != null && daysLeft == 0);
      enriched.put("_registrationNotYetOpen", notYetOpen);

      result.add(item.withDataJson(enriched));
    }
    return result;
  }

  private static LocalDate parseYmdOrNull(String value) {
    if (value == null || value.length() < 10) return null;
    try {
      return LocalDate.parse(value.substring(0, 10));
    } catch (Exception e) {
      return null;
    }
  }

  private static Integer registrationDaysLeft(String registerPeriodTo, LocalDate today) {
    LocalDate to = parseYmdOrNull(registerPeriodTo);
    if (to == null) return null;
    return (int) ChronoUnit.DAYS.between(today, to);
  }

  private static Boolean registrationNotYetOpen(String registerPeriodFrom, LocalDate today) {
    LocalDate from = parseYmdOrNull(registerPeriodFrom);
    if (from == null) return null;
    return today.isBefore(from);
  }

  private boolean matchesCondition(Map<String, Object> dataJson, String condition, Long siteId) {
    String today = LocalDate.now(resolveZone(siteId)).format(DateTimeFormatter.ofPattern("yyyyMMdd"));
    for (CondToken t : parseConditionExpr(condition)) {
      Object val = extractFieldValue(dataJson, t.key());
      String strVal = val != null ? val.toString() : "";
      String left;
      String right;
      if (t.isToday()) {
        String digitsOnly = strVal.replaceAll("[^0-9]", "");
        left = digitsOnly.length() >= 8 ? digitsOnly.substring(0, 8) : digitsOnly;
        right = today;
      } else {
        left = strVal;
        right = t.value();
      }
      if (!compareOp(left, t.op(), right)) {
        return false;
      }
    }
    return true;
  }

  private boolean compareOp(String left, String op, String right) {
    return switch (op) {
      case "=" -> left.equals(right);
      case "!=" -> !left.equals(right);
      case "<" -> left.compareTo(right) < 0;
      case ">" -> left.compareTo(right) > 0;
      case "<=" -> left.compareTo(right) <= 0;
      case ">=" -> left.compareTo(right) >= 0;
      default -> false;
    };
  }

  @SuppressWarnings("unchecked")
  private Object extractFieldValue(Map<String, Object> dataJson, String fieldPath) {
    String[] parts = fieldPath.split("\\.", 2);
    if (parts.length == 1) {
      return dataJson.get(parts[0]);
    }
    Object nested = dataJson.get(parts[0]);
    if (nested instanceof Map) {
      return ((Map<String, Object>) nested).get(parts[1]);
    }
    return null;
  }

  private String getCurrentUserId() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
      return null;
    }
    String email = auth.getName();
    return adminRepository.findByEmail(email)
        .map(u -> String.valueOf(u.getId()))
        .orElse(null);
  }

  private String serializeDataJson(Map<String, Object> dataMap) {
    try {
      return objectMapper.writeValueAsString(dataMap);
    } catch (Exception e) {
      log.error("dataJson 직렬화 실패: {}", e.getMessage());
      return "{}";
    }
  }

  private PageDataResponse mapRowToResponse(Object[] row, Map<Long, String> userNameMap) {
    Map<String, Object> dataMap = Collections.emptyMap();
    try {
      if (row[2] != null) {
        dataMap = objectMapper.readValue(row[2].toString(),
          new com.fasterxml.jackson.core.type.TypeReference<>() {
          });
      }
    } catch (Exception e) {
      log.warn("dataJson 파싱 실패: {}", e.getMessage());
    }

    return PageDataResponse.builder()
                .id(((Number) row[0]).longValue())
                .templateSlug((String) row[1])
                .dataJson(dataMap)
                .groupId((String) row[3])
                .createdBy(resolveUserName((String) row[4], userNameMap))
                .createdAt(row[5] != null ? toLocalDateTime(row[5]) : null)
                .updatedBy(resolveUserName((String) row[6], userNameMap))
                .updatedAt(row[7] != null ? toLocalDateTime(row[7]) : null)
                .count(row[8] != null ? ((Number) row[8]).longValue() : null)
                .build();
  }

  private Map<Long, String> buildUserNameMap(List<Object[]> rows, int... colIndexes) {
    Set<Long> ids = new HashSet<>();
    for (Object[] row : rows) {
      for (int idx : colIndexes) {
        if (row[idx] == null) continue;
        try { ids.add(Long.parseLong(row[idx].toString())); } catch (NumberFormatException ignored) {}
      }
    }
    if (ids.isEmpty()) return Collections.emptyMap();
    return adminRepository.findAllById(ids).stream()
        .collect(java.util.stream.Collectors.toMap(AdminUser::getId, AdminUser::getName));
  }

  private String resolveUserName(String idStr) {
    if (idStr == null) return null;
    try {
      Long id = Long.parseLong(idStr);
      return adminRepository.findById(id).map(AdminUser::getName).orElse(idStr);
    } catch (NumberFormatException e) {
      return idStr;
    }
  }

  private String resolveUserName(String idStr, Map<Long, String> userNameMap) {
    if (idStr == null) return null;
    try {
      Long id = Long.parseLong(idStr);
      return userNameMap.getOrDefault(id, idStr);
    } catch (NumberFormatException e) {
      return idStr;
    }
  }

  private java.time.LocalDateTime toLocalDateTime(Object obj) {
    if (obj == null) return null;
    log.debug("createdAt 실제 타입: {}, 값: {}", obj.getClass().getName(), obj);
    if (obj instanceof java.time.LocalDateTime ldt) return ldt;
    if (obj instanceof java.sql.Timestamp ts) return ts.toLocalDateTime();
    if (obj instanceof java.time.OffsetDateTime odt) return odt.toLocalDateTime();
    if (obj instanceof java.time.ZonedDateTime zdt) return zdt.toLocalDateTime();
    if (obj instanceof java.time.Instant instant) return java.time.LocalDateTime.ofInstant(instant, java.time.ZoneOffset.UTC);
    try { return java.time.LocalDateTime.parse(obj.toString()); } catch (Exception ignored) {}
    return null;
  }

  private void appendWhereConditions(StringBuilder whereClause, Map<String, String> searchParams) {
    searchParams.forEach((key, value) -> {
      if (key.equals("filterExpr")) {
        appendConditionSql(whereClause, value);
        return;
      }

      if (key.startsWith("drs_")) {
        String rangeKey = key.substring(4);
        String fromPart, toPart;
        if (rangeKey.contains(".")) {
          String[] segs = rangeKey.split("\\.");
          if (!isValidSegments(segs)) return;
          String[] fromSegs = segs.clone(); fromSegs[fromSegs.length - 1] = fromSegs[fromSegs.length - 1] + "_from";
          String[] toSegs   = segs.clone(); toSegs[toSegs.length - 1]     = toSegs[toSegs.length - 1]     + "_to";
          fromPart = buildJsonPath(fromSegs);
          toPart   = buildJsonPath(toSegs);
          String fromBound = toRangeBoundExpr(fromPart, false);
          String toBound   = toRangeBoundExpr(toPart, true);
          String now       = ":nowValue";
          switch (value) {
            case "before":
              whereClause.append(" AND ").append(fromBound).append(" > ").append(now);
              break;
            case "in_range":
              whereClause.append(" AND ").append(fromBound).append(" <= ").append(now)
                         .append(" AND ").append(toBound).append(" >= ").append(now);
              break;
            case "after":
              whereClause.append(" AND ").append(toBound).append(" < ").append(now);
              break;
            default: break;
          }
        } else {
          if (!rangeKey.matches("[a-zA-Z0-9_]+")) return;
          String fromRoot   = toRangeBoundExpr("data_json->>'" + rangeKey + "_from'", false);
          String toRoot     = toRangeBoundExpr("data_json->>'" + rangeKey + "_to'", true);
          String fromNested = toRangeBoundExpr("kv.value->>'" + rangeKey + "_from'", false);
          String toNested   = toRangeBoundExpr("kv.value->>'" + rangeKey + "_to'", true);
          String now        = ":nowValue";
          String nested     = " OR EXISTS (SELECT 1 FROM jsonb_each(data_json) kv WHERE jsonb_typeof(kv.value) = 'object' AND ";
          switch (value) {
            case "before":
              whereClause.append(" AND (")
                  .append(fromRoot).append(" > ").append(now)
                  .append(nested).append(fromNested).append(" > ").append(now).append(")")
                  .append(")");
              break;
            case "in_range":
              whereClause.append(" AND (")
                  .append(fromRoot).append(" <= ").append(now).append(" AND ").append(toRoot).append(" >= ").append(now)
                  .append(nested)
                  .append(fromNested).append(" <= ").append(now).append(" AND ").append(toNested).append(" >= ").append(now).append(")")
                  .append(")");
              break;
            case "after":
              whereClause.append(" AND (")
                  .append(toRoot).append(" < ").append(now)
                  .append(nested).append(toNested).append(" < ").append(now).append(")")
                  .append(")");
              break;
            default: break;
          }
        }
        return;
      }

      if (key.startsWith("condval_")) return;

      if (key.startsWith("month_")) {
        String fieldKey = key.substring(6);
        if (!fieldKey.matches("[a-zA-Z0-9_]+")) return;
        if (!value.matches("0[1-9]|1[0-2]")) return;
        String paramName = "p_" + key;
        String topMonth = "substring(regexp_replace(data_json->>'" + fieldKey + "', '[^0-9]', '', 'g'), 5, 2)";
        String nestedMonth = "substring(regexp_replace(kv.value->>'" + fieldKey + "', '[^0-9]', '', 'g'), 5, 2)";
        whereClause.append(" AND (").append(topMonth).append(" = :").append(paramName)
            .append(" OR EXISTS (SELECT 1 FROM jsonb_each(data_json) kv")
            .append(" WHERE jsonb_typeof(kv.value) = 'object'")
            .append(" AND ").append(nestedMonth).append(" = :").append(paramName).append("))");
        return;
      }

      if (key.startsWith("year_")) {
        String fieldKey = key.substring(5);
        if (!fieldKey.matches("[a-zA-Z0-9_]+")) return;
        if (!value.matches("[0-9]{4}")) return;
        String paramName = "p_" + key;
        String topYear = "substring(regexp_replace(data_json->>'" + fieldKey + "', '[^0-9]', '', 'g'), 1, 4)";
        String nestedYear = "substring(regexp_replace(kv.value->>'" + fieldKey + "', '[^0-9]', '', 'g'), 1, 4)";
        whereClause.append(" AND (").append(topYear).append(" = :").append(paramName)
            .append(" OR EXISTS (SELECT 1 FROM jsonb_each(data_json) kv")
            .append(" WHERE jsonb_typeof(kv.value) = 'object'")
            .append(" AND ").append(nestedYear).append(" = :").append(paramName).append("))");
        return;
      }

      if (key.startsWith("has_markets_")) {
        String fieldKey = key.substring("has_markets_".length());
        if (!fieldKey.matches("[a-zA-Z0-9_]+")) return;
        if (!value.matches("[0-9]{3}")) return;
        String paramName = "p_" + key;
        String topLike = "(',' || COALESCE(data_json->>'" + fieldKey + "','') || ',') LIKE :" + paramName;
        String nestedLike = "(',' || COALESCE(kv.value->>'" + fieldKey + "','') || ',') LIKE :" + paramName;
        whereClause.append(" AND (").append(topLike)
            .append(" OR EXISTS (SELECT 1 FROM jsonb_each(data_json) kv")
            .append(" WHERE jsonb_typeof(kv.value) = 'object'")
            .append(" AND ").append(nestedLike).append("))");
        return;
      }

      if (key.startsWith("condexpr_")) {
        String fk = key.substring("condexpr_".length());
        if (!fk.matches("[a-zA-Z0-9_]+")) return;
        String selectedVal = searchParams.get("condval_" + fk);
        if (selectedVal == null) return;
        String[] ternary = splitTernaryExpr(value);
        if (ternary == null) return;
        boolean matched;
        if (selectedVal.equals(ternary[1])) matched = true;
        else if (selectedVal.equals(ternary[2])) matched = false;
        else return;

        List<CondToken> tokens = parseConditionExpr(ternary[0]);
        if (tokens.isEmpty()) return;

        String condExpr = buildCondTokenSql(tokens, "p_cond_" + fk);
        if (matched) {
          whereClause.append(" AND ").append(condExpr);
        } else {
          whereClause.append(" AND NOT COALESCE(").append(condExpr).append(", FALSE)");
        }
        return;
      }

      if (key.startsWith("eq_")) {
        String fieldKey = key.substring(3);
        if (fieldKey.contains(".")) {
          String[] segments = fieldKey.split("\\.");
          if (!isValidSegments(segments)) return;
          String paramName = "p_" + key.replace(".", "_");
          whereClause.append(" AND ").append(buildJsonPath(segments)).append(" = :").append(paramName);
        } else {
          if (!fieldKey.matches("[a-zA-Z0-9_]+")) return;
          whereClause.append(" AND (data_json->>'").append(fieldKey).append("' = :p_").append(key)
              .append(" OR EXISTS (SELECT 1 FROM jsonb_each(data_json) kv1")
              .append(" WHERE jsonb_typeof(kv1.value) = 'object'")
              .append(" AND (kv1.value->>'").append(fieldKey).append("' = :p_").append(key)
              .append(" OR EXISTS (SELECT 1 FROM jsonb_each(kv1.value) kv2")
              .append(" WHERE jsonb_typeof(kv2.value) = 'object'")
              .append(" AND kv2.value->>'").append(fieldKey).append("' = :p_").append(key).append("))))");
        }
        return;
      }

      if (key.startsWith("in_")) {
        String fieldKey = key.substring(3);
        List<String> inValues = parseInValues(value);
        if (inValues.isEmpty()) return;
        String baseParam = "p_" + key.replace(".", "_");
        List<String> placeholders = new ArrayList<>();
        for (int i = 0; i < inValues.size(); i++) {
          placeholders.add(":" + baseParam + "_" + i);
        }
        String inList = String.join(", ", placeholders);
        if (fieldKey.contains(".")) {
          String[] segments = fieldKey.split("\\.");
          if (!isValidSegments(segments)) return;
          whereClause.append(" AND ").append(buildJsonPath(segments)).append(" IN (").append(inList).append(")");
        } else {
          if (!fieldKey.matches("[a-zA-Z0-9_]+")) return;
          whereClause.append(" AND (data_json->>'").append(fieldKey).append("' IN (").append(inList).append(")")
              .append(" OR EXISTS (SELECT 1 FROM jsonb_each(data_json) kv1")
              .append(" WHERE jsonb_typeof(kv1.value) = 'object'")
              .append(" AND (kv1.value->>'").append(fieldKey).append("' IN (").append(inList).append(")")
              .append(" OR EXISTS (SELECT 1 FROM jsonb_each(kv1.value) kv2")
              .append(" WHERE jsonb_typeof(kv2.value) = 'object'")
              .append(" AND kv2.value->>'").append(fieldKey).append("' IN (").append(inList).append(")))))");
        }
        return;
      }

      if (key.startsWith("ne_")) {
        String fieldKey = key.substring(3);
        if (!fieldKey.matches("[a-zA-Z0-9_]+")) return;
        whereClause.append(" AND data_json->>'").append(fieldKey).append("' IS DISTINCT FROM :p_").append(key);
        return;
      }

      if (key.contains(".")) {
        String[] segments = key.split("\\.");
        if (!isValidSegments(segments)) return;
        String paramName = "p_" + key.replace(".", "_");
        String jsonPath  = buildJsonPath(segments);
        if (value.contains("~")) {
          String[] parts = value.split("~", 2);
          String start = parts[0].trim();
          String end   = parts.length > 1 ? parts[1].trim() : "";
          if (!start.isEmpty()) whereClause.append(" AND ").append(jsonPath).append(" >= :").append(paramName).append("_start");
          if (!end.isEmpty())   whereClause.append(" AND ").append(jsonPath).append(" <= :").append(paramName).append("_end");
        } else {
          whereClause.append(" AND ").append(jsonPath).append(" ILIKE :").append(paramName);
        }
        return;
      }

      if (key.endsWith("_from")) {
        if (!key.matches("[a-zA-Z0-9_]+")) return;
        String paramName = "p_" + key;
        String baseKey = key.substring(0, key.length() - 5);
        String auditCol = toAuditDateColumn(baseKey);
        if (auditCol != null) {
          whereClause.append(" AND ").append(auditCol).append(" >= CAST(:").append(paramName).append(" AS timestamptz)");
        } else {
          whereClause.append(" AND (data_json->>'").append(key).append("' >= :").append(paramName)
              .append(" OR EXISTS (SELECT 1 FROM jsonb_each(data_json) kv")
              .append(" WHERE jsonb_typeof(kv.value) = 'object'")
              .append(" AND kv.value->>'").append(key).append("' >= :").append(paramName).append("))");
        }
        return;
      }

      if (key.endsWith("_to")) {
        if (!key.matches("[a-zA-Z0-9_]+")) return;
        String paramName = "p_" + key;
        String baseKey = key.substring(0, key.length() - 3);
        String auditCol = toAuditDateColumn(baseKey);
        if (auditCol != null) {
          whereClause.append(" AND ").append(auditCol).append(" <= CAST(:").append(paramName).append(" AS timestamptz)");
        } else {
          whereClause.append(" AND (data_json->>'").append(key).append("' <= :").append(paramName)
              .append(" OR EXISTS (SELECT 1 FROM jsonb_each(data_json) kv")
              .append(" WHERE jsonb_typeof(kv.value) = 'object'")
              .append(" AND kv.value->>'").append(key).append("' <= :").append(paramName).append("))");
        }
        return;
      }

      if (key.endsWith("_gte")) {
        if (!key.matches("[a-zA-Z0-9_]+")) return;
        String fieldKey = key.substring(0, key.length() - 4);
        String paramName = "p_" + key;
        String auditCol = toAuditDateColumn(fieldKey);
        if (auditCol != null) {
          whereClause.append(" AND ").append(auditCol).append(" >= CAST(:").append(paramName).append(" AS timestamptz)");
        } else {
          whereClause.append(" AND (data_json->>'").append(fieldKey).append("' >= :").append(paramName)
              .append(" OR EXISTS (SELECT 1 FROM jsonb_each(data_json) kv")
              .append(" WHERE jsonb_typeof(kv.value) = 'object'")
              .append(" AND kv.value->>'").append(fieldKey).append("' >= :").append(paramName).append("))");
        }
        return;
      }

      if (key.endsWith("_lte")) {
        if (!key.matches("[a-zA-Z0-9_]+")) return;
        String fieldKey = key.substring(0, key.length() - 4);
        String paramName = "p_" + key;
        String auditCol = toAuditDateColumn(fieldKey);
        if (auditCol != null) {
          whereClause.append(" AND ").append(auditCol).append(" <= CAST(:").append(paramName).append(" AS timestamptz)");
        } else {
          whereClause.append(" AND (data_json->>'").append(fieldKey).append("' <= :").append(paramName)
              .append(" OR EXISTS (SELECT 1 FROM jsonb_each(data_json) kv")
              .append(" WHERE jsonb_typeof(kv.value) = 'object'")
              .append(" AND kv.value->>'").append(fieldKey).append("' <= :").append(paramName).append("))");
        }
        return;
      }

      if (key.contains("|")) {
        String[] fields = key.split("\\|");
        if (!Arrays.stream(fields).allMatch(f -> f.matches("[a-zA-Z0-9_]+"))) return;
        String paramName = "p_or_" + key.replace("|", "__");
        String topLevel = Arrays.stream(fields)
            .map(f -> "data_json->>'" + f + "' ILIKE :" + paramName)
            .collect(java.util.stream.Collectors.joining(" OR "));
        String nested = Arrays.stream(fields)
            .map(f -> "kv.value->>'" + f + "' ILIKE :" + paramName)
            .collect(java.util.stream.Collectors.joining(" OR "));
        whereClause.append(" AND (")
            .append(topLevel)
            .append(" OR EXISTS (SELECT 1 FROM jsonb_each(data_json) kv")
            .append(" WHERE jsonb_typeof(kv.value) = 'object'")
            .append(" AND (").append(nested).append(")))");
        return;
      }

      if (!key.matches("[a-zA-Z0-9_]+")) return;
      if (value.contains("~")) {
        String[] parts = value.split("~", 2);
        String start = parts[0].trim();
        String end   = parts.length > 1 ? parts[1].trim() : "";
        if (!start.isEmpty()) whereClause.append(" AND data_json->>'").append(key).append("' >= :p_").append(key).append("_start");
        if (!end.isEmpty())   whereClause.append(" AND data_json->>'").append(key).append("' <= :p_").append(key).append("_end");
      } else {
        whereClause.append(" AND (data_json->>'").append(key).append("' ILIKE :p_").append(key)
            .append(" OR EXISTS (SELECT 1 FROM jsonb_each(data_json) kv")
            .append(" WHERE jsonb_typeof(kv.value) = 'object'")
            .append(" AND kv.value->>'").append(key).append("' ILIKE :p_").append(key).append("))");
      }
    });
  }

  private void appendWhereConditionsDatetime(StringBuilder whereClause, Map<String, String> searchParams) {
    searchParams.forEach((key, value) -> {
      if (key.startsWith("drs_")) {
        String rangeKey = key.substring(4);
        if (rangeKey.contains(".")) {
          String[] segs = rangeKey.split("\\.");
          if (!isValidSegments(segs)) return;
          String[] fromSegs = segs.clone(); fromSegs[fromSegs.length - 1] = fromSegs[fromSegs.length - 1] + "_from";
          String[] toSegs   = segs.clone(); toSegs[toSegs.length - 1]     = toSegs[toSegs.length - 1]     + "_to";
          String fromPart = buildJsonPath(fromSegs);
          String toPart   = buildJsonPath(toSegs);
          String fromCmp = toRangeBoundExpr(fromPart, false);
          String toCmp   = toRangeBoundExpr(toPart, true);
          String now = ":nowValue";
          switch (value) {
            case "before":
              whereClause.append(" AND ").append(fromCmp).append(" > ").append(now);
              break;
            case "in_range":
              whereClause.append(" AND ").append(fromCmp).append(" <= ").append(now)
                         .append(" AND ").append(toCmp).append(" >= ").append(now);
              break;
            case "after":
              whereClause.append(" AND ").append(toCmp).append(" < ").append(now);
              break;
            default: break;
          }
        } else {
          if (!rangeKey.matches("[a-zA-Z0-9_]+")) return;
          String fromRootCmp   = toRangeBoundExpr("data_json->>'" + rangeKey + "_from'", false);
          String toRootCmp     = toRangeBoundExpr("data_json->>'" + rangeKey + "_to'", true);
          String fromNestedCmp = toRangeBoundExpr("kv.value->>'" + rangeKey + "_from'", false);
          String toNestedCmp   = toRangeBoundExpr("kv.value->>'" + rangeKey + "_to'", true);
          String now    = ":nowValue";
          String nested = " OR EXISTS (SELECT 1 FROM jsonb_each(data_json) kv WHERE jsonb_typeof(kv.value) = 'object' AND ";
          switch (value) {
            case "before":
              whereClause.append(" AND (")
                  .append(fromRootCmp).append(" > ").append(now)
                  .append(nested).append(fromNestedCmp).append(" > ").append(now).append(")")
                  .append(")");
              break;
            case "in_range":
              whereClause.append(" AND (")
                  .append(fromRootCmp).append(" <= ").append(now).append(" AND ").append(toRootCmp).append(" >= ").append(now)
                  .append(nested)
                  .append(fromNestedCmp).append(" <= ").append(now).append(" AND ").append(toNestedCmp).append(" >= ").append(now).append(")")
                  .append(")");
              break;
            case "after":
              whereClause.append(" AND (")
                  .append(toRootCmp).append(" < ").append(now)
                  .append(nested).append(toNestedCmp).append(" < ").append(now).append(")")
                  .append(")");
              break;
            default: break;
          }
        }
        return;
      }

      if (key.startsWith("condval_")) return;

      if (key.startsWith("month_")) {
        String fieldKey = key.substring(6);
        if (!fieldKey.matches("[a-zA-Z0-9_]+")) return;
        if (!value.matches("0[1-9]|1[0-2]")) return;
        String paramName = "p_" + key;
        String topMonth = "substring(regexp_replace(data_json->>'" + fieldKey + "', '[^0-9]', '', 'g'), 5, 2)";
        String nestedMonth = "substring(regexp_replace(kv.value->>'" + fieldKey + "', '[^0-9]', '', 'g'), 5, 2)";
        whereClause.append(" AND (").append(topMonth).append(" = :").append(paramName)
            .append(" OR EXISTS (SELECT 1 FROM jsonb_each(data_json) kv")
            .append(" WHERE jsonb_typeof(kv.value) = 'object'")
            .append(" AND ").append(nestedMonth).append(" = :").append(paramName).append("))");
        return;
      }

      if (key.startsWith("year_")) {
        String fieldKey = key.substring(5);
        if (!fieldKey.matches("[a-zA-Z0-9_]+")) return;
        if (!value.matches("[0-9]{4}")) return;
        String paramName = "p_" + key;
        String topYear = "substring(regexp_replace(data_json->>'" + fieldKey + "', '[^0-9]', '', 'g'), 1, 4)";
        String nestedYear = "substring(regexp_replace(kv.value->>'" + fieldKey + "', '[^0-9]', '', 'g'), 1, 4)";
        whereClause.append(" AND (").append(topYear).append(" = :").append(paramName)
            .append(" OR EXISTS (SELECT 1 FROM jsonb_each(data_json) kv")
            .append(" WHERE jsonb_typeof(kv.value) = 'object'")
            .append(" AND ").append(nestedYear).append(" = :").append(paramName).append("))");
        return;
      }

      if (key.startsWith("has_markets_")) {
        String fieldKey = key.substring("has_markets_".length());
        if (!fieldKey.matches("[a-zA-Z0-9_]+")) return;
        if (!value.matches("[0-9]{3}")) return;
        String paramName = "p_" + key;
        String topLike = "(',' || COALESCE(data_json->>'" + fieldKey + "','') || ',') LIKE :" + paramName;
        String nestedLike = "(',' || COALESCE(kv.value->>'" + fieldKey + "','') || ',') LIKE :" + paramName;
        whereClause.append(" AND (").append(topLike)
            .append(" OR EXISTS (SELECT 1 FROM jsonb_each(data_json) kv")
            .append(" WHERE jsonb_typeof(kv.value) = 'object'")
            .append(" AND ").append(nestedLike).append("))");
        return;
      }

      if (key.equals("filterExpr")) {
        appendConditionSql(whereClause, value);
        return;
      }

      if (key.startsWith("condexpr_")) {
        String fk = key.substring("condexpr_".length());
        if (!fk.matches("[a-zA-Z0-9_]+")) return;
        String selectedVal = searchParams.get("condval_" + fk);
        if (selectedVal == null) return;
        String[] ternary = splitTernaryExpr(value);
        if (ternary == null) return;
        boolean matched;
        if (selectedVal.equals(ternary[1])) matched = true;
        else if (selectedVal.equals(ternary[2])) matched = false;
        else return;

        List<CondToken> tokens = parseConditionExpr(ternary[0]);
        if (tokens.isEmpty()) return;

        String condExpr = buildCondTokenSql(tokens, "p_cond_" + fk);
        if (matched) {
          whereClause.append(" AND ").append(condExpr);
        } else {
          whereClause.append(" AND NOT COALESCE(").append(condExpr).append(", FALSE)");
        }
        return;
      }

      if (key.startsWith("eq_")) {
        String fieldKey = key.substring(3);
        if (fieldKey.contains(".")) {
          String[] segments = fieldKey.split("\\.");
          if (!isValidSegments(segments)) return;
          String paramName = "p_" + key.replace(".", "_");
          whereClause.append(" AND ").append(buildJsonPath(segments)).append(" = :").append(paramName);
        } else {
          if (!fieldKey.matches("[a-zA-Z0-9_]+")) return;
          whereClause.append(" AND (data_json->>'").append(fieldKey).append("' = :p_").append(key)
              .append(" OR EXISTS (SELECT 1 FROM jsonb_each(data_json) kv1")
              .append(" WHERE jsonb_typeof(kv1.value) = 'object'")
              .append(" AND (kv1.value->>'").append(fieldKey).append("' = :p_").append(key)
              .append(" OR EXISTS (SELECT 1 FROM jsonb_each(kv1.value) kv2")
              .append(" WHERE jsonb_typeof(kv2.value) = 'object'")
              .append(" AND kv2.value->>'").append(fieldKey).append("' = :p_").append(key).append("))))");
        }
        return;
      }

      if (key.startsWith("ne_")) {
        String fieldKey = key.substring(3);
        if (!fieldKey.matches("[a-zA-Z0-9_]+")) return;
        whereClause.append(" AND data_json->>'").append(fieldKey).append("' IS DISTINCT FROM :p_").append(key);
        return;
      }

      if (key.contains(".")) {
        String[] segments = key.split("\\.");
        if (!isValidSegments(segments)) return;
        String paramName = "p_" + key.replace(".", "_");
        String jsonPath  = buildJsonPath(segments);
        if (value.contains("~")) {
          String[] parts = value.split("~", 2);
          String start = parts[0].trim();
          String end   = parts.length > 1 ? parts[1].trim() : "";
          if (!start.isEmpty()) whereClause.append(" AND ").append(jsonPath).append(" >= :").append(paramName).append("_start");
          if (!end.isEmpty())   whereClause.append(" AND ").append(jsonPath).append(" <= :").append(paramName).append("_end");
        } else {
          whereClause.append(" AND ").append(jsonPath).append(" ILIKE :").append(paramName);
        }
        return;
      }

      if (key.endsWith("_from")) {
        if (!key.matches("[a-zA-Z0-9_]+")) return;
        String paramName = "p_" + key;
        String baseKey = key.substring(0, key.length() - 5);
        String auditCol = toAuditDateColumn(baseKey);
        if (auditCol != null) {
          whereClause.append(" AND ").append(auditCol).append(" >= CAST(:").append(paramName).append(" AS timestamptz)");
        } else {
          whereClause.append(" AND (data_json->>'").append(key).append("' >= :").append(paramName)
              .append(" OR EXISTS (SELECT 1 FROM jsonb_each(data_json) kv")
              .append(" WHERE jsonb_typeof(kv.value) = 'object'")
              .append(" AND kv.value->>'").append(key).append("' >= :").append(paramName).append("))");
        }
        return;
      }

      if (key.endsWith("_to")) {
        if (!key.matches("[a-zA-Z0-9_]+")) return;
        String paramName = "p_" + key;
        String baseKey = key.substring(0, key.length() - 3);
        String auditCol = toAuditDateColumn(baseKey);
        if (auditCol != null) {
          whereClause.append(" AND ").append(auditCol).append(" <= CAST(:").append(paramName).append(" AS timestamptz)");
        } else {
          whereClause.append(" AND (data_json->>'").append(key).append("' <= :").append(paramName)
              .append(" OR EXISTS (SELECT 1 FROM jsonb_each(data_json) kv")
              .append(" WHERE jsonb_typeof(kv.value) = 'object'")
              .append(" AND kv.value->>'").append(key).append("' <= :").append(paramName).append("))");
        }
        return;
      }

      if (key.endsWith("_gte")) {
        if (!key.matches("[a-zA-Z0-9_]+")) return;
        String fieldKey = key.substring(0, key.length() - 4);
        String paramName = "p_" + key;
        String auditCol = toAuditDateColumn(fieldKey);
        if (auditCol != null) {
          whereClause.append(" AND ").append(auditCol).append(" >= CAST(:").append(paramName).append(" AS timestamptz)");
        } else {
          whereClause.append(" AND (data_json->>'").append(fieldKey).append("' >= :").append(paramName)
              .append(" OR EXISTS (SELECT 1 FROM jsonb_each(data_json) kv")
              .append(" WHERE jsonb_typeof(kv.value) = 'object'")
              .append(" AND kv.value->>'").append(fieldKey).append("' >= :").append(paramName).append("))");
        }
        return;
      }

      if (key.endsWith("_lte")) {
        if (!key.matches("[a-zA-Z0-9_]+")) return;
        String fieldKey = key.substring(0, key.length() - 4);
        String paramName = "p_" + key;
        String auditCol = toAuditDateColumn(fieldKey);
        if (auditCol != null) {
          whereClause.append(" AND ").append(auditCol).append(" <= CAST(:").append(paramName).append(" AS timestamptz)");
        } else {
          whereClause.append(" AND (data_json->>'").append(fieldKey).append("' <= :").append(paramName)
              .append(" OR EXISTS (SELECT 1 FROM jsonb_each(data_json) kv")
              .append(" WHERE jsonb_typeof(kv.value) = 'object'")
              .append(" AND kv.value->>'").append(fieldKey).append("' <= :").append(paramName).append("))");
        }
        return;
      }

      if (key.contains("|")) {
        String[] fields = key.split("\\|");
        if (!Arrays.stream(fields).allMatch(f -> f.matches("[a-zA-Z0-9_]+"))) return;
        String paramName = "p_or_" + key.replace("|", "__");
        String topLevel = Arrays.stream(fields)
            .map(f -> "data_json->>'" + f + "' ILIKE :" + paramName)
            .collect(java.util.stream.Collectors.joining(" OR "));
        String nested = Arrays.stream(fields)
            .map(f -> "kv.value->>'" + f + "' ILIKE :" + paramName)
            .collect(java.util.stream.Collectors.joining(" OR "));
        whereClause.append(" AND (")
            .append(topLevel)
            .append(" OR EXISTS (SELECT 1 FROM jsonb_each(data_json) kv")
            .append(" WHERE jsonb_typeof(kv.value) = 'object'")
            .append(" AND (").append(nested).append(")))");
        return;
      }

      if (!key.matches("[a-zA-Z0-9_]+")) return;
      if (value.contains("~")) {
        String[] parts = value.split("~", 2);
        String start = parts[0].trim();
        String end   = parts.length > 1 ? parts[1].trim() : "";
        if (!start.isEmpty()) whereClause.append(" AND data_json->>'").append(key).append("' >= :p_").append(key).append("_start");
        if (!end.isEmpty())   whereClause.append(" AND data_json->>'").append(key).append("' <= :p_").append(key).append("_end");
      } else {
        whereClause.append(" AND (data_json->>'").append(key).append("' ILIKE :p_").append(key)
            .append(" OR EXISTS (SELECT 1 FROM jsonb_each(data_json) kv")
            .append(" WHERE jsonb_typeof(kv.value) = 'object'")
            .append(" AND kv.value->>'").append(key).append("' ILIKE :p_").append(key).append("))");
      }
    });
  }

  private boolean isValidSegments(String[] segments) {
    if (segments.length == 0) return false;
    return Arrays.stream(segments).allMatch(s -> s.matches("[a-zA-Z0-9_]+"));
  }

  private String normalizeDateFrom(String value) {
    if (value == null) return value;
    if (value.length() == 8 && value.matches("[0-9]+")) return value + "000000";
    if (value.length() == 10 && value.contains("-"))    return value + "T00:00:00";
    return value;
  }

  private String normalizeDateTo(String value) {
    if (value == null) return value;
    if (value.length() == 8 && value.matches("[0-9]+")) return value + "235959";
    if (value.length() == 10 && value.contains("-"))    return value + "T23:59:59";
    return value;
  }

  private String toIsoTimestamp(String normalized) {
    if (normalized != null && normalized.length() == 14 && normalized.matches("[0-9]+")) {
      return normalized.substring(0, 4) + "-" + normalized.substring(4, 6) + "-" + normalized.substring(6, 8)
          + "T" + normalized.substring(8, 10) + ":" + normalized.substring(10, 12) + ":" + normalized.substring(12, 14);
    }
    return normalized;
  }

  private String buildJsonPath(String[] segments) {
    return buildJsonPath(null, segments);
  }

  private String buildJsonPath(String tableAlias, String[] segments) {
    StringBuilder path = new StringBuilder();
    if (StringUtils.hasText(tableAlias)) {
      path.append(tableAlias).append(".");
    }
    path.append("data_json");
    for (int i = 0; i < segments.length - 1; i++) {
      path.append("->'").append(segments[i]).append("'");
    }
    path.append("->>'").append(segments[segments.length - 1]).append("'");
    return path.toString();
  }

  private String buildOrderByClause(String sortParam, String slug, Long siteId) {
    return buildOrderByClause(sortParam, slug, siteId, null);
  }

  private String buildOrderByClause(String sortParam, String slug, Long siteId, String relationValuePath) {
    String orderBy = " ORDER BY created_at DESC";
    if (sortParam == null || sortParam.isBlank()) {
      return orderBy;
    }
    String[] parts = sortParam.split(",", 2);
    String sortCol = parts[0].trim();
    String sortDir = parts.length > 1 && "desc".equalsIgnoreCase(parts[1].trim()) ? "DESC" : "ASC";
    String relationExpr = buildRelationSortExpr(sortCol, slug, siteId, relationValuePath);
    if (relationExpr != null) {
      orderBy = " ORDER BY " + buildNumericAwareOrderByExpr(relationExpr, sortDir);
    } else if (sortCol.contains(".")) {
      String[] segs = sortCol.split("\\.");
      if (isValidSegments(segs)) {
        orderBy = " ORDER BY " + buildNumericAwareOrderByExpr(buildJsonPath(segs), sortDir);
      }
    } else if (sortCol.matches("[a-zA-Z0-9_]+")) {
      String auditCol = toAuditColumn(sortCol);
      if (auditCol != null) {
        orderBy = " ORDER BY " + auditCol + " " + sortDir;
      } else {
        orderBy = " ORDER BY " + buildNumericAwareOrderByExpr(buildNestedOrderByExpr(sortCol), sortDir);
      }
    }
    return orderBy;
  }

  private String buildNumericAwareOrderByExpr(String jsonTextExpr, String sortDir) {
    return "CASE WHEN " + jsonTextExpr + " ~ '^-?[0-9]+$' THEN (" + jsonTextExpr + ")::numeric END "
        + sortDir + " NULLS LAST, " + jsonTextExpr + " " + sortDir + " NULLS LAST";
  }

  /** 정렬용 record — sql은 ORDER BY 절 전체(" ORDER BY ..." 포함), params는 dataQuery에만 바인딩(countQuery는 ORDER BY 없어 불필요) */
  private record OrderByClause(String sql, Map<String, Object> params) {}

  private static final int SORT_EXPR_MAX_LENGTH = 500;
  private static final int SORT_EXPR_MAX_FIELD_REFS = 10;

  /**
   * sortExpr(FE evalColumnDataExpr와 동일 문법의 계산식)이 있으면 그걸 SQL로 변환해 정렬하고,
   * 없거나 파싱 실패 시 기존 buildOrderByClause(sort 컬럼명 기반)로 폴백한다.
   * allowSortExpr=false(FO 게시게이트 경로)면 sortExpr는 아예 읽지 않는다.
   */
  private OrderByClause buildOrderByClauseWithExpr(String sortParam, String sortExpr, boolean allowSortExpr,
                                                   String slug, Long siteId) {
    String relationValuePath = allowSortExpr ? resolveRelationSortValuePath(sortParam, sortExpr) : null;
    if (allowSortExpr && relationValuePath == null && sortExpr != null && !sortExpr.isBlank()) {
      String sortDir = "ASC";
      if (sortParam != null && !sortParam.isBlank()) {
        String[] parts = sortParam.split(",", 2);
        sortDir = parts.length > 1 && "desc".equalsIgnoreCase(parts[1].trim()) ? "DESC" : "ASC";
      }
      Map<String, Object> exprParams = new LinkedHashMap<>();
      String exprSql = buildExpressionOrderByExpr(sortExpr, exprParams, new AtomicInteger(0), slug, siteId);
      if (exprSql != null) {
        return new OrderByClause(" ORDER BY " + buildNumericAwareOrderByExpr(exprSql, sortDir), exprParams);
      }
    }
    return new OrderByClause(buildOrderByClause(sortParam, slug, siteId, relationValuePath), Map.of());
  }

  private String resolveRelationSortValuePath(String sortParam, String sortExpr) {
    if (sortParam == null || sortParam.isBlank() || sortExpr == null || sortExpr.isBlank()) return null;
    String sortCol = sortParam.split(",", 2)[0].trim();
    if (!isRelationSortAccessor(sortCol)) return null;
    String path = sortExpr.trim();
    return path.matches("[a-zA-Z0-9_]+(\\.[a-zA-Z0-9_]+)*") ? path : null;
  }

  private boolean isRelationSortAccessor(String sortCol) {
    if (sortCol == null || sortCol.isBlank()) return false;
    String[] tokens = sortCol.split(REL_SORT_MULTI_SEPARATOR_REGEX);
    if (tokens.length == 0 || tokens.length > REL_SORT_MAX_PARTS) return false;
    for (String token : tokens) {
      if (!FETCH_SORT_ACCESSOR_PATTERN.matcher(token.trim()).matches()) return false;
    }
    return true;
  }

  /**
   * 계산식(condition?trueExpr:falseExpr / token1+token2+...)을 ORDER BY용 SQL 조각으로 변환.
   * 파싱 실패·길이초과·필드참조 과다는 예외 없이 null을 반환해 호출부가 기본 정렬로 폴백하게 한다.
   */
  private String buildExpressionOrderByExpr(String expr, Map<String, Object> outParams, AtomicInteger paramSeq,
                                            String slug, Long siteId) {
    try {
      String sql = buildExpressionOrderByExprRec(expr, outParams, paramSeq, new AtomicInteger(0), slug, siteId);
      if (sql == null) {
        outParams.clear();
      }
      return sql;
    } catch (RuntimeException e) {
      outParams.clear();
      return null;
    }
  }

  private String buildExpressionOrderByExprRec(String expr, Map<String, Object> outParams, AtomicInteger paramSeq,
                                               AtomicInteger fieldRefCount, String slug, Long siteId) {
    if (expr == null) return null;
    String trimmed = expr.trim();
    if (trimmed.isEmpty() || trimmed.length() > SORT_EXPR_MAX_LENGTH) return null;

    String[] ternary = splitTopLevelTernary(trimmed);
    if (ternary != null) {
      List<CondToken> tokens = parseConditionExpr(ternary[0]);
      if (tokens.isEmpty()) return null;

      String condPrefix = "p_sortcond_" + paramSeq.getAndIncrement();
      String condSql = buildCondTokenSql(tokens, condPrefix);
      int idx = 0;
      for (CondToken t : tokens) {
        if (!t.isToday() && !t.isNow()) {
          outParams.put(condPrefix + "_" + idx, t.value());
        }
        idx++;
      }

      String trueSql = buildExpressionOrderByExprRec(ternary[1], outParams, paramSeq, fieldRefCount, slug, siteId);
      String falseSql = buildExpressionOrderByExprRec(ternary[2], outParams, paramSeq, fieldRefCount, slug, siteId);
      if (trueSql == null || falseSql == null) return null;
      return "CASE WHEN (" + condSql + ") THEN (" + trueSql + ") ELSE (" + falseSql + ") END";
    }

    List<String> tokens = parseSortExprConcatTokens(trimmed);
    if (tokens.isEmpty()) return null;

    List<String> parts = new ArrayList<>();
    for (String token : tokens) {
      if (isSortExprLiteralToken(token)) {
        String pName = "p_sort_" + paramSeq.getAndIncrement();
        outParams.put(pName, stripQuotes(token));
        parts.add("CAST(:" + pName + " AS text)");
      } else if (FETCH_SORT_ACCESSOR_PATTERN.matcher(token).matches()) {
        if (fieldRefCount.incrementAndGet() > SORT_EXPR_MAX_FIELD_REFS) return null;
        String relationExpr = buildSingleRelationSortExpr(token, slug, siteId, null);
        if (relationExpr == null) return null;
        parts.add(relationExpr);
      } else {
        if (!token.matches("[a-zA-Z0-9_]+")) return null;
        if (fieldRefCount.incrementAndGet() > SORT_EXPR_MAX_FIELD_REFS) return null;
        parts.add(buildNestedOrderByExpr(token));
      }
    }
    return parts.size() == 1 ? parts.get(0) : String.join(" || ", parts);
  }

  /** 최상위(중첩 무시) ?/: 위치 탐색 — depth 카운팅으로 중첩 ternary의 :를 건너뛴다(splitTernaryExpr과 달리 따옴표 안 벗김) */
  private String[] splitTopLevelTernary(String expr) {
    int depth = 0;
    int qIdx = -1;
    int cIdx = -1;
    boolean inSingleQuote = false;
    boolean inDoubleQuote = false;
    for (int i = 0; i < expr.length(); i++) {
      char ch = expr.charAt(i);
      if (ch == '\'' && !inDoubleQuote) { inSingleQuote = !inSingleQuote; continue; }
      if (ch == '"' && !inSingleQuote) { inDoubleQuote = !inDoubleQuote; continue; }
      if (inSingleQuote || inDoubleQuote) continue;
      if (ch == '?') {
        if (qIdx < 0) qIdx = i;
        depth++;
      } else if (ch == ':' && qIdx >= 0) {
        depth--;
        if (depth == 0) { cIdx = i; break; }
      }
    }
    if (qIdx < 0 || cIdx < 0) return null;
    return new String[]{expr.substring(0, qIdx).trim(), expr.substring(qIdx + 1, cIdx).trim(), expr.substring(cIdx + 1).trim()};
  }

  /** +로 토큰 분리(따옴표 안의 +는 무시) — FE parseConcatTokens와 동일 규칙 */
  private List<String> parseSortExprConcatTokens(String expr) {
    List<String> tokens = new ArrayList<>();
    StringBuilder cur = new StringBuilder();
    boolean inSingleQuote = false;
    boolean inDoubleQuote = false;
    for (int i = 0; i < expr.length(); i++) {
      char ch = expr.charAt(i);
      if (ch == '\'' && !inDoubleQuote) { inSingleQuote = !inSingleQuote; cur.append(ch); continue; }
      if (ch == '"' && !inSingleQuote) { inDoubleQuote = !inDoubleQuote; cur.append(ch); continue; }
      if (!inSingleQuote && !inDoubleQuote && ch == '+') {
        tokens.add(cur.toString().trim());
        cur.setLength(0);
        continue;
      }
      cur.append(ch);
    }
    String last = cur.toString().trim();
    if (!last.isEmpty() || tokens.isEmpty()) tokens.add(last);
    return tokens;
  }

  private boolean isSortExprLiteralToken(String token) {
    if (token == null) return false;
    String t = token.trim();
    if (t.length() < 2) return false;
    char q = t.charAt(0);
    if ((q != '\'' && q != '"') || t.charAt(t.length() - 1) != q) return false;
    return t.indexOf(q, 1) == t.length() - 1;
  }

  private String buildNestedOrderByExpr(String key) {
    return buildNestedJsonbLookupExpr("data_json", key);
  }

  private String buildNestedJsonbLookupExpr(String jsonbExpr, String key) {
    if (key == null || !key.matches("[a-zA-Z0-9_]+")) {
      return "NULL";
    }
    return "COALESCE(" + jsonbExpr + "->>'" + key + "', "
        + "(SELECT kv1.value->>'" + key + "' FROM jsonb_each(" + jsonbExpr + ") kv1 "
        + "WHERE jsonb_typeof(kv1.value) = 'object' AND jsonb_exists(kv1.value, '" + key + "') "
        + "ORDER BY kv1.key LIMIT 1), "
        + "(SELECT kv2.value->>'" + key + "' "
        + "FROM (SELECT kv1.key AS k1, kv1.value AS v1 FROM jsonb_each(" + jsonbExpr + ") kv1 "
        + "WHERE jsonb_typeof(kv1.value) = 'object') sub1, "
        + "LATERAL jsonb_each(sub1.v1) kv2 "
        + "WHERE jsonb_typeof(kv2.value) = 'object' AND jsonb_exists(kv2.value, '" + key + "') "
        + "ORDER BY sub1.k1, kv2.key LIMIT 1))";
  }

  private static final java.util.regex.Pattern FETCH_SORT_ACCESSOR_PATTERN =
      java.util.regex.Pattern.compile("^_fetchedRel([0-9]{1,18})(?:\\.([a-zA-Z0-9_]+(?:\\.[a-zA-Z0-9_]+)*))?$");
  private static final String REL_SORT_MULTI_SEPARATOR_REGEX = "\\|";
  private static final String REL_SORT_MULTI_DISPLAY_SEPARATOR = "', '";
  private static final int REL_SORT_MAX_PARTS = 5;
  private static final String REL_SORT_SLAVE_ALIAS = "rsrc";
  private static final int REL_SORT_MAX_CHAIN_DEPTH = 10;

  private String buildRelationSortExpr(String sortCol, String slug, Long siteId, String relationValuePath) {
    if (sortCol == null || sortCol.isBlank()) return null;
    String[] tokens = sortCol.split(REL_SORT_MULTI_SEPARATOR_REGEX);
    if (tokens.length == 0 || tokens.length > REL_SORT_MAX_PARTS) return null;

    List<String> exprs = new ArrayList<>();
    for (String token : tokens) {
      String expr = buildSingleRelationSortExpr(token.trim(), slug, siteId, relationValuePath);
      if (expr == null) return null;
      exprs.add(expr);
    }
    if (exprs.size() == 1) return exprs.get(0);

    String args = exprs.stream()
        .map(e -> "NULLIF(" + e + ", '')")
        .collect(java.util.stream.Collectors.joining(", "));
    return "NULLIF(concat_ws(" + REL_SORT_MULTI_DISPLAY_SEPARATOR + ", " + args + "), '')";
  }

  private String buildSingleRelationSortExpr(String sortCol, String slug, Long siteId, String relationValuePath) {
    if (sortCol == null) return null;
    java.util.regex.Matcher matcher = FETCH_SORT_ACCESSOR_PATTERN.matcher(sortCol);
    if (!matcher.matches()) return null;

    SlugRelation rel;
    try {
      rel = slugRelationRepository.findById(Long.parseLong(matcher.group(1))).orElse(null);
    } catch (RuntimeException e) {
      return null;
    }
    if (rel == null) return null;
    if (!"FETCH".equals(rel.getRelationDir())) return null;
    if (slug != null && !slug.equals(rel.getMasterSlug())) return null;

    boolean isArrayContains = "ARRAY_CONTAINS".equals(rel.getJoinType());
    if (!isArrayContains && !"EQ".equals(rel.getJoinType())) return null;

    String slaveSlug = rel.getSlaveSlug();
    if (!StringUtils.hasText(slaveSlug) || !slaveSlug.matches("[a-zA-Z0-9_-]+")) return null;

    String[] slaveSegs = splitValidatedPath(rel.getSlaveKey());
    if (slaveSegs == null) return null;

    String accessorPath = matcher.group(2);
    boolean isCategory = "CATEGORY".equals(rel.getSlaveType());
    String valueExpr;
    if (accessorPath != null) {
      valueExpr = buildRelationAccessorValueExpr(accessorPath);
    } else if (isCategory) {
      valueExpr = isArrayContains ? null : buildCategoryRelationSortValueExpr(rel, slaveSlug);
    } else {
      String[] fetchSegs = splitValidatedPath(rel.getFetchFields());
      valueExpr = fetchSegs != null
          ? buildJsonPath(REL_SORT_SLAVE_ALIAS, fetchSegs)
          : (relationValuePath == null ? null : buildRelationAccessorValueExpr(relationValuePath));
    }
    if (valueExpr == null) return null;

    String slaveFilterSql = buildRelationSortSlaveFilterSql(rel.getSlaveFilter());
    if (slaveFilterSql == null) return null;

    String separator = toSqlLiteral(StringUtils.hasText(rel.getFetchSeparator()) ? rel.getFetchSeparator() : ",");
    if (separator == null) return null;

    boolean applySiteId = !(isCategory && !isArrayContains) && siteId != null;
    String siteIdSql = applySiteId
        ? " AND (" + REL_SORT_SLAVE_ALIAS + ".site_id = :siteId OR " + REL_SORT_SLAVE_ALIAS + ".site_id IS NULL)"
        : "";

    return isArrayContains
        ? buildArrayContainsRelationSortExpr(rel, slaveSlug, slaveSegs, valueExpr, slaveFilterSql, siteIdSql,
            REL_SORT_MULTI_DISPLAY_SEPARATOR)
        : buildEqRelationSortExpr(rel, slaveSlug, slaveSegs, valueExpr, slaveFilterSql, siteIdSql, separator);
  }

  private String buildRelationMasterKeyExpr(String masterKey) {
    String[] segs = splitValidatedPath(masterKey);
    if (segs == null) return null;
    return segs.length == 1
        ? buildNestedJsonbLookupExpr("page_data.data_json", segs[0])
        : buildJsonPath("page_data", segs);
  }

  private String buildRelationAccessorValueExpr(String accessorPath) {
    String[] segs = splitValidatedPath(accessorPath);
    if (segs == null) return null;
    String slaveJson = REL_SORT_SLAVE_ALIAS + ".data_json";
    return segs.length == 1
        ? buildNestedJsonbLookupExpr(slaveJson, segs[0])
        : buildJsonbExprPath(slaveJson, segs);
  }

  private String buildEqRelationSortExpr(SlugRelation rel, String slaveSlug, String[] slaveSegs, String valueExpr,
                                         String slaveFilterSql, String siteIdSql, String separator) {
    String masterKeyExpr = buildRelationMasterKeyExpr(rel.getMasterKey());
    if (masterKeyExpr == null) return null;

    String source = "SELECT " + valueExpr + " AS rs_val"
        + " FROM page_data " + REL_SORT_SLAVE_ALIAS
        + " WHERE " + REL_SORT_SLAVE_ALIAS + ".data_slug = " + toSqlLiteral(slaveSlug)
        + " AND " + REL_SORT_SLAVE_ALIAS + ".is_deleted = false"
        + " AND " + buildJsonPath(REL_SORT_SLAVE_ALIAS, slaveSegs) + " = " + masterKeyExpr
        + slaveFilterSql
        + siteIdSql;

    return "(SELECT string_agg(rsv.rs_val, " + separator + " ORDER BY rsv.rs_val)"
        + " FROM (" + source + ") rsv)";
  }

  private String buildArrayContainsRelationSortExpr(SlugRelation rel, String slaveSlug, String[] slaveSegs,
                                                    String valueExpr, String slaveFilterSql, String siteIdSql,
                                                    String separator) {
    String masterContainer = buildValidatedJsonbContainerPath("page_data", rel.getMasterKey());
    if (masterContainer == null) return null;

    String arraySrc = "CASE WHEN jsonb_typeof(" + masterContainer + ") = 'array' THEN " + masterContainer
        + " ELSE '[]'::jsonb END";
    String elemKeyExpr = "COALESCE(rsel.elem->>'productId', rsel.elem->>'id', rsel.elem #>> '{}')";

    String distinctKeys = "SELECT rsk.rs_key AS rs_key, MIN(rsk.rs_ord) AS rs_ord FROM ("
        + "SELECT " + elemKeyExpr + " AS rs_key, rsel.ord AS rs_ord"
        + " FROM jsonb_array_elements(" + arraySrc + ") WITH ORDINALITY rsel(elem, ord)) rsk"
        + " WHERE rsk.rs_key ~ '^-?[0-9]+$' GROUP BY rsk.rs_key";

    String source = "SELECT " + valueExpr + " AS rs_val, rsd.rs_ord AS rs_ord"
        + " FROM (" + distinctKeys + ") rsd"
        + " JOIN page_data " + REL_SORT_SLAVE_ALIAS
        + " ON " + REL_SORT_SLAVE_ALIAS + ".data_slug = " + toSqlLiteral(slaveSlug)
        + " AND " + REL_SORT_SLAVE_ALIAS + ".is_deleted = false"
        + " AND " + buildJsonPath(REL_SORT_SLAVE_ALIAS, slaveSegs) + " = rsd.rs_key"
        + slaveFilterSql
        + siteIdSql;

    return "(SELECT string_agg(rsv.rs_val, " + separator + " ORDER BY rsv.rs_ord, rsv.rs_val)"
        + " FROM (" + source + ") rsv)";
  }

  private String buildValidatedJsonbContainerPath(String tableAlias, String keyPath) {
    String[] segs = splitValidatedPath(keyPath);
    if (segs == null) return null;
    StringBuilder path = new StringBuilder();
    if (StringUtils.hasText(tableAlias)) {
      path.append(tableAlias).append(".");
    }
    path.append("data_json");
    for (String seg : segs) {
      path.append("->'").append(seg).append("'");
    }
    return path.toString();
  }

  private String buildCategoryRelationSortValueExpr(SlugRelation rel, String slaveSlug) {
    if (Boolean.TRUE.equals(rel.getIncludeLeaf())) return null;

    int targetDepth = rel.getCategoryDepth() != null ? rel.getCategoryDepth() : 1;
    int fromDepth = Math.max(1, rel.getCategoryDepthFrom() != null ? rel.getCategoryDepthFrom() : targetDepth);
    if (targetDepth < fromDepth || targetDepth > REL_SORT_MAX_CHAIN_DEPTH) return null;

    String nameExpr = buildRelationMultiPathExpr("rs_anc.dj", rel.getFetchFields());
    if (nameExpr == null) return null;

    String selfDepthExpr = buildRelationMultiPathExpr(REL_SORT_SLAVE_ALIAS + ".data_json", "category.depth,product.depth");
    if (selfDepthExpr == null) return null;

    String parentIdExpr = "COALESCE(rs_anc.dj->>'parentId',"
        + " (SELECT rskv.value->>'parentId' FROM jsonb_each(rs_anc.dj) rskv"
        + " WHERE jsonb_typeof(rskv.value) = 'object' AND jsonb_exists(rskv.value, 'parentId')"
        + " ORDER BY rskv.key LIMIT 1))";

    return "(WITH RECURSIVE rs_anc(dj, lvl) AS ("
        + "SELECT " + REL_SORT_SLAVE_ALIAS + ".data_json, 0"
        + " UNION ALL"
        + " SELECT rsp.data_json, rs_anc.lvl + 1 FROM rs_anc"
        + " JOIN page_data rsp ON rsp.data_slug = " + toSqlLiteral(slaveSlug) + " AND rsp.is_deleted = false"
        + " AND rsp.data_json->>'id' = " + parentIdExpr
        + " WHERE rs_anc.lvl < " + REL_SORT_MAX_CHAIN_DEPTH + ")"
        + " SELECT CASE WHEN " + selfDepthExpr + " IS NULL"
        + " OR " + selfDepthExpr + " !~ '^[0-9]+$'"
        + " OR (" + selfDepthExpr + ")::int - 1 = MAX(rsa.rs_n)"
        + " THEN string_agg(rsa.rs_nm, ' > ' ORDER BY rsa.rs_lvl DESC) END"
        + " FROM (SELECT " + nameExpr + " AS rs_nm, rs_anc.lvl AS rs_lvl, COUNT(*) OVER () AS rs_n"
        + " FROM rs_anc WHERE rs_anc.lvl >= 1 AND " + nameExpr + " IS NOT NULL) rsa"
        + " WHERE (rsa.rs_n - rsa.rs_lvl + 1) BETWEEN " + fromDepth + " AND " + targetDepth + ")";
  }

  private String buildRelationSortSlaveFilterSql(String slaveFilter) {
    if (!StringUtils.hasText(slaveFilter)) return "";
    StringBuilder sql = new StringBuilder();
    for (String cond : slaveFilter.split("&")) {
      int eqIdx = cond.indexOf('=');
      int tildeIdx = cond.indexOf('~');
      boolean ilike = tildeIdx >= 0 && (eqIdx < 0 || tildeIdx < eqIdx);

      String key;
      String value;
      if (ilike) {
        key = cond.substring(0, tildeIdx).trim();
        value = cond.substring(tildeIdx + 1).trim();
      } else {
        String[] kv = cond.split("=", 2);
        if (kv.length != 2) continue;
        key = kv[0].trim();
        value = kv[1].trim();
      }
      if (!key.matches("[a-zA-Z0-9_.]+")) continue;

      String literal = toSqlLiteral(ilike ? "%" + value + "%" : value);
      if (literal == null) return null;
      String op = ilike ? "ILIKE" : "=";

      if (key.contains(".")) {
        String[] segs = splitValidatedPath(key);
        if (segs == null) continue;
        sql.append(" AND ").append(buildJsonPath(REL_SORT_SLAVE_ALIAS, segs))
           .append(" ").append(op).append(" ").append(literal);
      } else {
        sql.append(" AND (").append(REL_SORT_SLAVE_ALIAS).append(".data_json->>'").append(key).append("' ")
           .append(op).append(" ").append(literal)
           .append(" OR EXISTS (SELECT 1 FROM jsonb_each(").append(REL_SORT_SLAVE_ALIAS).append(".data_json) rskv2")
           .append(" WHERE jsonb_typeof(rskv2.value) = 'object' AND rskv2.value->>'").append(key).append("' ")
           .append(op).append(" ").append(literal).append("))");
      }
    }
    return sql.toString();
  }

  private String buildRelationMultiPathExpr(String jsonbExpr, String csvFieldPaths) {
    if (!StringUtils.hasText(csvFieldPaths)) return null;
    List<String> parts = new ArrayList<>();
    for (String rawPath : csvFieldPaths.split(",")) {
      String[] segs = splitValidatedPath(rawPath);
      if (segs == null) continue;
      parts.add(buildJsonbExprPath(jsonbExpr, segs));
    }
    if (parts.isEmpty()) return null;
    return parts.size() == 1 ? parts.get(0) : "COALESCE(" + String.join(", ", parts) + ")";
  }

  private String buildJsonbExprPath(String jsonbExpr, String[] segments) {
    StringBuilder path = new StringBuilder(jsonbExpr);
    for (int i = 0; i < segments.length - 1; i++) {
      path.append("->'").append(segments[i]).append("'");
    }
    path.append("->>'").append(segments[segments.length - 1]).append("'");
    return path.toString();
  }

  private String[] splitValidatedPath(String keyPath) {
    if (!StringUtils.hasText(keyPath)) return null;
    String[] segs = keyPath.trim().split("\\.");
    return isValidSegments(segs) ? segs : null;
  }

  private String toSqlLiteral(String value) {
    if (value == null || value.indexOf('\\') >= 0 || value.indexOf('\0') >= 0) return null;
    return "'" + value.replace("'", "''") + "'";
  }

  private void appendExistsRelationConditions(StringBuilder whereClause, Map<String, String> existsRelParams,
                                              Map<String, String> bindParams, Long siteId) {
    if (existsRelParams == null || existsRelParams.isEmpty()) return;

    Map<String, String[]> groups = new LinkedHashMap<>();
    existsRelParams.forEach((key, value) -> {
      int idx;
      if (key.startsWith("exs_")) idx = 0;
      else if (key.startsWith("exk_")) idx = 1;
      else if (key.startsWith("exm_")) idx = 2;
      else if (key.startsWith("exf_")) idx = 3;
      else return;
      groups.computeIfAbsent(key.substring(4), k -> new String[4])[idx] = value;
    });

    int seq = 0;
    for (String[] group : groups.values()) {
      String slaveSlug = group[0];
      String slaveKey = group[1];
      String masterKey = group[2];
      String slaveFilter = group[3];
      if (!StringUtils.hasText(slaveSlug) || !StringUtils.hasText(slaveKey) || !StringUtils.hasText(masterKey)) continue;
      if (!slaveSlug.matches("[a-zA-Z0-9_-]+")) continue;

      String[] slaveSegs = slaveKey.trim().split("\\.");
      String[] masterSegs = masterKey.trim().split("\\.");
      if (!isValidSegments(slaveSegs) || !isValidSegments(masterSegs)) continue;

      String alias = "exr" + seq;
      String slugParam = "exSlug_" + seq;
      StringBuilder cond = new StringBuilder(" AND EXISTS (SELECT 1 FROM page_data ").append(alias)
          .append(" WHERE ").append(alias).append(".data_slug = :").append(slugParam)
          .append(" AND ").append(alias).append(".is_deleted = false")
          .append(" AND ").append(buildJsonPath(alias, slaveSegs))
          .append(" = ").append(buildJsonPath("page_data", masterSegs));
      bindParams.put(slugParam, slaveSlug.trim());

      if (siteId != null) {
        cond.append(" AND (").append(alias).append(".site_id = :siteId OR ").append(alias).append(".site_id IS NULL)");
      }

      if (StringUtils.hasText(slaveFilter)) {
        int filterSeq = 0;
        for (String rawCond : slaveFilter.split("&")) {
          String[] kv = rawCond.split("=", 2);
          if (kv.length != 2) continue;
          String[] filterSegs = kv[0].trim().split("\\.");
          if (!isValidSegments(filterSegs)) continue;
          String filterParam = "exFilter_" + seq + "_" + filterSeq;
          cond.append(" AND ").append(buildJsonPath(alias, filterSegs)).append(" = :").append(filterParam);
          bindParams.put(filterParam, kv[1].trim());
          filterSeq++;
        }
      }

      cond.append(")");
      whereClause.append(cond);
      seq++;
    }
  }

  private String buildJsonbContainerPath(String keyPath) {
    StringBuilder path = new StringBuilder("data_json");
    for (String seg : keyPath.split("\\.")) {
      path.append("->'").append(seg).append("'");
    }
    return path.toString();
  }

  private String buildArrayContainsCondition(String masterKey, Set<String> linkValues) {
    String container = buildJsonbContainerPath(masterKey);
    return linkValues.stream()
        .filter(v -> v.matches("-?\\d+"))
        .map(v -> "(" + container + " @> '[" + v + "]'::jsonb"
            + " OR " + container + " @> '[{\"productId\":" + v + "}]'::jsonb"
            + " OR " + container + " @> '[{\"id\":" + v + "}]'::jsonb)")
        .collect(java.util.stream.Collectors.joining(" OR "));
  }

  private Map<String, String> extractStatusParams(Map<String, String> allParams, String... extraExcludeKeys) {
    Set<String> excludes = new HashSet<>(RESERVED_PARAMS);
    excludes.addAll(Arrays.asList(extraExcludeKeys));
    Map<String, String> statusParams = new LinkedHashMap<>();
    allParams.forEach((key, value) -> {
      if (excludes.contains(key) || value == null || value.isBlank()) return;
      statusParams.put(key, value);
    });
    return statusParams;
  }

  private String resolveFieldExpr(String field, boolean allowAudit) {
    if (field == null || field.isBlank()) {
      throw BusinessException.badRequest("필드값이 필요합니다.");
    }
    if (field.contains(".")) {
      String[] segs = field.split("\\.");
      if (!isValidSegments(segs)) {
        throw BusinessException.badRequest("올바르지 않은 필드 경로입니다: " + field);
      }
      return buildJsonPath(segs);
    }
    if (allowAudit) {
      String auditCol = toAuditColumn(field);
      if (auditCol != null) {
        return auditCol;
      }
    }
    if (!field.matches("[a-zA-Z0-9_]+")) {
      throw BusinessException.badRequest("올바르지 않은 필드명입니다: " + field);
    }
    return "data_json->>'" + field + "'";
  }

  private AdjacentResponse.AdjacentItem queryAdjacentItem(String sql, String slug, Long id, Long siteId,
                                                          Map<String, String> statusParams) {
    Query query = entityManager.createNativeQuery(sql);
    query.setParameter("slug", slug);
    query.setParameter("id", id);
    if (siteId != null) {
      query.setParameter("siteId", siteId);
    }
    bindSearchParams(query, statusParams, siteId);
    bindTodayIfPresent(query, sql, siteId);
    bindNowIfPresent(query, sql, siteId);

    @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();
    if (rows.isEmpty()) {
      return null;
    }
    Object[] row = rows.get(0);
    Long rowId = ((Number) row[0]).longValue();
    String title = row[1] != null ? row[1].toString() : null;
    return new AdjacentResponse.AdjacentItem(rowId, title);
  }

  private record CondToken(String key, String op, String value, boolean isToday, boolean isNow) {}

  private static final Pattern COND_TOKEN_PATTERN =
      Pattern.compile("^([a-zA-Z0-9_]+(?:\\.[a-zA-Z0-9_]+)?)\\s*(!=|>=|<=|=|<|>)\\s*(.+)$");

  private List<CondToken> parseConditionExpr(String expr) {
    List<CondToken> tokens = new ArrayList<>();
    if (expr == null || expr.isBlank()) return tokens;
    for (String part : expr.split(",")) {
      Matcher m = COND_TOKEN_PATTERN.matcher(part.trim());
      if (!m.matches()) continue;
      String val = m.group(3).trim();
      boolean isToday = "today()".equals(val);
      boolean isNow = "now()".equals(val);
      tokens.add(new CondToken(m.group(1), m.group(2), (isToday || isNow) ? null : stripQuotes(val), isToday, isNow));
    }
    return tokens;
  }

  /**
   * CondToken 리스트 → WHERE 조건 SQL 조각. appendWhereConditions/appendWhereConditionsDatetime/
   * buildExpressionOrderByExprRec(정렬용 CASE WHEN 조건)이 공유하는 헬퍼 — 중복 제거 목적.
   * paramSeq_idx 순으로 파라미터명을 채번하므로 호출부는 동일 규칙으로 값 바인딩해야 한다.
   */
  private String buildCondTokenSql(List<CondToken> tokens, String paramPrefix) {
    String today = ":today";
    String now = ":nowValue";
    List<String> topParts = new ArrayList<>();
    List<String> nestedParts = new ArrayList<>();
    int idx = 0;
    for (CondToken t : tokens) {
      String pName = paramPrefix + "_" + idx;
      String sqlOp = "!=".equals(t.op()) ? "<>" : t.op();
      if (t.isToday()) {
        topParts.add("substring(regexp_replace(data_json->>'" + t.key() + "', '[^0-9]', '', 'g'), 1, 8) " + sqlOp + " " + today);
        nestedParts.add("substring(regexp_replace(kv.value->>'" + t.key() + "', '[^0-9]', '', 'g'), 1, 8) " + sqlOp + " " + today);
      } else if (t.isNow()) {
        topParts.add(toNowPaddedExpr("data_json->>'" + t.key() + "'") + " " + sqlOp + " " + now);
        nestedParts.add(toNowPaddedExpr("kv.value->>'" + t.key() + "'") + " " + sqlOp + " " + now);
      } else {
        topParts.add("data_json->>'" + t.key() + "' " + sqlOp + " :" + pName);
        nestedParts.add("kv.value->>'" + t.key() + "' " + sqlOp + " :" + pName);
      }
      idx++;
    }
    return "((" + String.join(" AND ", topParts) + ")"
        + " OR EXISTS (SELECT 1 FROM jsonb_each(data_json) kv WHERE jsonb_typeof(kv.value) = 'object' AND ("
        + String.join(" AND ", nestedParts) + ")))";
  }

  private String[] splitTernaryExpr(String dataExpr) {
    if (dataExpr == null) return null;
    int q = dataExpr.indexOf('?');
    if (q < 0) return null;
    String cond = dataExpr.substring(0, q).trim();
    String rest = dataExpr.substring(q + 1);
    int c = rest.indexOf(':');
    if (c < 0) return null;
    return new String[]{cond, stripQuotes(rest.substring(0, c).trim()), stripQuotes(rest.substring(c + 1).trim())};
  }

  private String stripQuotes(String s) {
    if (s.length() >= 2 && ((s.startsWith("'") && s.endsWith("'")) || (s.startsWith("\"") && s.endsWith("\"")))) {
      return s.substring(1, s.length() - 1);
    }
    return s;
  }

  private void bindSearchParams(Query query, Map<String, String> searchParams, Long siteId) {
    searchParams.forEach((key, value) -> {
      if (key.equals("filterExpr")) {
        setConditionParams(query, value);
        return;
      }

      if (key.startsWith("drs_")) return;

      if (key.startsWith("condval_")) return;

      if (key.startsWith("month_")) {
        String fieldKey = key.substring(6);
        if (!fieldKey.matches("[a-zA-Z0-9_]+")) return;
        if (!value.matches("0[1-9]|1[0-2]")) return;
        query.setParameter("p_" + key, value);
        return;
      }

      if (key.startsWith("year_")) {
        String fieldKey = key.substring(5);
        if (!fieldKey.matches("[a-zA-Z0-9_]+")) return;
        if (!value.matches("[0-9]{4}")) return;
        query.setParameter("p_" + key, value);
        return;
      }

      if (key.startsWith("has_markets_")) {
        String fieldKey = key.substring("has_markets_".length());
        if (!fieldKey.matches("[a-zA-Z0-9_]+")) return;
        if (!value.matches("[0-9]{3}")) return;
        query.setParameter("p_" + key, "%," + value + ",%");
        return;
      }

      if (key.startsWith("condexpr_")) {
        String fk = key.substring("condexpr_".length());
        if (!fk.matches("[a-zA-Z0-9_]+")) return;
        String selectedVal = searchParams.get("condval_" + fk);
        if (selectedVal == null) return;
        String[] ternary = splitTernaryExpr(value);
        if (ternary == null) return;
        if (!selectedVal.equals(ternary[1]) && !selectedVal.equals(ternary[2])) return;
        List<CondToken> tokens = parseConditionExpr(ternary[0]);
        int idx = 0;
        for (CondToken t : tokens) {
          if (!t.isToday() && !t.isNow()) query.setParameter("p_cond_" + fk + "_" + idx, t.value());
          idx++;
        }
        return;
      }

      if (key.startsWith("eq_")) {
        String fieldKey = key.substring(3);
        if (fieldKey.contains(".")) {
          String[] segments = fieldKey.split("\\.");
          if (!isValidSegments(segments)) return;
          query.setParameter("p_" + key.replace(".", "_"), value);
        } else {
          if (!fieldKey.matches("[a-zA-Z0-9_]+")) return;
          query.setParameter("p_" + key, value);
        }
        return;
      }

      if (key.startsWith("in_")) {
        String fieldKey = key.substring(3);
        List<String> inValues = parseInValues(value);
        if (inValues.isEmpty()) return;
        if (fieldKey.contains(".")) {
          String[] segments = fieldKey.split("\\.");
          if (!isValidSegments(segments)) return;
        } else {
          if (!fieldKey.matches("[a-zA-Z0-9_]+")) return;
        }
        String baseParam = "p_" + key.replace(".", "_");
        for (int i = 0; i < inValues.size(); i++) {
          query.setParameter(baseParam + "_" + i, inValues.get(i));
        }
        return;
      }

      if (key.startsWith("ne_")) {
        String fieldKey = key.substring(3);
        if (!fieldKey.matches("[a-zA-Z0-9_]+")) return;
        query.setParameter("p_" + key, value);
        return;
      }

      if (key.contains(".")) {
        String[] segments = key.split("\\.");
        if (!isValidSegments(segments)) return;
        String paramName = "p_" + key.replace(".", "_");
        if (value.contains("~")) {
          String[] parts = value.split("~", 2);
          String start = parts[0].trim();
          String end   = parts.length > 1 ? parts[1].trim() : "";
          if (!start.isEmpty()) query.setParameter(paramName + "_start", start);
          if (!end.isEmpty())   query.setParameter(paramName + "_end", end);
        } else {
          query.setParameter(paramName, "%" + value + "%");
        }
        return;
      }

      if (key.endsWith("_from") || key.endsWith("_gte")) {
        if (!key.matches("[a-zA-Z0-9_]+")) return;
        String baseKey = key.endsWith("_from") ? key.substring(0, key.length() - 5) : key.substring(0, key.length() - 4);
        String resolvedValue = "today()".equals(value) ? resolveTodayIsoDate(siteId) : value;
        String normalized = normalizeDateFrom(resolvedValue);
        String bindValue = toAuditDateColumn(baseKey) != null ? toIsoTimestamp(normalized) : normalized;
        query.setParameter("p_" + key, bindValue);
        return;
      }

      if (key.endsWith("_to") || key.endsWith("_lte")) {
        if (!key.matches("[a-zA-Z0-9_]+")) return;
        String baseKey = key.endsWith("_to") ? key.substring(0, key.length() - 3) : key.substring(0, key.length() - 4);
        String resolvedValue = "today()".equals(value) ? resolveTodayIsoDate(siteId) : value;
        String normalized = normalizeDateTo(resolvedValue);
        String bindValue = toAuditDateColumn(baseKey) != null ? toIsoTimestamp(normalized) : normalized;
        query.setParameter("p_" + key, bindValue);
        return;
      }

      if (key.contains("|")) {
        String[] fields = key.split("\\|");
        if (!Arrays.stream(fields).allMatch(f -> f.matches("[a-zA-Z0-9_]+"))) return;
        query.setParameter("p_or_" + key.replace("|", "__"), "%" + value + "%");
        return;
      }

      if (!key.matches("[a-zA-Z0-9_]+")) return;
      if (value.contains("~")) {
        String[] parts = value.split("~", 2);
        String start = parts[0].trim();
        String end   = parts.length > 1 ? parts[1].trim() : "";
        if (!start.isEmpty()) query.setParameter("p_" + key + "_start", start);
        if (!end.isEmpty())   query.setParameter("p_" + key + "_end", end);
      } else {
        query.setParameter("p_" + key, "%" + value + "%");
      }
    });
  }

  private String toAuditColumn(String key) {
    return switch (key) {
      case "createdAt" -> "created_at";
      case "updatedAt" -> "updated_at";
      case "createdBy" -> "created_by";
      case "updatedBy" -> "updated_by";
      case "count" -> "\"count\"";
      default -> null;
    };
  }

  private String toAuditDateColumn(String key) {
    return switch (key) {
      case "createdAt" -> "created_at";
      case "updatedAt" -> "updated_at";
      default -> null;
    };
  }

    @SuppressWarnings("unchecked")
    private Set<Long> resolveFilterRelationIds(Map<String, String> relFilterParams) {
        Set<Long> resultIds = null;

        for (Map.Entry<String, String> entry : relFilterParams.entrySet()) {
            String key = entry.getKey();
            String categoryValue = entry.getValue();
            if (categoryValue == null || categoryValue.isBlank()) continue;

            Long relId;
            try { relId = Long.parseLong(key.substring(4)); }
            catch (NumberFormatException e) { continue; }

            long catId;
            try { catId = Long.parseLong(categoryValue.trim()); }
            catch (NumberFormatException e) { continue; }

            SlugRelation rel = slugRelationRepository.findById(relId).orElse(null);
            if (rel == null || !"FILTER".equals(rel.getRelationDir())) continue;

            int lastDot = rel.getSlaveKey().lastIndexOf('.');
            String parentKeyPath = lastDot >= 0
                ? rel.getSlaveKey().substring(0, lastDot + 1) + "parentId"
                : "parentId";

            Set<Long> catIds = collectCategoryAndDescendants(rel.getSlaveSlug(), catId, rel.getSlaveKey());
            String catIdList = catIds.stream()
                .map(id -> "'" + id + "'")
                .collect(java.util.stream.Collectors.joining(","));

            StringBuilder sql = new StringBuilder(
                "SELECT id, data_json::text FROM page_data WHERE data_slug = :slaveSlug AND is_deleted = false");
            appendSlaveKeyInCondition(sql, parentKeyPath, catIdList);
            Map<String, String> filterParams = new LinkedHashMap<>();
            if (StringUtils.hasText(rel.getSlaveFilter())) {
                appendSlaveFilter(sql, rel.getSlaveFilter(), filterParams);
            }

            Query q = entityManager.createNativeQuery(sql.toString());
            q.setParameter("slaveSlug", rel.getSlaveSlug());
            filterParams.forEach(q::setParameter);

            boolean selfReference = rel.getMasterSlug().equals(rel.getSlaveSlug());

            List<Object[]> rows = q.getResultList();
            Set<Long> ids = new HashSet<>();
            for (Object[] row : rows) {
                try {
                    Long rowId = ((Number) row[0]).longValue();
                    if (selfReference) {
                        ids.add(rowId);
                    } else {
                        Map<String, Object> dataJson = objectMapper.readValue(
                            row[1].toString(), new com.fasterxml.jackson.core.type.TypeReference<>() {});
                        String masterVal = extractField(dataJson, rel.getSlaveKey());
                        if (masterVal != null && !masterVal.isBlank()) {
                            ids.add(Long.parseLong(masterVal.trim()));
                        }
                    }
                } catch (Exception e) {
                    log.warn("FILTER RELATION master id 추출 실패: {}", e.getMessage());
                }
            }

            if (resultIds == null) resultIds = new HashSet<>(ids);
            else resultIds.retainAll(ids);
        }

        return resultIds;
    }

    @SuppressWarnings("unchecked")
    private Set<Long> resolveCurrDtlMgmtIdsByCategoryFilter(Long categoryId) {
        String sql = """
            WITH RECURSIVE descendant_categories AS (
                SELECT id FROM page_data
                 WHERE data_slug = 'category-data' AND id = :categoryId AND is_deleted = false
                UNION ALL
                SELECT p.id FROM page_data p
                 JOIN descendant_categories d
                   ON p.data_slug = 'category-data'
                  AND p.is_deleted = false
                  AND COALESCE(NULLIF(p.data_json->'category'->>'parentId',''), NULLIF(p.data_json->'product'->>'parentId','')) ~ '^[0-9]+$'
                  AND COALESCE(NULLIF(p.data_json->'category'->>'parentId',''), NULLIF(p.data_json->'product'->>'parentId',''))::bigint = d.id
            )
            SELECT id FROM page_data
             WHERE data_slug = 'currDtlMgmt-data'
               AND is_deleted = false
               AND (
                    EXISTS (SELECT 1 FROM jsonb_array_elements(COALESCE(data_json->'power_list','[]'::jsonb)) el
                             WHERE ((el->>'depth1') ~ '^[0-9]+$' AND (el->>'depth1')::bigint IN (SELECT id FROM descendant_categories))
                                OR ((el->>'depth2') ~ '^[0-9]+$' AND (el->>'depth2')::bigint IN (SELECT id FROM descendant_categories))
                                OR ((el->>'depth3') ~ '^[0-9]+$' AND (el->>'depth3')::bigint IN (SELECT id FROM descendant_categories)))
                 OR EXISTS (SELECT 1 FROM jsonb_array_elements(COALESCE(data_json->'automation_list','[]'::jsonb)) el
                             WHERE ((el->>'depth1') ~ '^[0-9]+$' AND (el->>'depth1')::bigint IN (SELECT id FROM descendant_categories))
                                OR ((el->>'depth2') ~ '^[0-9]+$' AND (el->>'depth2')::bigint IN (SELECT id FROM descendant_categories))
                                OR ((el->>'depth3') ~ '^[0-9]+$' AND (el->>'depth3')::bigint IN (SELECT id FROM descendant_categories)))
               )
            """;
        Query q = entityManager.createNativeQuery(sql);
        q.setParameter("categoryId", categoryId);
        List<Object> rows = q.getResultList();
        Set<Long> ids = new HashSet<>();
        for (Object row : rows) {
            ids.add(((Number) row).longValue());
        }
        return ids;
    }

    private Set<Long> resolveJoinFilterIds(Map<String, String> joinFilterParams) {
        Map<String, String[]> groups = new LinkedHashMap<>();
        joinFilterParams.forEach((key, value) -> {
            String n; int idx;
            if (key.startsWith("joinr_")) { n = key.substring(6); idx = 0; }
            else if (key.startsWith("joink_")) { n = key.substring(6); idx = 1; }
            else if (key.startsWith("joinv_")) { n = key.substring(6); idx = 2; }
            else return;
            groups.computeIfAbsent(n, k -> new String[3])[idx] = value;
        });

        Set<Long> resultIds = null;

        for (String[] g : groups.values()) {
            String relIdStr = g[0];
            String joinFieldKeyRaw = g[1];
            String value = g[2];
            if (relIdStr == null || joinFieldKeyRaw == null || value == null || value.isBlank()) continue;

            Long relId;
            try { relId = Long.parseLong(relIdStr.trim()); }
            catch (NumberFormatException e) { continue; }

            boolean ilike = joinFieldKeyRaw.startsWith("~");
            String joinFieldKey = (ilike ? joinFieldKeyRaw.substring(1) : joinFieldKeyRaw).trim();
            if (!joinFieldKey.matches("[a-zA-Z0-9_.]+")) continue;

            SlugRelation rel = slugRelationRepository.findById(relId).orElse(null);
            if (rel == null) continue;
            if (!StringUtils.hasText(rel.getMasterKey())) continue;
            boolean isArrayContains = "ARRAY_CONTAINS".equals(rel.getJoinType());

            StringBuilder slaveSql = new StringBuilder(
                "SELECT data_json::text FROM page_data WHERE data_slug = :slaveSlug AND is_deleted = false");
            Map<String, String> condParams = new LinkedHashMap<>();
            String condExpr = joinFieldKey + (ilike ? "~" : "=") + value;
            appendSlaveFilter(slaveSql, condExpr, condParams);
            if (StringUtils.hasText(rel.getSlaveFilter())) {
                appendSlaveFilter(slaveSql, rel.getSlaveFilter(), condParams);
            }

            Query slaveQuery = entityManager.createNativeQuery(slaveSql.toString());
            slaveQuery.setParameter("slaveSlug", rel.getSlaveSlug());
            condParams.forEach(slaveQuery::setParameter);

            List<Object> slaveRows = slaveQuery.getResultList();
            Set<String> linkValues = new HashSet<>();
            for (Object row : slaveRows) {
                try {
                    Map<String, Object> dataJson = objectMapper.readValue(
                        row.toString(), new com.fasterxml.jackson.core.type.TypeReference<>() {});
                    String linkVal = extractField(dataJson, rel.getSlaveKey());
                    if (linkVal != null && !linkVal.isBlank()) linkValues.add(linkVal.trim());
                } catch (Exception e) {
                    log.warn("JOIN FILTER 연동 slug 레코드 파싱 실패: {}", e.getMessage());
                }
            }

            if (linkValues.isEmpty()) {
                resultIds = new HashSet<>();
                break;
            }

            Set<Long> ids = new HashSet<>();
            StringBuilder masterSql = new StringBuilder("SELECT id FROM page_data WHERE data_slug = :masterSlug AND is_deleted = false");
            boolean runMasterQuery = true;

            if (isArrayContains) {
                if (!rel.getMasterKey().matches("[a-zA-Z0-9_.]+")) continue;
                String containsCond = buildArrayContainsCondition(rel.getMasterKey(), linkValues);
                if (containsCond.isBlank()) runMasterQuery = false;
                else masterSql.append(" AND (").append(containsCond).append(")");
            } else {
                String linkValueList = linkValues.stream()
                    .map(v -> "'" + v.replace("'", "''") + "'")
                    .collect(java.util.stream.Collectors.joining(","));
                appendSlaveKeyInCondition(masterSql, rel.getMasterKey(), linkValueList);
            }

            if (runMasterQuery) {
                Query masterQuery = entityManager.createNativeQuery(masterSql.toString());
                masterQuery.setParameter("masterSlug", rel.getMasterSlug());

                List<Object> masterRows = masterQuery.getResultList();
                for (Object row : masterRows) {
                    try { ids.add(((Number) row).longValue()); }
                    catch (Exception e) {  }
                }
            }

            if (resultIds == null) resultIds = new HashSet<>(ids);
            else resultIds.retainAll(ids);
        }

        return resultIds;
    }

    private Set<Long> resolveInnerRelationIds(Map<String, String> innerRelParams) {
        List<String> relIdStrs = new ArrayList<>();
        innerRelParams.forEach((key, value) -> {
            if (key.startsWith("innerRel_") && value != null && !value.isBlank()) {
                relIdStrs.add(value.trim());
            }
        });

        Set<Long> resultIds = null;

        for (String relIdStr : relIdStrs) {
            Long relId;
            try { relId = Long.parseLong(relIdStr); }
            catch (NumberFormatException e) { continue; }

            SlugRelation rel = slugRelationRepository.findById(relId).orElse(null);
            if (rel == null) continue;
            if (!StringUtils.hasText(rel.getMasterKey())) continue;
            boolean isArrayContains = "ARRAY_CONTAINS".equals(rel.getJoinType());

            StringBuilder slaveSql = new StringBuilder(
                "SELECT data_json::text FROM page_data WHERE data_slug = :slaveSlug AND is_deleted = false");
            Map<String, String> condParams = new LinkedHashMap<>();
            if (StringUtils.hasText(rel.getSlaveFilter())) {
                appendSlaveFilter(slaveSql, rel.getSlaveFilter(), condParams);
            }

            Query slaveQuery = entityManager.createNativeQuery(slaveSql.toString());
            slaveQuery.setParameter("slaveSlug", rel.getSlaveSlug());
            condParams.forEach(slaveQuery::setParameter);

            List<Object> slaveRows = slaveQuery.getResultList();
            Set<String> linkValues = new HashSet<>();
            for (Object row : slaveRows) {
                try {
                    Map<String, Object> dataJson = objectMapper.readValue(
                        row.toString(), new com.fasterxml.jackson.core.type.TypeReference<>() {});
                    String linkVal = extractField(dataJson, rel.getSlaveKey());
                    if (linkVal != null && !linkVal.isBlank()) linkValues.add(linkVal.trim());
                } catch (Exception e) {
                    log.warn("INNER RELATION 연동 slug 레코드 파싱 실패: {}", e.getMessage());
                }
            }

            if (linkValues.isEmpty()) {
                resultIds = new HashSet<>();
                break;
            }

            Set<Long> ids = new HashSet<>();
            StringBuilder masterSql = new StringBuilder("SELECT id FROM page_data WHERE data_slug = :masterSlug AND is_deleted = false");
            boolean runMasterQuery = true;

            if (isArrayContains) {
                if (!rel.getMasterKey().matches("[a-zA-Z0-9_.]+")) continue;
                String containsCond = buildArrayContainsCondition(rel.getMasterKey(), linkValues);
                if (containsCond.isBlank()) runMasterQuery = false;
                else masterSql.append(" AND (").append(containsCond).append(")");
            } else {
                String linkValueList = linkValues.stream()
                    .map(v -> "'" + v.replace("'", "''") + "'")
                    .collect(java.util.stream.Collectors.joining(","));
                appendSlaveKeyInCondition(masterSql, rel.getMasterKey(), linkValueList);
            }

            if (runMasterQuery) {
                Query masterQuery = entityManager.createNativeQuery(masterSql.toString());
                masterQuery.setParameter("masterSlug", rel.getMasterSlug());

                List<Object> masterRows = masterQuery.getResultList();
                for (Object row : masterRows) {
                    try { ids.add(((Number) row).longValue()); }
                    catch (Exception e) {  }
                }
            }

            if (resultIds == null) resultIds = new HashSet<>(ids);
            else resultIds.retainAll(ids);
        }

        return resultIds;
    }

    private Set<Long> collectCategoryAndDescendants(String slaveSlug, long catId, String slaveKey) {
        String[] keySegs = slaveKey.split("\\.");
        String notLinkRecord = "AND " + buildJsonPath(keySegs) + " IS NULL";

        Set<Long> allIds = new LinkedHashSet<>();
        allIds.add(catId);
        Set<Long> frontier = new HashSet<>();
        frontier.add(catId);

        for (int i = 0; i < 10 && !frontier.isEmpty(); i++) {
            String frontierList = frontier.stream()
                .map(id -> "'" + id + "'")
                .collect(java.util.stream.Collectors.joining(","));

            @SuppressWarnings("unchecked")
            List<Object> rows = entityManager.createNativeQuery(
                "SELECT id FROM page_data WHERE data_slug = :slug AND is_deleted = false"
                + " AND EXISTS ("
                + "   SELECT 1 FROM jsonb_each(data_json) kv"
                + "   WHERE jsonb_typeof(kv.value) = 'object'"
                + "   AND kv.value->>'parentId' IN (" + frontierList + ")"
                + " ) " + notLinkRecord
            ).setParameter("slug", slaveSlug).getResultList();

            Set<Long> newIds = new HashSet<>();
            for (Object row : rows) {
                try {
                    Long id = Long.parseLong(row.toString());
                    if (!allIds.contains(id)) newIds.add(id);
                } catch (NumberFormatException e) {  }
            }
            if (newIds.isEmpty()) break;
            allIds.addAll(newIds);
            frontier = newIds;
        }

        return allIds;
    }

    private void appendSlaveKeyInCondition(StringBuilder sql, String keyPath, String idList) {
        if (keyPath.contains(".")) {
            String[] segs = keyPath.split("\\.");
            sql.append(" AND ").append(buildJsonPath(segs)).append(" IN (").append(idList).append(")");
        } else {
            sql.append(" AND data_json->>'").append(keyPath).append("' IN (").append(idList).append(")");
        }
    }

    private Set<Long> parseFetchRelationIds(Map<String, String> allParams) {
        String raw = allParams.get("fetchRelationIds");
        if (raw == null || raw.isBlank()) return null;
        Set<Long> ids = new HashSet<>();
        for (String part : raw.split(",")) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) continue;
            try { ids.add(Long.parseLong(trimmed)); }
            catch (NumberFormatException e) {  }
        }
        return ids;
    }

    private List<PageDataResponse> applyFetch(String slug, List<PageDataResponse> content, Long siteId, Set<Long> restrictToRelationIds) {
        List<SlugRelation> allFetchRelations = slugRelationRepository.findByMasterSlugAndRelationDir(slug, "FETCH");
        List<SlugRelation> fetchRelations = restrictToRelationIds != null
            ? allFetchRelations.stream().filter(r -> restrictToRelationIds.contains(r.getId())).toList()
            : allFetchRelations;
        if (fetchRelations.isEmpty()) return content;

        Map<Long, Map<String, Object>> tableFetchCache = new HashMap<>();
        Map<Long, Map<String, Object>> categoryFetchCache = new HashMap<>();
        /* 같은 제품이 카테고리 여러 곳에 매핑된 경우, 라벨 텍스트(categoryFetchCache)만으로는 매핑을 구분 못 하므로
           매핑(depth3) 고유 id를 relationId별로 병렬 보관 — MultiSelectRenderer가 행(row) 단위로 구분해 쓴다 */
        Map<Long, Map<String, Object>> categoryFetchMappingIdCache = new HashMap<>();
        Map<String, Map<String, List<Map<String, Object>>>> groupedFetchCache = new HashMap<>();
        for (SlugRelation rel : fetchRelations) {
            boolean isArrayContains = "ARRAY_CONTAINS".equals(rel.getJoinType());
            boolean isCategory = "CATEGORY".equals(rel.getSlaveType());
            if (isArrayContains) {
                if (isCategory && StringUtils.hasText(rel.getFetchFields())) {
                    List<String> arrayValues = content.stream()
                        .flatMap(item -> extractFieldAsList(item.getDataJson(), rel.getMasterKey()).stream())
                        .filter(v -> v != null && !v.isBlank())
                        .distinct()
                        .toList();
                    categoryFetchCache.put(rel.getId(), batchResolveCategoryFetch(rel, arrayValues, siteId).labels());
                }
                continue;
            }

            List<String> masterValues = content.stream()
                .map(item -> extractField(item.getDataJson(), rel.getMasterKey()))
                .filter(v -> v != null && !v.isBlank())
                .distinct()
                .toList();

            if (isCategory) {
                if (StringUtils.hasText(rel.getFetchFields())) {
                    CategoryFetchResult categoryResult = batchResolveCategoryFetch(rel, masterValues, siteId);
                    categoryFetchCache.put(rel.getId(), categoryResult.labels());
                    categoryFetchMappingIdCache.put(rel.getId(), categoryResult.mappingIds());
                }
                continue;
            }

            /* slaveSlug/slaveKey/masterKey/slaveFilter가 같으면 조회 대상이 완전히 동일 —
               relation별로 fetchFields만 다르게 뽑아 쓰므로, DB 조회+그룹핑은 조합당 1번만 수행하고 공유한다 */
            String groupKey = rel.getSlaveSlug() + "#" + rel.getSlaveKey() + "#"
                + rel.getMasterKey() + "#" + (rel.getSlaveFilter() == null ? "" : rel.getSlaveFilter());
            Map<String, List<Map<String, Object>>> grouped = groupedFetchCache.computeIfAbsent(groupKey,
                k -> fetchSlaveGrouped(rel.getSlaveSlug(), rel.getSlaveKey(), rel.getSlaveFilter(), masterValues, siteId));
            tableFetchCache.put(rel.getId(), batchResolveTableFetch(grouped, rel));
        }

        return content.stream().map(item -> {
            Map<String, Object> enriched = new LinkedHashMap<>(item.getDataJson());
            for (SlugRelation rel : fetchRelations) {
                boolean isArrayContains = "ARRAY_CONTAINS".equals(rel.getJoinType());
                boolean isCategory = "CATEGORY".equals(rel.getSlaveType());

                Object fetchedValue;
                Object categoryMappingId = null;
                if (isArrayContains) {
                    List<String> masterValues = extractFieldAsList(item.getDataJson(), rel.getMasterKey());
                    if (masterValues.isEmpty()) continue;
                    if (isCategory) {
                        Map<String, Object> resolved = categoryFetchCache.get(rel.getId());
                        if (resolved == null || resolved.isEmpty()) continue;
                        List<String> names = collectCategoryNames(resolved, masterValues);
                        if (names.isEmpty()) continue;
                        fetchedValue = names.size() == 1 ? names.get(0) : names;
                    } else {
                        fetchedValue = resolveArrayContainsFetch(rel, masterValues);
                    }
                } else {
                    String masterValue = extractField(item.getDataJson(), rel.getMasterKey());
                    if (masterValue == null || masterValue.isBlank()) continue;

                    if (isCategory) {
                        fetchedValue = categoryFetchCache.containsKey(rel.getId())
                            ? categoryFetchCache.get(rel.getId()).get(masterValue)
                            : resolveCategoryFetch(rel, masterValue);
                        /* 라벨 텍스트와 별개로, 같은 제품이 카테고리 여러 곳에 매핑된 경우 FE가 행별로 구분해
                           선택할 수 있도록 매핑(depth3) 고유 id를 추가 키로 함께 내려준다 (기존 _fetchedRel{id} 값/형식 불변) */
                        categoryMappingId = categoryFetchMappingIdCache
                            .getOrDefault(rel.getId(), Collections.emptyMap())
                            .get(masterValue);
                    } else {
                        fetchedValue = tableFetchCache.getOrDefault(rel.getId(), Collections.emptyMap()).get(masterValue);
                    }
                }

                if (fetchedValue != null) {
                    enriched.put(buildFetchKey(rel.getId()), fetchedValue);
                    if ((!isArrayContains || isCategory) && fetchedValue instanceof List) {
                        String sep = StringUtils.hasText(rel.getFetchSeparator()) ? rel.getFetchSeparator() : ",";
                        enriched.put(buildFetchKey(rel.getId()) + "_sep", sep);
                    }
                }
                if (categoryMappingId != null) {
                    enriched.put(buildFetchKey(rel.getId()) + "_mappingId", categoryMappingId);
                }
            }
            return enriched.size() > item.getDataJson().size() ? item.withDataJson(enriched) : item;
        }).toList();
    }

    private Map<String, List<Map<String, Object>>> fetchSlaveGrouped(
            String slaveSlug, String slaveKey, String slaveFilter, List<String> masterValues, Long siteId) {
        if (masterValues.isEmpty()) return Collections.emptyMap();

        String idList = masterValues.stream().distinct()
            .map(v -> "'" + v + "'")
            .collect(java.util.stream.Collectors.joining(","));

        StringBuilder sql = new StringBuilder("SELECT data_json::text FROM page_data WHERE data_slug = :slaveSlug AND is_deleted = false");
        appendSlaveKeyInCondition(sql, slaveKey, idList);
        if (siteId != null) {
            sql.append(" AND (site_id = :siteId OR site_id IS NULL)");
        }
        Map<String, String> filterParams = new LinkedHashMap<>();
        if (slaveFilter != null && !slaveFilter.isBlank()) {
            appendSlaveFilter(sql, slaveFilter, filterParams);
        }

        Query q = entityManager.createNativeQuery(sql.toString());
        q.setParameter("slaveSlug", slaveSlug);
        if (siteId != null) {
            q.setParameter("siteId", siteId);
        }
        filterParams.forEach(q::setParameter);

        List<Object> results = q.getResultList();
        if (results.isEmpty()) return Collections.emptyMap();

        return groupSlaveRecordsByKey(results, slaveKey);
    }

    private Map<String, Object> batchResolveTableFetch(Map<String, List<Map<String, Object>>> grouped, SlugRelation rel) {
        if (grouped.isEmpty()) return Collections.emptyMap();

        boolean hasFetchFields = StringUtils.hasText(rel.getFetchFields());
        Map<String, Object> resultMap = new LinkedHashMap<>();
        for (Map.Entry<String, List<Map<String, Object>>> entry : grouped.entrySet()) {
            List<Map<String, Object>> matched = entry.getValue();
            if (!hasFetchFields) {
                resultMap.put(entry.getKey(), matched.get(0));
                continue;
            }
            List<String> values = new ArrayList<>();
            for (Map<String, Object> m : matched) {
                String val = extractField(m, rel.getFetchFields());
                if (val != null) values.add(val);
            }
            if (!values.isEmpty()) {
                resultMap.put(entry.getKey(), values.size() == 1 ? values.get(0) : values);
            }
        }
        return resultMap;
    }

    @SuppressWarnings("unchecked")
    /** labels = 화면 표시용 경로 텍스트(기존 동작 그대로), mappingIds = 같은 순서로 대응하는 매핑(depth3) 고유 id */
    private record CategoryFetchResult(Map<String, Object> labels, Map<String, Object> mappingIds) {}

    private CategoryFetchResult batchResolveCategoryFetch(SlugRelation rel, List<String> masterValues, Long siteId) {
        if (masterValues.isEmpty()) return new CategoryFetchResult(Collections.emptyMap(), Collections.emptyMap());

        int targetDepth = rel.getCategoryDepth() != null ? rel.getCategoryDepth() : 1;
        int fromDepth = rel.getCategoryDepthFrom() != null ? rel.getCategoryDepthFrom() : targetDepth;

        String masterIdList = masterValues.stream().distinct()
            .map(v -> "'" + v.replace("'", "''") + "'")
            .collect(java.util.stream.Collectors.joining(","));

        StringBuilder sql1 = new StringBuilder("SELECT data_json::text FROM page_data WHERE data_slug = :slaveSlug AND is_deleted = false");
        appendSlaveKeyInCondition(sql1, rel.getSlaveKey(), masterIdList);
        Map<String, String> filterParams = new LinkedHashMap<>();
        if (rel.getSlaveFilter() != null && !rel.getSlaveFilter().isBlank()) {
            appendSlaveFilter(sql1, rel.getSlaveFilter(), filterParams);
        }

        Query q1 = entityManager.createNativeQuery(sql1.toString());
        q1.setParameter("slaveSlug", rel.getSlaveSlug());
        filterParams.forEach(q1::setParameter);

        List<Object> r1 = q1.getResultList();
        if (r1.isEmpty()) return new CategoryFetchResult(Collections.emptyMap(), Collections.emptyMap());

        Map<String, List<Map<String, Object>>> grouped = groupSlaveRecordsByKey(r1, rel.getSlaveKey());
        if (grouped.isEmpty()) return new CategoryFetchResult(Collections.emptyMap(), Collections.emptyMap());

        List<String> rowIds = new ArrayList<>();
        Map<String, Map<String, Object>> leafRecords = new LinkedHashMap<>();
        Map<String, String> rowMasterValue = new LinkedHashMap<>();
        for (Map.Entry<String, List<Map<String, Object>>> entry : grouped.entrySet()) {
            String masterValue = entry.getKey();
            List<Map<String, Object>> records = entry.getValue();
            for (int i = 0; i < records.size(); i++) {
                String rowId = masterValue + "#" + i;
                rowIds.add(rowId);
                leafRecords.put(rowId, records.get(i));
                rowMasterValue.put(rowId, masterValue);
            }
        }

        Map<String, List<String>> pathAccumulator = new LinkedHashMap<>();
        Map<String, Map<String, Object>> cursor = new LinkedHashMap<>();
        Map<String, String> parentKeyPath = new LinkedHashMap<>();
        Set<String> active = new LinkedHashSet<>(rowIds);
        for (String rowId : rowIds) {
            pathAccumulator.put(rowId, new ArrayList<>());
            Map<String, Object> leaf = leafRecords.get(rowId);
            cursor.put(rowId, leaf);
            parentKeyPath.put(rowId, autoDetectParentKeyPath(leaf));
        }

        for (int guard = 0; guard < 10 && !active.isEmpty(); guard++) {
            Map<String, String> rowParentId = new LinkedHashMap<>();
            Set<String> parentIdSet = new LinkedHashSet<>();
            for (String rowId : active) {
                String pid = extractField(cursor.get(rowId), parentKeyPath.get(rowId));
                if (pid == null || pid.isBlank()) continue;
                rowParentId.put(rowId, pid);
                parentIdSet.add(pid);
            }
            if (rowParentId.isEmpty()) break;

            String parentIdList = parentIdSet.stream()
                .map(id -> "'" + id.replace("'", "''") + "'")
                .collect(java.util.stream.Collectors.joining(","));
            StringBuilder sqlLevel = new StringBuilder("SELECT data_json::text FROM page_data WHERE data_slug = :slaveSlug AND is_deleted = false");
            appendSlaveKeyInCondition(sqlLevel, "id", parentIdList);
            Query qLevel = entityManager.createNativeQuery(sqlLevel.toString());
            qLevel.setParameter("slaveSlug", rel.getSlaveSlug());
            List<Object> levelRows = qLevel.getResultList();

            Map<String, Map<String, Object>> byId = new LinkedHashMap<>();
            for (Object row : levelRows) {
                try {
                    Map<String, Object> dataJson = objectMapper.readValue(row.toString(),
                        new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
                    String id = extractField(dataJson, "id");
                    if (id != null) byId.put(id, dataJson);
                } catch (Exception e) {
                    log.warn("CATEGORY FETCH 배치 카테고리 레코드 파싱 실패: {}", e.getMessage());
                }
            }

            Set<String> nextActive = new LinkedHashSet<>();
            for (String rowId : active) {
                String pid = rowParentId.get(rowId);
                if (pid == null) continue;
                Map<String, Object> parentDataJson = byId.get(pid);
                if (parentDataJson == null) continue;

                String name = extractFieldMulti(parentDataJson, rel.getFetchFields());
                if (name != null) pathAccumulator.get(rowId).add(0, name);
                cursor.put(rowId, parentDataJson);
                parentKeyPath.put(rowId, autoDetectParentKeyPath(parentDataJson));
                nextActive.add(rowId);
            }
            active = nextActive;
        }

        boolean includeLeaf = Boolean.TRUE.equals(rel.getIncludeLeaf());
        if (includeLeaf) {
            Set<String> resolvedByCategoryTitle = new LinkedHashSet<>();
            for (String rowId : rowIds) {
                String categoryTitle = extractField(leafRecords.get(rowId), "category.title");
                if (categoryTitle == null || categoryTitle.isBlank()) continue;
                pathAccumulator.get(rowId).add(categoryTitle);
                resolvedByCategoryTitle.add(rowId);
            }

            Map<String, String> rowProductId = new LinkedHashMap<>();
            Set<String> productIdSet = new LinkedHashSet<>();
            for (String rowId : rowIds) {
                if (resolvedByCategoryTitle.contains(rowId)) continue;
                String productId = extractField(leafRecords.get(rowId), "product.id");
                if (productId == null || productId.isBlank()) continue;
                rowProductId.put(rowId, productId);
                productIdSet.add(productId);
            }

            if (!productIdSet.isEmpty()) {
                String productIdList = productIdSet.stream()
                    .map(id -> "'" + id.replace("'", "''") + "'")
                    .collect(java.util.stream.Collectors.joining(","));
                StringBuilder sqlProduct = new StringBuilder("SELECT data_json::text FROM page_data WHERE data_slug = :slaveSlug AND is_deleted = false");
                appendSlaveKeyInCondition(sqlProduct, "id", productIdList);
                Query qProduct = entityManager.createNativeQuery(sqlProduct.toString());
                qProduct.setParameter("slaveSlug", "product-data");
                List<Object> productRows = qProduct.getResultList();

                Map<String, Map<String, Object>> productById = new LinkedHashMap<>();
                for (Object row : productRows) {
                    try {
                        Map<String, Object> dataJson = objectMapper.readValue(row.toString(),
                            new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
                        String id = extractField(dataJson, "id");
                        if (id != null) productById.put(id, dataJson);
                    } catch (Exception e) {
                        log.warn("CATEGORY FETCH 리프 product-data 파싱 실패: {}", e.getMessage());
                    }
                }

                for (String rowId : rowIds) {
                    String productId = rowProductId.get(rowId);
                    if (productId == null) continue;
                    Map<String, Object> productDataJson = productById.get(productId);
                    if (productDataJson == null) continue;
                    String leafName = extractField(productDataJson, "product.product_name");
                    if (leafName != null) pathAccumulator.get(rowId).add(leafName);
                }
            }
        }

        Map<String, List<String>> resultsByMaster = new LinkedHashMap<>();
        /* 라벨과 같은 루프·같은 조건으로 쌓아 인덱스를 1:1로 맞춘다 (leafRecords가 곧 매핑(depth3) 자신) */
        Map<String, List<String>> mappingIdsByMaster = new LinkedHashMap<>();
        for (String rowId : rowIds) {
            List<String> fullPath = pathAccumulator.get(rowId);
            if (isCategoryChainBroken(leafRecords.get(rowId), fullPath.size(), includeLeaf)) continue;
            if (fullPath.isEmpty()) continue;
            int availableDepth = Math.min(fullPath.size(), targetDepth);
            List<String> rangeNames = new ArrayList<>();
            for (int d = Math.max(1, fromDepth); d <= availableDepth; d++) {
                rangeNames.add(fullPath.get(d - 1));
            }
            if (!rangeNames.isEmpty()) {
                resultsByMaster.computeIfAbsent(rowMasterValue.get(rowId), k -> new ArrayList<>())
                    .add(String.join(" > ", rangeNames));
                mappingIdsByMaster.computeIfAbsent(rowMasterValue.get(rowId), k -> new ArrayList<>())
                    .add(extractField(leafRecords.get(rowId), "id"));
            }
        }

        Map<String, Object> resultMap = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : resultsByMaster.entrySet()) {
            List<String> results = entry.getValue();
            if (results.isEmpty()) continue;
            resultMap.put(entry.getKey(), results.size() == 1 ? results.get(0) : results);
        }

        Map<String, Object> mappingIdMap = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : mappingIdsByMaster.entrySet()) {
            List<String> ids = entry.getValue();
            if (ids.isEmpty()) continue;
            mappingIdMap.put(entry.getKey(), ids.size() == 1 ? ids.get(0) : ids);
        }
        return new CategoryFetchResult(resultMap, mappingIdMap);
    }

    private List<String> collectCategoryNames(Map<String, Object> resolved, List<String> masterValues) {
        List<String> names = new ArrayList<>();
        Set<String> seenValues = new LinkedHashSet<>();
        for (String masterValue : masterValues) {
            if (masterValue == null || masterValue.isBlank()) continue;
            if (!seenValues.add(masterValue)) continue;
            Object cached = resolved.get(masterValue);
            if (cached == null) continue;
            if (cached instanceof List<?> list) {
                for (Object element : list) {
                    if (element == null) continue;
                    String name = element.toString();
                    if (!name.isBlank()) names.add(name);
                }
            } else {
                String name = cached.toString();
                if (!name.isBlank()) names.add(name);
            }
        }
        return names;
    }

    private void applyFetchBatch(String slug, List<Map<String, Object>> rows, List<Long> relationIds) {
        if (relationIds == null || relationIds.isEmpty() || rows.isEmpty()) return;

        for (Long relationId : relationIds) {
            SlugRelation rel = slugRelationRepository.findById(relationId).orElse(null);
            if (rel == null) {
                log.warn("applyFetchBatch: relationId={} 조회 실패, 건너뜀", relationId);
                continue;
            }
            if (!"FETCH".equals(rel.getRelationDir())) {
                log.warn("applyFetchBatch: relationId={}는 FETCH 방향이 아니므로 건너뜀 (relationDir={})", relationId, rel.getRelationDir());
                continue;
            }
            boolean isArrayContains = "ARRAY_CONTAINS".equals(rel.getJoinType());
            if ("CATEGORY".equals(rel.getSlaveType()) && !isArrayContains) {
                log.warn("applyFetchBatch: relationId={}는 CATEGORY 타입이라 배치 처리 대상이 아니므로 건너뜀", relationId);
                continue;
            }

            if (isArrayContains) {
                applyArrayContainsFetchBatch(rel, rows);
            } else {
                applyTableFetchBatch(rel, rows);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void applyTableFetchBatch(SlugRelation rel, List<Map<String, Object>> rows) {
        List<String> masterValues = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String v = extractField(row, rel.getMasterKey());
            if (v != null && v.matches("-?\\d+")) masterValues.add(v);
        }
        if (masterValues.isEmpty()) return;

        String idList = masterValues.stream().distinct()
            .map(v -> "'" + v + "'")
            .collect(java.util.stream.Collectors.joining(","));

        StringBuilder sql = new StringBuilder("SELECT data_json::text FROM page_data WHERE data_slug = :slaveSlug AND is_deleted = false");
        appendSlaveKeyInCondition(sql, rel.getSlaveKey(), idList);
        Map<String, String> filterParams = new LinkedHashMap<>();
        if (rel.getSlaveFilter() != null && !rel.getSlaveFilter().isBlank()) {
            appendSlaveFilter(sql, rel.getSlaveFilter(), filterParams);
        }

        Query q = entityManager.createNativeQuery(sql.toString());
        q.setParameter("slaveSlug", rel.getSlaveSlug());
        filterParams.forEach(q::setParameter);

        List<Object> results = q.getResultList();
        if (results.isEmpty()) return;

        Map<String, List<Map<String, Object>>> grouped = groupSlaveRecordsByKey(results, rel.getSlaveKey());
        if (grouped.isEmpty()) return;

        boolean hasFetchFields = StringUtils.hasText(rel.getFetchFields());
        String separator = StringUtils.hasText(rel.getFetchSeparator()) ? rel.getFetchSeparator() : ",";
        String fetchKey = buildFetchKey(rel.getId());

        for (Map<String, Object> row : rows) {
            String masterValue = extractField(row, rel.getMasterKey());
            if (masterValue == null) continue;
            List<Map<String, Object>> matched = grouped.get(masterValue);
            if (matched == null || matched.isEmpty()) continue;

            Object fetchedValue = buildFetchedValue(matched, hasFetchFields, rel.getFetchFields(), separator);
            if (fetchedValue != null) row.put(fetchKey, fetchedValue);
        }
    }

    @SuppressWarnings("unchecked")
    private void applyArrayContainsFetchBatch(SlugRelation rel, List<Map<String, Object>> rows) {
        boolean isCategory = "CATEGORY".equals(rel.getSlaveType());
        List<List<String>> rowMasterValues = new ArrayList<>(rows.size());
        List<String> allValues = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            List<String> extracted = extractFieldAsList(row, rel.getMasterKey());
            List<String> values = isCategory
                ? extracted
                : extracted.stream().filter(v -> v.matches("-?\\d+")).toList();
            rowMasterValues.add(values);
            allValues.addAll(values);
        }
        if (allValues.isEmpty()) return;

        if (isCategory) {
            applyArrayContainsCategoryFetchBatch(rel, rows, rowMasterValues, allValues);
            return;
        }

        String idList = allValues.stream().distinct()
            .map(v -> "'" + v + "'")
            .collect(java.util.stream.Collectors.joining(","));

        StringBuilder sql = new StringBuilder("SELECT data_json::text FROM page_data WHERE data_slug = :slaveSlug AND is_deleted = false");
        appendSlaveKeyInCondition(sql, rel.getSlaveKey(), idList);
        Map<String, String> filterParams = new LinkedHashMap<>();
        if (rel.getSlaveFilter() != null && !rel.getSlaveFilter().isBlank()) {
            appendSlaveFilter(sql, rel.getSlaveFilter(), filterParams);
        }

        Query q = entityManager.createNativeQuery(sql.toString());
        q.setParameter("slaveSlug", rel.getSlaveSlug());
        filterParams.forEach(q::setParameter);

        List<Object> results = q.getResultList();
        if (results.isEmpty()) return;

        Map<String, List<Map<String, Object>>> grouped = groupSlaveRecordsByKey(results, rel.getSlaveKey());
        if (grouped.isEmpty()) return;

        boolean hasFetchFields = StringUtils.hasText(rel.getFetchFields());
        String separator = StringUtils.hasText(rel.getFetchSeparator()) ? rel.getFetchSeparator() : ",";
        String fetchKey = buildFetchKey(rel.getId());

        for (int i = 0; i < rows.size(); i++) {
            List<String> values = rowMasterValues.get(i);
            if (values.isEmpty()) continue;

            List<Map<String, Object>> matched = new ArrayList<>();
            for (String v : values) {
                List<Map<String, Object>> g = grouped.get(v);
                if (g != null) matched.addAll(g);
            }
            if (matched.isEmpty()) continue;

            Object fetchedValue = buildFetchedValue(matched, hasFetchFields, rel.getFetchFields(), separator);
            if (fetchedValue != null) rows.get(i).put(fetchKey, fetchedValue);
        }
    }

    private void applyArrayContainsCategoryFetchBatch(SlugRelation rel, List<Map<String, Object>> rows,
                                                      List<List<String>> rowMasterValues, List<String> allValues) {
        if (!StringUtils.hasText(rel.getFetchFields())) {
            log.warn("applyArrayContainsCategoryFetchBatch: relationId={}는 fetchFields가 없어 건너뜀", rel.getId());
            return;
        }

        Map<String, Object> resolved = batchResolveCategoryFetch(rel, allValues.stream().distinct().toList(), null).labels();
        if (resolved.isEmpty()) return;

        String separator = StringUtils.hasText(rel.getFetchSeparator()) ? rel.getFetchSeparator() : ",";
        String fetchKey = buildFetchKey(rel.getId());

        for (int i = 0; i < rows.size(); i++) {
            List<String> values = rowMasterValues.get(i);
            if (values.isEmpty()) continue;
            List<String> names = collectCategoryNames(resolved, values);
            if (names.isEmpty()) continue;
            rows.get(i).put(fetchKey, String.join(separator, names));
        }
    }

    private Map<String, List<Map<String, Object>>> groupSlaveRecordsByKey(List<Object> results, String slaveKey) {
        Map<String, List<Map<String, Object>>> grouped = new LinkedHashMap<>();
        for (Object row : results) {
            try {
                Map<String, Object> dataJson = objectMapper.readValue(row.toString(),
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
                String key = extractField(dataJson, slaveKey);
                if (key == null) continue;
                grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(dataJson);
            } catch (Exception e) {
                log.warn("applyFetchBatch slave dataJson 파싱 실패: {}", e.getMessage());
            }
        }
        return grouped;
    }

    private Object buildFetchedValue(List<Map<String, Object>> matched, boolean hasFetchFields, String fetchFields, String separator) {
        if (!hasFetchFields) {
            return matched.get(0);
        }
        List<String> values = new ArrayList<>();
        for (Map<String, Object> m : matched) {
            String v = extractField(m, fetchFields);
            if (v != null) values.add(v);
        }
        return values.isEmpty() ? null : String.join(separator, values);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> resolveArrayContainsFetch(SlugRelation rel, List<String> masterValues) {
        String idList = masterValues.stream()
            .filter(v -> v.matches("-?\\d+"))
            .map(v -> "'" + v + "'")
            .collect(java.util.stream.Collectors.joining(","));
        if (idList.isBlank()) return null;

        StringBuilder sql = new StringBuilder("SELECT data_json::text FROM page_data WHERE data_slug = :slaveSlug AND is_deleted = false");
        appendSlaveKeyInCondition(sql, rel.getSlaveKey(), idList);
        Map<String, String> filterParams = new LinkedHashMap<>();
        if (rel.getSlaveFilter() != null && !rel.getSlaveFilter().isBlank()) {
            appendSlaveFilter(sql, rel.getSlaveFilter(), filterParams);
        }
        sql.append(" ORDER BY array_position(ARRAY[").append(idList).append("]::text[], ")
           .append(buildSlaveKeyTextExpr(rel.getSlaveKey())).append(")");
        sql.append(" LIMIT 200");

        Query q = entityManager.createNativeQuery(sql.toString());
        q.setParameter("slaveSlug", rel.getSlaveSlug());
        filterParams.forEach(q::setParameter);

        List<Object> results = q.getResultList();
        if (results.isEmpty()) return null;

        List<Map<String, Object>> records = new ArrayList<>();
        for (Object row : results) {
            try {
                records.add(objectMapper.readValue(row.toString(),
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {}));
            } catch (Exception e) {
                log.warn("ARRAY_CONTAINS FETCH dataJson 파싱 실패: {}", e.getMessage());
            }
        }
        return records.isEmpty() ? null : records;
    }

    private String buildSlaveKeyTextExpr(String keyPath) {
        if (keyPath.contains(".")) {
            return buildJsonPath(keyPath.split("\\."));
        }
        return "data_json->>'" + keyPath + "'";
    }

    @SuppressWarnings("unchecked")
    private Object resolveCategoryFetch(SlugRelation rel, String masterValue) {
        int targetDepth = rel.getCategoryDepth() != null ? rel.getCategoryDepth() : 1;
        int fromDepth = rel.getCategoryDepthFrom() != null ? rel.getCategoryDepthFrom() : targetDepth;
        boolean hasFetchFields = StringUtils.hasText(rel.getFetchFields());


        StringBuilder sql1 = new StringBuilder("SELECT data_json::text FROM page_data WHERE data_slug = :slaveSlug AND is_deleted = false");
        appendSlaveKeyCondition(sql1, rel.getSlaveKey(), "masterValue");
        Map<String, String> filterParams = new LinkedHashMap<>();
        if (rel.getSlaveFilter() != null && !rel.getSlaveFilter().isBlank()) {
            appendSlaveFilter(sql1, rel.getSlaveFilter(), filterParams);
        }

        Query q1 = entityManager.createNativeQuery(sql1.toString());
        q1.setParameter("slaveSlug", rel.getSlaveSlug());
        q1.setParameter("masterValue", masterValue);
        filterParams.forEach(q1::setParameter);

        List<Object> r1 = q1.getResultList();
        if (r1.isEmpty()) return null;

        if (!hasFetchFields) {
            try {
                Map<String, Object> linkDataJson = objectMapper.readValue(r1.get(0).toString(),
                    new com.fasterxml.jackson.core.type.TypeReference<>() {});
                return collectCategoryRecordAtDepth(linkDataJson, rel.getSlaveSlug(), targetDepth);
            } catch (Exception e) {
                log.warn("CATEGORY FETCH 연결 레코드 파싱 실패: {}", e.getMessage());
                return null;
            }
        }

        boolean includeLeaf = Boolean.TRUE.equals(rel.getIncludeLeaf());
        List<String> results = new ArrayList<>();
        for (Object row : r1) {
            Map<String, Object> linkDataJson;
            try {
                linkDataJson = objectMapper.readValue(row.toString(), new com.fasterxml.jackson.core.type.TypeReference<>() {});
            } catch (Exception e) {
                log.warn("CATEGORY FETCH 연결 레코드 파싱 실패: {}", e.getMessage());
                continue;
            }
            List<String> fullPath = collectFullCategoryPath(linkDataJson, rel);
            if (isCategoryChainBroken(linkDataJson, fullPath.size(), includeLeaf)) continue;
            if (fullPath.isEmpty()) continue;
            int availableDepth = Math.min(fullPath.size(), targetDepth);
            List<String> rangeNames = new ArrayList<>();
            for (int d = Math.max(1, fromDepth); d <= availableDepth; d++) {
                rangeNames.add(fullPath.get(d - 1));
            }
            if (!rangeNames.isEmpty()) results.add(String.join(" > ", rangeNames));
        }
        if (results.isEmpty()) return null;
        return results.size() == 1 ? results.get(0) : results;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> collectCategoryRecordAtDepth(
            Map<String, Object> linkDataJson, String slaveSlug, int targetDepth) {

        List<Map<String, Object>> records = new ArrayList<>();
        Map<String, Object> cursor = linkDataJson;
        String currentParentKeyPath = autoDetectParentKeyPath(cursor);

        for (int guard = 0; guard < 10; guard++) {
            String parentId = extractField(cursor, currentParentKeyPath);
            if (parentId == null || parentId.isBlank()) break;

            String sql = "SELECT data_json::text FROM page_data WHERE data_slug = :slaveSlug"
                + " AND is_deleted = false"
                + " AND data_json->>'id' = :parentId LIMIT 1";
            Query q = entityManager.createNativeQuery(sql);
            q.setParameter("slaveSlug", slaveSlug);
            q.setParameter("parentId", parentId);

            List<Object> rows = q.getResultList();
            if (rows.isEmpty()) break;

            try {
                Map<String, Object> categoryRecord = objectMapper.readValue(rows.get(0).toString(),
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
                records.add(categoryRecord);
                cursor = categoryRecord;
                currentParentKeyPath = autoDetectParentKeyPath(categoryRecord);
            } catch (Exception e) {
                log.warn("CATEGORY FETCH 카테고리 레코드 파싱 실패: {}", e.getMessage());
                break;
            }
        }

        if (records.isEmpty()) return null;
        Collections.reverse(records);
        return records.size() >= targetDepth ? records.get(targetDepth - 1) : null;
    }

    @SuppressWarnings("unchecked")
    private String autoDetectParentKeyPath(Map<String, Object> dataJson) {
        if (dataJson.containsKey("parentId")) return "parentId";
        for (Map.Entry<String, Object> entry : dataJson.entrySet()) {
            if (entry.getValue() instanceof Map) {
                Map<String, Object> section = (Map<String, Object>) entry.getValue();
                if (section.containsKey("parentId")) {
                    return entry.getKey() + ".parentId";
                }
            }
        }
        return "parentId";
    }

    @SuppressWarnings("unchecked")
    private List<String> collectFullCategoryPath(
            Map<String, Object> linkDataJson, SlugRelation rel) {

        List<String> path = new ArrayList<>();
        Map<String, Object> cursor = linkDataJson;
        String currentParentKeyPath = autoDetectParentKeyPath(cursor);

        for (int guard = 0; guard < 10; guard++) {
            String parentId = extractField(cursor, currentParentKeyPath);
            if (parentId == null || parentId.isBlank()) break;

            String sql = "SELECT data_json::text FROM page_data WHERE data_slug = :slaveSlug"
                + " AND is_deleted = false"
                + " AND data_json->>'id' = :parentId LIMIT 1";
            Query q = entityManager.createNativeQuery(sql);
            q.setParameter("slaveSlug", rel.getSlaveSlug());
            q.setParameter("parentId", parentId);

            List<Object> rows = q.getResultList();
            if (rows.isEmpty()) break;

            try {
                Map<String, Object> parentDataJson = objectMapper.readValue(rows.get(0).toString(), new com.fasterxml.jackson.core.type.TypeReference<>() {});
                String name = extractFieldMulti(parentDataJson, rel.getFetchFields());
                if (name != null) path.add(0, name);
                cursor = parentDataJson;
                currentParentKeyPath = autoDetectParentKeyPath(parentDataJson);
            } catch (Exception e) {
                log.warn("CATEGORY FETCH 카테고리 경로 수집 실패: {}", e.getMessage());
                break;
            }
        }

        if (Boolean.TRUE.equals(rel.getIncludeLeaf())) {
            String categoryTitle = extractField(linkDataJson, "category.title");
            if (categoryTitle != null && !categoryTitle.isBlank()) {
                path.add(categoryTitle);
            } else {
                String productId = extractField(linkDataJson, "product.id");
                if (productId != null && !productId.isBlank()) {
                    String sqlProduct = "SELECT data_json::text FROM page_data WHERE data_slug = 'product-data'"
                        + " AND is_deleted = false"
                        + " AND data_json->>'id' = :productId LIMIT 1";
                    Query qProduct = entityManager.createNativeQuery(sqlProduct);
                    qProduct.setParameter("productId", productId);
                    List<Object> productRows = qProduct.getResultList();
                    if (!productRows.isEmpty()) {
                        try {
                            Map<String, Object> productDataJson = objectMapper.readValue(productRows.get(0).toString(),
                                new com.fasterxml.jackson.core.type.TypeReference<>() {});
                            String leafName = extractField(productDataJson, "product.product_name");
                            if (leafName != null) path.add(leafName);
                        } catch (Exception e) {
                            log.warn("CATEGORY FETCH 리프 product-data 파싱 실패: {}", e.getMessage());
                        }
                    }
                }
            }
        }

        return path;
    }

    private void appendSlaveKeyCondition(StringBuilder sql, String slaveKey, String paramName) {
        if (slaveKey.contains(".")) {
            String[] segs = slaveKey.split("\\.");
            sql.append(" AND ").append(buildJsonPath(segs)).append(" = :").append(paramName);
        } else {
            sql.append(" AND data_json->>'").append(slaveKey).append("' = :").append(paramName);
        }
    }

    private void appendSlaveFilter(StringBuilder sql, String slaveFilter, Map<String, String> params) {
        for (String cond : slaveFilter.split("&")) {
            int eqIdx = cond.indexOf('=');
            int tildeIdx = cond.indexOf('~');
            boolean ilike = tildeIdx >= 0 && (eqIdx < 0 || tildeIdx < eqIdx);

            String k;
            String v;
            if (ilike) {
                k = cond.substring(0, tildeIdx).trim();
                v = cond.substring(tildeIdx + 1).trim();
            } else {
                String[] kv = cond.split("=", 2);
                if (kv.length != 2) continue;
                k = kv[0].trim();
                v = kv[1].trim();
            }
            if (!k.matches("[a-zA-Z0-9_.]+")) continue;

            String paramName = "sf_" + k.replace(".", "_") + (ilike ? "_ilike" : "");
            String op = ilike ? "ILIKE" : "=";
            if (k.contains(".")) {
                String[] segs = k.split("\\.");
                if (!isValidSegments(segs)) continue;
                sql.append(" AND ").append(buildJsonPath(segs)).append(" ").append(op).append(" :").append(paramName);
            } else {
                sql.append(" AND (data_json->>'").append(k).append("' ").append(op).append(" :").append(paramName)
                   .append(" OR EXISTS (SELECT 1 FROM jsonb_each(data_json) kv")
                   .append(" WHERE jsonb_typeof(kv.value) = 'object' AND kv.value->>'").append(k).append("' ").append(op).append(" :").append(paramName).append("))");
            }
            params.put(paramName, ilike ? "%" + v + "%" : v);
        }
    }

    @SuppressWarnings("unchecked")
    private String extractField(Map<String, Object> dataJson, String fieldPath) {
        if (dataJson == null || fieldPath == null || fieldPath.isBlank()) return null;
        String[] segs = fieldPath.split("\\.");
        Object current = dataJson;
        for (String seg : segs) {
            if (!(current instanceof Map)) return null;
            current = ((Map<String, Object>) current).get(seg);
        }
        if (current != null) return current.toString();

        if (segs.length == 1) {
            List<Object> matches = new ArrayList<>();
            collectFieldMatches(dataJson, segs[0], matches);
            if (matches.size() == 1) {
                Object val = matches.get(0);
                return val != null ? val.toString() : null;
            }
        }
        return null;
    }

    private String extractFieldMulti(Map<String, Object> dataJson, String csvFieldPaths) {
        if (dataJson == null || csvFieldPaths == null || csvFieldPaths.isBlank()) return null;
        for (String fieldPath : csvFieldPaths.split(",")) {
            String trimmed = fieldPath.trim();
            if (trimmed.isEmpty()) continue;
            String value = extractField(dataJson, trimmed);
            if (value != null) return value;
        }
        return null;
    }

    private boolean isCategoryChainBroken(Map<String, Object> selfDataJson, int fullPathSize, boolean includeLeaf) {
        String raw = extractFieldMulti(selfDataJson, "category.depth,product.depth");
        if (raw == null) return false;
        int selfDepth;
        try {
            selfDepth = Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return false;
        }
        int expectedSize = includeLeaf ? selfDepth : selfDepth - 1;
        return fullPathSize != expectedSize;
    }

    @SuppressWarnings("unchecked")
    private List<String> extractFieldAsList(Map<String, Object> dataJson, String fieldPath) {
        if (dataJson == null || fieldPath == null || fieldPath.isBlank()) return List.of();
        String[] segs = fieldPath.split("\\.");
        Object current = dataJson;
        for (String seg : segs) {
            if (!(current instanceof Map)) return List.of();
            current = ((Map<String, Object>) current).get(seg);
        }
        if (current instanceof List<?> list) {
            Set<String> ordered = new LinkedHashSet<>();
            for (Object item : list) {
                if (item == null) continue;
                String value;
                if (item instanceof Map) {
                    Map<String, Object> m = (Map<String, Object>) item;
                    Object v = m.containsKey("productId") ? m.get("productId") : m.get("id");
                    value = v == null ? null : String.valueOf(v);
                } else {
                    value = item.toString();
                }
                if (value != null && !value.isBlank()) {
                    ordered.add(value);
                }
            }
            return List.copyOf(ordered);
        }
        if (current != null) return List.of(current.toString());
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private void collectFieldMatches(Map<String, Object> map, String fieldKey, List<Object> matches) {
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (entry.getKey().equals(fieldKey)) {
                matches.add(entry.getValue());
            }
            if (entry.getValue() instanceof Map) {
                collectFieldMatches((Map<String, Object>) entry.getValue(), fieldKey, matches);
            }
        }
    }

    private String buildFetchKey(Long relationId) {
        return "_fetchedRel" + relationId;
    }

  private PageDataListResponse buildEmptyResponse(int page, int size) {
    return PageDataListResponse.builder()
                .content(Collections.emptyList())
                .totalElements(0)
                .totalPages(0)
                .page(page)
                .size(size)
                .last(true)
                .first(true)
                .build();
  }
}
