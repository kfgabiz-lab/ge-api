package com.ge.bo.repository;

/**
 * [SLUG-ENTITY-CODEGEN-AUTO-GENERATED]
 * Repository — 테스트데이터
 * 생성일시: 2026-07-13T13:30:49.157417100+09:00
 * 원본 Slug Entity: id=18, tableName=test_data
 * 주의: 이 파일을 직접 수정한 뒤 다시 생성하면 수정 내용이 사라집니다.
 *       (재생성 시 기존 파일은 자동으로 *.bak.{timestamp} 로 백업됩니다.)
 */
import com.ge.bo.entity.TestData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * 테스트데이터 Repository
 */
public interface TestDataRepository
    extends JpaRepository<TestData, Long>, JpaSpecificationExecutor<TestData> {
}
