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

            /* ── 컬럼별 최대 표시 너비 추적 (헤더 기준 초기화) ── */
      int[] colMaxLen = new int[headers.size()];

            /* ── 헤더 행 작성 ── */
      Row headerRow = sheet.createRow(0);
      for (int i = 0; i < headers.size(); i++) {
        Cell cell = headerRow.createCell(i);
        cell.setCellValue(headers.get(i));
        cell.setCellStyle(headerStyle);
        colMaxLen[i] = measureLen(headers.get(i));
      }

            /* ── 데이터 행 작성 + 최대 너비 추적 ── */
      for (int rowIdx = 0; rowIdx < rows.size(); rowIdx++) {
        Row dataRow = sheet.createRow(rowIdx + 1);
        Map<String, Object> rowData = rows.get(rowIdx);
        for (int colIdx = 0; colIdx < keys.size(); colIdx++) {
          Object value = getNestedValue(rowData, keys.get(colIdx));
          String text = value != null ? value.toString() : "";
          Cell cell = dataRow.createCell(colIdx);
          cell.setCellValue(text);
          if (colIdx < colMaxLen.length) {
            colMaxLen[colIdx] = Math.max(colMaxLen[colIdx], measureLen(text));
          }
        }
      }

            /* ── 컬럼 너비 적용 (최소 12 ~ 최대 50자, 여유 2자, 단위 256) ── */
      for (int i = 0; i < headers.size(); i++) {
        sheet.setColumnWidth(i, Math.min(Math.max(colMaxLen[i] + 2, 12), 50) * 256);
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
                      Object value = getNestedValue(row, key);
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
     * 문자열 표시 너비 측정 (한글=2, 영문·숫자=1)
     * - 한글 완성형(AC00~D7A3), 자모(1100~11FF, 3130~318F) 2자 처리
     *
     * @param text 측정할 문자열
     * @return 표시 너비
     */
  private int measureLen(String text) {
    if (text == null || text.isEmpty()) return 0;
    int len = 0;
    for (char c : text.toCharArray()) {
      boolean isKorean = (c >= 0xAC00 && c <= 0xD7A3)
                      || (c >= 0x1100 && c <= 0x11FF)
                      || (c >= 0x3130 && c <= 0x318F);
      len += isKorean ? 2 : 1;
    }
    return len;
  }

    /**
     * 도트 표기식 키로 중첩 Map에서 값을 재귀 탐색
     *
     * 사용법:
     *   getNestedValue(row, "제목")           → row.get("제목")           [1단계]
     *   getNestedValue(row, "form1.제목")     → row→"form1"→"제목"        [2단계]
     *   getNestedValue(row, "tab1.form1.제목") → row→"tab1"→"form1"→"제목" [3단계]
     *
     * @param map 현재 탐색 중인 Map
     * @param key 단순 키 또는 도트 표기식 키 (예: "tab1.form1.제목")
     * @return 찾은 값, 없으면 null
     */
  @SuppressWarnings("unchecked")
  private Object getNestedValue(Map<String, Object> map, String key) {
    if (!key.contains(".")) {
      return map.get(key);
    }
    String[] parts = key.split("\\.", 2);
    Object nested = map.get(parts[0]);
    if (nested instanceof Map<?, ?>) {
      return getNestedValue((Map<String, Object>) nested, parts[1]);
    }
    return null;
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
