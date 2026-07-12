package com.ge.bo.service;

import com.ge.bo.dto.ApiInfoRequest;
import com.ge.bo.dto.ApiInfoResponse;
import com.ge.bo.dto.ApiInfoSyncResponse;
import com.ge.bo.entity.ApiInfo;
import com.ge.bo.exception.ErrorCode;
import com.ge.bo.repository.ApiInfoRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * API 정보 서비스
 */
@Service
public class ApiInfoService {

  private final ApiInfoRepository apiInfoRepository;
    /** Spring MVC에 등록된 모든 핸들러 매핑 정보 */
  private final RequestMappingHandlerMapping handlerMapping;
  private final DatabaseService databaseService;

  public ApiInfoService(ApiInfoRepository apiInfoRepository,
            RequestMappingHandlerMapping handlerMapping,
            DatabaseService databaseService) {
    this.apiInfoRepository = apiInfoRepository;
    this.handlerMapping = handlerMapping;
    this.databaseService = databaseService;
  }

    /* ══════════ 목록 조회 ══════════ */

    /**
     * 동적 필터 + 페이징 목록 조회
     *
     * @param category 카테고리 코드 필터 (null이면 전체)
     * @param method   HTTP 메서드 필터 (null이면 전체)
     * @param active   사용여부 필터 (null이면 전체)
     * @param keyword  name/urlPattern LIKE 검색 (null이면 전체)
     * @param pageable 페이지 정보
     */
  @Transactional(readOnly = true)
    public Page<ApiInfoResponse> getList(String category, String method, Boolean active, String keyword, Pageable pageable) {
    Specification<ApiInfo> spec = buildSpec(category, method, active, keyword);
    return apiInfoRepository.findAll(spec, pageable).map(ApiInfoResponse::from);
  }

    /* ══════════ 활성 전체 목록 (빌더 드롭다운용) ══════════ */

    /** active=true만, name 오름차순 정렬, 페이징 없음 */
  @Transactional(readOnly = true)
    public List<ApiInfoResponse> getActiveList() {
    return apiInfoRepository.findAllByActiveTrueOrderByNameAsc()
                .stream().map(ApiInfoResponse::from).toList();
  }

    /* ══════════ 단건 조회 ══════════ */

  @Transactional(readOnly = true)
    public ApiInfoResponse getOne(Long id) {
    return ApiInfoResponse.from(findOrThrow(id));
  }

    /* ══════════ 등록 ══════════ */

  @Transactional
    public ApiInfoResponse create(ApiInfoRequest request) {
    ApiInfo entity = ApiInfo.builder()
                .category(trimOrNull(request.category()))
                .name(request.name().trim())
                .method(request.method().trim().toUpperCase())
                .urlPattern(request.urlPattern().trim())
                .description(trimOrNull(request.description()))
                .connectedEntity(trimOrNull(request.connectedEntity()))
                .active(request.active() != null ? request.active() : true)
                .build();
    return ApiInfoResponse.from(apiInfoRepository.save(entity));
  }

    /* ══════════ 수정 ══════════ */

  @Transactional
    public ApiInfoResponse update(Long id, ApiInfoRequest request) {
    ApiInfo entity = findOrThrow(id);
    entity.setCategory(trimOrNull(request.category()));
    entity.setName(request.name().trim());
    entity.setMethod(request.method().trim().toUpperCase());
    entity.setUrlPattern(request.urlPattern().trim());
    entity.setDescription(trimOrNull(request.description()));
    entity.setConnectedEntity(trimOrNull(request.connectedEntity()));
    if (request.active() != null) {
      entity.setActive(request.active());
    }
    return ApiInfoResponse.from(entity);
  }

    /* ══════════ 삭제 ══════════ */

  @Transactional
    public void delete(Long id) {
    apiInfoRepository.delete(findOrThrow(id));
  }

    /* ══════════ 동기화 ══════════ */

