package com.ge.bo.batch.contents;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 콘텐츠 4테이블(contents_master → contents_category → contents_version → contents_file) 공통 Upsert Writer.
 * 원천을 구분하지 않고 ContentDocument만 알면 동작한다 — 원천별 INSERT 로직 중복을 막는 지점.
 *
 * 문서 1건 = REQUIRES_NEW 트랜잭션 1개. Spring 프록시가 self-invocation에서 무시되는 문제를 피하기 위해
 * 오케스트레이터(CatalogContentsBatchService)와 별도 Bean으로 분리했다 — 반드시 다른 Bean에서 호출해야
 * @Transactional(REQUIRES_NEW)이 실제로 적용된다.
 *
 * Null 업데이트 정책: CATALOG/SSQ는 문서 변경 시 전체 재송신되는 스냅샷 성격이라 EXCLUDED
 * 값으로 무조건 덮어쓴다(원천 null도 실제 null로 반영). CERTI는 최근 변경분만 보내는 델타 소스라 null이 "미전송"을
 * 의미할 수 있어, contents_master의 서술성 컬럼은 COALESCE로 기존값을 보존한다(source_system=CERTI일 때만 분기).
 * attrs는 항상 최소 '{}'로 직렬화되므로(ContentsJsonSupport), '{}'인 경우만 "값 없음"으로 간주해 기존 attrs를 유지한다.
 */
@Component
@RequiredArgsConstructor
public class ContentsWriter {

    private static final String MASTER_UPSERT_SQL = """
        INSERT INTO contents_master
          (source_system, source_doc_key, doc_type, doc_title, nahp_title, nahp_lang, expose, attrs,
           source_created_at, source_updated_at, is_deleted, deleted_at, delete_check_batch_id, created_at, updated_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, now(), now())
        ON CONFLICT (source_system, source_doc_key) DO UPDATE SET
          doc_type = EXCLUDED.doc_type, doc_title = EXCLUDED.doc_title, nahp_title = EXCLUDED.nahp_title,
          nahp_lang = EXCLUDED.nahp_lang, expose = EXCLUDED.expose,
          attrs = EXCLUDED.attrs, source_created_at = EXCLUDED.source_created_at,
          source_updated_at = EXCLUDED.source_updated_at, is_deleted = EXCLUDED.is_deleted,
          deleted_at = EXCLUDED.deleted_at, delete_check_batch_id = EXCLUDED.delete_check_batch_id, updated_at = now()
        RETURNING id, (xmax = 0) AS was_insert
        """;

    // CERTI(델타 소스) 전용 — 서술성 컬럼은 COALESCE로 기존값 보존, expose/is_deleted/delete_check_batch_id는 항상 반영
    private static final String MASTER_UPSERT_PRESERVE_NULL_SQL = """
        INSERT INTO contents_master
          (source_system, source_doc_key, doc_type, doc_title, nahp_title, nahp_lang, expose, attrs,
           source_created_at, source_updated_at, is_deleted, deleted_at, delete_check_batch_id, created_at, updated_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, now(), now())
        ON CONFLICT (source_system, source_doc_key) DO UPDATE SET
          doc_type = EXCLUDED.doc_type,
          doc_title = COALESCE(EXCLUDED.doc_title, contents_master.doc_title),
          nahp_title = COALESCE(EXCLUDED.nahp_title, contents_master.nahp_title),
          nahp_lang = COALESCE(EXCLUDED.nahp_lang, contents_master.nahp_lang),
          expose = EXCLUDED.expose,
          attrs = COALESCE(NULLIF(EXCLUDED.attrs::text, '{}')::jsonb, contents_master.attrs),
          source_created_at = COALESCE(EXCLUDED.source_created_at, contents_master.source_created_at),
          source_updated_at = COALESCE(EXCLUDED.source_updated_at, contents_master.source_updated_at),
          is_deleted = EXCLUDED.is_deleted, deleted_at = EXCLUDED.deleted_at,
          delete_check_batch_id = EXCLUDED.delete_check_batch_id, updated_at = now()
        RETURNING id, (xmax = 0) AS was_insert
        """;

