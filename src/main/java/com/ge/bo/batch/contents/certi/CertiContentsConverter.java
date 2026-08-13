package com.ge.bo.batch.contents.certi;

import com.ge.bo.batch.contents.CategoryItem;
import com.ge.bo.batch.contents.ContentDocument;
import com.ge.bo.batch.contents.ContentsNormalizer;
import com.ge.bo.batch.contents.ConversionResult;
import com.ge.bo.batch.contents.FileItem;
import com.ge.bo.batch.contents.RowFailure;
import com.ge.bo.batch.contents.SourceSystem;
import com.ge.bo.batch.contents.VersionItem;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * IF_R_CERTI_MASTER(인증서 IF) → ContentDocument 변환
 * (NAHP_IF_콘텐츠테이블_매핑정의서_v0.7 06_CERTI_MASTER 시트 기준)
 *
 * CERTI는 최근 변경분만 보내는 델타 방식. CERTI_STATUS='7'(규격폐기)이 CATALOG의 USE_YN='D', SSQ의
 * delete_yn='Y'와 동급인 명시적 삭제 신호로, explicitDelete=true → contents_master.is_deleted=true 처리된다.
 * 버전 개념이 없어 DEFAULT 버전 1건, 파일은 LAST_CERTI_FILE이 있을 때만 1건 생성한다.
 *
 * ※ 명세서 충돌: 테이블 명세서는 contents_master.nahp_title에도 CERTI의 NAHP_TITLE을 채우라고 하지만,
 *   IF 매핑정의서는 NAHP_TITLE의 Target을 doc_title로만 명시한다. 데이터 유실 없이 안전한 쪽으로
 *   두 값 모두에 동일하게 채운다(doc_title=nahp_title=NAHP_TITLE) — 최종 확정 필요.
 */
@Component
public class CertiContentsConverter {

    // 문서 유형 코드 개편(2026-07-16) — 인증서는 기존 T가 아니라 R
    private static final String DOC_TYPE = "R";
    private static final String DEFAULT_KEY = "DEFAULT";
    private static final String SOURCE_TABLE = "if_r_certi_master";

