package com.ge.bo.dto;

import java.util.List;

public record MenuImportResponse(
    int importedCount,
    int skippedCount,
    List<MenuResponse> importedMenus,
    List<Long> skippedMenuIds
) {}
