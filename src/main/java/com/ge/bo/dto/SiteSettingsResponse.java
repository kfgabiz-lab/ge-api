package com.ge.bo.dto;

/**
 * FO 공개용 사이트 설정 응답 — timezone/locale만 노출(관리자 전용 필드는 제외).
 */
public record SiteSettingsResponse(String timezone, String locale) {
}
