package com.ge.bo.controller;

import com.ge.bo.annotation.ApiLinkedEntity;
import com.ge.bo.dto.PageTemplateGenerateRequest;
import com.ge.bo.dto.PageTemplateRequest;
import com.ge.bo.dto.PageTemplateResponse;
import com.ge.bo.service.PageTemplateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

import java.util.List;

/**
 * 페이지 템플릿 API 컨트롤러
 * 기준: /api/v1/page-templates
 */
@RestController
@RequestMapping("/api/v1/page-templates")
@RequiredArgsConstructor
@ApiLinkedEntity("PageTemplate")
public class PageTemplateController {

  private final PageTemplateService pageTemplateService;

    /** 목록 조회 (이름 오름차순) */
  @GetMapping
    public ResponseEntity<List<PageTemplateResponse>> getAll() {
    return ResponseEntity.ok(pageTemplateService.getAll());
  }

    /** 단건 조회 (id) */
  @GetMapping("/{id}")
    public ResponseEntity<PageTemplateResponse> getById(@PathVariable Long id) {
    return ResponseEntity.ok(pageTemplateService.getById(id));
  }

    /** 단건 조회 (slug) — type 파라미터 미전달 시 slug만으로 조회 */
  @GetMapping("/by-slug/{slug}")
    public ResponseEntity<PageTemplateResponse> getBySlug(
            @PathVariable String slug,
            @RequestParam(required = false) String type) {
    return ResponseEntity.ok(pageTemplateService.getBySlug(slug, type));
  }

    /** 파일만 생성 (DB 저장 없음) — 방식 B (생성 방식) */
  @PostMapping("/generate")
    @PreAuthorize("@securityService.isSystemAdmin(authentication)")
    public ResponseEntity<Map<String, String>> generate(@Valid @RequestBody PageTemplateGenerateRequest request) {
    String pageUrl = pageTemplateService.generateFile(request.getSlug(), request.getTsxCode(),
                request.getTemplateType(), request.getFileName());
    return ResponseEntity.ok(Map.of("pageUrl", pageUrl));
  }

    /** 생성 (DB + TSX 파일) */
  @PostMapping
    @PreAuthorize("@securityService.isSystemAdmin(authentication)")
    public ResponseEntity<PageTemplateResponse> create(@Valid @RequestBody PageTemplateRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(pageTemplateService.create(request));
  }

    /** 수정 (DB + TSX 파일 덮어쓰기) */
  @PutMapping("/{id}")
    @PreAuthorize("@securityService.isSystemAdmin(authentication)")
    public ResponseEntity<PageTemplateResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody PageTemplateRequest request) {
    return ResponseEntity.ok(pageTemplateService.update(id, request));
  }

    /** 삭제 (DB + TSX 파일 삭제) */
  @DeleteMapping("/{id}")
    @PreAuthorize("@securityService.isSystemAdmin(authentication)")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
    pageTemplateService.delete(id);
    return ResponseEntity.noContent().build();
  }
}
