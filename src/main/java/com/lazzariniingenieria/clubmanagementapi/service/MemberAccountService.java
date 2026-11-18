package com.lazzariniingenieria.clubmanagementapi.service;

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
import com.lazzariniingenieria.clubmanagementapi.repository.ClubRepository;
import com.lazzariniingenieria.clubmanagementapi.repository.MemberRepository;
import com.lazzariniingenieria.clubmanagementapi.repository.UserAccountRepository;
import com.lazzariniingenieria.clubmanagementapi.security.AuthenticatedUser;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class MemberAccountService {

    private final UserAccountRepository userAccountRepository;
    private final MemberRepository memberRepository;
    private final ClubRepository clubRepository;
    private final PasswordEncoder passwordEncoder;
    private final MemberAccountMapper memberAccountMapper;
    private final RefreshTokenService refreshTokenService;

    public Optional<MemberAccountResponse> getAccount(Long clubId, Long memberId) {
        validateMemberBelongsToClub(clubId, memberId);
        Optional<UserAccount> account = userAccountRepository.findByMemberIdAndClubId(memberId, clubId);

        if (account.isEmpty()) {
            return Optional.empty();
        }

        MemberAccountResponse response = memberAccountMapper.toResponse(account.get());

        return Optional.of(response);
    }

    public MemberAccountResponse createAccount(AuthenticatedUser currentUser, Long memberId, CreateMemberAccountRequest request) {
        Long clubId = currentUser.clubId();
        validateMemberAppEnabled(clubId);
        Member member = memberRepository.findByIdAndClubId(memberId, clubId).orElseThrow(() -> new MemberNotFoundException(memberId));
        validateMemberCanHaveAccount(member);
        validateNoAccountYet(clubId, member);

        UserAccount account = buildMemberAccount(currentUser, member, request.password());
        UserAccount savedAccount = userAccountRepository.save(account);
        log.info("Created member account userAccountId={} for memberId={} in clubId={} by userAccountId={}", savedAccount.getId(),
                memberId, clubId, currentUser.userAccountId());

        return memberAccountMapper.toResponse(savedAccount);
    }

    @Transactional
    public MemberAccountResponse resetPassword(AuthenticatedUser currentUser, Long memberId, ResetMemberAccountPasswordRequest request) {
        Long clubId = currentUser.clubId();
        validateMemberBelongsToClub(clubId, memberId);
        UserAccount account = findMemberAccountForUpdateOrThrow(clubId, memberId);
        account.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        account.setSessionsRevokedAt(Instant.now());
        markUpdated(account, currentUser.userAccountId());

        UserAccount savedAccount = userAccountRepository.save(account);
        refreshTokenService.revokeActiveTokens(account.getId());
        log.info("Reset password for member account userAccountId={} of memberId={} in clubId={}", account.getId(), memberId, clubId);

        return memberAccountMapper.toResponse(savedAccount);
    }

    private void validateMemberAppEnabled(Long clubId) {
        Club club = clubRepository.findById(clubId).orElseThrow(() -> new ClubNotFoundException(clubId));

        if (!club.isMemberAppEnabled()) {
            throw new InvalidMemberAccountException("member app is not enabled for this club");
        }
    }

    private void validateMemberBelongsToClub(Long clubId, Long memberId) {
        boolean memberExists = memberRepository.existsByIdAndClubId(memberId, clubId);

        if (!memberExists) {
            throw new MemberNotFoundException(memberId);
        }
    }

    private void validateMemberCanHaveAccount(Member member) {
        if (member.getStatus() == MemberStatus.INACTIVE) {
            throw new InvalidMemberAccountException("member " + member.getId() + " is inactive");
        }

        if (member.getDni() == null) {
            throw new InvalidMemberAccountException("member " + member.getId() + " has no dni");
        }
    }

    private void validateNoAccountYet(Long clubId, Member member) {
        boolean memberHasAccount = userAccountRepository.existsByMemberIdAndClubId(member.getId(), clubId);

        if (memberHasAccount) {
            throw new MemberAlreadyHasAccountException(member.getId());
        }

        boolean dniInUse = userAccountRepository.existsByClubIdAndDni(clubId, member.getDni());

        if (dniInUse) {
            throw new DuplicateDniException(member.getDni());
        }
    }

    private UserAccount buildMemberAccount(AuthenticatedUser currentUser, Member member, String rawPassword) {
        Instant now = Instant.now();

        return UserAccount.builder()
                .clubId(currentUser.clubId())
                .memberId(member.getId())
                .dni(member.getDni())
                .passwordHash(passwordEncoder.encode(rawPassword))
                .role(UserRole.MEMBER)
                .createdAt(now)
                .updatedAt(now)
                .createdByUserId(currentUser.userAccountId())
                .updatedByUserId(currentUser.userAccountId())
                .build();
    }

    private void markUpdated(UserAccount account, Long actingUserId) {
        account.setUpdatedAt(Instant.now());
        account.setUpdatedByUserId(actingUserId);
    }

    private UserAccount findMemberAccountForUpdateOrThrow(Long clubId, Long memberId) {
        return userAccountRepository
                .findByMemberIdAndClubIdAndRoleForUpdate(memberId, clubId, UserRole.MEMBER)
                .orElseThrow(() -> new MemberAccountNotFoundException(memberId));
    }
}
