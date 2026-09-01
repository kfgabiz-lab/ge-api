package com.ge.bo.dto;

import java.util.List;

/**
 * FO Download Center 버전 1건(contents_version 단위)
 * - 노출 게이트(version_expose=true, is_deleted=false)를 통과하고, 노출 파일이 1건 이상 있는 버전만 내려간다.
 * - 정렬: version_name을 숫자로 캐스팅 가능하면 그 값 DESC(최신 버전 먼저), 숫자가 아니면(예: CATALOG의
 *   "YYYYMM vNN" 형식) 뒤로 밀린다 — DownloadCenterService#loadContents() 참고.
 *
 * @param versionId   contents_version.id
 * @param versionName 버전 표시명(nullable — 원천에 없을 수 있음)
 * @param sortKey     원천 저장용 정렬 키(화면 정렬에는 더 이상 쓰이지 않음, 참고용)
 * @param files       해당 버전의 노출 파일 목록
 */
public record DownloadCenterVersionResponse(
        Long versionId,
        String versionName,
        int sortKey,
        List<DownloadCenterFileResponse> files
) {}
