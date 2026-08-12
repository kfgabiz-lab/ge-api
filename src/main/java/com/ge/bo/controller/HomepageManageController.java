package com.ge.bo.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.ge.bo.dto.HomepageManageDto;
import com.ge.bo.service.HomepageManageService;

@RestController
@RequestMapping("/api/v1/homepage-manage")
@RequiredArgsConstructor
@PreAuthorize("@securityService.isSystemAdmin(authentication)")
public class HomepageManageController {

  private final HomepageManageService homepageManageService;

  /** 설정 조회 */
  @GetMapping
  public ResponseEntity<HomepageManageDto.Response> getSettings() {
    return ResponseEntity.ok(homepageManageService.getSettings());
  }

  /** 설정 수정 */
  @PatchMapping
  public ResponseEntity<HomepageManageDto.Response> updateSettings(
      @Valid @RequestBody HomepageManageDto.UpdateRequest request) {
    return ResponseEntity.ok(homepageManageService.updateSettings(request));
  }
}
