package com.ge.bo.dto;

import com.ge.bo.entity.TsxGeneration;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * TSX 생성 이력 응답 DTO (목록/단건 공통)
 */
@Getter
@Builder
public class TsxGenerationResponse {

    private Long id;
    private String name;
    private String folderName;
    private String fileName;
    private String templateType;
    /** 빌더 재편집용 configJson */
    private String configJson;
    /** 생성된 TSX 코드 전문 */
    private String tsxCode;
    private String createdBy;
    private LocalDateTime createdAt;

    /** TsxGeneration 엔티티 → 응답 DTO 변환 */
    public static TsxGenerationResponse from(TsxGeneration entity) {
        return TsxGenerationResponse.builder()
                .id(entity.getId())
                .name(entity.getName())
                .folderName(entity.getFolderName())
                .fileName(entity.getFileName())
                .templateType(entity.getTemplateType())
                .configJson(entity.getConfigJson())
                .tsxCode(entity.getTsxCode())
                .createdBy(entity.getCreatedBy())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
