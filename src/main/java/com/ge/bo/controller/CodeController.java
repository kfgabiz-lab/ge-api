package com.ge.bo.controller;

import com.ge.bo.annotation.ApiLinkedEntity;
import com.ge.bo.dto.*;
import com.ge.bo.service.CodeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/codes")
@RequiredArgsConstructor
@ApiLinkedEntity("CodeGroup")
public class CodeController {

  private final CodeService codeService;

  @GetMapping
  public ResponseEntity<List<CodeGroupResponse>> getAllGroups() {
    return ResponseEntity.ok(codeService.getAllGroups());
  }

  @GetMapping("/{id}")
  public ResponseEntity<CodeGroupResponse> getGroup(@PathVariable Long id) {
    return ResponseEntity.ok(codeService.getGroup(id));
  }

  @PostMapping
  public ResponseEntity<CodeGroupResponse> createGroup(
      @Valid @RequestBody CodeGroupRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(codeService.createGroup(request));
  }

  @PutMapping("/{id}")
  public ResponseEntity<CodeGroupResponse> updateGroup(
      @PathVariable Long id,
      @Valid @RequestBody CodeGroupRequest request) {
    return ResponseEntity.ok(codeService.updateGroup(id, request));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> deleteGroup(@PathVariable Long id) {
    codeService.deleteGroup(id);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{groupId}/details")
  public ResponseEntity<CodeDetailResponse> createDetail(
      @PathVariable Long groupId,
      @Valid @RequestBody CodeDetailRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(codeService.createDetail(groupId, request));
  }

  @PutMapping("/{groupId}/details/{detailId}")
  public ResponseEntity<CodeDetailResponse> updateDetail(
      @PathVariable Long groupId,
      @PathVariable Long detailId,
      @Valid @RequestBody CodeDetailRequest request) {
    return ResponseEntity.ok(codeService.updateDetail(groupId, detailId, request));
  }

  @DeleteMapping("/{groupId}/details/{detailId}")
  public ResponseEntity<Void> deleteDetail(
      @PathVariable Long groupId,
      @PathVariable Long detailId) {
    codeService.deleteDetail(groupId, detailId);
    return ResponseEntity.noContent().build();
  }
}
