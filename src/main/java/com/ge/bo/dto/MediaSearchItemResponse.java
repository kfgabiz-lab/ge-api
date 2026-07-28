package com.ge.bo.dto;

/**
 * FO 통합 미디어 검색 결과 1건 — 4개 소스(Tech Hub 영상, blog/press/articles 게시글)를 단일 포맷으로 합쳐 내린다.
 *
 * @param sourceType 소스 구분: TECH_HUB / BLOG / PRESS / ARTICLE
 * @param id         소스 내 원본 식별자(TECH_HUB=contents_master.id, 나머지=page_data.id) — link 조립/라우팅 키
 * @param title      표시 제목(TECH_HUB=nahp_title|doc_title, page_data=컨테이너 title)
 * @param snippet    본문 발췌(HTML raw) — page_data content 앞 500자 캡. TECH_HUB 은 null(스니펫 없음). FE 에서 stripHtml.
 * @param imageUrl   썸네일 URL — TECH_HUB=YouTube hqdefault(video_url 파싱), page_data=프록시(/api/v1/fo/page-files/{id}). 없으면 null.
 * @param sortDate   정렬/표시용 날짜 문자열 — TECH_HUB=source_updated_at(YYYY-MM-DD), page_data=publish_dttm 원본
 * @param link       상세 라우트(FE 라우팅 기준): /support/tech-hub/view/{id}, /company/{blog|press|articles}/detail/{id}
 */
public record MediaSearchItemResponse(
        String sourceType,
        Long id,
        String title,
        String snippet,
        String imageUrl,
        String sortDate,
        String link
) {}
