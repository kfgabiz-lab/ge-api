package com.ge.bo.dto;

/**
 * FO Download Center 문서유형(docType)별 콘텐츠 건수 — 필터 패널 문서유형 옆 숫자 표시용.
 * - 6개 문서유형(C/M/D/S/R/O)을 항상 순회해 매칭되지 않으면 count=0으로 채워 내린다(FE가 전 유형 렌더링).
 * - OS/Firmware 는 대응 doc_type 이 없어 이 응답에 포함하지 않는다(FE에서 정적 표시, count=0 고정).
 *
 * @param docType      문서유형 코드(C/M/D/S/R/O)
 * @param docTypeLabel 표시 라벨(C=Catalog, M=Manuals, D=Drawings, S=Software, R=Certificates, O=Tech Data)
 * @param count        해당 문서유형의 노출 콘텐츠(master) 수
 */
public record DownloadCenterDocTypeCountResponse(
        String docType,
        String docTypeLabel,
        int count
) {}
