package com.ge.bo.repository;

import com.ge.bo.entity.IfSsqDocument;
import com.ge.bo.entity.IfSsqDocumentId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.QueryHint;

import java.util.List;
import java.util.stream.Stream;

/**
 * IF_R_SSQ_DOCUMENT(SSQ 문서 IF) 조회 Repository — EAI 소유 테이블, 읽기 + if_result 갱신만 수행
 */
public interface IfSsqDocumentRepository extends JpaRepository<IfSsqDocument, IfSsqDocumentId> {

    @QueryHints(@QueryHint(name = "org.hibernate.fetchSize", value = "200"))
    @Query("select d from IfSsqDocument d where d.ifResult = :ifResult order by d.docId asc")
    Stream<IfSsqDocument> streamPending(@Param("ifResult") String ifResult);

    /** if_trc_id/if_date는 EAI 전용 추적 컬럼이라 배치가 쓰지 않음(매핑정의서 — 참조만) */
    @Modifying
    @Query("update IfSsqDocument d set d.ifResult = :ifResult where d.docId = :docId")
    int updateIfResultByDocId(@Param("docId") Integer docId, @Param("ifResult") String ifResult);

    /**
     * 미처리(N) 행 중 같은 복합키(doc_id, spec_group, level_1~4)가 2건 이상이면 — Hibernate가 동일 엔티티로
     * 인식해 값이 조용히 유실될 위험이 있어(2026-07-30 확인) — if_date가 가장 이른 1건만 남기고 나머지(중복
     * 재수신분)를 골라낸다. 로그 남긴 뒤 quarantineDuplicateKeys()로 격리 처리한다.
     */
    @Query(value = "SELECT doc_id, spec_group, level_1, level_2, level_3, level_4, if_date FROM if_r_ssq_document "
        + "WHERE if_result = 'N' AND ctid NOT IN ("
        + "  SELECT DISTINCT ON (doc_id, spec_group, level_1, level_2, level_3, level_4) ctid FROM if_r_ssq_document"
        + "  WHERE if_result = 'N' ORDER BY doc_id, spec_group, level_1, level_2, level_3, level_4,"
        + "    if_date ASC NULLS LAST, ctid ASC)",
        nativeQuery = true)
    List<Object[]> findDuplicateKeyRows();

    /** findDuplicateKeyRows()와 동일 기준으로 골라낸 행들을 'E'(격리) 처리한다(가장 이른 1건은 남김). */
    @Modifying
    @Query(value = "UPDATE if_r_ssq_document SET if_result = 'E' "
        + "WHERE if_result = 'N' AND ctid NOT IN ("
        + "  SELECT DISTINCT ON (doc_id, spec_group, level_1, level_2, level_3, level_4) ctid FROM if_r_ssq_document"
        + "  WHERE if_result = 'N' ORDER BY doc_id, spec_group, level_1, level_2, level_3, level_4,"
        + "    if_date ASC NULLS LAST, ctid ASC)",
        nativeQuery = true)
    int quarantineDuplicateKeys();
}
