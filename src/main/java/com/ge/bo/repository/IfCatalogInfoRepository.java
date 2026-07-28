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
}
