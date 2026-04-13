package com.ge.bo.controller;

import com.ge.bo.dto.TsxGenerationListResponse;
import com.ge.bo.dto.TsxGenerationRequest;
import com.ge.bo.dto.TsxGenerationResponse;
import com.ge.bo.service.TsxGenerationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * TSX 생성 이력 API 컨트롤러
 * 기준: /api/v1/tsx-generation
 */
@RestController
@RequestMapping("/api/v1/tsx-generation")
@RequiredArgsConstructor
public class TsxGenerationController {

    private final TsxGenerationService tsxGenerationService;

    /**
     * 생성 이력 저장 — [생성] 버튼 클릭 시 호출
     */
    @PostMapping
    public ResponseEntity<TsxGenerationResponse> save(
            @Valid @RequestBody TsxGenerationRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(tsxGenerationService.save(request));
    }

    /**
     * 이력 목록 조회 — templateType 필터 + 페이지네이션
     *
     * @param templateType LIST / LAYER 필터 (선택)
     * @param page         페이지 번호 (0-based, 기본 0)
     * @param size         페이지 크기 (기본 20)
     */
    @GetMapping
    public ResponseEntity<TsxGenerationListResponse> getList(
            @RequestParam(required = false) String templateType,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(tsxGenerationService.getList(templateType, page, size));
    }

    /**
     * 단건 조회 — 빌더 재편집용 configJson 반환
     */
    @GetMapping("/{id}")
    public ResponseEntity<TsxGenerationResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(tsxGenerationService.getById(id));
    }

    /**
     * 이력 삭제
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        tsxGenerationService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
