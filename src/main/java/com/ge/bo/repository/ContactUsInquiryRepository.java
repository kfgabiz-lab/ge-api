package com.ge.bo.repository;

import com.ge.bo.entity.ContactUsInquiry;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * FO Contact Us 문의 접수 Repository (append-only 저장 전용)
 */
public interface ContactUsInquiryRepository extends JpaRepository<ContactUsInquiry, Long> {
}
