package com.ge.bo.service;

import com.ge.bo.common.client.ApiCallRequest;
import com.ge.bo.common.client.ApiCallResult;
import com.ge.bo.common.client.ExternalApiClient;
import com.ge.bo.dto.CtpFileDownResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

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
        String apiUrlWithParam;
        String downloadUrl = "";

        // 파일 경로 검증
        if(path.startsWith("CTP")){
            // url + param 조합 하기
            apiUrlWithParam = apiUrl + "?code=" + code + "&filePath=" + path;

            // CTP 파일 다운로드 API 호출
            ApiCallRequest request = ApiCallRequest.get(apiUrlWithParam).header("Content-Type", "application/json").build();
            ApiCallResult<CtpFileDownResponse> result = externalApiClient.call(request, CtpFileDownResponse.class);

            downloadUrl = result.getData().downloadUrl();

            // 리턴 받은 url 특정 값 치환('\u0026' -> '&', '\u003d' -> '=')
            downloadUrl = downloadUrl.replace("\\u0026", "&").replace("\\u003d", "=");
        }

        log.info("downloadUrl : {}", downloadUrl);
        return downloadUrl;
    }
}
