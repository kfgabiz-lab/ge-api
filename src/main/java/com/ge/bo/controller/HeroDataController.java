package com.ge.bo.controller;

/**
 * [SLUG-ENTITY-CODEGEN-AUTO-GENERATED]
 * Controller — 히어로
 * 생성일시: 2026-07-11T14:24:12.514194400+09:00
 * 원본 Slug Entity: id=5, tableName=hero_banner
 * 주의: 이 파일을 직접 수정한 뒤 다시 생성하면 수정 내용이 사라집니다.
 *       (재생성 시 기존 파일은 자동으로 *.bak.{timestamp} 로 백업됩니다.)
 */
import com.ge.bo.annotation.ApiLinkedEntity;
import com.ge.bo.dto.HeroDataRequest;
import com.ge.bo.dto.HeroDataResponse;
import com.ge.bo.service.HeroDataService;
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
 * 히어로 REST API
 */
@RestController
@RequestMapping("/api/v1/hero-data")
@RequiredArgsConstructor
@ApiLinkedEntity("HeroData")
public class HeroDataController {

  private final HeroDataService heroDataService;

  /** 목록 조회 (페이징) */
  @GetMapping
  public ResponseEntity<Page<HeroDataResponse>> getList(
      @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC)
      Pageable pageable) {
    return ResponseEntity.ok(heroDataService.getList(pageable));
  }

  /** 단건 조회 */
  @GetMapping("/{id}")
  public ResponseEntity<HeroDataResponse> getOne(@PathVariable Long id) {
    return ResponseEntity.ok(heroDataService.getOne(id));
  }

  /** 등록 */
  @PostMapping
  public ResponseEntity<HeroDataResponse> create(
      @Valid @RequestBody HeroDataRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(heroDataService.create(request));
  }

  /** 수정 */
  @PutMapping("/{id}")
  public ResponseEntity<HeroDataResponse> update(
      @PathVariable Long id, @Valid @RequestBody HeroDataRequest request) {
    return ResponseEntity.ok(heroDataService.update(id, request));
  }

  /** 삭제 */
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable Long id) {
    heroDataService.delete(id);
    return ResponseEntity.noContent().build();
  }
}
