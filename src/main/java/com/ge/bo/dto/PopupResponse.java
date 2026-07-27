package com.ge.bo.dto;

/**
 * FO 메인화면 레이어팝업(popup-data) 활성 1건 조회 응답 DTO
 * - 게시기간(post_period_from ~ post_period_to)이 오늘을 포함하는 활성 팝업 최신 1건을 표현한다.
 * - 활성 팝업이 없으면 exists=false, 나머지 필드 null.
 * - 이미지 자체는 반환하지 않고 파일ID만 전달 → FE가 기존 /api/v1/fo/page-files/{fileId}로 별도 조회.
 *
 * @param exists      활성 팝업 존재 여부
 * @param url         팝업 클릭 시 이동 URL(data_json.popup.url)
 * @param imageFileId 팝업 이미지 파일ID(data_json.popup.image 배열의 첫 원소) — page_file.file_id
 */
public record PopupResponse(
        boolean exists,
        String url,
        Long imageFileId
) {}
