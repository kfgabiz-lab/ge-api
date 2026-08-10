package com.ge.bo.batch.contents.ssq;

import com.ge.bo.batch.contents.CategoryItem;
import com.ge.bo.batch.contents.ContentDocument;
import com.ge.bo.batch.contents.ContentsNormalizer;
import com.ge.bo.batch.contents.ConversionResult;
import com.ge.bo.batch.contents.FileItem;
import com.ge.bo.batch.contents.RowFailure;
import com.ge.bo.batch.contents.SourceSystem;
import com.ge.bo.batch.contents.VersionItem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * IF_R_SSQ_DOCUMENT(문서·카테고리) + IF_R_SSQ_FILE_INFO(버전·파일) → ContentDocument 변환
 * (NAHP_IF_콘텐츠테이블_매핑정의서_v0.7 04_SSQ_DOCUMENT / 05_SSQ_FILE 시트 + 실데이터 검증 기준)
 *
 * SSQ는 카테고리×버전×파일이 곱 형태로 반복 수신되므로 dedupe가 핵심이다.
 * - 카테고리: source_path 기준(문제 있는 행만 건너뜀 — 행 단위 격리)
 * - 버전(version_id)/파일(file_id): 반복 행 간 값 충돌 시 문서 전체를 격리한다(SSQ 전용 규칙 — 카테고리보다 엄격).
 * 날짜 파싱 실패는 문서 전체를 막지 않고 null + 리포트로 완화 처리한다(표시용 부가정보라는 판단).
 */
@Component
@RequiredArgsConstructor
public class SsqContentsConverter {

    // 문서 유형 코드: C(카탈로그)/M(매뉴얼)/D(CAD)/S(소프트웨어)/V(교육영상)/R(인증서)/O(OS/Firmware)/T(기술자료).
    // Z는 원천이 실제로 보내는 값이 아니라, 이 중 어디에도 없는 미확인 doc_type을 받았을 때 우리가 붙이는
    // 내부 fallback 라벨이다(CATALOG와 동일한 이유 — 새 유형이 추가될 때마다 매핑 갱신 전까지 문서가
    // 통째로 유실되는 걸 막기 위함).
    private static final Set<String> KNOWN_DOC_TYPES = Set.of("C", "M", "D", "S", "V", "R", "O", "T");
    private static final String FALLBACK_DOC_TYPE = "Z";
    private static final Map<String, String> FULLNAME_TO_CODE = Map.of(
        "SOFTWARE", "S", "MANUAL", "M", "CATALOG", "C", "CAD", "D", "VIDEO", "V");
    private static final String VIDEO_TYPE = "V";
    private static final String SOURCE_TABLE_DOC = "if_r_ssq_document";
    private static final String SOURCE_TABLE_FILE = "if_r_ssq_file_info";

    private final SsqCategoryMapping categoryMapping;
    private final SsqHtmlSupport htmlSupport;

