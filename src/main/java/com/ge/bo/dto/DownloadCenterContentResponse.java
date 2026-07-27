package com.ge.bo.dto;

import java.util.List;

/**
 * FO Download Center 목록 카드 1건(콘텐츠 = contents_master 단위)
 * - 대상: doc_type <> 'V'(영상 제외) 이고 노출 게이트(master.expose + 노출 버전 + 노출 파일)를 통과한 콘텐츠.
 * - 하나의 콘텐츠는 여러 버전(versions)을, 각 버전은 여러 파일을 가질 수 있다.
 *
 * @param docType      원천 문서 유형 코드(C/M/D/S/R/O 등)
 * @param docTypeLabel docType 표시 라벨(C=Catalog, M=Manuals, D=Drawings, S=Software, R=Certificates, O=Tech Data)
 * @param title        표시 제목: nahp_title 우선, 없으면 doc_title
 * @param date         원천 갱신일(YYYY-MM-DD, nullable) — 정렬/표시용
 * @param categoryL1Id 대표 LV1 카테고리 코드(nullable)
 * @param categoryL2Id 대표 LV2 카테고리 코드(nullable)
 * @param versions     버전 목록(sort_key DESC)
 */
public record DownloadCenterContentResponse(
        Long id,
        String docType,
        String docTypeLabel,
        String title,
        String date,
        String categoryL1Id,
        String categoryL2Id,
        List<DownloadCenterVersionResponse> versions
) {}
