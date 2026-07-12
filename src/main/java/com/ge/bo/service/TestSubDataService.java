package com.ge.bo.service;

/**
 * [SLUG-ENTITY-CODEGEN-AUTO-GENERATED]
 * Service — 테스트서브데이터
 * 생성일시: 2026-07-12T20:46:55.027515800+09:00
 * 원본 Slug Entity: id=19, tableName=test_products
 * 주의: 이 파일을 직접 수정한 뒤 다시 생성하면 수정 내용이 사라집니다.
 *       (재생성 시 기존 파일은 자동으로 *.bak.{timestamp} 로 백업됩니다.)
 */
import com.ge.bo.dto.TestSubDataRequest;
import com.ge.bo.dto.TestSubDataResponse;
import com.ge.bo.entity.TestSubData;
import com.ge.bo.exception.BusinessException;
import com.ge.bo.repository.TestSubDataRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 테스트서브데이터 서비스
 */
@Service
@RequiredArgsConstructor
public class TestSubDataService {

  private final TestSubDataRepository testSubDataRepository;

  /** 목록 조회 (페이징) */
  @Transactional(readOnly = true)
  public Page<TestSubDataResponse> getList(Pageable pageable) {
    return testSubDataRepository.findAll(pageable).map(TestSubDataResponse::from);
  }

  /** 단건 조회 */
  @Transactional(readOnly = true)
  public TestSubDataResponse getOne(Long id) {
    return TestSubDataResponse.from(findOrThrow(id));
  }

  /** 등록 */
  @Transactional
  public TestSubDataResponse create(TestSubDataRequest request) {
    TestSubData entity = TestSubData.builder()
        .testDataId(request.testDataId())
        .name(request.name())
        .dispFrom(request.dispFrom())
        .dispTo(request.dispTo())
        .file1Id(request.file1Id())
        .file2Id(request.file2Id())
        .build();
    return TestSubDataResponse.from(testSubDataRepository.save(entity));
  }

  /** 수정 */
  @Transactional
  public TestSubDataResponse update(Long id, TestSubDataRequest request) {
    TestSubData entity = findOrThrow(id);
    entity.setTestDataId(request.testDataId());
    entity.setName(request.name());
    entity.setDispFrom(request.dispFrom());
    entity.setDispTo(request.dispTo());
    entity.setFile1Id(request.file1Id());
    entity.setFile2Id(request.file2Id());
    return TestSubDataResponse.from(entity);
  }

  /** 삭제 */
  @Transactional
  public void delete(Long id) {
    testSubDataRepository.delete(findOrThrow(id));
  }

  /** id로 조회, 없으면 예외 */
  private TestSubData findOrThrow(Long id) {
    return testSubDataRepository.findById(id)
        .orElseThrow(() -> BusinessException.notFound("해당 데이터를 찾을 수 없습니다. id=" + id));
  }
}
