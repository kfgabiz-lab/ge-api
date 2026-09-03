package com.ge.bo.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record MenuImportRequest(

    @NotNull(message = "원본 사이트를 선택해주세요.")
    Long sourceSiteId,

    @NotEmpty(message = "가져올 메뉴를 선택해주세요.")
    List<Long> menuIds
) {}
