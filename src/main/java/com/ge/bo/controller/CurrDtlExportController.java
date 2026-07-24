package com.ge.bo.controller;

import com.ge.bo.common.excel.ExcelService;
import com.ge.bo.service.CurrDtlExportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/export/")
public class CurrDtlExportController {

    private final ExcelService excelService;
    private final CurrDtlExportService currDtlExportService;

    @GetMapping("/trnSchedules")
    public ResponseEntity<byte[]> trnSchedulesExport(
            @RequestParam Map<String, String> allParams
    ) {

        // 검색 파라미터 설정(3가지 중 1개)
        List<Map<String, Object>> rows = currDtlExportService.getTrnSchedulesList(allParams);

        // 쿼리 조회 후 header, key, dateFormat list 생성
        List<String> headerList =
                List.of("Curriculum Title","Training Start Date","Training End Date","Training Date", "Start Time", "End Time", "Training Title", "Description", "Trainer", "Training Fee");
        List<String> keyList =
                List.of("curr_title","training_date_from","training_date_to", "training_date", "time_from", "time_to", "title", "description", "trainer","training_fee");
        List<String> dateFormatList = List.of("","","", "", "", "", "", "", "","");

        // 파일명: trainingSchedules_{yyyyMMdd}.xlsx or .csv
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String fileName = "TrainingSchedules_" + today + ".csv";
//        String fileName = "TrainingSchedules_" + today + ".xlsx";

        // 엑셀/CSV 바이트 생성
        byte[] fileBytes = excelService.buildCsv(headerList, keyList, dateFormatList, null, rows);
//        byte[] fileBytes = excelService.buildXlsx(headerList, keyList, dateFormatList, null, rows, "fileName");

        // 응답 헤더 설정
        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        responseHeaders.setContentDisposition(
                ContentDisposition.attachment()
                        .filename(fileName, StandardCharsets.UTF_8)
                        .build());

        return ResponseEntity.ok()
                .headers(responseHeaders)
                .body(fileBytes);
    }

    @GetMapping("/currDetail")
    public ResponseEntity<byte[]> currDetailExport(
            @RequestParam Map<String, String> allParams
    ) {

        // 검색 파라미터 설정(3가지 중 1개)
        List<Map<String, Object>> rows = currDtlExportService.getCurrDetailList(allParams);

        // 쿼리 조회 후 header, key, dateFormat list 생성
        List<String> headerList =
                List.of("Training", "Curriculum Title", "Training Type", "Power Products", "Automation Products", "Title", "Training Start Date", "Training End Date", "Training Duration", "Training Capacity", "Training Address", "Tel", "Email");
        List<String> keyList =
                List.of("training_course", "curr_title", "training_type", "power_list", "automation_list", "title", "training_date_from","training_date_to", "duration", "capacity", "address", "phone", "email");
        List<String> dateFormatList = List.of("", "", "", "", "", "", "", "", "", "", "", "", "");

        // 파일명: trainingSchedules_{yyyyMMdd}.xlsx or .csv
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String fileName = "CurriculumDetail_" + today + ".csv";
//        String fileName = "CurriculumDetail_" + today + ".xlsx";

        // 엑셀/CSV 바이트 생성
        byte[] fileBytes = excelService.buildCsv(headerList, keyList, dateFormatList, null, rows);
//        byte[] fileBytes = excelService.buildXlsx(headerList, keyList, dateFormatList, null, rows, "fileName");

        // 응답 헤더 설정
        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        responseHeaders.setContentDisposition(
                ContentDisposition.attachment()
                        .filename(fileName, StandardCharsets.UTF_8)
                        .build());

        return ResponseEntity.ok()
                .headers(responseHeaders)
                .body(fileBytes);
    }
}