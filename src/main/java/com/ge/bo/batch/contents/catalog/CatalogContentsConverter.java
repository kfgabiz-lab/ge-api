package com.ge.bo.batch.contents.catalog;

import com.ge.bo.batch.contents.CategoryItem;
import com.ge.bo.batch.contents.ContentDocument;
import com.ge.bo.batch.contents.ContentsNormalizer;
import com.ge.bo.batch.contents.ConversionResult;
import com.ge.bo.batch.contents.FileItem;
import com.ge.bo.batch.contents.RowFailure;
import com.ge.bo.batch.contents.SourceSystem;
import com.ge.bo.batch.contents.VersionItem;
import com.ge.bo.entity.IfCatalogFileInfo;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * IF_R_CATALOG_INFO(헤더) + IF_R_CATALOG_FILE_INFO(파일) → ContentDocument 변환
 * (NAHP_IF_콘텐츠테이블_매핑정의서_v0.7 02_CATALOG_INFO / 03_CATALOG_FILE 시트 기준)
 *
 * 문서 가드: 헤더 행들 사이에 값 충돌(제목/타입/노출/버전 등)이 있으면 문서 전체를 만들 수 없으므로 document=null로
 *           반환한다(FAILED). 카테고리·파일 개별 행 문제는 그 행만 건너뛰고 문서는 계속 만든다(PARTIAL_ERROR).
 */
@Component
public class CatalogContentsConverter {

    // 문서 유형 코드 — C(카탈로그)/M(매뉴얼)/D(CAD)/S(소프트웨어)/V(교육영상)/T(기술자료).
    // Z는 원천이 실제로 보내는 값이 아니라, 이 중 어디에도 없는 미확인 DATA_CODE를 받았을 때 우리가 붙이는
    // 내부 fallback 라벨이다 — 예전엔 목록에 없으면 문서 전체를 거부했는데, 새로운 유형이 추가될 때마다
    // 매핑 갱신 전까지 문서가 통째로 유실되는 문제가 있어 Z로 받아들이고 문서는 정상 생성하도록 바꿨다.
    private static final Set<String> KNOWN_DOC_TYPES = Set.of("C", "M", "D", "S", "V", "T");
    private static final String FALLBACK_DOC_TYPE = "Z";
    private static final String VIDEO_DATA_CODE = "V";
    private static final String SOURCE_TABLE_INFO = "if_r_catalog_info";
    private static final String SOURCE_TABLE_FILE = "if_r_catalog_file_info";

