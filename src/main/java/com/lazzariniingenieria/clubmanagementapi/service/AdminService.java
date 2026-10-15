package com.lazzariniingenieria.clubmanagementapi.service;

import com.lazzariniingenieria.clubmanagementapi.dto.AdminResponse;
import com.lazzariniingenieria.clubmanagementapi.dto.CreateAdminRequest;
import com.lazzariniingenieria.clubmanagementapi.dto.ResetAdminPasswordRequest;
import com.lazzariniingenieria.clubmanagementapi.dto.UpdateAdminRequest;
import com.lazzariniingenieria.clubmanagementapi.entity.UserAccount;
import com.lazzariniingenieria.clubmanagementapi.entity.UserRole;
import com.lazzariniingenieria.clubmanagementapi.exception.AdminNotFoundException;
import com.lazzariniingenieria.clubmanagementapi.exception.DuplicateDniException;
import com.lazzariniingenieria.clubmanagementapi.exception.MemberNotFoundException;
import com.lazzariniingenieria.clubmanagementapi.mapper.AdminMapper;
import com.lazzariniingenieria.clubmanagementapi.repository.MemberRepository;
import com.lazzariniingenieria.clubmanagementapi.repository.UserAccountRepository;
import com.lazzariniingenieria.clubmanagementapi.security.AuthenticatedUser;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminService {

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final AdminMapper adminMapper;
    private final MemberRepository memberRepository;
    private final RefreshTokenService refreshTokenService;

    public AdminResponse createAdmin(AuthenticatedUser currentUser, CreateAdminRequest request) {
        Long clubId = currentUser.clubId();
        boolean dniInUse = userAccountRepository.existsByClubIdAndDni(clubId, request.dni());

        if (dniInUse) {
            throw new DuplicateDniException(request.dni());
        }

        validateMemberBelongsToClub(clubId, request.memberId());

        Instant now = Instant.now();
        UserAccount admin = UserAccount.builder()
                .clubId(clubId)
                .dni(request.dni())
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(UserRole.ADMIN)
                .email(request.email())
                .memberId(request.memberId())
                .createdAt(now)
                .updatedAt(now)
                .createdByUserId(currentUser.userAccountId())
                .updatedByUserId(currentUser.userAccountId())
                .build();

        UserAccount savedAdmin = userAccountRepository.save(admin);
        log.info("Created admin userAccountId={} for clubId={}", savedAdmin.getId(), clubId);

        return adminMapper.toResponse(savedAdmin);
    }

    public List<AdminResponse> listAdmins(Long clubId) {
        List<UserAccount> admins = userAccountRepository.findByClubIdAndRoleOrderByCreatedAtDesc(clubId, UserRole.ADMIN);

        return adminMapper.toResponseList(admins);
    }

    public AdminResponse getAdmin(Long clubId, Long adminId) {
        UserAccount admin = findAdminOrThrow(clubId, adminId);

        return adminMapper.toResponse(admin);
    }

    public AdminResponse updateAdmin(AuthenticatedUser currentUser, Long adminId, UpdateAdminRequest request) {
        Long clubId = currentUser.clubId();
        UserAccount admin = findAdminOrThrow(clubId, adminId);
        boolean dniTakenByAnotherAccount = userAccountRepository.existsByClubIdAndDniAndIdNot(clubId, request.dni(), adminId);

        if (dniTakenByAnotherAccount) {
            throw new DuplicateDniException(request.dni());
        }

        validateMemberBelongsToClub(clubId, request.memberId());

        admin.setDni(request.dni());
        admin.setEmail(request.email());
        admin.setMemberId(request.memberId());
        markUpdated(admin, currentUser.userAccountId());

        UserAccount savedAdmin = userAccountRepository.save(admin);
        log.info("Updated admin userAccountId={} for clubId={}", adminId, clubId);

        return adminMapper.toResponse(savedAdmin);
    }

    public AdminResponse deactivateAdmin(AuthenticatedUser currentUser, Long adminId) {
        Long clubId = currentUser.clubId();
        UserAccount admin = findAdminOrThrow(clubId, adminId);
        admin.setActive(false);
        markUpdated(admin, currentUser.userAccountId());

        UserAccount savedAdmin = userAccountRepository.save(admin);
        log.info("Deactivated admin userAccountId={} for clubId={}", adminId, clubId);

        return adminMapper.toResponse(savedAdmin);
    }

    public AdminResponse reactivateAdmin(AuthenticatedUser currentUser, Long adminId) {
        Long clubId = currentUser.clubId();
        UserAccount admin = findAdminOrThrow(clubId, adminId);
        admin.setActive(true);
        markUpdated(admin, currentUser.userAccountId());

        UserAccount savedAdmin = userAccountRepository.save(admin);
        log.info("Reactivated admin userAccountId={} for clubId={}", adminId, clubId);

        return adminMapper.toResponse(savedAdmin);
    }

    @Transactional
    public AdminResponse resetPassword(AuthenticatedUser currentUser, Long adminId, ResetAdminPasswordRequest request) {
        Long clubId = currentUser.clubId();
        UserAccount admin = findAdminOrThrow(clubId, adminId);
        admin.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        markUpdated(admin, currentUser.userAccountId());

        UserAccount savedAdmin = userAccountRepository.save(admin);
        refreshTokenService.revokeActiveTokens(adminId);
        log.info("Reset password for admin userAccountId={} in clubId={}", adminId, clubId);

        return adminMapper.toResponse(savedAdmin);
    }

    private void validateMemberBelongsToClub(Long clubId, Long memberId) {
        if (memberId == null) {
            return;
        }

        boolean memberExists = memberRepository.existsByIdAndClubId(memberId, clubId);

        if (!memberExists) {
            throw new MemberNotFoundException(memberId);
        }
    }

    private void markUpdated(UserAccount admin, Long actingUserId) {
        admin.setUpdatedAt(Instant.now());
        admin.setUpdatedByUserId(actingUserId);
    }

    private UserAccount findAdminOrThrow(Long clubId, Long adminId) {
        return userAccountRepository
                .findByIdAndClubIdAndRole(adminId, clubId, UserRole.ADMIN)
                .orElseThrow(() -> new AdminNotFoundException(adminId));
    }
}
