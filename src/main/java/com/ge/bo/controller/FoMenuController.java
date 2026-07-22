package com.ge.bo.controller;

import com.ge.bo.dto.FoGnbMenuResponse;
import com.ge.bo.service.MenuService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
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
}
