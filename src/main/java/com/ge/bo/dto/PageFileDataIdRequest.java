package com.ge.bo.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 임시 파일에 data_id 연결 요청 DTO
 * 폼 저장 완료 후 FE가 호출 — fileIds 목록을 dataId와 연결
 */
@Getter
@NoArgsConstructor
public class PageFileDataIdRequest {

    /** 연결할 page_file.id 목록 */
    @NotEmpty(message = "파일 ID 목록은 필수입니다.")
    private List<Long> fileIds;

    /** 연결할 page_data.id */
    @NotNull(message = "dataId는 필수입니다.")
    private Long dataId;
}
