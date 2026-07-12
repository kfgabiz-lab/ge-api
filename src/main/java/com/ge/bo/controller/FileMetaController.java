package com.ge.bo.controller;

import com.ge.bo.annotation.ApiLinkedEntity;
import com.ge.bo.dto.FileMetaResponse;
import com.ge.bo.service.FileMetaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 파일 메타정보 업로드/조회 API 컨트롤러
 * Slug Entity 시스템 FILE 타입 필드가 참조하는 범용 파일 메타 처리
 * 기준: /api/v1/file-meta
 */
@RestController
@RequestMapping("/api/v1/file-meta")
@RequiredArgsConstructor
@ApiLinkedEntity("FileMeta")
public class FileMetaController {

  private final FileMetaService fileMetaService;

  /**
   * 파일 단건 업로드
   * POST /api/v1/file-meta/upload
   * multipart/form-data: file
   */
  @PostMapping("/upload")
  public ResponseEntity<FileMetaResponse> upload(@RequestParam("file") MultipartFile file) {
    // 현재 인증된 관리자 이메일을 createdBy로 사용
    String createdBy = SecurityContextHolder.getContext().getAuthentication().getName();
    FileMetaResponse response = fileMetaService.upload(file, createdBy);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  /**
   * 파일 메타데이터 단건 조회
   * GET /api/v1/file-meta/{id}
   */
  @GetMapping("/{id}")
  public ResponseEntity<FileMetaResponse> getOne(@PathVariable Long id) {
    return ResponseEntity.ok(fileMetaService.getOne(id));
  }

  /**
   * 파일 메타데이터 일괄(다건) 조회
   * GET /api/v1/file-meta?ids=1,2,3
   * entity Form FILE 필드 수정 모드 진입 시 기존 파일 목록(파일명·미리보기) 표시용
   * 존재하지 않는 id가 섞여 있어도 에러 없이 존재하는 항목만 반환한다.
   */
  @GetMapping
  public ResponseEntity<List<FileMetaResponse>> getByIds(@RequestParam List<Long> ids) {
    return ResponseEntity.ok(fileMetaService.getByIds(ids));
  }

  /**
   * 파일 다운로드/blob 제공 (스트리밍)
   * GET /api/v1/file-meta/{id}/download
   * Content-Type: mime_type, Content-Disposition 파일명: original_name
   */
  @GetMapping("/{id}/download")
  public ResponseEntity<org.springframework.core.io.Resource> download(@PathVariable Long id) {
    FileMetaService.DownloadResult result = fileMetaService.download(id);

    // 한글 파일명을 안전하게 인코딩
    ContentDisposition contentDisposition = ContentDisposition.attachment()
        .filename(result.originalName(), StandardCharsets.UTF_8)
        .build();

    HttpHeaders headers = new HttpHeaders();
    headers.setContentDisposition(contentDisposition);
    headers.setContentType(MediaType.parseMediaType(result.mimeType()));

    return ResponseEntity.ok()
        .headers(headers)
        .body(result.resource());
  }

  /**
   * 파일 삭제 (DB + 파일시스템)
   * DELETE /api/v1/file-meta/{id}
   */
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable Long id) {
    fileMetaService.delete(id);
    return ResponseEntity.noContent().build();
  }
}
