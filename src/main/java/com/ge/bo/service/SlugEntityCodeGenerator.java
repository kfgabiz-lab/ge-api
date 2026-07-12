package com.ge.bo.service;

import com.ge.bo.dto.SlugEntityCodePreviewResponse;
import com.ge.bo.entity.SlugEntity;
import com.ge.bo.entity.SlugEntityField;
import com.ge.bo.exception.BusinessException;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Slug Entity(SlugEntity + SlugEntityField 목록) 정의를 읽어
 * Entity / Request DTO / Response DTO / Repository / Service / Controller 6개 .java 파일 코드와
 * 참고용 CREATE TABLE DDL 문자열을 조립하는 순수 조립기.
 * - 이 클래스는 파일시스템에 아무것도 쓰지 않는다. (사이드이펙트 없음 — 문자열만 반환)
 * - 실제 파일 쓰기는 SlugEntityCodeWriter가 전담한다.
 */
@Service
public class SlugEntityCodeGenerator {

  /** 생성된 파일 상단에 남기는 마커 — 재생성 시 "이 기능이 만든 파일인지" 판별하는 기준으로도 사용된다. */
  static final String GENERATED_FILE_MARKER = "[SLUG-ENTITY-CODEGEN-AUTO-GENERATED]";

  /** Java 예약어 + 리터럴 + 컨텍스트 키워드 — 필드 key로 사용할 수 없는 단어 목록 */
  private static final Set<String> JAVA_RESERVED_WORDS = Set.of(
      "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const",
      "continue", "default", "do", "double", "else", "enum", "extends", "final", "finally", "float",
      "for", "goto", "if", "implements", "import", "instanceof", "int", "interface", "long", "native",
      "new", "package", "private", "protected", "public", "return", "short", "static", "strictfp",
      "super", "switch", "synchronized", "this", "throw", "throws", "transient", "try", "void",
      "volatile", "while", "true", "false", "null", "var", "record", "yield", "sealed", "permits");

  private final Environment environment;

  public SlugEntityCodeGenerator(Environment environment) {
    this.environment = environment;
  }

  /** 필드 1개의 코드 생성용 계산 결과 — 빌더 화면의 SlugEntityField를 코드 조립에 필요한 형태로 가공한 값 */
  private record FieldMeta(
      String key,
      String columnName,
      String label,
      String description,
      SlugColumnTypeMapping mapping,
      Integer columnLength,
      boolean nullable) {
  }

  /* ══════════ 미리보기 (진입점) ══════════ */

  /**
   * SlugEntity 정의를 검증한 뒤 6개 파일 코드 + DDL을 조립해서 반환한다.
   * 파일시스템에는 아무것도 쓰지 않는다.
   */
  public SlugEntityCodePreviewResponse preview(SlugEntity slugEntity) {
    guardLocalProfileOnly();

    List<String> errors = validate(slugEntity);
    if (!errors.isEmpty()) {
      throw BusinessException.badRequest(String.join(" / ", errors));
    }

    String className = toPascalCase(slugEntity.getSlug());
    List<FieldMeta> fields = toFieldMetas(slugEntity.getFields());

    String entityCode = buildEntity(slugEntity, className, fields);
    String requestCode = buildRequest(slugEntity, className, fields);
    String responseCode = buildResponse(slugEntity, className, fields);
    String repositoryCode = buildRepository(slugEntity, className);
    String serviceCode = buildService(slugEntity, className, fields);
    String controllerCode = buildController(slugEntity, className);
    String ddl = buildDdl(slugEntity, fields);

    return new SlugEntityCodePreviewResponse(
        slugEntity.getId(), slugEntity.getSlug(), slugEntity.getTableName(), className,
        className + ".java", entityCode,
        className + "Request.java", requestCode,
        className + "Response.java", responseCode,
        className + "Repository.java", repositoryCode,
        className + "Service.java", serviceCode,
        className + "Controller.java", controllerCode,
        ddl);
  }

  /* ══════════ 검증 ══════════ */

