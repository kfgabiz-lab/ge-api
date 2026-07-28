package com.ge.bo.batch.contents;

import java.util.List;

/**
 * 문서 1건 Upsert 결과 집계 — 배치 오케스트레이터가 전체 배치 합계로 누적한다.
 * insertedCategoryPaths/insertedVersionKeys는 이번 upsert에서 신규 INSERT된 항목의 자연키 목록 —
 * 이미 존재하던 문서(masterUpdate=1)에 새 카테고리/에피소드가 추가됐는지 판별하는 데 쓰인다.
 * changedCategoryDetails/changedVersionDetails/masterFieldChanges는 이미 있던 행이 이번 배치에서
 * 필드값이 바뀐 경우("필드명: 이전값 → 새값" 형태)의 목록 — expose/delete_yn 등 특정 필드에 국한하지 않고
 * 매핑된 필드 전체를 비교해서 담는다.
 */
public record WriteCounts(long contentsId, int masterInsert, int masterUpdate, int categoryInsert, int categoryUpdate,
                           int versionInsert, int versionUpdate, int fileInsert, int fileUpdate,
                           List<String> insertedCategoryPaths, List<String> insertedVersionKeys,
                           List<String> changedCategoryDetails, List<String> changedVersionDetails,
                           List<String> masterFieldChanges) {
}
