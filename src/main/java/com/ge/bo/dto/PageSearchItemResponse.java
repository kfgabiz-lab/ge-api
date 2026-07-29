package com.ge.bo.dto;

/**
 * FO 통합검색 Pages 탭 결과 1건.
 *
 * <p>원천은 관리자 기능 "검색관리"(search_manage + search_manage_text)다.
 * search_manage(=페이지 URL) 1건에 search_manage_text(=검색용 제목/텍스트) 가 N건 붙는 1:N 구조이며,
 * 검색결과는 <b>URL 기준으로 중복 제거</b>되어 한 페이지가 한 번만 노출된다.
 * 따라서 title/snippet 은 그 URL 에 붙은 여러 텍스트 중 "대표 1건"의 값이다.</p>
 *
 * @param id          search_manage.id (URL 단위 식별자. FE 목록 key 용)
 * @param url         search_manage.url — 이동 대상 경로. NOT NULL 컬럼이라 항상 값이 있다.
 * @param title       표시 제목. 대표 검색텍스트의 title 이며, title 이 NULL 이면 url 로 폴백한다(→ null 로 내려가지 않음).
 * @param snippet     본문 발췌 = 대표 검색텍스트의 text(평문, 200자 캡). text 는 NOT NULL 이라 항상 값이 있다.
 * @param section     분류 코드값(search_manage.page_section, 예 "MARKETS"). FK 아닌 얕은 참조라 미지정/코드 삭제 시 null.
 * @param sectionName 분류 표시명(code_detail.name, 예 "Markets"). section 이 null 이거나 매칭 코드가 없으면 null(폴백 문자열 생성하지 않음).
 */
public record PageSearchItemResponse(
        Long id,
        String url,
        String title,
        String snippet,
        String section,
        String sectionName
) {}
