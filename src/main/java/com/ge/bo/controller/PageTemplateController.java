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

@RestController
@RequestMapping("/api/v1/page-templates")
@RequiredArgsConstructor
@ApiLinkedEntity("PageTemplate")
public class PageTemplateController {

  private final PageTemplateService pageTemplateService;

  @GetMapping
  public ResponseEntity<List<PageTemplateResponse>> getAll() {
    return ResponseEntity.ok(pageTemplateService.getAll());
  }

  @GetMapping("/{id}")
  public ResponseEntity<PageTemplateResponse> getById(@PathVariable Long id) {
    return ResponseEntity.ok(pageTemplateService.getById(id));
  }

  @GetMapping("/by-slug/{slug}")
  @PreAuthorize("@securityService.canAccessWidgetMenu(authentication, #slug, #menuId) or @securityService.isSystemAdmin(authentication)")
  public ResponseEntity<PageTemplateResponse> getBySlug(
          @PathVariable("slug") String slug,
          @RequestParam(required = false) String type,
          @RequestHeader(value = "X-Menu-Id", required = false) Long menuId) {
    return ResponseEntity.ok(pageTemplateService.getBySlug(slug, type));
  }

  @PostMapping("/generate")
  @PreAuthorize("@securityService.isSystemAdmin(authentication)")
  public ResponseEntity<Map<String, String>> generate(@Valid @RequestBody PageTemplateGenerateRequest request) {
    String pageUrl = pageTemplateService.generateFile(request.getSlug(), request.getTsxCode(),
                request.getTemplateType(), request.getFileName());
    return ResponseEntity.ok(Map.of("pageUrl", pageUrl));
  }

  @PostMapping
  @PreAuthorize("@securityService.isSystemAdmin(authentication)")
  public ResponseEntity<PageTemplateResponse> create(@Valid @RequestBody PageTemplateRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(pageTemplateService.create(request));
  }

  @PutMapping("/{id}")
  @PreAuthorize("@securityService.isSystemAdmin(authentication)")
  public ResponseEntity<PageTemplateResponse> update(
          @PathVariable Long id,
          @Valid @RequestBody PageTemplateRequest request) {
    return ResponseEntity.ok(pageTemplateService.update(id, request));
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("@securityService.isSystemAdmin(authentication)")
  public ResponseEntity<Void> delete(@PathVariable Long id) {
    pageTemplateService.delete(id);
    return ResponseEntity.noContent().build();
  }
}