    public ConversionResult convert(int docId, List<SsqDocumentRow> categoryRows, List<SsqFileInfoRow> fileRows) {
        List<RowFailure> rowFailures = new ArrayList<>();

        SsqDocumentRow first = categoryRows.get(0);
        String docTitle = ContentsNormalizer.trimToNull(first.docTitle());
        String rawDocType = ContentsNormalizer.trimToNull(first.docType());
        boolean expose = Boolean.TRUE.equals(first.expose());
        String siteLanguage = first.siteLanguage();
        String createDatetime = first.createDatetime();
        String updateDatetime = first.updateDatetime();
        String deleteYn = ContentsNormalizer.trimToNull(first.deleteYn());

        for (SsqDocumentRow row : categoryRows) {
            boolean conflict = !eq(docTitle, ContentsNormalizer.trimToNull(row.docTitle()))
                || !eq(rawDocType, ContentsNormalizer.trimToNull(row.docType()))
                || !Objects.equals(expose, Boolean.TRUE.equals(row.expose()))
                || !eq(siteLanguage, row.siteLanguage())
                || !eq(createDatetime, row.createDatetime())
                || !eq(updateDatetime, row.updateDatetime())
                || !eq(deleteYn, ContentsNormalizer.trimToNull(row.deleteYn()));
            if (conflict) {
                return documentLevelFailure(SOURCE_TABLE_DOC, docId,
                    "같은 doc_id의 문서 반복 행 사이에 문서 레벨 값(doc_title/doc_type/expose/site_language/"
                        + "create_datetime/update_datetime/delete_yn)이 서로 다름", rawRow(row));
            }
        }

        String docType = resolveDocType(rawDocType);
        if (docType == null) {
            return documentLevelFailure(SOURCE_TABLE_DOC, docId, "doc_type이 비어있음", rawRow(first));
        }

        boolean explicitDelete;
        if ("Y".equalsIgnoreCase(deleteYn)) {
            explicitDelete = true;
        } else if ("N".equalsIgnoreCase(deleteYn)) {
            explicitDelete = false;
        } else {
            return documentLevelFailure(SOURCE_TABLE_DOC, docId, "정의되지 않은 delete_yn: '" + deleteYn + "'", rawRow(first));
        }

        OffsetDateTime sourceCreatedAt = safeParseDate(createDatetime, SOURCE_TABLE_DOC, "doc_id=" + docId, "create_datetime", rowFailures);
        OffsetDateTime sourceUpdatedAt = safeParseDate(updateDatetime, SOURCE_TABLE_DOC, "doc_id=" + docId, "update_datetime", rowFailures);

        // 카테고리 — 행마다 1건
        Map<String, CategoryItem> categoriesByPath = new LinkedHashMap<>();
        for (SsqDocumentRow row : categoryRows) {
            String sourcePath = ContentsNormalizer.buildCategoryPath(">",
                row.level1(), row.level2(), row.level3(), row.level4());
            if (sourcePath == null) {
                rowFailures.add(new RowFailure(SOURCE_TABLE_DOC, rowKey(docId, row.specGroup()), "NULL_KEY",
                    "카테고리 레벨(level_1~level_4)이 전부 비어있음", rawRow(row)));
                continue;
            }
            SsqCategoryResolution resolution = categoryMapping.resolve(
                row.level1(), row.level2(), row.level3(), row.level4())
                .orElse(null);
            if (resolution == null) {
                rowFailures.add(new RowFailure(SOURCE_TABLE_DOC, rowKey(docId, row.specGroup()), "UNMAPPED_CATEGORY",
                    "미매핑 카테고리 경로(level_1~level_4, SsqCategoryMapping 보완 필요): " + sourcePath, rawRow(row)));
                continue;
            }
            // nahp_display_flag 원천값이 t/f, Y/N 두 표기가 섞여 온다 — null이면 기존과 동일하게 노출로 간주.
            boolean displayFlag;
            try {
                String flag = ContentsNormalizer.trimToNull(row.nahpDisplayFlag());
                displayFlag = flag == null || ContentsNormalizer.parseStrictBoolean(flag);
            } catch (IllegalArgumentException e) {
                rowFailures.add(new RowFailure(SOURCE_TABLE_DOC, rowKey(docId, row.specGroup()), "VALUE_CONFLICT",
                    "NAHP_DISPLAY_FLAG 값 해석 불가: " + e.getMessage(), rawRow(row)));
                continue;
            }

            CategoryItem candidate = CategoryItem.builder()
                .sourcePath(sourcePath)
                .nahpCategoryId(resolution != null ? resolution.nahpCategoryId() : null)
                .categoryL1Id(resolution != null ? resolution.categoryL1Id() : null)
                .categoryL2Id(resolution != null ? resolution.categoryL2Id() : null)
                .categoryL3Id(resolution != null ? resolution.categoryL3Id() : null)
                .nahpLevelSeq(null)
                .nahpDisplayFlag(displayFlag).build();

            CategoryItem existing = categoriesByPath.get(sourcePath);
            if (existing != null && (existing.isNahpDisplayFlag() != candidate.isNahpDisplayFlag()
                || !eq(existing.getNahpCategoryId(), candidate.getNahpCategoryId()))) {
                rowFailures.add(new RowFailure(SOURCE_TABLE_DOC, rowKey(docId, row.specGroup()), "VALUE_CONFLICT",
                    "동일 source_path에 서로 다른 카테고리 값이 반복 수신됨: " + sourcePath, rawRow(row)));
                continue;
            }
            categoriesByPath.putIfAbsent(sourcePath, candidate);
        }

        // 버전/파일 — version_id 기준 그룹화. 반복 행 간 값 충돌은 문서 전체 격리(SSQ 전용 규칙)
        Map<Integer, List<SsqFileInfoRow>> byVersionId = new LinkedHashMap<>();
        for (SsqFileInfoRow row : fileRows) {
            String fileDocType = ContentsNormalizer.trimToNull(row.docType());
            // 헤더가 Z로 폴백된 경우(rawDocType이 미확인) 파일 쪽도 원본 문자열이 헤더 원본과 같아야 진짜
            // 매칭으로 본다 — 둘 다 "미확인"이라고 무조건 같다고 보면 서로 다른 미확인 doc_type끼리도 같은
            // 문서로 섞여 불일치를 못 잡아낸다.
            boolean mismatch = FALLBACK_DOC_TYPE.equals(docType)
                ? fileDocType != null && !eq(fileDocType.toUpperCase(), rawDocType.toUpperCase())
                : fileDocType != null && !eq(resolveDocType(fileDocType), docType);
            if (mismatch) {
                return documentLevelFailure(SOURCE_TABLE_FILE, docId,
                    "파일 IF의 doc_type(" + fileDocType + ")이 문서 doc_type(" + docType + ")과 다름", rawRow(row));
            }
            byVersionId.computeIfAbsent(row.versionId(), k -> new ArrayList<>()).add(row);
        }

        List<VersionItem> versions = new ArrayList<>();
        for (Map.Entry<Integer, List<SsqFileInfoRow>> entry : byVersionId.entrySet()) {
            VersionBuildOutcome outcome = buildVersion(docId, docType, entry.getKey(), entry.getValue());
            if (outcome.documentFailure != null) {
                return outcome.documentFailure;
            }
            rowFailures.addAll(outcome.rowFailures);
            versions.add(outcome.version);
        }

        Map<String, Object> attrs = Map.of();

        ContentDocument document = ContentDocument.builder()
            .sourceSystem(SourceSystem.SSQ)
            .sourceDocKey(String.valueOf(docId))
            .docType(docType)
            .docTitle(docTitle)
            .nahpTitle(null)
            .nahpLang(ContentsNormalizer.normalizeLang(siteLanguage))
            .expose(expose)
            .attrs(attrs)
            .sourceCreatedAt(sourceCreatedAt)
            .sourceUpdatedAt(sourceUpdatedAt)
            .explicitDelete(explicitDelete)
            .categories(new ArrayList<>(categoriesByPath.values()))
            .versions(versions)
            .build();

        // 명시적 삭제(delete_yn='Y') 문서는 카테고리·파일이 원래 비어서 올 수 있어 완결성 검사 대상에서 제외한다.
        // documentLevelFailure()를 안 쓰고 rowFailures에 직접 추가하는 이유: 그 헬퍼는 결과를 통째로 새로
        // 만들어서, 카테고리·버전 처리 중 이미 쌓아둔 개별 실패 사유(미매핑 카테고리 경로 등 — SsqCategoryMapping
        // 보완에 필요한 정보)가 사라지고 이 요약 사유 하나만 남는 문제가 있었다.
        // 이미 개별 사유가 있으면(카테고리/버전 처리 중 기록된 실패) 중복되는 요약 사유는 생략한다.
        if (!explicitDelete && categoriesByPath.isEmpty()) {
            if (rowFailures.isEmpty()) {
                rowFailures.add(new RowFailure(SOURCE_TABLE_DOC, "doc_id=" + docId, "EMPTY_CATEGORY",
                    "카테고리(level_1~level_4) 등록이 0건인 문서", rawRow(first)));
            }
            return new ConversionResult(null, rowFailures, List.of());
        }
        boolean hasExposableContent = versions.stream()
            .anyMatch(v -> v.getVideoUrl() != null || !v.getFiles().isEmpty());
        if (!explicitDelete && !versions.isEmpty() && !hasExposableContent) {
            if (rowFailures.isEmpty()) {
                rowFailures.add(new RowFailure(SOURCE_TABLE_FILE, "doc_id=" + docId, "EMPTY_CONTENT",
                    "노출 파일(if_r_ssq_file_info)·영상(version_desc) URL이 모두 0건인 문서(빈 콘텐츠)", rawRow(first)));
            }
            return new ConversionResult(null, rowFailures, List.of());
        }

        return new ConversionResult(document, rowFailures, List.of());
    }

