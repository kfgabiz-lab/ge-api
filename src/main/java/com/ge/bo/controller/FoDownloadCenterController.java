package com.ge.bo.controller;

import com.ge.bo.dto.DownloadCenterCategoryCountResponse;
import com.ge.bo.dto.DownloadCenterDocTypeCountResponse;
import com.ge.bo.dto.DownloadCenterContentPageResponse;
import com.ge.bo.dto.FoDocumentSearchResponse;
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
     * GET /api/v1/fo/download-center/contents?q={키워드}&categories={LV2코드,콤마}&docTypes={유형코드,콤마}&productCodes={LV3제품코드,콤마}&sort=newest&page=0&size=12
     * - categories: LV2 카테고리 코드 콤마구분(그룹 내 OR). 미지정 시 전체.
     * - parentCategories: LV1 카테고리 코드(예: L01) 콤마구분. 해당 LV1 에 속하지만 LV2 가 지정되지 않은
     *   (category_l2_id IS NULL) 콘텐츠를 categories(LV2) 조건과 OR 로 함께 포함시킨다. 미지정 시 미적용.
     * - docTypes: 문서유형 코드 콤마구분(IN). 미지정 시 전체.
     * - productCodes: LV3 제품코드(contents_category.category_l3_id, 예: L01-02-01) 콤마구분(IN). 미지정 시 전체.
     *   제품상세(/product/[slug]) Downloads 섹션에서 "현재 제품과 연계된 파일만" 거를 때 사용.
     *   한 제품 slug 가 복수 product_code 를 가질 수 있어 다중값을 받는다. categories 와는 AND 결합.
     * - sort: doctype(문서유형 우선순위 → 동일 유형 내 등록일시 내림차순)/newest(기본)/oldest/title/title_desc.
     *   페이지 크기 기본 12.
     */
    @GetMapping("/contents")
    public ResponseEntity<DownloadCenterContentPageResponse> getContents(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String categories,
            @RequestParam(required = false) String parentCategories,
            @RequestParam(required = false) String docTypes,
            @RequestParam(required = false) String productCodes,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {
        List<String> categoryL2Ids = parseCsv(categories);
        List<String> categoryL1Ids = parseCsv(parentCategories);
        List<String> docTypeList = parseCsv(docTypes);
        List<String> productCodeList = parseCsv(productCodes);
        return ResponseEntity.ok(
            downloadCenterService.getContents(q, categoryL2Ids, categoryL1Ids, docTypeList, productCodeList, sort, page, size));
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
     * GET /api/v1/fo/download-center/doctype-counts?productCodes={LV3제품코드,콤마}
     * - 6개 문서유형(C/M/D/S/R/O) 전체를 항상 반환(매칭 없으면 count=0).
     * - OS/Firmware 는 대응 doc_type 이 없어 미포함(FE 정적 표시).
     * - productCodes: LV3 제품코드(contents_category.category_l3_id, 예: L02-01-01) 콤마구분.
     *   미지정 시 Download Center 전체 기준(기존 동작). 지정 시 해당 제품에 연계된 콘텐츠만 유형별 집계
     *   → 제품상세 Downloads 섹션의 Document Type 필터 유형별 검색결과 수.
     */
    @GetMapping("/doctype-counts")
    public ResponseEntity<List<DownloadCenterDocTypeCountResponse>> getDocTypeCounts(
            @RequestParam(required = false) String productCodes) {
        List<String> productCodeList = parseCsv(productCodes);
        return ResponseEntity.ok(downloadCenterService.getDocTypeCounts(productCodeList));
    }

    /**
     * FO 통합검색용 문서 키워드 검색
     * GET /api/v1/fo/download-center/search?q={키워드}&limit={기본10}
     * - q: 제목(nahp_title 우선, 없으면 doc_title) 부분일치. null/blank 이면 {total:0, items:[]} 즉시 반환(전체 덤프 금지).
     * - limit: 반환 상위 건수(기본 10). total 은 limit 무관 전체 매칭 건수.
     * - 응답: { total, items[](기존 Download Center 카드 구조 = 버전/파일 중첩 포함) }.
     */
    @GetMapping("/search")
    public ResponseEntity<FoDocumentSearchResponse> searchDocuments(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(downloadCenterService.searchDocuments(q, limit));
    }

    /**
     * FO 통합검색 AI챗봇 keyword 기반 문서 전체 목록
     * GET /api/v1/fo/download-center/keyword-contents?keyword={챗봇 keyword,연관검색어 콤마구분}
     * - Azure AI Search 후보(file_name 매칭) + contents_master 제목(주 키워드) 직접매칭을 합친 전체 결과를
     *   페이징/필터 없이 한 번에 반환한다. All탭(4건)/Documents탭(10건씩)의 슬라이싱·카테고리/문서유형 필터는
     *   FE가 이 전체 리스트를 받아 클라이언트에서 처리한다(재호출 없음).
     */
    @GetMapping("/keyword-contents")
    public ResponseEntity<FoDocumentSearchResponse> getContentsByKeyword(
            @RequestParam(required = false) String keyword) {
        return ResponseEntity.ok(downloadCenterService.getContentsByKeyword(keyword));
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
