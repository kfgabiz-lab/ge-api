package com.ge.bo.controller;

import com.ge.bo.service.PageFileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

/**
 * FO(비로그인 공개) 파일 인라인 조회 API 컨트롤러
 * 기준: /api/v1/fo/page-files
 *
 * SecurityConfig에서 /api/v1/fo/** 는 permitAll 이므로 비로그인 FO가 호출 가능.
 * 관리자용 PageFileController.download()와 달리 Content-Disposition을 inline으로 설정해
 * 브라우저가 다운로드창을 띄우지 않고 &lt;img src&gt;로 인라인 렌더링할 수 있게 한다.
 * 파일 읽기 로직은 기존 PageFileService.download(id)를 그대로 재사용(로컬/blob 자동 분기).
 */
@RestController
@RequestMapping("/api/v1/fo/page-files")
@RequiredArgsConstructor
public class FoPageFileController {

  private final PageFileService pageFileService;

  /**
   * 파일 인라인 조회 (스트리밍)
   * GET /api/v1/fo/page-files/{id}
   * Content-Disposition: inline — 브라우저에서 다운로드 강제 없이 인라인 표시
   *
   * @param id page_file.id
   * @return 파일 Resource (스트리밍)
   */
  @GetMapping("/{id}")
  public ResponseEntity<org.springframework.core.io.Resource> view(@PathVariable Long id) {
    PageFileService.DownloadResult result = pageFileService.download(id);

    // 한글 파일명을 안전하게 인코딩 — inline으로 설정해 다운로드 강제하지 않음
    ContentDisposition contentDisposition = ContentDisposition.inline()
            .filename(result.origName(), StandardCharsets.UTF_8)
            .build();

    HttpHeaders headers = new HttpHeaders();
    headers.setContentDisposition(contentDisposition);
    headers.setContentType(MediaType.parseMediaType(result.mimeType()));
    // page_file은 id별로 내용이 불변(수정 없이 업로드/삭제만 존재)이라 무기한 캐시 + ETag로
    // 슬라이드 순환 등 재생 반복 시 브라우저가 매번 재요청하지 않고 캐시를 재사용하게 한다.
    headers.setCacheControl("public, max-age=31536000, immutable");
    headers.setETag("\"pf-" + id + "\"");

    return ResponseEntity.ok()
            .headers(headers)
            .body(result.resource());
  }
}