    private static final String CATEGORY_UPSERT_SQL = """
        INSERT INTO contents_category
          (contents_id, source_system, source_path, nahp_category_id, category_l1_id, category_l2_id, category_l3_id,
           nahp_level_seq, nahp_display_flag, is_deleted, deleted_at, delete_check_batch_id, created_at, updated_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, false, null, ?, now(), now())
        ON CONFLICT (contents_id, source_path) DO UPDATE SET
          nahp_category_id = EXCLUDED.nahp_category_id, category_l1_id = EXCLUDED.category_l1_id,
          category_l2_id = EXCLUDED.category_l2_id, category_l3_id = EXCLUDED.category_l3_id,
          nahp_level_seq = EXCLUDED.nahp_level_seq, nahp_display_flag = EXCLUDED.nahp_display_flag,
          is_deleted = false, deleted_at = null,
          delete_check_batch_id = EXCLUDED.delete_check_batch_id, updated_at = now()
        RETURNING id, (xmax = 0) AS was_insert
        """;

    private static final String VERSION_UPSERT_SQL = """
        INSERT INTO contents_version
          (contents_id, source_system, source_version_key, version_name, version_desc, video_url, version_expose,
           sort_key, source_updated_at, is_deleted, deleted_at, delete_check_batch_id, created_at, updated_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, false, null, ?, now(), now())
        ON CONFLICT (contents_id, source_version_key) DO UPDATE SET
          version_name = EXCLUDED.version_name, version_desc = EXCLUDED.version_desc,
          video_url = EXCLUDED.video_url, version_expose = EXCLUDED.version_expose, sort_key = EXCLUDED.sort_key,
          source_updated_at = EXCLUDED.source_updated_at, is_deleted = false, deleted_at = null,
          delete_check_batch_id = EXCLUDED.delete_check_batch_id, updated_at = now()
        RETURNING id, (xmax = 0) AS was_insert
        """;

    private static final String FILE_UPSERT_SQL = """
        INSERT INTO contents_file
          (contents_version_id, source_system, source_file_key, file_name, file_ext, file_size, file_lang,
           source_file_path, file_path, attrs, file_expose, source_updated_at, is_deleted, deleted_at, delete_check_batch_id,
           created_at, updated_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, false, null, ?, now(), now())
        ON CONFLICT (contents_version_id, source_file_key) DO UPDATE SET
          file_name = EXCLUDED.file_name, file_ext = EXCLUDED.file_ext, file_size = EXCLUDED.file_size,
          file_lang = EXCLUDED.file_lang, source_file_path = EXCLUDED.source_file_path, file_path = EXCLUDED.file_path,
          attrs = EXCLUDED.attrs, file_expose = EXCLUDED.file_expose, source_updated_at = EXCLUDED.source_updated_at,
          is_deleted = false, deleted_at = null, delete_check_batch_id = EXCLUDED.delete_check_batch_id, updated_at = now()
        RETURNING id, (xmax = 0) AS was_insert
        """;

    private static final String MASTER_SELECT_SQL = """
        SELECT doc_type, doc_title, nahp_title, nahp_lang, expose, is_deleted
        FROM contents_master WHERE source_system = ? AND source_doc_key = ?
        """;

    private static final String CATEGORY_SELECT_SQL = """
        SELECT nahp_category_id, category_l1_id, category_l2_id, category_l3_id, nahp_level_seq, nahp_display_flag
        FROM contents_category WHERE contents_id = ? AND source_path = ?
        """;

    private static final String VERSION_SELECT_SQL = """
        SELECT version_name, version_desc, video_url, version_expose, sort_key
        FROM contents_version WHERE contents_id = ? AND source_version_key = ?
        """;

    private final JdbcTemplate jdbcTemplate;
    private final ContentsJsonSupport jsonSupport;

