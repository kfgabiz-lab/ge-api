package com.ge.bo.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ge.bo.dto.PageDataListResponse;
import com.ge.bo.dto.PageDataRequest;
import com.ge.bo.dto.PageDataResponse;
import com.ge.bo.entity.AdminUser;
import com.ge.bo.entity.PageData;
import com.ge.bo.entity.SlugRelation;
import com.ge.bo.exception.ErrorCode;
import com.ge.bo.repository.AdminRepository;
import com.ge.bo.repository.PageDataRepository;
import com.ge.bo.repository.SlugRelationRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.*;

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
  private final AdminRepository adminRepository;
  private final ObjectMapper objectMapper;
  private final PageFileService pageFileService;
  private final SlugRelationRepository slugRelationRepository;

  @PersistenceContext
    private EntityManager entityManager;

    /** 예약 파라미터 — 검색 조건에서 제외 */
  private static final Set<String> RESERVED_PARAMS = Set.of("page", "size", "sort");

    /**
     * 목록 조회 — 동적 JSONB 검색 + 페이지네이션
     *
     * @param slug      페이지 식별자
     * @param allParams 요청 Query Params 전체 (page/size 포함)
     * @param page      페이지 번호 (0-based)
     * @param size      페이지 크기
     */
  @Transactional(readOnly = true)
    public PageDataListResponse search(String slug, Map<String, String> allParams, int page, int size, Long siteId) {
        // 검색 조건 파라미터 추출 — rel_ 접두사(FILTER slug_relation)와 일반 파라미터 분리
    Map<String, String> relFilterParams = new LinkedHashMap<>();
    Map<String, String> searchParams = new LinkedHashMap<>();
    allParams.forEach((key, value) -> {
      if (RESERVED_PARAMS.contains(key) || value == null || value.isBlank()) return;
      if (key.startsWith("rel_")) relFilterParams.put(key, value);
      else searchParams.put(key, value);
    });

        // WHERE 절 동적 생성
    StringBuilder whereClause = new StringBuilder("WHERE data_slug = :slug");
    if (siteId != null) {
            // 해당 사이트 데이터 + 공통(NULL) 데이터 함께 조회
      whereClause.append(" AND (site_id = :siteId OR site_id IS NULL)");
    }
    appendWhereConditions(whereClause, searchParams);

        // FILTER slug_relation 처리 — rel_{id}=카테고리ID → master id IN (...) 조건 추가
    if (!relFilterParams.isEmpty()) {
      Set<Long> filterIds = resolveFilterRelationIds(relFilterParams);
      if (filterIds != null) {
        if (filterIds.isEmpty()) return buildEmptyResponse(page, size);
        String idList = filterIds.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
        whereClause.append(" AND id IN (").append(idList).append(")");
      }
    }

        // 전체 건수 조회
    String countSql = "SELECT COUNT(*) FROM page_data " + whereClause;
    Query countQuery = entityManager.createNativeQuery(countSql);
    countQuery.setParameter("slug", slug);
    if (siteId != null) {
      countQuery.setParameter("siteId", siteId);
    }
    bindSearchParams(countQuery, searchParams);
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
            // dot notation 정렬 지원 — "tab1.form1.title,asc" 형태
      if (sortCol.contains(".")) {
        String[] segs = sortCol.split("\\.");
        if (isValidSegments(segs)) {
          orderBy = " ORDER BY " + buildJsonPath(segs) + " " + sortDir;
        }
      } else if (sortCol.matches("[a-zA-Z0-9_]+")) {
        // 감사 컬럼(createdAt 등)은 테이블 실제 컬럼으로 매핑, 나머지는 data_json 경로
        String auditCol = toAuditColumn(sortCol);
        if (auditCol != null) {
          orderBy = " ORDER BY " + auditCol + " " + sortDir;
        } else {
          orderBy = " ORDER BY data_json->>'" + sortCol + "' " + sortDir;
        }
      }
    }

        // 데이터 조회
    String dataSql = "SELECT id, template_slug, data_json::text, group_id,"
                + " created_by, created_at, updated_by, updated_at "
                + "FROM page_data " + whereClause
                + orderBy
                + " LIMIT :size OFFSET :offset";
    Query dataQuery = entityManager.createNativeQuery(dataSql);
    dataQuery.setParameter("slug", slug);
    dataQuery.setParameter("size", size);
    dataQuery.setParameter("offset", (long) page * size);
    if (siteId != null) {
      dataQuery.setParameter("siteId", siteId);
    }
    bindSearchParams(dataQuery, searchParams);

    @SuppressWarnings("unchecked")
        List<Object[]> rows = dataQuery.getResultList();

        // createdBy/updatedBy unique id 추출 → 한 번에 조회하여 id→name 맵 구성 (N+1 방지)
    Map<Long, String> userNameMap = buildUserNameMap(rows, 4, 6);

    List<PageDataResponse> content = rows.stream()
                .map(row -> mapRowToResponse(row, userNameMap))
                .toList();

    // FETCH 관계 적용 — slave 데이터를 master 응답에 병합
    content = applyFetch(slug, content);

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

    /**
     * 단건 조회
     *
     * @param slug 페이지 식별자
     * @param id   데이터 PK
     */
  @Transactional(readOnly = true)
    public PageDataResponse getById(String slug, Long id) {
    PageData pageData = pageDataRepository.findByIdAndDataSlug(id, slug)
                .orElseThrow(ErrorCode.PAGE_DATA_NOT_FOUND::toException);
    PageDataResponse response = PageDataResponse.from(pageData);
    // _fetchedRel{id} 포함 — 검색 API와 동일한 FETCH 관계 적용
    List<PageDataResponse> enriched = applyFetch(slug, List.of(response));
    PageDataResponse enrichedResponse = enriched.get(0);
    // createdBy/updatedBy id → name 변환
    return enrichedResponse.withUserNames(
        resolveUserName(pageData.getCreatedBy()),
        resolveUserName(pageData.getUpdatedBy())
    );
  }

    /**
     * 등록 — 네이티브 쿼리로 직접 INSERT하여 ::jsonb 캐스팅 적용
     * pkKeys 있으면 PK 중복 여부 선행 체크 후 INSERT
     *
     * @param slug    페이지 식별자
     * @param request 등록 요청 (dataJson Map, pkKeys 목록)
     */
  @Transactional
    public PageDataResponse create(String slug, PageDataRequest request, Long siteId) {
        // PK 중복 체크 — pkKeys가 있을 때만 수행
    if (request.getPkKeys() != null && !request.getPkKeys().isEmpty()) {
      checkPkDuplicate(slug, request.getPkKeys(), request.getDataJson(), null);
    }

    String dataJsonStr = serializeDataJson(request.getDataJson());
    String currentUser = getCurrentUserId();
        // group_id 있으면 함께 저장 (다중 slug 저장 그룹), 없으면 기존 방식
    final Query insertQuery;
    if (request.getGroupId() != null && !request.getGroupId().isBlank()) {
      insertQuery = entityManager.createNativeQuery(
          "INSERT INTO page_data"
          + " (template_slug, data_slug, data_json, site_id, group_id, created_by, created_at, updated_by, updated_at)"
          + " VALUES (:templateSlug, :slug, CAST(:dataJson AS jsonb), :siteId, :groupId, :createdBy, NOW(), :updatedBy, NOW())"
          + " RETURNING id");
      insertQuery.setParameter("groupId", request.getGroupId());
    } else {
      insertQuery = entityManager.createNativeQuery(
          "INSERT INTO page_data"
          + " (template_slug, data_slug, data_json, site_id, created_by, created_at, updated_by, updated_at)"
          + " VALUES (:templateSlug, :slug, CAST(:dataJson AS jsonb), :siteId, :createdBy, NOW(), :updatedBy, NOW())"
          + " RETURNING id");
    }
        // data_slug: path의 slug (조회 기준), template_slug: 페이지 slug (저장 전용)
    insertQuery.setParameter("templateSlug", request.getTemplateSlug() != null ? request.getTemplateSlug() : slug);
    insertQuery.setParameter("slug", slug);
    insertQuery.setParameter("dataJson", dataJsonStr);
    insertQuery.setParameter("siteId", siteId);
    insertQuery.setParameter("createdBy", currentUser);
    insertQuery.setParameter("updatedBy", currentUser);
    Long newId = ((Number) insertQuery.getSingleResult()).longValue();

        // 생성된 id를 dataJson에 자동 주입 — 카테고리 계층 등 id 참조가 필요한 모든 곳에서 활용
    Map<String, Object> dataJsonWithId = new LinkedHashMap<>(request.getDataJson());
    dataJsonWithId.put("id", newId);
    Query updateIdQuery = entityManager.createNativeQuery(
                "UPDATE page_data SET data_json = CAST(:dataJson AS jsonb) WHERE id = :id");
    updateIdQuery.setParameter("dataJson", serializeDataJson(dataJsonWithId));
    updateIdQuery.setParameter("id", newId);
    updateIdQuery.executeUpdate();

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
    pageDataRepository.findByIdAndDataSlug(id, slug)
                .orElseThrow(ErrorCode.PAGE_DATA_NOT_FOUND::toException);
        // 수정 시에도 id 보장 — dataJson에 id 항상 포함
    Map<String, Object> dataJsonWithId = new LinkedHashMap<>(request.getDataJson());
    dataJsonWithId.put("id", id);
    String dataJsonStr = serializeDataJson(dataJsonWithId);
    String currentUser = getCurrentUserId();
        // JPA save() 대신 네이티브 쿼리 사용: String → JSONB 타입 명시적 캐스팅
        // 수정 시 updated_by/updated_at만 변경, created_by/created_at은 유지
    Query updateQuery = entityManager.createNativeQuery(
        "UPDATE page_data"
        + " SET data_json = CAST(:dataJson AS jsonb), updated_by = :updatedBy, updated_at = NOW()"
        + ", template_slug = :templateSlug"
        + " WHERE id = :id AND data_slug = :slug");
    updateQuery.setParameter("dataJson", dataJsonStr);
    updateQuery.setParameter("updatedBy", currentUser);
    updateQuery.setParameter("templateSlug", request.getTemplateSlug() != null ? request.getTemplateSlug() : slug);
    updateQuery.setParameter("id", id);
    updateQuery.setParameter("slug", slug);
    updateQuery.executeUpdate();
    return getById(slug, id);
  }

    /**
     * 단일 필드 부분 수정 — inlineEdit 셀 변경 시 해당 fieldKey만 업데이트
     *
     * @param slug     페이지 식별자
     * @param id       데이터 PK
     * @param fieldKey data_json 내 필드 키 (점 표기법 미지원, 최상위 키만)
     * @param value    변경할 값
     */
  @Transactional
    public PageDataResponse patchField(String slug, Long id, String fieldKey, Object value) {
        // 기존 data_json 전체 조회
    PageData existing = pageDataRepository.findByIdAndDataSlug(id, slug)
                .orElseThrow(ErrorCode.PAGE_DATA_NOT_FOUND::toException);
        // 기존 data_json에 해당 필드만 덮어쓰기
    Map<String, Object> dataJson;
    try {
      dataJson = new LinkedHashMap<>(objectMapper.readValue(
          existing.getDataJson(), new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {}));
    } catch (Exception e) {
      dataJson = new LinkedHashMap<>();
    }
    dataJson.put(fieldKey, value);
    dataJson.put("id", id);
    String dataJsonStr = serializeDataJson(dataJson);
    String currentUser = getCurrentUserId();
    Query updateQuery = entityManager.createNativeQuery(
        "UPDATE page_data"
        + " SET data_json = CAST(:dataJson AS jsonb), updated_by = :updatedBy, updated_at = NOW()"
        + " WHERE id = :id AND data_slug = :slug");
    updateQuery.setParameter("dataJson", dataJsonStr);
    updateQuery.setParameter("updatedBy", currentUser);
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
    pageDataRepository.findByIdAndDataSlug(id, slug)
                .orElseThrow(ErrorCode.PAGE_DATA_NOT_FOUND::toException);

        // 연관 파일 일괄 삭제 (파일시스템 + DB)
    pageFileService.deleteByDataId(id);

    pageDataRepository.deleteByIdAndDataSlug(id, slug);
  }

    /**
     * PK 기반 삭제 — pkKeys + dataJson 값으로 레코드 id를 찾아 기존 delete() 재사용
     * Form 위젯에서 삭제 버튼 클릭 시 사용 (id를 모르는 경우)
     *
     * @param slug     페이지 식별자
     * @param pkKeys   PK 필드 키 목록
     * @param dataJson 폼 입력 값 맵
     */
  @Transactional
    public void deleteByPk(String slug, List<String> pkKeys, Map<String, Object> dataJson) {
        // pkKeys 필수 검증
    if (pkKeys == null || pkKeys.isEmpty()) {
      throw ErrorCode.PAGE_DATA_PK_REQUIRED.toException();
    }

        // 유효한 키만 필터링 (SQL Injection 방지)
    List<String> validKeys = pkKeys.stream()
                .filter(k -> k != null && k.matches("[a-zA-Z0-9_]+"))
                .toList();
    if (validKeys.isEmpty()) {
      throw ErrorCode.PAGE_DATA_PK_REQUIRED.toException();
    }

        // pkKeys + dataJson 값으로 id 조회
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
        // 기존 delete() 재사용 — 연관 파일 정리 포함
    delete(slug, id);
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
    public List<Map<String, Object>> exportAll(String slug, Map<String, String> allParams, Long siteId) {
        // 예약 파라미터 확장 (export 전용 파라미터 추가)
    Set<String> reservedForExport = new HashSet<>(RESERVED_PARAMS);
    reservedForExport.addAll(Set.of("format", "headers", "keys", "dateFormats"));

        // 검색 조건 파라미터 추출
    Map<String, String> searchParams = new LinkedHashMap<>();
    allParams.forEach((key, value) -> {
      if (!reservedForExport.contains(key) && value != null && !value.isBlank()) {
        searchParams.put(key, value);
      }
    });

        // WHERE 절 동적 생성 — search()와 동일하게 사이트 ID 필터 포함
    StringBuilder whereClause = new StringBuilder("WHERE data_slug = :slug");
    if (siteId != null) {
      whereClause.append(" AND (site_id = :siteId OR site_id IS NULL)");
    }
    appendWhereConditions(whereClause, searchParams);

        // LIMIT/OFFSET 없이 전체 조회
    String dataSql = "SELECT id, template_slug, data_json::text, created_by, created_at, updated_by, updated_at "
                + "FROM page_data " + whereClause
                + " ORDER BY created_at DESC";
    Query dataQuery = entityManager.createNativeQuery(dataSql);
    dataQuery.setParameter("slug", slug);
    if (siteId != null) {
      dataQuery.setParameter("siteId", siteId);
    }
    bindSearchParams(dataQuery, searchParams);

    @SuppressWarnings("unchecked")
        List<Object[]> rows = dataQuery.getResultList();

        // Map<키, 값> 형태로 변환 + FE buildTableRow와 동일한 플래트닝 적용
    return rows.stream()
                .map(row -> {
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
                  // FE buildTableRow와 동일하게 메타 필드 추가
                  Map<String, Object> result = flattenDataJson(dataMap);
                  result.put("createdBy",  row[3]);
                  result.put("createdAt",  row[4] != null ? row[4].toString() : null);
                  result.put("updatedBy",  row[5]);
                  result.put("updatedAt",  row[6] != null ? row[6].toString() : null);
                  return result;
                })
                .toList();
  }

    /**
     * FE buildTableRow 플래트닝과 동일 로직 — data_json 중첩 섹션을 루트로 병합
     *
     * 예) { "form1": { "title": "A", "content": "B" } }
     *   → { "form1": {...}, "title": "A", "content": "B" }
     *
     * 중복 키(여러 섹션에 동일 키 존재) 는 루트로 올리지 않음 — dot notation accessor 사용
     */
    @SuppressWarnings("unchecked")
  private Map<String, Object> flattenDataJson(Map<String, Object> raw) {
    if (raw == null || raw.isEmpty()) return raw;

        // 최상위 object 값(contentKey 섹션) 목록
    List<Map.Entry<String, Object>> sectionEntries = raw.entrySet().stream()
                .filter(e -> e.getValue() instanceof Map)
                .collect(java.util.stream.Collectors.toList());

    if (sectionEntries.isEmpty()) return raw;

        // 각 키가 몇 개 섹션에 존재하는지 카운트
    Map<String, Integer> keyCount = new LinkedHashMap<>();
    for (Map.Entry<String, Object> entry : sectionEntries) {
      Map<String, Object> section = (Map<String, Object>) entry.getValue();
      section.keySet().forEach(k -> keyCount.merge(k, 1, Integer::sum));
    }

        // 중복 없는 키만 루트로 flat 병합
    Map<String, Object> result = new LinkedHashMap<>(raw);
    for (Map.Entry<String, Object> entry : sectionEntries) {
      Map<String, Object> section = (Map<String, Object>) entry.getValue();
      section.forEach((k, v) -> {
        if (keyCount.getOrDefault(k, 0) == 1) result.put(k, v);
      });
    }
    return result;
  }

    /**
     * group_id + templateSlug 조합 단건 조회
     * 다중 slug 수정 모드에서 각 slug별 데이터 로드에 사용
     *
     * @param groupId      그룹 식별자 (UUID)
     * @param slug         페이지 식별자
     */
  @Transactional(readOnly = true)
    public PageDataResponse findByGroupIdAndSlug(String groupId, String slug) {
    PageData pageData = pageDataRepository.findByGroupIdAndDataSlug(groupId, slug)
                .orElseThrow(ErrorCode.PAGE_DATA_NOT_FOUND::toException);
    return PageDataResponse.from(pageData);
  }

    /**
     * group_id 기반 일괄 삭제
     * 다중 slug 저장 그룹 전체를 한 번에 삭제 (연관 파일 포함)
     *
     * @param groupId 그룹 식별자 (UUID)
     */
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

    // ── private 헬퍼 ──────────────────────────────────────────

    /**
     * PK 중복 체크 — pkKeys에 해당하는 필드 값이 동일한 레코드가 이미 존재하면 예외 발생
     *
     * @param slug      페이지 식별자
     * @param pkKeys    PK 필드 키 목록 (영문자/숫자/언더스코어만 허용)
     * @param dataJson  저장할 데이터 맵
     * @param excludeId 수정 시 자신 제외용 ID (등록 시 null)
     */
  private void checkPkDuplicate(String slug, List<String> pkKeys,
                                   Map<String, Object> dataJson, Long excludeId) {
        // 유효한 키만 필터링 (SQL Injection 방지)
    List<String> validKeys = pkKeys.stream()
                .filter(k -> k != null && k.matches("[a-zA-Z0-9_]+"))
                .toList();
    if (validKeys.isEmpty()) {
      return;
    }

        // WHERE 절 동적 생성
    StringBuilder sql = new StringBuilder(
                "SELECT COUNT(*) FROM page_data WHERE data_slug = :slug");
    for (String key : validKeys) {
      sql.append(" AND data_json->>'").append(key).append("' = :pk_").append(key);
    }
    if (excludeId != null) {
      sql.append(" AND id != :excludeId");
    }

    Query query = entityManager.createNativeQuery(sql.toString());
    query.setParameter("slug", slug);
    for (String key : validKeys) {
      Object val = dataJson.get(key);
      query.setParameter("pk_" + key, val != null ? val.toString() : "");
    }
    if (excludeId != null) {
      query.setParameter("excludeId", excludeId);
    }

    long count = ((Number) query.getSingleResult()).longValue();
    if (count > 0) {
      throw ErrorCode.PAGE_DATA_PK_DUPLICATE.toException();
    }
  }

  /** 현재 로그인 사용자 id 반환 — created_by/updated_by 저장용 (비로그인 시 null) */
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
     * 컬럼 순서: id, template_slug, data_json::text, group_id, created_by,
     * created_at, updated_by, updated_at
     * userNameMap: admin_user id→name 맵 (미리 일괄 조회하여 N+1 방지)
     */
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
                .createdAt(row[5] != null ? toOffsetDateTime(row[5]) : null)
                .updatedBy(resolveUserName((String) row[6], userNameMap))
                .updatedAt(row[7] != null ? toOffsetDateTime(row[7]) : null)
                .build();
  }

  /**
   * rows에서 지정 컬럼 인덱스의 id값을 모아 AdminUser 일괄 조회 후 id→name 맵 반환
   * 숫자가 아닌 값(기존 email 형식 등)은 무시하여 하위 호환 유지
   */
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

  /**
   * id 문자열 → 사용자명 변환 (userNameMap 없는 단건 조회 전용)
   * 숫자 파싱 실패 시 원래 값 반환 (기존 email 데이터 하위 호환)
   */
  private String resolveUserName(String idStr) {
    if (idStr == null) return null;
    try {
      Long id = Long.parseLong(idStr);
      return adminRepository.findById(id).map(AdminUser::getName).orElse(idStr);
    } catch (NumberFormatException e) {
      return idStr;
    }
  }

  /**
   * id 문자열 → 사용자명 변환 (userNameMap 활용)
   * 맵에 없거나 숫자 파싱 실패 시 원래 값 반환
   */
  private String resolveUserName(String idStr, Map<Long, String> userNameMap) {
    if (idStr == null) return null;
    try {
      Long id = Long.parseLong(idStr);
      return userNameMap.getOrDefault(id, idStr);
    } catch (NumberFormatException e) {
      return idStr;
    }
  }

    /**
     * 네이티브 쿼리 결과의 타임스탬프 변환
     * Hibernate 6 + PostgreSQL JDBC에서 TIMESTAMPTZ는 다양한 타입으로 반환될 수 있음
     */
  private java.time.OffsetDateTime toOffsetDateTime(Object obj) {
    if (obj == null) return null;
    log.debug("createdAt 실제 타입: {}, 값: {}", obj.getClass().getName(), obj);
    if (obj instanceof java.time.OffsetDateTime odt) return odt;
    if (obj instanceof java.time.Instant instant) return instant.atOffset(java.time.ZoneOffset.UTC);
    if (obj instanceof java.time.ZonedDateTime zdt) return zdt.toOffsetDateTime();
    if (obj instanceof java.sql.Timestamp ts) return ts.toInstant().atOffset(java.time.ZoneOffset.UTC);
    if (obj instanceof java.time.LocalDateTime ldt) return ldt.atOffset(java.time.ZoneOffset.UTC);
    try { return java.time.OffsetDateTime.parse(obj.toString()); } catch (Exception ignored) {}
    return null;
  }

    /**
     * WHERE 절에 검색 조건 추가
     * - eq_ 접두사: 정확 일치 (ex: eq_title=홍 / eq_tab1.form1.title=홍)
     * - 값에 '~' 포함: range 검색 (>= start, <= end)
     * - 일반 값: ILIKE 부분 일치
     * - dot notation 키: 중첩 경로 직접 지정 (ex: form1.title / tab1.form1.title)
     * SQL Injection 방지: 키 세그먼트 각각 영문자/숫자/언더스코어만 허용
     */
  private void appendWhereConditions(StringBuilder whereClause, Map<String, String> searchParams) {
    searchParams.forEach((key, value) -> {
      // drs_ 접두사 → dateRangeStatus 날짜 범위 쿼리
      // 형식: drs_{rangeKey}=before|in_range|after
      // rangeKey: dateRange 컬럼의 accessor (단순 키 또는 dot notation)
      if (key.startsWith("drs_")) {
        String rangeKey = key.substring(4);
        String fromPart, toPart;
        if (rangeKey.contains(".")) {
          // dot notation: 명시적 경로 지정 → _from/_to 분리 키로 탐색
          String[] segs = rangeKey.split("\\.");
          if (!isValidSegments(segs)) return;
          // 마지막 세그먼트에 _from/_to 접미사 붙여 경로 생성
          String[] fromSegs = segs.clone(); fromSegs[fromSegs.length - 1] = fromSegs[fromSegs.length - 1] + "_from";
          String[] toSegs   = segs.clone(); toSegs[toSegs.length - 1]     = toSegs[toSegs.length - 1]     + "_to";
          fromPart = buildJsonPath(fromSegs);
          toPart   = buildJsonPath(toSegs);
          // regexp_replace로 숫자만 추출 후 8자리 비교 — YYYY-MM-DD/YYYYMMDD/YYYYMMDDHHMMSS 포맷 모두 대응
          String fromSub = "substring(regexp_replace(" + fromPart + ", '[^0-9]', '', 'g'), 1, 8)";
          String toSub   = "substring(regexp_replace(" + toPart   + ", '[^0-9]', '', 'g'), 1, 8)";
          String today   = "to_char(CURRENT_DATE, 'YYYYMMDD')";
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
          // 단순 키: _from/_to 분리 키로 최상위 + 1단계 중첩 동시 탐색
          // regexp_replace로 숫자만 추출 후 8자리 비교 — YYYY-MM-DD/YYYYMMDD/YYYYMMDDHHMMSS 포맷 모두 대응
          if (!rangeKey.matches("[a-zA-Z0-9_]+")) return;
          String fromRoot   = "substring(regexp_replace(data_json->>'" + rangeKey + "_from', '[^0-9]', '', 'g'), 1, 8)";
          String toRoot     = "substring(regexp_replace(data_json->>'" + rangeKey + "_to', '[^0-9]', '', 'g'), 1, 8)";
          String fromNested = "substring(regexp_replace(kv.value->>'" + rangeKey + "_from', '[^0-9]', '', 'g'), 1, 8)";
          String toNested   = "substring(regexp_replace(kv.value->>'" + rangeKey + "_to', '[^0-9]', '', 'g'), 1, 8)";
          String today      = "to_char(CURRENT_DATE, 'YYYYMMDD')";
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

      // eq_ 접두사 → 정확 일치
      if (key.startsWith("eq_")) {
        String fieldKey = key.substring(3);
        if (fieldKey.contains(".")) {
          // dot notation 정확 일치: eq_tab1.form1.title=홍
          String[] segments = fieldKey.split("\\.");
          if (!isValidSegments(segments)) return;
          String paramName = "p_" + key.replace(".", "_");
          whereClause.append(" AND ").append(buildJsonPath(segments)).append(" = :").append(paramName);
        } else {
          // 단순 키 정확 일치 — 최상위 + 1단계(contentKey) + 2단계(tabKey+contentKey) 동시 탐색
          // eq_ 파라미터가 있을 때만 이 블록 진입 (무조건 붙지 않음)
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

      // dot notation 파라미터 → 경로 기반 직접 검색 (ex: form1.title / tab1.form1.title)
      if (key.contains(".")) {
        String[] segments = key.split("\\.");
        if (!isValidSegments(segments)) return;
        String paramName = "p_" + key.replace(".", "_");
        String jsonPath  = buildJsonPath(segments);
        if (value.contains("~")) {
          // range 검색
          String[] parts = value.split("~", 2);
          String start = parts[0].trim();
          String end   = parts.length > 1 ? parts[1].trim() : "";
          if (!start.isEmpty()) whereClause.append(" AND ").append(jsonPath).append(" >= :").append(paramName).append("_start");
          if (!end.isEmpty())   whereClause.append(" AND ").append(jsonPath).append(" <= :").append(paramName).append("_end");
        } else {
          // ILIKE 부분 일치
          whereClause.append(" AND ").append(jsonPath).append(" ILIKE :").append(paramName);
        }
        return;
      }

      // _from 접미사 → dateRange 시작일 이상 조건 (최상위 + 1단계 중첩 동시 검색)
      // bindSearchParams에서 검색값에 000000/T00:00:00 suffix를 붙여 저장값 포맷에 맞게 정규화
      if (key.endsWith("_from")) {
        if (!key.matches("[a-zA-Z0-9_]+")) return;
        String paramName = "p_" + key;
        whereClause.append(" AND (data_json->>'").append(key).append("' >= :").append(paramName)
            .append(" OR EXISTS (SELECT 1 FROM jsonb_each(data_json) kv")
            .append(" WHERE jsonb_typeof(kv.value) = 'object'")
            .append(" AND kv.value->>'").append(key).append("' >= :").append(paramName).append("))");
        return;
      }

      // _to 접미사 → dateRange 종료일 이하 조건 (최상위 + 1단계 중첩 동시 검색)
      // bindSearchParams에서 검색값에 235959/T23:59:59 suffix를 붙여 종료일 당일 데이터 포함
      if (key.endsWith("_to")) {
        if (!key.matches("[a-zA-Z0-9_]+")) return;
        String paramName = "p_" + key;
        whereClause.append(" AND (data_json->>'").append(key).append("' <= :").append(paramName)
            .append(" OR EXISTS (SELECT 1 FROM jsonb_each(data_json) kv")
            .append(" WHERE jsonb_typeof(kv.value) = 'object'")
            .append(" AND kv.value->>'").append(key).append("' <= :").append(paramName).append("))");
        return;
      }

      // _gte 접미사 → 단일 date 컬럼 범위 검색 시작 조건 (fieldKey = key에서 _gte 제거)
      // bindSearchParams에서 검색값에 000000/T00:00:00 suffix를 붙여 저장값 포맷에 맞게 정규화
      if (key.endsWith("_gte")) {
        if (!key.matches("[a-zA-Z0-9_]+")) return;
        String fieldKey = key.substring(0, key.length() - 4);
        String paramName = "p_" + key;
        whereClause.append(" AND (data_json->>'").append(fieldKey).append("' >= :").append(paramName)
            .append(" OR EXISTS (SELECT 1 FROM jsonb_each(data_json) kv")
            .append(" WHERE jsonb_typeof(kv.value) = 'object'")
            .append(" AND kv.value->>'").append(fieldKey).append("' >= :").append(paramName).append("))");
        return;
      }

      // _lte 접미사 → 단일 date 컬럼 범위 검색 종료 조건 (fieldKey = key에서 _lte 제거)
      // bindSearchParams에서 검색값에 235959/T23:59:59 suffix를 붙여 종료일 당일 데이터 포함
      if (key.endsWith("_lte")) {
        if (!key.matches("[a-zA-Z0-9_]+")) return;
        String fieldKey = key.substring(0, key.length() - 4);
        String paramName = "p_" + key;
        whereClause.append(" AND (data_json->>'").append(fieldKey).append("' <= :").append(paramName)
            .append(" OR EXISTS (SELECT 1 FROM jsonb_each(data_json) kv")
            .append(" WHERE jsonb_typeof(kv.value) = 'object'")
            .append(" AND kv.value->>'").append(fieldKey).append("' <= :").append(paramName).append("))");
        return;
      }

      // | 구분자 → OR 다중 필드 ILIKE (최상위 + 1단계 중첩 동시 검색)
      if (key.contains("|")) {
        String[] fields = key.split("\\|");
        if (!Arrays.stream(fields).allMatch(f -> f.matches("[a-zA-Z0-9_]+"))) return;
        String paramName = "p_or_" + key.replace("|", "__");
        // 최상위 OR 조건
        String topLevel = Arrays.stream(fields)
            .map(f -> "data_json->>'" + f + "' ILIKE :" + paramName)
            .collect(java.util.stream.Collectors.joining(" OR "));
        // 1단계 중첩 OR 조건
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

      // 단순 키 (기존 방식 유지 — 최상위 + 1단계 중첩 동시 검색)
      if (!key.matches("[a-zA-Z0-9_]+")) return;
      if (value.contains("~")) {
        // range 검색
        String[] parts = value.split("~", 2);
        String start = parts[0].trim();
        String end   = parts.length > 1 ? parts[1].trim() : "";
        if (!start.isEmpty()) whereClause.append(" AND data_json->>'").append(key).append("' >= :p_").append(key).append("_start");
        if (!end.isEmpty())   whereClause.append(" AND data_json->>'").append(key).append("' <= :p_").append(key).append("_end");
      } else {
        // ILIKE 부분 일치 (최상위 + 1단계 중첩 동시 검색)
        whereClause.append(" AND (data_json->>'").append(key).append("' ILIKE :p_").append(key)
            .append(" OR EXISTS (SELECT 1 FROM jsonb_each(data_json) kv")
            .append(" WHERE jsonb_typeof(kv.value) = 'object'")
            .append(" AND kv.value->>'").append(key).append("' ILIKE :p_").append(key).append("))");
      }
    });
  }

  /**
   * 세그먼트 배열 유효성 검증 — SQL Injection 방지
   * 각 세그먼트가 영문자/숫자/언더스코어만 포함해야 함
   */
  private boolean isValidSegments(String[] segments) {
    if (segments.length == 0) return false;
    return Arrays.stream(segments).allMatch(s -> s.matches("[a-zA-Z0-9_]+"));
  }

  /**
   * 날짜 검색값 시작일 정규화 — 저장값 포맷에 맞게 시간 suffix 추가
   * YYYYMMDD(8자리 숫자)       → YYYYMMDD000000
   * YYYY-MM-DD(10자리 하이픈)  → YYYY-MM-DDT00:00:00
   * 이미 시간 포함(길이 > 10)  → 그대로
   */
  private String normalizeDateFrom(String value) {
    if (value == null) return value;
    if (value.length() == 8 && value.matches("[0-9]+")) return value + "000000";
    if (value.length() == 10 && value.contains("-"))    return value + "T00:00:00";
    return value;
  }

  /**
   * 날짜 검색값 종료일 정규화 — 저장값 포맷에 맞게 시간 suffix 추가
   * YYYYMMDD(8자리 숫자)       → YYYYMMDD235959
   * YYYY-MM-DD(10자리 하이픈)  → YYYY-MM-DDT23:59:59
   * 이미 시간 포함(길이 > 10)  → 그대로
   */
  private String normalizeDateTo(String value) {
    if (value == null) return value;
    if (value.length() == 8 && value.matches("[0-9]+")) return value + "235959";
    if (value.length() == 10 && value.contains("-"))    return value + "T23:59:59";
    return value;
  }

  /**
   * 세그먼트 배열 → JSONB 경로 표현식 생성
   * ["tab1","form1","title"] → data_json->'tab1'->'form1'->>'title'
   * ["form1","title"]        → data_json->'form1'->>'title'
   * ["title"]                → data_json->>'title'
   */
  private String buildJsonPath(String[] segments) {
    StringBuilder path = new StringBuilder("data_json");
    for (int i = 0; i < segments.length - 1; i++) {
      path.append("->'").append(segments[i]).append("'");
    }
    path.append("->>'").append(segments[segments.length - 1]).append("'");
    return path.toString();
  }

    /**
     * 쿼리에 검색 파라미터 바인딩
     * - eq_ 접두사: 값 그대로 바인딩 (정확 일치)
     * - dot notation 키: . → _ 치환한 파라미터명으로 바인딩
     * - '~' range 값: p_{key}_start / p_{key}_end 바인딩
     * - 일반 값: p_{key} ILIKE 패턴 바인딩
     */
  private void bindSearchParams(Query query, Map<String, String> searchParams) {
    searchParams.forEach((key, value) -> {
      // drs_ 접두사 → CURRENT_DATE 직접 사용, 파라미터 바인딩 불필요
      if (key.startsWith("drs_")) return;

      // eq_ 접두사 → 정확 일치 바인딩
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

      // dot notation → . 을 _ 로 치환한 파라미터명 사용
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

      // _from/_gte 접미사 → 시작일 suffix 추가 후 바인딩
      // YYYYMMDD → YYYYMMDD000000 / YYYY-MM-DD → YYYY-MM-DDT00:00:00 / 이미 시간 포함 → 그대로
      if (key.endsWith("_from") || key.endsWith("_gte")) {
        if (!key.matches("[a-zA-Z0-9_]+")) return;
        query.setParameter("p_" + key, normalizeDateFrom(value));
        return;
      }

      // _to/_lte 접미사 → 종료일 suffix 추가 후 바인딩
      // YYYYMMDD → YYYYMMDD235959 / YYYY-MM-DD → YYYY-MM-DDT23:59:59 / 이미 시간 포함 → 그대로
      if (key.endsWith("_to") || key.endsWith("_lte")) {
        if (!key.matches("[a-zA-Z0-9_]+")) return;
        query.setParameter("p_" + key, normalizeDateTo(value));
        return;
      }

      // | 구분자 → OR 다중 필드 ILIKE 파라미터 바인딩
      if (key.contains("|")) {
        String[] fields = key.split("\\|");
        if (!Arrays.stream(fields).allMatch(f -> f.matches("[a-zA-Z0-9_]+"))) return;
        query.setParameter("p_or_" + key.replace("|", "__"), "%" + value + "%");
        return;
      }

      // 단순 키 (기존 방식)
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

  /** 감사 컬럼 camelCase 키 → 실제 테이블 컬럼명 매핑 (해당 없으면 null) */
  private String toAuditColumn(String key) {
    return switch (key) {
      case "createdAt" -> "created_at";
      case "updatedAt" -> "updated_at";
      case "createdBy" -> "created_by";
      case "updatedBy" -> "updated_by";
      default -> null;
    };
  }

    /**
     * FILTER slug_relation 처리 — rel_{id}=카테고리ID 파라미터로 master id 목록 반환
     * catValue + 모든 하위 카테고리 ID를 IN 조건으로 사용 (대분류 선택 시에도 하위 포함)
     * 복수 rel_ 파라미터: AND 교집합 처리
     * null 반환: 적용할 FILTER 없음, 빈 Set 반환: 결과 없음
     */
    @SuppressWarnings("unchecked")
    private Set<Long> resolveFilterRelationIds(Map<String, String> relFilterParams) {
        Set<Long> resultIds = null;

        for (Map.Entry<String, String> entry : relFilterParams.entrySet()) {
            String key = entry.getKey();             // "rel_1"
            String categoryValue = entry.getValue(); // 선택한 카테고리 ID
            if (categoryValue == null || categoryValue.isBlank()) continue;

            Long relId;
            try { relId = Long.parseLong(key.substring(4)); }
            catch (NumberFormatException e) { continue; }

            // catValue 숫자 검증 (SQL injection 방지)
            long catId;
            try { catId = Long.parseLong(categoryValue.trim()); }
            catch (NumberFormatException e) { continue; }

            SlugRelation rel = slugRelationRepository.findById(relId).orElse(null);
            if (rel == null || !"FILTER".equals(rel.getRelationDir())) continue;

            // slaveKey에서 parentId 경로 유도: "product.id" → "product.parentId"
            int lastDot = rel.getSlaveKey().lastIndexOf('.');
            String parentKeyPath = lastDot >= 0
                ? rel.getSlaveKey().substring(0, lastDot + 1) + "parentId"
                : "parentId";

            // catId + 모든 하위 카테고리 ID 수집 (대분류 선택 시 중분류까지 포함)
            Set<Long> catIds = collectCategoryAndDescendants(rel.getSlaveSlug(), catId, rel.getSlaveKey());
            String catIdList = catIds.stream()
                .map(id -> "'" + id + "'")
                .collect(java.util.stream.Collectors.joining(","));

            // slave에서 parentKeyPath IN (catIds) + slaveFilter 조건인 연결 레코드 조회
            StringBuilder sql = new StringBuilder(
                "SELECT data_json::text FROM page_data WHERE data_slug = :slaveSlug");
            appendSlaveKeyInCondition(sql, parentKeyPath, catIdList);
            Map<String, String> filterParams = new LinkedHashMap<>();
            if (StringUtils.hasText(rel.getSlaveFilter())) {
                appendSlaveFilter(sql, rel.getSlaveFilter(), filterParams);
            }

            Query q = entityManager.createNativeQuery(sql.toString());
            q.setParameter("slaveSlug", rel.getSlaveSlug());
            filterParams.forEach(q::setParameter);

            List<Object> rows = q.getResultList();
            Set<Long> ids = new HashSet<>();
            for (Object row : rows) {
                try {
                    Map<String, Object> dataJson = objectMapper.readValue(
                        row.toString(), new com.fasterxml.jackson.core.type.TypeReference<>() {});
                    // slaveKey(product.id)에서 master id 추출
                    String masterVal = extractField(dataJson, rel.getSlaveKey());
                    if (masterVal != null && !masterVal.isBlank()) {
                        ids.add(Long.parseLong(masterVal.trim()));
                    }
                } catch (Exception e) {
                    log.warn("FILTER RELATION master id 추출 실패: {}", e.getMessage());
                }
            }

            // 복수 rel_ 파라미터: AND 교집합
            if (resultIds == null) resultIds = new HashSet<>(ids);
            else resultIds.retainAll(ids);
        }

        return resultIds;
    }

    /**
     * catId와 모든 하위 카테고리 ID 재귀 수집
     * slaveKey(product.id)가 있는 연결 레코드는 카테고리로 간주하지 않음
     */
    private Set<Long> collectCategoryAndDescendants(String slaveSlug, long catId, String slaveKey) {
        String[] keySegs = slaveKey.split("\\.");
        // 연결 레코드 판별 조건: slaveKey 경로 값이 NULL인 것만 카테고리로 탐색
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
                } catch (NumberFormatException e) { /* skip */ }
            }
            if (newIds.isEmpty()) break;
            allIds.addAll(newIds);
            frontier = newIds;
        }

        return allIds;
    }

    /** dot notation 키 → JSON 경로 IN 조건 (idList는 숫자 검증된 값만 사용) */
    private void appendSlaveKeyInCondition(StringBuilder sql, String keyPath, String idList) {
        if (keyPath.contains(".")) {
            String[] segs = keyPath.split("\\.");
            sql.append(" AND ").append(buildJsonPath(segs)).append(" IN (").append(idList).append(")");
        } else {
            sql.append(" AND data_json->>'").append(keyPath).append("' IN (").append(idList).append(")");
        }
    }

    /** FETCH 관계 적용 — FETCH 방향 slug_relation으로 slave 데이터를 master content에 병합 */
    @SuppressWarnings("unchecked")
    private List<PageDataResponse> applyFetch(String slug, List<PageDataResponse> content) {
        List<SlugRelation> fetchRelations = slugRelationRepository.findByMasterSlugAndRelationDir(slug, "FETCH");
        if (fetchRelations.isEmpty()) return content;

        return content.stream().map(item -> {
            Map<String, Object> enriched = new LinkedHashMap<>(item.getDataJson());
            for (SlugRelation rel : fetchRelations) {
                boolean isArrayContains = "ARRAY_CONTAINS".equals(rel.getJoinType());

                Object fetchedValue;
                if (isArrayContains) {
                    // ARRAY_CONTAINS: masterKey가 id 배열(multiSelect 등)인 경우 — CATEGORY는 미지원
                    if ("CATEGORY".equals(rel.getSlaveType())) {
                        log.warn("ARRAY_CONTAINS는 CATEGORY 타입을 지원하지 않습니다. relationId={}", rel.getId());
                        continue;
                    }
                    List<String> masterValues = extractFieldAsList(item.getDataJson(), rel.getMasterKey());
                    if (masterValues.isEmpty()) continue;
                    // 반환값: 매칭된 slave 레코드 전체를 배열로 반환 — 표시 형식은 FE(빌더 Data 표현식)에서 결정
                    fetchedValue = resolveArrayContainsFetch(rel, masterValues);
                } else {
                    boolean isCategory = "CATEGORY".equals(rel.getSlaveType());
                    String masterValue = extractField(item.getDataJson(), rel.getMasterKey());
                    if (masterValue == null || masterValue.isBlank()) continue;

                    // 반환값: fetch_fields 있으면 매칭 1건=String/2건 이상=List<String>, 없으면 Map<String,Object> 전체 객체
                    fetchedValue = isCategory
                        ? resolveCategoryFetch(rel, masterValue)
                        : resolveTableFetch(rel, masterValue);
                }

                if (fetchedValue != null) {
                    enriched.put(buildFetchKey(rel.getId()), fetchedValue);
                    // 다건 매칭(List<String>)일 때만 — FE가 합칠 구분자를 형제 키로 함께 전달 (ARRAY_CONTAINS는 fetchSeparator 미사용이라 제외)
                    if (!isArrayContains && fetchedValue instanceof List) {
                        String sep = StringUtils.hasText(rel.getFetchSeparator()) ? rel.getFetchSeparator() : ",";
                        enriched.put(buildFetchKey(rel.getId()) + "_sep", sep);
                    }
                }
            }
            return enriched.size() > item.getDataJson().size() ? item.withDataJson(enriched) : item;
        }).toList();
    }

    /**
     * ARRAY_CONTAINS 조인 전용 — masterKey 배열의 각 id와 slaveKey가 일치하는
     * slave 레코드 전체를 조회해 배열로 반환한다.
     * fetch_fields/fetch_separator는 사용하지 않음 — 표시 형식(1줄 합치기/행별 나열)은 FE 빌더에서 결정.
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> resolveArrayContainsFetch(SlugRelation rel, List<String> masterValues) {
        // 숫자 검증된 값만 IN 조건에 사용 (SQL Injection 방지)
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

    /**
     * TABLE 유형 FETCH
     * - fetch_fields 있음: 해당 경로 값을 문자열로 추출 — 매칭 1건이면 String, 2건 이상이면 List<String> 반환(합치지 않음, FE에서 fetchSeparator로 합침)
     * - fetch_fields 없음: 첫 번째 slave 레코드 dataJson 전체를 Map으로 반환
     *   → FE에서 accessor "_fetchedRel{id}.form1.title" 등 dot notation으로 자유롭게 접근 가능
     */
    @SuppressWarnings("unchecked")
    private Object resolveTableFetch(SlugRelation rel, String masterValue) {
        boolean hasFetchFields = StringUtils.hasText(rel.getFetchFields());

        StringBuilder sql = new StringBuilder("SELECT data_json::text FROM page_data WHERE data_slug = :slaveSlug");
        appendSlaveKeyCondition(sql, rel.getSlaveKey(), "masterValue");
        Map<String, String> filterParams = new LinkedHashMap<>();
        if (rel.getSlaveFilter() != null && !rel.getSlaveFilter().isBlank()) {
            appendSlaveFilter(sql, rel.getSlaveFilter(), filterParams);
        }
        // fetch_fields 없으면 첫 번째 레코드만 조회, 있으면 다중 지원
        sql.append(hasFetchFields ? " LIMIT 100" : " LIMIT 1");

        Query q = entityManager.createNativeQuery(sql.toString());
        q.setParameter("slaveSlug", rel.getSlaveSlug());
        q.setParameter("masterValue", masterValue);
        filterParams.forEach(q::setParameter);

        List<Object> results = q.getResultList();
        if (results.isEmpty()) return null;

        if (!hasFetchFields) {
            // fetch_fields 없음 → 첫 번째 slave dataJson 전체 Map 반환
            try {
                return objectMapper.readValue(results.get(0).toString(),
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
            } catch (Exception e) {
                log.warn("TABLE FETCH 전체 JSON 파싱 실패: {}", e.getMessage());
                return null;
            }
        }

        // fetch_fields 있음 → 해당 경로 값 문자열 추출 (합치지 않음 — 매칭 건수에 따라 String 또는 List<String> 반환)
        List<String> values = new ArrayList<>();
        for (Object row : results) {
            try {
                Map<String, Object> dataJson = objectMapper.readValue(row.toString(), new com.fasterxml.jackson.core.type.TypeReference<>() {});
                String val = extractField(dataJson, rel.getFetchFields());
                if (val != null) values.add(val);
            } catch (Exception e) {
                log.warn("TABLE FETCH dataJson 파싱 실패: {}", e.getMessage());
            }
        }
        if (values.isEmpty()) return null;
        return values.size() == 1 ? values.get(0) : values;
    }

    /**
     * CATEGORY 유형 FETCH: 연결 레코드 전체 조회 → 각각 최상위까지 경로 수집 → targetDepth개 잘라 반환
     *
     * 데이터 구조:
     * - 연결 레코드: product.id(매칭키), product.parentId(카테고리 id) — form1.title 없음
     * - 카테고리 레코드: form1.title(이름), form1.parentId(상위 id)
     *
     * depth=1 → 최상위(대분류) 이름만
     * depth=2 → "대분류 > 중분류"
     * 다중 연결 레코드 → 매칭 1건이면 String, 2건 이상이면 List<String> 반환 (합치지 않음, FE에서 fetchSeparator로 합침)
     */
    /**
     * CATEGORY 유형 FETCH
     * - fetch_fields 있음: depth에 해당하는 이름(문자열)을 추출 — 매칭 1건이면 String, 2건 이상이면 List<String> 반환
     * - fetch_fields 없음: depth에 해당하는 카테고리 레코드 전체 Map 반환
     *   → FE에서 "_fetchedRel{id}.form1.title" 등 dot notation으로 원하는 필드 직접 접근
     */
    @SuppressWarnings("unchecked")
    private Object resolveCategoryFetch(SlugRelation rel, String masterValue) {
        int targetDepth = rel.getCategoryDepth() != null ? rel.getCategoryDepth() : 1;
        // categoryDepthFrom 미설정 시 targetDepth와 동일 → 기존처럼 단일 depth만 표시 (하위 호환)
        int fromDepth = rel.getCategoryDepthFrom() != null ? rel.getCategoryDepthFrom() : targetDepth;
        boolean hasFetchFields = StringUtils.hasText(rel.getFetchFields());

        // slaveKey 기반 linkParentKeyPath: "product.id" → "product.parentId"
        int lastDot = rel.getSlaveKey().lastIndexOf('.');
        String linkParentKeyPath = lastDot >= 0
            ? rel.getSlaveKey().substring(0, lastDot + 1) + "parentId"
            : "parentId";

        // 연결 레코드 조회
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
            // fetch_fields 없음 → 첫 번째 연결 레코드 기준으로 depth에 해당하는 카테고리 레코드 전체 Map 반환
            try {
                Map<String, Object> linkDataJson = objectMapper.readValue(r1.get(0).toString(),
                    new com.fasterxml.jackson.core.type.TypeReference<>() {});
                return collectCategoryRecordAtDepth(linkDataJson, rel.getSlaveSlug(), targetDepth, linkParentKeyPath);
            } catch (Exception e) {
                log.warn("CATEGORY FETCH 연결 레코드 파싱 실패: {}", e.getMessage());
                return null;
            }
        }

        // fetch_fields 있음 → depth에 해당하는 이름 문자열 추출 (합치지 않음)
        String categoryParentKeyPath = deriveCategoryParentKeyPath(rel.getFetchFields());
        List<String> results = new ArrayList<>();
        for (Object row : r1) {
            Map<String, Object> linkDataJson;
            try {
                linkDataJson = objectMapper.readValue(row.toString(), new com.fasterxml.jackson.core.type.TypeReference<>() {});
            } catch (Exception e) {
                log.warn("CATEGORY FETCH 연결 레코드 파싱 실패: {}", e.getMessage());
                continue;
            }
            List<String> fullPath = collectFullCategoryPath(linkDataJson, rel, linkParentKeyPath, categoryParentKeyPath);
            if (fullPath.isEmpty()) continue;
            if (fullPath.size() >= targetDepth) {
                // fromDepth~targetDepth 범위의 이름을 계층 구분자(" > ", 고정)로 합침 — 레코드 간 구분자(sep)와 달라야 경계가 섞이지 않음
                List<String> rangeNames = new ArrayList<>();
                for (int d = Math.max(1, fromDepth); d <= targetDepth; d++) {
                    if (fullPath.size() >= d) rangeNames.add(fullPath.get(d - 1));
                }
                if (!rangeNames.isEmpty()) results.add(String.join(" > ", rangeNames));
            }
        }
        if (results.isEmpty()) return null;
        // 매칭된 레코드(row)가 여러 건이면 List<String> 그대로 반환 — FE에서 fetchSeparator로 합침
        return results.size() == 1 ? results.get(0) : results;
    }

    /**
     * fetch_fields 없는 CATEGORY 타입 전용 — depth에 해당하는 카테고리 레코드 전체 Map 반환
     * 연결 레코드에서 최상위까지 순회 후 역순 정렬, targetDepth번째 레코드 반환
     * 카테고리 레코드 내 parentId 경로는 autoDetectParentKeyPath()로 자동 탐지
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> collectCategoryRecordAtDepth(
            Map<String, Object> linkDataJson, String slaveSlug,
            int targetDepth, String linkParentKeyPath) {

        // 연결 레코드 → 최상위까지 순서대로 수집 (소분류→중분류→대분류 순)
        List<Map<String, Object>> records = new ArrayList<>();
        Map<String, Object> cursor = linkDataJson;
        String currentParentKeyPath = linkParentKeyPath;

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
        // 역순: 대분류(index=0), 중분류(index=1), ...
        Collections.reverse(records);
        return records.size() >= targetDepth ? records.get(targetDepth - 1) : null;
    }

    /**
     * 카테고리 레코드에서 parentId 경로 자동 탐지
     * 1. 최상위에 "parentId" 있으면 → "parentId"
     * 2. 섹션(Map 값) 내에 "parentId" 있으면 → "{섹션키}.parentId"
     */
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

    /**
     * 연결 레코드 → 최상위 카테고리까지 전체 경로 수집 (대분류가 앞, 중분류가 뒤)
     * parentId가 비어있을 때까지 거슬러 올라감 (무한루프 방지: 최대 10단계)
     */
    @SuppressWarnings("unchecked")
    private List<String> collectFullCategoryPath(
            Map<String, Object> linkDataJson, SlugRelation rel,
            String linkParentKeyPath, String categoryParentKeyPath) {

        List<String> path = new ArrayList<>();
        Map<String, Object> cursor = linkDataJson;
        String currentParentKeyPath = linkParentKeyPath; // 첫 번째: 연결 레코드 → 카테고리

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
                String name = extractField(parentDataJson, rel.getFetchFields());
                if (name != null) path.add(0, name); // 상위일수록 앞에 추가
                cursor = parentDataJson;
                currentParentKeyPath = categoryParentKeyPath; // 두 번째부터: 카테고리 간 이동
            } catch (Exception e) {
                log.warn("CATEGORY FETCH 카테고리 경로 수집 실패: {}", e.getMessage());
                break;
            }
        }

        return path;
    }

    /** fetchFields에서 카테고리 레코드 간 parentId 경로 추출: "form1.title" → "form1.parentId" */
    private String deriveCategoryParentKeyPath(String fetchFields) {
        if (fetchFields == null) return "parentId";
        int dot = fetchFields.lastIndexOf('.');
        return dot >= 0 ? fetchFields.substring(0, dot + 1) + "parentId" : "parentId";
    }

    /** slave_key 경로 조건 SQL 추가 (dot notation → data_json->'x'->>'y') */
    private void appendSlaveKeyCondition(StringBuilder sql, String slaveKey, String paramName) {
        if (slaveKey.contains(".")) {
            String[] segs = slaveKey.split("\\.");
            sql.append(" AND ").append(buildJsonPath(segs)).append(" = :").append(paramName);
        } else {
            sql.append(" AND data_json->>'").append(slaveKey).append("' = :").append(paramName);
        }
    }

    /** slave_filter 파싱: "depth=3&active=Y" → WHERE 조건 + 파라미터 맵 추가 */
    private void appendSlaveFilter(StringBuilder sql, String slaveFilter, Map<String, String> params) {
        for (String cond : slaveFilter.split("&")) {
            String[] kv = cond.split("=", 2);
            if (kv.length != 2) continue;
            String k = kv[0].trim();
            String v = kv[1].trim();
            if (!k.matches("[a-zA-Z0-9_.]+")) continue;
            String paramName = "sf_" + k.replace(".", "_");
            if (k.contains(".")) {
                String[] segs = k.split("\\.");
                if (!isValidSegments(segs)) continue;
                sql.append(" AND ").append(buildJsonPath(segs)).append(" = :").append(paramName);
            } else {
                // 최상위 + 1단계 중첩 모두 탐색
                sql.append(" AND (data_json->>'").append(k).append("' = :").append(paramName)
                   .append(" OR EXISTS (SELECT 1 FROM jsonb_each(data_json) kv")
                   .append(" WHERE jsonb_typeof(kv.value) = 'object' AND kv.value->>'").append(k).append("' = :").append(paramName).append("))");
            }
            params.put(paramName, v);
        }
    }

    /** dataJson에서 dot notation 경로로 값 추출 ("form1.title" → dataJson.form1.title) */
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

        // 정확한 경로로 못 찾았고 세그먼트가 1개(contentKey 없는 bare fieldKey)면
        // dataJson 전체를 재귀 탐색해 해당 키가 유일하게 존재할 때만 그 값을 사용한다.
        // 2곳 이상에서 발견되면 어느 값인지 모호하므로 null 반환(안전한 폴백).
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

    /**
     * dataJson에서 dot notation 경로로 배열 값 추출 (ARRAY_CONTAINS 전용)
     * 경로 값이 배열이면 각 요소를 문자열로 변환한 리스트 반환, 배열이 아니면 단일 요소 리스트로 감싸 반환
     */
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

    /** dataJson을 재귀 탐색해 fieldKey와 일치하는 모든 값을 matches에 수집 (extractField의 bare fieldKey 폴백용) */
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

    /** relationId → FETCH 결과 키 변환: 2 → "_fetchedRel2" */
    private String buildFetchKey(Long relationId) {
        return "_fetchedRel" + relationId;
    }

    /** 검색 결과 없을 때 빈 응답 생성 */
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