  /**
   * 코드 생성 가능 여부를 검증하고, 발견된 문제를 모두 모아서 반환한다. (문제 없으면 빈 리스트)
   * - table_name 필수
   * - 필드별 key 필수 / Java 식별자 형식 / 예약어 금지 / 중복 금지
   * - key → snake_case 변환 후 컬럼명 충돌 금지 (PK 컬럼명 id 포함)
   * - columnType 매핑표 존재 여부 (fail-fast)
   */
  private List<String> validate(SlugEntity slugEntity) {
    List<String> errors = new ArrayList<>();

    if (slugEntity.getTableName() == null || slugEntity.getTableName().isBlank()) {
      errors.add("테이블명(table_name)이 설정되지 않았습니다. Slug Entity 관리 화면에서 테이블명을 먼저 입력해주세요.");
    }

    List<SlugEntityField> fields = slugEntity.getFields();
    if (fields == null || fields.isEmpty()) {
      errors.add("필드가 하나도 정의되어 있지 않습니다.");
      return errors;
    }

    Set<String> seenKeys = new HashSet<>();
    Set<String> seenColumnNames = new HashSet<>();

    for (SlugEntityField field : fields) {
      String key = field.getKey();
      String label = (field.getLabel() != null && !field.getLabel().isBlank()) ? field.getLabel() : "(라벨없음)";

      if (key == null || key.isBlank()) {
        errors.add("필드 '" + label + "'에 key가 설정되어 있지 않습니다.");
        continue;
      }
      key = key.trim();

      if (!key.matches("^[a-zA-Z_$][a-zA-Z0-9_$]*$")) {
        errors.add("필드 key '" + key + "'는 올바른 Java 식별자 형식이 아닙니다.");
      }
      if (JAVA_RESERVED_WORDS.contains(key)) {
        errors.add("필드 key '" + key + "'는 Java 예약어이므로 사용할 수 없습니다.");
      }
      if ("id".equals(key)) {
        errors.add("필드 key 'id'는 PK로 고정 예약되어 있어 사용할 수 없습니다.");
      }

      // 중복 검사는 반드시 camelCase 정규화 이후 값(=실제 Java 필드명) 기준으로 해야 한다.
      // (예: orderNo / order_no 는 raw key가 다르지만 정규화하면 둘 다 orderNo가 되어 충돌한다.)
      String normalizedKey = toCamelCase(key);
      if (!seenKeys.add(normalizedKey)) {
        errors.add("필드 key '" + key + "'가 정규화(" + normalizedKey + ") 후 다른 필드와 중복되었습니다.");
      }

      String columnName = toSnakeCase(normalizedKey);
      if ("id".equals(columnName)) {
        errors.add("필드 key '" + key + "'의 컬럼명이 예약된 PK 컬럼명 'id'와 충돌합니다.");
      }
      if (!seenColumnNames.add(columnName)) {
        errors.add("필드 key '" + key + "'의 컬럼명 '" + columnName + "'이(가) 다른 필드와 충돌합니다.");
      }

      if (SlugColumnTypeMapping.tryParse(field.getColumnType()) == null) {
        errors.add("필드 '" + key + "'의 컬럼타입이 매핑되지 않았습니다: " + field.getColumnType());
      }
    }

    String className = toPascalCase(slugEntity.getSlug());
    if (className.isEmpty() || Character.isDigit(className.charAt(0))) {
      errors.add("slug '" + slugEntity.getSlug() + "'로부터 유효한 클래스명을 생성할 수 없습니다.");
    }

    return errors;
  }

  private List<FieldMeta> toFieldMetas(List<SlugEntityField> fields) {
    List<FieldMeta> result = new ArrayList<>();
    for (SlugEntityField field : fields) {
      // raw key(snake_case/혼합 표기 가능)를 camelCase Java 식별자로 정규화한 뒤,
      // 이 정규화된 값을 Java 필드명과 컬럼명 계산 양쪽에 동일하게 사용한다.
      String key = toCamelCase(field.getKey().trim());
      boolean nullable = field.getIsNullable() == null || field.getIsNullable();
      result.add(new FieldMeta(
          key,
          toSnakeCase(key),
          field.getLabel(),
          field.getDescription(),
          SlugColumnTypeMapping.fromColumnType(field.getColumnType()),
          field.getColumnLength(),
          nullable));
    }
    return result;
  }

