package com.ge.bo.repository;

import com.ge.bo.entity.IfCertiMaster;
import com.ge.bo.entity.IfCertiMasterId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.QueryHint;

import java.util.stream.Stream;

/**
 * IF_R_CERTI_MASTER(인증서 IF) 조회 Repository — EAI 소유 테이블, 읽기 + if_result 갱신만 수행
 */
public interface IfCertiMasterRepository extends JpaRepository<IfCertiMaster, IfCertiMasterId> {

    @QueryHints(@QueryHint(name = "org.hibernate.fetchSize", value = "200"))
    @Query("select c from IfCertiMaster c where c.ifResult = :ifResult order by c.certiNo asc, c.bi asc")
    Stream<IfCertiMaster> streamPending(@Param("ifResult") String ifResult);

    /** if_trc_id/if_date는 EAI 전용 추적 컬럼이라 배치가 쓰지 않음(매핑정의서 — 참조만) */
    @Modifying
    @Query("update IfCertiMaster c set c.ifResult = :ifResult where c.certiNo = :certiNo and c.bi = :bi")
    int updateIfResultByCertiNoAndBi(@Param("certiNo") String certiNo, @Param("bi") String bi,
                                      @Param("ifResult") String ifResult);
}
