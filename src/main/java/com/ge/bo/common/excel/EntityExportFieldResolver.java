package com.ge.bo.common.excel;

import com.ge.bo.entity.SlugEntityField;
import com.ge.bo.repository.SlugEntityFieldRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Entity CSV export 시 FILE 타입 필드를 SlugEntityField 메타데이터 기준으로 해석하는 헬퍼.
 *
 * <p>FILE 타입은 DB에 {@code bigint[]} + {@code @JdbcTypeCode(ARRAY)}로 매핑되어 있어
 * 생성 Entity 클래스의 리플렉션만으로는 다른 배열 타입과 구분할 수 없다. 따라서 {@code slug_entity_field}
 * 메타데이터(column_type, column_name)를 조회해야만 FILE 컬럼을 식별할 수 있다.
 * 이 클래스는 그 메타 조회 · 분류(단일책임)를 {@link EntityExcelExportService}에서 분리해 담당한다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EntityExportFieldResolver {

  private final SlugEntityFieldRepository slugEntityFieldRepository;

  /**
   * export 대상 slug의 특수 필드 분류 결과.
   *
   * @param fileColumns FILE 타입 필드의 DB 컬럼명(snake_case) 집합 — CSV에서 완전히 제외 대상
   */
  public record ExportFieldMeta(Set<String> fileColumns) {
    /** 메타 없음(=일반 export) */
    static ExportFieldMeta empty() {
      return new ExportFieldMeta(Set.of());
    }
  }

  /**
   * slug에 해당하는 export 대상 Entity의 필드 메타데이터를 조회해 FILE 컬럼을 분류한다.
   * 메타가 없거나 조회 실패 시에는 빈 결과(=일반 export)로 폴백한다. (export 자체를 실패시키지 않음)
   */
  public ExportFieldMeta resolveMeta(String slug) {
    List<SlugEntityField> fields;
    try {
      fields = slugEntityFieldRepository.findAllByEntitySlugFetchConnected(slug);
    } catch (Exception e) {
      log.warn("entity export 메타 조회 실패, 일반 export로 진행 (slug={}): {}", slug, e.getMessage());
      return ExportFieldMeta.empty();
    }
    if (fields == null || fields.isEmpty()) {
      return ExportFieldMeta.empty();
    }

    Set<String> fileColumns = new HashSet<>();

    for (SlugEntityField f : fields) {
      String columnType = f.getColumnType();
      String columnName = f.getColumnName();
      if (columnType == null || columnName == null || columnName.isBlank()) {
        continue;
      }

      if ("FILE".equals(columnType)) {
        // FILE 타입 — CSV에서 완전 제외
        fileColumns.add(columnName);
      }
    }

    return new ExportFieldMeta(fileColumns);
  }
}
