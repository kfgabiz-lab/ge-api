package com.ge.bo.repository;

import com.ge.bo.entity.TrainingRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * FO Training Request(비정기 교육 신청) 저장소
 * - JpaSpecificationExecutor: 관리자 조회 화면의 동적 필터링 지원(LoginLogRepository와 동일 패턴)
 */
public interface TrainingRequestRepository extends JpaRepository<TrainingRequest, Long>, JpaSpecificationExecutor<TrainingRequest> {
}
