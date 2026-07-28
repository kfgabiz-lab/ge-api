package com.ge.bo.batch.contents;

import lombok.Getter;

import java.util.Map;

/**
 * 콘텐츠 배치 격리 신호 — 정제·변환·저장 중 저장 불가 행을 만나면 던진다.
 * 배치는 이 예외를 잡아 contents_if_fail_row에 원본·사유를 남기고 해당 행/문서만 건너뛴 뒤 계속 진행한다(BusinessException과
 * 달리 HTTP 응답용이 아니라 배치 내부 흐름 제어용이라 별도 타입으로 둔다).
 */
@Getter
public class ContentsIngestException extends RuntimeException {

    /** CLEANSE(정제) / CONVERT(변환) / UPSERT(저장) */
    private final String failStep;
    /** NULL_KEY / PARSE_DATE / UNKNOWN_DOC_TYPE / VALUE_CONFLICT / DOC_NOT_FOUND 등 정형 분류 코드 */
    private final String failCode;
    private final String sourceTable;
    private final String sourceDocKey;
    private final String sourceRowKey;
    private final Map<String, Object> rawData;

    public ContentsIngestException(String failStep, String failCode, String sourceTable, String sourceDocKey,
                                   String sourceRowKey, Map<String, Object> rawData, String message) {
        super(message);
        this.failStep = failStep;
        this.failCode = failCode;
        this.sourceTable = sourceTable;
        this.sourceDocKey = sourceDocKey;
        this.sourceRowKey = sourceRowKey;
        this.rawData = rawData;
    }
}
