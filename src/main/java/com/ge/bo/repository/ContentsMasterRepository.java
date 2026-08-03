package com.ge.bo.repository;

import com.ge.bo.entity.ContentsMaster;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * contents_master(콘텐츠 원장) Repository
 */
public interface ContentsMasterRepository extends JpaRepository<ContentsMaster, Long> {

    Optional<ContentsMaster> findBySourceSystemAndSourceDocKey(String sourceSystem, String sourceDocKey);

    /** 현재 소스에 살아있는(soft-delete 안 된) 전체 문서 — CATALOG의 동일 자료코드 최신버전 판단 등에 쓴다. */
    List<ContentsMaster> findBySourceSystemAndIsDeletedFalse(String sourceSystem);

    /**
     * 특정 배치 1회 실행에서 실제로 적재(성공/부분성공 포함)된 문서 목록 — delete_check_batch_id가 그 배치를 도장
     * 찍은 문서 전부(ContentsWriter가 upsert 성공 시 항상 stamp함) + 하위 카테고리/버전/파일 건수를 함께 반환한다.
     * 컬럼 순서: id, source_doc_key, doc_type, doc_title, expose, is_deleted, category_count, version_count, file_count
     */
    @Query(value = "SELECT m.id, m.source_doc_key, m.doc_type, m.doc_title, m.expose, m.is_deleted, "
        + "COALESCE(cat.cnt, 0), COALESCE(ver.cnt, 0), COALESCE(fil.cnt, 0) "
        + "FROM contents_master m "
        + "LEFT JOIN (SELECT contents_id, COUNT(*) cnt FROM contents_category WHERE is_deleted = false GROUP BY contents_id) cat "
        + "  ON cat.contents_id = m.id "
        + "LEFT JOIN (SELECT contents_id, COUNT(*) cnt FROM contents_version WHERE is_deleted = false GROUP BY contents_id) ver "
        + "  ON ver.contents_id = m.id "
        + "LEFT JOIN (SELECT cv.contents_id, COUNT(cf.id) cnt FROM contents_version cv "
        + "  JOIN contents_file cf ON cf.contents_version_id = cv.id AND cf.is_deleted = false GROUP BY cv.contents_id) fil "
        + "  ON fil.contents_id = m.id "
        + "WHERE m.source_system = :sourceSystem AND m.delete_check_batch_id = :batchId "
        + "ORDER BY m.source_doc_key",
        nativeQuery = true)
    List<Object[]> findProcessedDocsByBatch(@Param("sourceSystem") String sourceSystem, @Param("batchId") Long batchId);
}