    private VersionBuildOutcome buildVersion(int docId, String docType, int versionId, List<SsqFileInfoRow> rows) {
        SsqFileInfoRow first = rows.get(0);
        String versionName = first.versionName();
        String versionDesc = first.versionDesc();
        boolean versionExpose = Boolean.TRUE.equals(first.versionExpose());
        String versionUpdateDatetime = first.versionUpdateDatetime();

        for (SsqFileInfoRow row : rows) {
            boolean conflict = !eq(versionName, row.versionName())
                || !eq(versionDesc, row.versionDesc())
                || versionExpose != Boolean.TRUE.equals(row.versionExpose())
                || !eq(versionUpdateDatetime, row.versionUpdateDatetime());
            if (conflict) {
                return VersionBuildOutcome.failure(documentLevelFailure(SOURCE_TABLE_FILE, docId,
                    "같은 version_id(" + versionId + ")의 반복 행 사이에 버전 정보(version_name/version_desc/"
                        + "version_expose/version_update_datetime)가 서로 다름", rawRow(row)));
            }
        }

        List<RowFailure> rowFailures = new ArrayList<>();
        String normalizedVersionName = ContentsNormalizer.trimToNull(versionName);
        if ("*".equals(normalizedVersionName)) {
            normalizedVersionName = null;
        }

        String videoUrl = null;
        String versionDescForStorage = null;
        if (VIDEO_TYPE.equals(docType)) {
            var extracted = htmlSupport.extractVideoUrl(versionDesc);
            if (extracted.isPresent()) {
                videoUrl = extracted.get();
            } else {
                rowFailures.add(new RowFailure(SOURCE_TABLE_FILE, "doc_id=" + docId + ", version_id=" + versionId, "PARSE_URL",
                    "영상 URL(version_desc 내 data-oembed-url) 추출 실패 또는 허용되지 않은 도메인",
                    Map.of("docId", docId, "versionId", versionId)));
            }
        } else {
            versionDescForStorage = htmlSupport.sanitize(versionDesc);
        }

        int sortKey;
        if (VIDEO_TYPE.equals(docType)) {
            Integer episode = tryParseInt(normalizedVersionName);
            sortKey = episode != null ? -episode : -versionId;
            // normalizedVersionName이 null인 건 원천이 값을 안 보냈거나("*", 위에서 null로 정규화됨) 정상적인
            // 무표시 상태라 에러가 아니다 — 값은 있는데 숫자로 해석이 안 되는 경우만 진짜 데이터 이상으로 본다.
            if (episode == null && normalizedVersionName != null) {
                rowFailures.add(new RowFailure(SOURCE_TABLE_FILE, "doc_id=" + docId + ", version_id=" + versionId,
                    "VALUE_CONFLICT", "version_name(에피소드번호) 파싱 실패, version_id 기준으로 대체됨: '" + versionName + "'",
                    Map.of()));
            }
        } else {
            sortKey = -versionId;
        }

        OffsetDateTime versionUpdatedAt = safeParseDate(versionUpdateDatetime, SOURCE_TABLE_FILE,
            "doc_id=" + docId + ", version_id=" + versionId, "version_update_datetime", rowFailures);

        // 파일 — (버전, file_id) 기준 dedupe. 값 충돌 시 문서 전체 격리
        Map<Integer, FileItem> filesByFileId = new LinkedHashMap<>();
        for (SsqFileInfoRow row : rows) {
            if (row.fileId() == null) {
                continue;
            }
            FileItem candidate = buildFileItem(docId, versionId, row, rowFailures);
            if (candidate == null) {
                continue;
            }
            FileItem existingFile = filesByFileId.get(row.fileId());
            if (existingFile != null && (!eq(existingFile.getFileName(), candidate.getFileName())
                || !eq(existingFile.getSourceFilePath(), candidate.getSourceFilePath())
                || !Objects.equals(existingFile.getFileSize(), candidate.getFileSize())
                || !eq(existingFile.getFileLang(), candidate.getFileLang())
                || existingFile.isFileExpose() != candidate.isFileExpose())) {
                return VersionBuildOutcome.failure(documentLevelFailure(SOURCE_TABLE_FILE, docId,
                    "같은 file_id(" + row.fileId() + ")의 반복 행 사이에 파일 정보(file_name/file_key/file_size/"
                        + "file_lang/file_expose)가 서로 다름", rawRow(row)));
            }
            filesByFileId.putIfAbsent(row.fileId(), candidate);
        }

        VersionItem version = VersionItem.builder()
            .sourceVersionKey(String.valueOf(versionId))
            .versionName(normalizedVersionName)
            .versionDesc(versionDescForStorage)
            .videoUrl(videoUrl)
            .versionExpose(versionExpose)
            .sortKey(sortKey)
            .sourceUpdatedAt(versionUpdatedAt)
            .files(new ArrayList<>(filesByFileId.values()))
            .build();

        return VersionBuildOutcome.success(version, rowFailures);
    }

