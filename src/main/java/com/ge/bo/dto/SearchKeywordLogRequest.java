package com.ge.bo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * FO 검색어 로그 저장 요청 (POST /api/v1/fo/search-keywords)
 * source는 앱 고정값 2종(다운로드센터/통합검색)만 허용한다
 */
public record SearchKeywordLogRequest(

        @NotBlank(message = "검색어 출처를 입력해주세요.")
        @Pattern(regexp = "DOWNLOAD_CENTER|UNIFIED_SEARCH", message = "유효하지 않은 검색어 출처입니다.")
        String source,

        @NotBlank(message = "검색어를 입력해주세요.")
        @Size(max = 255) String keyword) {
}
