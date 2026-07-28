package com.ge.bo.batch.contents;

import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 원천 공통 문서 모델 — contents_master 1건 + 하위 카테고리/버전(+파일) 목록에 대응.
 * 원천별 Converter가 IF 원천 행을 이 모델로 변환하면, ContentsWriter는 원천을 구분하지 않고 동일 로직으로 저장한다.
 */
@Getter
@Builder
public class ContentDocument {
    private final SourceSystem sourceSystem;
    private final String sourceDocKey;
    private final String docType;
    private final String docTitle;
    private final String nahpTitle;
    private final String nahpLang;
    private final boolean expose;
    private final Map<String, Object> attrs;
    private final OffsetDateTime sourceCreatedAt;
    private final OffsetDateTime sourceUpdatedAt;
    /** 소스의 명시적 삭제 신호(CATALOG USE_YN='D', SSQ delete_yn='Y') — 삭제여부검증(누락 기반)과는 별개로 즉시 반영 */
    private final boolean explicitDelete;
    @Builder.Default
    private final List<CategoryItem> categories = new ArrayList<>();
    @Builder.Default
    private final List<VersionItem> versions = new ArrayList<>();
}
