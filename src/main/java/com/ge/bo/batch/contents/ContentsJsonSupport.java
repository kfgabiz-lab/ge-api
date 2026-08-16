package com.ge.bo.batch.contents;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * attrs/raw_data 등 JSONB 컬럼 직렬화 지원 — 실패를 조용히 삼키지 않고 예외로 드러낸다(요구사항: 직렬화 실패 시
 * 해당 문서를 정상 적재한 것으로 처리하지 말 것). 프로젝트 공통 ObjectMapper(JacksonConfig) 빈을 그대로 사용한다.
 */
@Component
@RequiredArgsConstructor
public class ContentsJsonSupport {

    private final ObjectMapper objectMapper;

    /** null 또는 빈 Map은 빈 JSON 객체 "{}"로 직렬화(첨부 지시: attrs 미보유 시 null보다 빈 객체 우선 검토) */
    public String toJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException e) {
            throw new ContentsIngestException("CONVERT", "ATTRS_SERIALIZE_FAILED", null, null, null, null,
                "attrs/raw_data JSON 직렬화 실패: " + e.getMessage());
        }
    }

    /** attrs 등 jsonb 컬럼을 다시 읽어올 때 사용 — null/빈 문자열은 빈 Map으로 취급 */
    public Map<String, Object> fromJson(String value) {
        if (value == null || value.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(value, new TypeReference<Map<String, Object>>() { });
        } catch (JsonProcessingException e) {
            throw new ContentsIngestException("CONVERT", "ATTRS_DESERIALIZE_FAILED", null, null, null, null,
                "attrs JSON 역직렬화 실패: " + e.getMessage());
        }
    }
}