    /**
     * Spring MVC에 등록된 엔드포인트를 스캔하여 DB에 없는 것만 추가
     * - /api/v1/ 로 시작하는 경로만 대상
     * - method + urlPattern 조합이 이미 있으면 건너뜀 (전체 삭제 금지)
     * - @ApiLinkedEntity 어노테이션이 있다면 연결 Entity로 즉시 설정
     */
  @Transactional
    public ApiInfoSyncResponse sync() {
    int added = 0;
    int skipped = 0;

    for (var entry : handlerMapping.getHandlerMethods().entrySet()) {
      var info = entry.getKey();
      var hm = entry.getValue();

            /* URL 패턴 추출 — 비어있으면 스킵 */
      Set<String> patterns = info.getPatternValues();
      if (patterns.isEmpty()) {
        continue;
      }
      String urlPattern = patterns.iterator().next();

            /* /api/v1/ 로 시작하는 경로만 처리 */
      if (!urlPattern.startsWith("/api/v1/")) {
        continue;
      }

            /* HTTP 메서드 추출 — 명시된 것이 없으면 스킵 */
      Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
      if (methods.isEmpty()) {
        continue;
      }

            /* @ApiLinkedEntity 어노테이션 추출 (메서드 단 -> 클래스 단 순서) */
      com.ge.bo.annotation.ApiLinkedEntity classAnno = hm.getBeanType()
                    .getAnnotation(com.ge.bo.annotation.ApiLinkedEntity.class);
      com.ge.bo.annotation.ApiLinkedEntity methodAnno = hm
                    .getMethodAnnotation(com.ge.bo.annotation.ApiLinkedEntity.class);

      String connectedEntityStr = null;
      if (methodAnno != null) {
        connectedEntityStr = methodAnno.value();
      } else if (classAnno != null) {
        connectedEntityStr = classAnno.value();
      }

      for (RequestMethod rm : methods) {
        String method = rm.name();

                /* 이미 DB에 같은 method + urlPattern 이 있으면 건너뜀 */
        if (apiInfoRepository.existsByMethodAndUrlPattern(method, urlPattern)) {
          skipped++;
          continue;
        }

                /* 없는 것만 저장 (카테고리/설명은 나중에 수동 입력) */
        apiInfoRepository.save(ApiInfo.builder()
                        .method(method)
                        .urlPattern(urlPattern)
                        .name(urlPattern) // 임시 명칭: URL 패턴 그대로
                        .active(true)
                        .connectedEntity(connectedEntityStr)
                        .build());
        added++;
      }
    }

    return new ApiInfoSyncResponse(added, skipped);
  }

    /* ══════════ Entity 동기화 ══════════ */

    /**
     * DB에 저장된 API 정보 전체를 검사하여, Controller에 정의된 @ApiLinkedEntity
     * 어노테이션 기반으로 정확한 Entity를 연결(매핑)합니다.
     */
  @Transactional
    public int syncEntities() {
    int updatedCount = 0;

    List<ApiInfo> allApis = apiInfoRepository.findAll();
    java.util.Map<String, ApiInfo> dbApiMap = allApis.stream()
                .collect(java.util.stream.Collectors.toMap(
                        api -> api.getMethod() + ":" + api.getUrlPattern(),
                        api -> api,
                        (existing, replacement) -> existing));

    for (var entry : handlerMapping.getHandlerMethods().entrySet()) {
      var info = entry.getKey();
      var hm = entry.getValue();

      Set<String> patterns = info.getPatternValues();
      if (patterns.isEmpty()) {
        continue;
      }
      String urlPattern = patterns.iterator().next();

      Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
      if (methods.isEmpty()) {
        continue;
      }

      com.ge.bo.annotation.ApiLinkedEntity classAnno = hm.getBeanType()
                    .getAnnotation(com.ge.bo.annotation.ApiLinkedEntity.class);
      com.ge.bo.annotation.ApiLinkedEntity methodAnno = hm
                    .getMethodAnnotation(com.ge.bo.annotation.ApiLinkedEntity.class);

      String connectedEntityStr = null;
      if (methodAnno != null) {
        connectedEntityStr = methodAnno.value();
      } else if (classAnno != null) {
        connectedEntityStr = classAnno.value();
      }

      if (connectedEntityStr == null) {
        continue;
      }

      for (RequestMethod rm : methods) {
        String method = rm.name();
        String key = method + ":" + urlPattern;

        ApiInfo dbApi = dbApiMap.get(key);
        if (dbApi != null) {
          if (!connectedEntityStr.equals(dbApi.getConnectedEntity())) {
            dbApi.setConnectedEntity(connectedEntityStr);
            apiInfoRepository.save(dbApi);
            updatedCount++;
          }
        }
      }
    }
    return updatedCount;
  }

    /* ══════════ 헬퍼 ══════════ */

  private ApiInfo findOrThrow(Long id) {
    return apiInfoRepository.findById(id).orElseThrow(ErrorCode.API_INFO_NOT_FOUND::toException);
  }

    /** null 또는 빈 문자열이면 null 반환, 아니면 trim */
  private String trimOrNull(String value) {
    return (value == null || value.trim().isEmpty()) ? null : value.trim();
  }

    /**
     * 동적 필터 Specification 구성
     * - category: 정확히 일치
     * - method: 정확히 일치
     * - active: 정확히 일치
     * - keyword: name 또는 urlPattern LIKE %keyword%
     */
  private Specification<ApiInfo> buildSpec(String category, String method, Boolean active, String keyword) {
    return (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();

      if (category != null && !category.isBlank()) {
        predicates.add(cb.equal(root.get("category"), category.trim()));
      }
      if (method != null && !method.isBlank()) {
        predicates.add(cb.equal(root.get("method"), method.trim().toUpperCase()));
      }
      if (active != null) {
        predicates.add(cb.equal(root.get("active"), active));
      }
      if (keyword != null && !keyword.isBlank()) {
        String like = "%" + keyword.trim() + "%";
        predicates.add(cb.or(
                        cb.like(root.get("name"), like),
                        cb.like(root.get("urlPattern"), like)));
      }

      return cb.and(predicates.toArray(new Predicate[0]));
    };
  }
}