    /**
     * 필드 단위 변경 감지는 SSQ에서만 사용한다(요청 범위) — 다른 소스는 diff 계산 비용만 늘어나고 아무도 읽지 않으므로 건너뛴다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public WriteCounts writeDocument(ContentDocument doc, long batchId) {
        boolean diffEnabled = doc.getSourceSystem() == SourceSystem.SSQ;

        List<String> masterFieldChanges = diffEnabled ? diffMaster(doc) : List.of();
        UpsertOutcome master = upsertMaster(doc, batchId);

        int categoryInsert = 0;
        int categoryUpdate = 0;
        List<String> insertedCategoryPaths = new ArrayList<>();
        List<String> changedCategoryDetails = new ArrayList<>();
        for (CategoryItem category : doc.getCategories()) {
            List<String> categoryFieldChanges = diffEnabled ? diffCategory(master.id(), category) : List.of();
            UpsertOutcome outcome = upsertCategory(master.id(), doc.getSourceSystem().name(), category, batchId);
            if (outcome.wasInsert()) {
                categoryInsert++;
                insertedCategoryPaths.add(category.getSourcePath());
            } else {
                categoryUpdate++;
                if (!categoryFieldChanges.isEmpty()) {
                    changedCategoryDetails.add(category.getSourcePath() + " (" + String.join(", ", categoryFieldChanges) + ")");
                }
            }
        }

        int versionInsert = 0;
        int versionUpdate = 0;
        int fileInsert = 0;
        int fileUpdate = 0;
        List<String> insertedVersionKeys = new ArrayList<>();
        List<String> changedVersionDetails = new ArrayList<>();
        for (VersionItem version : doc.getVersions()) {
            List<String> versionFieldChanges = diffEnabled ? diffVersion(master.id(), version) : List.of();
            UpsertOutcome versionOutcome = upsertVersion(master.id(), doc.getSourceSystem().name(), version, batchId);
            if (versionOutcome.wasInsert()) {
                versionInsert++;
                insertedVersionKeys.add(version.getSourceVersionKey());
            } else {
                versionUpdate++;
                if (!versionFieldChanges.isEmpty()) {
                    changedVersionDetails.add(version.getSourceVersionKey() + " (" + String.join(", ", versionFieldChanges) + ")");
                }
            }
            for (FileItem file : version.getFiles()) {
                UpsertOutcome fileOutcome =
                    upsertFile(versionOutcome.id(), doc.getSourceSystem().name(), doc.getDocType(), file, batchId);
                if (fileOutcome.wasInsert()) {
                    fileInsert++;
                } else {
                    fileUpdate++;
                }
            }
        }

        return new WriteCounts(master.id(), master.wasInsert() ? 1 : 0, master.wasInsert() ? 0 : 1,
            categoryInsert, categoryUpdate, versionInsert, versionUpdate, fileInsert, fileUpdate,
            insertedCategoryPaths, insertedVersionKeys, changedCategoryDetails, changedVersionDetails, masterFieldChanges);
    }

    private List<String> diffMaster(ContentDocument doc) {
        Map<String, Object> oldRow = queryExistingRow(MASTER_SELECT_SQL, doc.getSourceSystem().name(), doc.getSourceDocKey());
        if (oldRow == null) {
            return List.of();
        }
        Map<String, Object> incoming = new LinkedHashMap<>();
        incoming.put("doc_type", doc.getDocType());
        incoming.put("doc_title", doc.getDocTitle());
        incoming.put("nahp_title", doc.getNahpTitle());
        incoming.put("nahp_lang", doc.getNahpLang());
        incoming.put("expose", doc.isExpose());
        incoming.put("is_deleted", doc.isExplicitDelete());
        return diffFields(oldRow, incoming);
    }

    private List<String> diffCategory(long contentsId, CategoryItem category) {
        Map<String, Object> oldRow = queryExistingRow(CATEGORY_SELECT_SQL, contentsId, category.getSourcePath());
        if (oldRow == null) {
            return List.of();
        }
        Map<String, Object> incoming = new LinkedHashMap<>();
        incoming.put("nahp_category_id", category.getNahpCategoryId());
        incoming.put("category_l1_id", category.getCategoryL1Id());
        incoming.put("category_l2_id", category.getCategoryL2Id());
        incoming.put("category_l3_id", category.getCategoryL3Id());
        incoming.put("nahp_level_seq", category.getNahpLevelSeq());
        incoming.put("nahp_display_flag", category.isNahpDisplayFlag());
        return diffFields(oldRow, incoming);
    }

    private List<String> diffVersion(long contentsId, VersionItem version) {
        Map<String, Object> oldRow = queryExistingRow(VERSION_SELECT_SQL, contentsId, version.getSourceVersionKey());
        if (oldRow == null) {
            return List.of();
        }
        Map<String, Object> incoming = new LinkedHashMap<>();
        incoming.put("version_name", version.getVersionName());
        incoming.put("version_desc", version.getVersionDesc());
        incoming.put("video_url", version.getVideoUrl());
        incoming.put("version_expose", version.isVersionExpose());
        incoming.put("sort_key", version.getSortKey());
        return diffFields(oldRow, incoming);
    }

    private Map<String, Object> queryExistingRow(String sql, Object... keyArgs) {
        try {
            return jdbcTemplate.queryForMap(sql, keyArgs);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    /** oldRow(DB 기존값) vs incoming(이번 배치 값)을 필드별로 비교해 달라진 것만 "필드명: 이전값 → 새값" 형태로 모은다. */
    private List<String> diffFields(Map<String, Object> oldRow, Map<String, Object> incoming) {
        List<String> changes = new ArrayList<>();
        for (Map.Entry<String, Object> entry : incoming.entrySet()) {
            Object oldValue = oldRow.get(entry.getKey());
            Object newValue = entry.getValue();
            if (!Objects.equals(oldValue, newValue)) {
                changes.add(entry.getKey() + ": " + formatValue(oldValue) + " → " + formatValue(newValue));
            }
        }
        return changes;
    }

