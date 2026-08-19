package com.ge.bo.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ge.bo.annotation.ApiLinkedEntity;
import com.ge.bo.common.excel.ExcelService;
import com.ge.bo.common.util.ClientIpUtils;
import com.ge.bo.dto.FieldPatchRequest;
import com.ge.bo.dto.PageDataListResponse;
import com.ge.bo.dto.PageDataRequest;
import com.ge.bo.dto.PageDataResponse;
import com.ge.bo.repository.AdminRepository;
import com.ge.bo.service.DownloadLogService;
import com.ge.bo.service.PageDataService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/page-data/{slug}")
@RequiredArgsConstructor
@Slf4j
@ApiLinkedEntity("PageData")
public class PageDataController {

  private final PageDataService pageDataService;
  private final ExcelService excelService;
  private final DownloadLogService downloadLogService;
  private final AdminRepository adminRepository;
  private final ObjectMapper objectMapper;

  @GetMapping
        @PreAuthorize("@securityService.canAccessWidgetMenu(authentication, #slug, #menuId)"
                        + " or @securityService.isSystemAdmin(authentication)")
        public ResponseEntity<PageDataListResponse> search(
                        @PathVariable("slug") String slug,
                        @RequestParam Map<String, String> allParams,
                        @RequestParam(defaultValue = "0") int page,
                        @RequestParam(defaultValue = "20") int size,
                        @RequestHeader(value = "X-Site-Id", required = false) Long siteId,
                        @RequestHeader(value = "X-Menu-Id", required = false) Long menuId) {
    return ResponseEntity.ok(pageDataService.search(slug, allParams, page, size, siteId));
  }

  @GetMapping("/{id}")
        @PreAuthorize("@securityService.canAccessWidgetMenu(authentication, #slug, #menuId)"
                        + " or @securityService.isSystemAdmin(authentication)")
        public ResponseEntity<PageDataResponse> getById(
                        @PathVariable("slug") String slug,
                        @PathVariable Long id,
                        @RequestHeader(value = "X-Menu-Id", required = false) Long menuId) {
    return ResponseEntity.ok(pageDataService.getById(slug, id));
  }

  @PostMapping
        @PreAuthorize("@securityService.canAccessWidgetMenu(authentication, #slug, #menuId)"
                        + " or @securityService.isSystemAdmin(authentication)")
        public ResponseEntity<PageDataResponse> create(
                        @PathVariable("slug") String slug,
                        @Valid @RequestBody PageDataRequest request,
                        @RequestHeader(value = "X-Site-Id", required = false) Long siteId,
                        @RequestHeader(value = "X-Menu-Id", required = false) Long menuId) {
    return ResponseEntity.status(HttpStatus.CREATED).body(pageDataService.create(slug, request, siteId));
  }

  @PutMapping("/{id}")
        @PreAuthorize("@securityService.canAccessWidgetMenu(authentication, #slug, #menuId)"
                        + " or @securityService.isSystemAdmin(authentication)")
        public ResponseEntity<PageDataResponse> update(
                        @PathVariable("slug") String slug,
                        @PathVariable Long id,
                        @Valid @RequestBody PageDataRequest request,
                        @RequestHeader(value = "X-Site-Id", required = false) Long siteId,
                        @RequestHeader(value = "X-Menu-Id", required = false) Long menuId) {
    return ResponseEntity.ok(pageDataService.update(slug, id, request, siteId));
  }

  @PatchMapping("/{id}/field")
        @PreAuthorize("@securityService.canAccessWidgetMenu(authentication, #slug, #menuId)"
                        + " or @securityService.isSystemAdmin(authentication)")
        public ResponseEntity<PageDataResponse> patchField(
                        @PathVariable("slug") String slug,
                        @PathVariable Long id,
                        @Valid @RequestBody FieldPatchRequest request,
                        @RequestHeader(value = "X-Menu-Id", required = false) Long menuId) {
    return ResponseEntity.ok(pageDataService.patchField(slug, id, request.getFieldKey(), request.getValue()));
  }

  @DeleteMapping("/{id}")
        @PreAuthorize("@securityService.canAccessWidgetMenu(authentication, #slug, #menuId)"
                        + " or @securityService.isSystemAdmin(authentication)")
        public ResponseEntity<Void> delete(
                        @PathVariable("slug") String slug,
                        @PathVariable Long id,
                        @RequestHeader(value = "X-Menu-Id", required = false) Long menuId) {
    pageDataService.delete(slug, id);
    return ResponseEntity.noContent().build();
  }

