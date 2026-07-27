package com.ge.bo.controller;

import com.ge.bo.dto.SiteSettingsResponse;
import com.ge.bo.exception.BusinessException;
import com.ge.bo.service.SiteService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * FO 공개용 사이트 설정 조회 API — 비로그인 전체 허용 (/api/v1/fo/**)
 * BO 관리자 화면과 동일한 SiteService 조회 로직을 재사용하고, timezone/locale만 얇게 내려준다.
 */
@RestController
@RequestMapping("/api/v1/fo/site-settings")
@RequiredArgsConstructor
public class FoSiteController {

    private final SiteService siteService;

    /**
     * GET /api/v1/fo/site-settings
     * X-Site-Id 미지정, 존재하지 않는 사이트, 비활성(isActive=false) 사이트 — 세 경우 모두
     * 동일하게 빈 설정(둘 다 null)을 200으로 반환한다(FO 쪽 기본값 폴백은 호출부 책임).
     * 미존재만 404로 다르게 응답하면 X-Site-Id 값을 무작위로 바꿔가며 유효 siteId 범위를 확인할 수 있고,
     * 비로그인·rate limit 없는 경로라 매 404가 오류로그 INSERT로 이어져 남용 시 error_log가 무제한 증가한다.
     */
    @GetMapping
    public ResponseEntity<SiteSettingsResponse> getSiteSettings(
            @RequestHeader(value = "X-Site-Id", required = false) Long siteId) {
        if (siteId == null) {
            return ResponseEntity.ok(new SiteSettingsResponse(null, null));
        }
        try {
            var site = siteService.getSiteById(siteId);
            if (!Boolean.TRUE.equals(site.getIsActive())) {
                return ResponseEntity.ok(new SiteSettingsResponse(null, null));
            }
            return ResponseEntity.ok(new SiteSettingsResponse(site.getTimezone(), site.getLocale()));
        } catch (BusinessException e) {
            return ResponseEntity.ok(new SiteSettingsResponse(null, null));
        }
    }
}
