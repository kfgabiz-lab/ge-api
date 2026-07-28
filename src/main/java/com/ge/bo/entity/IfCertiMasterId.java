package com.ge.bo.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * IF_R_CERTI_MASTER 복합 PK — 실제 개발DB 물리 스키마 기준(certi_no, bi, nahp_level_seq)
 * 이 IF의 1행 = '인증서 하나가 카테고리 한 곳에 등록된 것'
 */
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class IfCertiMasterId implements Serializable {
    private String certiNo;
    private String bi;
    private Short nahpLevelSeq;
}
