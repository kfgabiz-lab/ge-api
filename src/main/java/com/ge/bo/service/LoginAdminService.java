package com.ge.bo.service;

import com.ge.bo.entity.AdminUser;
import com.ge.bo.repository.AdminRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class LoginAdminService {

    private final AdminRepository adminRepository;

    /** 아이디/비밀번호 오류 시 로그인 실패 횟수 +1 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int incrementFailure(Long adminId, int maxAttempts) {
        AdminUser admin = adminRepository.findById(adminId)
                .orElseThrow(() -> new IllegalStateException("Admin not found: " + adminId));
        int attempts = admin.getFailedLoginAttempts() + 1;
        admin.setFailedLoginAttempts(attempts);
        adminRepository.save(admin);
        return attempts;
    }

    /** 신규 SSO 유저 저장 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveNewSsoUser(AdminUser admin) {
        adminRepository.save(admin);
    }

    /** 퇴사자/휴직자 등 SSO FAIL 시 계정 비활성화 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deactivateUser(Long adminId) {
        adminRepository.findById(adminId).ifPresent(admin -> {
            admin.setActive(false);
            adminRepository.save(admin);
        });
    }

    /** 부서 변경 감지 시 부서정보 갱신 + 비활성화 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateDeptChange(Long adminId, String deptCode, String deptName, String name) {
        adminRepository.findById(adminId).ifPresent(admin -> {
            admin.setDeptCode(deptCode);
            if (deptName != null) admin.setDeptName(deptName);
            if (name != null) admin.setName(name);
            admin.setActive(false);
            adminRepository.save(admin);
        });
    }

    /** 성공 시 실패 카운터 초기화 + lastLoginAt 갱신 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSuccess(Long adminId) {
        AdminUser admin = adminRepository.findById(adminId)
                .orElseThrow(() -> new IllegalStateException("Admin not found: " + adminId));
        admin.setFailedLoginAttempts(0);
        admin.setLastLoginAt(LocalDateTime.now());
        adminRepository.save(admin);
    }
}
