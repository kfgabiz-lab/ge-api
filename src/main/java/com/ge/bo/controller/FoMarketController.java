package com.ge.bo.controller;

import com.ge.bo.dto.FoMarketProductRowResponse;
import com.ge.bo.service.FoMarketService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/fo/markets")
@RequiredArgsConstructor
public class FoMarketController {

    private final FoMarketService foMarketService;

    @GetMapping("/{name}/products")
    public ResponseEntity<List<FoMarketProductRowResponse>> getMarketProducts(
            @PathVariable String name,
            @RequestHeader(value = "X-Site-Id", required = false) Long siteId) {
        return ResponseEntity.ok(foMarketService.findMarketProducts(name, siteId));
    }
}
