package com.ge.bo.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 메뉴 엔티티 — self-referencing (대메뉴 ↔ 하위메뉴)
 */
@Entity
@Table(name = "menu",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_menu_name_parent_type",
        columnNames = {"name", "parent_id", "menu_type"}
    ),
    indexes = @Index(name = "idx_menu_type_parent", columnList = "menu_type, parent_id")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class Menu {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 메뉴명 */
    @Column(nullable = false, length = 50)
    private String name;

    /** 메뉴 URL (대메뉴는 NULL 가능) */
    @Column(length = 200)
    private String url;

    /** 아이콘명 (lucide-react) */
    @Column(nullable = false, length = 30)
    @Builder.Default
    private String icon = "Folder";

    /** 상위 메뉴 (self-join) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Menu parent;

    /** 하위 메뉴 목록 */
    @OneToMany(mappedBy = "parent", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    @Builder.Default
    private List<Menu> children = new ArrayList<>();

    /** 메뉴 구분 (BO/FO) */
    @Column(name = "menu_type", nullable = false, length = 2)
    private String menuType;

    /** 정렬 순서 */
    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private Integer sortOrder = 1;

    /** 노출 여부 */
    @Column(nullable = false)
    @Builder.Default
    private Boolean visible = true;

    /** 카테고리 여부 (MAIN, MANAGEMENT, TEMPLATES, SYSTEM 등 그룹 라벨) */
    @Column(name = "is_category", nullable = false)
    @Builder.Default
    private Boolean isCategory = false;

    /** page-data API 식별 슬러그 (영문·숫자·하이픈·언더스코어, nullable) */
    @Column(length = 100)
    private String slug;

    @CreatedBy
    @Column(name = "created_by", nullable = false, updatable = false, length = 50)
    private String createdBy;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedBy
    @Column(name = "updated_by", nullable = false, length = 50)
    private String updatedBy;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /* ── 비즈니스 메서드 ── */

    /** 대메뉴 여부 */
    public boolean isParent() {
        return parent == null;
    }

    /** 시스템 메뉴 여부 (삭제 불가) */
    public boolean isMenuManagement() {
        return "/admin/settings/menus".equals(url);
    }
}