  /**
   * 필드 목록에서 필요한 java.time.* import FQCN을 모아서 반환한다.
   * TreeSet을 사용해 정렬 + 중복 제거를 동시에 처리한다.
   * (DATE → java.time.LocalDate, TIMESTAMPTZ → java.time.OffsetDateTime, 그 외 타입은 import 불필요)
   */
  private Set<String> collectExtraImports(List<FieldMeta> fields) {
    return fields.stream()
        .map(f -> f.mapping().javaImportFqcn())
        .filter(Objects::nonNull)
        .collect(Collectors.toCollection(TreeSet::new));
  }

  /* ══════════ Entity ══════════ */

  private String buildEntity(SlugEntity slugEntity, String className, List<FieldMeta> fields) {
    boolean hasJsonb = fields.stream().anyMatch(f -> f.mapping().jsonb());
    // FILE 등 배열(bigint[]) 매핑 필드가 있으면 @JdbcTypeCode(SqlTypes.ARRAY) + List import를 조건부로 추가한다.
    boolean hasArray = fields.stream().anyMatch(f -> f.mapping().array());
    // audit 필드(createdAt/updatedAt)는 항상 OffsetDateTime을 사용하므로 고정으로 포함하고,
    // 필드 타입별로 필요한 java.time.* import를 같은 Set에 모아 중복 없이 한 번씩만 출력한다.
    Set<String> timeImports = collectExtraImports(fields);
    timeImports.add("java.time.OffsetDateTime");

    StringBuilder sb = new StringBuilder();
    sb.append("package com.ge.bo.entity;\n\n");
    sb.append(buildFileHeader(slugEntity, "Entity — " + slugEntity.getName()));
    sb.append("import jakarta.persistence.*;\n");
    sb.append("import lombok.*;\n");
    if (hasJsonb) {
      sb.append("import io.hypersistence.utils.hibernate.type.json.JsonStringType;\n");
      sb.append("import org.hibernate.annotations.Type;\n");
    }
    if (hasArray) {
      sb.append("import org.hibernate.annotations.JdbcTypeCode;\n");
      sb.append("import org.hibernate.type.SqlTypes;\n");
    }
    sb.append("import org.springframework.data.annotation.CreatedBy;\n");
    sb.append("import org.springframework.data.annotation.CreatedDate;\n");
    sb.append("import org.springframework.data.annotation.LastModifiedBy;\n");
    sb.append("import org.springframework.data.annotation.LastModifiedDate;\n");
    sb.append("import org.springframework.data.jpa.domain.support.AuditingEntityListener;\n\n");
    if (hasArray) {
      sb.append("import java.util.List;\n");
    }
    for (String imp : timeImports) {
      sb.append("import ").append(imp).append(";\n");
    }
    sb.append("\n");
    sb.append("/**\n");
    sb.append(" * ").append(escapeJavadoc(slugEntity.getName())).append(" 엔티티\n");
    if (slugEntity.getDescription() != null && !slugEntity.getDescription().isBlank()) {
      sb.append(" * ").append(escapeJavadoc(slugEntity.getDescription())).append("\n");
    }
    sb.append(" */\n");
    sb.append("@Entity\n");
    sb.append("@Table(name = \"").append(slugEntity.getTableName()).append("\")\n");
    sb.append("@Getter\n@Setter\n@NoArgsConstructor\n@AllArgsConstructor\n@Builder\n");
    sb.append("@EntityListeners(AuditingEntityListener.class)\n");
    sb.append("public class ").append(className).append(" {\n\n");

    sb.append("  @Id\n");
    sb.append("  @GeneratedValue(strategy = GenerationType.IDENTITY)\n");
    sb.append("  private Long id;\n\n");

    for (FieldMeta field : fields) {
      String comment = field.description() != null && !field.description().isBlank()
          ? field.description() : field.label();
      if (comment != null && !comment.isBlank()) {
        sb.append("  /** ").append(escapeJavadoc(comment)).append(" */\n");
      }
      if (field.mapping().jsonb()) {
        sb.append("  @Type(JsonStringType.class)\n");
        sb.append("  @Column(name = \"").append(field.columnName())
            .append("\", nullable = ").append(field.nullable())
            .append(", columnDefinition = \"jsonb\")\n");
      } else if (field.mapping().array()) {
        // 배열 타입(FILE=file_meta.id 목록 / ENTITY_REF=연동 Entity 레코드 id 목록): bigint[] 배열 컬럼으로 저장 (Hibernate ARRAY 매핑)
        sb.append("  @JdbcTypeCode(SqlTypes.ARRAY)\n");
        sb.append("  @Column(name = \"").append(field.columnName())
            .append("\", nullable = ").append(field.nullable())
            .append(", columnDefinition = \"bigint[]\")\n");
      } else {
        sb.append("  @Column(name = \"").append(field.columnName()).append("\"");
        if (field.mapping().lengthApplicable() && field.columnLength() != null) {
          sb.append(", length = ").append(field.columnLength());
        }
        sb.append(", nullable = ").append(field.nullable());
        sb.append(")\n");
      }
      sb.append("  private ").append(field.mapping().javaType()).append(" ").append(field.key()).append(";\n\n");
    }

    sb.append("  @CreatedBy\n");
    sb.append("  @Column(name = \"created_by\", nullable = false, updatable = false, length = 50)\n");
    sb.append("  private String createdBy;\n\n");

    sb.append("  @CreatedDate\n");
    sb.append("  @Column(name = \"created_at\", nullable = false, updatable = false)\n");
    sb.append("  private OffsetDateTime createdAt;\n\n");

    sb.append("  @LastModifiedBy\n");
    sb.append("  @Column(name = \"updated_by\", nullable = false, length = 50)\n");
    sb.append("  private String updatedBy;\n\n");

    sb.append("  @LastModifiedDate\n");
    sb.append("  @Column(name = \"updated_at\", nullable = false)\n");
    sb.append("  private OffsetDateTime updatedAt;\n");
    sb.append("}\n");

    return sb.toString();
  }

