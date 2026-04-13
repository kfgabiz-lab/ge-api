package com.ge.bo.dto;

import com.ge.bo.entity.PageFile;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 파일 업로드/조회 응답 DTO
 * 파일 경로(filePath), 저장명(saveName)은 보안상 클라이언트에 노출하지 않음
 */
@Getter
@Builder
public class PageFileResponse {

    private Long id;
    private String templateSlug;
    private Long dataId;
    private String fieldKey;
    private String origName;
    private Long fileSize;
    private String mimeType;
    private LocalDateTime createdAt;

    /** PageFile 엔티티 → 응답 DTO 변환 */
    public static PageFileResponse from(PageFile pageFile) {
        return PageFileResponse.builder()
                .id(pageFile.getId())
                .templateSlug(pageFile.getTemplateSlug())
                .dataId(pageFile.getDataId())
                .fieldKey(pageFile.getFieldKey())
                .origName(pageFile.getOrigName())
                .fileSize(pageFile.getFileSize())
                .mimeType(pageFile.getMimeType())
                .createdAt(pageFile.getCreatedAt())
                .build();
    }
}
