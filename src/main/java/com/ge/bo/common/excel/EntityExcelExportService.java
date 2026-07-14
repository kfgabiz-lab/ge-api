package com.ge.bo.common.excel;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ge.bo.repository.AdminRepository;
import com.ge.bo.service.DownloadLogService;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Slug Entity 코드 생성기가 만든 임의의 Entity에 대한 CSV export 공통 서비스.
 *
 * <p>entity별로 코드가 중복되지 않도록, 생성되는 per-entity Service/Controller에는 얇은 위임 코드만 두고
 * 실제 동적 필터링 · 엔티티→Map 변환 · CSV 생성 · 다운로드 이력 로깅 로직은 이 한 곳에 집약한다.
 * (page-data export = {@code PageDataController.export}와 계약(파라미터/파일명/응답헤더/로깅)을 최대한 동일하게 맞춘다)</p>
 *
 * <p>entity export는 site_id 개념이 없으므로(entity 테이블에 site_id 컬럼 없음) X-Site-Id 처리는 하지 않는다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EntityExcelExportService {

  private final ExcelService excelService;
  private final DownloadLogService downloadLogService;
  private final AdminRepository adminRepository;
  private final ObjectMapper objectMapper;
  /** FILE 제외용 메타 해석 헬퍼 (단일책임 분리) */
  private final EntityExportFieldResolver fieldResolver;

  /** export 예약 파라미터 — 동적 필터 후보에서 제외 (PageDataController.export 계약과 동일) */
  private static final Set<String> RESERVED_PARAMS = Set.of(
      "headers", "keys", "dateFormats", "codeMaps", "reason", "format", "page", "size", "sort");

  /**
   * 임의의 생성 Entity 클래스에 대해 동적 필터 조건으로 전체 데이터를 조회한 뒤 CSV로 내려준다.
   *
   * @param repository   해당 Entity의 Repository (JpaSpecificationExecutor)
   * @param entityClass  Entity 클래스 (리플렉션으로 필드/타입 해석)
   * @param slug         Entity slug (파일명 · 다운로드 이력 키로 사용)
   * @param allParams    요청 Query Params 전체 (예약어 제외 나머지를 필터 후보로 취급)
   * @param headers      컬럼 헤더 목록 (쉼표 구분)
   * @param keys         컬럼 키 목록 (헤더와 순서 일치, 쉼표 구분)
   * @param dateFormats  날짜 포맷 목록 (keys와 순서 일치, 쉼표 구분)
   * @param codeMaps     공통코드 라벨 매핑표 JSON ({ key: { code: label } })
   * @param reason       개인정보 다운로드 사유 (있으면 다운로드 이력 저장)
   * @param request      IP/헤더 추출용
   */
  public <T> ResponseEntity<byte[]> export(
      JpaSpecificationExecutor<T> repository,
      Class<T> entityClass,
      String slug,
      Map<String, String> allParams,
      String headers, String keys, String dateFormats, String codeMaps,
      String reason,
      HttpServletRequest request) {

    // 1. headers/keys/dateFormats 파싱 — split(",", -1)로 trailing 빈값 보존 (PageDataController와 동일 규칙)
    List<String> headerList = splitCsv(headers);
    List<String> keyList = splitCsv(keys);
    List<String> dateFormatList = splitCsv(dateFormats);

    // 공통코드 라벨 매핑표 파싱 — 파싱 실패 시 빈 맵으로 폴백 (로그만 warn)
    Map<String, Map<String, String>> codeMapData = Collections.emptyMap();
    if (codeMaps != null && !codeMaps.isBlank()) {
      try {
        codeMapData = objectMapper.readValue(codeMaps, new TypeReference<Map<String, Map<String, String>>>() {});
      } catch (Exception e) {
        log.warn("entity export codeMaps 파싱 실패, 매핑 없이 진행: {}", e.getMessage());
      }
    }

    // 2. 동적 필터(Specification) 생성 → 3. 전체 조회
    Specification<T> spec = buildSpecification(entityClass, allParams);
    List<T> entities = repository.findAll(spec, Sort.by(Sort.Direction.DESC, "id"));

    // 3-1. slug_entity_field 메타 조회 — FILE 컬럼(제외) 분류
    //      (생성 Entity 클래스 리플렉션만으로는 bigint[] 타입을 구분할 수 없어 메타 조회가 필요)
    EntityExportFieldResolver.ExportFieldMeta meta = fieldResolver.resolveMeta(slug);

    // 3-2. FILE 컬럼은 헤더/키/날짜포맷 목록(FE가 넘긴 컬럼 정의)에서 통째로 제거 → CSV에서 완전 제외
    ColumnLists cols = excludeFileColumns(headerList, keyList, dateFormatList, meta.fileColumns());

    // 4. entity → List<Map<String,Object>> 변환 (camelCase/snake_case 키 이중 등록, FILE 컬럼은 제외)
    List<Map<String, Object>> rows = toRows(entities, entityClass, meta);

    // 5. CSV 생성 (UTF-8 BOM 포함) — FILE 제외된 컬럼 목록 사용
    byte[] fileBytes = excelService.buildCsv(cols.headers(), cols.keys(), cols.dateFormats(), codeMapData, rows);

    // 6. 파일명 {slug}_{yyyyMMdd}.csv + 응답 헤더 (PageDataController와 동일)
    String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
    String fileName = slug + "_" + today + ".csv";
    HttpHeaders responseHeaders = new HttpHeaders();
    responseHeaders.setContentType(MediaType.APPLICATION_OCTET_STREAM);
    responseHeaders.setContentDisposition(
        ContentDisposition.attachment().filename(fileName, StandardCharsets.UTF_8).build());

    // 7. reason이 있으면 다운로드 이력 비동기 저장 (PageDataController와 동일 방식)
    if (reason != null && !reason.isBlank()) {
      String email = SecurityContextHolder.getContext().getAuthentication().getName();
      String createdBy = adminRepository.findByEmail(email)
          .map(u -> String.valueOf(u.getId()))
          .orElse(null);
      String forwardedFor = request.getHeader("X-Forwarded-For");
      String ipAddress = (forwardedFor != null && !forwardedFor.isBlank())
          ? forwardedFor.split(",")[0].trim()
          : request.getRemoteAddr();
      downloadLogService.saveAsync(slug, reason, "csv", createdBy, ipAddress);
    }

    return ResponseEntity.ok().headers(responseHeaders).body(fileBytes);
  }

  /* ══════════ 동적 필터(Specification) ══════════ */

  /**
   * allParams(예약어 제외)로부터 Entity 필드 매칭 동적 필터를 만든다.
   *
   * <p>필터 규칙 — PageDataService.appendWhereConditions의 기존 컨벤션과 일관되게 맞춤:</p>
   * <ul>
   *   <li>String 필드: 대소문자 무시 부분일치(ILIKE) — page-data의 일반(무접두사) 검색과 동일</li>
   *   <li>Long/Integer/Boolean 필드: 파싱 후 등가(equal)</li>
   *   <li>날짜(LocalDate/OffsetDateTime) 필드: 파라미터 키가 {@code {fieldName}From}/{@code {fieldName}To}로
   *       끝나면 접미사를 제거한 필드에 대해 범위(&gt;=, &lt;=) 조건, 그 외에는 등가</li>
   * </ul>
   *
   * <p>필터 제외 대상: {@code @Type}(JSONB) / {@code @JdbcTypeCode}(FILE·ENTITY_REF 배열) 필드 —
   * 등가/범위 비교에 부적합하므로 필터 후보에서 빠진다. (id·감사컬럼 포함 나머지는 모두 후보)</p>
   */
  private <T> Specification<T> buildSpecification(Class<T> entityClass, Map<String, String> allParams) {
    // 필터 가능한 필드 목록 (camelCase 필드명 → Field) — JSONB/배열 필드는 제외
    Map<String, Field> filterable = new LinkedHashMap<>();
    for (Field f : entityClass.getDeclaredFields()) {
      if (f.isSynthetic() || Modifier.isStatic(f.getModifiers())) {
        continue;
      }
      if (f.isAnnotationPresent(org.hibernate.annotations.Type.class)
          || f.isAnnotationPresent(org.hibernate.annotations.JdbcTypeCode.class)) {
        continue;
      }
      filterable.put(f.getName(), f);
    }

    // 후보 파라미터 → (필드, 비교모드) 결정
    List<FilterSpec> filters = new ArrayList<>();
    for (Map.Entry<String, String> entry : allParams.entrySet()) {
      String rawKey = entry.getKey();
      String value = entry.getValue();
      if (rawKey == null || value == null || value.isBlank() || RESERVED_PARAMS.contains(rawKey)) {
        continue;
      }

      // 파라미터 키를 camelCase로 정규화 (SlugEntityCodeGenerator.toCamelCase와 동일 규칙)
      String norm = toCamelCase(rawKey);
      Field target = null;
      int mode = FilterSpec.EQUAL;

      // {fieldName}From / {fieldName}To 접미사 → 접미사 제거 필드가 날짜 타입이면 범위 조건
      if (norm.endsWith("From")) {
        Field base = filterable.get(norm.substring(0, norm.length() - "From".length()));
        if (base != null && isDateType(base.getType())) {
          target = base;
          mode = FilterSpec.RANGE_FROM;
        }
      }
      if (target == null && norm.endsWith("To")) {
        Field base = filterable.get(norm.substring(0, norm.length() - "To".length()));
        if (base != null && isDateType(base.getType())) {
          target = base;
          mode = FilterSpec.RANGE_TO;
        }
      }
      // 범위로 해석되지 않으면 정규화된 키 그대로 직접 필드 매칭 (등가/부분일치)
      if (target == null) {
        target = filterable.get(norm);
      }
      if (target == null) {
        continue; // 매칭되는 필드 없음 → 조용히 무시
      }
      filters.add(new FilterSpec(target, mode, value));
    }

    return (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();
      for (FilterSpec fs : filters) {
        try {
          Predicate p = toPredicate(cb, root, fs);
          if (p != null) {
            predicates.add(p);
          }
        } catch (Exception ex) {
          // 값 파싱 실패 등은 해당 조건만 건너뛴다 (export 자체는 실패시키지 않음)
          log.warn("entity export 필터 무시 (field={}, value={}): {}",
              fs.field().getName(), fs.value(), ex.getMessage());
        }
      }
      return cb.and(predicates.toArray(new Predicate[0]));
    };
  }

  /** 파라미터 1개에 대한 필터 명세 — 어떤 필드를 어떤 모드로 어떤 값과 비교할지 */
  private record FilterSpec(Field field, int mode, String value) {
    static final int EQUAL = 0;
    static final int RANGE_FROM = 1;
    static final int RANGE_TO = 2;
  }

  /** FilterSpec 1개 → JPA Predicate 변환 (필드 타입별 비교 규칙 적용) */
  @SuppressWarnings({"unchecked", "rawtypes"})
  private Predicate toPredicate(CriteriaBuilder cb, Root<?> root, FilterSpec fs) {
    Field field = fs.field();
    Class<?> type = field.getType();
    Path<?> path = root.get(field.getName());
    String value = fs.value();

    // 날짜 범위 (>= / <=)
    if (fs.mode() == FilterSpec.RANGE_FROM || fs.mode() == FilterSpec.RANGE_TO) {
      Object bound = parseDate(type, value);
      if (bound == null) {
        return null;
      }
      Path cmpPath = path;
      return fs.mode() == FilterSpec.RANGE_FROM
          ? cb.greaterThanOrEqualTo(cmpPath, (Comparable) bound)
          : cb.lessThanOrEqualTo(cmpPath, (Comparable) bound);
    }

    // 등가/부분일치
    if (type == String.class) {
      // PageDataService의 일반(무접두사) String 검색과 동일하게 대소문자 무시 부분일치(ILIKE)
      return cb.like(cb.lower(path.as(String.class)), "%" + value.toLowerCase(Locale.ROOT) + "%");
    }
    if (type == Long.class || type == long.class) {
      return cb.equal(path, Long.parseLong(value.trim()));
    }
    if (type == Integer.class || type == int.class) {
      return cb.equal(path, Integer.parseInt(value.trim()));
    }
    if (type == Boolean.class || type == boolean.class) {
      return cb.equal(path, Boolean.parseBoolean(value.trim()));
    }
    if (isDateType(type)) {
      Object parsed = parseDate(type, value);
      return parsed == null ? null : cb.equal(path, parsed);
    }
    // 그 외 타입 — 문자열 등가 폴백
    return cb.equal(path.as(String.class), value);
  }

  /** LocalDate / OffsetDateTime 여부 */
  private boolean isDateType(Class<?> type) {
    return type == LocalDate.class || type == OffsetDateTime.class;
  }

  /**
   * 날짜 문자열을 필드 타입에 맞는 값으로 파싱 (실패 시 null).
   * OffsetDateTime 필드에 날짜만(예: 2026-01-01) 들어오면 시스템 시간대 자정으로 보정한다.
   */
  private Object parseDate(Class<?> type, String value) {
    String v = value.trim();
    try {
      if (type == LocalDate.class) {
        return LocalDate.parse(v);
      }
      if (type == OffsetDateTime.class) {
        try {
          return OffsetDateTime.parse(v);
        } catch (Exception ignore) {
          return LocalDate.parse(v).atStartOfDay(ZoneId.systemDefault()).toOffsetDateTime();
        }
      }
    } catch (Exception e) {
      return null;
    }
    return null;
  }

  /* ══════════ FILE 컬럼 제외 ══════════ */

  /** FILE 제외 후의 헤더/키/날짜포맷 병렬 목록 묶음 */
  private record ColumnLists(List<String> headers, List<String> keys, List<String> dateFormats) {}

  /**
   * FE가 넘긴 헤더/키/날짜포맷 병렬 목록에서 FILE 타입 컬럼에 해당하는 인덱스를 통째로 제거한다.
   * (헤더·데이터 모두에서 FILE 컬럼이 사라지도록 — keyList가 CSV 컬럼을 결정하므로 여기서 빼는 것이 정석)
   *
   * <p>keyList 항목은 snake_case일 수도 camelCase일 수도 있어, 양쪽을 camelCase로 정규화해 비교한다.</p>
   *
   * @param fileColumns FILE 필드의 DB 컬럼명(snake_case) 집합
   */
  private ColumnLists excludeFileColumns(List<String> headers, List<String> keys, List<String> dateFormats,
      Set<String> fileColumns) {
    if (fileColumns == null || fileColumns.isEmpty()) {
      return new ColumnLists(headers, keys, dateFormats);
    }
    // FILE 컬럼(snake)을 camelCase로 정규화한 제외 대상 집합
    Set<String> excludedCamel = new LinkedHashSet<>();
    for (String col : fileColumns) {
      excludedCamel.add(toCamelCase(col));
    }

    List<String> newHeaders = new ArrayList<>();
    List<String> newKeys = new ArrayList<>();
    List<String> newDateFormats = new ArrayList<>();
    for (int i = 0; i < keys.size(); i++) {
      String key = keys.get(i);
      // key를 camelCase로 정규화해 FILE 컬럼과 비교 (일치하면 제외)
      if (key != null && excludedCamel.contains(toCamelCase(key.trim()))) {
        continue;
      }
      newKeys.add(key);
      if (i < headers.size()) {
        newHeaders.add(headers.get(i));
      }
      if (i < dateFormats.size()) {
        newDateFormats.add(dateFormats.get(i));
      }
    }
    return new ColumnLists(newHeaders, newKeys, newDateFormats);
  }

  /* ══════════ entity → Map 변환 ══════════ */

  /**
   * 조회된 Entity 목록을 CSV 생성용 {@code List<Map<String,Object>>}로 변환한다.
   *
   * <p>각 필드값을 camelCase(field.getName())와 snake_case 양쪽 키로 이중 등록한다.
   * FE TableColumnConfig.accessor가 원본 entity key(snake_case일 수도, camelCase일 수도 있음) 그대로
   * 저장되어 있어, 어느 케이싱으로 keys가 넘어와도 매칭되도록 하기 위함이다.
   * (entityApi.ts의 normalizeEntityRow가 getCasingAliases로 해결한 것과 동일한 패턴을 BE에서 재현)</p>
   *
   * <p>특수 필드 처리 (meta 기준):</p>
   * <ul>
   *   <li>FILE: 애초에 map에 넣지 않는다 (헤더/키에서도 excludeFileColumns로 제거됨 — 이중 방어)</li>
   * </ul>
   */
  private <T> List<Map<String, Object>> toRows(List<T> entities, Class<T> entityClass,
      EntityExportFieldResolver.ExportFieldMeta meta) {
    List<Field> fields = new ArrayList<>();
    for (Field f : entityClass.getDeclaredFields()) {
      if (f.isSynthetic() || Modifier.isStatic(f.getModifiers())) {
        continue;
      }
      f.setAccessible(true);
      fields.add(f);
    }

    List<Map<String, Object>> rows = new ArrayList<>(entities.size());
    for (T entity : entities) {
      Map<String, Object> row = new LinkedHashMap<>();
      for (Field f : fields) {
        String camel = f.getName();
        String snake = toSnakeCase(camel);

        // FILE 필드는 map에 아예 넣지 않는다 (헤더/키에서도 제거되므로 이중 방어)
        if (meta.fileColumns().contains(snake)) {
          continue;
        }

        Object val;
        try {
          val = f.get(entity);
        } catch (IllegalAccessException e) {
          val = null;
        }

        // camelCase/snake_case 양쪽 키로 등록 (먼저 등록된 값은 덮어쓰지 않음)
        row.putIfAbsent(camel, val);
        if (!snake.equals(camel)) {
          row.putIfAbsent(snake, val);
        }
      }
      rows.add(row);
    }
    return rows;
  }

  /* ══════════ 문자열 헬퍼 ══════════ */

  /** "a,b,c" → [a, b, c] / null·blank → 빈 목록 (split(",", -1)로 trailing 빈값 보존) */
  private List<String> splitCsv(String s) {
    return (s != null && !s.isBlank()) ? Arrays.asList(s.split(",", -1)) : Collections.emptyList();
  }

  /** 파라미터 키 정규화 — SlugEntityCodeGenerator.toCamelCase와 동일 규칙 (언더스코어 뒤 문자를 대문자로) */
  private static String toCamelCase(String raw) {
    StringBuilder sb = new StringBuilder();
    boolean upperNext = false;
    for (int i = 0; i < raw.length(); i++) {
      char c = raw.charAt(i);
      if (c == '_') {
        upperNext = true;
      } else if (upperNext) {
        sb.append(Character.toUpperCase(c));
        upperNext = false;
      } else {
        sb.append(c);
      }
    }
    return sb.toString();
  }

  /** camelCase → snake_case — SlugEntityCodeGenerator.toSnakeCase와 동일 규칙 */
  private static String toSnakeCase(String camel) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < camel.length(); i++) {
      char c = camel.charAt(i);
      if (Character.isUpperCase(c)) {
        if (i > 0) {
          sb.append('_');
        }
        sb.append(Character.toLowerCase(c));
      } else {
        sb.append(c);
      }
    }
    return sb.toString();
  }
}
