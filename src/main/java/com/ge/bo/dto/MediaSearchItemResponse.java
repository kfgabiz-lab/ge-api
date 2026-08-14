package com.ge.bo.dto;

/**
 * FO 통합 미디어 검색 결과 1건 — 5개 소스(Tech Hub 영상, integration_contents 의 blog/press/article/event 게시글)를 단일 포맷으로 합쳐 내린다.
 *
 * @param sourceType 소스 구분: TECH_HUB / BLOG / PRESS / ARTICLE / EVENT
 * @param id         소스 내 원본 식별자 — TECH_HUB=contents_master.id / 그 외=integration_contents.content_id(=원본 page_data.id). link 조립/라우팅 키
 * @param title      표시 제목(TECH_HUB=nahp_title|doc_title, 그 외=integration_contents.title)
 * @param snippet    본문 발췌(HTML raw) — integration_contents.content 앞 200자 캡. TECH_HUB 은 null(스니펫 없음). FE 에서 stripHtml.
 * @param imageUrl   썸네일 URL — TECH_HUB=YouTube hqdefault(video_url 파싱), 그 외=file_id 프록시(/api/v1/fo/page-files/{id}). 없으면 null.
 * @param sortDate   정렬/표시용 날짜 문자열(yyyy-MM-dd, 사이트 timezone 기준) — TECH_HUB=source_updated_at, 그 외=integration_contents.updated_at
 * @param link       상세 라우트(FE 라우팅 기준): /support/tech-hub/view/{id}, /company/{blog|press|articles|events}/detail/{id}
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
