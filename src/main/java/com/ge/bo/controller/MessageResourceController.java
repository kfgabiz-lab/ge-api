package com.ge.bo.controller;

import com.ge.bo.dto.MessageResourceDto;
import com.ge.bo.service.MessageResourceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/message-resources")
@RequiredArgsConstructor
public class MessageResourceController {

  private final MessageResourceService messageResourceService;

  @GetMapping
    public ResponseEntity<MessageResourceDto.PageResponse> getList(
            @RequestParam(defaultValue = "")    String key,
            @RequestParam(defaultValue = "")    String ko,
            @RequestParam(defaultValue = "")    String en,
            @RequestParam(defaultValue = "")    String active,
            @RequestParam(defaultValue = "")    String resourceType,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
    return ResponseEntity.ok(messageResourceService.getList(key, ko, en, active, resourceType, pageable));
  }

  @PostMapping
    @PreAuthorize("@securityService.isSystemAdmin(authentication) or @securityService.isSuperAdmin(authentication)")
    public ResponseEntity<MessageResourceDto.Response> create(
            @Valid @RequestBody MessageResourceDto.CreateRequest request) {
    return ResponseEntity.ok(messageResourceService.create(request));
  }

  @PutMapping("/{id}")
    @PreAuthorize("@securityService.isSystemAdmin(authentication) or @securityService.isSuperAdmin(authentication)")
    public ResponseEntity<MessageResourceDto.Response> update(
            @PathVariable Long id,
            @Valid @RequestBody MessageResourceDto.UpdateRequest request) {
    return ResponseEntity.ok(messageResourceService.update(id, request));
  }

  @DeleteMapping("/{id}")
    @PreAuthorize("@securityService.isSystemAdmin(authentication) or @securityService.isSuperAdmin(authentication)")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
    messageResourceService.delete(id);
    return ResponseEntity.noContent().build();
  }
}