  /* ══════════ Request DTO ══════════ */

  private String buildRequest(SlugEntity slugEntity, String className, List<FieldMeta> fields) {
    // 필드 중 DATE/TIMESTAMPTZ 타입이 있으면 그에 맞는 java.time.* import가 추가로 필요하다.
    Set<String> timeImports = collectExtraImports(fields);
    // FILE 등 배열 타입(List<Long>) 필드가 있으면 java.util.List import를 추가한다.
    boolean hasArray = fields.stream().anyMatch(f -> f.mapping().array());

    StringBuilder sb = new StringBuilder();
    sb.append("package com.ge.bo.dto;\n\n");
    sb.append(buildFileHeader(slugEntity, "Request DTO — " + slugEntity.getName()));
    sb.append("import jakarta.validation.constraints.*;\n");
    if (hasArray) {
      sb.append("import java.util.List;\n");
    }
    for (String imp : timeImports) {
      sb.append("import ").append(imp).append(";\n");
    }
    sb.append("\n");
    sb.append("/**\n * ").append(escapeJavadoc(slugEntity.getName())).append(" 등록/수정 요청 DTO\n */\n");
    sb.append("public record ").append(className).append("Request(\n\n");

    for (int i = 0; i < fields.size(); i++) {
      FieldMeta field = fields.get(i);
      String label = (field.label() != null && !field.label().isBlank()) ? field.label() : field.key();

      if (!field.nullable()) {
        String annotation = "String".equals(field.mapping().javaType()) ? "@NotBlank" : "@NotNull";
        sb.append("    ").append(annotation)
            .append("(message = \"필수 입력 항목입니다: ").append(escapeJavaString(label)).append("\")\n");
      }
      if (field.mapping().lengthApplicable() && field.columnLength() != null) {
        sb.append("    @Size(max = ").append(field.columnLength())
            .append(", message = \"최대 ").append(field.columnLength())
            .append("자까지 입력 가능합니다: ").append(escapeJavaString(label)).append("\")\n");
      }
      sb.append("    ").append(field.mapping().javaType()).append(" ").append(field.key());
      sb.append(i < fields.size() - 1 ? ",\n\n" : "\n");
    }

    sb.append(") {}\n");
    return sb.toString();
  }

  /* ══════════ Response DTO ══════════ */