    public ConversionResult convert(String certiNo, String bi, List<CertiRow> rows) {
        List<RowFailure> rowFailures = new ArrayList<>();
        String sourceDocKey = certiNo + "|" + bi;

        CertiRow first = rows.get(0);
        for (CertiRow row : rows) {
            boolean conflict = !eq(first.nahpTitle(), row.nahpTitle())
                || !eq(first.nahpLang(), row.nahpLang())
                || !Objects.equals(first.updateDate(), row.updateDate())
                || !eq(first.certiType(), row.certiType())
                || !eq(first.certiStatus(), row.certiStatus())
                || !eq(first.cportalDispFlag(), row.cportalDispFlag())
                || !eq(first.lastCertiFile(), row.lastCertiFile());
            if (conflict) {
                RowFailure failure = new RowFailure(SOURCE_TABLE, "certi_no=" + certiNo + ",bi=" + bi, "VALUE_CONFLICT",
                    "같은 인증서(CERTI_NO+BI)의 반복 행 사이에 문서 레벨 값(NAHP_TITLE/NAHP_LANG/UPDATE_DATE/"
                        + "CERTI_TYPE/CERTI_STATUS/CPORTAL_DISP_FLAG/LAST_CERTI_FILE)이 서로 다름", rawRow(row));
                return new ConversionResult(null, List.of(failure), List.of());
            }
        }

        String title = ContentsNormalizer.trimToNull(first.nahpTitle());
        OffsetDateTime sourceUpdatedAt = first.updateDate() != null
            ? ContentsNormalizer.koreaTimeToUtc(first.updateDate().atStartOfDay()) : null;
        // CERTI_STATUS='7'(규격폐기) — CATALOG USE_YN='D', SSQ delete_yn='Y'와 동급인 명시적 삭제 신호로 취급한다.
        boolean explicitDelete = "7".equals(ContentsNormalizer.trimToNull(first.certiStatus()));
        boolean expose = !explicitDelete;

        // 카테고리 — 행마다 1건, source_path 기준 dedupe(행 단위 격리)
        Map<String, CategoryItem> categoriesByPath = new LinkedHashMap<>();
        for (CertiRow row : rows) {
            String sourcePath = ContentsNormalizer.buildCategoryPath("|",
                row.nahpLevel1Id(), row.nahpLevel2Id(), row.nahpLevel3Id());
            if (sourcePath == null) {
                rowFailures.add(new RowFailure(SOURCE_TABLE, rowKey(certiNo, bi, row.nahpLevelSeq()), "NULL_KEY",
                    "카테고리 레벨(NAHP_LEVEL1_ID~NAHP_LEVEL3_ID)이 전부 비어있음", rawRow(row)));
                continue;
            }
            String nahpCategoryId = ContentsNormalizer.firstNonBlank(
                row.nahpLevel3Id(), row.nahpLevel2Id(), row.nahpLevel1Id());
            boolean displayFlag;
            try {
                String flag = ContentsNormalizer.trimToNull(row.nahpDispFlag());
                displayFlag = flag == null || ContentsNormalizer.parseStrictBoolean(flag);
            } catch (IllegalArgumentException e) {
                rowFailures.add(new RowFailure(SOURCE_TABLE, rowKey(certiNo, bi, row.nahpLevelSeq()),
                    "VALUE_CONFLICT", "NAHP_DISP_FLAG 값 해석 불가: " + e.getMessage(), rawRow(row)));
                continue;
            }

            CategoryItem candidate = CategoryItem.builder()
                .sourcePath(sourcePath).nahpCategoryId(nahpCategoryId)
                .categoryL1Id(ContentsNormalizer.trimToNull(row.nahpLevel1Id()))
                .categoryL2Id(ContentsNormalizer.trimToNull(row.nahpLevel2Id()))
                .categoryL3Id(ContentsNormalizer.trimToNull(row.nahpLevel3Id()))
                .nahpLevelSeq(row.nahpLevelSeq() != null ? row.nahpLevelSeq().intValue() : null)
                .nahpDisplayFlag(displayFlag).build();

            CategoryItem existing = categoriesByPath.get(sourcePath);
            if (existing != null && (existing.isNahpDisplayFlag() != candidate.isNahpDisplayFlag()
                || !eq(existing.getNahpCategoryId(), candidate.getNahpCategoryId()))) {
                rowFailures.add(new RowFailure(SOURCE_TABLE, rowKey(certiNo, bi, row.nahpLevelSeq()),
                    "VALUE_CONFLICT", "동일 source_path에 서로 다른 카테고리 값이 반복 수신됨: " + sourcePath, rawRow(row)));
                continue;
            }
            categoriesByPath.putIfAbsent(sourcePath, candidate);
        }

        // 파일 — LAST_CERTI_FILE이 있을 때만 1건. 버전은 구조 통일을 위한 DEFAULT 1행
        List<FileItem> files = new ArrayList<>();
        String lastCertiFile = ContentsNormalizer.trimToNull(first.lastCertiFile());
        if (lastCertiFile != null) {
            String fileName = ContentsNormalizer.extractFileNameFromPath(lastCertiFile);
            if (fileName == null) {
                rowFailures.add(new RowFailure(SOURCE_TABLE, "certi_no=" + certiNo + ",bi=" + bi, "NULL_KEY",
                    "LAST_CERTI_FILE에서 파일명을 추출할 수 없음: '" + lastCertiFile + "'", rawRow(first)));
            } else {
                files.add(FileItem.builder()
                    .sourceFileKey(DEFAULT_KEY)
                    .fileName(fileName)
                    .fileExt(ContentsNormalizer.extractExtension(fileName))
                    .fileSize(null)
                    // if_r_certi_master에는 파일 단위 언어 컬럼이 없어, 문서 레벨(NAHP_LANG)의 언어를 그대로 사용한다.
                    .fileLang(ContentsNormalizer.normalizeLang(first.nahpLang()))
                    .sourceFilePath(lastCertiFile)
                    .attrs(Map.of())
                    .fileExpose(true)
                    .sourceUpdatedAt(null)
                    .build());
            }
        }

        VersionItem version = VersionItem.builder()
            .sourceVersionKey(DEFAULT_KEY)
            .versionName(null)
            .versionDesc(null)
            .videoUrl(null)
            .versionExpose(true)
            .sortKey(0)
            .sourceUpdatedAt(null)
            .files(files)
            .build();

        Map<String, Object> attrs = buildAttrs(first);

        ContentDocument document = ContentDocument.builder()
            .sourceSystem(SourceSystem.CERTI)
            .sourceDocKey(sourceDocKey)
            .docType(DOC_TYPE)
            .docTitle(title)
            .nahpTitle(title)
            .nahpLang(ContentsNormalizer.normalizeLang(first.nahpLang()))
            .expose(expose)
            .attrs(attrs)
            .sourceCreatedAt(null)
            .sourceUpdatedAt(sourceUpdatedAt)
            .explicitDelete(explicitDelete)
            .categories(new ArrayList<>(categoriesByPath.values()))
            .versions(List.of(version))
            .build();

        // 명시적 삭제(CERTI_STATUS='7') 인증서는 카테고리·파일이 원래 비어서 올 수 있어 완결성 검사 대상에서
        // 제외한다(CATALOG/SSQ와 동일 원칙) — 그래야 규격폐기 신호가 EMPTY_CATEGORY/EMPTY_CONTENT로 막히지
        // 않고 문서가 정상 upsert되어 is_deleted=true가 실제로 반영된다.
        // rowFailures에 직접 추가하는 이유: 결과를 통째로 새로 만들면 위에서 이미 쌓인 개별 실패 사유
        // (예: LAST_CERTI_FILE 파일명 추출 실패)가 사라지고 이 요약 사유 하나만 남는 문제가 있었다.
        // 이미 개별 사유가 있으면(예: NULL_KEY로 파일명 추출 실패가 기록됨) 중복되는 요약 사유는 생략한다.
        if (!explicitDelete && categoriesByPath.isEmpty()) {
            if (rowFailures.isEmpty()) {
                rowFailures.add(new RowFailure(SOURCE_TABLE, sourceDocKey, "EMPTY_CATEGORY",
                    "카테고리(NAHP_LEVEL1_ID~NAHP_LEVEL3_ID) 등록이 0건인 인증서", rawRow(first)));
            }
            return new ConversionResult(null, rowFailures, List.of());
        }
        if (!explicitDelete && files.isEmpty()) {
            if (rowFailures.isEmpty()) {
                rowFailures.add(new RowFailure(SOURCE_TABLE, sourceDocKey, "EMPTY_CONTENT",
                    "첨부 파일이 없는 인증서(LAST_CERTI_FILE 없음)", rawRow(first)));
            }
            return new ConversionResult(null, rowFailures, List.of());
        }

        return new ConversionResult(document, rowFailures, List.of());
    }

