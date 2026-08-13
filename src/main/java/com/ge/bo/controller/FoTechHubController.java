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

@RestController
@RequestMapping("/api/v1/fo/tech-hub")
@RequiredArgsConstructor
public class FoTechHubController {

    private final TechHubService techHubService;

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

    @GetMapping("/contents/{masterId}")
    public ResponseEntity<TechHubDetailResponse> getContentDetail(@PathVariable Long masterId) {
        TechHubDetailResponse detail = techHubService.getContentDetail(masterId);
        if (detail == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(detail);
    }

    @GetMapping("/category-counts")
    public ResponseEntity<List<TechHubCategoryCountResponse>> getCategoryCounts(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String categories,
            @RequestParam(required = false) String certs) {
        List<String> categoryL2Ids = parseCsv(categories);
        List<String> certCodes = parseCsv(certs);
        return ResponseEntity.ok(techHubService.getCategoryCounts(q, categoryL2Ids, certCodes));
    }

    @GetMapping("/cert-counts")
    public ResponseEntity<List<TechHubCertCountResponse>> getCertCounts(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String categories,
            @RequestParam(required = false) String certs) {
        List<String> categoryL2Ids = parseCsv(categories);
        List<String> certCodes = parseCsv(certs);
        return ResponseEntity.ok(techHubService.getCertCounts(q, categoryL2Ids, certCodes));
    }

    private List<String> parseCsv(String csv) {
        if (csv == null || csv.isBlank()) return null;
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
