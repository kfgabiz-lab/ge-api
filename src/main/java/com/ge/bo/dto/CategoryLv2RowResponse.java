package com.ge.bo.dto;

/**
 * FO 카테고리 랜딩 "하위 카테고리(Lv2)" 목록 전용 응답 DTO
 * - category-data 중 Lv1(:categoryId)에 맵핑된 depth=2 행이면서 공개(is_visible=001)이고,
 *   그 하위 Lv3(제품 연결)에 공개+판매중 제품이 1건 이상 존재하는 카테고리 1건을 표현한다.
 * - 카드 링크(href)·이미지 URL 가공은 FE 책임(slug/image 원본만 전달).
 *
 * @param id    page_data.id (Lv2 카테고리 원본 행 PK)
 * @param title 카테고리명(data_json.category.title)
 * @param slug  상세 경로용 slug(data_json.seo.slug)
 * @param image 업로드 이미지 파일ID 배열의 JSON 텍스트("[123]") 또는 null — FE에서 첫 id → page-files URL 변환
 */
public record CategoryLv2RowResponse(
        Long id,
        String title,
        String slug,
        String image
) {}
