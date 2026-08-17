package com.lazzariniingenieria.clubmanagementapi.service;

import com.lazzariniingenieria.clubmanagementapi.dto.AdminResponse;
import com.lazzariniingenieria.clubmanagementapi.dto.CreateAdminRequest;
import com.lazzariniingenieria.clubmanagementapi.dto.UpdateAdminRequest;
import com.lazzariniingenieria.clubmanagementapi.entity.UserAccount;
import com.lazzariniingenieria.clubmanagementapi.entity.UserRole;
import com.lazzariniingenieria.clubmanagementapi.exception.AdminNotFoundException;
import com.lazzariniingenieria.clubmanagementapi.exception.DuplicateDniException;
import com.lazzariniingenieria.clubmanagementapi.mapper.AdminMapper;
import com.lazzariniingenieria.clubmanagementapi.repository.UserAccountRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminService {

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final AdminMapper adminMapper;

    public AdminResponse createAdmin(Long clubId, CreateAdminRequest request) {
        boolean dniInUse = userAccountRepository.existsByClubIdAndDni(clubId, request.dni());

        if (dniInUse) {
            throw new DuplicateDniException(request.dni());
        }

        UserAccount admin = UserAccount.builder()
                .clubId(clubId)
                .dni(request.dni())
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(UserRole.ADMIN)
                .email(request.email())
                .memberId(request.memberId())
                .createdAt(Instant.now())
                .build();

        UserAccount savedAdmin = userAccountRepository.save(admin);
        log.info("Created admin userAccountId={} for clubId={}", savedAdmin.getId(), clubId);

        return adminMapper.toResponse(savedAdmin);
    }

    public List<AdminResponse> listAdmins(Long clubId) {
        List<UserAccount> admins = userAccountRepository.findByClubIdAndRoleOrderByCreatedAtDesc(clubId, UserRole.ADMIN);
        List<AdminResponse> responses = new ArrayList<>();

        for (UserAccount admin : admins) {
            responses.add(adminMapper.toResponse(admin));
        }

        return responses;
    }

    public AdminResponse getAdmin(Long clubId, Long adminId) {
        UserAccount admin = findAdminOrThrow(clubId, adminId);

        return adminMapper.toResponse(admin);
    }

    public AdminResponse updateAdmin(Long clubId, Long adminId, UpdateAdminRequest request) {
        UserAccount admin = findAdminOrThrow(clubId, adminId);
        boolean dniTakenByAnotherAccount =
                userAccountRepository.existsByClubIdAndDniAndIdNot(clubId, request.dni(), adminId);

        if (dniTakenByAnotherAccount) {
            throw new DuplicateDniException(request.dni());
        }

        admin.setDni(request.dni());
        admin.setEmail(request.email());
        admin.setMemberId(request.memberId());

        UserAccount savedAdmin = userAccountRepository.save(admin);

        return adminMapper.toResponse(savedAdmin);
    }

    public AdminResponse deactivateAdmin(Long clubId, Long adminId) {
        UserAccount admin = findAdminOrThrow(clubId, adminId);
        admin.setActive(false);

        UserAccount savedAdmin = userAccountRepository.save(admin);
        log.info("Deactivated admin userAccountId={} for clubId={}", adminId, clubId);

        return adminMapper.toResponse(savedAdmin);
    }

    public AdminResponse reactivateAdmin(Long clubId, Long adminId) {
        UserAccount admin = findAdminOrThrow(clubId, adminId);
        admin.setActive(true);

        UserAccount savedAdmin = userAccountRepository.save(admin);
        log.info("Reactivated admin userAccountId={} for clubId={}", adminId, clubId);

        return adminMapper.toResponse(savedAdmin);
    }

    private UserAccount findAdminOrThrow(Long clubId, Long adminId) {
        return userAccountRepository.findByIdAndClubIdAndRole(adminId, clubId, UserRole.ADMIN)
                .orElseThrow(() -> new AdminNotFoundException(adminId));
    }
}
