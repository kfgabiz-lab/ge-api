package com.ge.bo.batch.contents;

import java.util.Map;

/**
 * 행 단위 격리 결과 — 문서 전체가 아니라 그 행만 건너뛸 때 사용. contents_if_fail_row 1행으로 저장된다.
 */
public record RowFailure(String sourceTable, String sourceRowKey, String failCode, String failDetail,
                          Map<String, Object> rawData) {
}
