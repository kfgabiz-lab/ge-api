package com.ge.bo.repository;

import com.ge.bo.entity.TrainingRequest;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * FO Training Request(비정기 교육 신청) 저장소
 * 이력성(append-only) 테이블이라 기본 save 만 사용한다(커스텀 쿼리 없음).
 */
public interface TrainingRequestRepository extends JpaRepository<TrainingRequest, Long> {
}
