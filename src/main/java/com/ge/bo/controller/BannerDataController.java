package com.ge.bo.controller;

/**
 * [SLUG-ENTITY-CODEGEN-AUTO-GENERATED]
 * Controller — 배너
 * 생성일시: 2026-07-12T13:37:46.284663+09:00
 * 원본 Slug Entity: id=1, tableName=banner
 * 주의: 이 파일을 직접 수정한 뒤 다시 생성하면 수정 내용이 사라집니다.
 *       (재생성 시 기존 파일은 자동으로 *.bak.{timestamp} 로 백업됩니다.)
 */
import com.ge.bo.annotation.ApiLinkedEntity;
import com.ge.bo.dto.BannerDataRequest;
import com.ge.bo.dto.BannerDataResponse;
import com.ge.bo.service.BannerDataService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 배너 REST API
 */
@RestController
@RequestMapping("/api/v1/banner-data")
@RequiredArgsConstructor
@ApiLinkedEntity("BannerData")
public class BannerDataController {

  private final BannerDataService bannerDataService;

  /** 목록 조회 (페이징) */
  @GetMapping
  public ResponseEntity<Page<BannerDataResponse>> getList(
      @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC)
      Pageable pageable) {
    return ResponseEntity.ok(bannerDataService.getList(pageable));
  }

  /** 단건 조회 */
  @GetMapping("/{id}")
  public ResponseEntity<BannerDataResponse> getOne(@PathVariable Long id) {
    return ResponseEntity.ok(bannerDataService.getOne(id));
  }

  /** 등록 */
  @PostMapping
  public ResponseEntity<BannerDataResponse> create(
      @Valid @RequestBody BannerDataRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(bannerDataService.create(request));
  }

  /** 수정 */
  @PutMapping("/{id}")
  public ResponseEntity<BannerDataResponse> update(
      @PathVariable Long id, @Valid @RequestBody BannerDataRequest request) {
    return ResponseEntity.ok(bannerDataService.update(id, request));
  }

  /** 삭제 */
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable Long id) {
    bannerDataService.delete(id);
    return ResponseEntity.noContent().build();
  }
}
