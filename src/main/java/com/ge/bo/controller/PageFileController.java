package com.ge.bo.controller;

import com.ge.bo.dto.PageFileDataIdRequest;
import com.ge.bo.dto.PageFileResponse;
import com.ge.bo.service.PageFileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 파일 업로드/다운로드/삭제 API 컨트롤러
 * 기준: /api/page-files
 */
@RestController
@RequestMapping("/api/page-files")
@RequiredArgsConstructor
public class PageFileController {

    private final PageFileService pageFileService;

    /**
     * 파일 단건 업로드
     * POST /api/page-files/upload
     * multipart/form-data: file + templateSlug + fieldKey
     */
    @PostMapping("/upload")
    public ResponseEntity<PageFileResponse> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("templateSlug") String templateSlug,
            @RequestParam("fieldKey") String fieldKey
    ) {
        PageFileResponse response = pageFileService.upload(file, templateSlug, fieldKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * 파일 메타데이터 일괄 조회
     * GET /api/page-files/meta?ids=1,2,3
     * 편집 모드에서 기존 파일 목록(파일명·크기) 표시용
     */
    @GetMapping("/meta")
    public ResponseEntity<List<PageFileResponse>> getMeta(@RequestParam List<Long> ids) {
        return ResponseEntity.ok(pageFileService.getMeta(ids));
    }

    /**
     * 파일 다운로드 (스트리밍)
     * GET /api/page-files/{id}
     * Content-Disposition: attachment — 브라우저에서 다운로드 처리
     */
    @GetMapping("/{id}")
    public ResponseEntity<org.springframework.core.io.Resource> download(@PathVariable Long id) {
        PageFileService.DownloadResult result = pageFileService.download(id);

        // 한글 파일명을 안전하게 인코딩
        ContentDisposition contentDisposition = ContentDisposition.attachment()
                .filename(result.origName(), StandardCharsets.UTF_8)
                .build();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(contentDisposition);
        headers.setContentType(MediaType.parseMediaType(result.mimeType()));

        return ResponseEntity.ok()
                .headers(headers)
                .body(result.resource());
    }

    /**
     * 임시 파일에 data_id 연결
     * PATCH /api/page-files/link
     * 폼 저장 완료 후 FE에서 호출
     */
    @PatchMapping("/link")
    public ResponseEntity<Void> link(@Valid @RequestBody PageFileDataIdRequest request) {
        pageFileService.link(request);
        return ResponseEntity.ok().build();
    }

    /**
     * 파일 삭제 (DB + 파일시스템)
     * DELETE /api/page-files/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        pageFileService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
