package com.ge.bo.service;

import com.ge.bo.common.client.ApiCallRequest;
import com.ge.bo.common.client.ApiCallResult;
import com.ge.bo.common.client.ExternalApiClient;
import com.ge.bo.dto.CtpFileDownResponse;
import com.ge.bo.dto.CtpFilePreviewResponse;
import com.ge.bo.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;


/**
 * Connect Portal(CTP) api를 통한 문서 파일 다운로드 URL 반환(blob-storage)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CtpFileDownloadService {

    private final ExternalApiClient externalApiClient;

    @Value("${ls.lse.out-api.ctp.file-down-api-url:}")
    private String fileDownApiUrl;

    @Value("${ls.lse.out-api.ctp.file-down-code:}")
    private String fileDownCode;

    @Value("${ls.lse.out-api.ctp.file-preview-api-url:}")
    private String filePreviewApiUrl;

    @Value("${ls.lse.out-api.ctp.file-preview-code:}")
    private String filePreviewCode;

    @Value("${ls.lse.out-api.ctp.file-expiry-minutes:}")
    private String fileExpiryMinutes;

    public String ctpFileDownApi(String path){

        String downloadUrl = "";

        // 파일 경로 검증
        if(path.startsWith("CTP")){

            // url + param 조합 하기
            String encPath = URLEncoder.encode(path, StandardCharsets.UTF_8);

            String url = UriComponentsBuilder.fromUriString(fileDownApiUrl)
                    .queryParam("code", fileDownCode)
                    .queryParam("filePath", encPath)
                    .queryParam("expiryMinutes", fileExpiryMinutes)
                    .build()
                    .toUriString();

            // CTP 파일 다운로드 API 호출
            ApiCallRequest request = ApiCallRequest.get(url).header("Content-Type", "application/json").build();
            ApiCallResult<CtpFileDownResponse> result = externalApiClient.callEncUrl(request, CtpFileDownResponse.class);

            if (!result.isSuccess()) {
                log.warn("CTP 파일 다운로드 URL 발급 실패 status={} error={} path={}",
                    result.getStatusCode(), result.getErrorMessage(), path);
                throw new BusinessException(HttpStatus.BAD_GATEWAY,
                    "CTP_FILE_DOWNLOAD_FAILED", "파일 다운로드 URL을 발급받지 못했습니다.");
            }

            CtpFileDownResponse body = result.getData();
            if (body == null || body.downloadUrl() == null || body.downloadUrl().isBlank()) {
                log.warn("CTP 파일 다운로드 URL 없음 status={} path={}", result.getStatusCode(), path);
                throw new BusinessException(HttpStatus.BAD_GATEWAY,
                    "CTP_FILE_DOWNLOAD_EMPTY", "파일 다운로드 URL이 비어 있습니다.");
            }

            downloadUrl = body.downloadUrl().replace("\\u0026", "&").replace("\\u003d", "=");
        }

        log.info("downloadUrl : {}", downloadUrl);
        return downloadUrl;
    }

    public String ctpFilePreviewApi(String path){

        String previewUrl = "";

        // 파일 경로 검증
        if(path.startsWith("CTP")){

            // url + param 조합 하기
            String encPath = URLEncoder.encode(path, StandardCharsets.UTF_8);

            String url = UriComponentsBuilder.fromUriString(filePreviewApiUrl)
                    .queryParam("code", filePreviewCode)
                    .queryParam("filePath", encPath)
                    .queryParam("expiryMinutes", fileExpiryMinutes)
                    .build()
                    .toUriString();

            // CTP 파일 다운로드 API 호출
            ApiCallRequest request = ApiCallRequest.get(url).header("Content-Type", "application/json").build();
            ApiCallResult<CtpFilePreviewResponse> result = externalApiClient.callEncUrl(request, CtpFilePreviewResponse.class);

            if (!result.isSuccess()) {
                log.warn("CTP 파일 미리보기 URL 발급 실패 status={} error={} path={}",
                        result.getStatusCode(), result.getErrorMessage(), path);
                throw new BusinessException(HttpStatus.BAD_GATEWAY,
                        "CTP_FILE_PREVIEW_FAILED", "파일 미리보기 URL을 발급받지 못했습니다.");
            }

            CtpFilePreviewResponse body = result.getData();
            if (body == null || body.data().previewUrl() == null || body.data().previewUrl().isBlank()) {
                log.warn("CTP 파일 미리보기 URL 없음 status={} path={}", result.getStatusCode(), path);
                throw new BusinessException(HttpStatus.BAD_GATEWAY,
                        "CTP_FILE_PREVIEW_EMPTY", "파일 미리보기 URL이 비어 있습니다.");
            }

            previewUrl = body.data().previewUrl().replace("\\u0026", "&").replace("\\u003d", "=");
        }

        log.info("previewUrl : {}", previewUrl);
        return previewUrl;
    }
}
