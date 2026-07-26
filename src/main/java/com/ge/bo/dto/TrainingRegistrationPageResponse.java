package com.ge.bo.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Training 신청 내역(관리자 조회) 목록 + 페이지네이션 응답 DTO
 * PageDataListResponse와 완전히 동일한 구조(설계서 8절 — 네이티브 COUNT+LIMIT/OFFSET 조합용 신규 페이징 DTO)
 */
@Getter
@Builder
public class TrainingRegistrationPageResponse {

    /** 현재 페이지 데이터 목록 */
    private List<TrainingRegistrationListResponse> content;

    /** 전체 데이터 수 */
    private long totalElements;

    /** 전체 페이지 수 */
    private int totalPages;

    /** 현재 페이지 번호 (0-based) */
    private int page;

    /** 페이지 크기 */
    private int size;

    /** 마지막 페이지 여부 */
    private boolean last;

    /** 첫 번째 페이지 여부 */
    private boolean first;
}
