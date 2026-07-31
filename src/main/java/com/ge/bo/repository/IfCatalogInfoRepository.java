package com.ge.bo.repository;

import com.ge.bo.entity.IfCatalogInfo;
import com.ge.bo.entity.IfCatalogInfoId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * IF_R_CATALOG_INFO(카탈로그 헤더 IF) 조회 Repository — EAI 소유 테이블, 읽기 + if_result 갱신만 수행
 */
public interface IfCatalogInfoRepository extends JpaRepository<IfCatalogInfo, IfCatalogInfoId> {

    /**
     * nahp_level_seq(복합키 구성요소)가 NULL인 행이 섞여있으면 Stream+fetchSize 커서 스트리밍에서 Hibernate가
     * 엔티티를 못 만들고 null을 반환하는 문제가 있어, 스트리밍 대신 일반 List 조회로 변경.
     */
    @Query("select c from IfCatalogInfo c where c.ifResult = :ifResult order by c.ctlgCode asc, c.nahpLevelSeq asc")
    List<IfCatalogInfo> findPending(@Param("ifResult") String ifResult);

    /**
     * if_trc_id/if_date는 EAI 전용 추적 컬럼이라 배치가 쓰지 않음(매핑정의서 — 참조만).
     * 반드시 c.ifResult = 'N'인 행만 갱신한다 — 조건 없이 ctlg_code만으로 갱신하면 같은 문서의 다른 복합키가
     * quarantineDuplicateKeys()로 이미 'E' 격리된 뒤에도 이 문서가 나중에 성공 처리될 때 그 'E' 행까지
     * 'P'로 덮어써버리는 버그가 있었다.
     */
    @Modifying
    @Query("update IfCatalogInfo c set c.ifResult = :ifResult where c.ctlgCode = :ctlgCode and c.ifResult = 'N'")
    int updateIfResultByCtlgCode(@Param("ctlgCode") String ctlgCode, @Param("ifResult") String ifResult);

    List<IfCatalogInfo> findByCtlgCodeOrderByNahpLevelSeqAsc(String ctlgCode);

    /**
     * 미처리(N) 행 중 같은 복합키(ctlg_code, nahp_level_seq)가 2건 이상이면 — Hibernate가 동일 엔티티로 인식해
     * 값이 조용히 유실될 위험이 있어 — if_date가 가장 이른 1건만 남기고 나머지(중복 재수신분)를
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
