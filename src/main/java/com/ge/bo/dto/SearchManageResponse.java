package com.ge.bo.dto;

import com.ge.bo.entity.SearchManage;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 검색관리 응답 DTO
 * - 목록 조회 시: texts 빈 배열
 * - 단건 조회 시: texts 포함 (최신순)
 */
public record SearchManageResponse(
    Long id,
    String url,
    Boolean active,
    /** 분류(페이지 섹션) — code_detail(group_code='PAGE_SECTION') 코드값. 미지정이면 null */
    String pageSection,
    /** 연동된 FO 메뉴 id — 수동 URL 입력이면 null */
    Long menuId,
    /** 연동된 FO 메뉴명 — BO 화면에서 셀렉트박스 초기 라벨 표시용. 미연동이면 null */
    String menuName,
    Integer textCount,
    List<SearchManageTextResponse> texts,
    String createdBy,
    OffsetDateTime createdAt,
    String updatedBy,
    OffsetDateTime updatedAt
) {
    /** 목록 조회용 — texts 빈 배열 */
    public static SearchManageResponse fromList(SearchManage e) {
        return new SearchManageResponse(
            e.getId(), e.getUrl(), e.getActive(), e.getPageSection(),
            e.getMenu() != null ? e.getMenu().getId() : null,
            e.getMenu() != null ? e.getMenu().getName() : null,
            e.getTexts().size(), List.of(),
            e.getCreatedBy(), e.getCreatedAt(), e.getUpdatedBy(), e.getUpdatedAt()
        );
    }

    /** 단건 조회용 — texts 포함 */
    public static SearchManageResponse from(SearchManage e) {
        List<SearchManageTextResponse> textList = e.getTexts().stream()
            .map(SearchManageTextResponse::from)
            .toList();
        return new SearchManageResponse(
            e.getId(), e.getUrl(), e.getActive(), e.getPageSection(),
            e.getMenu() != null ? e.getMenu().getId() : null,
            e.getMenu() != null ? e.getMenu().getName() : null,
            textList.size(), textList,
            e.getCreatedBy(), e.getCreatedAt(), e.getUpdatedBy(), e.getUpdatedAt()
        );
    }
}
