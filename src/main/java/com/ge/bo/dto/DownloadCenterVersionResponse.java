package com.ge.bo.dto;

import java.util.List;

/**
 * FO Download Center 버전 1건(contents_version 단위)
 * - 노출 게이트(version_expose=true, is_deleted=false)를 통과하고, 노출 파일이 1건 이상 있는 버전만 내려간다.
 * - 정렬: sort_key DESC(최신 버전 먼저).
 *
 * @param versionId   contents_version.id
 * @param versionName 버전 표시명(nullable — 원천에 없을 수 있음)
 * @param sortKey     정렬 키(클수록 최신)
 * @param files       해당 버전의 노출 파일 목록
 */
public record DownloadCenterVersionResponse(
        Long versionId,
        String versionName,
        int sortKey,
        List<DownloadCenterFileResponse> files
) {}
