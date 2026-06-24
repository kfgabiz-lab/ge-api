package com.ge.bo.controller;

import com.ge.bo.dto.SlugRelationRequest;
import com.ge.bo.dto.SlugRelationResponse;
import com.ge.bo.service.SlugRelationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 슬러그 관계 매핑 REST API
 */
@RestController
@RequestMapping("/api/v1/slug-relations")
@RequiredArgsConstructor
@PreAuthorize("@securityService.isSystemAdmin(authentication)")
public class SlugRelationController {

    private final SlugRelationService slugRelationService;

    /** 목록 조회 (masterSlug/slaveSlug/relationDir 필터 + 페이징) */
    @GetMapping
    public ResponseEntity<Page<SlugRelationResponse>> getList(
            @RequestParam(required = false) String masterSlug,
            @RequestParam(required = false) String slaveSlug,
            @RequestParam(required = false) String relationDir,
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(slugRelationService.getList(masterSlug, slaveSlug, relationDir, pageable));
    }

    /** 단건 조회 */
    @GetMapping("/{id}")
    public ResponseEntity<SlugRelationResponse> getOne(@PathVariable Long id) {
        return ResponseEntity.ok(slugRelationService.getOne(id));
    }

    /** 등록 */
    @PostMapping
    public ResponseEntity<SlugRelationResponse> create(@Valid @RequestBody SlugRelationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(slugRelationService.create(request));
    }

    /** 수정 */
    @PutMapping("/{id}")
    public ResponseEntity<SlugRelationResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody SlugRelationRequest request) {
        return ResponseEntity.ok(slugRelationService.update(id, request));
    }

    /** 삭제 */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        slugRelationService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
