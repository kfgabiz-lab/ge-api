package com.ge.bo.controller;

import com.ge.bo.dto.FoGnbMenuResponse;
import com.ge.bo.dto.FoMenuMetaResponse;
import com.ge.bo.service.MenuService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * FO 공개 메뉴 API — 비로그인 전체 허용 (/api/v1/fo/**)
 */
@RestController
@RequestMapping("/api/v1/fo/menus")
@RequiredArgsConstructor
public class FoMenuController {

    private final MenuService menuService;

    /**
     * GNB 메뉴 조회
     * GET /api/v1/fo/menus/gnb
     * visible=true 루트 메뉴 + 자식 메뉴 트리 반환
     */
    @GetMapping("/gnb")
    public ResponseEntity<List<FoGnbMenuResponse>> getGnbMenus(
            @RequestHeader(value = "X-Site-Id", required = false) Long siteId) {
        return ResponseEntity.ok(menuService.getFoGnbMenus(siteId));
    }

    /**
     * FO 정적 메뉴 페이지 SEO 메타 조회
     * GET /api/v1/fo/menus/meta?url=/markets/power-grid
     */
    @GetMapping("/meta")
    public ResponseEntity<FoMenuMetaResponse> getMenuMeta(
            @RequestParam String url,
            @RequestHeader(value = "X-Site-Id", required = false) Long siteId) {
        return ResponseEntity.ok(menuService.getFoMenuMeta(url, siteId));
    }
}
