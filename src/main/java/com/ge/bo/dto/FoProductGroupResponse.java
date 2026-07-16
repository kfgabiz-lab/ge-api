package com.ge.bo.dto;

import lombok.Builder;

import java.util.List;

/**
 * FO 공개 제품 그룹 응답 DTO — 비로그인 공개 API용 경량 응답 (Discover Our Products 섹션)
 * - 그룹(prdGrp-data, product_group) 1건 + 그 안의 제품(ms) 목록을 제품 상세(product-data)로 치환한 형태
 * - 존재하지 않는 제품 참조(dangling)는 ms에서 제외됨, image/slug 미입력 시 null
 */
@Builder
public record FoProductGroupResponse(
        /** 그룹 데이터 PK (page_data.id) */
        Long id,
        /** 그룹명 (product_group.group_name) */
        String prdGrpNm,
        /** 그룹 정렬값 (product_group.group_order, 원본 문자열 유지) */
        String prdGrpOrd,
        /** 그룹 내 제품 목록 (sortOrder 오름차순, 동률 시 제품 id 오름차순) */
        List<Product> ms
) {

    /**
     * 그룹에 속한 개별 제품 — product-data 상세 + 그룹 내 정렬값(sortOrder) 결합
     */
    @Builder
    public record Product(
            /** 제품 데이터 PK (product-data.id) */
            Long id,
            /** 제품명 (product.product_name) */
            String productNm,
            /** 제품 설명 (product.product_description) */
            String prdSubDesc,
            /** 수상 코드값 (product.awards, 값 있으면 FE에서 배지 노출) */
            String awards,
            /** 대표 이미지 URL (product_info.image 배열 첫 요소 미디어 ID → /api/v1/fo/page-files/{id}, 미입력 시 null) */
            String image,
            /** 상세 페이지 슬러그 (seo.slug, 미입력 시 null) */
            String slug,
            /** 그룹 내 정렬값 (ms 항목의 sortOrder, 원본 문자열 유지) */
            String sortOrder
    ) {
    }
}
