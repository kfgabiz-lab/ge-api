package com.ge.bo.dto;

/**
 * FO GNB "Devices & Systems" 메가메뉴 전용 응답 DTO
 * - category-data 의 depth1(대분류)/depth2(하위분류)/depth3(제품 연결) 를 단일 쿼리로 조회한
 *   평평한(flat) 행 하나를 표현한다. 트리 조립(depth별 부모-자식 매칭)은 FE 책임이며 BE 는 중첩 구조로 가공하지 않는다.
 * - depth1/2 행은 category 관련 필드(및 sortOrder)가 채워지고 product 관련 필드는 null,
 *   depth3 행은 product* 필드가 채워지고 category_title/category_slug 는 null(단, parentId 는 depth 공통으로 항상 존재)
 *
 * @param rowId              page_data.id (category-data 원본 행 PK)
 * @param depth              "1"/"2"/"3" — category 섹션이 있으면 category.depth, 없으면(제품 연결 행) product.depth
 * @param parentId            상위 행의 row_id 문자열 (depth1은 빈 문자열)
 * @param categoryTitle       대분류/하위분류 제목 (depth3 행은 null)
 * @param categoryDescription 대분류/하위분류 설명 텍스트(줄바꿈 포함 일반 텍스트, depth3 행은 null)
 * @param categorySlug        대분류/하위분류 slug (depth3 행은 null)
 * @param sortOrder           정렬 순번 원본 문자열(depth1/2 전용, depth3 행은 null — 텍스트이므로 숫자 비교 시 별도 캐스팅 필요)
 * @param productId           depth3 행에서 연결된 product-data PK (FK), depth1/2 행은 null
 * @param productSlug         연결된 product-data 의 seo.slug
 * @param productTitle        연결된 product-data 의 product.product_name (junction 의 product_name 은 폐기 예정이라 사용하지 않음)
 * @param productDescription  연결된 product-data 의 product_info.info_description
 * @param productImage        연결된 product-data 의 product_info.image
 */
public record DevicesTreeRowResponse(
        Long rowId,
        String depth,
        String parentId,
        String categoryTitle,
        String categoryDescription,
        String categorySlug,
        String sortOrder,
        Long productId,
        String productSlug,
        String productTitle,
        String productDescription,
        String productImage
) {}