    /**
     * @param ctlgCode  문서 자연키(CTLG_CODE)
     * @param headerRows 이 ctlgCode에 속한 if_r_catalog_info 행(카테고리 등록 수만큼, 1건 이상)
     * @param fileRows  이 ctlgCode에 속한 if_r_catalog_file_info 행(0건 이상 — 헤더만 오고 파일이 아직 없을 수 있음)
     */
    public ConversionResult convert(String ctlgCode, List<CatalogHeaderRow> headerRows, List<IfCatalogFileInfo> fileRows) {
        List<RowFailure> rowFailures = new ArrayList<>();

        CatalogHeaderRow first = headerRows.get(0);
        String docTitle = ContentsNormalizer.trimToNull(first.ctlgName());
        String dataCode = ContentsNormalizer.trimToNull(first.dataCode());
        String useYn = ContentsNormalizer.trimToNull(first.useYn());
        String prtYymm = ContentsNormalizer.trimToNull(first.prtYymm());
        String prtVer = ContentsNormalizer.trimToNull(first.prtVer());
        String ctpDispYn = ContentsNormalizer.trimToNull(first.ctpDispYn());

        // 문서 가드: 헤더 반복 행 사이의 문서 레벨 값 충돌 검증 — 하나라도 다르면 문서 전체를 만들 수 없음
        for (CatalogHeaderRow row : headerRows) {
            boolean conflict = !eq(docTitle, ContentsNormalizer.trimToNull(row.ctlgName()))
                || !eq(dataCode, ContentsNormalizer.trimToNull(row.dataCode()))
                || !eq(useYn, ContentsNormalizer.trimToNull(row.useYn()))
                || !eq(prtYymm, ContentsNormalizer.trimToNull(row.prtYymm()))
                || !eq(prtVer, ContentsNormalizer.trimToNull(row.prtVer()))
                || !eq(ctpDispYn, ContentsNormalizer.trimToNull(row.ctpDispYn()));
            if (conflict) {
                return documentLevelFailure(ctlgCode, "VALUE_CONFLICT",
                    "같은 CTLG_CODE의 헤더 행 사이에 문서 레벨 값(CTLG_NAME/DATA_CODE/USE_YN/PRT_YYMM/PRT_VER/CTP_DISP_YN)이 서로 다름", rawRow(row));
            }
        }

        if (dataCode == null) {
            return documentLevelFailure(ctlgCode, "UNKNOWN_DOC_TYPE", "DATA_CODE가 비어있음", rawRow(first));
        }
        // 미확인 DATA_CODE는 Z로 받아들이되, 원본 값은 따로 보존해둔다 — 파일 쪽 DATA_CODE와 비교할 때 둘 다
        // 그냥 "Z"로만 비교하면 서로 다른 미확인 코드끼리도 같은 값으로 오인해 불일치를 못 잡아낸다.
        String rawDataCode = dataCode;
        if (!KNOWN_DOC_TYPES.contains(dataCode)) {
            dataCode = FALLBACK_DOC_TYPE;
        }

        boolean expose;
        boolean explicitDelete;
        if ("Y".equalsIgnoreCase(useYn)) {
            expose = true;
            explicitDelete = false;
        } else if ("N".equalsIgnoreCase(useYn)) {
            expose = false;
            explicitDelete = false;
        } else if ("D".equalsIgnoreCase(useYn)) {
            expose = false;
            explicitDelete = true;
        } else {
            return documentLevelFailure(ctlgCode, "UNKNOWN_USE_YN", "정의되지 않은 USE_YN: '" + useYn + "'", rawRow(first));
        }
        // 카테고리 — 헤더 행마다 1건, source_path 기준 dedupe
        Map<String, CategoryItem> categoriesByPath = new LinkedHashMap<>();
        for (CatalogHeaderRow row : headerRows) {
            String sourcePath = ContentsNormalizer.buildCategoryPath("|",
                row.nahpLevel1Id(), row.nahpLevel2Id(), row.nahpLevel3Id());
            if (sourcePath == null) {
                rowFailures.add(new RowFailure(SOURCE_TABLE_INFO, rowKey(ctlgCode, row.nahpLevelSeq()),
                    "NULL_KEY", "카테고리 레벨 ID(NAHP_LEVEL1_ID~NAHP_LEVEL3_ID)가 전부 비어있음", rawRow(row)));
                continue;
            }
            String nahpCategoryId = ContentsNormalizer.firstNonBlank(
                row.nahpLevel3Id(), row.nahpLevel2Id(), row.nahpLevel1Id());
            boolean displayFlag;
            try {
                String dispYn = ContentsNormalizer.trimToNull(row.nahpDispYn());
                displayFlag = dispYn == null || ContentsNormalizer.parseStrictBoolean(dispYn);
            } catch (IllegalArgumentException e) {
                rowFailures.add(new RowFailure(SOURCE_TABLE_INFO, rowKey(ctlgCode, row.nahpLevelSeq()),
                    "VALUE_CONFLICT", "NAHP_DISP_YN 값 해석 불가: " + e.getMessage(), rawRow(row)));
                continue;
            }

            CategoryItem candidate = CategoryItem.builder()
                .sourcePath(sourcePath)
                .nahpCategoryId(nahpCategoryId)
                .categoryL1Id(ContentsNormalizer.trimToNull(row.nahpLevel1Id()))
                .categoryL2Id(ContentsNormalizer.trimToNull(row.nahpLevel2Id()))
                .categoryL3Id(ContentsNormalizer.trimToNull(row.nahpLevel3Id()))
                .nahpLevelSeq(row.nahpLevelSeq() != null ? row.nahpLevelSeq().intValue() : null)
                .nahpDisplayFlag(displayFlag)
                .build();

            CategoryItem existing = categoriesByPath.get(sourcePath);
            if (existing != null && (existing.isNahpDisplayFlag() != candidate.isNahpDisplayFlag()
                || !eq(existing.getNahpCategoryId(), candidate.getNahpCategoryId()))) {
                rowFailures.add(new RowFailure(SOURCE_TABLE_INFO, rowKey(ctlgCode, row.nahpLevelSeq()),
                    "VALUE_CONFLICT", "동일 source_path에 서로 다른 카테고리 값이 반복 수신됨: " + sourcePath, rawRow(row)));
                continue;
            }
            categoriesByPath.putIfAbsent(sourcePath, candidate);
        }

        // 버전 — CTLG_CODE 단위 1행 (PRT_YYMM + PRT_VER 조합)
        String versionKey = (prtYymm == null && prtVer == null) ? "DEFAULT"
            : (prtYymm == null ? "" : prtYymm) + "|" + (prtVer == null ? "" : prtVer);
        String versionName = (prtYymm == null && prtVer == null) ? null
            : ((prtYymm == null ? "" : prtYymm) + (prtVer == null ? "" : " v" + prtVer)).trim();

        // 파일 — CTLG_CODE 안에서 DATA_CODE|FILE_SEQ 기준 dedupe. 비디오는 contents_file이 아닌 version.videoUrl로
        Map<String, FileItem> filesByKey = new LinkedHashMap<>();
        String videoUrl = null;
        for (IfCatalogFileInfo fileRow : fileRows) {
            String rowKey = ctlgCode + "/" + fileRow.getDataCode();
            String fileDataCode = ContentsNormalizer.trimToNull(fileRow.getDataCode());
            String resolvedFileDataCode = fileDataCode != null && !KNOWN_DOC_TYPES.contains(fileDataCode)
                ? FALLBACK_DOC_TYPE : fileDataCode;
            // 헤더가 Z로 폴백된 경우 파일 쪽도 원본 문자열이 헤더 원본과 같아야 진짜 매칭으로 본다 — 둘 다
            // "미확인"이라고 무조건 같다고 보면 서로 다른 미확인 코드끼리(예: 헤더 'X', 파일 'Y')도 같은 문서로
            // 섞여 불일치를 못 잡아낸다.
            boolean mismatch = FALLBACK_DOC_TYPE.equals(dataCode)
                ? !eq(fileDataCode, rawDataCode)
                : !eq(resolvedFileDataCode, dataCode);
            if (mismatch) {
                rowFailures.add(new RowFailure(SOURCE_TABLE_FILE, rowKey, "VALUE_CONFLICT",
                    "파일 IF의 DATA_CODE(" + fileDataCode + ")가 문서 DATA_CODE(" + dataCode + ")와 다름", rawRow(fileRow)));
                continue;
            }
            if (fileRow.getFileSeq() == null) {
                rowFailures.add(new RowFailure(SOURCE_TABLE_FILE, rowKey, "NULL_KEY",
                    "FILE_SEQ가 NULL이라 source_file_key(DATA_CODE|FILE_SEQ 조합)를 만들 수 없음", rawRow(fileRow)));
                continue;
            }
            String sourceFileKey = fileDataCode + "|" + fileRow.getFileSeq();

            if (VIDEO_DATA_CODE.equals(dataCode)) {
                String url = ContentsNormalizer.trimToNull(fileRow.getFileSrc());
                if (url == null || !(url.startsWith("http://") || url.startsWith("https://"))) {
                    rowFailures.add(new RowFailure(SOURCE_TABLE_FILE, rowKey, "PARSE_URL",
                        "영상 URL 형식이 아님(FILE_SRC): '" + url + "'", rawRow(fileRow)));
                    continue;
                }
                videoUrl = url;
                continue;
            }

            // FILE_NAME은 시스템이 붙인 타임스탬프 접두어를 포함한 이름(예: "220822162717_[Susol LV MDB]_..pdf")이고,
            // FILE_ORI가 사람이 보는 원본 파일명(예: "[Susol LV MDB]_..pdf")이다.
            // blob 경로(file_path)는 문서 간 충돌을 피하기 위해 FILE_NAME 그대로 쓰고, 화면 노출용 파일명만
            // FILE_ORI를 우선 사용한다(없으면 FILE_NAME).
            String pathFileName = ContentsNormalizer.trimToNull(fileRow.getFileName());
            if (pathFileName == null) {
                rowFailures.add(new RowFailure(SOURCE_TABLE_FILE, rowKey, "NULL_KEY",
                    "FILE_NAME이 비어있어 파일 경로를 결정할 수 없음", rawRow(fileRow)));
                continue;
            }
            String fileName = ContentsNormalizer.firstNonBlank(fileRow.getFileOri(), pathFileName);
            // FILE_SIZE는 부가 메타데이터라 값이 이상해도 파일 자체(다운로드 가능 여부)는 살리고 사이즈만
            // NULL 처리한다 — 대신 실패 기록은 남겨서 원천 데이터 이상은 계속 추적 가능하게 한다.
            Long fileSize = null;
            String rawSize = ContentsNormalizer.trimToNull(fileRow.getFileSize());
            if (rawSize != null) {
                try {
                    long parsed = Long.parseLong(rawSize);
                    if (parsed < 0) {
                        rowFailures.add(new RowFailure(SOURCE_TABLE_FILE, rowKey, "VALUE_CONFLICT",
                            "음수 FILE_SIZE 수신 — NULL 처리: '" + rawSize + "'", rawRow(fileRow)));
                    } else {
                        fileSize = parsed;
                    }
                } catch (NumberFormatException e) {
                    rowFailures.add(new RowFailure(SOURCE_TABLE_FILE, rowKey, "VALUE_CONFLICT",
                        "FILE_SIZE 숫자 변환 실패 — NULL 처리: '" + rawSize + "'", rawRow(fileRow)));
                }
            }

            FileItem candidate = FileItem.builder()
                .sourceFileKey(sourceFileKey)
                .fileName(fileName)
                .pathFileName(pathFileName)
                .fileExt(ContentsNormalizer.extractExtension(pathFileName))
                .fileSize(fileSize)
                .sourceFilePath(ContentsNormalizer.trimToNull(fileRow.getFileSrc()))
                // if_r_catalog_file_info 실물에 USE_YN 컬럼이 없음(헤더에서만 관리) — 헤더 USE_YN 기준 노출값을 그대로 사용
                .fileExpose(expose)
                // if_r_catalog_file_info에는 파일 단위 언어 컬럼이 없어, 문서 헤더(NAHP_LANG)의 언어를 그대로 사용한다.
                .fileLang(ContentsNormalizer.normalizeLang(first.nahpLang()))
                .sourceUpdatedAt(ContentsNormalizer.koreaTimeToUtc(fileRow.getCreatedDate()))
                .build();

            FileItem existing = filesByKey.get(sourceFileKey);
            if (existing != null && (!eq(existing.getFileName(), candidate.getFileName())
                || !eq(existing.getSourceFilePath(), candidate.getSourceFilePath()))) {
                rowFailures.add(new RowFailure(SOURCE_TABLE_FILE, rowKey, "VALUE_CONFLICT",
                    "동일 source_file_key에 서로 다른 파일 정보(FILE_NAME/FILE_SRC)가 반복 수신됨: " + sourceFileKey,
                    rawRow(fileRow)));
                continue;
            }
            filesByKey.putIfAbsent(sourceFileKey, candidate);
        }

        // 파일 삭제 대상 체크 기준표 — CTP_LINK_YN='N' and NAHP_LINK_YN='N'이면 비노출(실삭제 아님).
        // 실물 컬럼은 ctp_disp_yn(문서 레벨)/nahp_disp_yn(카테고리별)으로 대응된다고 보고, NAHP_LINK_YN 쪽은
        // "이 문서의 모든 카테고리가 비노출(N)"일 때로 판단한다(카테고리가 하나도 없으면 이 조건은 적용하지 않음
        // — 그 경우는 "카테고리 등록 0건" 리포트로 별도 다룬다).
        boolean allCategoriesHidden = !categoriesByPath.isEmpty()
            && categoriesByPath.values().stream().noneMatch(CategoryItem::isNahpDisplayFlag);
        if ("N".equalsIgnoreCase(ctpDispYn) && allCategoriesHidden) {
            expose = false;
        }

        Map<String, Object> attrs = new LinkedHashMap<>();
        if (first.nahpVideoProdStandard() != null) {
            attrs.put("video_prod_standard", first.nahpVideoProdStandard());
        }

        VersionItem version = VersionItem.builder()
            .sourceVersionKey(versionKey)
            .versionName(versionName)
            .versionDesc(null)
            .videoUrl(videoUrl)
            .versionExpose(true)
            .sortKey(0)
            .sourceUpdatedAt(null) // 소스에 버전 단위 수정일시 컬럼이 없음
            .files(new ArrayList<>(filesByKey.values()))
            .build();

        ContentDocument document = ContentDocument.builder()
            .sourceSystem(SourceSystem.CATALOG)
            .sourceDocKey(ctlgCode)
            .docType(dataCode)
            .docTitle(docTitle)
            .nahpTitle(ContentsNormalizer.trimToNull(first.nahpTitle()))
            .nahpLang(ContentsNormalizer.normalizeLang(first.nahpLang()))
            .expose(expose)
            .attrs(attrs)
            .sourceCreatedAt(null)
            .sourceUpdatedAt(ContentsNormalizer.koreaTimeToUtc(first.updatedDate()))
            .explicitDelete(explicitDelete)
            .categories(new ArrayList<>(categoriesByPath.values()))
            .versions(List.of(version))
            .build();

        // 카테고리가 0건이어도 문서(마스터+파일) 자체는 유효한 콘텐츠라 문서 생성은 막지 않는다 — 카테고리
        // 테이블만 비어있게 되고, EMPTY_CATEGORY는 문서를 막지 않는 행 수준 실패로 기록해 부분성공(P) 처리한다.
        // 카테고리 행 각각의 개별 사유(SOURCE_TABLE_INFO)가 이미 기록돼 있으면 그걸로 충분히 설명되니
        // 중복되는 요약 사유는 덧붙이지 않는다 — EMPTY_CONTENT(SOURCE_TABLE_FILE) 사유와는 무관하게 독립 판단.
        boolean hasCategoryRowFailure = rowFailures.stream().anyMatch(f -> SOURCE_TABLE_INFO.equals(f.sourceTable()));
        if (!explicitDelete && categoriesByPath.isEmpty() && !hasCategoryRowFailure) {
            rowFailures.add(new RowFailure(SOURCE_TABLE_INFO, "ctlg_code=" + ctlgCode, "EMPTY_CATEGORY",
                "카테고리(NAHP_LEVEL1_ID~NAHP_LEVEL3_ID) 등록이 0건인 문서 — 카테고리 없이 문서만 생성됨", rawRow(first)));
        }

        // 명시적 삭제(USE_YN='D') 문서는 파일이 원래 비어서 올 수 있어 완결성 검사 대상에서 제외한다.
        // documentLevelFailure()가 아니라 rowFailures에 직접 추가하는 이유: 그 헬퍼는 결과를 통째로 새로
        // 만들어서, 파일 반복문에서 이미 쌓아둔 개별 실패 사유(왜 파일이 하나도 안 남았는지)가
        // 사라지고 이 요약 사유 하나만 남는 문제가 있었다. 위 EMPTY_CATEGORY와 독립적으로 파일 쪽 개별
        // 사유(SOURCE_TABLE_FILE)만 확인한다.
        if (!explicitDelete && filesByKey.isEmpty() && videoUrl == null) {
            boolean hasFileRowFailure = rowFailures.stream().anyMatch(f -> SOURCE_TABLE_FILE.equals(f.sourceTable()));
            if (!hasFileRowFailure) {
                rowFailures.add(new RowFailure(SOURCE_TABLE_FILE, "ctlg_code=" + ctlgCode, "EMPTY_CONTENT",
                    "노출 파일(if_r_catalog_file_info)·영상(FILE_SRC) URL이 모두 0건인 문서(빈 콘텐츠)", rawRow(first)));
            }
            return new ConversionResult(null, rowFailures, List.of());
        }

        return new ConversionResult(document, rowFailures, List.of());
    }

