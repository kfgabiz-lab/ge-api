package com.ge.bo.repository;

import com.ge.bo.entity.TrainingRegistration;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * FO 트레이닝 세션 등록 Repository (append-only 저장 전용)
 * ContactUsInquiryRepository 와 동일 패턴.
 */
public interface TrainingRegistrationRepository extends JpaRepository<TrainingRegistration, Long> {
}
