package com.ge.bo.batch.contents;

import java.util.List;

/**
 * 원천 Converter의 변환 결과.
 * document가 null이면 문서 전체를 저장할 수 없는 치명적 오류(FAILED) — rowFailures에 사유가 담긴다.
 * document가 있고 rowFailures가 비어있지 않으면 일부 행만 격리된 것(PARTIAL_ERROR) — 삭제여부검증 스코프에서 제외해야 한다.
 */
public record ConversionResult(ContentDocument document, List<RowFailure> rowFailures, List<String> reportNotes) {

    public boolean hasDocument() {
        return document != null;
    }

    public boolean hasRowFailures() {
        return rowFailures != null && !rowFailures.isEmpty();
    }
}
