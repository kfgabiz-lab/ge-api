package com.ge.bo.service;

import com.ge.bo.dto.PageFileDataIdRequest;
import com.ge.bo.dto.PageFileResponse;
import com.ge.bo.entity.PageFile;
import com.ge.bo.exception.ErrorCode;
import com.ge.bo.repository.PageFileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * 파일 업로드/다운로드/삭제 비즈니스 로직
 * 파일 저장 경로: {upload-root}/page-files/{YYYY}/{MM}/{UUID}.{ext}
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PageFileService {

    private final PageFileRepository pageFileRepository;

    /** application.yml: file.upload-root */
    @Value("${file.upload-root:/uploads}")
    private String uploadRoot;

    // ── 업로드 ────────────────────────────────────────────────

    /**
     * 파일 단건 업로드
     * 순서: 유효성 검사 → 파일시스템 저장 → DB INSERT
     * 파일시스템 저장 실패 시 예외 발생 → @Transactional 롤백으로 DB INSERT 무효화
     *
     * @param file         업로드할 파일
     * @param templateSlug 페이지 템플릿 slug
     * @param fieldKey     필드 키 (예: attachFiles)
     * @return 저장된 파일 정보 DTO
     */
    @Transactional
    public PageFileResponse upload(MultipartFile file, String templateSlug, String fieldKey) {

        // 파일 null 검사 (파일 자체가 없는 경우)
        if (file == null) {
            throw ErrorCode.FILE_REQUIRED.toException();
        }
        // 파일 비어있음 검사 (파일은 있지만 크기가 0인 경우)
        if (file.isEmpty()) {
            throw ErrorCode.FILE_EMPTY.toException();
        }

        // 원본 파일명 추출 (없으면 "unknown")
        String origName = file.getOriginalFilename();
        if (origName == null || origName.isBlank()) {
            origName = "unknown";
        }

        // UUID 기반 저장명 생성: {UUID}.{확장자소문자} (예: a3f2c1d4.pdf)
        String ext = extractExtension(origName);
        String saveName = UUID.randomUUID() + (ext.isEmpty() ? "" : "." + ext);

        // 연월 디렉토리 경로 생성: {upload-root}/page-files/{YYYY}/{MM}/
        LocalDate today = LocalDate.now();
        String dirPath = uploadRoot + "/page-files/"
                + today.getYear() + "/"
                + String.format("%02d", today.getMonthValue()) + "/";

        // 파일시스템에 저장 (실패 시 FILE_UPLOAD_FAILED 예외 → 트랜잭션 롤백)
        saveToFileSystem(file, dirPath, saveName);

        // DB에 파일 메타데이터 저장 (data_id = NULL: 폼 저장 전 임시 상태)
        PageFile pageFile = PageFile.builder()
                .templateSlug(templateSlug)
                .dataId(null)
                .fieldKey(fieldKey)
                .origName(origName)
                .saveName(saveName)
                .filePath(dirPath)
                .fileSize(file.getSize())
                .mimeType(file.getContentType() != null ? file.getContentType() : "application/octet-stream")
                .build();

        PageFile saved = pageFileRepository.save(pageFile);
        return PageFileResponse.from(saved);
    }

    // ── 메타데이터 조회 ───────────────────────────────────────

    /**
     * ID 목록으로 파일 메타데이터 일괄 조회
     * GET /api/page-files/meta?ids=1,2,3
     *
     * @param ids 조회할 page_file.id 목록
     * @return 파일 메타데이터 목록
     */
    @Transactional(readOnly = true)
    public List<PageFileResponse> getMeta(List<Long> ids) {
        return pageFileRepository.findAllById(ids).stream()
                .map(PageFileResponse::from)
                .toList();
    }

    // ── 다운로드 ──────────────────────────────────────────────

    /**
     * 파일 다운로드 — InputStreamResource 스트리밍 방식
     * 컨트롤러에서 Content-Disposition, Content-Type 헤더를 설정
     *
     * @param id page_file.id
     * @return 파일 Resource (스트리밍)
     */
    @Transactional(readOnly = true)
    public DownloadResult download(Long id) {
        // DB에서 파일 메타데이터 조회
        PageFile pageFile = pageFileRepository.findById(id)
                .orElseThrow(ErrorCode.FILE_NOT_FOUND::toException);

        // 실제 파일 경로 구성: filePath + saveName
        Path filePath = Paths.get(pageFile.getFilePath(), pageFile.getSaveName());

        try {
            InputStream inputStream = Files.newInputStream(filePath);
            Resource resource = new InputStreamResource(inputStream);
            return new DownloadResult(resource, pageFile.getOrigName(), pageFile.getMimeType());
        } catch (IOException e) {
            log.error("[PageFileService] 파일 읽기 실패: id={}, path={}", id, filePath, e);
            throw ErrorCode.FILE_NOT_FOUND.toException();
        }
    }

    /**
     * 다운로드 결과 묶음 (Resource + 헤더 정보)
     * 컨트롤러에서 응답 헤더 설정에 사용
     */
    public record DownloadResult(Resource resource, String origName, String mimeType) {}

    // ── data_id 연결 ──────────────────────────────────────────

    /**
     * 임시 파일(data_id=NULL)들에 page_data.id 연결
     * 폼 저장 완료 후 FE에서 호출
     *
     * @param request fileIds 목록 + dataId
     */
    @Transactional
    public void link(PageFileDataIdRequest request) {
        List<PageFile> files = pageFileRepository.findAllById(request.getFileIds());

        // data_id 일괄 업데이트
        files.forEach(f -> f.setDataId(request.getDataId()));

        pageFileRepository.saveAll(files);
        log.info("[PageFileService] data_id 연결 완료: dataId={}, fileIds={}", request.getDataId(), request.getFileIds());
    }

    // ── 단건 삭제 ─────────────────────────────────────────────

    /**
     * 파일 단건 삭제
     * 순서: DB DELETE → 파일시스템 삭제 (파일시스템 실패는 로그만 기록)
     *
     * @param id page_file.id
     */
    @Transactional
    public void delete(Long id) {
        // 파일 조회 (없으면 404)
        PageFile pageFile = pageFileRepository.findById(id)
                .orElseThrow(ErrorCode.FILE_NOT_FOUND::toException);

        // DB에서 먼저 삭제
        pageFileRepository.delete(pageFile);

        // 파일시스템에서 삭제 (실패해도 예외 비전파 — 로그만 기록)
        deleteFromFileSystem(pageFile.getFilePath(), pageFile.getSaveName());
    }

    // ── page_data 삭제 시 연관 파일 일괄 삭제 ─────────────────

    /**
     * data_id에 연관된 파일 전체 삭제
     * PageDataService.delete() 내부에서 호출
     *
     * @param dataId 삭제할 page_data.id
     */
    @Transactional
    public void deleteByDataId(Long dataId) {
        List<PageFile> files = pageFileRepository.findByDataId(dataId);

        if (files.isEmpty()) {
            return;
        }

        // 파일시스템 파일 일괄 삭제 (실패해도 로그만 기록)
        files.forEach(f -> deleteFromFileSystem(f.getFilePath(), f.getSaveName()));

        // DB 일괄 삭제
        pageFileRepository.deleteAll(files);
        log.info("[PageFileService] 연관 파일 일괄 삭제 완료: dataId={}, 건수={}", dataId, files.size());
    }

    // ── private 헬퍼 ──────────────────────────────────────────

    /**
     * 파일시스템에 저장
     * 디렉토리 자동 생성 → 파일 쓰기
     * 실패 시 FILE_UPLOAD_FAILED 예외 발생 (트랜잭션 롤백 유발)
     */
    private void saveToFileSystem(MultipartFile file, String dirPath, String saveName) {
        Path dir = Paths.get(dirPath);
        Path dest = dir.resolve(saveName);
        try {
            // 연월 디렉토리 없으면 자동 생성
            Files.createDirectories(dir);
            // NIO InputStream 방식으로 저장 (Windows에서 File.isAbsolute() 오판 방지)
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, dest, StandardCopyOption.REPLACE_EXISTING);
            }
            log.info("[PageFileService] 파일 저장 완료: {}", dest);
        } catch (IOException e) {
            log.error("[PageFileService] 파일 저장 실패: path={}", dest, e);
            throw ErrorCode.FILE_UPLOAD_FAILED.toException();
        }
    }

    /**
     * 파일시스템에서 파일 삭제
     * 실패해도 예외를 던지지 않음 — WARN 로그만 기록
     */
    private void deleteFromFileSystem(String dirPath, String saveName) {
        Path filePath = Paths.get(dirPath, saveName);
        try {
            if (Files.exists(filePath)) {
                Files.delete(filePath);
                log.info("[PageFileService] 파일 삭제 완료: {}", filePath);
            } else {
                log.warn("[PageFileService] 삭제 대상 파일 없음 (무시): {}", filePath);
            }
        } catch (IOException e) {
            // 파일 삭제 실패는 로그만 기록 — 예외 비전파
            log.warn("[PageFileService] 파일 삭제 실패 (무시): path={}", filePath, e);
        }
    }

    /**
     * 원본 파일명에서 확장자 추출 (소문자 변환)
     * 예) "report.PDF" → "pdf", "noext" → ""
     */
    private String extractExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dotIndex + 1).toLowerCase();
    }
}
