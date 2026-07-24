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

    /** 메뉴명 (한국어 기본값) */
  @Column(nullable = false, length = 50)
    private String name;

    /** 메뉴명 다국어 키 (message_resource.key 참조, 예: menu.1.name) */
  @Column(name = "name_msg_key")
    private String nameMsgKey;

    /** 메뉴 설명 (한국어 기본값, 페이지 상단 표시) */
  @Column(length = 500)
    private String description;

    /** 메뉴 설명 다국어 키 (message_resource.key 참조, 예: menu.1.description) */
  @Column(name = "description_msg_key")
    private String descriptionMsgKey;

    /** 메뉴 URL (대메뉴는 NULL 가능) */
  @Column(length = 200)
    private String url;

    /** 아이콘명 (lucide-react) — 빈 문자열이면 아이콘 없음 */
  @Column(nullable = false, length = 30)
    @Builder.Default
    private String icon = "";

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
  @Column(name = "is_visible", nullable = false)
    @Builder.Default
    private Boolean visible = true;

    /** 소속 사이트 (NULL = 전체 공통, 값 있으면 해당 사이트 전용) */
  @Column(name = "site_id")
    private Long siteId;

    /** 시스템 전용 메뉴 (SYSTEM_ADMIN만 사이드바에서 볼 수 있음) */
  @Column(name = "is_system", nullable = false)
    @Builder.Default
    private boolean isSystem = false;

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
