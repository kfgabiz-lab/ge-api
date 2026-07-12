package com.ge.bo.service;

import com.ge.bo.common.file.FileStorageService;
import com.ge.bo.dto.FileMetaResponse;
import com.ge.bo.entity.FileMeta;
import com.ge.bo.exception.ErrorCode;
import com.ge.bo.repository.FileMetaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * 파일 메타정보 업로드/조회 비즈니스 로직
 * Slug Entity 시스템 FILE 타입 필드가 참조하는 범용 파일 메타 처리
 * 파일 저장 경로: {upload-root}/file-meta/{YYYY}/{MM}/{UUID}.{ext}
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileMetaService {

  private final FileMetaRepository fileMetaRepository;
  private final FileStorageService fileStorageService;

  /** application.yml: ls.file-storage (blob | 파일시스템) */
  @Value("${ls.file-storage}")
  private String fileStorage;

  /** application.yml: file.upload-root */
  @Value("${file.upload-root:/uploads}")
  private String uploadRoot;

  // ── 업로드 ────────────────────────────────────────────────

  /**
   * 파일 단건 업로드
   * 순서: 유효성 검사 → 파일 저장(blob 또는 파일시스템) → DB INSERT
   * 파일 저장 실패 시 예외 발생 → @Transactional 롤백으로 DB INSERT 무효화
   *
   * @param file      업로드할 파일
   * @param createdBy 업로드한 관리자 이메일
   * @return 저장된 파일 메타 정보 DTO
   */
  @Transactional
  public FileMetaResponse upload(MultipartFile file, String createdBy) {

    // 파일 null 검사 (파일 자체가 없는 경우)
    if (file == null) {
      throw ErrorCode.FILE_REQUIRED.toException();
    }
    // 파일 비어있음 검사 (파일은 있지만 크기가 0인 경우)
    if (file.isEmpty()) {
      throw ErrorCode.FILE_EMPTY.toException();
    }

    // 원본 파일명 추출 (없으면 "unknown")
    String originalName = file.getOriginalFilename();
    if (originalName == null || originalName.isBlank()) {
      originalName = "unknown";
    }

    // UUID 기반 저장명 생성: {UUID}.{확장자소문자} (예: a3f2c1d4.pdf)
    String ext = fileStorageService.extractExtension(originalName);
    String saveName = UUID.randomUUID() + (ext.isEmpty() ? "" : "." + ext);

    // 연월 디렉토리 경로 생성: {upload-root}/file-meta/{YYYY}/{MM}/
    LocalDate today = LocalDate.now();
    String dirPath = uploadRoot + "/file-meta/"
        + today.getYear() + "/"
        + String.format("%02d", today.getMonthValue()) + "/";

    // Azure Blob Storage 또는 파일시스템에 저장
    String blobUrl = null;
    if ("blob".equals(fileStorage)) {
      // blob storage에 upload
      blobUrl = fileStorageService.uploadFile(file, dirPath + saveName);
    } else {
      // 파일시스템에 저장 (실패 시 FILE_UPLOAD_FAILED 예외 → 트랜잭션 롤백)
      fileStorageService.saveToLocal(file, dirPath, saveName);
    }

    // DB에 파일 메타데이터 저장 (createdBy는 @CreatedBy Auditing으로도 보정됨)
    FileMeta fileMeta = FileMeta.builder()
        .originalName(originalName)
        .saveName(saveName)
        .filePath(dirPath)
        .blobUrl(blobUrl)
        .fileSize(file.getSize())
        .mimeType(file.getContentType() != null ? file.getContentType() : "application/octet-stream")
        .createdBy(createdBy)
        .build();

    FileMeta saved = fileMetaRepository.save(fileMeta);
    return FileMetaResponse.from(saved);
  }

  // ── 단건 조회 ─────────────────────────────────────────────

  /**
   * ID로 파일 메타데이터 단건 조회
   *
   * @param id file_meta.id
   * @return 파일 메타 정보 DTO (없으면 404)
   */
  @Transactional(readOnly = true)
  public FileMetaResponse getOne(Long id) {
    FileMeta fileMeta = fileMetaRepository.findById(id)
        .orElseThrow(ErrorCode.FILE_NOT_FOUND::toException);
    return FileMetaResponse.from(fileMeta);
  }

  // ── 다건(일괄) 조회 ───────────────────────────────────────

  /**
   * ID 목록으로 파일 메타데이터 일괄 조회
   * GET /api/v1/file-meta?ids=1,2,3
   * 존재하지 않는 id는 결과에서 제외될 뿐 에러를 발생시키지 않는다 (FE 부분 실패 유연 처리).
   *
   * @param ids 조회할 file_meta.id 목록
   * @return 파일 메타데이터 목록
   */
  @Transactional(readOnly = true)
  public List<FileMetaResponse> getByIds(List<Long> ids) {
    return fileMetaRepository.findAllById(ids).stream()
        .map(FileMetaResponse::from)
        .toList();
  }

  // ── 다운로드 ──────────────────────────────────────────────

  /**
   * 파일 다운로드 — InputStreamResource(local) 또는 UrlResource(blob) 스트리밍 방식
   * 컨트롤러에서 Content-Disposition, Content-Type 헤더를 설정
   *
   * @param id file_meta.id
   * @return 파일 Resource (스트리밍) + 원본 파일명 + MIME 타입
   */
  @Transactional(readOnly = true)
  public DownloadResult download(Long id) {
    FileMeta fileMeta = fileMetaRepository.findById(id)
        .orElseThrow(ErrorCode.FILE_NOT_FOUND::toException);

    if ("blob".equals(fileStorage)) {
      // blobUrl로 직접 접근하는 방식
      Resource resource;
      try {
        resource = new UrlResource(URI.create(fileMeta.getBlobUrl()));
      } catch (Exception e) {
        throw new RuntimeException("Blob URL을 Resource로 변환할 수 없습니다.", e);
      }
      return new DownloadResult(resource, fileMeta.getOriginalName(), fileMeta.getMimeType());
    } else {
      // 실제 파일 경로 구성: filePath + saveName
      Path filePath = Paths.get(fileMeta.getFilePath(), fileMeta.getSaveName());
      try {
        InputStream inputStream = Files.newInputStream(filePath);
        Resource resource = new InputStreamResource(inputStream);
        return new DownloadResult(resource, fileMeta.getOriginalName(), fileMeta.getMimeType());
      } catch (IOException e) {
        log.error("[FileMetaService] 파일 읽기 실패: id={}, path={}", id, filePath, e);
        throw ErrorCode.FILE_NOT_FOUND.toException();
      }
    }
  }

  /**
   * 다운로드 결과 묶음 (Resource + 헤더 정보)
   * 컨트롤러에서 응답 헤더 설정에 사용
   */
  public record DownloadResult(Resource resource, String originalName, String mimeType) {}

  // ── 단건 삭제 ─────────────────────────────────────────────

  /**
   * 파일 단건 삭제
   * 순서: DB DELETE → 파일시스템 삭제 (파일시스템 실패는 로그만 기록)
   * PageFileService.delete()와 동일 패턴
   *
   * @param id file_meta.id
   */
  @Transactional
  public void delete(Long id) {
    // 파일 조회 (없으면 404)
    FileMeta fileMeta = fileMetaRepository.findById(id)
        .orElseThrow(ErrorCode.FILE_NOT_FOUND::toException);

    // DB에서 먼저 삭제
    fileMetaRepository.delete(fileMeta);

    // 파일시스템에서 삭제 (실패해도 예외 비전파 — 로그만 기록)
    deleteFromFileSystem(fileMeta.getFilePath(), fileMeta.getSaveName());
  }

  // ── private 헬퍼 ──────────────────────────────────────────

  /**
   * 파일시스템에서 파일 삭제
   * 실패해도 예외를 던지지 않음 — WARN 로그만 기록
   */
  private void deleteFromFileSystem(String dirPath, String saveName) {
    Path filePath = Paths.get(dirPath, saveName);
    try {
      if (Files.exists(filePath)) {
        Files.delete(filePath);
        log.info("[FileMetaService] 파일 삭제 완료: {}", filePath);
      } else {
        log.warn("[FileMetaService] 삭제 대상 파일 없음 (무시): {}", filePath);
      }
    } catch (IOException e) {
      // 파일 삭제 실패는 로그만 기록 — 예외 비전파
      log.warn("[FileMetaService] 파일 삭제 실패 (무시): path={}", filePath, e);
    }
  }
}
