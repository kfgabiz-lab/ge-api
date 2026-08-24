package com.ge.bo.dto;

/**
 * FO 제품상세 "Insights"(제품 맵핑 게시글) 전용 응답 DTO
 * - blog-data/press-data/articles-data 중 data_json.product_list(JSONB 배열)에 현재 제품 id가 포함되고
 *   공개(is_visible=001) + 게시일(publish_dttm) 과거 조건을 만족하는 게시글 1건을 표현한다.
 * - tag(Press/Blog/Articles)·상세 href·이미지 URL 가공은 FE 책임(dataSlug/image 원본만 전달).
 *
 * @param id          page_data.id (게시글 원본 행 PK)
 * @param dataSlug    'blog-data' | 'press-data' | 'articles-data' — FE에서 태그/상세경로 판별에 사용
 * @param title       게시글 제목(data_json.title)
 * @param publishDttm 게시일시 원본 문자열(data_json.publish_dttm)
 * @param image       업로드 이미지 파일ID 배열의 JSON 텍스트("[123]") 또는 null — FE에서 첫 id → page-files URL 변환
 * @param slug        SEO slug(data_json.seo.slug) 또는 null — FE 상세 경로 {id}/{slug} 구성에 사용
 */
public record ProductInsightRowResponse(
        Long id,
        String dataSlug,
        String title,
        String publishDttm,
        String image,
        String slug
) {}
