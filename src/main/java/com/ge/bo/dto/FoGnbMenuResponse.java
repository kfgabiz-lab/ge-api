package com.ge.bo.dto;

import com.ge.bo.entity.Menu;

import java.util.List;
import java.util.Map;

/**
 * FO GNB 메뉴 응답 DTO — 비로그인 공개 API용 경량 응답
 * 사용법: FoGnbMenuResponse.from(menu) — visible=true 자식만 포함
 */
public record FoGnbMenuResponse(
        Long id,
        String name,
        String nameMsgKey,
        String description,
        String descriptionMsgKey,
        String url,
        String icon,
        Integer sortOrder,
        List<FoGnbMenuResponse> children
) {

    /** Menu 엔티티 → DTO 변환 (자식 visible=true 필터링 포함) */
    public static FoGnbMenuResponse from(Menu menu) {
        List<FoGnbMenuResponse> childResponses = menu.getChildren().stream()
                .filter(c -> Boolean.TRUE.equals(c.getVisible()))
                .map(FoGnbMenuResponse::from)
                .toList();

        return new FoGnbMenuResponse(
                menu.getId(),
                menu.getName(),
                menu.getNameMsgKey(),
                menu.getDescription(),
                menu.getDescriptionMsgKey(),
                menu.getUrl(),
                menu.getIcon(),
                menu.getSortOrder(),
                childResponses
        );
    }

    /**
     * Menu 엔티티 → DTO 변환 (en 배치 치환 버전)
     * - enMap: msgKey → en 텍스트 (MessageResourceService.resolveEnMap 결과)
     * - name/description을 enMap 값으로 치환, msgKey 없거나 맵에 없으면 원래 한국어값 유지(폴백)
     * - children도 동일 맵으로 재귀 치환 (계층 트리 전체 en 적용)
     */
    public static FoGnbMenuResponse from(Menu menu, Map<String, String> enMap) {
        List<FoGnbMenuResponse> childResponses = menu.getChildren().stream()
                .filter(c -> Boolean.TRUE.equals(c.getVisible()))
                .map(c -> from(c, enMap))
                .toList();

        String name = resolveText(menu.getNameMsgKey(), menu.getName(), enMap);
        String description = resolveText(menu.getDescriptionMsgKey(), menu.getDescription(), enMap);

        return new FoGnbMenuResponse(
                menu.getId(),
                name,
                menu.getNameMsgKey(),
                description,
                menu.getDescriptionMsgKey(),
                menu.getUrl(),
                menu.getIcon(),
                menu.getSortOrder(),
                childResponses
        );
    }

    /** msgKey가 있고 enMap에 값이 있으면 en, 아니면 원래값(폴백) */
    private static String resolveText(String msgKey, String original, Map<String, String> enMap) {
        if (msgKey == null || msgKey.isBlank() || enMap == null) return original;
        return enMap.getOrDefault(msgKey, original);
    }
}
