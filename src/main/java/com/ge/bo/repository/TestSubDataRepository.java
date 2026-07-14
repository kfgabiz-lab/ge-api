package com.ge.bo.repository;

/**
 * [SLUG-ENTITY-CODEGEN-AUTO-GENERATED]
 * Repository — 테스트서브데이터
 * 생성일시: 2026-07-13T13:31:22.729860200+09:00
 * 원본 Slug Entity: id=19, tableName=test_products
 * 주의: 이 파일을 직접 수정한 뒤 다시 생성하면 수정 내용이 사라집니다.
 *       (재생성 시 기존 파일은 자동으로 *.bak.{timestamp} 로 백업됩니다.)
 */
import com.ge.bo.entity.TestSubData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * 테스트서브데이터 Repository
 */
public interface TestSubDataRepository
    extends JpaRepository<TestSubData, Long>, JpaSpecificationExecutor<TestSubData> {
}