    private FileItem buildFileItem(int docId, int versionId, SsqFileInfoRow row, List<RowFailure> rowFailures) {
        String rowKey = "doc_id=" + docId + ", version_id=" + versionId + ", file_id=" + row.fileId();
        String fileName = ContentsNormalizer.trimToNull(row.fileName());
        if (fileName == null) {
            rowFailures.add(new RowFailure(SOURCE_TABLE_FILE, rowKey, "NULL_KEY", "file_name이 비어있음", Map.of("docId", docId)));
            return null;
        }
        String sourceFilePath = buildSourceFilePath(row);
        if (sourceFilePath == null) {
            rowFailures.add(new RowFailure(SOURCE_TABLE_FILE, rowKey, "VALUE_CONFLICT",
                "size_flag('" + row.sizeFlag() + "')로 파일 경로를 조립할 수 없음", Map.of("docId", docId)));
            return null;
        }
        Long fileSize = row.fileSize();
        if (fileSize != null && fileSize < 0) {
            fileSize = null;
        }
        Map<String, Object> attrs = row.sizeFlag() != null ? Map.of("size_flag", row.sizeFlag()) : Map.of();
        OffsetDateTime fileUpdatedAt = safeParseDate(row.fileUpsertDatetime(), SOURCE_TABLE_FILE, rowKey,
            "file_upsert_datetime", rowFailures);

        // blob 서빙 경로(file_path)의 문서별 하위 폴더 — doc_id + version_id를 그대로 이어붙인다
        // (source_doc_key + source_version_key 조합, 구분자 없음). 서로 다른 SSQ 문서가 같은 파일명을
        // 쓸 때 경로 충돌을 막기 위함.
        String folderKey = docId + String.valueOf(versionId);

        return FileItem.builder()
            .sourceFileKey(String.valueOf(row.fileId()))
            .fileName(fileName)
            .fileExt(ContentsNormalizer.extractExtension(fileName))
            .fileSize(fileSize)
            .fileLang(ContentsNormalizer.normalizeLang(row.fileLang()))
            .sourceFilePath(sourceFilePath)
            .sourceFolderKey(folderKey)
            .attrs(attrs)
            .fileExpose(row.fileExpose() == null || row.fileExpose())
            .sourceUpdatedAt(fileUpdatedAt)
            .build();
    }

