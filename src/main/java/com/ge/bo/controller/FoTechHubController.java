package com.ge.bo.controller;

import com.ge.bo.dto.TechHubCategoryCountResponse;
import com.ge.bo.dto.TechHubCertCountResponse;
import com.ge.bo.dto.TechHubContentPageResponse;
import com.ge.bo.dto.TechHubDetailResponse;
import com.ge.bo.service.TechHubService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

/**
 * FO Tech Hub 전용 API — 비로그인 전체 허용 (/api/v1/fo/**)
 * - 데이터 소스: contents_master/version/category(SSQ 배치 적재 영상 콘텐츠). page_data slug 아님.
 * - STEP1: 목록(카드) 조회 + 카테고리별 건수. 상세(Chapter/Related Video)는 이후 STEP.
 */
@RestController
@RequestMapping("/api/v1/fo/tech-hub")
@RequiredArgsConstructor
public class FoTechHubController {

    private final TechHubService techHubService;

    /**
     * Tech Hub 목록(카드) 조회
     * GET /api/v1/fo/tech-hub/contents?q={키워드}&categories={LV2코드,콤마구분}&certs={ul,iec}&page=0&size=12
     * - categories: LV2 카테고리 코드 콤마구분(그룹 내 OR). 미지정 시 전체.
     * - certs: 인증 코드 콤마구분(ul/iec, 그룹 내 OR). 미지정 시 전체(기존 동작 유지) → 카테고리와는 AND.
     * - 정렬 source_updated_at DESC, 페이지 크기 기본 12.
     */
    @GetMapping("/contents")
    public ResponseEntity<TechHubContentPageResponse> getContents(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String categories,
            @RequestParam(required = false) String certs,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {
        List<String> categoryL2Ids = parseCsv(categories);
        List<String> certCodes = parseCsv(certs);
        return ResponseEntity.ok(techHubService.getContents(q, categoryL2Ids, certCodes, page, size));
    }

    /**
     * Tech Hub 콘텐츠 상세 조회 (콘텐츠 = contents_master)
     * GET /api/v1/fo/tech-hub/contents/{masterId}
     * - 챕터 전체(다버전) + 단일버전이면 Related Video 를 한 번에 반환(FE 클라이언트 전환).
     * - 미존재/미노출/재생가능버전 없음 → 404.
     */
    @GetMapping("/contents/{masterId}")
    public ResponseEntity<TechHubDetailResponse> getContentDetail(@PathVariable Long masterId) {
        TechHubDetailResponse detail = techHubService.getContentDetail(masterId);
        if (detail == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(detail);
    }

    /**
     * LV2 카테고리별 콘텐츠 건수(필터 아코디언 숫자)
     * GET /api/v1/fo/tech-hub/category-counts
     */
    @GetMapping("/category-counts")
    public ResponseEntity<List<TechHubCategoryCountResponse>> getCategoryCounts() {
        return ResponseEntity.ok(techHubService.getCategoryCounts());
    }

    /**
     * 인증(UL/IEC)별 콘텐츠 건수(필터 패널 숫자)
     * GET /api/v1/fo/tech-hub/cert-counts
     * - UL/IEC 두 항목 항상 반환(0 포함). IEC/UL 동시 취득 콘텐츠는 양쪽에 가산.
     */
    @GetMapping("/cert-counts")
    public ResponseEntity<List<TechHubCertCountResponse>> getCertCounts() {
        return ResponseEntity.ok(techHubService.getCertCounts());
    }

    /** "L01-12,L02-01" → ["L01-12","L02-01"] (공백/빈값 제거). null/blank → null(필터 미적용). */
    private List<String> parseCsv(String csv) {
        if (csv == null || csv.isBlank()) return null;
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
