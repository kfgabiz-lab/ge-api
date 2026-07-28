package com.ge.bo.batch.contents;

import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 원천 공통 버전 모델 — contents_version 1행 + 하위 파일 목록에 대응
 */
@Getter
@Builder
public class VersionItem {
    /** 문서 안에서 버전을 구분하는 키: SSQ=version_id, CATALOG=PRT_YYMM|PRT_VER, CERTI='DEFAULT' */
    private final String sourceVersionKey;
    private final String versionName;
    private final String versionDesc;
    private final String videoUrl;
    private final boolean versionExpose;
    private final int sortKey;
    private final OffsetDateTime sourceUpdatedAt;
    @Builder.Default
    private final List<FileItem> files = new ArrayList<>();
}
