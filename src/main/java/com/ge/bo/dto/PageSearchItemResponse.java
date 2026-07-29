package com.ge.bo.dto;

/**
 * FO 통합검색 Pages 탭 결과 1건.
 *
 * <p>대상 데이터는 integration_contents 중 {@code type} 이 없는(NULL/빈문자) 행이다.
 * (type 'B'/'P'/'A' 는 Media 탭이 담당)</p>
 *
 * @param id      integration_contents.id (원본 page_data 역참조 없음 — content_id 가 비어 있는 데이터)
 * @param title   표시 제목(integration_contents.title, 평문 저장)
 * @param snippet 본문 발췌 — integration_contents.content(HTML)를 SQL 에서 평문화한 뒤 앞 200자 캡
 */
public record PageSearchItemResponse(
        Long id,
        String title,
        String snippet
) {}
