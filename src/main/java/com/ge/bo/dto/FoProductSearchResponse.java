package com.ge.bo.dto;

import java.util.List;

/**
 * FO 제품 키워드 검색 전용 응답 DTO
 * - product-data 중 공개(is_visible=001) 상태에서 제품명/제품설명이 키워드와 부분일치하는 제품 목록을 표현한다.
 * - total: 키워드에 매칭되는 전체 건수(카드 표시 limit과 별개 — Products 탭 "99+" 표기용)
 * - items: 실제로 반환하는 상위 limit 건의 제품 카드 정보
 *
 * @param total 키워드 매칭 전체 건수(limit 무관)
 * @param items 상위 limit 건의 제품 목록
 */
public record FoProductSearchResponse(
        long total,
        List<Item> items
) {
    /**
     * 검색 결과 제품 1건
     *
     * @param id                 page_data.id (제품 원본 행 PK)
     * @param productName        제품명(data_json.product.product_name)
     * @param productDescription 제품 설명(data_json.product.product_description)
     * @param image              대표 이미지 프록시 URL("/api/v1/fo/page-files/{미디어ID}") 또는 null
     * @param slug               상세 페이지 slug(data_json.seo.slug) 또는 null
     * @param category           소속 L1 카테고리명(category-data depth=1 title) 또는 null
     * @param highlight          소속 L2 카테고리명(category-data depth=2 title) 또는 null
     */
    public record Item(
            Long id,
            String productName,
            String productDescription,
            String image,
            String slug,
            String category,
            String highlight
    ) {}
}