    private ConversionResult documentLevelFailure(String ctlgCode, String failCode, String detail, Map<String, Object> raw) {
        RowFailure failure = new RowFailure(SOURCE_TABLE_INFO, "ctlg_code=" + ctlgCode, failCode, detail, raw);
        return new ConversionResult(null, List.of(failure), List.of());
    }

    private boolean eq(String a, String b) {
        return java.util.Objects.equals(a, b);
    }

    private String rowKey(String ctlgCode, Short levelSeq) {
        return "ctlg_code=" + ctlgCode + ", nahp_level_seq=" + levelSeq;
    }

    private Map<String, Object> rawRow(CatalogHeaderRow row) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("ctlg_code", row.ctlgCode());
        map.put("nahp_level_seq", row.nahpLevelSeq());
        map.put("ctlg_name", row.ctlgName());
        map.put("data_code", row.dataCode());
        map.put("use_yn", row.useYn());
        map.put("prt_yymm", row.prtYymm());
        map.put("prt_ver", row.prtVer());
        map.put("nahp_disp_yn", row.nahpDispYn());
        map.put("nahp_level1_id", row.nahpLevel1Id());
        map.put("nahp_level2_id", row.nahpLevel2Id());
        map.put("nahp_level3_id", row.nahpLevel3Id());
        return map;
    }

    private Map<String, Object> rawRow(IfCatalogFileInfo row) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("ctlg_code", row.getCtlgCode());
        map.put("data_code", row.getDataCode());
        map.put("file_seq", row.getFileSeq());
        map.put("file_name", row.getFileName());
        map.put("file_ori", row.getFileOri());
        map.put("file_src", row.getFileSrc());
        map.put("file_size", row.getFileSize());
        return map;
    }
}
