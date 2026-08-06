package com.ge.bo.dto;

import com.ge.bo.entity.Menu;

/**
 * FO 정적 메뉴 페이지 SEO 메타 응답 DTO — 비로그인 공개 API용
 */
public record FoMenuMetaResponse(String metaTitle, String metaDescription) {

    public static final FoMenuMetaResponse EMPTY = new FoMenuMetaResponse("", "");

    public static FoMenuMetaResponse from(Menu menu) {
        return new FoMenuMetaResponse(
                menu.getMetaTitle() != null ? menu.getMetaTitle() : "",
                menu.getMetaDescription() != null ? menu.getMetaDescription() : ""
        );
    }
}