  private String buildResponse(SlugEntity slugEntity, String className, List<FieldMeta> fields) {
    // audit 필드(createdAt/updatedAt)는 항상 OffsetDateTime을 사용하므로 고정으로 포함하고,
    // 필드 타입별로 필요한 java.time.* import를 같은 Set에 모아 중복 없이 한 번씩만 출력한다.
    Set<String> timeImports = collectExtraImports(fields);
    timeImports.add("java.time.OffsetDateTime");
    // FILE 등 배열 타입(List<Long>) 필드가 있으면 java.util.List import를 추가한다.
    boolean hasArray = fields.stream().anyMatch(f -> f.mapping().array());

    StringBuilder sb = new StringBuilder();
    sb.append("package com.ge.bo.dto;\n\n");
    sb.append(buildFileHeader(slugEntity, "Response DTO — " + slugEntity.getName()));
    sb.append("import com.ge.bo.entity.").append(className).append(";\n");
    if (hasArray) {
      sb.append("import java.util.List;\n");
    }
    for (String imp : timeImports) {
      sb.append("import ").append(imp).append(";\n");
    }
    sb.append("\n");
    sb.append("/**\n * ").append(escapeJavadoc(slugEntity.getName())).append(" 응답 DTO\n */\n");
    sb.append("public record ").append(className).append("Response(\n");
    sb.append("    Long id,\n");
    for (FieldMeta field : fields) {
      sb.append("    ").append(field.mapping().javaType()).append(" ").append(field.key()).append(",\n");
    }
    sb.append("    String createdBy,\n");
    sb.append("    OffsetDateTime createdAt,\n");
    sb.append("    String updatedBy,\n");
    sb.append("    OffsetDateTime updatedAt) {\n\n");
    sb.append("  public static ").append(className).append("Response from(").append(className).append(" e) {\n");
    sb.append("    return new ").append(className).append("Response(\n");
    sb.append("        e.getId(),\n");
    for (FieldMeta field : fields) {
      sb.append("        e.get").append(capitalize(field.key())).append("(),\n");
    }
    sb.append("        e.getCreatedBy(), e.getCreatedAt(), e.getUpdatedBy(), e.getUpdatedAt());\n");
    sb.append("  }\n");
    sb.append("}\n");
    return sb.toString();
  }

  /* ══════════ Repository ══════════ */

  private String buildRepository(SlugEntity slugEntity, String className) {
    StringBuilder sb = new StringBuilder();
    sb.append("package com.ge.bo.repository;\n\n");
    sb.append(buildFileHeader(slugEntity, "Repository — " + slugEntity.getName()));
    sb.append("import com.ge.bo.entity.").append(className).append(";\n");
    sb.append("import org.springframework.data.jpa.repository.JpaRepository;\n");
    sb.append("import org.springframework.data.jpa.repository.JpaSpecificationExecutor;\n\n");
    sb.append("/**\n * ").append(escapeJavadoc(slugEntity.getName())).append(" Repository\n */\n");
    sb.append("public interface ").append(className).append("Repository\n");
    sb.append("    extends JpaRepository<").append(className).append(", Long>, JpaSpecificationExecutor<")
        .append(className).append("> {\n");
    sb.append("}\n");
    return sb.toString();
  }

  /* ══════════ Service ══════════ */

