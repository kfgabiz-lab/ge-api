package com.ge.bo.dto;

/**
 * FO Tech Hub 상세 — 챕터(= contents_version) 1건. 다버전 콘텐츠의 Chapter 사이드바용.
 * - 정렬은 sort_key DESC(= Chapter 1 먼저, sort_key -1 이 최상단). chapterName 은 version_name(번호 "01".."26").
 * - version_desc 는 실데이터가 전부 비어있어 제외. 표시 제목은 FE가 master.title + "Chapter {chapterName}"으로 조합.
 * - 각 챕터가 자기 video_url 을 가지므로, FE는 사이드바 클릭 시 페이지 이동 없이 클라이언트에서 영상만 전환한다.
 *
 * @param versionId  contents_version.id (현재 재생 중 판별/하이라이트 키)
 * @param chapterName version_name (챕터 번호)
 * @param sortKey    정렬 키(음수, -1 이 Chapter 1)
 * @param videoUrl   챕터 영상 YouTube URL
 */
public record TechHubChapterResponse(
        Long versionId,
        String chapterName,
        int sortKey,
        String videoUrl
) {}
