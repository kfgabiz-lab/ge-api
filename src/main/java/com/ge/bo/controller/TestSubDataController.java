package com.ge.bo.controller;

/**
 * [SLUG-ENTITY-CODEGEN-AUTO-GENERATED]
 * Controller — 테스트서브데이터
 * 생성일시: 2026-07-12T20:46:55.027515800+09:00
 * 원본 Slug Entity: id=19, tableName=test_products
 * 주의: 이 파일을 직접 수정한 뒤 다시 생성하면 수정 내용이 사라집니다.
 *       (재생성 시 기존 파일은 자동으로 *.bak.{timestamp} 로 백업됩니다.)
 */
import com.ge.bo.annotation.ApiLinkedEntity;
import com.ge.bo.dto.TestSubDataRequest;
import com.ge.bo.dto.TestSubDataResponse;
import com.ge.bo.service.TestSubDataService;
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
 * 테스트서브데이터 REST API
 */
@RestController
@RequestMapping("/api/v1/test_sub_data")
@RequiredArgsConstructor
@PreAuthorize("@securityService.isSystemAdmin(authentication)")
@ApiLinkedEntity("TestSubData")
public class TestSubDataController {

  private final TestSubDataService testSubDataService;

  /** 목록 조회 (페이징) */
  @GetMapping
  public ResponseEntity<Page<TestSubDataResponse>> getList(
      @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC)
      Pageable pageable) {
    return ResponseEntity.ok(testSubDataService.getList(pageable));
  }

  /** 단건 조회 */
  @GetMapping("/{id}")
  public ResponseEntity<TestSubDataResponse> getOne(@PathVariable Long id) {
    return ResponseEntity.ok(testSubDataService.getOne(id));
  }

  /** 등록 */
  @PostMapping
  public ResponseEntity<TestSubDataResponse> create(
      @Valid @RequestBody TestSubDataRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(testSubDataService.create(request));
  }

  /** 수정 */
  @PutMapping("/{id}")
  public ResponseEntity<TestSubDataResponse> update(
      @PathVariable Long id, @Valid @RequestBody TestSubDataRequest request) {
    return ResponseEntity.ok(testSubDataService.update(id, request));
  }

  /** 삭제 */
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable Long id) {
    testSubDataService.delete(id);
    return ResponseEntity.noContent().build();
  }
}