    /** size_flag='Y'(대용량): file_key에 파일명까지 포함돼 있어 그대로 사용. 'N'(소용량): file_key + '/' + file_name */
    private String buildSourceFilePath(SsqFileInfoRow row) {
        String fileKey = ContentsNormalizer.trimToNull(row.fileKey());
        if (fileKey == null) {
            return null;
        }
        String sizeFlag = ContentsNormalizer.trimToNull(row.sizeFlag());
        if ("Y".equalsIgnoreCase(sizeFlag)) {
            return fileKey;
        }
        if ("N".equalsIgnoreCase(sizeFlag)) {
            String fileName = ContentsNormalizer.trimToNull(row.fileName());
            if (fileName == null) {
                return null;
            }
            String trimmedKey = fileKey.endsWith("/") ? fileKey.substring(0, fileKey.length() - 1) : fileKey;
            return trimmedKey + "/" + fileName;
        }
        return null;
    }

    private String resolveDocType(String raw) {
        if (raw == null) {
            return null;
        }
        String upper = raw.toUpperCase();
        if (KNOWN_DOC_TYPES.contains(upper)) {
            return upper;
        }
        return FULLNAME_TO_CODE.getOrDefault(upper, FALLBACK_DOC_TYPE);
    }

    private OffsetDateTime safeParseDate(String raw, String sourceTable, String rowKey, String field, List<RowFailure> rowFailures) {
        try {
            return ContentsNormalizer.parseSsqUtcDateTime(raw);
        } catch (IllegalArgumentException e) {
            rowFailures.add(new RowFailure(sourceTable, rowKey, "VALUE_CONFLICT",
                "날짜 파싱 실패(" + field + "): '" + raw + "'", Map.of()));
            return null;
        }
    }

