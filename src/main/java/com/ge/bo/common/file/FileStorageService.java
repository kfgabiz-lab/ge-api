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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

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
}
