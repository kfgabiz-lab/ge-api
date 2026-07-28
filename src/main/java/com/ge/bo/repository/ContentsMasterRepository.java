package com.ge.bo.repository;

import com.ge.bo.entity.ContentsMaster;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * contents_master(콘텐츠 원장) Repository
 */
public interface ContentsMasterRepository extends JpaRepository<ContentsMaster, Long> {

    Optional<ContentsMaster> findBySourceSystemAndSourceDocKey(String sourceSystem, String sourceDocKey);

    /** 현재 소스에 살아있는(soft-delete 안 된) 전체 문서 — CATALOG의 동일 자료코드 최신버전 판단 등에 쓴다. */
    List<ContentsMaster> findBySourceSystemAndIsDeletedFalse(String sourceSystem);
}