    private Map<String, Object> buildAttrs(CertiRow row) {
        Map<String, Object> attrs = new LinkedHashMap<>();
        putIfPresent(attrs, "certi_type", row.certiType());
        putIfPresent(attrs, "certi_type_name", row.certiTypeName());
        putIfPresent(attrs, "certi_org", row.certiOrg());
        putIfPresent(attrs, "certi_org_name", row.certiOrgName());
        putDateIfPresent(attrs, "begin_date", row.certiBeginDate());
        putDateIfPresent(attrs, "renewal_date", row.lastCertiRenewalDate());
        putDateIfPresent(attrs, "exp_date", row.lastCertiExpDate());
        putDateIfPresent(attrs, "disuse_date", row.certiDisuseDate());
        putIfPresent(attrs, "certi_acq_no", row.lastCertiAcqNo());
        putIfPresent(attrs, "status", row.certiStatus());
        putIfPresent(attrs, "plant", row.plant());
        putIfPresent(attrs, "plant_name", row.plantName());
        putIfPresent(attrs, "pdt_bigclass", row.pdtBigclass());
        putIfPresent(attrs, "pdt_bigclass_name", row.pdtBigclassName());
        putIfPresent(attrs, "pdt_middleclass", row.pdtMiddleclass());
        putIfPresent(attrs, "pdt_middleclass_name", row.pdtMiddleclassName());
        putIfPresent(attrs, "pdt_series", row.pdtSeries());
        putIfPresent(attrs, "pdt_series_name", row.pdtSeriesName());
        putIfPresent(attrs, "pdt_name", row.pdtName());
        return attrs;
    }

    private void putIfPresent(Map<String, Object> map, String key, String value) {
        String v = ContentsNormalizer.trimToNull(value);
        if (v != null) {
            map.put(key, v);
        }
    }

    private void putDateIfPresent(Map<String, Object> map, String key, LocalDate value) {
        if (value != null) {
            map.put(key, value.toString());
        }
    }

    private boolean eq(String a, String b) {
        return Objects.equals(a, b);
    }

    private String rowKey(String certiNo, String bi, Short levelSeq) {
        return "certi_no=" + certiNo + ", bi=" + bi + ", nahp_level_seq=" + levelSeq;
    }

    private Map<String, Object> rawRow(CertiRow row) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("certi_no", row.certiNo());
        map.put("bi", row.bi());
        map.put("nahp_level_seq", row.nahpLevelSeq());
        map.put("nahp_title", row.nahpTitle());
        map.put("certi_type", row.certiType());
        map.put("certi_status", row.certiStatus());
        return map;
    }
}
