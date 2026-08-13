package com.ge.bo.controller;

import com.ge.bo.dto.ValidationRuleRequest;
import com.ge.bo.dto.ValidationRuleResponse;
import com.ge.bo.service.ValidationRuleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 검증 규칙 REST API
 * - 규칙생성 팝업(빌더 패널)에서 slug별 규칙 CRUD
 * - 버튼필드컴포넌트의 다중선택 selectbox 조회에도 동일 목록 API 사용
 */
@RestController
@RequestMapping("/api/v1/validation-rules")
@RequiredArgsConstructor
public class ValidationRuleController {

  private final ValidationRuleService validationRuleService;

    /** slug별 규칙 목록 조회 */
  @GetMapping
    public ResponseEntity<List<ValidationRuleResponse>> getList(
            @RequestParam Long slugRegistryId) {
    return ResponseEntity.ok(validationRuleService.getListBySlugRegistry(slugRegistryId));
  }

    /** 등록 */
  @PostMapping
    public ResponseEntity<ValidationRuleResponse> create(@Valid @RequestBody ValidationRuleRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(validationRuleService.create(request));
  }

    /** 수정 */
  @PutMapping("/{id}")
    public ResponseEntity<ValidationRuleResponse> update(@PathVariable Long id,
            @Valid @RequestBody ValidationRuleRequest request) {
    return ResponseEntity.ok(validationRuleService.update(id, request));
  }

    /** 삭제 */
  @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
    validationRuleService.delete(id);
    return ResponseEntity.noContent().build();
  }
}
