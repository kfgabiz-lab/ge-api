package com.ge.bo.repository;

import com.ge.bo.entity.IfCatalogFileInfo;
import com.ge.bo.entity.IfCatalogFileInfoId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.QueryHint;

import java.util.List;
import java.util.stream.Stream;

/**
 * IF_R_CATALOG_FILE_INFO(카탈로그 파일 IF) 조회 Repository — EAI 소유 테이블, 읽기 + if_result 갱신만 수행
 */
public interface IfCatalogFileInfoRepository extends JpaRepository<IfCatalogFileInfo, IfCatalogFileInfoId> {

    @QueryHints(@QueryHint(name = "org.hibernate.fetchSize", value = "200"))
    @Query("select f from IfCatalogFileInfo f where f.ifResult = :ifResult order by f.ctlgCode asc, f.dataCode asc")
    Stream<IfCatalogFileInfo> streamPending(@Param("ifResult") String ifResult);

    /** if_trc_id/if_date는 EAI 전용 추적 컬럼이라 배치가 쓰지 않음(매핑정의서 — 참조만) */
    @Modifying
    @Query("update IfCatalogFileInfo f set f.ifResult = :ifResult where f.ctlgCode = :ctlgCode")
    int updateIfResultByCtlgCode(@Param("ctlgCode") String ctlgCode, @Param("ifResult") String ifResult);

    List<IfCatalogFileInfo> findByCtlgCodeOrderByDataCodeAsc(String ctlgCode);
}
