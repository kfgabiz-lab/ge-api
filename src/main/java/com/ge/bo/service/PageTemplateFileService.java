package com.ge.bo.service;

import com.ge.bo.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * TSX 파일 쓰기/삭제 전담 서비스
 * DB 트랜잭션과 독립적으로 파일 시스템만 담당
 */
@Slf4j
@Service
public class PageTemplateFileService {

    /** application.yml: page-template.output-dir */
    @Value("${page-template.output-dir}")
    private String outputDir;

    /**
     * TSX 파일 생성
     * 경로: LIST → {outputDir}/{slug}/page.tsx (fileName 지정 시 {slug}/{fileName}.tsx)
     *       LAYER → {outputDir}/{slug}/LayerPopup.tsx (fileName 지정 시 {slug}/{fileName}.tsx)
     *
     * @param slug         kebab-case 식별자 (정규식 검증 완료된 값)
     * @param tsxCode      FE 생성 TSX 코드 문자열
     * @param templateType 템플릿 타입 ("LIST" 또는 "LAYER")
     * @return 생성된 파일의 절대경로 문자열
     * @throws RuntimeException 파일 쓰기 실패 시 → DB 트랜잭션 롤백 유발
     */
    public String writeFile(String slug, String tsxCode, String templateType) {
        return writeFile(slug, tsxCode, templateType, null);
    }

    /**
     * TSX 파일 생성 (파일명 직접 지정)
     *
     * @param slug         폴더명 (kebab-case 식별자)
     * @param tsxCode      FE 생성 TSX 코드 문자열
     * @param templateType 템플릿 타입 ("LIST" 또는 "LAYER")
     * @param customFileName 커스텀 파일명 (확장자 제외, null이면 기본값 사용)
     * @return 생성된 파일의 절대경로 문자열
     */
    public String writeFile(String slug, String tsxCode, String templateType, String customFileName) {
        // 파일명 결정: 커스텀 입력 → 기본값(타입 기반)
        String fileName;
        if (customFileName != null && !customFileName.isBlank()) {
            fileName = customFileName + ".tsx";
        } else {
            fileName = "LAYER".equals(templateType) ? "LayerPopup.tsx" : "page.tsx";
        }
        // 경로 구성: {outputDir}/{slug}/{fileName}
        Path filePath = Paths.get(outputDir, slug, fileName).toAbsolutePath().normalize();

        // 출력 디렉토리 경계 검증 (Path Traversal 방지)
        Path baseDir = Paths.get(outputDir).toAbsolutePath().normalize();
        if (!filePath.startsWith(baseDir)) {
            throw ErrorCode.PAGE_TEMPLATE_FILE_ERROR.toException();
        }

        try {
            // 상위 디렉토리 자동 생성
            Files.createDirectories(filePath.getParent());
            // 파일 쓰기 (덮어쓰기)
            Files.writeString(filePath, tsxCode);
            log.info("[PageTemplateFileService] TSX 파일 생성 완료: {}", filePath);
            return filePath.toString();
        } catch (IOException e) {
            log.error("[PageTemplateFileService] TSX 파일 생성 실패: slug={}, 경로={}", slug, filePath, e);
            // RuntimeException 발생 → @Transactional 자동 롤백
            throw ErrorCode.PAGE_TEMPLATE_FILE_ERROR.toException();
        }
    }

    /**
     * TSX 파일 삭제
     * 파일이 없으면 무시 (예외 비전파)
     * 삭제 실패 시 로그만 기록 — DB 삭제는 이미 완료 상태이므로 강제 롤백하지 않음
     *
     * @param filePath 삭제할 파일의 절대경로 (DB에 저장된 file_path 값)
     */
    public void deleteFile(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return;
        }
        try {
            Path path = Paths.get(filePath);
            if (Files.exists(path)) {
                Files.delete(path);
                // 빈 디렉토리도 정리
                Path parent = path.getParent();
                if (parent != null && Files.isDirectory(parent) && isDirectoryEmpty(parent)) {
                    Files.delete(parent);
                }
                log.info("[PageTemplateFileService] TSX 파일 삭제 완료: {}", filePath);
            } else {
                log.warn("[PageTemplateFileService] 삭제 대상 파일 없음 (무시): {}", filePath);
            }
        } catch (IOException e) {
            log.error("[PageTemplateFileService] TSX 파일 삭제 실패 (무시): {}", filePath, e);
            // 삭제 실패는 로그만 기록 — 예외 비전파
        }
    }

    /** 보상 처리용: 파일 쓰기 시도 후 DB 롤백이 발생한 경우 파일도 정리 */
    public void deleteFileQuietly(String filePath) {
        deleteFile(filePath);
    }

    private boolean isDirectoryEmpty(Path dir) throws IOException {
        try (var stream = Files.list(dir)) {
            return stream.findFirst().isEmpty();
        }
    }
}