    private Integer tryParseInt(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private ConversionResult documentLevelFailure(String sourceTable, int docId, String detail, Map<String, Object> raw) {
        RowFailure failure = new RowFailure(sourceTable, "doc_id=" + docId, "VALUE_CONFLICT", detail, raw);
        return new ConversionResult(null, List.of(failure), List.of());
    }

    private boolean eq(String a, String b) {
        return Objects.equals(a, b);
    }

    private String rowKey(int docId, String specGroup) {
        return "doc_id=" + docId + ", spec_group=" + specGroup;
    }

    private Map<String, Object> rawRow(SsqDocumentRow row) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("doc_id", row.docId());
        map.put("spec_group", row.specGroup());
        map.put("doc_title", row.docTitle());
        map.put("doc_type", row.docType());
        map.put("level_1", row.level1());
        map.put("level_2", row.level2());
        map.put("level_3", row.level3());
        map.put("level_4", row.level4());
        return map;
    }

    private Map<String, Object> rawRow(SsqFileInfoRow row) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("doc_id", row.docId());
        map.put("version_id", row.versionId());
        map.put("file_id", row.fileId());
        map.put("file_name", row.fileName());
        return map;
    }

    private static final class VersionBuildOutcome {
        private final VersionItem version;
        private final List<RowFailure> rowFailures;
        private final ConversionResult documentFailure;

        private VersionBuildOutcome(VersionItem version, List<RowFailure> rowFailures, ConversionResult documentFailure) {
            this.version = version;
            this.rowFailures = rowFailures;
            this.documentFailure = documentFailure;
        }

        static VersionBuildOutcome success(VersionItem version, List<RowFailure> rowFailures) {
            return new VersionBuildOutcome(version, rowFailures, null);
        }

        static VersionBuildOutcome failure(ConversionResult documentFailure) {
            return new VersionBuildOutcome(null, List.of(), documentFailure);
        }
    }
}
