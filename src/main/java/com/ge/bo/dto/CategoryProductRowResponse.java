package com.ge.bo.dto;

/**
 * FO 카테고리(Lv2) 랜딩 "제품 목록" 전용 응답 DTO
 * - Lv2 카테고리에 맵핑된 depth=3 연결행(category-data)을 통해 이어진 product-data 중
 *   공개(is_visible=001) + 판매중(order_status=01) 제품 1건을 표현한다.
 * - 카드 링크(href)·이미지 URL 가공은 FE 책임(slug/image 원본만 전달).
 *
 * @param id              page_data.id (제품 원본 행 PK)
 * @param productName     제품명(data_json.product.product_name)
 * @param image           업로드 이미지 파일ID 배열의 JSON 텍스트("[123]") 또는 null — FE에서 첫 id → page-files URL 변환
 * @param infoDescription 제품 요약 설명(data_json.product_info.info_description)
 * @param slug            상세 경로용 slug(data_json.seo.slug)
 * @param awards          수상 코드값(data_json.product.awards). "01"=iF Design Awards, 미수상은 빈 문자열/null.
 *                        기획서 product-lv2.png 9번 요건 — FE에서 이 값으로 카드 Design Awards 배지 노출 여부를 판정한다.
 */
public record CategoryProductRowResponse(
        Long id,
        String productName,
        String image,
        String infoDescription,
        String slug,
        String awards
) {}