  private String buildService(SlugEntity slugEntity, String className, List<FieldMeta> fields) {
    String var = decapitalize(className);
    StringBuilder sb = new StringBuilder();
    sb.append("package com.ge.bo.service;\n\n");
    sb.append(buildFileHeader(slugEntity, "Service — " + slugEntity.getName()));
    sb.append("import com.ge.bo.dto.").append(className).append("Request;\n");
    sb.append("import com.ge.bo.dto.").append(className).append("Response;\n");
    sb.append("import com.ge.bo.entity.").append(className).append(";\n");
    sb.append("import com.ge.bo.exception.BusinessException;\n");
    sb.append("import com.ge.bo.repository.").append(className).append("Repository;\n");
    sb.append("import lombok.RequiredArgsConstructor;\n");
    sb.append("import org.springframework.data.domain.Page;\n");
    sb.append("import org.springframework.data.domain.Pageable;\n");
    sb.append("import org.springframework.stereotype.Service;\n");
    sb.append("import org.springframework.transaction.annotation.Transactional;\n\n");
    sb.append("/**\n * ").append(escapeJavadoc(slugEntity.getName())).append(" 서비스\n */\n");
    sb.append("@Service\n@RequiredArgsConstructor\n");
    sb.append("public class ").append(className).append("Service {\n\n");
    sb.append("  private final ").append(className).append("Repository ").append(var).append("Repository;\n\n");

    sb.append("  /** 목록 조회 (페이징) */\n");
    sb.append("  @Transactional(readOnly = true)\n");
    sb.append("  public Page<").append(className).append("Response> getList(Pageable pageable) {\n");
    sb.append("    return ").append(var).append("Repository.findAll(pageable).map(")
        .append(className).append("Response::from);\n");
    sb.append("  }\n\n");

    sb.append("  /** 단건 조회 */\n");
    sb.append("  @Transactional(readOnly = true)\n");
    sb.append("  public ").append(className).append("Response getOne(Long id) {\n");
    sb.append("    return ").append(className).append("Response.from(findOrThrow(id));\n");
    sb.append("  }\n\n");

    sb.append("  /** 등록 */\n");
    sb.append("  @Transactional\n");
    sb.append("  public ").append(className).append("Response create(")
        .append(className).append("Request request) {\n");
    sb.append("    ").append(className).append(" entity = ").append(className).append(".builder()\n");
    for (FieldMeta field : fields) {
      sb.append("        .").append(field.key()).append("(request.").append(field.key()).append("())\n");
    }
    sb.append("        .build();\n");
    sb.append("    return ").append(className).append("Response.from(")
        .append(var).append("Repository.save(entity));\n");
    sb.append("  }\n\n");

    sb.append("  /** 수정 */\n");
    sb.append("  @Transactional\n");
    sb.append("  public ").append(className).append("Response update(Long id, ")
        .append(className).append("Request request) {\n");
    sb.append("    ").append(className).append(" entity = findOrThrow(id);\n");
    for (FieldMeta field : fields) {
      sb.append("    entity.set").append(capitalize(field.key()))
          .append("(request.").append(field.key()).append("());\n");
    }
    sb.append("    return ").append(className).append("Response.from(entity);\n");
    sb.append("  }\n\n");

    sb.append("  /** 삭제 */\n");
    sb.append("  @Transactional\n");
    sb.append("  public void delete(Long id) {\n");
    sb.append("    ").append(var).append("Repository.delete(findOrThrow(id));\n");
    sb.append("  }\n\n");

    sb.append("  /** id로 조회, 없으면 예외 */\n");
    sb.append("  private ").append(className).append(" findOrThrow(Long id) {\n");
    sb.append("    return ").append(var).append("Repository.findById(id)\n");
    sb.append("        .orElseThrow(() -> BusinessException.notFound(\"해당 데이터를 찾을 수 없습니다. id=\" + id));\n");
    sb.append("  }\n");
    sb.append("}\n");
    return sb.toString();
  }

  /* ══════════ Controller ══════════ */

