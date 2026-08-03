package com.ge.bo.dto;

public record FoMarketProductRowResponse(
        Long id,
        String type,
        String code,
        String title,
        String categoryTitle,
        String slug,
        String image,
        String awards
) {}
