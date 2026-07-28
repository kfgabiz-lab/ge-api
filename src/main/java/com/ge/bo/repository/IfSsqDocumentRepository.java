package com.ge.bo.repository;

import com.ge.bo.entity.IfSsqDocument;
import com.ge.bo.entity.IfSsqDocumentId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.QueryHint;

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
}
