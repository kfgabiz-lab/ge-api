package com.ge.bo.service;

import com.ge.bo.common.client.ApiCallRequest;
import com.ge.bo.common.client.ApiCallResult;
import com.ge.bo.common.client.ExternalApiClient;
import com.ge.bo.dto.CtpFileDownResponse;
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

    @Value("${ls.lse.in-api.ctp-file-down-api-url}")
    private String apiUrl;

    @Value("${ls.lse.in-api.ctp-file-down-code}")
    private String code;

    public String ctpFileDownApi(String path){
//        String apiUrlWithParam;
        String downloadUrl = "";

        // 파일 경로 검증
        if(path.startsWith("CTP")){

            // url + param 조합 하기
//            apiUrlWithParam = apiUrl + "?code=" + code + "&filePath=" + path;
            String encPath = URLEncoder.encode(path, StandardCharsets.UTF_8);

            String url = UriComponentsBuilder.fromUriString(apiUrl)
                    .queryParam("code", code)
                    .queryParam("filePath", encPath)
                    .build()
                    .toUriString();

            // CTP 파일 다운로드 API 호출
            ApiCallRequest request = ApiCallRequest.get(url).header("Content-Type", "application/json").build();
            ApiCallResult<CtpFileDownResponse> result = externalApiClient.call(request, CtpFileDownResponse.class);

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
}
