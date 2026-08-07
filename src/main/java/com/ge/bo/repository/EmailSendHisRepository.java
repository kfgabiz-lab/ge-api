package com.ge.bo.repository;

import com.ge.bo.entity.EmailSendHis;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 이메일 발송 이력 Repository — 동적 필터(sendStatus/emailSendType/emailSendDetailType/recipientEmail/기간)를
 * Specification으로 조합해야 해서 JpaSpecificationExecutor를 함께 상속한다.
 */
public interface EmailSendHisRepository extends JpaRepository<EmailSendHis, Long>, JpaSpecificationExecutor<EmailSendHis> {

    /**
     * 재발송 시 발송상태/수정자/수정일시를 강제로 갱신한다.
     * entity.save() 방식(dirty-checking)을 쓰지 않는 이유: 재발송 결과가 이전과 같은 상태(예: 실패→실패)면
     * 필드값이 안 바뀌어 Hibernate가 UPDATE 자체를 스킵하고, 그러면 "재발송 시도" 자체가 updated_at에 기록되지 않는다.
     * 원래 요구사항(재발송 실패 시에도 시도 자체는 기록되도록 항상 갱신)을 지키려면 값 동일 여부와 무관하게 항상 UPDATE가 나가야 한다.
     */
    @Modifying
    @Query("UPDATE EmailSendHis e SET e.sendStatus = :sendStatus, e.updatedBy = :updatedBy, e.updatedAt = CURRENT_TIMESTAMP WHERE e.id = :id")
    int updateSendStatus(@Param("id") Long id, @Param("sendStatus") String sendStatus, @Param("updatedBy") String updatedBy);
}
