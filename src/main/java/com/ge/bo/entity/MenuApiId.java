package com.ge.bo.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * MenuApi 복합 PK 클래스
 */
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class MenuApiId implements Serializable {
  private Long menuId;
  private Long apiInfoId;
}
