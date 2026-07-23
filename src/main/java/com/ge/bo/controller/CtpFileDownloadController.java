package com.ge.bo.controller;

import com.ge.bo.service.CtpFileDownloadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/fo/ctpApi")
public class CtpFileDownloadController {

    private final CtpFileDownloadService ctpFileDownloadService;

    @GetMapping("/fileDownUrl")
    public String fileDownload(@RequestParam("filePath") String filePath) {
        return ctpFileDownloadService.ctpFileDownApi(filePath);
    }
}
