package com.ge.bo.dto;

/**
 * FO Tech Hub 목록 카드 1건(콘텐츠 = contents_master 단위)
 * - 영상 콘텐츠(doc_type='V')만 대상. 다버전(챕터) 시리즈도 카드 1장으로 노출하고,
 *   대표 영상(Chapter 1 = sort_key 최대 = version_name '1')의 video_url을 카드 썸네일/링크 기준으로 쓴다.
 * - 썸네일 URL 가공은 FE(youtubeEmbed.getYoutubePosterSrc)에서 video_url로부터 파생한다(BE는 video_url만 전달).
 *
 * @param id              contents_master.id (카드/상세 라우팅 키)
 * @param title           표시 제목: nahp_title 우선, 없으면 doc_title
 * @param sourceUpdatedAt 원천 갱신일(YYYY-MM-DD) — 최신 등록순 정렬/표시용(created_at은 배치적재시각이라 미사용)
 * @param categoryL1Id    LV1 카테고리 코드(category-data category.code depth1)
 * @param categoryL2Id    LV2 카테고리 코드(category-data category.code depth2)
 * @param videoUrl        대표 버전(Chapter 1)의 YouTube URL
 * @param versionCount    노출 버전 수(2 이상이면 상세에서 Chapter 사이드바, 1이면 Related Video 폴백)
 */
public record TechHubContentResponse(
        Long id,
        String title,
        String sourceUpdatedAt,
        String categoryL1Id,
        String categoryL2Id,
        String videoUrl,
        int versionCount
) {}
