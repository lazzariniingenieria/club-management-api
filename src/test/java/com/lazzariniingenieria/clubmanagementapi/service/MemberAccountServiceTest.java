package com.lazzariniingenieria.clubmanagementapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.lazzariniingenieria.clubmanagementapi.dto.CreateMemberAccountRequest;
import com.lazzariniingenieria.clubmanagementapi.dto.MemberAccountResponse;
import com.lazzariniingenieria.clubmanagementapi.dto.ResetMemberAccountPasswordRequest;
import com.lazzariniingenieria.clubmanagementapi.entity.Club;
import com.lazzariniingenieria.clubmanagementapi.entity.Member;
import com.lazzariniingenieria.clubmanagementapi.entity.MemberStatus;
import com.lazzariniingenieria.clubmanagementapi.entity.UserAccount;
import com.lazzariniingenieria.clubmanagementapi.entity.UserRole;
import com.lazzariniingenieria.clubmanagementapi.exception.ClubNotFoundException;
import com.lazzariniingenieria.clubmanagementapi.exception.DuplicateDniException;
import com.lazzariniingenieria.clubmanagementapi.exception.InvalidMemberAccountException;
import com.lazzariniingenieria.clubmanagementapi.exception.MemberAccountNotFoundException;
import com.lazzariniingenieria.clubmanagementapi.exception.MemberAlreadyHasAccountException;
import com.lazzariniingenieria.clubmanagementapi.exception.MemberNotFoundException;
import com.lazzariniingenieria.clubmanagementapi.mapper.MemberAccountMapper;
import com.lazzariniingenieria.clubmanagementapi.mapper.MemberAccountMapperImpl;
import com.lazzariniingenieria.clubmanagementapi.repository.ClubRepository;
import com.lazzariniingenieria.clubmanagementapi.repository.MemberRepository;
import com.lazzariniingenieria.clubmanagementapi.repository.UserAccountRepository;
import com.lazzariniingenieria.clubmanagementapi.security.AuthenticatedUser;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class MemberAccountServiceTest {

    private static final Long CLUB_ID = 1L;
    private static final Long MEMBER_ID = 34L;
    private static final Long ACCOUNT_ID = 40L;
    private static final Long ACTING_USER_ID = 12L;
    private static final Long PREVIOUS_ACTOR_ID = 99L;
    private static final String DNI = "30111222";
    private static final String RAW_PASSWORD = "s3cr3t123";
    private static final String HASHED_PASSWORD = "hashed-password";
    private static final String NEW_RAW_PASSWORD = "n3wS3cr3t";
    private static final String NEW_HASHED_PASSWORD = "new-hashed-password";
    private static final Instant CREATED_AT = Instant.parse("2026-11-01T00:00:00Z");
    private static final AuthenticatedUser CURRENT_USER = new AuthenticatedUser(ACTING_USER_ID, CLUB_ID, UserRole.ADMIN, null);
    private static final CreateMemberAccountRequest CREATE_REQUEST = new CreateMemberAccountRequest(RAW_PASSWORD);
    private static final ResetMemberAccountPasswordRequest RESET_REQUEST = new ResetMemberAccountPasswordRequest(NEW_RAW_PASSWORD);

    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private ClubRepository clubRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private RefreshTokenService refreshTokenService;

    private final MemberAccountMapper memberAccountMapper = new MemberAccountMapperImpl();

    private MemberAccountService memberAccountService;

    @BeforeEach
    void setUp() {
        memberAccountService = new MemberAccountService(userAccountRepository, memberRepository, clubRepository, passwordEncoder,
                memberAccountMapper, refreshTokenService);
    }

    @Test
    void shouldReturnTheAccountWhenMemberHasOne() {
        when(memberRepository.existsByIdAndClubId(MEMBER_ID, CLUB_ID)).thenReturn(true);
        when(userAccountRepository.findByMemberIdAndClubId(MEMBER_ID, CLUB_ID)).thenReturn(Optional.of(account(UserRole.MEMBER)));

        Optional<MemberAccountResponse> response = memberAccountService.getAccount(CLUB_ID, MEMBER_ID);

        assertThat(response).isPresent();
        assertThat(response.get().id()).isEqualTo(ACCOUNT_ID);
        assertThat(response.get().memberId()).isEqualTo(MEMBER_ID);
        assertThat(response.get().dni()).isEqualTo(DNI);
        assertThat(response.get().role()).isEqualTo(UserRole.MEMBER);
        assertThat(response.get().active()).isTrue();
        assertThat(response.get().createdByUserId()).isEqualTo(PREVIOUS_ACTOR_ID);
    }

    @Test
    void shouldReturnTheAdminAccountWhenMemberIsAlsoAnAdmin() {
        when(memberRepository.existsByIdAndClubId(MEMBER_ID, CLUB_ID)).thenReturn(true);
        when(userAccountRepository.findByMemberIdAndClubId(MEMBER_ID, CLUB_ID)).thenReturn(Optional.of(account(UserRole.ADMIN)));

        Optional<MemberAccountResponse> response = memberAccountService.getAccount(CLUB_ID, MEMBER_ID);

        assertThat(response).isPresent();
        assertThat(response.get().role()).isEqualTo(UserRole.ADMIN);
    }

    @Test
    void shouldReturnEmptyWhenMemberHasNoAccount() {
        when(memberRepository.existsByIdAndClubId(MEMBER_ID, CLUB_ID)).thenReturn(true);
        when(userAccountRepository.findByMemberIdAndClubId(MEMBER_ID, CLUB_ID)).thenReturn(Optional.empty());

        Optional<MemberAccountResponse> response = memberAccountService.getAccount(CLUB_ID, MEMBER_ID);

        assertThat(response).isEmpty();
    }

    @Test
    void shouldThrowMemberNotFoundWhenGettingAccountOfMemberFromAnotherClub() {
        when(memberRepository.existsByIdAndClubId(MEMBER_ID, CLUB_ID)).thenReturn(false);

        assertThatThrownBy(() -> memberAccountService.getAccount(CLUB_ID, MEMBER_ID)).isInstanceOf(MemberNotFoundException.class);

        verifyNoInteractions(userAccountRepository);
    }

    @Test
    void shouldNotCheckTheMemberAppFlagWhenGettingAccount() {
        when(memberRepository.existsByIdAndClubId(MEMBER_ID, CLUB_ID)).thenReturn(true);
        when(userAccountRepository.findByMemberIdAndClubId(MEMBER_ID, CLUB_ID)).thenReturn(Optional.empty());

        memberAccountService.getAccount(CLUB_ID, MEMBER_ID);

        verifyNoInteractions(clubRepository);
    }

    @Test
    void shouldCreateMemberAccountWithTheMemberDniAndTheHashedPassword() {
        stubCreatableMember(member(MemberStatus.ACTIVE, DNI));
        when(passwordEncoder.encode(RAW_PASSWORD)).thenReturn(HASHED_PASSWORD);
        when(userAccountRepository.save(any(UserAccount.class))).thenReturn(account(UserRole.MEMBER));

        memberAccountService.createAccount(CURRENT_USER, MEMBER_ID, CREATE_REQUEST);

        UserAccount savedAccount = capturedSavedAccount();
        assertThat(savedAccount.getClubId()).isEqualTo(CLUB_ID);
        assertThat(savedAccount.getMemberId()).isEqualTo(MEMBER_ID);
        assertThat(savedAccount.getDni()).isEqualTo(DNI);
        assertThat(savedAccount.getPasswordHash()).isEqualTo(HASHED_PASSWORD);
        assertThat(savedAccount.getRole()).isEqualTo(UserRole.MEMBER);
        assertThat(savedAccount.getEmail()).isNull();
        assertThat(savedAccount.isActive()).isTrue();
    }

    @Test
    void shouldRecordWhoCreatedTheMemberAccountAndWhen() {
        Instant beforeCreation = Instant.now();
        stubCreatableMember(member(MemberStatus.ACTIVE, DNI));
        when(passwordEncoder.encode(RAW_PASSWORD)).thenReturn(HASHED_PASSWORD);
        when(userAccountRepository.save(any(UserAccount.class))).thenReturn(account(UserRole.MEMBER));

        memberAccountService.createAccount(CURRENT_USER, MEMBER_ID, CREATE_REQUEST);

        UserAccount savedAccount = capturedSavedAccount();
        assertThat(savedAccount.getCreatedAt()).isBetween(beforeCreation, Instant.now());
        assertThat(savedAccount.getUpdatedAt()).isEqualTo(savedAccount.getCreatedAt());
        assertThat(savedAccount.getCreatedByUserId()).isEqualTo(ACTING_USER_ID);
        assertThat(savedAccount.getUpdatedByUserId()).isEqualTo(ACTING_USER_ID);
    }

    @Test
    void shouldReturnTheSavedAccountWhenCreatingIt() {
        stubCreatableMember(member(MemberStatus.ACTIVE, DNI));
        when(passwordEncoder.encode(RAW_PASSWORD)).thenReturn(HASHED_PASSWORD);
        when(userAccountRepository.save(any(UserAccount.class))).thenReturn(account(UserRole.MEMBER));

        MemberAccountResponse response = memberAccountService.createAccount(CURRENT_USER, MEMBER_ID, CREATE_REQUEST);

        assertThat(response.id()).isEqualTo(ACCOUNT_ID);
        assertThat(response.memberId()).isEqualTo(MEMBER_ID);
        assertThat(response.role()).isEqualTo(UserRole.MEMBER);
    }

    @Test
    void shouldRejectCreationWhenTheClubHasTheMemberAppDisabled() {
        when(clubRepository.findById(CLUB_ID)).thenReturn(Optional.of(club(false)));

        assertThatThrownBy(() -> memberAccountService.createAccount(CURRENT_USER, MEMBER_ID, CREATE_REQUEST))
                .isInstanceOf(InvalidMemberAccountException.class)
                .hasMessage("member app is not enabled for this club");

        verifyNoInteractions(memberRepository, userAccountRepository);
    }

    @Test
    void shouldThrowClubNotFoundWhenTokenClubHasNoRow() {
        when(clubRepository.findById(CLUB_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> memberAccountService.createAccount(CURRENT_USER, MEMBER_ID, CREATE_REQUEST))
                .isInstanceOf(ClubNotFoundException.class);

        verifyNoInteractions(userAccountRepository);
    }

    @Test
    void shouldThrowMemberNotFoundWhenCreatingAccountForMemberFromAnotherClub() {
        when(clubRepository.findById(CLUB_ID)).thenReturn(Optional.of(club(true)));
        when(memberRepository.findByIdAndClubId(MEMBER_ID, CLUB_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> memberAccountService.createAccount(CURRENT_USER, MEMBER_ID, CREATE_REQUEST))
                .isInstanceOf(MemberNotFoundException.class);

        verifyNoInteractions(userAccountRepository);
    }

    @Test
    void shouldRejectCreationWhenMemberIsInactive() {
        when(clubRepository.findById(CLUB_ID)).thenReturn(Optional.of(club(true)));
        when(memberRepository.findByIdAndClubId(MEMBER_ID, CLUB_ID)).thenReturn(Optional.of(member(MemberStatus.INACTIVE, DNI)));

        assertThatThrownBy(() -> memberAccountService.createAccount(CURRENT_USER, MEMBER_ID, CREATE_REQUEST))
                .isInstanceOf(InvalidMemberAccountException.class)
                .hasMessage("member 34 is inactive");

        verifyNoInteractions(userAccountRepository);
    }

    @Test
    void shouldRejectCreationWhenMemberHasNoDni() {
        when(clubRepository.findById(CLUB_ID)).thenReturn(Optional.of(club(true)));
        when(memberRepository.findByIdAndClubId(MEMBER_ID, CLUB_ID)).thenReturn(Optional.of(member(MemberStatus.ACTIVE, null)));

        assertThatThrownBy(() -> memberAccountService.createAccount(CURRENT_USER, MEMBER_ID, CREATE_REQUEST))
                .isInstanceOf(InvalidMemberAccountException.class)
                .hasMessage("member 34 has no dni");

        verifyNoInteractions(userAccountRepository);
    }

    @Test
    void shouldReportTheInactiveMemberBeforeTheMissingDni() {
        when(clubRepository.findById(CLUB_ID)).thenReturn(Optional.of(club(true)));
        when(memberRepository.findByIdAndClubId(MEMBER_ID, CLUB_ID)).thenReturn(Optional.of(member(MemberStatus.INACTIVE, null)));

        assertThatThrownBy(() -> memberAccountService.createAccount(CURRENT_USER, MEMBER_ID, CREATE_REQUEST))
                .hasMessage("member 34 is inactive");
    }

    @Test
    void shouldThrowMemberAlreadyHasAccountWhenMemberAlreadyHasOne() {
        when(clubRepository.findById(CLUB_ID)).thenReturn(Optional.of(club(true)));
        when(memberRepository.findByIdAndClubId(MEMBER_ID, CLUB_ID)).thenReturn(Optional.of(member(MemberStatus.ACTIVE, DNI)));
        when(userAccountRepository.existsByMemberIdAndClubId(MEMBER_ID, CLUB_ID)).thenReturn(true);

        assertThatThrownBy(() -> memberAccountService.createAccount(CURRENT_USER, MEMBER_ID, CREATE_REQUEST))
                .isInstanceOf(MemberAlreadyHasAccountException.class)
                .hasMessage("member 34 already has an account");

        verify(userAccountRepository, never()).save(any());
    }

    @Test
    void shouldReportTheExistingAccountBeforeLookingForTheDni() {
        when(clubRepository.findById(CLUB_ID)).thenReturn(Optional.of(club(true)));
        when(memberRepository.findByIdAndClubId(MEMBER_ID, CLUB_ID)).thenReturn(Optional.of(member(MemberStatus.ACTIVE, DNI)));
        when(userAccountRepository.existsByMemberIdAndClubId(MEMBER_ID, CLUB_ID)).thenReturn(true);

        assertThatThrownBy(() -> memberAccountService.createAccount(CURRENT_USER, MEMBER_ID, CREATE_REQUEST))
                .isInstanceOf(MemberAlreadyHasAccountException.class);

        verify(userAccountRepository, never()).existsByClubIdAndDni(anyLong(), anyString());
    }

    @Test
    void shouldThrowDuplicateDniWhenAnotherAccountOfTheClubUsesTheMemberDni() {
        when(clubRepository.findById(CLUB_ID)).thenReturn(Optional.of(club(true)));
        when(memberRepository.findByIdAndClubId(MEMBER_ID, CLUB_ID)).thenReturn(Optional.of(member(MemberStatus.ACTIVE, DNI)));
        when(userAccountRepository.existsByMemberIdAndClubId(MEMBER_ID, CLUB_ID)).thenReturn(false);
        when(userAccountRepository.existsByClubIdAndDni(CLUB_ID, DNI)).thenReturn(true);

        assertThatThrownBy(() -> memberAccountService.createAccount(CURRENT_USER, MEMBER_ID, CREATE_REQUEST))
                .isInstanceOf(DuplicateDniException.class)
                .hasMessage("dni 30111222 is already in use in this club");

        verify(userAccountRepository, never()).save(any());
    }

    @Test
    void shouldReplacePasswordHashWhenResettingMemberAccountPassword() {
        UserAccount existingAccount = stubResettableAccount();
        when(passwordEncoder.encode(NEW_RAW_PASSWORD)).thenReturn(NEW_HASHED_PASSWORD);

        memberAccountService.resetPassword(CURRENT_USER, MEMBER_ID, RESET_REQUEST);

        assertThat(existingAccount.getPasswordHash()).isEqualTo(NEW_HASHED_PASSWORD);
    }

    @Test
    void shouldRevokeTheRefreshTokensOfTheAccountWhenResettingItsPassword() {
        stubResettableAccount();

        memberAccountService.resetPassword(CURRENT_USER, MEMBER_ID, RESET_REQUEST);

        verify(refreshTokenService).revokeActiveTokens(ACCOUNT_ID);
    }

    @Test
    void shouldRecordWhenTheMemberPasswordWasResetSoAccessTokensIssuedBeforeStopWorking() {
        Instant beforeReset = Instant.now();
        UserAccount existingAccount = stubResettableAccount();

        memberAccountService.resetPassword(CURRENT_USER, MEMBER_ID, RESET_REQUEST);

        assertThat(existingAccount.getSessionsRevokedAt()).isBetween(beforeReset, Instant.now());
    }

    @Test
    void shouldRecordWhoResetTheMemberPasswordWithoutTouchingTheCreator() {
        UserAccount existingAccount = stubResettableAccount();

        memberAccountService.resetPassword(CURRENT_USER, MEMBER_ID, RESET_REQUEST);

        assertThat(existingAccount.getUpdatedByUserId()).isEqualTo(ACTING_USER_ID);
        assertThat(existingAccount.getCreatedByUserId()).isEqualTo(PREVIOUS_ACTOR_ID);
        assertThat(existingAccount.getUpdatedAt()).isNotEqualTo(CREATED_AT);
    }

    @Test
    void shouldReturnTheAccountAfterResettingItsPassword() {
        stubResettableAccount();

        MemberAccountResponse response = memberAccountService.resetPassword(CURRENT_USER, MEMBER_ID, RESET_REQUEST);

        assertThat(response.id()).isEqualTo(ACCOUNT_ID);
        assertThat(response.updatedByUserId()).isEqualTo(ACTING_USER_ID);
    }

    @Test
    void shouldThrowMemberNotFoundWhenResettingPasswordOfMemberFromAnotherClub() {
        when(memberRepository.existsByIdAndClubId(MEMBER_ID, CLUB_ID)).thenReturn(false);

        assertThatThrownBy(() -> memberAccountService.resetPassword(CURRENT_USER, MEMBER_ID, RESET_REQUEST))
                .isInstanceOf(MemberNotFoundException.class);

        verifyNoInteractions(userAccountRepository, refreshTokenService);
    }

    @Test
    void shouldThrowMemberAccountNotFoundWhenMemberHasNoMemberAccount() {
        when(memberRepository.existsByIdAndClubId(MEMBER_ID, CLUB_ID)).thenReturn(true);
        when(userAccountRepository.findByMemberIdAndClubIdAndRoleForUpdate(MEMBER_ID, CLUB_ID, UserRole.MEMBER))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> memberAccountService.resetPassword(CURRENT_USER, MEMBER_ID, RESET_REQUEST))
                .isInstanceOf(MemberAccountNotFoundException.class)
                .hasMessage("member 34 has no member account");

        verify(userAccountRepository, never()).save(any());
        verifyNoInteractions(refreshTokenService);
    }

    @Test
    void shouldNotCheckTheMemberAppFlagWhenResettingPassword() {
        stubResettableAccount();

        memberAccountService.resetPassword(CURRENT_USER, MEMBER_ID, RESET_REQUEST);

        verifyNoInteractions(clubRepository);
    }

    private void stubCreatableMember(Member member) {
        when(clubRepository.findById(CLUB_ID)).thenReturn(Optional.of(club(true)));
        when(memberRepository.findByIdAndClubId(MEMBER_ID, CLUB_ID)).thenReturn(Optional.of(member));
        when(userAccountRepository.existsByMemberIdAndClubId(MEMBER_ID, CLUB_ID)).thenReturn(false);
        when(userAccountRepository.existsByClubIdAndDni(CLUB_ID, DNI)).thenReturn(false);
    }

    private UserAccount stubResettableAccount() {
        UserAccount existingAccount = account(UserRole.MEMBER);
        when(memberRepository.existsByIdAndClubId(MEMBER_ID, CLUB_ID)).thenReturn(true);
        when(userAccountRepository.findByMemberIdAndClubIdAndRoleForUpdate(MEMBER_ID, CLUB_ID, UserRole.MEMBER))
                .thenReturn(Optional.of(existingAccount));
        when(userAccountRepository.save(existingAccount)).thenReturn(existingAccount);

        return existingAccount;
    }

    private UserAccount capturedSavedAccount() {
        ArgumentCaptor<UserAccount> savedAccountCaptor = ArgumentCaptor.forClass(UserAccount.class);
        verify(userAccountRepository).save(savedAccountCaptor.capture());

        return savedAccountCaptor.getValue();
    }

    private Club club(boolean memberAppEnabled) {
        return Club.builder()
                .id(CLUB_ID)
                .name("Club 1")
                .memberAppEnabled(memberAppEnabled)
                .build();
    }

    private Member member(MemberStatus status, String dni) {
        return Member.builder()
                .id(MEMBER_ID)
                .clubId(CLUB_ID)
                .firstName("Ana")
                .lastName("Pérez")
                .dni(dni)
                .status(status)
                .build();
    }

    private UserAccount account(UserRole role) {
        return UserAccount.builder()
                .id(ACCOUNT_ID)
                .clubId(CLUB_ID)
                .memberId(MEMBER_ID)
                .dni(DNI)
                .passwordHash(HASHED_PASSWORD)
                .role(role)
                .createdAt(CREATED_AT)
                .updatedAt(CREATED_AT)
                .createdByUserId(PREVIOUS_ACTOR_ID)
                .updatedByUserId(PREVIOUS_ACTOR_ID)
                .build();
    }
}
