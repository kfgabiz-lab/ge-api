package com.ge.bo.controller;

import com.ge.bo.dto.PopupResponse;
import com.ge.bo.service.PageDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * FO 메인화면 레이어팝업(popup-data) 전용 API — 비로그인 전체 허용 (/api/v1/fo/**)
 * - 게시기간이 오늘을 포함하는 활성 팝업 최신 1건을 서버에서 필터링해 반환한다.
 * - 이미지 파일ID만 전달하며, 이미지 자체는 FE가 기존 /api/v1/fo/page-files/{fileId}로 별도 조회한다.
 */
@RestController
@RequestMapping("/api/v1/fo/popup")
@RequiredArgsConstructor
public class FoPopupController {

    private final PageDataService pageDataService;

    /**
     * 활성 레이어팝업 1건 조회
     * GET /api/v1/fo/popup
     * 활성 팝업이 없으면 {"exists": false, "url": null, "imageFileId": null} 반환.
     */
    @GetMapping
    public ResponseEntity<PopupResponse> getActivePopup(
            @RequestHeader(value = "X-Site-Id", required = false) Long siteId) {
        return ResponseEntity.ok(pageDataService.findActivePopup(siteId));
    }
}
