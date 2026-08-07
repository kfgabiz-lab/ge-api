package com.ge.bo.controller;

import com.ge.bo.common.util.ClientIpUtils;
import com.ge.bo.dto.SearchKeywordLogRequest;
import com.ge.bo.entity.SearchKeywordLog;
import com.ge.bo.exception.BusinessException;
import com.ge.bo.service.SearchKeywordLogService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * FO 검색어 로그 / 인기검색어(Popular Keywords) API — 비로그인 전체 허용 (/api/v1/fo/**)
 * - POST /api/v1/fo/search-keywords          : 검색어 적재 (fire-and-forget, 204)
 * - GET  /api/v1/fo/search-keywords/popular  : 최근 30일 인기검색어 상위 5건
 * source(DOWNLOAD_CENTER/UNIFIED_SEARCH)로 다운로드센터 랭킹과 통합검색 랭킹이 분리된다
 */
@RestController
@RequestMapping("/api/v1/fo/search-keywords")
@RequiredArgsConstructor
public class FoSearchKeywordController {

    private final SearchKeywordLogService searchKeywordLogService;

    /**
     * 검색어 저장 — 검색 실행 시 호출 (본문 없이 204 반환)
     * POST /api/v1/fo/search-keywords
     */
    @PostMapping
    public ResponseEntity<Void> log(
            @Valid @RequestBody SearchKeywordLogRequest request,
            @RequestHeader(value = "X-Site-Id", required = false) Long siteId,
            HttpServletRequest httpRequest) {
        searchKeywordLogService.logKeyword(request.source(), request.keyword(), siteId, ClientIpUtils.resolve(httpRequest));
        return ResponseEntity.noContent().build();
    }

    /**
     * 인기검색어 조회 — 최근 30일 집계 상위 5건 (0건이면 빈 배열)
     * GET /api/v1/fo/search-keywords/popular?source=DOWNLOAD_CENTER
     */
    @GetMapping("/popular")
    public ResponseEntity<List<String>> popular(
            @RequestParam String source,
            @RequestHeader(value = "X-Site-Id", required = false) Long siteId) {
        if (!SearchKeywordLog.SOURCE_DOWNLOAD_CENTER.equals(source)
                && !SearchKeywordLog.SOURCE_UNIFIED_SEARCH.equals(source)) {
            throw BusinessException.badRequest("유효하지 않은 검색어 출처입니다.");
        }
        return ResponseEntity.ok(searchKeywordLogService.findPopularKeywords(source, siteId));
    }
}
