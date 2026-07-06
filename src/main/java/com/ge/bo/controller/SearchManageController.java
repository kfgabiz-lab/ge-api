package com.ge.bo.controller;

import com.ge.bo.annotation.ApiLinkedEntity;
import com.ge.bo.dto.SearchManageRequest;
import com.ge.bo.dto.SearchManageResponse;
import com.ge.bo.dto.SearchManageTextRequest;
import com.ge.bo.service.SearchManageService;
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
 * 검색관리 REST API — 관리자 전용
 */
@RestController
@RequestMapping("/api/v1/search-manage")
@RequiredArgsConstructor
@ApiLinkedEntity("SearchManage")
@PreAuthorize("@securityService.isSystemAdmin(authentication)")
public class SearchManageController {

    private final SearchManageService searchManageService;

    /** 목록 조회 (url 필터 + 페이징) */
    @GetMapping
    public ResponseEntity<Page<SearchManageResponse>> getList(
        @RequestParam(required = false) String url,
        @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(searchManageService.getList(url, pageable));
    }

    /** 단건 조회 (검색텍스트 포함, 최신순) */
    @GetMapping("/{id}")
    public ResponseEntity<SearchManageResponse> getOne(@PathVariable Long id) {
        return ResponseEntity.ok(searchManageService.getOne(id));
    }

    /** 등록 */
    @PostMapping
    public ResponseEntity<SearchManageResponse> create(@Valid @RequestBody SearchManageRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(searchManageService.create(request));
    }

    /** 수정 */
    @PutMapping("/{id}")
    public ResponseEntity<SearchManageResponse> update(
        @PathVariable Long id,
        @Valid @RequestBody SearchManageRequest request) {
        return ResponseEntity.ok(searchManageService.update(id, request));
    }

    /** 삭제 (하위 검색텍스트 CASCADE) */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        searchManageService.delete(id);
        return ResponseEntity.noContent().build();
    }

    /** 검색텍스트 등록 — 최신순 정렬이라 조회 시 자동으로 맨 위에 노출 */
    @PostMapping("/{id}/texts")
    public ResponseEntity<SearchManageResponse> addText(
        @PathVariable Long id,
        @Valid @RequestBody SearchManageTextRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(searchManageService.addText(id, request));
    }

    /** 검색텍스트 삭제 */
    @DeleteMapping("/{id}/texts/{textId}")
    public ResponseEntity<SearchManageResponse> deleteText(
        @PathVariable Long id,
        @PathVariable Long textId) {
        return ResponseEntity.ok(searchManageService.deleteText(id, textId));
    }
}
