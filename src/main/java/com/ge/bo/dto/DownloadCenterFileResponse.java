package com.ge.bo.dto;

/**
 * FO Download Center 파일 1건(contents_file 단위)
 * - 노출 게이트(file_expose=true, is_deleted=false)를 통과한 파일만 내려간다.
 * - filePath: Blob Storage(CTP) 원본 경로. FE는 이 값을 기존 다운로드 엔드포인트
 *   (GET /api/v1/fo/ctpApi/fileDownUrl?filePath=...)에 넘겨 그때그때 fresh SAS URL을 발급받는다.
 *   ⚠️ SAS URL은 단기 만료라 목록 응답에 downloadUrl/copyUrl 을 넣지 않는다.
 *
 * @param fileId       contents_file.id
 * @param fileName     원본 파일명(확장자 포함)
 * @param fileExt      확장자(pdf/zip/msi 등, nullable)
 * @param fileSize     파일 크기(byte, nullable)
 * @param fileSizeText fileSize 를 사람이 읽는 단위(B/KB/MB/GB, 소수 2자리)로 포맷한 문자열(nullable)
 * @param sourceSystem 원천 시스템 코드(예: SSQ)
 * @param filePath     CTP Blob Storage 원본 경로(다운로드/URL복사 시 CtpFileDownloadService 호출용)
 */
public record DownloadCenterFileResponse(
        Long fileId,
        String fileName,
        String fileExt,
        Long fileSize,
        String fileSizeText,
        String sourceSystem,
        String filePath,
        String sourceFilePath
) {}
