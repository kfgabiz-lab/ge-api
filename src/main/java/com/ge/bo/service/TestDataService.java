package com.ge.bo.service;

/**
 * [SLUG-ENTITY-CODEGEN-AUTO-GENERATED]
 * Service — 테스트데이터
 * 생성일시: 2026-07-12T20:46:31.983774900+09:00
 * 원본 Slug Entity: id=18, tableName=test_data
 * 주의: 이 파일을 직접 수정한 뒤 다시 생성하면 수정 내용이 사라집니다.
 *       (재생성 시 기존 파일은 자동으로 *.bak.{timestamp} 로 백업됩니다.)
 */
import com.ge.bo.dto.TestDataRequest;
import com.ge.bo.dto.TestDataResponse;
import com.ge.bo.entity.TestData;
import com.ge.bo.exception.BusinessException;
import com.ge.bo.repository.TestDataRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 테스트데이터 서비스
 */
@Service
@RequiredArgsConstructor
public class TestDataService {

  private final TestDataRepository testDataRepository;

  /** 목록 조회 (페이징) */
  @Transactional(readOnly = true)
  public Page<TestDataResponse> getList(Pageable pageable) {
    return testDataRepository.findAll(pageable).map(TestDataResponse::from);
  }

  /** 단건 조회 */
  @Transactional(readOnly = true)
  public TestDataResponse getOne(Long id) {
    return TestDataResponse.from(findOrThrow(id));
  }

  /** 등록 */
  @Transactional
  public TestDataResponse create(TestDataRequest request) {
    TestData entity = TestData.builder()
        .titme(request.titme())
        .content(request.content())
        .display(request.display())
        .orderType(request.orderType())
        .dispFrom(request.dispFrom())
        .dispTo(request.dispTo())
        .products(request.products())
        .persons(request.persons())
        .info1(request.info1())
        .info2(request.info2())
        .build();
    return TestDataResponse.from(testDataRepository.save(entity));
  }

  /** 수정 */
  @Transactional
  public TestDataResponse update(Long id, TestDataRequest request) {
    TestData entity = findOrThrow(id);
    entity.setTitme(request.titme());
    entity.setContent(request.content());
    entity.setDisplay(request.display());
    entity.setOrderType(request.orderType());
    entity.setDispFrom(request.dispFrom());
    entity.setDispTo(request.dispTo());
    entity.setProducts(request.products());
    entity.setPersons(request.persons());
    entity.setInfo1(request.info1());
    entity.setInfo2(request.info2());
    return TestDataResponse.from(entity);
  }

  /** 삭제 */
  @Transactional
  public void delete(Long id) {
    testDataRepository.delete(findOrThrow(id));
  }

  /** id로 조회, 없으면 예외 */
  private TestData findOrThrow(Long id) {
    return testDataRepository.findById(id)
        .orElseThrow(() -> BusinessException.notFound("해당 데이터를 찾을 수 없습니다. id=" + id));
  }
}
