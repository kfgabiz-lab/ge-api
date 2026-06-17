package com.ge.bo.controller;

import com.ge.bo.annotation.ApiLinkedEntity;
import com.ge.bo.common.excel.ExcelService;
import com.ge.bo.dto.PageDataListResponse;
import com.ge.bo.dto.PageDataRequest;
import com.ge.bo.dto.PageDataResponse;
import com.ge.bo.service.PageDataService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 페이지 데이터 API 컨트롤러
 * 기준: /api/v1/page-data/{slug}
 * - slug: 페이지 메이커에서 설정한 템플릿 식별자 (ex: user-list)
 */
@RestController
@RequestMapping("/api/v1/page-data/{slug}")
@RequiredArgsConstructor
@ApiLinkedEntity("PageData")
public class PageDataController {

  private final PageDataService pageDataService;
  private final ExcelService excelService;

        /**
         * 목록 조회 — 페이지네이션 + 동적 JSONB 검색
         * Query Params: page(기본 0), size(기본 20), 그 외는 검색 조건
         */
  @GetMapping
        public ResponseEntity<PageDataListResponse> search(
                        @PathVariable String slug,
                        @RequestParam Map<String, String> allParams,
                        @RequestParam(defaultValue = "0") int page,
                        @RequestParam(defaultValue = "20") int size,
                        @RequestHeader(value = "X-Site-Id", required = false) Long siteId) {
    return ResponseEntity.ok(pageDataService.search(slug, allParams, page, size, siteId));
  }

        /** 단건 조회 */
  @GetMapping("/{id}")
        public ResponseEntity<PageDataResponse> getById(
                        @PathVariable String slug,
                        @PathVariable Long id) {
    return ResponseEntity.ok(pageDataService.getById(slug, id));
  }

        /** 등록 */
  @PostMapping
        public ResponseEntity<PageDataResponse> create(
                        @PathVariable String slug,
                        @Valid @RequestBody PageDataRequest request,
                        @RequestHeader(value = "X-Site-Id", required = false) Long siteId) {
    return ResponseEntity.status(HttpStatus.CREATED).body(pageDataService.create(slug, request, siteId));
  }

        /** 수정 */
  @PutMapping("/{id}")
        public ResponseEntity<PageDataResponse> update(
                        @PathVariable String slug,
                        @PathVariable Long id,
                        @Valid @RequestBody PageDataRequest request) {
    return ResponseEntity.ok(pageDataService.update(slug, id, request));
  }

        /** 삭제 — id 지정 */
  @DeleteMapping("/{id}")
        public ResponseEntity<Void> delete(
                        @PathVariable String slug,
                        @PathVariable Long id) {
    pageDataService.delete(slug, id);
    return ResponseEntity.noContent().build();
  }

        /**
         * PK 기반 삭제 — id 없이 pkKeys + dataJson 값으로 레코드를 찾아 삭제
         * Form 위젯에서 삭제 버튼 클릭 시 사용 (id를 모르는 경우)
         */
  @DeleteMapping
        public ResponseEntity<Void> deleteByPk(
                        @PathVariable String slug,
                        @RequestBody PageDataRequest request) {
    pageDataService.deleteByPk(slug, request.getPkKeys(), request.getDataJson());
    return ResponseEntity.noContent().build();
  }

        /**
         * group_id + slug 조합 단건 조회 — 다중 slug 수정 모드에서 각 slug 데이터 로드
         * GET /api/v1/page-data/{slug}/group/{groupId}
         */
  @GetMapping("/group/{groupId}")
        public ResponseEntity<PageDataResponse> findByGroupId(
                        @PathVariable String slug,
                        @PathVariable String groupId) {
    return ResponseEntity.ok(pageDataService.findByGroupIdAndSlug(groupId, slug));
  }

        /**
         * group_id 기반 일괄 삭제 — 다중 slug 저장 그룹 전체 삭제
         * DELETE /api/v1/page-data/{slug}/group/{groupId}
         */
  @DeleteMapping("/group/{groupId}")
        public ResponseEntity<Void> deleteByGroupId(
                        @PathVariable String slug,
                        @PathVariable String groupId) {
    pageDataService.deleteByGroupId(groupId);
    return ResponseEntity.noContent().build();
  }

        /**
         * 전체 데이터 엑셀/CSV 다운로드
         * GET
         * /api/v1/page-data/{slug}/export?format=xlsx&headers=이름,이메일&keys=name,email&검색조건...
         *
         * @param format  파일 형식 (xlsx | csv, 기본값: xlsx)
         * @param headers 컬럼 헤더 목록 (쉼표 구분, 예: "이름,이메일,상태")
         * @param keys    data_json 키 목록 (헤더와 순서 일치, 예: "name,email,status")
         */
  @GetMapping("/export")
        public ResponseEntity<byte[]> export(
                        @PathVariable String slug,
                        @RequestParam(defaultValue = "xlsx") String format,
                        @RequestParam(required = false) String headers,
                        @RequestParam(required = false) String keys,
                        @RequestParam Map<String, String> allParams) {
                // 헤더/키 파싱 (미전달 시 빈 목록)
                // split(",", -1) — limit=-1로 trailing 빈 문자열도 보존 (기본 split은 trailing 제거)
    List<String> headerList = (headers != null && !headers.isBlank())
                                ? Arrays.asList(headers.split(",", -1))
                                : Collections.emptyList();
    List<String> keyList = (keys != null && !keys.isBlank())
                                ? Arrays.asList(keys.split(",", -1))
                                : Collections.emptyList();

                // 전체 데이터 조회
    List<Map<String, Object>> rows = pageDataService.exportAll(slug, allParams);

                // 파일명: {slug}_{yyyyMMdd}.xlsx or .csv
    String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
    boolean isCsv = "csv".equalsIgnoreCase(format);
    String fileName = slug + "_" + today + (isCsv ? ".csv" : ".xlsx");

                // 엑셀/CSV 바이트 생성
    byte[] fileBytes = isCsv
                                ? excelService.buildCsv(headerList, keyList, rows)
                                : excelService.buildXlsx(headerList, keyList, rows, slug);

                // 응답 헤더 설정
    HttpHeaders responseHeaders = new HttpHeaders();
    responseHeaders.setContentType(MediaType.APPLICATION_OCTET_STREAM);
    responseHeaders.setContentDisposition(
                                ContentDisposition.attachment()
                                                .filename(fileName, StandardCharsets.UTF_8)
                                                .build());

    return ResponseEntity.ok()
                                .headers(responseHeaders)
                                .body(fileBytes);
  }
}
