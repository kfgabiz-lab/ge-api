package com.ge.bo.controller;

import com.ge.bo.annotation.ApiLinkedEntity;

import com.ge.bo.dto.ApiInfoRequest;
import com.ge.bo.dto.ApiInfoResponse;
import com.ge.bo.dto.ApiInfoSyncResponse;
import com.ge.bo.service.ApiInfoService;
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

import java.util.List;

/**
 * API 정보 관리 REST API
 */
@RestController
@RequestMapping("/api/v1/api-infos")
@RequiredArgsConstructor
@PreAuthorize("@securityService.isSystemAdmin(authentication)")
@ApiLinkedEntity("ApiInfo")
public class ApiInfoController {

  private final ApiInfoService apiInfoService;

    /** 목록 조회 (카테고리/메서드/사용여부/키워드 필터 + 페이징) */
  @GetMapping
    public ResponseEntity<Page<ApiInfoResponse>> getList(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String method,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String keyword,
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
    return ResponseEntity.ok(apiInfoService.getList(category, method, active, keyword, pageable));
  }

    /** 활성 API 정보 전체 목록 (프론트 빌더 셀렉트박스용, 인증 사용자 접근 가능) */
  @GetMapping("/active")
  @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<ApiInfoResponse>> getActiveList() {
    return ResponseEntity.ok(apiInfoService.getActiveList());
  }

    /** 단건 조회 */
  @GetMapping("/{id}")
    public ResponseEntity<ApiInfoResponse> getOne(@PathVariable Long id) {
    return ResponseEntity.ok(apiInfoService.getOne(id));
  }

    /** 등록 */
  @PostMapping
    public ResponseEntity<ApiInfoResponse> create(@Valid @RequestBody ApiInfoRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(apiInfoService.create(request));
  }

    /** 수정 */
  @PutMapping("/{id}")
    public ResponseEntity<ApiInfoResponse> update(@PathVariable Long id, @Valid @RequestBody ApiInfoRequest request) {
    return ResponseEntity.ok(apiInfoService.update(id, request));
  }

    /** 삭제 */
  @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
    apiInfoService.delete(id);
    return ResponseEntity.noContent().build();
  }

    /** Spring MVC 엔드포인트 스캔 후 DB에 없는 것만 추가 */
  @PostMapping("/sync")
    public ResponseEntity<ApiInfoSyncResponse> sync() {
    return ResponseEntity.ok(apiInfoService.sync());
  }

    /** 어노테이션 기반으로 연결 엔티티 자동 매핑 (syncEntities) */
  @PostMapping("/sync-entities")
    public ResponseEntity<Integer> syncEntities() {
    return ResponseEntity.ok(apiInfoService.syncEntities());
  }
}
