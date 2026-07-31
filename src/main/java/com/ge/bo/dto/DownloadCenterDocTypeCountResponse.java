package com.ge.bo.dto;

/**
 * FO Download Center 문서유형(docType)별 콘텐츠 건수 — 필터 패널 문서유형 옆 숫자 표시용.
 * - 공통코드 그룹 DOC_TYPE 에 등록된 활성 코드를 sort_order 순으로 항상 순회해
 *   매칭되지 않으면 count=0으로 채워 내린다(FE가 전 유형 렌더링).
 * - 노출 대상 문서유형의 추가/삭제/라벨 변경은 소스가 아니라 공통코드 관리에서 처리한다.
 *
 * @param docType      문서유형 코드(code_detail.code)
 * @param docTypeLabel 표시 라벨(code_detail.name)
 * @param count        해당 문서유형의 노출 콘텐츠(master) 수
 */
public record DownloadCenterDocTypeCountResponse(
        String docType,
        String docTypeLabel,
        int count
) {}
