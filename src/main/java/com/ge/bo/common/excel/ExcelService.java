package com.ge.bo.common.excel;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 공통 엑셀/CSV 파일 생성 서비스
 *
 * 사용법:
 *   // xlsx 생성
 *   byte[] bytes = excelService.buildXlsx(headers, keys, rows, "시트명");
 *
 *   // csv 생성 (UTF-8 BOM 포함 — 엑셀 한글 깨짐 방지)
 *   byte[] bytes = excelService.buildCsv(headers, keys, rows);
 *
 * 파라미터 공통:
 *   headers — 컬럼 헤더 목록 (예: ["이름", "이메일", "상태"])
 *   keys    — data_json 키 목록 (헤더와 순서 동일, 예: ["name", "email", "status"])
 *   rows    — 데이터 목록 (Map<키, 값>)
 */
@Service
public class ExcelService {

    /**
     * xlsx 파일 생성 (Apache POI XSSFWorkbook 사용)
     *
     * @param headers   컬럼 헤더 목록
     * @param keys      data_json 키 목록 (헤더와 순서 일치)
     * @param rows      데이터 목록
     * @param sheetName 시트명
     * @return xlsx 바이트 배열
     */
    public byte[] buildXlsx(List<String> headers, List<String> keys,
                             List<Map<String, Object>> rows, String sheetName) {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet(sheetName);

            /* ── 헤더 스타일 (굵은 글씨 + 배경색) ── */
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setBorderBottom(BorderStyle.THIN);

            /* ── 헤더 행 작성 ── */
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.size(); i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers.get(i));
                cell.setCellStyle(headerStyle);
            }

            /* ── 데이터 행 작성 ── */
            for (int rowIdx = 0; rowIdx < rows.size(); rowIdx++) {
                Row dataRow = sheet.createRow(rowIdx + 1);
                Map<String, Object> rowData = rows.get(rowIdx);
                for (int colIdx = 0; colIdx < keys.size(); colIdx++) {
                    Object value = rowData.get(keys.get(colIdx));
                    Cell cell = dataRow.createCell(colIdx);
                    cell.setCellValue(value != null ? value.toString() : "");
                }
            }

            /* ── 컬럼 너비 자동 조정 ── */
            for (int i = 0; i < headers.size(); i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(out);
            return out.toByteArray();

        } catch (IOException e) {
            throw new RuntimeException("xlsx 파일 생성 실패", e);
        }
    }

    /**
     * CSV 파일 생성 (UTF-8 BOM 포함 — 엑셀에서 한글 깨짐 방지)
     *
     * @param headers 컬럼 헤더 목록
     * @param keys    data_json 키 목록 (헤더와 순서 일치)
     * @param rows    데이터 목록
     * @return csv 바이트 배열 (BOM + UTF-8)
     */
    public byte[] buildCsv(List<String> headers, List<String> keys,
                            List<Map<String, Object>> rows) {
        StringBuilder sb = new StringBuilder();

        /* ── 헤더 행 ── */
        sb.append(String.join(",", headers.stream().map(this::escapeCsv).toList()));
        sb.append("\n");

        /* ── 데이터 행 ── */
        for (Map<String, Object> row : rows) {
            List<String> values = keys.stream()
                    .map(key -> {
                        Object value = row.get(key);
                        return escapeCsv(value != null ? value.toString() : "");
                    })
                    .toList();
            sb.append(String.join(",", values));
            sb.append("\n");
        }

        /* ── UTF-8 BOM + 본문 결합 ── */
        byte[] bom = new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] content = sb.toString().getBytes(StandardCharsets.UTF_8);
        byte[] result = new byte[bom.length + content.length];
        System.arraycopy(bom, 0, result, 0, bom.length);
        System.arraycopy(content, 0, result, bom.length, content.length);
        return result;
    }

    /**
     * CSV 셀 값 이스케이프 처리
     * - 쉼표/큰따옴표/줄바꿈 포함 시 큰따옴표로 감싸기
     * - 내부 큰따옴표는 "" 로 이스케이프
     */
    private String escapeCsv(String value) {
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