    /** 리포트 노트가 과도하게 길어지지 않도록 긴 값(예: version_desc의 HTML 본문)은 길이만 표시하고 잘라낸다. */
    private String formatValue(Object value) {
        String s = String.valueOf(value);
        if (s.length() > 60) {
            return s.substring(0, 60) + "...(" + s.length() + "자)";
        }
        return s;
    }

    private UpsertOutcome upsertMaster(ContentDocument doc, long batchId) {
        boolean isDeleted = doc.isExplicitDelete();
        OffsetDateTime deletedAt = isDeleted ? OffsetDateTime.now() : null;
        String sql = doc.getSourceSystem() == SourceSystem.CERTI ? MASTER_UPSERT_PRESERVE_NULL_SQL : MASTER_UPSERT_SQL;
        return queryOne(sql,
            doc.getSourceSystem().name(), doc.getSourceDocKey(), doc.getDocType(), doc.getDocTitle(),
            doc.getNahpTitle(), doc.getNahpLang(), doc.isExpose(),
            jsonSupport.toJson(doc.getAttrs()), doc.getSourceCreatedAt(), doc.getSourceUpdatedAt(),
            isDeleted, deletedAt, batchId);
    }

    private UpsertOutcome upsertCategory(long contentsId, String sourceSystem, CategoryItem category, long batchId) {
        return queryOne(CATEGORY_UPSERT_SQL,
            contentsId, sourceSystem, category.getSourcePath(), category.getNahpCategoryId(),
            category.getCategoryL1Id(), category.getCategoryL2Id(), category.getCategoryL3Id(),
            category.getNahpLevelSeq(), category.isNahpDisplayFlag(), batchId);
    }

    private UpsertOutcome upsertVersion(long contentsId, String sourceSystem, VersionItem version, long batchId) {
        return queryOne(VERSION_UPSERT_SQL,
            contentsId, sourceSystem, version.getSourceVersionKey(), version.getVersionName(),
            version.getVersionDesc(), version.getVideoUrl(), version.isVersionExpose(), version.getSortKey(),
            version.getSourceUpdatedAt(), batchId);
    }

    private UpsertOutcome upsertFile(long contentsVersionId, String sourceSystem, String docType, FileItem file, long batchId) {
        String pathFileName = file.getPathFileName() != null ? file.getPathFileName() : file.getFileName();
        String filePath = ContentsNormalizer.buildBlobFilePath(docType, file.getSourceFolderKey(), pathFileName);
        return queryOne(FILE_UPSERT_SQL,
            contentsVersionId, sourceSystem, file.getSourceFileKey(), file.getFileName(), file.getFileExt(),
            file.getFileSize(), file.getFileLang(), file.getSourceFilePath(), filePath, jsonSupport.toJson(file.getAttrs()),
            file.isFileExpose(), file.getSourceUpdatedAt(), batchId);
    }

    private UpsertOutcome queryOne(String sql, Object... args) {
        return jdbcTemplate.queryForObject(sql,
            (rs, rowNum) -> new UpsertOutcome(rs.getLong("id"), rs.getBoolean("was_insert")), args);
    }

    private record UpsertOutcome(long id, boolean wasInsert) {
    }
}
