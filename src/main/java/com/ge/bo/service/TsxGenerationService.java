package com.ge.bo.service;

import com.ge.bo.dto.TsxGenerationListResponse;
import com.ge.bo.dto.TsxGenerationRequest;
import com.ge.bo.dto.TsxGenerationResponse;
import com.ge.bo.entity.TsxGeneration;
import com.ge.bo.exception.ErrorCode;
import com.ge.bo.repository.TsxGenerationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * TSX 생성 이력 비즈니스 로직
 * - 저장 / 목록 조회 / 단건 조회 / 삭제
 */
@Service
@RequiredArgsConstructor
public class TsxGenerationService {

    private final TsxGenerationRepository tsxGenerationRepository;

    /**
     * 생성 이력 저장
     * [생성] 버튼 클릭 시 TSX 파일 생성과 동시에 호출
     */
    @Transactional
    public TsxGenerationResponse save(TsxGenerationRequest request) {
        TsxGeneration entity = TsxGeneration.builder()
                .name(request.getName())
                .folderName(request.getFolderName())
                .fileName(request.getFileName())
                .templateType(request.getTemplateType())
                .configJson(request.getConfigJson())
                .tsxCode(request.getTsxCode())
                .build();

        return TsxGenerationResponse.from(tsxGenerationRepository.save(entity));
    }

    /**
     * 이력 목록 조회 (created_at DESC 정렬 + 페이지네이션)
     *
     * @param templateType 템플릿 유형 필터 (null이면 전체 조회)
     * @param page         페이지 번호 (0-based)
     * @param size         페이지 크기
     */
    @Transactional(readOnly = true)
    public TsxGenerationListResponse getList(String templateType, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<TsxGeneration> result = (templateType != null && !templateType.isBlank())
                ? tsxGenerationRepository.findAllByTemplateType(templateType, pageable)
                : tsxGenerationRepository.findAll(pageable);

        List<TsxGenerationResponse> content = result.getContent()
                .stream()
                .map(TsxGenerationResponse::from)
                .toList();

        return TsxGenerationListResponse.builder()
                .content(content)
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .page(page)
                .size(size)
                .build();
    }

    /**
     * 단건 조회 — 빌더 재편집용 configJson 반환
     *
     * @param id 이력 PK
     */
    @Transactional(readOnly = true)
    public TsxGenerationResponse getById(Long id) {
        TsxGeneration entity = tsxGenerationRepository.findById(id)
                .orElseThrow(ErrorCode.TSX_GENERATION_NOT_FOUND::toException);
        return TsxGenerationResponse.from(entity);
    }

    /**
     * 이력 삭제
     *
     * @param id 이력 PK
     */
    @Transactional
    public void delete(Long id) {
        TsxGeneration entity = tsxGenerationRepository.findById(id)
                .orElseThrow(ErrorCode.TSX_GENERATION_NOT_FOUND::toException);
        tsxGenerationRepository.delete(entity);
    }
}
