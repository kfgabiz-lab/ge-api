package com.ge.bo.dto;

import java.util.List;

/**
 * FO Tech Hub 콘텐츠 상세 응답(콘텐츠 = contents_master 단위) — tech3.png
 * - 상세 API 1회 호출로 챕터 전체를 받아 FE가 클라이언트에서 영상 전환(페이지 이동 없이 상태 유지, tech3 정책).
 * - versionCount >= 2 : chapters(Chapter 사이드바) 사용, relatedVideos 는 빈 목록.
 * - versionCount == 1 : Related Video(동일 LV2 최신 3, 자기 제외) 사용. 동일 LV2 콘텐츠 없으면 빈 목록(FE 영역 미노출).
 * - 기본 재생 영상은 chapters[0](= Chapter 1, sort_key 최대).
 *
 * @param id              contents_master.id
 * @param title           표시 제목(nahp_title 우선, 없으면 doc_title)
 * @param sourceUpdatedAt 원천 갱신일(YYYY-MM-DD)
 * @param categoryL1Id    LV1 카테고리 코드
 * @param categoryL2Id    LV2 카테고리 코드
 * @param versionCount    노출 버전 수(2 이상 → Chapter, 1 → Related Video)
 * @param chapters        노출 버전(챕터) 목록, sort_key DESC(Chapter 1 먼저)
 * @param relatedVideos   단일버전일 때만 채워지는 동일 LV2 관련 영상(카드 형태 재사용), 그 외 빈 목록
 */
public record TechHubDetailResponse(
        Long id,
        String title,
        String sourceUpdatedAt,
        String categoryL1Id,
        String categoryL2Id,
        int versionCount,
        List<TechHubChapterResponse> chapters,
        List<TechHubContentResponse> relatedVideos
) {}
