package com.lazzariniingenieria.clubmanagementapi.service;

import com.lazzariniingenieria.clubmanagementapi.dto.CreateMemberRequest;
import com.lazzariniingenieria.clubmanagementapi.dto.MemberResponse;
import com.lazzariniingenieria.clubmanagementapi.dto.UpdateMemberRequest;
import com.lazzariniingenieria.clubmanagementapi.entity.Member;
import com.lazzariniingenieria.clubmanagementapi.entity.MemberStatus;
import com.lazzariniingenieria.clubmanagementapi.exception.DuplicateDniException;
import com.lazzariniingenieria.clubmanagementapi.exception.FamilyGroupNotFoundException;
import com.lazzariniingenieria.clubmanagementapi.exception.MemberNotFoundException;
import com.lazzariniingenieria.clubmanagementapi.mapper.MemberMapper;
import com.lazzariniingenieria.clubmanagementapi.repository.FamilyGroupRepository;
import com.lazzariniingenieria.clubmanagementapi.repository.MemberRepository;
import com.lazzariniingenieria.clubmanagementapi.security.AuthenticatedUser;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class MemberService {

    private final MemberRepository memberRepository;
    private final MemberMapper memberMapper;
    private final FamilyGroupRepository familyGroupRepository;

    public MemberResponse createMember(AuthenticatedUser currentUser, CreateMemberRequest request) {
        Long clubId = currentUser.clubId();
        boolean dniInUse = memberRepository.existsByClubIdAndDni(clubId, request.dni());

        if (dniInUse) {
            throw new DuplicateDniException(request.dni());
        }

        Instant now = Instant.now();
        Member member = Member.builder()
                .clubId(clubId)
                .firstName(request.firstName())
                .lastName(request.lastName())
                .dni(request.dni())
                .phone(request.phone())
                .email(request.email())
                .joinedAt(LocalDate.now())
                .status(MemberStatus.ACTIVE)
                .createdAt(now)
                .updatedAt(now)
                .createdByUserId(currentUser.userAccountId())
                .updatedByUserId(currentUser.userAccountId())
                .build();

        Member savedMember = memberRepository.save(member);
        log.info("Created member memberId={} for clubId={}", savedMember.getId(), clubId);

        return memberMapper.toResponse(savedMember);
    }

    public List<MemberResponse> listMembers(Long clubId) {
        List<Member> members = memberRepository.findByClubIdOrderByCreatedAtDesc(clubId);

        return memberMapper.toResponseList(members);
    }

    public MemberResponse getMember(Long clubId, Long memberId) {
        Member member = findMemberOrThrow(clubId, memberId);

        return memberMapper.toResponse(member);
    }

    public MemberResponse updateMember(AuthenticatedUser currentUser, Long memberId, UpdateMemberRequest request) {
        Long clubId = currentUser.clubId();
        Member member = findMemberOrThrow(clubId, memberId);
        boolean dniTakenByAnotherMember = memberRepository.existsByClubIdAndDniAndIdNot(clubId, request.dni(), memberId);

        if (dniTakenByAnotherMember) {
            throw new DuplicateDniException(request.dni());
        }

        member.setFirstName(request.firstName());
        member.setLastName(request.lastName());
        member.setDni(request.dni());
        member.setPhone(request.phone());
        member.setEmail(request.email());
        markUpdated(member, currentUser.userAccountId());

        Member savedMember = memberRepository.save(member);
        log.info("Updated member memberId={} for clubId={}", memberId, clubId);

        return memberMapper.toResponse(savedMember);
    }

    public MemberResponse deactivateMember(AuthenticatedUser currentUser, Long memberId) {
        Long clubId = currentUser.clubId();
        Member member = findMemberOrThrow(clubId, memberId);
        member.setStatus(MemberStatus.INACTIVE);
        markUpdated(member, currentUser.userAccountId());

        Member savedMember = memberRepository.save(member);
        log.info("Deactivated member memberId={} for clubId={}", memberId, clubId);

        return memberMapper.toResponse(savedMember);
    }

    public MemberResponse reactivateMember(AuthenticatedUser currentUser, Long memberId) {
        Long clubId = currentUser.clubId();
        Member member = findMemberOrThrow(clubId, memberId);
        member.setStatus(MemberStatus.ACTIVE);
        markUpdated(member, currentUser.userAccountId());

        Member savedMember = memberRepository.save(member);
        log.info("Reactivated member memberId={} for clubId={}", memberId, clubId);

        return memberMapper.toResponse(savedMember);
    }

    public MemberResponse assignFamilyGroup(AuthenticatedUser currentUser, Long memberId, Long familyGroupId) {
        Long clubId = currentUser.clubId();
        Member member = findMemberOrThrow(clubId, memberId);
        boolean familyGroupExists = familyGroupRepository.existsByIdAndClubId(familyGroupId, clubId);

        if (!familyGroupExists) {
            throw new FamilyGroupNotFoundException(familyGroupId);
        }

        member.setFamilyGroupId(familyGroupId);
        markUpdated(member, currentUser.userAccountId());

        Member savedMember = memberRepository.save(member);
        log.info("Assigned familyGroupId={} to member memberId={} for clubId={}", familyGroupId, memberId, clubId);

        return memberMapper.toResponse(savedMember);
    }

    public MemberResponse unassignFamilyGroup(AuthenticatedUser currentUser, Long memberId) {
        Long clubId = currentUser.clubId();
        Member member = findMemberOrThrow(clubId, memberId);
        member.setFamilyGroupId(null);
        markUpdated(member, currentUser.userAccountId());

        Member savedMember = memberRepository.save(member);
        log.info("Unassigned family group from member memberId={} for clubId={}", memberId, clubId);

        return memberMapper.toResponse(savedMember);
    }

    private void markUpdated(Member member, Long actingUserId) {
        member.setUpdatedAt(Instant.now());
        member.setUpdatedByUserId(actingUserId);
    }

    private Member findMemberOrThrow(Long clubId, Long memberId) {
        return memberRepository
                .findByIdAndClubId(memberId, clubId)
                .orElseThrow(() -> new MemberNotFoundException(memberId));
    }
}
