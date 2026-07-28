package com.ge.bo.batch.contents.ssq;

import com.ge.bo.batch.contents.CategoryItem;
import com.ge.bo.batch.contents.ContentDocument;
import com.ge.bo.batch.contents.ContentsNormalizer;
import com.ge.bo.batch.contents.ConversionResult;
import com.ge.bo.batch.contents.FileItem;
import com.ge.bo.batch.contents.RowFailure;
import com.ge.bo.batch.contents.SourceSystem;
import com.ge.bo.batch.contents.VersionItem;
import com.ge.bo.entity.IfSsqDocument;
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

    // 문서 유형 코드: C(카탈로그)/M(매뉴얼)/D(CAD)/S(소프트웨어)/V(교육영상)/R(인증서)/O(OS/Firmware)/T(기술자료)/Z(기타)
    private static final Set<String> VALID_DOC_TYPES = Set.of("C", "M", "D", "S", "V", "R", "O", "T", "Z");
    private static final Map<String, String> FULLNAME_TO_CODE = Map.of(
        "SOFTWARE", "S", "MANUAL", "M", "CATALOG", "C", "CAD", "D", "VIDEO", "V");
    private static final String VIDEO_TYPE = "V";
    private static final String SOURCE_TABLE_DOC = "if_r_ssq_document";
    private static final String SOURCE_TABLE_FILE = "if_r_ssq_file_info";

    private final SsqCategoryMapping categoryMapping;
    private final SsqHtmlSupport htmlSupport;

    public ConversionResult convert(int docId, List<IfSsqDocument> categoryRows, List<SsqFileInfoRow> fileRows) {
        List<RowFailure> rowFailures = new ArrayList<>();
        List<String> reportNotes = new ArrayList<>();

        IfSsqDocument first = categoryRows.get(0);
        String docTitle = ContentsNormalizer.trimToNull(first.getDocTitle());
        String rawDocType = ContentsNormalizer.trimToNull(first.getDocType());
        boolean expose = Boolean.TRUE.equals(first.getExpose());
        String siteLanguage = first.getSiteLanguage();
        String createDatetime = first.getCreateDatetime();
        String updateDatetime = first.getUpdateDatetime();
        String deleteYn = ContentsNormalizer.trimToNull(first.getDeleteYn());

        for (IfSsqDocument row : categoryRows) {
            boolean conflict = !eq(docTitle, ContentsNormalizer.trimToNull(row.getDocTitle()))
                || !eq(rawDocType, ContentsNormalizer.trimToNull(row.getDocType()))
                || !Objects.equals(expose, Boolean.TRUE.equals(row.getExpose()))
                || !eq(siteLanguage, row.getSiteLanguage())
                || !eq(createDatetime, row.getCreateDatetime())
                || !eq(updateDatetime, row.getUpdateDatetime())
                || !eq(deleteYn, ContentsNormalizer.trimToNull(row.getDeleteYn()));
            if (conflict) {
                return documentLevelFailure(SOURCE_TABLE_DOC, docId,
                    "같은 doc_id의 문서 반복 행 사이에 문서 레벨 값(doc_title/doc_type/expose/site_language/"
                        + "create_datetime/update_datetime/delete_yn)이 서로 다름", rawRow(row));
            }
        }

        String docType = resolveDocType(rawDocType);
        if (docType == null) {
            return documentLevelFailure(SOURCE_TABLE_DOC, docId, "정의되지 않은 doc_type: '" + rawDocType + "'", rawRow(first));
        }

        boolean explicitDelete;
        if ("Y".equalsIgnoreCase(deleteYn)) {
            explicitDelete = true;
        } else if ("N".equalsIgnoreCase(deleteYn)) {
            explicitDelete = false;
        } else {
            return documentLevelFailure(SOURCE_TABLE_DOC, docId, "정의되지 않은 delete_yn: '" + deleteYn + "'", rawRow(first));
        }

        OffsetDateTime sourceCreatedAt = safeParseDate(createDatetime, "doc_id=" + docId, "create_datetime", reportNotes);
        OffsetDateTime sourceUpdatedAt = safeParseDate(updateDatetime, "doc_id=" + docId, "update_datetime", reportNotes);

        // 카테고리 — 행마다 1건
        Map<String, CategoryItem> categoriesByPath = new LinkedHashMap<>();
        for (IfSsqDocument row : categoryRows) {
            String sourcePath = ContentsNormalizer.buildCategoryPath(">",
                row.getLevel1(), row.getLevel2(), row.getLevel3(), row.getLevel4());
            if (sourcePath == null) {
                rowFailures.add(new RowFailure(SOURCE_TABLE_DOC, rowKey(docId, row.getSpecGroup()), "NULL_KEY",
                    "카테고리 레벨(level_1~level_4)이 전부 비어있음", rawRow(row)));
                continue;
            }
            SsqCategoryResolution resolution = categoryMapping.resolve(
                row.getLevel1(), row.getLevel2(), row.getLevel3(), row.getLevel4())
                .orElse(null);
            if (resolution == null) {
                reportNotes.add("미매핑 카테고리 경로(level_1~level_4, SsqCategoryMapping 보완 필요): doc_id=" + docId + ", path=" + sourcePath);
            }
            // nahp_display_flag가 bit -> varchar(1)('t'/'f')로 변경됨(2026-07-16) — null이면 기존과 동일하게 노출로 간주.
            boolean displayFlag = row.getNahpDisplayFlag() == null || "t".equalsIgnoreCase(row.getNahpDisplayFlag().trim());

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
                rowFailures.add(new RowFailure(SOURCE_TABLE_DOC, rowKey(docId, row.getSpecGroup()), "VALUE_CONFLICT",
                    "동일 source_path에 서로 다른 카테고리 값이 반복 수신됨: " + sourcePath, rawRow(row)));
                continue;
            }
            categoriesByPath.putIfAbsent(sourcePath, candidate);
        }

        // 버전/파일 — version_id 기준 그룹화. 반복 행 간 값 충돌은 문서 전체 격리(SSQ 전용 규칙)
        Map<Integer, List<SsqFileInfoRow>> byVersionId = new LinkedHashMap<>();
        for (SsqFileInfoRow row : fileRows) {
            String fileDocType = ContentsNormalizer.trimToNull(row.docType());
            if (fileDocType != null && !eq(resolveDocType(fileDocType), docType)) {
                return documentLevelFailure(SOURCE_TABLE_FILE, docId,
                    "파일 IF의 doc_type(" + fileDocType + ")이 문서 doc_type(" + docType + ")과 다름", rawRow(row));
            }
            byVersionId.computeIfAbsent(row.versionId(), k -> new ArrayList<>()).add(row);
        }

        List<VersionItem> versions = new ArrayList<>();
        for (Map.Entry<Integer, List<SsqFileInfoRow>> entry : byVersionId.entrySet()) {
            VersionBuildOutcome outcome = buildVersion(docId, docType, entry.getKey(), entry.getValue(), reportNotes);
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

        if (categoriesByPath.isEmpty()) {
            reportNotes.add("카테고리(level_1~level_4) 등록이 0건인 문서: doc_id=" + docId);
        }
        boolean hasExposableContent = versions.stream()
            .anyMatch(v -> v.getVideoUrl() != null || !v.getFiles().isEmpty());
        if (!versions.isEmpty() && !hasExposableContent) {
            reportNotes.add("노출 파일(if_r_ssq_file_info)·영상(version_desc) URL이 모두 0건인 문서(빈 콘텐츠): doc_id=" + docId);
        }

        return new ConversionResult(document, rowFailures, reportNotes);
    }

    private VersionBuildOutcome buildVersion(int docId, String docType, int versionId, List<SsqFileInfoRow> rows,
                                              List<String> reportNotes) {
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
            if (episode == null) {
                reportNotes.add("version_name(에피소드번호) 파싱 실패, version_id 기준으로 대체: doc_id=" + docId + ", version_id=" + versionId);
            }
        } else {
            sortKey = -versionId;
        }

        OffsetDateTime versionUpdatedAt = safeParseDate(versionUpdateDatetime, "doc_id=" + docId + ", version_id=" + versionId,
            "version_update_datetime", reportNotes);

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
        List<String> ignoredNotes = new ArrayList<>();
        OffsetDateTime fileUpdatedAt = safeParseDate(row.fileUpsertDatetime(), rowKey, "file_upsert_datetime", ignoredNotes);

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
        if (VALID_DOC_TYPES.contains(upper)) {
            return upper;
        }
        return FULLNAME_TO_CODE.get(upper);
    }

    private OffsetDateTime safeParseDate(String raw, String rowKey, String field, List<String> reportNotes) {
        try {
            return ContentsNormalizer.parseSsqUtcDateTime(raw);
        } catch (IllegalArgumentException e) {
            reportNotes.add("날짜 파싱 실패(" + field + ") — NULL 처리: " + rowKey + " ('" + raw + "')");
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

    private Map<String, Object> rawRow(IfSsqDocument row) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("doc_id", row.getDocId());
        map.put("spec_group", row.getSpecGroup());
        map.put("doc_title", row.getDocTitle());
        map.put("doc_type", row.getDocType());
        map.put("level_1", row.getLevel1());
        map.put("level_2", row.getLevel2());
        map.put("level_3", row.getLevel3());
        map.put("level_4", row.getLevel4());
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