  private String buildController(SlugEntity slugEntity, String className) {
    String var = decapitalize(className);
    String basePath = "/api/v1/" + slugEntity.getSlug();
    StringBuilder sb = new StringBuilder();
    sb.append("package com.ge.bo.controller;\n\n");
    sb.append(buildFileHeader(slugEntity, "Controller — " + slugEntity.getName()));
    sb.append("import com.ge.bo.annotation.ApiLinkedEntity;\n");
    sb.append("import com.ge.bo.dto.").append(className).append("Request;\n");
    sb.append("import com.ge.bo.dto.").append(className).append("Response;\n");
    sb.append("import com.ge.bo.service.").append(className).append("Service;\n");
    sb.append("import jakarta.validation.Valid;\n");
    sb.append("import lombok.RequiredArgsConstructor;\n");
    sb.append("import org.springframework.data.domain.Page;\n");
    sb.append("import org.springframework.data.domain.Pageable;\n");
    sb.append("import org.springframework.data.domain.Sort;\n");
    sb.append("import org.springframework.data.web.PageableDefault;\n");
    sb.append("import org.springframework.http.HttpStatus;\n");
    sb.append("import org.springframework.http.ResponseEntity;\n");
    sb.append("import org.springframework.security.access.prepost.PreAuthorize;\n");
    sb.append("import org.springframework.web.bind.annotation.*;\n");
    sb.append("\n");
    sb.append("/**\n * ").append(escapeJavadoc(slugEntity.getName())).append(" REST API\n */\n");
    sb.append("@RestController\n");
    sb.append("@RequestMapping(\"").append(basePath).append("\")\n");
    sb.append("@RequiredArgsConstructor\n");
    sb.append("@PreAuthorize(\"@securityService.isSystemAdmin(authentication)\")\n");
    sb.append("@ApiLinkedEntity(\"").append(className).append("\")\n");
    sb.append("public class ").append(className).append("Controller {\n\n");
    sb.append("  private final ").append(className).append("Service ").append(var).append("Service;\n");
    sb.append("\n");

    sb.append("  /** 목록 조회 (페이징) */\n");
    sb.append("  @GetMapping\n");
    sb.append("  public ResponseEntity<Page<").append(className).append("Response>> getList(\n");
    sb.append("      @PageableDefault(size = 20, sort = \"id\", direction = Sort.Direction.DESC)\n");
    sb.append("      Pageable pageable) {\n");
    sb.append("    return ResponseEntity.ok(").append(var).append("Service.getList(pageable));\n");
    sb.append("  }\n\n");

    sb.append("  /** 단건 조회 */\n");
    sb.append("  @GetMapping(\"/{id}\")\n");
    sb.append("  public ResponseEntity<").append(className).append("Response> getOne(@PathVariable Long id) {\n");
    sb.append("    return ResponseEntity.ok(").append(var).append("Service.getOne(id));\n");
    sb.append("  }\n\n");

    sb.append("  /** 등록 */\n");
    sb.append("  @PostMapping\n");
    sb.append("  public ResponseEntity<").append(className).append("Response> create(\n");
    sb.append("      @Valid @RequestBody ").append(className).append("Request request) {\n");
    sb.append("    return ResponseEntity.status(HttpStatus.CREATED).body(")
        .append(var).append("Service.create(request));\n");
    sb.append("  }\n\n");

    sb.append("  /** 수정 */\n");
    sb.append("  @PutMapping(\"/{id}\")\n");
    sb.append("  public ResponseEntity<").append(className).append("Response> update(\n");
    sb.append("      @PathVariable Long id, @Valid @RequestBody ").append(className).append("Request request) {\n");
    sb.append("    return ResponseEntity.ok(").append(var).append("Service.update(id, request));\n");
    sb.append("  }\n\n");

    sb.append("  /** 삭제 */\n");
    sb.append("  @DeleteMapping(\"/{id}\")\n");
    sb.append("  public ResponseEntity<Void> delete(@PathVariable Long id) {\n");
    sb.append("    ").append(var).append("Service.delete(id);\n");
    sb.append("    return ResponseEntity.noContent().build();\n");
    sb.append("  }\n");
    sb.append("}\n");
    return sb.toString();
  }

  /* ══════════ DDL (참고용 — 실제 실행되지 않음) ══════════ */

  private String buildDdl(SlugEntity slugEntity, List<FieldMeta> fields) {
    StringBuilder sb = new StringBuilder();
    sb.append("-- ").append(GENERATED_FILE_MARKER).append("\n");
    sb.append("-- 생성일시: ").append(OffsetDateTime.now()).append("\n");
    sb.append("-- 원본 Slug Entity: id=").append(slugEntity.getId())
        .append(", slug=").append(slugEntity.getSlug()).append("\n");
    sb.append("-- 주의: 이 DDL은 참고용으로만 생성되며, 이 기능이 직접 실행하지는 않습니다. 검토 후 별도로 실행해주세요.\n");
    sb.append("CREATE TABLE ").append(slugEntity.getTableName()).append(" (\n");
    sb.append("    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,\n");
    for (FieldMeta field : fields) {
      sb.append("    ").append(field.columnName()).append(" ").append(field.mapping().ddlType());
      if (field.mapping().lengthApplicable() && field.columnLength() != null) {
        sb.append("(").append(field.columnLength()).append(")");
      }
      if (!field.nullable()) {
        sb.append(" NOT NULL");
      }
      sb.append(",\n");
    }
    sb.append("    created_by VARCHAR(50) NOT NULL,\n");
    sb.append("    created_at TIMESTAMPTZ NOT NULL,\n");
    sb.append("    updated_by VARCHAR(50) NOT NULL,\n");
    sb.append("    updated_at TIMESTAMPTZ NOT NULL\n");
    sb.append(");\n");
    return sb.toString();
  }

