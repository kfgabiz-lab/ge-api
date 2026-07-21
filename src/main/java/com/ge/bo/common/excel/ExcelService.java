package com.ge.bo.common.excel;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

/**
 * 공통 엑셀/CSV 파일 생성 서비스
 *
 * 사용법:
 *   // xlsx 생성
 *   byte[] bytes = excelService.buildXlsx(headers, keys, dateFormats, codeMaps, rows, "시트명");
 *
 *   // csv 생성 (UTF-8 BOM 포함 — 엑셀 한글 깨짐 방지)
 *   byte[] bytes = excelService.buildCsv(headers, keys, dateFormats, codeMaps, rows);
 *
 * 파라미터 공통:
 *   headers   — 컬럼 헤더 목록 (예: ["이름", "이메일", "상태"])
 *   keys      — data_json 키 목록 (헤더와 순서 동일, 예: ["name", "email", "status"])
 *   codeMaps  — 공통코드 라벨 매핑표 (key → {코드값: 라벨}, 없으면 빈 맵)
 *   rows      — 데이터 목록 (Map<키, 값>)
 */
@Service
public class ExcelService {

    /**
     * xlsx 파일 생성 (Apache POI XSSFWorkbook 사용)
     *
     * @param headers     컬럼 헤더 목록
     * @param keys        data_json 키 목록 (헤더와 순서 일치)
     * @param dateFormats 날짜 포맷 목록 (keys와 순서 일치, 빈 문자열이면 포맷 미적용)
     * @param codeMaps    공통코드 라벨 매핑표 (key → {코드값: 라벨}, FE가 다국어까지 반영해 생성, 없으면 빈 맵)
     * @param rows        데이터 목록
     * @param sheetName   시트명
     * @return xlsx 바이트 배열
     */
  public byte[] buildXlsx(List<String> headers, List<String> keys, List<String> dateFormats,
                             Map<String, Map<String, String>> codeMaps,
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
          String fmt   = (dateFormats != null && colIdx < dateFormats.size()) ? dateFormats.get(colIdx) : "";
          String text  = applyDateFormat(value, fmt);
          text = applyCodeMap(text, codeMaps != null ? codeMaps.get(keys.get(colIdx)) : null);
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
     * @param headers     컬럼 헤더 목록
     * @param keys        data_json 키 목록 (헤더와 순서 일치)
     * @param dateFormats 날짜 포맷 목록 (keys와 순서 일치, 빈 문자열이면 포맷 미적용)
     * @param codeMaps    공통코드 라벨 매핑표 (key → {코드값: 라벨}, FE가 다국어까지 반영해 생성, 없으면 빈 맵)
     * @param rows        데이터 목록
     * @return csv 바이트 배열 (BOM + UTF-8)
     */
  public byte[] buildCsv(List<String> headers, List<String> keys, List<String> dateFormats,
                            Map<String, Map<String, String>> codeMaps,
                            List<Map<String, Object>> rows) {
    StringBuilder sb = new StringBuilder();

        /* ── 헤더 행 ── */
    sb.append(String.join(",", headers.stream().map(this::escapeCsv).toList()));
    sb.append("\n");

        /* ── 데이터 행 ── */
    for (Map<String, Object> row : rows) {
      List<String> values = new java.util.ArrayList<>();
      for (int i = 0; i < keys.size(); i++) {
        Object value = getNestedValue(row, keys.get(i));
        String fmt   = (dateFormats != null && i < dateFormats.size()) ? dateFormats.get(i) : "";
        String text  = applyDateFormat(value, fmt);
        text = applyCodeMap(text, codeMaps != null ? codeMaps.get(keys.get(i)) : null);
        values.add(escapeCsv(text));
      }
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
     * 날짜 포맷 적용 — FE TableCellRenderer와 동일한 규칙
     *
     * 사용법:
     *   applyDateFormat("2024-03-15T09:30:00Z", "YYYY-MM-DD") → "2024-03-15"
     *   applyDateFormat("2024-03-15T09:30:00Z", "YYYY/MM/DD HH:mm") → "2024/03/15 09:30"
     *   applyDateFormat(value, "") → value.toString() (포맷 없음)
     *
     * @param value  원본 값 (null 허용)
     * @param format 날짜 포맷 문자열 (빈 문자열이면 원본값 반환)
     * @return 포맷 적용된 문자열
     */
  private String applyDateFormat(Object value, String format) {
    if (value == null) return "";
    String raw = value.toString();
    if (format == null || format.isBlank()) return raw;
    try {
            // ISO 8601 형식 파싱 (Instant 또는 LocalDateTime 모두 처리)
      ZonedDateTime zdt;
      if (raw.endsWith("Z") || raw.contains("+")) {
        zdt = Instant.parse(raw).atZone(ZoneId.systemDefault());
      } else {
        zdt = java.time.LocalDateTime.parse(raw).atZone(ZoneId.systemDefault());
      }
      String YYYY = String.format("%04d", zdt.getYear());
      String MM   = String.format("%02d", zdt.getMonthValue());
      String DD   = String.format("%02d", zdt.getDayOfMonth());
      String HH   = String.format("%02d", zdt.getHour());
      String mm   = String.format("%02d", zdt.getMinute());
      String ss   = String.format("%02d", zdt.getSecond());
      return format
                .replace("YYYY", YYYY).replace("MM", MM).replace("DD", DD)
                .replace("HH", HH).replace("mm", mm).replace("ss", ss);
    } catch (Exception e) {
            // 파싱 실패 시 원본값 그대로 반환
      return raw;
    }
  }

    /**
     * 공통코드 값을 라벨로 치환 (쉼표 구분 다중값 지원)
     * - FE가 이미 다국어(nameMsgKey) 반영해 만든 매핑표를 그대로 기계적으로 치환만 함
     * - 매핑표가 없거나 값이 매핑표에 없으면 원본 값 그대로 반환
     *
     * @param value   원본 값 (쉼표 구분 다중값 가능, 예: "Y,N")
     * @param codeMap 코드값 → 라벨 매핑 (해당 컬럼에 매핑표가 없으면 null)
     * @return 치환된 문자열
     */
  private String applyCodeMap(String value, Map<String, String> codeMap) {
    if (codeMap == null || codeMap.isEmpty() || value == null || value.isEmpty()) return value;
    String[] codes = value.split(",", -1);
    List<String> labels = new java.util.ArrayList<>();
    for (String code : codes) {
      String trimmed = code.trim();
      labels.add(codeMap.getOrDefault(trimmed, trimmed));
    }
    return String.join(",", labels);
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
     * ⚠️ _fetchedRel{id} 축약키 폴백 — FE 화면(flattenPageDataItem)은 "_fetchedRel8.currMgmtTitle"처럼
     * 중간 contentKey를 생략한 축약키도 보여주지만, 실제 저장 구조는 "_fetchedRel8.currMgmtForm.currMgmtTitle"로
     * 한 단계 더 깊다. 정확한 경로 탐색이 실패했고 최상위 키가 "_fetchedRel"로 시작하면, 그 섹션 안에서
     * 마지막 segment와 이름이 같은 leaf를 재귀 스캔해 유일하게(1곳만) 존재할 때만 반환한다 — 이렇게 하면
     * 빌더에서 전체 경로를 입력하든, 화면에 보이는 축약키를 그대로 입력하든 둘 다 정상 동작한다.
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
      Object value = getNestedValue((Map<String, Object>) nested, parts[1]);
      if (value != null) return value;
      if (parts[0].startsWith("_fetchedRel")) {
        String leaf = parts[1].contains(".") ? parts[1].substring(parts[1].lastIndexOf('.') + 1) : parts[1];
        return findUniqueFetchedRelLeaf((Map<String, Object>) nested, leaf);
      }
      return null;
    }
    return null;
  }

    /**
     * _fetchedRel{id} 섹션 내부를 재귀 스캔해 leaf 키 이름과 일치하는 값을 찾되,
     * 같은 이름이 여러 경로에 있으면 모호하므로 유일하게(1곳만) 존재할 때만 반환한다.
     */
  private Object findUniqueFetchedRelLeaf(Map<String, Object> map, String leaf) {
    List<Object> matches = new java.util.ArrayList<>();
    collectLeafMatches(map, leaf, matches);
    return matches.size() == 1 ? matches.get(0) : null;
  }

  @SuppressWarnings("unchecked")
  private void collectLeafMatches(Map<String, Object> map, String leaf, List<Object> matches) {
    for (Map.Entry<String, Object> entry : map.entrySet()) {
      if (entry.getKey().equals(leaf)) {
        matches.add(entry.getValue());
      }
      if (entry.getValue() instanceof Map<?, ?>) {
        collectLeafMatches((Map<String, Object>) entry.getValue(), leaf, matches);
      }
    }
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
