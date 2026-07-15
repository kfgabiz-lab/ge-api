package com.ge.bo.service;

import com.ge.bo.dto.*;
import com.ge.bo.entity.CodeDetail;
import com.ge.bo.entity.CodeGroup;
import com.ge.bo.exception.ErrorCode;
import com.ge.bo.repository.CodeDetailRepository;
import com.ge.bo.repository.CodeGroupRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class CodeService {

  private final CodeGroupRepository groupRepository;
  private final CodeDetailRepository detailRepository;
  private final MessageResourceService messageResourceService;

  private static final Pattern XSS_PATTERN = Pattern.compile("[<>\"']");

    /* ══════════ 그룹 조회 ══════════ */

  @Transactional(readOnly = true)
    public List<CodeGroupResponse> getAllGroups() {
    return groupRepository.findAllByOrderByIdDesc()
                .stream().map(CodeGroupResponse::from).toList();
  }

  @Transactional(readOnly = true)
    public CodeGroupResponse getGroup(Long id) {
    return CodeGroupResponse.from(findGroupOrThrow(id));
  }

    /* ══════════ FO 공개 코드 목록 조회 ══════════ */

    /**
     * FO 공개용 — groupCode에 속한 활성 코드 목록을 sortOrder 오름차순 {code, name} 형태로 반환
     * 기존 detailRepository를 그대로 재사용하는 얇은 읽기 메서드 (관리 메타는 노출하지 않음)
     */
  @Transactional(readOnly = true)
    public List<FoCodeResponse> getFoCodes(String groupCode) {
    List<CodeDetail> details = detailRepository.findAllByGroup_GroupCodeAndActiveTrueOrderBySortOrderAsc(groupCode);

        /* nameMsgKey 수집 → en 배치 조회(단일 호출) 후 name 값만 en으로 치환 */
    Map<String, String> enMap = messageResourceService.resolveEnMap(
                details.stream().map(CodeDetail::getNameMsgKey).toList());

    return details.stream()
                .map(d -> FoCodeResponse.from(d, enMap))
                .toList();
  }

    /* ══════════ 그룹 생성 ══════════ */

  @Transactional
    public CodeGroupResponse createGroup(CodeGroupRequest request) {
    String code = sanitizeCode(request.groupCode());

    /* groupCode 중복 확인 */
    if (groupRepository.existsByGroupCode(code)) {
      throw ErrorCode.CODE_GROUP_DUPLICATE.toException();
    }

    /* msgKey가 있으면 ko 텍스트를 groupName에 저장 (폴백용), 없으면 직접 입력값 사용 */
    String name = resolveGroupName(request.groupNameMsgKey(), request.groupName());

    CodeGroup group = CodeGroup.builder()
                .groupCode(code)
                .groupName(name)
                .groupNameMsgKey(request.groupNameMsgKey())
                .description(request.description() != null ? request.description().trim() : null)
                .active(request.active() != null ? request.active() : true)
                .build();

    return CodeGroupResponse.from(groupRepository.save(group));
  }

    /* ══════════ 그룹 수정 ══════════ */

  @Transactional
    public CodeGroupResponse updateGroup(Long id, CodeGroupRequest request) {
    CodeGroup group = findGroupOrThrow(id);

    /* groupCode는 수정 불가 — 무시 */
    String name = resolveGroupName(request.groupNameMsgKey(), request.groupName());
    group.setGroupName(name);
    group.setGroupNameMsgKey(request.groupNameMsgKey());
    group.setDescription(request.description() != null ? request.description().trim() : null);
    if (request.active() != null) {
      group.setActive(request.active());
    }

    return CodeGroupResponse.from(group);
  }

    /* ══════════ 그룹 삭제 ══════════ */

  @Transactional
    public void deleteGroup(Long id) {
    CodeGroup group = findGroupOrThrow(id);
    groupRepository.delete(group); // cascade로 details 자동 삭제
  }

    /* ══════════ 코드 추가 ══════════ */

  @Transactional
    public CodeDetailResponse createDetail(Long groupId, CodeDetailRequest request) {
    CodeGroup group = findGroupOrThrow(groupId);
    String code = sanitizeCode(request.code());
    String name = resolveDetailName(request.nameMsgKey(), request.name());

    if (detailRepository.existsByGroupAndCode(group, code)) {
      throw ErrorCode.CODE_DETAIL_DUPLICATE.toException();
    }

    CodeDetail detail = CodeDetail.builder()
                .group(group)
                .code(code)
                .name(name)
                .nameMsgKey(request.nameMsgKey())
                .description(request.description() != null ? request.description().trim() : null)
                .sortOrder(request.sortOrder() != null ? request.sortOrder() : 1)
                .active(request.active() != null ? request.active() : true)
                .extra1(trimOrNull(request.extra1()))
                .extra2(trimOrNull(request.extra2()))
                .extra3(trimOrNull(request.extra3()))
                .extra4(trimOrNull(request.extra4()))
                .extra5(trimOrNull(request.extra5()))
                .build();

    return CodeDetailResponse.from(detailRepository.save(detail));
  }

    /* ══════════ 코드 수정 ══════════ */

  @Transactional
    public CodeDetailResponse updateDetail(Long groupId, Long detailId, CodeDetailRequest request) {
    CodeGroup group = findGroupOrThrow(groupId);
    CodeDetail detail = findDetailOrThrow(detailId);

        /* 그룹 소속 확인 */
    if (!detail.getGroup().getId().equals(group.getId())) {
      throw ErrorCode.CODE_GROUP_MISMATCH.toException();
    }

    String code = sanitizeCode(request.code());
    String name = resolveDetailName(request.nameMsgKey(), request.name());

        /* 중복 체크 (자신 제외) */
    if (detailRepository.existsByGroupAndCodeAndIdNot(group, code, detailId)) {
      throw ErrorCode.CODE_DETAIL_DUPLICATE.toException();
    }

    detail.setCode(code);
    detail.setName(name);
    detail.setNameMsgKey(request.nameMsgKey());
    detail.setDescription(request.description() != null ? request.description().trim() : null);
    if (request.sortOrder() != null) {
      detail.setSortOrder(request.sortOrder());
    }
    if (request.active() != null) {
      detail.setActive(request.active());
    }
    detail.setExtra1(trimOrNull(request.extra1()));
    detail.setExtra2(trimOrNull(request.extra2()));
    detail.setExtra3(trimOrNull(request.extra3()));
    detail.setExtra4(trimOrNull(request.extra4()));
    detail.setExtra5(trimOrNull(request.extra5()));

    return CodeDetailResponse.from(detail);
  }

    /* ══════════ 코드 삭제 ══════════ */

  @Transactional
    public void deleteDetail(Long groupId, Long detailId) {
    CodeGroup group = findGroupOrThrow(groupId);
    CodeDetail detail = findDetailOrThrow(detailId);

    if (!detail.getGroup().getId().equals(group.getId())) {
      throw ErrorCode.CODE_GROUP_MISMATCH.toException();
    }

    detailRepository.delete(detail);
  }

    /* ══════════ 헬퍼 ══════════ */

  private CodeGroup findGroupOrThrow(Long id) {
    return groupRepository.findById(id).orElseThrow(ErrorCode.CODE_GROUP_NOT_FOUND::toException);
  }

  private CodeDetail findDetailOrThrow(Long id) {
    return detailRepository.findById(id).orElseThrow(ErrorCode.CODE_DETAIL_NOT_FOUND::toException);
  }

    /** 그룹명 결정: msgKey가 있으면 ko 텍스트, 없으면 직접 입력값 사용 */
  private String resolveGroupName(String msgKey, String directName) {
    if (msgKey != null && !msgKey.isBlank()) {
      return messageResourceService.resolveKo(msgKey);
    }
    return sanitizeName(directName != null ? directName : "");
  }

    /** 코드명 결정: msgKey가 있으면 ko 텍스트, 없으면 직접 입력값 사용 */
  private String resolveDetailName(String msgKey, String directName) {
    if (msgKey != null && !msgKey.isBlank()) {
      return messageResourceService.resolveKo(msgKey);
    }
    return sanitizeName(directName != null ? directName : "");
  }

    /** null 또는 빈 문자열이면 null 반환, 아니면 trim */
  private String trimOrNull(String value) {
    return (value == null || value.trim().isEmpty()) ? null : value.trim();
  }

    /** 코드값 정제: XSS 체크 + 대문자 + trim */
  private String sanitizeCode(String value) {
    if (value == null || value.trim().isEmpty()) {
      return "";
    }
    if (XSS_PATTERN.matcher(value).find()) {
      throw ErrorCode.CODE_XSS_DETECTED.toException();
    }
    return value.trim().toUpperCase();
  }

    /** 이름 정제: XSS 체크 + trim */
  private String sanitizeName(String value) {
    if (value == null || value.trim().isEmpty()) {
      return "";
    }
    if (XSS_PATTERN.matcher(value).find()) {
      throw ErrorCode.CODE_XSS_DETECTED.toException();
    }
    return value.trim();
  }
}
