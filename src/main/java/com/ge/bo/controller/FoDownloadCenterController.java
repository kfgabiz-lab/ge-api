package com.ge.bo.controller;

import com.ge.bo.dto.DownloadCenterCategoryCountResponse;
import com.ge.bo.dto.DownloadCenterDocTypeCountResponse;
import com.ge.bo.dto.DownloadCenterContentPageResponse;
import com.ge.bo.service.DownloadCenterService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

/**
 * FO Download Center 전용 API — 비로그인 전체 허용 (/api/v1/fo/**)
 * - 데이터 소스: contents_master/version/file/category(SSQ 배치 적재 문서 콘텐츠). page_data slug 아님.
 * - 목록(카드) 조회 + 카테고리별 건수. 파일 다운로드는 기존 CtpFileDownloadController(/api/v1/fo/ctpApi/fileDownUrl) 재사용.
 */
@RestController
@RequestMapping("/api/v1/fo/download-center")
@RequiredArgsConstructor
public class FoDownloadCenterController {

    private final DownloadCenterService downloadCenterService;

    /**
     * Download Center 목록(카드) 조회
     * GET /api/v1/fo/download-center/contents?q={키워드}&categories={LV2코드,콤마}&docTypes={유형코드,콤마}&sort=newest&page=0&size=12
     * - categories: LV2 카테고리 코드 콤마구분(그룹 내 OR). 미지정 시 전체.
     * - docTypes: 문서유형 코드 콤마구분(IN). 미지정 시 전체.
     * - sort: newest(기본)/oldest/title. 페이지 크기 기본 12.
     */
    @GetMapping("/contents")
    public ResponseEntity<DownloadCenterContentPageResponse> getContents(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String categories,
            @RequestParam(required = false) String docTypes,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {
        List<String> categoryL2Ids = parseCsv(categories);
        List<String> docTypeList = parseCsv(docTypes);
        return ResponseEntity.ok(
            downloadCenterService.getContents(q, categoryL2Ids, docTypeList, sort, page, size));
    }

    /**
     * LV2 카테고리별 콘텐츠 건수(필터 아코디언 숫자)
     * GET /api/v1/fo/download-center/category-counts
     */
    @GetMapping("/category-counts")
    public ResponseEntity<List<DownloadCenterCategoryCountResponse>> getCategoryCounts() {
        return ResponseEntity.ok(downloadCenterService.getCategoryCounts());
    }

    /**
     * 문서유형(docType)별 콘텐츠 건수(필터 패널 문서유형 옆 숫자)
     * GET /api/v1/fo/download-center/doctype-counts
     * - 6개 문서유형(C/M/D/S/R/O) 전체를 항상 반환(매칭 없으면 count=0).
     * - OS/Firmware 는 대응 doc_type 이 없어 미포함(FE 정적 표시).
     */
    @GetMapping("/doctype-counts")
    public ResponseEntity<List<DownloadCenterDocTypeCountResponse>> getDocTypeCounts() {
        return ResponseEntity.ok(downloadCenterService.getDocTypeCounts());
    }

    /** "C,M,R" → ["C","M","R"] (공백/빈값 제거). null/blank → null(필터 미적용). */
    private List<String> parseCsv(String csv) {
        if (csv == null || csv.isBlank()) return null;
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
