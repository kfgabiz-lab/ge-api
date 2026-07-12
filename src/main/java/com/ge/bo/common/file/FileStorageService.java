package com.ge.bo.common.file;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.models.BlobHttpHeaders;
import com.ge.bo.entity.PageFile;
import com.ge.bo.exception.ErrorCode;
import com.ge.bo.repository.PageFileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileStorageService {

    private final AzureBlobProperties properties;

    private final PageFileRepository pageFileRepository;

    private BlobContainerClient getContainerClient() {

        BlobServiceClient blobServiceClient = new BlobServiceClientBuilder()
                .endpoint(properties.getEndpointUrl())
                .sasToken(properties.getSasToken())
                .buildClient();

        return blobServiceClient.getBlobContainerClient(properties.getContainerName());
    }

    public String uploadFile(MultipartFile file, String blobPath) {

        // blobPath에 첫 / 제거
        blobPath = blobPath.replaceAll("^/+", "");

        String encodedFileName = URLEncoder
                .encode(file.getOriginalFilename() != null ? file.getOriginalFilename() : "", StandardCharsets.UTF_8)
                .replace("+", "%20");

        BlobHttpHeaders headers = new BlobHttpHeaders()
                .setContentType(file.getContentType() != null ? file.getContentType() : "application/octet-stream")
                .setContentDisposition(
                        "attachment; filename*=UTF-8''" + encodedFileName
                );

        try {
            BlobContainerClient containerClient = getContainerClient();
            BlobClient blobClient = containerClient.getBlobClient(blobPath);

            blobClient.upload(
                    file.getInputStream(),
                    file.getSize(),
                    true
            );
            blobClient.setHttpHeaders(headers);

            return blobClient.getBlobUrl();

        } catch (IOException e) {
            throw new RuntimeException("Azure Blob 파일 업로드 실패", e);
        }
    }

    public byte[] downloadFile(String blobPath) {
        BlobContainerClient containerClient = getContainerClient();
        BlobClient blobClient = containerClient.getBlobClient(blobPath);

        if (!blobClient.exists()) {
            throw new RuntimeException("Blob 파일이 존재하지 않습니다: " + blobPath);
        }

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        blobClient.downloadStream(outputStream);

        return outputStream.toByteArray();
    }

    public void deleteFile(String blobPath) {
        BlobContainerClient containerClient = getContainerClient();
        BlobClient blobClient = containerClient.getBlobClient(blobPath);

        blobClient.deleteIfExists();
    }

    public String blobUrlDownload(Long id){
        PageFile pageFile = pageFileRepository.findById(id)
                .orElseThrow(ErrorCode.FILE_NOT_FOUND::toException);

        return pageFile.getBlobUrl();
    }

    /**
     * 파일시스템 로컬 저장
     * 디렉토리 자동 생성 → 파일 쓰기
     * 실패 시 FILE_UPLOAD_FAILED 예외 발생 (트랜잭션 롤백 유발)
     */
    public void saveToLocal(MultipartFile file, String dirPath, String saveName) {
        Path dir = Paths.get(dirPath);
        Path dest = dir.resolve(saveName);
        try {
            // 연월 디렉토리 없으면 자동 생성
            Files.createDirectories(dir);
            // NIO InputStream 방식으로 저장 (Windows에서 File.isAbsolute() 오판 방지)
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, dest, StandardCopyOption.REPLACE_EXISTING);
            }
            log.info("[FileStorageService] 파일 저장 완료: {}", dest);
        } catch (IOException e) {
            log.error("[FileStorageService] 파일 저장 실패: path={}", dest, e);
            throw ErrorCode.FILE_UPLOAD_FAILED.toException();
        }
    }

    /**
     * 원본 파일명에서 확장자 추출 (소문자 변환)
     * 예) "report.PDF" → "pdf", "noext" → ""
     */
    public String extractExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dotIndex + 1).toLowerCase();
    }
}
