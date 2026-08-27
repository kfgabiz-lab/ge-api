package com.ge.bo.controller;

import com.ge.bo.service.SitemapService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/**
 * FO 공개 sitemap.xml — 비로그인 전체 허용 (/api/v1/fo/**).
 * FO(Next.js rewrites) 또는 CDN/Front Door가 {@code /sitemap.xml} → 이 엔드포인트로 프록시한다.
 */
@RestController
@RequestMapping("/api/v1/fo")
@RequiredArgsConstructor
public class FoSitemapController {

    private final SitemapService sitemapService;

    /**
     * GET /api/v1/fo/sitemap.xml
     * 인메모리 스냅샷을 반환하고, 없으면 즉시 1회 생성한다(일배치 SitemapScheduler가 매일 갱신).
     * {@code ?refresh=Y} 를 주면 캐시를 무시하고 그 요청에서 강제로 재생성한다.
     */
    @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> sitemap(
            @RequestParam(name = "refresh", required = false) String refresh) {
        boolean forceRefresh = "Y".equalsIgnoreCase(refresh);
        String xml = forceRefresh ? sitemapService.rebuild().xml() : sitemapService.getOrBuildXml();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_XML)
                .cacheControl(forceRefresh
                        ? CacheControl.noStore()
                        : CacheControl.maxAge(Duration.ofHours(1)).cachePublic())
                .body(xml);
    }
}
