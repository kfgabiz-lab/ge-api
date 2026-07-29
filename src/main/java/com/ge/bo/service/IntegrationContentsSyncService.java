package com.ge.bo.service;

import com.ge.bo.entity.IntegrationContents;
import com.ge.bo.exception.ErrorCode;
import com.ge.bo.repository.IntegrationContentsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * blog/articles/press(page_data) ↔ integration_contents(외부연동 테이블) 동기화
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IntegrationContentsSyncService {

  private final IntegrationContentsRepository integrationContentsRepository;

    /** slug(page_template.slug) → integration_contents.type 코드 — 실제 page_data.data_slug 값과 정확히 일치해야 한다(articles는 복수형) */
  private static final Map<String, String> SLUG_TYPE_CODE = Map.of(
      "blog-data", "B",
      "articles-data", "A",
      "press-data", "P"
  );

    /** slug → data_json 내 title/content가 담긴 중첩 객체 키 — slug에서 '-data'를 제거한 값(articles-data→articles) */
  private static final Map<String, String> SLUG_JSON_KEY = Map.of(
      "blog-data", "blog",
      "articles-data", "articles",
      "press-data", "press"
  );

    /** 코드값 기준 공개상태 — "001" 공개, 그 외 비공개 */
  private static final String VISIBLE_CODE = "001";

    /**
     * 등록/수정 반영 — content_id(=page_data.id) 기준 upsert
     * is_visible은 폼 자체의 공개상태 코드(is_visible=001)를 그대로 반영 — 작성자가 비공개로 저장하면 연동 데이터도 비노출
     * 대상 slug가 아니면 아무 동작 없음
     *
     * @param slug       page_data의 template_slug/data_slug
     * @param pageDataId 원본 page_data.id
     * @param dataJson   저장된 폼 데이터 (title/content/is_visible/image가 담긴 중첩 객체 포함)
     * @param siteId     page_data.site_id
     */
  @Transactional
    public void syncUpsert(String slug, Long pageDataId, Map<String, Object> dataJson, Long siteId) {
    String typeCode = SLUG_TYPE_CODE.get(slug);
    if (typeCode == null) {
      return;
    }

    Object nested = dataJson.get(SLUG_JSON_KEY.get(slug));
    if (!(nested instanceof Map<?, ?> fields)
        || fields.get("title") == null
        || fields.get("content") == null) {
      throw ErrorCode.INTEGRATION_CONTENTS_FIELD_MISSING.toException();
    }

    String contentId = String.valueOf(pageDataId);
    IntegrationContents entity = integrationContentsRepository.findByContentId(contentId)
        .orElseGet(() -> IntegrationContents.builder()
            .contentId(contentId)
            .build());
    entity.setType(typeCode);
    entity.setTitle(fields.get("title").toString());
    entity.setContent(fields.get("content").toString());
    entity.setVisible(VISIBLE_CODE.equals(fields.get("is_visible")));
    entity.setSiteId(siteId);
    entity.setFileId(extractFirstFileId(fields.get("image")));
    integrationContentsRepository.save(entity);
  }

    /** data_json의 image 필드(배열, 첫 값 하나만 사용)에서 file_id를 뽑아낸다 — 없으면 null */
  private Long extractFirstFileId(Object imageValue) {
    Object first = (imageValue instanceof List<?> list)
        ? (list.isEmpty() ? null : list.get(0))
        : imageValue;
    if (first instanceof Number number) {
      return number.longValue();
    }
    if (first == null) {
      return null;
    }
    try {
      return Long.parseLong(first.toString());
    } catch (NumberFormatException e) {
      return null;
    }
  }

    /**
     * 삭제 반영 — 물리삭제 대신 is_visible=false 처리
     * 대상 slug가 아니거나 연동 레코드가 없으면 아무 동작 없음
     *
     * @param slug       page_data의 template_slug/data_slug
     * @param pageDataId 원본 page_data.id
     */
  @Transactional
    public void syncSoftDelete(String slug, Long pageDataId) {
    if (!SLUG_TYPE_CODE.containsKey(slug)) {
      return;
    }
    integrationContentsRepository.findByContentId(String.valueOf(pageDataId))
        .ifPresent(entity -> {
          entity.setVisible(false);
          integrationContentsRepository.save(entity);
        });
  }
}
