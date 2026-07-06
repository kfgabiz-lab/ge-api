package com.ge.bo.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 검색관리 — static 페이지 검색 노출용 URL + 검색텍스트 등록 관리
 */
@Entity
@Table(name = "search_manage")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@EntityListeners(AuditingEntityListener.class)
public class SearchManage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 검색 노출 대상 URL */
    @Column(nullable = false, length = 500)
    private String url;

    /** 사용여부 */
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean active = true;

    /** 등록된 검색텍스트 목록 — cascade ALL + orphanRemoval로 일괄 저장/삭제 처리 */
    @OneToMany(mappedBy = "searchManage", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("createdAt DESC")
    @Builder.Default
    private List<SearchManageText> texts = new ArrayList<>();

    @CreatedBy
    @Column(name = "created_by", nullable = false, updatable = false, length = 50)
    private String createdBy;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @LastModifiedBy
    @Column(name = "updated_by", nullable = false, length = 50)
    private String updatedBy;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
