package com.ge.bo.dto;

/**
 * 인접글(이전/다음) 응답 DTO — FO 상세 페이지 prev/next 네비게이션용
 * prev/next 각 항목은 해당 인접글이 없으면 null
 */
public record AdjacentResponse(AdjacentItem prev, AdjacentItem next) {

    /** 인접글 단건 — id, 표시용 제목(title), 상세 URL용 slug(미요청 시 null) */
    public record AdjacentItem(Long id, String title, String slug) {}
}
