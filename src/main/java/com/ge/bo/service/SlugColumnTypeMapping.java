package com.ge.bo.service;

import com.ge.bo.exception.BusinessException;

/**
 * Slug Entity 코드 생성 시 사용하는 "DB 컬럼타입 → Java 타입" 결정론적 매핑표.
 * - SlugEntityFieldRequest에서 허용하는 컬럼타입 10종(VARCHAR/TEXT/BIGINT/INT/BOOLEAN/TIMESTAMPTZ/DATE/JSONB/FILE/ENTITY_REF)과 1:1로 매핑된다.
 * - 매핑표에 없는 컬럼타입이 들어오면 fail-fast로 즉시 코드 생성을 차단한다. (fromColumnType 참고)
 */
enum SlugColumnTypeMapping {

  /** 가변 길이 문자열 — 길이 지정 필요 */
  VARCHAR("String", null, "VARCHAR", true, false, false),
  /** 긴 텍스트 — 길이 제한 없음 */
  TEXT("String", null, "TEXT", false, false, false),
  /** 64bit 정수 */
  BIGINT("Long", null, "BIGINT", false, false, false),
  /** 32bit 정수 */
  INT("Integer", null, "INTEGER", false, false, false),
  /** 참/거짓 */
  BOOLEAN("Boolean", null, "BOOLEAN", false, false, false),
  /** 날짜(시간 없음) */
  DATE("LocalDate", "java.time.LocalDate", "DATE", false, false, false),
  /** 타임존 포함 일시 */
  TIMESTAMPTZ("OffsetDateTime", "java.time.OffsetDateTime", "TIMESTAMPTZ", false, false, false),
  /** JSON — PageData.java의 매핑 방식(@Type(JsonStringType.class) + columnDefinition=jsonb)과 동일하게 적용 */
  JSONB("String", null, "JSONB", false, true, false),
  /**
   * 파일 메타정보 참조 — file_meta.id(BIGINT) 목록을 저장.
   * Form 위젯의 file 타입 필드는 파일 개수와 무관하게 항상 배열로 값을 싣기 때문에(예: [12] 또는 []),
   * 단일 Long이 아니라 List&lt;Long&gt; / bigint[] 배열 컬럼으로 매핑한다. (array=true)
   */
  FILE("List<Long>", null, "BIGINT[]", false, false, true),
  /**
   * 연동 대상 Slug Entity 레코드 참조 — 연동 Entity의 레코드 id(BIGINT) 목록을 저장.
   * Form 위젯의 다중 선택 값이 파일과 마찬가지로 항상 배열로 실리기 때문에,
   * 단일 Long이 아니라 List&lt;Long&gt; / bigint[] 배열 컬럼으로 매핑한다. (FILE과 동일한 array=true 방식 재사용)
   * 어느 Entity를 연동 대상으로 삼는지는 slug_entity_field.connected_entity_id(메타 컬럼)가 별도로 지정한다.
   */
  ENTITY_REF("List<Long>", null, "BIGINT[]", false, false, true);

  private final String javaType;
  private final String javaImportFqcn;
  private final String ddlType;
  private final boolean lengthApplicable;
  private final boolean jsonb;
  private final boolean array;

  SlugColumnTypeMapping(String javaType, String javaImportFqcn, String ddlType,
      boolean lengthApplicable, boolean jsonb, boolean array) {
    this.javaType = javaType;
    this.javaImportFqcn = javaImportFqcn;
    this.ddlType = ddlType;
    this.lengthApplicable = lengthApplicable;
    this.jsonb = jsonb;
    this.array = array;
  }

  /** 생성될 Java 필드/DTO에 사용할 타입 단순명 (예: String, Long, LocalDate) */
  String javaType() {
    return javaType;
  }

  /** 생성될 코드 상단에 추가로 필요한 import 문 (없으면 null — java.lang 타입 등) */
  String javaImportFqcn() {
    return javaImportFqcn;
  }

  /** DDL 생성 시 사용할 컬럼 타입 문자열 (길이는 별도로 붙임) */
  String ddlType() {
    return ddlType;
  }

  /** VARCHAR처럼 길이(length) 지정이 의미 있는 타입인지 여부 */
  boolean lengthApplicable() {
    return lengthApplicable;
  }

  /** JSONB 특수 매핑(PageData.java 방식) 적용 대상인지 여부 */
  boolean jsonb() {
    return jsonb;
  }

  /** 배열(bigint[]) 매핑 적용 대상인지 여부 — FILE(파일 id 목록), ENTITY_REF(연동 Entity 레코드 id 목록)가 해당 */
  boolean array() {
    return array;
  }

  /**
   * columnType 문자열을 매핑 정보로 변환한다. 매핑표에 없으면 null을 반환한다.
   * (검증 단계에서 여러 오류를 한 번에 모아 반환하기 위해 예외를 던지지 않는 버전)
   */
  static SlugColumnTypeMapping tryParse(String columnType) {
    if (columnType == null || columnType.isBlank()) {
      return null;
    }
    try {
      return valueOf(columnType.trim());
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  /**
   * columnType 문자열을 매핑 정보로 변환한다.
   * 매핑표에 없으면 즉시 BusinessException(400)을 던져 코드 생성을 차단한다. (fail-fast)
   */
  static SlugColumnTypeMapping fromColumnType(String columnType) {
    SlugColumnTypeMapping mapping = tryParse(columnType);
    if (mapping == null) {
      throw BusinessException.badRequest("매핑되지 않은 컬럼타입: " + columnType);
    }
    return mapping;
  }
}
