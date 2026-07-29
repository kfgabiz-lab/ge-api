package com.ge.bo.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.OffsetDateTime;

/**
 * 검색관리 하위 검색텍스트 — 등록/삭제만 가능(수정 없음) → 이력성 감사컬럼(생성자/생성일시)만 보유
 */
@Entity
@Table(name = "search_manage_text",
    indexes = {
        @Index(name = "idx_search_manage_text_search_manage", columnList = "search_manage_id")
    }
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@EntityListeners(AuditingEntityListener.class)
public class SearchManageText {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 소속 검색관리 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "search_manage_id", nullable = false)
    private SearchManage searchManage;

    /**
     * 검색결과 노출용 제목 — nullable.
     * 뒤늦게 추가된 컬럼이라 기존 데이터는 전부 NULL 이다. 신규 등록 시에도 선택 입력이므로 NOT NULL 로 올리면 안 된다.
     * (FO page-search 는 title 이 NULL 이면 소속 search_manage.url 을 대신 노출한다)
     */
    @Column(length = 200)
    private String title;

    /** 검색용 텍스트 */
    @Column(columnDefinition = "TEXT", nullable = false)
    private String text;

    @CreatedBy
    @Column(name = "created_by", nullable = false, updatable = false, length = 50)
    private String createdBy;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