  /* ══════════ 파일 상단 마커 헤더 ══════════ */

  /**
   * 생성 파일 상단에 남기는 한글 javadoc 헤더.
   * "자동생성됨, 생성일시, 원본 slug_entity(id/tableName), 직접 수정 후 재생성하면 사라짐(백업 안내)"을 포함한다.
   * GENERATED_FILE_MARKER 문자열은 SlugEntityCodeWriter가 재생성 대상 판별(마커 검사)에도 그대로 사용한다.
   */
  private String buildFileHeader(SlugEntity slugEntity, String fileDescription) {
    StringBuilder sb = new StringBuilder();
    sb.append("/**\n");
    sb.append(" * ").append(GENERATED_FILE_MARKER).append("\n");
    sb.append(" * ").append(fileDescription).append("\n");
    sb.append(" * 생성일시: ").append(OffsetDateTime.now()).append("\n");
    sb.append(" * 원본 Slug Entity: id=").append(slugEntity.getId())
        .append(", tableName=").append(slugEntity.getTableName()).append("\n");
    sb.append(" * 주의: 이 파일을 직접 수정한 뒤 다시 생성하면 수정 내용이 사라집니다.\n");
    sb.append(" *       (재생성 시 기존 파일은 자동으로 *.bak.{timestamp} 로 백업됩니다.)\n");
    sb.append(" */\n");
    return sb.toString();
  }

  /* ══════════ 로컬 전용 가드 ══════════ */

  /** local 프로파일이 아니면 코드 생성 기능 자체를 차단한다. */
  private void guardLocalProfileOnly() {
    if (!environment.acceptsProfiles(Profiles.of("local"))) {
      throw BusinessException.forbidden("Slug Entity 코드 생성 기능은 local 프로파일에서만 사용할 수 있습니다.");
    }
  }

  /* ══════════ 문자열 변환 헬퍼 ══════════ */

  /** kebab-case 등 SlugEntity.slug → PascalCase 클래스명 변환 (예: member-info → MemberInfo) */
  static String toPascalCase(String source) {
    if (source == null) {
      return "";
    }
    String[] parts = source.trim().split("[^a-zA-Z0-9]+");
    StringBuilder sb = new StringBuilder();
    for (String part : parts) {
      if (part.isEmpty()) {
        continue;
      }
      sb.append(Character.toUpperCase(part.charAt(0)));
      if (part.length() > 1) {
        sb.append(part.substring(1));
      }
    }
    return sb.toString();
  }

  /** camelCase key → snake_case 컬럼명 변환 (예: memberName → member_name) */
  static String toSnakeCase(String camel) {
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

  /**
   * snake_case(또는 혼합 표기) key → camelCase Java 식별자 변환 (예: default_val → defaultVal).
   * 이미 camelCase(언더스코어 없음)인 입력은 그대로 반환한다 (예: title → title, postDate → postDate).
   * toSnakeCase와 짝을 이루는 함수로, toFieldMetas()에서 Java 필드명을 정할 때 사용한다.
   */
  static String toCamelCase(String raw) {
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

  private String capitalize(String s) {
    if (s == null || s.isEmpty()) {
      return s;
    }
    return Character.toUpperCase(s.charAt(0)) + s.substring(1);
  }

  private String decapitalize(String s) {
    if (s == null || s.isEmpty()) {
      return s;
    }
    return Character.toLowerCase(s.charAt(0)) + s.substring(1);
  }

  /** javadoc 주석 안전 처리 — 주석 종료 기호 충돌 및 줄바꿈 제거 */
  private String escapeJavadoc(String text) {
    if (text == null) {
      return "";
    }
    return text.replace("*/", "* /").replace("\r", " ").replace("\n", " ").trim();
  }

  /** Java 문자열 리터럴 안전 처리 — 따옴표/역슬래시/줄바꿈 이스케이프 */
  private String escapeJavaString(String text) {
    if (text == null) {
      return "";
    }
    return text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\r", " ");
  }
}
