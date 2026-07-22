package com.ge.bo.controller;

import com.ge.bo.dto.DevicesTreeRowResponse;
import com.ge.bo.service.PageDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * FO GNB 메가메뉴 전용 API — 비로그인 전체 허용 (/api/v1/fo/**)
 * - FoMenuController(/api/v1/fo/menus/gnb) 는 menu 엔티티 기반 일반 GNB 메뉴 트리를 다루고,
 *   본 컨트롤러는 category-data/product-data(page_data) 기반의 "Devices & Systems" 메가메뉴 전용 조회를 담당한다.
 */
@RestController
@RequestMapping("/api/v1/fo/gnb")
@RequiredArgsConstructor
public class FoGnbController {

    private final PageDataService pageDataService;

    /**
     * "Devices & Systems" 메가메뉴 depth1(대분류)+depth2(하위분류)+depth3(제품) 트리 데이터 조회
     * GET /api/v1/fo/gnb/devices-tree
     * 기존 3회 개별 호출(depth1/depth2 반복/제품코드 접두사 매칭) 조립 방식을 단일 쿼리로 대체한다.
     * 응답은 평평한(flat) 행 리스트이며, 트리 조립(부모-자식 매칭)은 FE 책임이다.
     */
    @GetMapping("/devices-tree")
    public ResponseEntity<List<DevicesTreeRowResponse>> getDevicesTree(
            @RequestHeader(value = "X-Site-Id", required = false) Long siteId) {
        return ResponseEntity.ok(pageDataService.findDevicesTree(siteId));
    }
}