  @DeleteMapping
        @PreAuthorize("@securityService.canAccessWidgetMenu(authentication, #slug, #menuId)"
                        + " or @securityService.isSystemAdmin(authentication)")
        public ResponseEntity<Void> deleteByPk(
                        @PathVariable("slug") String slug,
                        @RequestBody PageDataRequest request,
                        @RequestHeader(value = "X-Menu-Id", required = false) Long menuId) {
    pageDataService.deleteByPk(slug, request.getPkKeys(), request.getDataJson());
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/group/{groupId}")
        @PreAuthorize("@securityService.canAccessWidgetMenu(authentication, #slug, #menuId)"
                        + " or @securityService.isSystemAdmin(authentication)")
        public ResponseEntity<PageDataResponse> findByGroupId(
                        @PathVariable("slug") String slug,
                        @PathVariable String groupId,
                        @RequestHeader(value = "X-Menu-Id", required = false) Long menuId) {
    return ResponseEntity.ok(pageDataService.findByGroupIdAndSlug(groupId, slug));
  }

  @DeleteMapping("/group/{groupId}")
        @PreAuthorize("@securityService.canAccessWidgetMenu(authentication, #slug, #menuId)"
                        + " or @securityService.isSystemAdmin(authentication)")
        public ResponseEntity<Void> deleteByGroupId(
                        @PathVariable("slug") String slug,
                        @PathVariable String groupId,
                        @RequestHeader(value = "X-Menu-Id", required = false) Long menuId) {
    pageDataService.deleteByGroupId(groupId);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/export")
        @PreAuthorize("@securityService.canAccessWidgetMenu(authentication, #slug, #menuId)"
                        + " or @securityService.isSystemAdmin(authentication)")
        public ResponseEntity<byte[]> export(
                        @PathVariable("slug") String slug,
                        @RequestParam(defaultValue = "xlsx") String format,
                        @RequestParam(required = false) String headers,
                        @RequestParam(required = false) String keys,
                        @RequestParam(required = false) String dateFormats,
                        @RequestParam(required = false) String codeMaps,
                        @RequestParam(required = false) String reason,
                        @RequestParam(required = false) String relationIds,
                        @RequestParam Map<String, String> allParams,
                        @RequestHeader(value = "X-Site-Id", required = false) Long siteId,
                        @RequestHeader(value = "X-Menu-Id", required = false) Long menuId,
                        HttpServletRequest request) {
    List<String> headerList = (headers != null && !headers.isBlank())
                                ? Arrays.asList(headers.split(",", -1))
                                : Collections.emptyList();
    List<String> keyList = (keys != null && !keys.isBlank())
                                ? Arrays.asList(keys.split(",", -1))
                                : Collections.emptyList();
    List<String> dateFormatList = (dateFormats != null && !dateFormats.isBlank())
                                ? Arrays.asList(dateFormats.split(",", -1))
                                : Collections.emptyList();

    List<Long> relationIdList = (relationIds != null && !relationIds.isBlank())
                                ? Arrays.stream(relationIds.split(",", -1))
                                                .filter(s -> !s.isBlank())
                                                .map(Long::parseLong)
                                                .toList()
                                : Collections.emptyList();

    Map<String, Map<String, String>> codeMapData = Collections.emptyMap();
    if (codeMaps != null && !codeMaps.isBlank()) {
      try {
        codeMapData = objectMapper.readValue(codeMaps, new TypeReference<Map<String, Map<String, String>>>() {});
      } catch (Exception e) {
        log.warn("export codeMaps 파싱 실패, 매핑 없이 진행: {}", e.getMessage());
      }
    }

    List<Map<String, Object>> rows = pageDataService.exportAll(slug, allParams, siteId, relationIdList);

    String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
    boolean isCsv = "csv".equalsIgnoreCase(format);
    String fileName = slug + "_" + today + (isCsv ? ".csv" : ".xlsx");

    byte[] fileBytes = isCsv
                                ? excelService.buildCsv(headerList, keyList, dateFormatList, codeMapData, rows)
                                : excelService.buildXlsx(headerList, keyList, dateFormatList, codeMapData, rows, slug);

    HttpHeaders responseHeaders = new HttpHeaders();
    responseHeaders.setContentType(MediaType.APPLICATION_OCTET_STREAM);
    responseHeaders.setContentDisposition(
                                ContentDisposition.attachment()
                                                .filename(fileName, StandardCharsets.UTF_8)
                                                .build());

    if (reason != null && !reason.isBlank()) {
        String employeeId = SecurityContextHolder.getContext().getAuthentication().getName();
        String createdBy = adminRepository.findByEmployeeId(employeeId)
                .map(u -> String.valueOf(u.getId()))
                .orElse(null);
        downloadLogService.saveAsync(slug, reason, isCsv ? "csv" : "xlsx", createdBy, ClientIpUtils.resolve(request));
    }

    return ResponseEntity.ok()
                                .headers(responseHeaders)
                                .body(fileBytes);
  }
}
