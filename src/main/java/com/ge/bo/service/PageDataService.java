package com.ge.bo.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ge.bo.common.context.SiteTimeZoneResolver;
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
import java.util.*;
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

  @PersistenceContext
    private EntityManager entityManager;

  private static final Set<String> RESERVED_PARAMS = Set.of("page", "size", "sort", "unpaged", "exclude", "fetchRelationIds");

  private static final String PRODUCT_DATA_SLUG_COND = "#slug == 'product-data'";

  private static final String CONTENTS_DATA_SLUG_COND =
      "#slug == 'blog-data' or #slug == 'press-data' or #slug == 'articles-data'";

  private static final String CATEGORY_LV2_CTE =
        "WITH visible_lv2 AS ("
      + "  SELECT c.id AS id, c.data_json AS data_json"
      + "  FROM page_data c"
      + "  WHERE c.data_slug = 'category-data'"
      + "   AND c.data_json->'category'->>'parentId' = :categoryId"
      + "   AND c.data_json->'category'->>'depth'    = '2'"
      + "   AND c.data_json->'category'->>'is_visible' = '001'"
      + "   AND (c.site_id = :siteId OR c.site_id IS NULL)"
      + "), visible_product AS ("
      + "  SELECT DISTINCT v.id AS lv2_id, p.id AS product_id"
      + "  FROM visible_lv2 v"
      + "  JOIN page_data j"
      + "    ON j.data_slug = 'category-data'"
      + "   AND j.data_json->'product'->>'depth'    = '3'"
      + "   AND j.data_json->'product'->>'parentId' = v.id::text"
      + "   AND (j.site_id = :siteId OR j.site_id IS NULL)"
      + "  JOIN page_data p"
      + "    ON p.data_slug = 'product-data'"
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
      + "   AND p.id::text = j.data_json->'product'->>'id'"
      + "   AND (p.site_id = :siteId OR p.site_id IS NULL)"
      + "   AND p.data_json->'product'->>'is_visible'   = '001'"
      + "   AND p.data_json->'product'->>'order_status' = '01'"
      + "  WHERE j.data_slug = 'category-data'"
      + "   AND j.data_json->'product'->>'depth'    = '3'"
      + "   AND j.data_json->'product'->>'parentId' = :categoryId"
      + "   AND (j.site_id = :siteId OR j.site_id IS NULL)"
      + "  ORDER BY p.id,"
      + "   CASE WHEN j.data_json->>'sortOrder' ~ '^[0-9]+$' THEN (j.data_json->>'sortOrder')::int END ASC NULLS LAST"
      + ")";

  @Transactional(readOnly = true)
    public PageDataListResponse search(String slug, Map<String, String> allParams, int page, int size, Long siteId) {
    return search(slug, allParams, page, size, siteId, false);
  }

  @Transactional(readOnly = true)
    public PageDataListResponse search(String slug, Map<String, String> allParams, int page, int size, Long siteId, boolean unpaged) {
    Map<String, String> relFilterParams = new LinkedHashMap<>();
    Map<String, String> joinFilterParams = new LinkedHashMap<>();
    Map<String, String> innerRelParams = new LinkedHashMap<>();
    Map<String, String> searchParams = new LinkedHashMap<>();
    allParams.forEach((key, value) -> {
      if (RESERVED_PARAMS.contains(key) || value == null || value.isBlank()) return;
      if (key.startsWith("rel_")) relFilterParams.put(key, value);
      else if (key.startsWith("joinr_") || key.startsWith("joink_") || key.startsWith("joinv_")) joinFilterParams.put(key, value);
      else if (key.startsWith("innerRel_")) innerRelParams.put(key, value);
      else searchParams.put(key, value);
    });

    StringBuilder whereClause = new StringBuilder("WHERE data_slug = :slug");
    if (siteId != null) {
      whereClause.append(" AND (site_id = :siteId OR site_id IS NULL)");
    }
    appendWhereConditions(whereClause, searchParams);

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

    long totalElements = -1;
    if (!unpaged) {
      String countSql = "SELECT COUNT(*) FROM page_data " + whereClause;
      Query countQuery = entityManager.createNativeQuery(countSql);
      countQuery.setParameter("slug", slug);
      if (siteId != null) {
        countQuery.setParameter("siteId", siteId);
      }
      bindSearchParams(countQuery, searchParams);
      bindTodayIfPresent(countQuery, countSql, siteId);
      bindNowIfPresent(countQuery, countSql, siteId);
      totalElements = ((Number) countQuery.getSingleResult()).longValue();

      if (totalElements == 0) {
        return buildEmptyResponse(page, size);
      }
    }

    String orderBy = " ORDER BY created_at DESC";
    String sortParam = allParams.get("sort");
    if (sortParam != null && !sortParam.isBlank()) {
      String[] parts = sortParam.split(",", 2);
      String sortCol = parts[0].trim();
      String sortDir = parts.length > 1 && "desc".equalsIgnoreCase(parts[1].trim()) ? "DESC" : "ASC";
      if (sortCol.contains(".")) {
        String[] segs = sortCol.split("\\.");
        if (isValidSegments(segs)) {
          orderBy = " ORDER BY " + buildJsonPath(segs) + " " + sortDir;
        }
      } else if (sortCol.matches("[a-zA-Z0-9_]+")) {
        String auditCol = toAuditColumn(sortCol);
        if (auditCol != null) {
          orderBy = " ORDER BY " + auditCol + " " + sortDir;
        } else {
          orderBy = " ORDER BY data_json->>'" + sortCol + "' " + sortDir;
        }
      }
    }

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
    bindSearchParams(dataQuery, searchParams);
    bindTodayIfPresent(dataQuery, dataSql, siteId);
    bindNowIfPresent(dataQuery, dataSql, siteId);

    @SuppressWarnings("unchecked")
        List<Object[]> rows = dataQuery.getResultList();

    Map<Long, String> userNameMap = buildUserNameMap(rows, 4, 6);

    List<PageDataResponse> content = rows.stream()
                .map(row -> mapRowToResponse(row, userNameMap))
                .toList();

    applyExclude(content, allParams.get("exclude"));

    content = applyFetch(slug, content, siteId, parseFetchRelationIds(allParams));

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
    Map<String, String> searchParams = new LinkedHashMap<>();
    allParams.forEach((key, value) -> {
      if (RESERVED_PARAMS.contains(key) || value == null || value.isBlank()) return;
      if (key.startsWith("rel_")) relFilterParams.put(key, value);
      else if (key.startsWith("joinr_") || key.startsWith("joink_") || key.startsWith("joinv_")) joinFilterParams.put(key, value);
      else if (key.startsWith("innerRel_")) innerRelParams.put(key, value);
      else searchParams.put(key, value);
    });

    StringBuilder whereClause = new StringBuilder("WHERE data_slug = :slug");
    if (siteId != null) {
      whereClause.append(" AND (site_id = :siteId OR site_id IS NULL)");
    }
    appendWhereConditionsDatetime(whereClause, searchParams);

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

    long totalElements = -1;
    if (!unpaged) {
      String countSql = "SELECT COUNT(*) FROM page_data " + whereClause;
      Query countQuery = entityManager.createNativeQuery(countSql);
      countQuery.setParameter("slug", slug);
      if (siteId != null) {
        countQuery.setParameter("siteId", siteId);
      }
      bindSearchParams(countQuery, searchParams);
      bindTodayIfPresent(countQuery, countSql, siteId);
      bindNowIfPresent(countQuery, countSql, siteId);
      totalElements = ((Number) countQuery.getSingleResult()).longValue();

      if (totalElements == 0) {
        return buildEmptyResponse(page, size);
      }
    }

    String orderBy = " ORDER BY created_at DESC";
    String sortParam = allParams.get("sort");
    if (sortParam != null && !sortParam.isBlank()) {
      String[] parts = sortParam.split(",", 2);
      String sortCol = parts[0].trim();
      String sortDir = parts.length > 1 && "desc".equalsIgnoreCase(parts[1].trim()) ? "DESC" : "ASC";
      if (sortCol.contains(".")) {
        String[] segs = sortCol.split("\\.");
        if (isValidSegments(segs)) {
          orderBy = " ORDER BY " + buildJsonPath(segs) + " " + sortDir;
        }
      } else if (sortCol.matches("[a-zA-Z0-9_]+")) {
        String auditCol = toAuditColumn(sortCol);
        if (auditCol != null) {
          orderBy = " ORDER BY " + auditCol + " " + sortDir;
        } else {
          orderBy = " ORDER BY data_json->>'" + sortCol + "' " + sortDir;
        }
      }
    }

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
    bindSearchParams(dataQuery, searchParams);
    bindTodayIfPresent(dataQuery, dataSql, siteId);
    bindNowIfPresent(dataQuery, dataSql, siteId);

    @SuppressWarnings("unchecked")
        List<Object[]> rows = dataQuery.getResultList();

    Map<Long, String> userNameMap = buildUserNameMap(rows, 4, 6);

    List<PageDataResponse> content = rows.stream()
                .map(row -> mapRowToResponse(row, userNameMap))
                .toList();

    applyExclude(content, allParams.get("exclude"));
    content = applyFetch(slug, content, siteId, parseFetchRelationIds(allParams));

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

    StringBuilder whereClause = new StringBuilder("WHERE data_slug = :slug AND id = :id");
    if (siteId != null) {
      whereClause.append(" AND (site_id = :siteId OR site_id IS NULL)");
    }
    appendWhereConditions(whereClause, statusParams);

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
    bindSearchParams(dataQuery, statusParams);
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

    StringBuilder baseWhere = new StringBuilder("WHERE data_slug = :slug");
    if (siteId != null) {
      baseWhere.append(" AND (site_id = :siteId OR site_id IS NULL)");
    }
    appendWhereConditions(baseWhere, statusParams);

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
        + "  p.data_json->'product'->>'order_status'            AS product_order_status"
        + " FROM page_data c"
        + " LEFT JOIN page_data p"
        + "  ON p.data_slug = 'product-data'"
        + " AND p.id = (c.data_json->'product'->>'id')::bigint"
        + " AND p.data_json->'product'->>'is_visible' = '001'"
        + " AND (p.site_id = :siteId OR p.site_id IS NULL)"
        + " WHERE c.data_slug = 'category-data'"
        + "  AND CASE WHEN jsonb_exists(c.data_json, 'category')"
        + "           THEN c.data_json->'category'->>'is_visible' = '001'"
        + "           ELSE true"
        + "      END"
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
          row[12] != null ? row[12].toString() : null
      ));
    }
    return result;
  }

    @Transactional(readOnly = true)
    public Optional<String> findProductManagerEmail(Long productId, Long siteId) {
        String sql = "SELECT data_json->'product_manager'->>'email'"
            + " FROM page_data"
            + " WHERE data_slug = 'productManager-data'"
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
        String whereClause = " WHERE pd.data_slug = 'product-data'"
            + "  AND pd.data_json->'product'->>'is_visible' = '001'"
            + siteCond;
        if (hasKeyword) {
            whereClause += "  AND ( pd.data_json->'product'->>'product_name'        ILIKE :kw ESCAPE '\\'"
                + "     OR pd.data_json->'product'->>'product_description' ILIKE :kw ESCAPE '\\' )";
        }
        if (hasCategories) {
            String junctionSiteCond = siteId != null ? " AND (j.site_id = :siteId OR j.site_id IS NULL)" : "";
            whereClause += " AND EXISTS ("
                + " SELECT 1 FROM page_data j"
                + " WHERE j.data_slug = 'category-data'"
                + "  AND j.data_json->'product'->>'depth' = '3'"
                + "  AND (j.data_json->'product'->>'id')::bigint = pd.id"
                + "  AND (j.data_json->'product'->>'parentId')::bigint IN (:categoryIds)"
                + junctionSiteCond
                + " )";
        }

        String countSql = "SELECT COUNT(*)" + fromClause + whereClause;
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
        String j3SiteCond = siteId != null ? " AND (j3.site_id = :siteId OR j3.site_id IS NULL)" : "";
        String lv2SiteCond = siteId != null ? " AND (lv2.site_id = :siteId OR lv2.site_id IS NULL)" : "";
        String lv1SiteCond = siteId != null ? " AND (lv1.site_id = :siteId OR lv1.site_id IS NULL)" : "";
        String categoryJoin = " LEFT JOIN LATERAL ("
            + "  SELECT (j3.data_json->'product'->>'parentId')::bigint AS lv2_id"
            + "  FROM page_data j3"
            + "  WHERE j3.data_slug = 'category-data'"
            + "   AND j3.data_json->'product'->>'depth' = '3'"
            + "   AND (j3.data_json->'product'->>'id')::bigint = pd.id"
            + j3SiteCond
            + "  ORDER BY"
            + "   CASE WHEN j3.data_json->>'sortOrder' ~ '^[0-9]+$' THEN (j3.data_json->>'sortOrder')::int END ASC NULLS LAST,"
            + "   j3.id ASC"
            + "  LIMIT 1"
            + " ) pc ON true"
            + " LEFT JOIN page_data lv2 ON lv2.id = pc.lv2_id AND lv2.data_slug = 'category-data'" + lv2SiteCond
            + " LEFT JOIN page_data lv1 ON lv1.id = (lv2.data_json->'category'->>'parentId')::bigint AND lv1.data_slug = 'category-data'" + lv1SiteCond;
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
        String junctionSiteCond = siteId != null ? " AND (j.site_id = :siteId OR j.site_id IS NULL)" : "";
        String sql = "SELECT (j.data_json->'product'->>'parentId')::bigint AS category_l2_id,"
            + "       count(DISTINCT p.id)::int AS cnt"
            + " FROM page_data p"
            + " JOIN page_data j ON j.data_slug = 'category-data'"
            + "   AND j.data_json->'product'->>'depth' = '3'"
            + "   AND (j.data_json->'product'->>'id')::bigint = p.id"
            + junctionSiteCond
            + " WHERE p.data_slug = 'product-data'"
            + "   AND p.data_json->'product'->>'is_visible' = '001'"
            + productSiteCond;
        if (hasKeyword) {
            sql += "  AND ( p.data_json->'product'->>'product_name'        ILIKE :kw ESCAPE '\\'"
                + "     OR p.data_json->'product'->>'product_description' ILIKE :kw ESCAPE '\\' )";
        }
        sql += " GROUP BY (j.data_json->'product'->>'parentId')::bigint";

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
          + "  AND lv1.id::text = lv2.data_json->'category'->>'parentId'"
          + " JOIN page_data j"
          + "   ON j.data_slug = 'category-data'"
          + "  AND j.data_json->'product'->>'depth' = '3'"
          + "  AND j.data_json->'product'->>'parentId' = lv2.id::text"
          + "  AND (j.site_id = :siteId OR j.site_id IS NULL)"
          + " JOIN page_data p"
          + "   ON p.data_slug = 'product-data'"
          + "  AND p.id::text = j.data_json->'product'->>'id'"
          + (activeOnly ? "  AND p.data_json->'product'->>'has_training' = '001'"
                        + "  AND p.data_json->'product'->>'is_visible' = '001'" : "")
          + "  AND (p.site_id = :siteId OR p.site_id IS NULL)"
          + " WHERE lv2.data_slug = 'category-data'"
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
          + "  AND p.id::text = c.data_json->'product'->>'id'"
          + "  AND (p.site_id = :siteId OR p.site_id IS NULL)"
          + " WHERE c.data_slug = 'category-data'"
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
            + "    AND n.data_json->'product_list' @> to_jsonb(vp.product_id)"
            + " )"
            + " SELECT n.id, n.data_slug,"
            + "  " + section + "->>'title'        AS title,"
            + "  " + section + "->>'publish_dttm' AS publish_dttm,"
            + "  " + section + "->>'image'        AS image"
            + " FROM page_data n"
            + " JOIN matched m ON m.id = n.id"
            + " WHERE " + section + "->>'is_visible' = '001'"
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
      if (!key.startsWith("_fetchedRel")) cleaned.put(key, value);
    });
    return cleaned;
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

  @Transactional
    @Caching(put = {
        @CachePut(cacheNames = "productData", key = "#id", condition = PRODUCT_DATA_SLUG_COND),
        @CachePut(cacheNames = "contentsData", key = "#id", condition = CONTENTS_DATA_SLUG_COND)
    })
    public PageDataResponse patchField(String slug, Long id, String fieldKey, Object value) {
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
                "SELECT id FROM page_data WHERE data_slug = :slug");
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
    Map<String, String> searchParams = new LinkedHashMap<>();
    allParams.forEach((key, value) -> {
      if (RESERVED_PARAMS.contains(key) || value == null || value.isBlank()) return;
      if (key.startsWith("rel_")) relFilterParams.put(key, value);
      else if (key.startsWith("joinr_") || key.startsWith("joink_") || key.startsWith("joinv_")) joinFilterParams.put(key, value);
      else if (key.startsWith("innerRel_")) innerRelParams.put(key, value);
      else searchParams.put(key, value);
    });

    StringBuilder whereClause = new StringBuilder("WHERE data_slug = :slug");
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

    String sql = "SELECT id FROM page_data " + whereClause;
    Query query = entityManager.createNativeQuery(sql);
    query.setParameter("slug", slug);
    if (siteId != null) {
      query.setParameter("siteId", siteId);
    }
    bindSearchParams(query, searchParams);
    bindTodayIfPresent(query, sql, siteId);
    bindNowIfPresent(query, sql, siteId);

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

    StringBuilder whereClause = new StringBuilder("WHERE data_slug = :slug");
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
    bindSearchParams(dataQuery, searchParams);
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
                "SELECT COUNT(*) FROM page_data WHERE data_slug = :slug");
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
                "SELECT COUNT(*) FROM page_data WHERE data_slug = :slug");
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
                "SELECT COUNT(*) FROM page_data WHERE data_slug = :slug");
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

  private void bindTodayIfPresent(Query query, String sql, Long siteId) {
    if (sql.contains(":today")) {
      query.setParameter("today", resolveTodayParam(siteId));
    }
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
          String fromSub = "substring(regexp_replace(" + fromPart + ", '[^0-9]', '', 'g'), 1, 8)";
          String toSub   = "substring(regexp_replace(" + toPart   + ", '[^0-9]', '', 'g'), 1, 8)";
          String today   = ":today";
          switch (value) {
            case "before":
              whereClause.append(" AND ").append(fromSub).append(" > ").append(today);
              break;
            case "in_range":
              whereClause.append(" AND ").append(fromSub).append(" <= ").append(today)
                         .append(" AND ").append(toSub).append(" >= ").append(today);
              break;
            case "after":
              whereClause.append(" AND ").append(toSub).append(" < ").append(today);
              break;
            default: break;
          }
        } else {
          if (!rangeKey.matches("[a-zA-Z0-9_]+")) return;
          String fromRoot   = "substring(regexp_replace(data_json->>'" + rangeKey + "_from', '[^0-9]', '', 'g'), 1, 8)";
          String toRoot     = "substring(regexp_replace(data_json->>'" + rangeKey + "_to', '[^0-9]', '', 'g'), 1, 8)";
          String fromNested = "substring(regexp_replace(kv.value->>'" + rangeKey + "_from', '[^0-9]', '', 'g'), 1, 8)";
          String toNested   = "substring(regexp_replace(kv.value->>'" + rangeKey + "_to', '[^0-9]', '', 'g'), 1, 8)";
          String today      = ":today";
          String nested     = " OR EXISTS (SELECT 1 FROM jsonb_each(data_json) kv WHERE jsonb_typeof(kv.value) = 'object' AND ";
          switch (value) {
            case "before":
              whereClause.append(" AND (")
                  .append(fromRoot).append(" > ").append(today)
                  .append(nested).append(fromNested).append(" > ").append(today).append(")")
                  .append(")");
              break;
            case "in_range":
              whereClause.append(" AND (")
                  .append(fromRoot).append(" <= ").append(today).append(" AND ").append(toRoot).append(" >= ").append(today)
                  .append(nested)
                  .append(fromNested).append(" <= ").append(today).append(" AND ").append(toNested).append(" >= ").append(today).append(")")
                  .append(")");
              break;
            case "after":
              whereClause.append(" AND (")
                  .append(toRoot).append(" < ").append(today)
                  .append(nested).append(toNested).append(" < ").append(today).append(")")
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

        String today = ":today";
        String now = ":nowValue";
        List<String> topParts = new ArrayList<>();
        List<String> nestedParts = new ArrayList<>();
        int idx = 0;
        for (CondToken t : tokens) {
          String pName = "p_cond_" + fk + "_" + idx;
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
        String condExpr = "((" + String.join(" AND ", topParts) + ")"
            + " OR EXISTS (SELECT 1 FROM jsonb_each(data_json) kv WHERE jsonb_typeof(kv.value) = 'object' AND ("
            + String.join(" AND ", nestedParts) + ")))";
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
          String fromDigits = "regexp_replace(" + fromPart + ", '[^0-9]', '', 'g')";
          String toDigits   = "regexp_replace(" + toPart   + ", '[^0-9]', '', 'g')";
          String fromCmp = "(CASE WHEN char_length(" + fromDigits + ") = 8 THEN " + fromDigits + " || '000000' ELSE " + fromDigits + " END)";
          String toCmp   = "(CASE WHEN char_length(" + toDigits   + ") = 8 THEN " + toDigits   + " || '235959' ELSE " + toDigits   + " END)";
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
          String fromRootDigits   = "regexp_replace(data_json->>'" + rangeKey + "_from', '[^0-9]', '', 'g')";
          String toRootDigits     = "regexp_replace(data_json->>'" + rangeKey + "_to', '[^0-9]', '', 'g')";
          String fromNestedDigits = "regexp_replace(kv.value->>'" + rangeKey + "_from', '[^0-9]', '', 'g')";
          String toNestedDigits   = "regexp_replace(kv.value->>'" + rangeKey + "_to', '[^0-9]', '', 'g')";
          String fromRootCmp   = "(CASE WHEN char_length(" + fromRootDigits   + ") = 8 THEN " + fromRootDigits   + " || '000000' ELSE " + fromRootDigits   + " END)";
          String toRootCmp     = "(CASE WHEN char_length(" + toRootDigits     + ") = 8 THEN " + toRootDigits     + " || '235959' ELSE " + toRootDigits     + " END)";
          String fromNestedCmp = "(CASE WHEN char_length(" + fromNestedDigits + ") = 8 THEN " + fromNestedDigits + " || '000000' ELSE " + fromNestedDigits + " END)";
          String toNestedCmp   = "(CASE WHEN char_length(" + toNestedDigits   + ") = 8 THEN " + toNestedDigits   + " || '235959' ELSE " + toNestedDigits   + " END)";
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

        String today = ":today";
        String now = ":nowValue";
        List<String> topParts = new ArrayList<>();
        List<String> nestedParts = new ArrayList<>();
        int idx = 0;
        for (CondToken t : tokens) {
          String pName = "p_cond_" + fk + "_" + idx;
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
        String condExpr = "((" + String.join(" AND ", topParts) + ")"
            + " OR EXISTS (SELECT 1 FROM jsonb_each(data_json) kv WHERE jsonb_typeof(kv.value) = 'object' AND ("
            + String.join(" AND ", nestedParts) + ")))";
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
    StringBuilder path = new StringBuilder("data_json");
    for (int i = 0; i < segments.length - 1; i++) {
      path.append("->'").append(segments[i]).append("'");
    }
    path.append("->>'").append(segments[segments.length - 1]).append("'");
    return path.toString();
  }

  private String buildJsonbContainerPath(String keyPath) {
    StringBuilder path = new StringBuilder("data_json");
    for (String seg : keyPath.split("\\.")) {
      path.append("->'").append(seg).append("'");
    }
    return path.toString();
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
    bindSearchParams(query, statusParams);
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

  private void bindSearchParams(Query query, Map<String, String> searchParams) {
    searchParams.forEach((key, value) -> {
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
        String normalized = normalizeDateFrom(value);
        String bindValue = toAuditDateColumn(baseKey) != null ? toIsoTimestamp(normalized) : normalized;
        query.setParameter("p_" + key, bindValue);
        return;
      }

      if (key.endsWith("_to") || key.endsWith("_lte")) {
        if (!key.matches("[a-zA-Z0-9_]+")) return;
        String baseKey = key.endsWith("_to") ? key.substring(0, key.length() - 3) : key.substring(0, key.length() - 4);
        String normalized = normalizeDateTo(value);
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
                "SELECT id, data_json::text FROM page_data WHERE data_slug = :slaveSlug");
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
                 WHERE data_slug = 'category-data' AND id = :categoryId
                UNION ALL
                SELECT p.id FROM page_data p
                 JOIN descendant_categories d
                   ON p.data_slug = 'category-data'
                  AND p.data_json->'category'->>'parentId' ~ '^[0-9]+$'
                  AND (p.data_json->'category'->>'parentId')::bigint = d.id
            )
            SELECT id FROM page_data
             WHERE data_slug = 'currDtlMgmt-data'
               AND (
                    EXISTS (SELECT 1 FROM jsonb_array_elements_text(data_json->'power_list') v
                             WHERE v ~ '^[0-9]+$' AND v::bigint IN (SELECT id FROM descendant_categories))
                 OR EXISTS (SELECT 1 FROM jsonb_array_elements_text(data_json->'automation_list') v
                             WHERE v ~ '^[0-9]+$' AND v::bigint IN (SELECT id FROM descendant_categories))
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
                "SELECT data_json::text FROM page_data WHERE data_slug = :slaveSlug");
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
            StringBuilder masterSql = new StringBuilder("SELECT id FROM page_data WHERE data_slug = :masterSlug");
            boolean runMasterQuery = true;

            if (isArrayContains) {
                if (!rel.getMasterKey().matches("[a-zA-Z0-9_.]+")) continue;
                String container = buildJsonbContainerPath(rel.getMasterKey());
                String containsCond = linkValues.stream()
                    .filter(v -> v.matches("-?\\d+"))
                    .map(v -> container + " @> '[" + v + "]'::jsonb")
                    .collect(java.util.stream.Collectors.joining(" OR "));
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
                "SELECT data_json::text FROM page_data WHERE data_slug = :slaveSlug");
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
            StringBuilder masterSql = new StringBuilder("SELECT id FROM page_data WHERE data_slug = :masterSlug");
            boolean runMasterQuery = true;

            if (isArrayContains) {
                if (!rel.getMasterKey().matches("[a-zA-Z0-9_.]+")) continue;
                String container = buildJsonbContainerPath(rel.getMasterKey());
                String containsCond = linkValues.stream()
                    .filter(v -> v.matches("-?\\d+"))
                    .map(v -> container + " @> '[" + v + "]'::jsonb")
                    .collect(java.util.stream.Collectors.joining(" OR "));
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
                "SELECT id FROM page_data WHERE data_slug = :slug"
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
                    categoryFetchCache.put(rel.getId(), batchResolveCategoryFetch(rel, arrayValues, siteId));
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
                    categoryFetchCache.put(rel.getId(), batchResolveCategoryFetch(rel, masterValues, siteId));
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

        StringBuilder sql = new StringBuilder("SELECT data_json::text FROM page_data WHERE data_slug = :slaveSlug");
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
    private Map<String, Object> batchResolveCategoryFetch(SlugRelation rel, List<String> masterValues, Long siteId) {
        if (masterValues.isEmpty()) return Collections.emptyMap();

        int targetDepth = rel.getCategoryDepth() != null ? rel.getCategoryDepth() : 1;
        int fromDepth = rel.getCategoryDepthFrom() != null ? rel.getCategoryDepthFrom() : targetDepth;

        String masterIdList = masterValues.stream().distinct()
            .map(v -> "'" + v.replace("'", "''") + "'")
            .collect(java.util.stream.Collectors.joining(","));

        StringBuilder sql1 = new StringBuilder("SELECT data_json::text FROM page_data WHERE data_slug = :slaveSlug");
        appendSlaveKeyInCondition(sql1, rel.getSlaveKey(), masterIdList);
        Map<String, String> filterParams = new LinkedHashMap<>();
        if (rel.getSlaveFilter() != null && !rel.getSlaveFilter().isBlank()) {
            appendSlaveFilter(sql1, rel.getSlaveFilter(), filterParams);
        }

        Query q1 = entityManager.createNativeQuery(sql1.toString());
        q1.setParameter("slaveSlug", rel.getSlaveSlug());
        filterParams.forEach(q1::setParameter);

        List<Object> r1 = q1.getResultList();
        if (r1.isEmpty()) return Collections.emptyMap();

        Map<String, List<Map<String, Object>>> grouped = groupSlaveRecordsByKey(r1, rel.getSlaveKey());
        if (grouped.isEmpty()) return Collections.emptyMap();

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
            StringBuilder sqlLevel = new StringBuilder("SELECT data_json::text FROM page_data WHERE data_slug = :slaveSlug");
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
                StringBuilder sqlProduct = new StringBuilder("SELECT data_json::text FROM page_data WHERE data_slug = :slaveSlug");
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
            }
        }

        Map<String, Object> resultMap = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : resultsByMaster.entrySet()) {
            List<String> results = entry.getValue();
            if (results.isEmpty()) continue;
            resultMap.put(entry.getKey(), results.size() == 1 ? results.get(0) : results);
        }
        return resultMap;
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

        StringBuilder sql = new StringBuilder("SELECT data_json::text FROM page_data WHERE data_slug = :slaveSlug");
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

        StringBuilder sql = new StringBuilder("SELECT data_json::text FROM page_data WHERE data_slug = :slaveSlug");
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

        Map<String, Object> resolved = batchResolveCategoryFetch(rel, allValues.stream().distinct().toList(), null);
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

        StringBuilder sql = new StringBuilder("SELECT data_json::text FROM page_data WHERE data_slug = :slaveSlug");
        appendSlaveKeyInCondition(sql, rel.getSlaveKey(), idList);
        Map<String, String> filterParams = new LinkedHashMap<>();
        if (rel.getSlaveFilter() != null && !rel.getSlaveFilter().isBlank()) {
            appendSlaveFilter(sql, rel.getSlaveFilter(), filterParams);
        }
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

    @SuppressWarnings("unchecked")
    private Object resolveCategoryFetch(SlugRelation rel, String masterValue) {
        int targetDepth = rel.getCategoryDepth() != null ? rel.getCategoryDepth() : 1;
        int fromDepth = rel.getCategoryDepthFrom() != null ? rel.getCategoryDepthFrom() : targetDepth;
        boolean hasFetchFields = StringUtils.hasText(rel.getFetchFields());


        StringBuilder sql1 = new StringBuilder("SELECT data_json::text FROM page_data WHERE data_slug = :slaveSlug");
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
            return list.stream()
                .filter(Objects::nonNull)
                .map(Object::toString)
                .filter(s -> !s.isBlank())
                .toList();
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
