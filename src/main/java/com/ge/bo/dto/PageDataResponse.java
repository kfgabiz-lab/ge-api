package com.ge.bo.dto;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ge.bo.entity.PageData;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;

/**
 * 페이지 데이터 단건 응답 DTO
 * dataJson: JSON 문자열 → Map 으로 변환하여 FE에 전달
 */
@Slf4j
@Getter
@Builder
public class PageDataResponse {

  private Long id;
  private String templateSlug;
  private Map<String, Object> dataJson;
    /** 다중 slug 저장 그룹 식별자 (UUID) — 단일 저장 시 null */
  private String groupId;
  private String createdBy;
  private LocalDateTime createdAt;
  private String updatedBy;
  private LocalDateTime updatedAt;

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** PageData 엔티티 → 응답 DTO 변환 */
  public static PageDataResponse from(PageData entity) {
    Map<String, Object> dataMap = parseDataJson(entity.getDataJson());
    return PageDataResponse.builder()
                .id(entity.getId())
                .templateSlug(entity.getTemplateSlug())
                .dataJson(dataMap)
                .groupId(entity.getGroupId())
                .createdBy(entity.getCreatedBy())
                .createdAt(entity.getCreatedAt())
                .updatedBy(entity.getUpdatedBy())
                .updatedAt(entity.getUpdatedAt())
                .build();
  }

  /** dataJson을 FETCH 결과로 보강한 새 인스턴스 반환 */
  public PageDataResponse withDataJson(Map<String, Object> newDataJson) {
    return PageDataResponse.builder()
                .id(this.id)
                .templateSlug(this.templateSlug)
                .dataJson(newDataJson)
                .groupId(this.groupId)
                .createdBy(this.createdBy)
                .createdAt(this.createdAt)
                .updatedBy(this.updatedBy)
                .updatedAt(this.updatedAt)
                .build();
  }

  /** createdBy/updatedBy를 이름으로 치환한 새 인스턴스 반환 */
  public PageDataResponse withUserNames(String createdByName, String updatedByName) {
    return PageDataResponse.builder()
                .id(this.id)
                .templateSlug(this.templateSlug)
                .dataJson(this.dataJson)
                .groupId(this.groupId)
                .createdBy(createdByName)
                .createdAt(this.createdAt)
                .updatedBy(updatedByName)
                .updatedAt(this.updatedAt)
                .build();
  }

    /** JSON 문자열 → Map 파싱 (파싱 실패 시 빈 Map 반환) */
  private static Map<String, Object> parseDataJson(String json) {
    try {
      return OBJECT_MAPPER.readValue(json, new TypeReference<>() {});
    } catch (Exception e) {
      log.warn("dataJson 파싱 실패: {}", e.getMessage());
      return Collections.emptyMap();
    }
  }
}
