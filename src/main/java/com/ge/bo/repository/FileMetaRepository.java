package com.ge.bo.repository;

import com.ge.bo.entity.FileMeta;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 파일 메타정보 레포지토리
 * 기본 CRUD는 JpaRepository 상속으로 제공 (커스텀 쿼리 불필요)
 */
public interface FileMetaRepository extends JpaRepository<FileMeta, Long> {
}
