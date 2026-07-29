package com.ge.bo.dto;

/**
 * FO 메인화면 레이어팝업(popup-data) 활성 팝업 1건을 표현하는 응답 DTO
 * - 게시기간(post_period_from ~ post_period_to)이 오늘을 포함하는 팝업이면 활성으로 간주, 여러 건이면 배열로 전달.
 * - 이미지 자체는 반환하지 않고 파일ID만 전달 → FE가 기존 /api/v1/fo/page-files/{fileId}로 별도 조회.
 *
 * @param id          팝업 식별자(page_data.id) — FE가 팝업별 개별 닫기/오늘 그만보기 상태를 키로 구분할 때 사용
 * @param url         팝업 클릭 시 이동 URL(data_json.popup.url)
 * @param imageFileId 팝업 이미지 파일ID(data_json.popup.image 배열의 첫 원소) — page_file.file_id
 */
public record PopupResponse(
        Long id,
        String url,
        Long imageFileId
) {}
