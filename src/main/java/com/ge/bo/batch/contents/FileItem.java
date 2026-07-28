package com.ge.bo.batch.contents;

import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * 원천 공통 파일 모델 — contents_file 1행에 대응 (원천 변환기가 채우고 ContentsWriter가 저장)
 */
@Getter
@Builder
public class FileItem {
    /** 버전 안에서 파일을 구분하는 키: SSQ=file_id, CATALOG=DATA_CODE|FILE_SEQ, CERTI='DEFAULT' */
    private final String sourceFileKey;
    /** 화면/다운로드에 노출할 파일명(사람이 보는 이름) — CATALOG는 FILE_ORI 우선, 그 외는 그대로 사용 */
    private final String fileName;
    /**
     * blob 경로(file_path) 생성에 쓸 파일명 — null이면 fileName을 그대로 사용한다. CATALOG만 다르게 채운다:
     * FILE_NAME(시스템이 붙인 타임스탬프 접두어 포함, 문서 간 충돌 방지용)을 그대로 써서 fileName(FILE_ORI로
     * 사람이 보기 좋게 바뀐 값)과 경로 생성용 값을 분리한다(2026-07-24 확인 — FILE_ORI만 쓰면 서로 다른 문서가
     * 같은 원본 파일명을 쓸 때 경로가 충돌할 수 있음).
     */
    private final String pathFileName;
    private final String fileExt;
    private final Long fileSize;
    private final String fileLang;
    private final String sourceFilePath;
    /** blob 서빙 경로(file_path)에 문서별 하위 폴더를 넣어야 하는 소스(SSQ)만 채움 — 그 외 소스는 null */
    private final String sourceFolderKey;
    private final Map<String, Object> attrs;
    private final boolean fileExpose;
    private final OffsetDateTime sourceUpdatedAt;
}
