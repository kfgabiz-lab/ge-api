package com.ge.bo.controller;

import com.ge.bo.dto.ProductInsightRowResponse;
import com.ge.bo.service.PageDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/fo")
@RequiredArgsConstructor
public class FoHighlightNewsController {

    private final PageDataService pageDataService;

    @GetMapping("/highlight-news")
    public ResponseEntity<List<ProductInsightRowResponse>> getHighlightNews(
            @RequestParam(name = "market", required = false) String market,
            @RequestHeader(value = "X-Site-Id", required = false) Long siteId) {
        return ResponseEntity.ok(pageDataService.findHighlightNews(market, siteId));
    }
}
