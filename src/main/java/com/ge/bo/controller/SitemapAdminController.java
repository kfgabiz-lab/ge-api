package com.ge.bo.controller;

import com.ge.bo.service.SitemapService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * sitemap 온디맨드 재생성 — 관리자 전용.
 * (인메모리 스냅샷이라 호출한 인스턴스에만 즉시 반영됨. 전체 반영은 일배치 SitemapScheduler가 담당.)
 */
@RestController
@RequestMapping("/api/v1/sitemap")
@RequiredArgsConstructor
@PreAuthorize("@securityService.isSystemAdmin(authentication) or @securityService.isSuperAdmin(authentication)")
public class SitemapAdminController {

    private final SitemapService sitemapService;

    /** POST /api/v1/sitemap/rebuild — 즉시 재생성 후 URL 수 / 생성시각 반환. */
    @PostMapping("/rebuild")
    public ResponseEntity<Map<String, Object>> rebuild() {
        SitemapService.Snapshot snap = sitemapService.rebuild();
        return ResponseEntity.ok(Map.of(
                "urlCount", snap.urlCount(),
                "generatedAt", snap.generatedAt()));
    }
}
