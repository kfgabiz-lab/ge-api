package com.ge.bo.dto;

import com.ge.bo.entity.CodeDetail;

import java.util.Map;

/**
 * FO 공개 코드 응답 DTO — 비로그인 공개 API용 경량 응답
 * 관리자용 CodeDetailResponse와 달리 관리 메타(id/sortOrder/active/extra 등)를 제외하고
 * FO에서 코드→라벨 변환에 필요한 code, name만 노출한다 (최소 노출 원칙).
 * 사용법: FoCodeResponse.from(codeDetail)
 */
public record FoCodeResponse(
        String code,
        String name
) {
    public static FoCodeResponse from(CodeDetail d) {
        return new FoCodeResponse(d.getCode(), d.getName());
    }

    /**
     * CodeDetail → DTO 변환 (en 배치 치환 버전)
     * - enMap: nameMsgKey → en 텍스트 (MessageResourceService.resolveEnMap 결과)
     * - 응답 스키마는 그대로(code, name) 유지하고 name 값만 en으로 치환
     * - nameMsgKey 없거나 맵에 없으면 원래 한국어 name 유지(폴백)
     */
    public static FoCodeResponse from(CodeDetail d, Map<String, String> enMap) {
        String name = d.getName();
        String msgKey = d.getNameMsgKey();
        if (msgKey != null && !msgKey.isBlank() && enMap != null) {
            name = enMap.getOrDefault(msgKey, d.getName());
        }
        return new FoCodeResponse(d.getCode(), name);
    }
}
