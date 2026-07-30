package com.ge.bo.repository;

import com.ge.bo.entity.IfCatalogInfo;
import com.ge.bo.entity.IfCatalogInfoId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.QueryHint;

import java.util.List;
import java.util.stream.Stream;

/**
 * IF_R_CATALOG_INFO(카탈로그 헤더 IF) 조회 Repository — EAI 소유 테이블, 읽기 + if_result 갱신만 수행
 */
public interface IfCatalogInfoRepository extends JpaRepository<IfCatalogInfo, IfCatalogInfoId> {

    /**
     * 미처리(if_result='N') 행을 ctlg_code 기준 정렬로 스트리밍 조회 — 전체를 메모리에 올리지 않고
     * 문서(ctlg_code) 경계를 감지하며 순차 소비하기 위함. 반드시 읽기전용 트랜잭션 안에서 사용해야 한다.
     */
    @QueryHints(@QueryHint(name = "org.hibernate.fetchSize", value = "200"))
    @Query("select c from IfCatalogInfo c where c.ifResult = :ifResult order by c.ctlgCode asc, c.nahpLevelSeq asc")
    Stream<IfCatalogInfo> streamPending(@Param("ifResult") String ifResult);

    /** if_trc_id/if_date는 EAI 전용 추적 컬럼이라 배치가 쓰지 않음(매핑정의서 — 참조만) */
    @Modifying
    @Query("update IfCatalogInfo c set c.ifResult = :ifResult where c.ctlgCode = :ctlgCode")
    int updateIfResultByCtlgCode(@Param("ctlgCode") String ctlgCode, @Param("ifResult") String ifResult);

    List<IfCatalogInfo> findByCtlgCodeOrderByNahpLevelSeqAsc(String ctlgCode);

    /**
     * 미처리(N) 행 중 같은 복합키(ctlg_code, nahp_level_seq)가 2건 이상이면 — Hibernate가 동일 엔티티로 인식해
     * 값이 조용히 유실될 위험이 있어(2026-07-30 확인) — if_date가 가장 이른 1건만 남기고 나머지(중복 재수신분)를
     * 골라낸다. 로그 남긴 뒤 quarantineDuplicateKeys()로 격리 처리한다.
     */
    @Query(value = "SELECT ctlg_code, nahp_level_seq, if_date FROM if_r_catalog_info "
        + "WHERE if_result = 'N' AND ctid NOT IN ("
        + "  SELECT DISTINCT ON (ctlg_code, nahp_level_seq) ctid FROM if_r_catalog_info"
        + "  WHERE if_result = 'N' ORDER BY ctlg_code, nahp_level_seq, if_date ASC NULLS LAST, ctid ASC)",
        nativeQuery = true)
    List<Object[]> findDuplicateKeyRows();

    /** findDuplicateKeyRows()와 동일 기준으로 골라낸 행들을 'E'(격리) 처리한다(가장 이른 1건은 남김). */
    @Modifying
    @Query(value = "UPDATE if_r_catalog_info SET if_result = 'E' "
        + "WHERE if_result = 'N' AND ctid NOT IN ("
        + "  SELECT DISTINCT ON (ctlg_code, nahp_level_seq) ctid FROM if_r_catalog_info"
        + "  WHERE if_result = 'N' ORDER BY ctlg_code, nahp_level_seq, if_date ASC NULLS LAST, ctid ASC)",
        nativeQuery = true)
    int quarantineDuplicateKeys();
}
