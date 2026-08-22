package com.lazzariniingenieria.clubmanagementapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lazzariniingenieria.clubmanagementapi.dto.AdminResponse;
import com.lazzariniingenieria.clubmanagementapi.dto.CreateAdminRequest;
import com.lazzariniingenieria.clubmanagementapi.dto.UpdateAdminRequest;
import com.lazzariniingenieria.clubmanagementapi.entity.UserAccount;
import com.lazzariniingenieria.clubmanagementapi.entity.UserRole;
import com.lazzariniingenieria.clubmanagementapi.exception.AdminNotFoundException;
import com.lazzariniingenieria.clubmanagementapi.exception.DuplicateDniException;
import com.lazzariniingenieria.clubmanagementapi.mapper.AdminMapper;
import com.lazzariniingenieria.clubmanagementapi.mapper.AdminMapperImpl;
import com.lazzariniingenieria.clubmanagementapi.repository.UserAccountRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    private static final Long CLUB_ID = 1L;
    private static final Long ADMIN_ID = 10L;
    private static final String DNI = "30111222";
    private static final String RAW_PASSWORD = "s3cr3t123";
    private static final String HASHED_PASSWORD = "hashed-password";
    private static final Instant CREATED_AT = Instant.parse("2026-01-01T00:00:00Z");

    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private final AdminMapper adminMapper = new AdminMapperImpl();

    private AdminService adminService;

    @BeforeEach
    void setUp() {
        adminService = new AdminService(userAccountRepository, passwordEncoder, adminMapper);
    }

    @Test
    void shouldCreateAdminWhenDniIsNotTaken() {
        CreateAdminRequest request = new CreateAdminRequest(DNI, RAW_PASSWORD, "admin@example.com", 5L);
        when(userAccountRepository.existsByClubIdAndDni(CLUB_ID, DNI)).thenReturn(false);
        when(passwordEncoder.encode(RAW_PASSWORD)).thenReturn(HASHED_PASSWORD);
        when(userAccountRepository.save(any(UserAccount.class))).thenReturn(adminUser());

        AdminResponse response = adminService.createAdmin(CLUB_ID, request);

        ArgumentCaptor<UserAccount> savedAdminCaptor = ArgumentCaptor.forClass(UserAccount.class);
        verify(userAccountRepository).save(savedAdminCaptor.capture());
        UserAccount savedAdmin = savedAdminCaptor.getValue();

        assertThat(savedAdmin.getClubId()).isEqualTo(CLUB_ID);
        assertThat(savedAdmin.getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(savedAdmin.getPasswordHash()).isEqualTo(HASHED_PASSWORD);
        assertThat(savedAdmin.isActive()).isTrue();
        assertThat(response.id()).isEqualTo(ADMIN_ID);
        assertThat(response.dni()).isEqualTo(DNI);
        assertThat(response.email()).isEqualTo("admin@example.com");
        assertThat(response.memberId()).isEqualTo(5L);
        assertThat(response.active()).isTrue();
    }

    @Test
    void shouldThrowDuplicateDniWhenCreatingWithDniAlreadyUsedInClub() {
        CreateAdminRequest request = new CreateAdminRequest(DNI, RAW_PASSWORD, null, null);
        when(userAccountRepository.existsByClubIdAndDni(CLUB_ID, DNI)).thenReturn(true);

        assertThatThrownBy(() -> adminService.createAdmin(CLUB_ID, request)).isInstanceOf(DuplicateDniException.class);

        verify(userAccountRepository, never()).save(any());
    }

    @Test
    void shouldReturnAllAdminsForClub() {
        when(userAccountRepository.findByClubIdAndRoleOrderByCreatedAtDesc(CLUB_ID, UserRole.ADMIN)).thenReturn(List.of(adminUser()));

        List<AdminResponse> response = adminService.listAdmins(CLUB_ID);

        assertThat(response).hasSize(1);
        assertThat(response.get(0).id()).isEqualTo(ADMIN_ID);
    }

    @Test
    void shouldReturnAdminWhenFound() {
        when(userAccountRepository.findByIdAndClubIdAndRole(ADMIN_ID, CLUB_ID, UserRole.ADMIN)).thenReturn(Optional.of(adminUser()));

        AdminResponse response = adminService.getAdmin(CLUB_ID, ADMIN_ID);

        assertThat(response.id()).isEqualTo(ADMIN_ID);
    }

    @Test
    void shouldThrowAdminNotFoundWhenGettingMissingAdmin() {
        when(userAccountRepository.findByIdAndClubIdAndRole(ADMIN_ID, CLUB_ID, UserRole.ADMIN)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminService.getAdmin(CLUB_ID, ADMIN_ID)).isInstanceOf(AdminNotFoundException.class);
    }

    @Test
    void shouldUpdateAdminProfileWhenDniIsAvailable() {
        UserAccount existingAdmin = adminUser();
        UpdateAdminRequest request = new UpdateAdminRequest("30999888", "new@example.com", 9L);
        when(userAccountRepository.findByIdAndClubIdAndRole(ADMIN_ID, CLUB_ID, UserRole.ADMIN)).thenReturn(Optional.of(existingAdmin));
        when(userAccountRepository.existsByClubIdAndDniAndIdNot(CLUB_ID, "30999888", ADMIN_ID)).thenReturn(false);
        when(userAccountRepository.save(existingAdmin)).thenReturn(existingAdmin);

        AdminResponse response = adminService.updateAdmin(CLUB_ID, ADMIN_ID, request);

        assertThat(response.dni()).isEqualTo("30999888");
        assertThat(response.email()).isEqualTo("new@example.com");
        assertThat(response.memberId()).isEqualTo(9L);
    }

    @Test
    void shouldUpdateAdminProfileWhenDniRemainsUnchanged() {
        UserAccount existingAdmin = adminUser();
        UpdateAdminRequest request = new UpdateAdminRequest(DNI, "updated@example.com", 12L);
        when(userAccountRepository.findByIdAndClubIdAndRole(ADMIN_ID, CLUB_ID, UserRole.ADMIN)).thenReturn(Optional.of(existingAdmin));
        when(userAccountRepository.existsByClubIdAndDniAndIdNot(CLUB_ID, DNI, ADMIN_ID)).thenReturn(false);
        when(userAccountRepository.save(existingAdmin)).thenReturn(existingAdmin);

        AdminResponse response = adminService.updateAdmin(CLUB_ID, ADMIN_ID, request);

        assertThat(response.dni()).isEqualTo(DNI);
        assertThat(response.email()).isEqualTo("updated@example.com");
        assertThat(response.memberId()).isEqualTo(12L);
    }

    @Test
    void shouldThrowDuplicateDniWhenUpdatingToDniUsedByAnotherAdmin() {
        UserAccount existingAdmin = adminUser();
        UpdateAdminRequest request = new UpdateAdminRequest("30999888", "new@example.com", 9L);
        when(userAccountRepository.findByIdAndClubIdAndRole(ADMIN_ID, CLUB_ID, UserRole.ADMIN)).thenReturn(Optional.of(existingAdmin));
        when(userAccountRepository.existsByClubIdAndDniAndIdNot(CLUB_ID, "30999888", ADMIN_ID)).thenReturn(true);

        assertThatThrownBy(() -> adminService.updateAdmin(CLUB_ID, ADMIN_ID, request)).isInstanceOf(DuplicateDniException.class);

        verify(userAccountRepository, never()).save(any());
    }

    @Test
    void shouldThrowAdminNotFoundWhenUpdatingMissingAdmin() {
        UpdateAdminRequest request = new UpdateAdminRequest("30999888", "new@example.com", 9L);
        when(userAccountRepository.findByIdAndClubIdAndRole(ADMIN_ID, CLUB_ID, UserRole.ADMIN)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminService.updateAdmin(CLUB_ID, ADMIN_ID, request)).isInstanceOf(AdminNotFoundException.class);
    }

    @Test
    void shouldDeactivateAdminWhenFound() {
        UserAccount existingAdmin = adminUser();
        when(userAccountRepository.findByIdAndClubIdAndRole(ADMIN_ID, CLUB_ID, UserRole.ADMIN)).thenReturn(Optional.of(existingAdmin));
        when(userAccountRepository.save(existingAdmin)).thenReturn(existingAdmin);

        AdminResponse response = adminService.deactivateAdmin(CLUB_ID, ADMIN_ID);

        assertThat(existingAdmin.isActive()).isFalse();
        assertThat(response.active()).isFalse();
    }

    @Test
    void shouldThrowAdminNotFoundWhenDeactivatingMissingAdmin() {
        when(userAccountRepository.findByIdAndClubIdAndRole(ADMIN_ID, CLUB_ID, UserRole.ADMIN)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminService.deactivateAdmin(CLUB_ID, ADMIN_ID)).isInstanceOf(AdminNotFoundException.class);
    }

    @Test
    void shouldReactivateAdminWhenFound() {
        UserAccount existingAdmin = adminUser();
        existingAdmin.setActive(false);
        when(userAccountRepository.findByIdAndClubIdAndRole(ADMIN_ID, CLUB_ID, UserRole.ADMIN)).thenReturn(Optional.of(existingAdmin));
        when(userAccountRepository.save(existingAdmin)).thenReturn(existingAdmin);

        AdminResponse response = adminService.reactivateAdmin(CLUB_ID, ADMIN_ID);

        assertThat(existingAdmin.isActive()).isTrue();
        assertThat(response.active()).isTrue();
    }

    @Test
    void shouldThrowAdminNotFoundWhenReactivatingMissingAdmin() {
        when(userAccountRepository.findByIdAndClubIdAndRole(ADMIN_ID, CLUB_ID, UserRole.ADMIN)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminService.reactivateAdmin(CLUB_ID, ADMIN_ID)).isInstanceOf(AdminNotFoundException.class);
    }

    private UserAccount adminUser() {
        return UserAccount.builder()
                .id(ADMIN_ID)
                .clubId(CLUB_ID)
                .memberId(5L)
                .dni(DNI)
                .passwordHash(HASHED_PASSWORD)
                .role(UserRole.ADMIN)
                .email("admin@example.com")
                .createdAt(CREATED_AT)
                .build();
    }
}
