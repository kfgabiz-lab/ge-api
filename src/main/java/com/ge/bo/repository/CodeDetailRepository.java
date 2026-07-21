package com.ge.bo.repository;

import com.ge.bo.entity.CodeDetail;
import com.ge.bo.entity.CodeGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CodeDetailRepository extends JpaRepository<CodeDetail, Long> {

  boolean existsByGroupAndCode(CodeGroup group, String code);

  boolean existsByGroupAndCodeAndIdNot(CodeGroup group, String code, Long id);

  Optional<CodeDetail> findFirstByGroup_GroupCodeAndActiveTrue(String groupCode);

  /**
   * FO 공개 코드 목록 조회용 — groupCode에 속한 활성(is_active=true) 코드를 sortOrder 오름차순으로 반환
   */
  List<CodeDetail> findAllByGroup_GroupCodeAndActiveTrueOrderBySortOrderAsc(String groupCode);

  /**
   * groupCode + code 조합이 활성(is_active=true) 코드로 존재하는지 검증
   * (Contact Us 문의 접수의 inquiry_type/country 공통코드 실시간 검증용)
   */
  boolean existsByGroup_GroupCodeAndCodeAndActiveTrue(String groupCode, String code);
}
