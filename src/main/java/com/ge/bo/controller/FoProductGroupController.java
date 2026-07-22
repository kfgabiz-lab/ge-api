package com.ge.bo.controller;

import com.ge.bo.dto.FoProductGroupResponse;
import com.ge.bo.service.FoProductGroupService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * FO 공개 제품 그룹 API — 비로그인 전체 허용 (/api/v1/fo/**)
 * - FoProductGroupService 에 위임하는 얇은 래퍼 (Discover Our Products 섹션 조회 전용)
 */
@RestController
@RequestMapping("/api/v1/fo/product-groups")
@RequiredArgsConstructor
public class FoProductGroupController {

    private final FoProductGroupService foProductGroupService;

    /**
     * 제품 그룹 목록 조회
     * GET /api/v1/fo/product-groups
     * 공개(isVisible=001) 그룹 + 각 그룹 내 제품(ms) 상세를 정렬해 반환
     */
    @GetMapping
    public ResponseEntity<List<FoProductGroupResponse>> getProductGroups(
            @RequestHeader(value = "X-Site-Id", required = false) Long siteId) {
        return ResponseEntity.ok(foProductGroupService.getProductGroups(siteId));
    }
}
