package com.ge.bo.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * IF_R_SSQ_DOCUMENT 복합 PK — 실제 개발DB 물리 스키마 기준(doc_id, spec_group, level_1, level_2, level_3, level_4).
 * ※ 2026-07-15 발견: 최초 구현 시 (doc_id, spec_group) 2개만 PK로 선언했었음 — 같은 spec_group(=level_2와 동일값)을
 *   공유하는 행이 실제로 많아서(예: doc_id=3103의 spec_group='XGB'만 32건), Hibernate가 서로 다른 행을 같은
 *   엔티티로 오인해 세션 1차 캐시에서 뭉개버려 카테고리 데이터가 대량 유실되는 버그가 있었다. level_1~4까지
 *   전부 PK에 포함해야 실제 유니크 인덱스(pk_if_r_ssq_document)와 일치한다.
 * 이 IF의 1행 = '문서 하나가 SSQ 카테고리 경로 한 곳에 등록된 것'
 */
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class IfSsqDocumentId implements Serializable {
    private Integer docId;
    private String specGroup;
    private String level1;
    private String level2;
    private String level3;
    private String level4;
}
