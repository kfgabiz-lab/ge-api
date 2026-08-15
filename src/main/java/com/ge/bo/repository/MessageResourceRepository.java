package com.ge.bo.repository;

import com.ge.bo.entity.MessageResource;
import com.ge.bo.entity.MessageResourceType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MessageResourceRepository extends JpaRepository<MessageResource, Long> {

    /** 번역 키 중복 확인 */
  boolean existsByKey(String key);

    /** 번역 키로 단건 조회 */
  Optional<MessageResource> findByKey(String key);

    /** 번역 키 목록으로 일괄 조회 */
  List<MessageResource> findByKeyIn(List<String> keys);

    /** 번역 키 목록으로 일괄 삭제 */
  void deleteByKeyIn(List<String> keys);

    /**
     * 검색 조건 AND 조합 조회
     * - key, ko, en: 부분 일치 (LIKE), 빈 문자열이면 조건 무시
     * - active: null이면 전체 조회
     */
  @Query("""
        SELECT m FROM MessageResource m
        WHERE (:key          IS NULL OR :key  = '' OR m.key LIKE %:key%)
          AND (:ko           IS NULL OR :ko   = '' OR m.ko  LIKE %:ko%)
          AND (:en           IS NULL OR :en   = '' OR m.en  LIKE %:en%)
          AND (:active       IS NULL OR m.active       = :active)
          AND (:resourceType IS NULL OR m.resourceType = :resourceType)
        """)
    Page<MessageResource> search(
        @Param("key")          String key,
        @Param("ko")           String ko,
        @Param("en")           String en,
        @Param("active")       Boolean active,
        @Param("resourceType") MessageResourceType resourceType,
        Pageable pageable
    );
}
