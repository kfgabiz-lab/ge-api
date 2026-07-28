package com.ge.bo.repository;

import com.ge.bo.entity.IntegrationContents;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface IntegrationContentsRepository extends JpaRepository<IntegrationContents, Long> {

    /** content_id(원본 page_data.id) 기준 조회 — upsert/소프트삭제 판별용 */
  Optional<IntegrationContents> findByContentId(String contentId);
}
